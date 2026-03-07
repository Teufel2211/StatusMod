package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.ColorMapper;
import com.teufel.statusmod.util.StatusTextUtil;
import com.teufel.statusmod.util.StatusColorUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Command to set player color using hex codes.
 * Usage:
 *   /color <hex> - set player's status color with hex code (#RRGGBB or #RGB)
 *   /color reset - reset to default color
 */
public class ColorCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("color")
            .then(Commands.argument("color", StringArgumentType.greedyString()).suggests(com.teufel.statusmod.command.CommandSuggestions.COLOR_SUGGESTIONS)
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    String color = StringArgumentType.getString(ctx, "color");
                    setColor(src, color);
                    return 1;
                })
            )
        );
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

            // Check if player is blocked
            if (StatusMod.blockedPlayers.isBlocked(uuid)) {
                src.sendFailure(Component.literal("Du wurdest vom Status-Mod blockiert."));
                return;
            }

            PlayerSettings settings = StatusMod.storage.forPlayer(uuid);

            // Handle reset
            if (colorInput.equalsIgnoreCase("reset")) {
                settings.color = "reset";
                StatusMod.storage.put(uuid, settings);
                applyCurrentStatusToTeam(src, player, settings);
                src.sendSuccess(
                    () -> Component.literal("Deine Status-Farbe wurde zurückgesetzt."),
                    true
                );
                return;
            }

            // Validate color format (named, hex, rgb)
            if (!ColorMapper.isValidColorInput(colorInput)) {
                src.sendFailure(Component.literal(
                    "Ungültige Farbe. Erlaubt: Name, Hex, rgb(...) oder Palette rgb(...)|rgb(...)."
                ));
                return;
            }

            // Save the selected color token
            settings.color = colorInput;
            StatusMod.storage.put(uuid, settings);
            applyCurrentStatusToTeam(src, player, settings);
            final String finalColorInput = colorInput;

            src.sendSuccess(
                () -> Component.literal("Deine Status-Farbe wurde auf " + finalColorInput + " gesetzt."),
                true
            );

        } catch (Exception e) {
            try {
                src.sendFailure(Component.literal("Fehler beim Setzen der Farbe."));
            } catch (Exception ignored) {}
            e.printStackTrace();
        }
    }

    private static void applyCurrentStatusToTeam(CommandSourceStack src, ServerPlayer player, PlayerSettings settings) {
        try {
            net.minecraft.server.ServerScoreboard scoreboard = src.getServer().getScoreboard();
            String teamName = "status_" + player.getUUID().toString().substring(0, 8);
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamName);
            }

            Component base = Component.literal(StatusTextUtil.renderStatusText(settings));
            Component colored = StatusColorUtil.applyColor(base, settings.color);

            if (settings.beforeName) {
                team.setPlayerPrefix(colored.copy().append(Component.literal(" ")));
                team.setPlayerSuffix(Component.empty());
            } else {
                team.setPlayerPrefix(Component.empty());
                team.setPlayerSuffix(Component.literal(" ").append(colored));
            }

            String playerName = player.getScoreboardName();
            PlayerTeam existing = scoreboard.getPlayerTeam(playerName);
            if (existing != null && existing != team) {
                scoreboard.removePlayerFromTeam(playerName, existing);
            }
            if (existing != team) {
                scoreboard.addPlayerToTeam(playerName, team);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
