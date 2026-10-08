package com.teufel.statusmod.fabric;

import net.fabricmc.api.ClientModInitializer;import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Client side: registers a keybind (default G, rebindable in vanilla settings
 * under "Status Mod") that sends "/status" as the player's own chat command,
 * which makes the server open the self-status GUI. Server-side players without
 * the mod installed keep using /status typed manually.
 * Never loaded on dedicated servers (client entrypoint only).
 *
 * Version notes: KeyMapping/connection/sendCommand member names differ between
 * 1.21.11 and 26.x, so command sending and click polling use reflection with
 * fallbacks; everything else is stable Mojang API on both versions.
 */
public final class StatusModClient implements ClientModInitializer {
    private static KeyMapping openMenuKey;
    /** GLFW_KEY_G value as int (26.3 dropped LWJGL-GLFW for SDL, so no import). */
    private static final int DEFAULT_MENU_KEY = 71;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = null;
        try {
            Identifier id = newIdentifier("statusmod", "general");
            if (id != null) {
                category = KeyMapping.Category.register(id);
            }
        } catch (Throwable ignored) {}
        if (category == null) {
            try {
                category = KeyMapping.Category.MISC;
            } catch (Throwable ignored) {}
        }
        if (category == null) return;
        try {
            // 3-arg ctor (defaults to keyboard input); the Type enum changed
            // across versions (KEYSYM < 26.3, KEYBOARD >= 26.3), so it is avoided.
            openMenuKey = registerMapping(new KeyMapping(
                "key.statusmod.open_menu",
                DEFAULT_MENU_KEY,
                category
            ));
        } catch (Throwable e) {
            System.err.println("[StatusMod] Could not register menu keybind: " + e.getMessage());
            return;
        }
        if (openMenuKey == null) {
            System.err.println("[StatusMod] Could not register menu keybind (no FAPI helper).");
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(StatusModClient::onEndTick);
    }

    /**
     * Registers via FAPI. The helper moved between FAPI releases
     * (keymapping.v1/KeyMappingHelper/registerKeyMapping on new ones,
     * keybinding.v1/KeyBindingHelper/registerKeyBinding on old ones),
     * so both are tried reflectively.
     */
    private static KeyMapping registerMapping(KeyMapping mapping) {
        try {
            Class<?> helper = Class.forName("net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper");
            Method m = helper.getMethod("registerKeyMapping", KeyMapping.class);
            Object out = m.invoke(null, mapping);
            if (out instanceof KeyMapping km) return km;
            return mapping;
        } catch (Throwable ignored) {}
        try {
            Class<?> helper = Class.forName("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper");
            Method m = helper.getMethod("registerKeyBinding", KeyMapping.class);
            Object out = m.invoke(null, mapping);
            if (out instanceof KeyMapping km) return km;
            return mapping;
        } catch (Throwable ignored) {}
        return null;
    }

    private static void onEndTick(Minecraft client) {
        try {
            if (client == null || client.player == null || openMenuKey == null) return;
            Object connection = client.player.connection;
            if (connection == null) return;
            boolean pressed = pollClick();
            if (!pressed) return;
            sendStatusCommand(connection);
        } catch (Throwable ignored) {}
    }

    private static boolean pollClick() {
        try {
            Method consume = KeyMapping.class.getMethod("consumeClick");
            while ((Boolean) consume.invoke(openMenuKey)) {
                return true;
            }
            return false;
        } catch (Throwable ignored) {}
        try {
            Method wasPressed = KeyMapping.class.getMethod("wasPressed");
            while ((Boolean) wasPressed.invoke(openMenuKey)) {
                return true;
            }
            return false;
        } catch (Throwable ignored) {}
        return false;
    }

    private static void sendStatusCommand(Object connection) {
        try {
            Method sendCommand = connection.getClass().getMethod("sendCommand", String.class);
            sendCommand.invoke(connection, "status");
            return;
        } catch (Throwable ignored) {}
        try {
            Method sendChatCommand = connection.getClass().getMethod("sendChatCommand", String.class);
            sendChatCommand.invoke(connection, "status");
        } catch (Throwable ignored) {}
    }

    private static Identifier newIdentifier(String namespace, String path) {
        try {
            Method of = Identifier.class.getMethod("of", String.class, String.class);
            return (Identifier) of.invoke(null, namespace, path);
        } catch (Throwable ignored) {}
        try {
            Constructor<Identifier> ctor = Identifier.class.getConstructor(String.class, String.class);
            return ctor.newInstance(namespace, path);
        } catch (Throwable ignored) {}
        return null;
    }
}
