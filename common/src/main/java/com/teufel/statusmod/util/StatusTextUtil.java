package com.teufel.statusmod.util;

import com.teufel.statusmod.storage.PlayerSettings;
import net.minecraft.server.level.ServerPlayer;

public final class StatusTextUtil {
    private StatusTextUtil() {}

    public static String renderStatusText(String rawStatus, PlayerSettings settings, ServerPlayer player) {
        String status = rawStatus == null ? "" : rawStatus;
        int bracketStyle = settings != null ? settings.brackets : 0;
        status = applyPlaceholders(status, player);
        return wrapBrackets(status, bracketStyle);
    }

    public static String wrapBrackets(String text, int style) {
        return switch (style) {
            case 1 -> "[" + text + "]";
            case 2 -> "<" + text + ">";
            default -> text;
        };
    }

    public static String resolveStatusForPlayer(PlayerSettings settings, ServerPlayer player) {
        if (settings == null) return "";
        String status = settings.status == null ? "" : settings.status;
        if (player == null) return status;
        String worldKey = CompatUtil.getWorldKey(player);
        if (worldKey != null && settings.statusByWorld != null) {
            String perWorld = settings.statusByWorld.get(worldKey);
            if (perWorld != null) return perWorld;
        }
        return status;
    }

    public static String resolveColorForPlayer(PlayerSettings settings, ServerPlayer player) {
        if (settings == null) return "reset";
        String color = settings.color == null ? "reset" : settings.color;
        if (player == null) return color;
        String worldKey = CompatUtil.getWorldKey(player);
        if (worldKey != null && settings.colorByWorld != null) {
            String perWorld = settings.colorByWorld.get(worldKey);
            if (perWorld != null) return perWorld;
        }
        return color;
    }

    private static String applyPlaceholders(String status, ServerPlayer player) {
        if (status == null || status.isEmpty() || player == null) return status;
        String out = status;
        try {
            out = out.replace("{player}", player.getScoreboardName());
        } catch (Exception ignored) {}
        try {
            out = out.replace("{health}", String.valueOf(Math.round(player.getHealth())));
        } catch (Exception ignored) {}
        try {
            out = out.replace("{x}", String.valueOf((int) player.getX()));
        } catch (Exception ignored) {}
        try {
            out = out.replace("{y}", String.valueOf((int) player.getY()));
        } catch (Exception ignored) {}
        try {
            out = out.replace("{z}", String.valueOf((int) player.getZ()));
        } catch (Exception ignored) {}
        try {
            String worldKey = CompatUtil.getWorldKey(player);
            out = out.replace("{world}", worldKey == null ? "" : worldKey);
        } catch (Exception ignored) {}
        try {
            out = out.replace("{ping}", String.valueOf(getPing(player)));
        } catch (Exception ignored) {}
        try {
            long ticks = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
            int hours = (int) (ticks / 20 / 3600);
            int minutes = (int) ((ticks / 20) % 3600 / 60);
            out = out.replace("{playtime}", hours + "h " + minutes + "m");
        } catch (Exception ignored) {}
        try {
            long dayTime = 0L;
            java.lang.reflect.Method getDayTimeMethod = null;
            try {
                getDayTimeMethod = player.level().getClass().getMethod("getDayTime");
            } catch (NoSuchMethodException ignored) {}
            if (getDayTimeMethod != null) {
                Object result = getDayTimeMethod.invoke(player.level());
                if (result instanceof Long l) dayTime = l % 24000L;
            }
            int hours = (int) ((dayTime / 1000 + 6) % 24);
            int minutes = (int) ((dayTime % 1000) * 60 / 1000);
            out = out.replace("{time}", String.format("%02d:%02d", hours, minutes));
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
