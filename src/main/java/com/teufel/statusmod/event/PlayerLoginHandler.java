package com.teufel.statusmod.event;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.ColorMapper;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Restores player statuses from persistent storage when they rejoin the server.
 * This ensures that statuses don't disappear after a player logs out and back in.
 */
public class PlayerLoginHandler {
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
            Component base = Component.literal(
                (settings.brackets ? "[" : "") + settings.status + (settings.brackets ? "]" : "")
            );
            Component colored = applyColor(base, settings.color);
            
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
            
            System.out.println("[StatusMod] Restored status for player " + player.getName().getString() + 
                             ": " + settings.status);
        } catch (Exception e) {
            System.err.println("[StatusMod] Error reapplying status for player " + uuid);
            e.printStackTrace();
        }
    }

    /**
     * Apply color to a component, supporting both hex codes (#RRGGBB) and named colors.
     */
    private static Component applyColor(Component base, String colorKey) {
        // Try hex color first
        TextColor hexColor = ColorMapper.parseHexColor(colorKey);
        if (hexColor != null) {
            return base.copy().withStyle(s -> s.withColor(hexColor));
        }
        
        // Fall back to named colors
        ChatFormatting color = ColorMapper.get(colorKey);
        return base.copy().withStyle(s -> s.withColor(color == null ? ChatFormatting.RESET : color));
    }
}
