package com.teufel.statusmod.util;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.ModConfig;
import com.teufel.statusmod.storage.PlayerSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

public final class StatusTeamUtil {
    private StatusTeamUtil() {}

    private static Component wrapWithWhiteBrackets(String text, int bracketStyle, String colorKey) {
        Component colored = StatusColorUtil.applyColor(text, colorKey);
        if (bracketStyle == 0) return colored;
        String prefix = bracketStyle == 1 ? "[" : "<";
        String suffix = bracketStyle == 1 ? "]" : ">";
        MutableComponent result = Component.literal(prefix).withStyle(ChatFormatting.WHITE);
        result.append(colored);
        result.append(Component.literal(suffix).withStyle(ChatFormatting.WHITE));
        return result;
    }

    public static void applyStatus(ServerScoreboard scoreboard, ServerPlayer player, PlayerSettings settings, String status, String colorKey, boolean isAdmin) {
        if (scoreboard == null || player == null || settings == null) return;
        ModConfig cfg = StatusMod.getConfig();
        if (cfg != null && !cfg.isEnabled("status") && !cfg.isEnabled("badge")) return;
        String uuid = player.getUUID().toString();
        String teamName = "status_" + uuid.substring(0, 8);
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) team = scoreboard.addPlayerTeam(teamName);
        boolean statusEnabled = cfg == null || cfg.isEnabled("status");
        Component colored;
        if (statusEnabled) {
            String inner = status == null ? "" : status;
            int bracketStyle = settings != null ? settings.brackets : 0;
            colored = wrapWithWhiteBrackets(inner, bracketStyle, colorKey);
        } else {
            colored = Component.empty();
        }
        Component finalComponent = colored;
        if (cfg != null && cfg.enableStaffBadge && cfg.isEnabled("badge")) {
            ModConfig.StaffBadge override = cfg.staffBadges == null ? null : cfg.staffBadges.get(uuid);
            if (override != null) {
                String badgeText = (override.text == null || override.text.isEmpty()) ? "STAFF" : override.text;
                String badgeColor = (override.color == null || override.color.isEmpty()) ? "red" : override.color;
                Component badge = wrapWithWhiteBrackets(badgeText, override.brackets, badgeColor);
                finalComponent = colored.copy().append(Component.literal(" ")).append(badge);
            } else if (isAdmin) {
                String badgeText = cfg.staffBadgeText == null ? "STAFF" : cfg.staffBadgeText;
                String badgeColor = cfg.staffBadgeColor == null ? "red" : cfg.staffBadgeColor;
                Component badge = wrapWithWhiteBrackets(badgeText, cfg.staffBadgeBrackets, badgeColor);
                finalComponent = colored.copy().append(Component.literal(" ")).append(badge);
            }
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
