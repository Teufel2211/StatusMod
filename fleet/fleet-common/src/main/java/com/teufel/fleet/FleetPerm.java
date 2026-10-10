package com.teufel.fleet;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;

/**
 * Minimal admin check for the fleet commands (standalone copy: the fleet mod
 * must not depend on StatusMod). Console/RCON always pass; players need OP
 * level 2 (UUID-based vanilla op list) or an equivalent command tier.
 */
public final class FleetPerm {
    private FleetPerm() {}

    public static boolean isAdmin(CommandSourceStack src) {
        try {
            if (src != null && src.getEntity() == null) {
                String name = src.getTextName();
                if ("Server".equals(name) || "Rcon".equals(name)) return true;
            }
        } catch (Exception ignored) {}
        ServerPlayer player = null;
        try { player = src.getPlayer(); } catch (Exception ignored) {}
        if (player == null) {
            if (src != null && tierLevelOf(sourcePermissions(src)) >= 2) return true;
            return false;
        }
        MinecraftServer server = null;
        try { server = src.getServer(); } catch (Exception ignored) {}
        if (server == null) {
            try { server = player.level().getServer(); } catch (Exception ignored) {}
        }
        if (server != null && opLevelOf(server, player) >= 2) return true;
        return tierLevelOf(playerPermissions(player)) >= 2;
    }

    private static int opLevelOf(MinecraftServer server, ServerPlayer player) {
        try {
            if (server == null || player == null) return -1;
            com.mojang.authlib.GameProfile profile = player.getGameProfile();
            if (profile == null) return -1;
            net.minecraft.server.players.ServerOpListEntry entry =
                server.getPlayerList().getOps().get(new NameAndId(profile));
            if (entry == null) return -1;
            try {
                LevelBasedPermissionSet perms = entry.permissions();
                if (perms != null) {
                    PermissionLevel lvl = perms.level();
                    if (lvl != null) return lvl.id();
                }
            } catch (Throwable ignored) {}
            return 4;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static int tierLevelOf(PermissionSet ps) {
        if (ps == null) return -1;
        try {
            if (ps instanceof LevelBasedPermissionSet lbs) {
                try {
                    PermissionLevel lvl = lbs.level();
                    if (lvl != null) return lvl.id();
                } catch (Throwable ignored) {}
            }
            if (ps.hasPermission(Permissions.COMMANDS_OWNER)) return 4;
            if (ps.hasPermission(Permissions.COMMANDS_ADMIN)) return 3;
            if (ps.hasPermission(Permissions.COMMANDS_GAMEMASTER)) return 2;
            if (ps.hasPermission(Permissions.COMMANDS_MODERATOR)) return 1;
        } catch (Throwable ignored) {}
        return -1;
    }

    private static PermissionSet playerPermissions(ServerPlayer player) {
        try {
            if (player != null) return player.permissions();
        } catch (Throwable ignored) {}
        return null;
    }

    private static PermissionSet sourcePermissions(CommandSourceStack src) {
        try {
            if (src != null) return src.permissions();
        } catch (Throwable ignored) {}
        return null;
    }
}
