package com.teufel.statusmod.event;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.ColorMapper;
import com.teufel.statusmod.util.StatusColorUtil;
import com.teufel.statusmod.util.StatusTextUtil;
import com.teufel.statusmod.util.StatusTeamUtil;
import com.teufel.statusmod.util.PermissionUtil;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Restores player statuses from persistent storage when they rejoin the server.
 * This ensures that statuses don't disappear after a player logs out and back in.
 */
public class PlayerLoginHandler {
    private static final int DEFAULT_REAPPLY_INTERVAL_TICKS = 100;
    private static final int MIN_REAPPLY_INTERVAL_TICKS = 20;
    private static int tickCounter = 0;

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            String uuid = player.getUUID().toString();
            
            try {
                PlayerSettings settings = StatusMod.storage.forPlayer(uuid);
                
                // Only reapply if status is not empty
                String status = StatusTextUtil.resolveStatusForPlayer(settings, player);
                if (status != null && !status.isEmpty()) {
                    reapplyStatus(server, player, uuid, settings);
                }
            } catch (Exception e) {
                System.err.println("[StatusMod] Error restoring status for player " + uuid);
                e.printStackTrace();
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server == null || StatusMod.storage == null) {
                return;
            }
            tickCounter++;
            int configuredInterval = DEFAULT_REAPPLY_INTERVAL_TICKS;
            try {
                if (StatusMod.config != null) {
                    configuredInterval = Math.max(MIN_REAPPLY_INTERVAL_TICKS, StatusMod.config.statusReapplyTicks);
                }
            } catch (Exception ignored) {
                configuredInterval = DEFAULT_REAPPLY_INTERVAL_TICKS;
            }

            // Animated colors (rainbow / palette) need frequent refresh to actually animate.
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try {
                    PlayerSettings ps = StatusMod.storage.forPlayer(player.getUUID().toString());
                    if (ps != null && ColorMapper.isAnimatedColorInput(ps.color)) {
                        configuredInterval = MIN_REAPPLY_INTERVAL_TICKS;
                        break;
                    }
                } catch (Exception ignored) {}
            }

            if (tickCounter < configuredInterval) {
                return;
            }
            tickCounter = 0;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                String uuid = player.getUUID().toString();
                try {
                    PlayerSettings settings = StatusMod.storage.forPlayer(uuid);
                    if (settings.statusExpiresAtMs > 0L && System.currentTimeMillis() >= settings.statusExpiresAtMs) {
                        settings.status = "";
                        settings.color = "reset";
                        settings.statusExpiresAtMs = 0L;
                        StatusMod.storage.put(uuid, settings);
                    }
                    String status = StatusTextUtil.resolveStatusForPlayer(settings, player);
                    if (status == null || status.isEmpty()) {
                        continue;
                    }
                    reapplyStatus(server, player, uuid, settings);
                } catch (Exception e) {
                    System.err.println("[StatusMod] Error during periodic status reapply for " + uuid);
                    e.printStackTrace();
                }
            }
        });
    }
    
    private static void reapplyStatus(net.minecraft.server.MinecraftServer server, ServerPlayer player, 
                                      String uuid, PlayerSettings settings) {
        try {
            net.minecraft.server.ServerScoreboard scoreboard = server.getScoreboard();
            String status = StatusTextUtil.resolveStatusForPlayer(settings, player);
            String color = StatusTextUtil.resolveColorForPlayer(settings, player);
            StatusTeamUtil.applyStatus(scoreboard, player, settings, status, color, PermissionUtil.hasAdminPermission(player));
            
        } catch (Exception e) {
            System.err.println("[StatusMod] Error reapplying status for player " + uuid);
            e.printStackTrace();
        }
    }

}
