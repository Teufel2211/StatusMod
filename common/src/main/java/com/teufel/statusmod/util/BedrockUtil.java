package com.teufel.statusmod.util;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Bedrock-Spieler-Erkennung ueber die Floodgate-API - bewusst nur via
 * Reflection, damit StatusMod auch OHNE installiertes Floodgate laeuft.
 *
 * <p>Hintergrund: Floodgate vergibt Bedrock-Spielern stabile Java-UUIDs, alle
 * UUID-basierten Features (Status, Sync, Mutes, Blocks, Avatare) funktionieren
 * daher unveraendert. Nur Koepfe haben ohne Mojang-Texturen kein Skin
 * (Steve-Fallback); dafuer gibt es /status avatar.
 */
public final class BedrockUtil {
    private BedrockUtil() {}

    private static volatile Boolean floodgatePresent = null;

    public static boolean isFloodgatePresent() {
        Boolean cached = floodgatePresent;
        if (cached != null) {
            return cached;
        }
        boolean present;
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            present = true;
        } catch (Throwable ignored) {
            present = false;
        }
        floodgatePresent = present;
        return present;
    }

    public static boolean isBedrockPlayer(UUID uuid) {
        if (uuid == null || !isFloodgatePresent()) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) {
                return false;
            }
            Method m = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            Object result = m.invoke(api, uuid);
            return result instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Bedrock-Anzeigename (inkl. Prefix), oder null wenn unbekannt/kein Bedrock.
     */
    public static String bedrockNameOrNull(UUID uuid) {
        if (uuid == null || !isFloodgatePresent()) {
            return null;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) {
                return null;
            }
            Object player = apiClass.getMethod("getPlayer", UUID.class).invoke(api, uuid);
            if (player == null) {
                return null;
            }
            Object name = player.getClass().getMethod("getJavaUsername").invoke(player);
            return name instanceof String s && !s.isBlank() ? s : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
