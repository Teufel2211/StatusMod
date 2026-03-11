package com.teufel.statusmod.util;

import com.teufel.statusmod.storage.PlayerSettings;
import net.minecraft.server.level.ServerPlayer;

public final class StatusTextUtil {
    private StatusTextUtil() {}

    public static String renderStatusText(PlayerSettings settings) {
        if (settings == null) return "";
        return renderStatusText(settings.status, settings);
    }

    public static String renderStatusText(String rawStatus, PlayerSettings settings) {
        return renderStatusText(rawStatus, settings, null);
    }

    public static String renderStatusText(String rawStatus, PlayerSettings settings, ServerPlayer player) {
        String status = rawStatus == null ? "" : rawStatus;
        String font = settings == null ? "normal" : settings.fontStyle;
        boolean brackets = settings != null && settings.brackets;

        status = applyPlaceholders(status, player);
        String transformed = FontMapper.apply(font, status);
        return (brackets ? "[" : "") + transformed + (brackets ? "]" : "");
    }

    public static String resolveStatusForPlayer(PlayerSettings settings, ServerPlayer player) {
        if (settings == null) return "";
        String status = settings.status == null ? "" : settings.status;
        if (player == null) return status;
        String worldKey = getWorldKey(player);
        if (worldKey != null && settings.statusByWorld != null) {
            String perWorld = settings.statusByWorld.get(worldKey);
            if (perWorld != null) {
                return perWorld;
            }
        }
        return status;
    }

    public static String resolveColorForPlayer(PlayerSettings settings, ServerPlayer player) {
        if (settings == null) return "reset";
        String color = settings.color == null ? "reset" : settings.color;
        if (player == null) return color;
        String worldKey = getWorldKey(player);
        if (worldKey != null && settings.colorByWorld != null) {
            String perWorld = settings.colorByWorld.get(worldKey);
            if (perWorld != null) {
                return perWorld;
            }
        }
        return color;
    }

    private static String getWorldKey(ServerPlayer player) {
        try {
            return player.level().dimension().toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String applyPlaceholders(String status, ServerPlayer player) {
        if (status == null || status.isEmpty() || player == null) return status;
        String out = status;
        try {
            out = out.replace("{world}", getWorldKey(player) == null ? "" : getWorldKey(player));
        } catch (Exception ignored) {}
        try {
            out = out.replace("{ping}", String.valueOf(getPing(player)));
        } catch (Exception ignored) {}
        return out;
    }

    private static int getPing(ServerPlayer player) {
        try {
            Object conn = player.connection;
            if (conn != null) {
                try {
                    java.lang.reflect.Method m = conn.getClass().getMethod("getLatency");
                    Object v = m.invoke(conn);
                    if (v instanceof Number n) return n.intValue();
                } catch (Exception ignored) {}
                try {
                    java.lang.reflect.Field f = conn.getClass().getDeclaredField("latency");
                    f.setAccessible(true);
                    Object v = f.get(conn);
                    if (v instanceof Number n) return n.intValue();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return -1;
    }
}
