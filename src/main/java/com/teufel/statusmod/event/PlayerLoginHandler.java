package com.teufel.statusmod.event;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.ColorMapper;
import com.teufel.statusmod.util.StatusColorUtil;
import com.teufel.statusmod.util.StatusTextUtil;
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
                if (settings.status != null && !settings.status.isEmpty()) {
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
                    if (settings.status == null || settings.status.isEmpty()) {
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
            String teamName = "status_" + uuid.substring(0, 8);
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamName);
            }
            
            // Reconstruct the colored status text using the applyColor helper
            Component base = Component.literal(StatusTextUtil.renderStatusText(settings));
            Component colored = StatusColorUtil.applyColor(base, settings.color);
            
            // Apply prefix/suffix based on position preference
            String playerName = player.getScoreboardName();
            if (settings.beforeName) {
                team.setPlayerPrefix(colored.copy().append(Component.literal(" ")));
                team.setPlayerSuffix(Component.empty());
            } else {
                team.setPlayerPrefix(Component.empty());
                team.setPlayerSuffix(Component.literal(" ").append(colored));
            }
            
            // Ensure player is moved into the correct team without triggering
            // IllegalStateException if they happen to already belong to a different
            // team (which was the cause of the crash reported by the user).
            PlayerTeam existing = scoreboard.getPlayerTeam(playerName);
            if (existing != null && existing != team) {
                scoreboard.removePlayerFromTeam(playerName, existing);
            }
            if (existing != team) {
                scoreboard.addPlayerToTeam(playerName, team);
            }
            
        } catch (Exception e) {
            System.err.println("[StatusMod] Error reapplying status for player " + uuid);
            e.printStackTrace();
        }
    }

}
