package com.teufel.statusmod.lifecycle;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.ModConfig;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.sync.SyncManager;
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
    private static final int MIN_REAPPLY_INTERVAL_TICKS = 5;
    private static final long AFK_CHECK_INTERVAL_MS = 5_000L;
    private static int tickCounter = 0;
    private static long lastAfkCheckMs = 0L;
    private static int cachedConfiguredInterval = DEFAULT_REAPPLY_INTERVAL_TICKS;
    private static long lastConfigRefreshMs = 0L;
    private static final long CONFIG_REFRESH_INTERVAL_MS = 60_000L;
    private static final Map<String, String> lastPlayerPositions = new ConcurrentHashMap<>();
    private static long lastBossbarMs = 0L;
    private static final long BOSSBAR_INTERVAL_MS = 2500L;
    private static String lastOverviewJson = null;

    /**
     * Maintains config/statusmod/statuses.json with every known player's
     * status (uuid, name, status, color, online flag). Skips the write when
     * nothing changed. Runs on the tick pass, so every mutation path
     * (commands, GUI, AFK, sync-pull) is covered automatically.
     */
    private static void writeStatusOverview(MinecraftServer server) {
        try {
            java.util.Map<String, PlayerSettings> all =
                new java.util.HashMap<>(StatusMod.storage.getAllSnapshot());
            java.util.Map<String, String> onlineNames = new java.util.HashMap<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                String uuid = p.getUUID().toString();
                onlineNames.put(uuid, p.getScoreboardName());
                if (!all.containsKey(uuid)) {
                    all.put(uuid, StatusMod.storage.forPlayer(uuid));
                }
            }
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            java.util.List<String> uuids = new java.util.ArrayList<>(all.keySet());
            uuids.sort(String::compareToIgnoreCase);
            for (String uuid : uuids) {
                PlayerSettings s = all.get(uuid);
                if (s == null) continue;
                String name = onlineNames.get(uuid);
                if (name == null || name.isEmpty()) name = s.lastKnownName == null ? "" : s.lastKnownName;
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("uuid", uuid);
                o.addProperty("name", name);
                o.addProperty("status", s.status == null ? "" : s.status);
                o.addProperty("color", s.color == null ? "reset" : s.color);
                o.addProperty("online", onlineNames.containsKey(uuid));
                arr.add(o);
            }
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            root.addProperty("updatedAt", System.currentTimeMillis());
            root.add("players", arr);
            String json = new com.google.gson.Gson().toJson(root);
            if (json.equals(lastOverviewJson)) return;
            lastOverviewJson = json;
            java.nio.file.Path dir = java.nio.file.Paths.get("config/statusmod");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path tmp = dir.resolve("statuses.json.tmp");
            java.nio.file.Path dst = dir.resolve("statuses.json");
            java.nio.file.Files.writeString(tmp, json, java.nio.charset.StandardCharsets.UTF_8);
            try {
                java.nio.file.Files.move(tmp, dst,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable ignored) {
                java.nio.file.Files.move(tmp, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable ignored) {}
    }

    private StatusLifecycle() {}

    public static void onPlayerJoin(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null || StatusMod.storage == null) return;
        String uuid = player.getUUID().toString();
        try {
            PlayerSettings settings = StatusMod.storage.forPlayer(uuid);
            settings.lastActivityAtMs = System.currentTimeMillis();
            String name = player.getScoreboardName();
            if (name != null && !name.isEmpty()) {
                settings.lastKnownName = name;
            }
            ModConfig cfg = StatusMod.getConfig();
            if (cfg == null) return;
            if (!cfg.restoreStatusOnJoin) {
                settings.status = "";
                settings.color = "reset";
                settings.statusByWorld.clear();
                settings.colorByWorld.clear();
            }
            if (!cfg.restoreAfkOnJoin) {
                settings.autoAfk = false;
            }
            if (!cfg.restoreTimedOnJoin) {
                settings.statusExpiresAtMs = 0L;
            }
            if (settings.status.isEmpty()) {
                settings.autoAfk = false;
            }
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
            try {
                com.teufel.statusmod.display.StatusBossBar.onDisconnect(player);
            } catch (Throwable ignored) {}
        }
    }

    public static void markActive(ServerPlayer player) {
        if (player == null || StatusMod.storage == null) return;
        String uuid = player.getUUID().toString();
        PlayerSettings settings = StatusMod.storage.forPlayer(uuid);
        settings.lastActivityAtMs = System.currentTimeMillis();
        if (settings.autoAfk) {
            returnFromAfk(player, settings, uuid);
        } else {
            StatusMod.storage.put(uuid, settings);
        }
    }

    /** Restores the pre-AFK status stashed when auto-AFK triggered. */
    private static void returnFromAfk(ServerPlayer player, PlayerSettings settings, String uuid) {
        String restored = settings.preAfkStatus == null ? "" : settings.preAfkStatus;
        String restoredColor = settings.preAfkColor == null ? "reset" : settings.preAfkColor;
        settings.autoAfk = false;
        settings.preAfkStatus = "";
        settings.preAfkColor = "reset";
        settings.status = restored;
        settings.color = restoredColor;
        StatusMod.storage.put(uuid, settings);
        SyncManager.requestPush();
        if (restored.isEmpty()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aDu bist nicht mehr AFK."));
        } else {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aDu bist nicht mehr AFK. Status: " + restored));
        }
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null || StatusMod.storage == null) return;
        tickCounter++;

        SyncManager.updateOnlineNames(server);

        long now = System.currentTimeMillis();
        // Bossbar/Actionbar ticker runs on its own cadence (actionbar fades
        // after ~3s, so it needs refreshes faster than the reapply interval).
        if ((now - lastBossbarMs) >= BOSSBAR_INTERVAL_MS) {
            lastBossbarMs = now;
            try {
                com.teufel.statusmod.display.StatusBossBar.sync(server);
            } catch (Throwable ignored) {}
            try {
                com.teufel.statusmod.display.StatusSidebar.sync(server);
            } catch (Throwable ignored) {}
            try {
                writeStatusOverview(server);
            } catch (Throwable ignored) {}
        }
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

        boolean afkEnabled = StatusMod.config != null && StatusMod.config.enableAutoAfk && StatusMod.config.isEnabled("afk");
        int afkTimeoutMs = (StatusMod.config != null ? StatusMod.config.afkTimeoutSeconds : 300) * 1000;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String uuid = player.getUUID().toString();
            try {
                PlayerSettings settings = StatusMod.storage.forPlayer(uuid);

                if (settings.statusExpiresAtMs > 0L && now >= settings.statusExpiresAtMs) {
                    settings.status = "";
                    settings.color = "reset";
                    settings.statusExpiresAtMs = 0L;
                    settings.autoAfk = false;
                    settings.preAfkStatus = "";
                    settings.preAfkColor = "reset";
                    StatusMod.storage.put(uuid, settings);
                }

                String posKey = player.getX() + "," + player.getY() + "," + player.getZ()
                    + "," + player.getYRot() + "," + player.getXRot();
                String lastPos = lastPlayerPositions.get(uuid);
                if (lastPos == null || !lastPos.equals(posKey)) {
                    settings.lastActivityAtMs = now;
                    lastPlayerPositions.put(uuid, posKey);
                    if (settings.autoAfk) {
                        returnFromAfk(player, settings, uuid);
                    } else {
                        StatusMod.storage.put(uuid, settings);
                    }
                }

                if (afkEnabled && !settings.autoAfk && (now - lastAfkCheckMs) >= AFK_CHECK_INTERVAL_MS) {
                    if ((now - settings.lastActivityAtMs) >= afkTimeoutMs) {
                        settings.preAfkStatus = settings.status == null ? "" : settings.status;
                        settings.preAfkColor = settings.color == null ? "reset" : settings.color;
                        settings.autoAfk = true;
                        ModConfig cfg = StatusMod.getConfig();
                        settings.status = (cfg == null || cfg.afkStatusText == null) ? "AFK" : cfg.afkStatusText;
                        settings.color = (cfg == null || cfg.afkColor == null) ? "gray" : cfg.afkColor;
                        settings.lastStatusChangeAtMs = now;
                        StatusMod.storage.put(uuid, settings);
                        SyncManager.requestPush();
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
