package com.teufel.statusmod.lifecycle;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.ColorMapper;
import com.teufel.statusmod.util.PermissionUtil;
import com.teufel.statusmod.util.StatusTeamUtil;
import com.teufel.statusmod.util.StatusTextUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StatusLifecycle {
    private static final int DEFAULT_REAPPLY_INTERVAL_TICKS = 100;
    private static final int MIN_REAPPLY_INTERVAL_TICKS = 20;
    private static final long AFK_CHECK_INTERVAL_MS = 5_000L;
    private static int tickCounter = 0;
    private static long lastAfkCheckMs = 0L;
    private static int cachedConfiguredInterval = DEFAULT_REAPPLY_INTERVAL_TICKS;
    private static long lastConfigRefreshMs = 0L;
    private static final long CONFIG_REFRESH_INTERVAL_MS = 60_000L;
    private static final Map<String, String> lastPlayerPositions = new ConcurrentHashMap<>();

    private StatusLifecycle() {}

    public static void onPlayerJoin(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null || StatusMod.storage == null) return;
        String uuid = player.getUUID().toString();
        try {
            PlayerSettings settings = StatusMod.storage.forPlayer(uuid);
            settings.lastActivityAtMs = System.currentTimeMillis();
            settings.autoAfk = false;
            String status = StatusTextUtil.resolveStatusForPlayer(settings, player);
            if (status != null && !status.isEmpty()) {
                reapplyStatus(server, player, uuid, settings);
            }
        } catch (Exception e) {
            System.err.println("[StatusMod] Error restoring status for player " + player.getScoreboardName());
            e.printStackTrace();
        }
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        if (player != null) {
            lastPlayerPositions.remove(player.getUUID().toString());
        }
    }

    public static void onPlayerMove(ServerPlayer player) {
        markActive(player);
    }

    public static void onPlayerChat(ServerPlayer player) {
        markActive(player);
    }

    public static void onPlayerCommand(ServerPlayer player) {
        markActive(player);
    }

    private static MinecraftServer getPlayerServer(ServerPlayer player) {
        if (player == null) return null;
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("getServer");
            return (MinecraftServer) m.invoke(player);
        } catch (Exception ignored) {}
        try {
            Object level = player.getClass().getMethod("level").invoke(player);
            if (level != null) {
                java.lang.reflect.Method m = level.getClass().getMethod("getServer");
                return (MinecraftServer) m.invoke(level);
            }
        } catch (Exception ignored) {}
        try {
            Object level = player.getClass().getMethod("getLevel").invoke(player);
            if (level != null) {
                java.lang.reflect.Method m = level.getClass().getMethod("getServer");
                return (MinecraftServer) m.invoke(level);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void markActive(ServerPlayer player) {
        if (player == null || StatusMod.storage == null) return;
        try {
            PlayerSettings ps = StatusMod.storage.forPlayer(player.getUUID().toString());
            if (ps == null) return;
            ps.lastActivityAtMs = System.currentTimeMillis();
            if (ps.autoAfk) {
                ps.autoAfk = false;
                ps.status = "";
                ps.color = "reset";
                StatusMod.storage.put(player.getUUID().toString(), ps);
                MinecraftServer server = getPlayerServer(player);
                if (server != null) {
                    reapplyStatus(server, player, player.getUUID().toString(), ps);
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aDu bist nicht mehr AFK."));
            }
        } catch (Exception ignored) {}
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null || StatusMod.storage == null) return;
        tickCounter++;

        long now = System.currentTimeMillis();
        if ((now - lastConfigRefreshMs) > CONFIG_REFRESH_INTERVAL_MS) {
            cachedConfiguredInterval = DEFAULT_REAPPLY_INTERVAL_TICKS;
            try {
                if (StatusMod.config != null) {
                    cachedConfiguredInterval = Math.max(MIN_REAPPLY_INTERVAL_TICKS, StatusMod.config.statusReapplyTicks);
                }
            } catch (Exception ignored) {}
            lastConfigRefreshMs = now;
        }

        int effectiveInterval = cachedConfiguredInterval;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                PlayerSettings ps = StatusMod.storage.forPlayer(player.getUUID().toString());
                if (ps != null && ColorMapper.isAnimatedColorInput(ps.color)) {
                    effectiveInterval = MIN_REAPPLY_INTERVAL_TICKS;
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (tickCounter < effectiveInterval) return;
        tickCounter = 0;

        boolean afkEnabled = StatusMod.config != null && StatusMod.config.enableAutoAfk;
        int afkTimeoutMs = (StatusMod.config != null ? StatusMod.config.afkTimeoutSeconds : 300) * 1000;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String uuid = player.getUUID().toString();
            try {
                PlayerSettings settings = StatusMod.storage.forPlayer(uuid);

                if (settings.statusExpiresAtMs > 0L && now >= settings.statusExpiresAtMs) {
                    settings.status = "";
                    settings.color = "reset";
                    settings.statusExpiresAtMs = 0L;
                    StatusMod.storage.put(uuid, settings);
                }

                String posKey = player.getX() + "," + player.getY() + "," + player.getZ();
                String lastPos = lastPlayerPositions.get(uuid);
                if (lastPos == null || !lastPos.equals(posKey)) {
                    settings.lastActivityAtMs = now;
                    lastPlayerPositions.put(uuid, posKey);
                    if (settings.autoAfk) {
                        settings.autoAfk = false;
                        settings.status = "";
                        settings.color = "reset";
                        StatusMod.storage.put(uuid, settings);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aDu bist nicht mehr AFK."));
                    }
                }

                if (afkEnabled && !settings.autoAfk && (now - lastAfkCheckMs) >= AFK_CHECK_INTERVAL_MS) {
                    if (settings.status.isEmpty() && (now - settings.lastActivityAtMs) >= afkTimeoutMs) {
                        settings.autoAfk = true;
                        settings.status = "AFK";
                        settings.color = "yellow";
                        settings.lastStatusChangeAtMs = now;
                        StatusMod.storage.put(uuid, settings);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eDu bist nun AFK (inaktiv seit " + ((now - settings.lastActivityAtMs) / 1000L) + "s)."));
                    }
                }

                String status = StatusTextUtil.resolveStatusForPlayer(settings, player);
                if (status == null || status.isEmpty()) continue;
                reapplyStatus(server, player, uuid, settings);
            } catch (Exception e) {
                System.err.println("[StatusMod] Error during periodic status reapply for " + player.getScoreboardName());
                e.printStackTrace();
            }
        }
        lastAfkCheckMs = now;
    }

    private static void reapplyStatus(MinecraftServer server, ServerPlayer player, String uuid, PlayerSettings settings) {
        try {
            var scoreboard = server.getScoreboard();
            String status = StatusTextUtil.resolveStatusForPlayer(settings, player);
            String color = StatusTextUtil.resolveColorForPlayer(settings, player);
            StatusTeamUtil.applyStatus(scoreboard, player, settings, status, color, PermissionUtil.hasAdminPermission(player));
        } catch (Exception e) {
            System.err.println("[StatusMod] Error reapplying status for player " + player.getScoreboardName());
            e.printStackTrace();
        }
    }
}
