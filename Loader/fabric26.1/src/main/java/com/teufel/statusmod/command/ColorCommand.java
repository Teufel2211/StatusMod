package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.CommandUtil;
import com.teufel.statusmod.util.ColorMapper;
import com.teufel.statusmod.util.PermissionUtil;
import com.teufel.statusmod.util.StatusTeamUtil;
import com.teufel.statusmod.util.StatusTextUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ColorCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("color").then(Commands.argument("color", StringArgumentType.greedyString()).suggests(CommandSuggestions.COLOR_SUGGESTIONS).executes(ctx -> {
            setColor(ctx.getSource(), StringArgumentType.getString(ctx, "color"));
            return 1;
        })));
    }

    private static void setColor(CommandSourceStack src, String colorInput) {
        try {
            colorInput = colorInput == null ? "" : colorInput.trim();
            ServerPlayer player = src.getPlayer();
            if (player == null) {
                src.sendFailure(Component.literal("Nur Spieler können diese Farbe benutzen."));
                return;
            }
            String uuid = player.getUUID().toString();
            if (StatusMod.getBlockedPlayers().isBlocked(uuid)) {
                src.sendFailure(Component.literal("Du wurdest vom Status-Mod blockiert."));
                return;
            }
            PlayerSettings settings = StatusMod.getStorage().forPlayer(uuid);
            if (colorInput.equalsIgnoreCase("reset")) {
                settings.color = "reset";
                StatusMod.getStorage().put(uuid, settings);
                applyCurrentStatusToTeam(src, player, settings);
                CommandUtil.sendSuccess(src, Component.literal("Deine Status-Farbe wurde zurückgesetzt."), true);
                return;
            }
            if (!ColorMapper.isValidColorInput(colorInput)) {
                src.sendFailure(Component.literal("Ungültige Farbe."));
                return;
            }
            settings.color = colorInput;
            StatusMod.getStorage().put(uuid, settings);
            applyCurrentStatusToTeam(src, player, settings);
            CommandUtil.sendSuccess(src, Component.literal("Deine Status-Farbe wurde auf " + colorInput + " gesetzt."), true);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Setzen der Farbe.")); } catch (Exception ignored) {}
            e.printStackTrace();
        }
    }

    private static void applyCurrentStatusToTeam(CommandSourceStack src, ServerPlayer player, PlayerSettings settings) {
        try {
            var scoreboard = src.getServer().getScoreboard();
            String status = StatusTextUtil.resolveStatusForPlayer(settings, player);
            String color = StatusTextUtil.resolveColorForPlayer(settings, player);
            StatusTeamUtil.applyStatus(scoreboard, player, settings, status, color, PermissionUtil.hasAdminPermission(player));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
