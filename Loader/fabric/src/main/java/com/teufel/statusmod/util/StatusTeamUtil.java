package com.teufel.statusmod.util;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.ModConfig;
import com.teufel.statusmod.storage.PlayerSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

public final class StatusTeamUtil {
    private StatusTeamUtil() {}

    public static void applyStatus(ServerScoreboard scoreboard, ServerPlayer player, PlayerSettings settings, String status, String colorKey, boolean isAdmin) {
        if (scoreboard == null || player == null || settings == null) return;
        String uuid = player.getUUID().toString();
        String teamName = "status_" + uuid.substring(0, 8);
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) team = scoreboard.addPlayerTeam(teamName);

        Component base = Component.literal(StatusTextUtil.renderStatusText(status, settings, player));
        Component colored = StatusColorUtil.applyColor(base, colorKey);

        Component finalComponent = colored;
        ModConfig cfg = StatusMod.config;
        if (isAdmin && cfg != null && cfg.enableStaffBadge) {
            String badgeText = cfg.staffBadgeText == null ? "[STAFF]" : cfg.staffBadgeText;
            String badgeColor = cfg.staffBadgeColor == null ? "red" : cfg.staffBadgeColor;
            Component badge = StatusColorUtil.applyColor(Component.literal(badgeText), badgeColor);
            finalComponent = colored.copy().append(Component.literal(" ")).append(badge);
        }

        if (settings.beforeName) {
            team.setPlayerPrefix(finalComponent.copy().append(Component.literal(" ")));
            team.setPlayerSuffix(Component.empty());
        } else {
            team.setPlayerPrefix(Component.empty());
            team.setPlayerSuffix(Component.literal(" ").append(finalComponent));
        }

        String playerName = player.getScoreboardName();
        PlayerTeam existing = scoreboard.getPlayerTeam(playerName);
        if (existing != null && existing != team) {
            scoreboard.removePlayerFromTeam(playerName, existing);
        }
        if (existing != team) {
            scoreboard.addPlayerToTeam(playerName, team);
        }
    }
}
