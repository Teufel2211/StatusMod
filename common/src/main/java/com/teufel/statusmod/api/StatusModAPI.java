package com.teufel.statusmod.api;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.StatusTeamUtil;
import com.teufel.statusmod.util.StatusTextUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class StatusModAPI {
    private StatusModAPI() {}

    public static String getStatus(UUID uuid) {
        if (uuid == null || StatusMod.storage == null) return "";
        PlayerSettings settings = StatusMod.storage.forPlayer(uuid.toString());
        return settings != null ? settings.status : "";
    }

    public static String getStatusColor(UUID uuid) {
        if (uuid == null || StatusMod.storage == null) return "reset";
        PlayerSettings settings = StatusMod.storage.forPlayer(uuid.toString());
        return settings != null ? settings.color : "reset";
    }

    public static boolean isAfk(UUID uuid) {
        if (uuid == null || StatusMod.storage == null) return false;
        PlayerSettings settings = StatusMod.storage.forPlayer(uuid.toString());
        return settings != null && settings.autoAfk;
    }

    public static void setStatus(UUID uuid, String status, String color) {
        if (uuid == null || StatusMod.storage == null || status == null) return;
        String key = uuid.toString();
        PlayerSettings settings = StatusMod.storage.forPlayer(key);
        settings.status = status;
        settings.color = (color != null && !color.isEmpty()) ? color : "reset";
        settings.lastStatusChangeAtMs = System.currentTimeMillis();
        StatusMod.storage.put(key, settings);
    }

    public static void clearStatus(UUID uuid) {
        if (uuid == null || StatusMod.storage == null) return;
        String key = uuid.toString();
        PlayerSettings settings = StatusMod.storage.forPlayer(key);
        settings.status = "";
        settings.color = "reset";
        settings.statusExpiresAtMs = 0L;
        StatusMod.storage.put(key, settings);
    }

    public static void refreshDisplay(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null || StatusMod.storage == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) return;
        String key = uuid.toString();
        PlayerSettings settings = StatusMod.storage.forPlayer(key);
        String status = StatusTextUtil.resolveStatusForPlayer(settings, player);
        String color = StatusTextUtil.resolveColorForPlayer(settings, player);
        StatusTeamUtil.applyStatus(server.getScoreboard(), player, settings, status, color, false);
    }
}
