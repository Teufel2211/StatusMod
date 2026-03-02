package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.ColorMapper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Command to set player color using hex codes.
 * Usage:
 *   /color <hex> - set player's status color with hex code (#RRGGBB or #RGB)
 *   /color reset - reset to default color
 */
public class ColorCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("color")
            .then(Commands.argument("hex", StringArgumentType.word()).suggests(com.teufel.statusmod.command.CommandSuggestions.COLOR_SUGGESTIONS)
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    String hex = StringArgumentType.getString(ctx, "hex");
                    setColor(src, hex);
                    return 1;
                })
            )
        );
    }

    private static void setColor(CommandSourceStack src, String hex) {
        try {
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
            if (hex.equalsIgnoreCase("reset")) {
                settings.color = "reset";
                StatusMod.storage.put(uuid, settings);
                src.sendSuccess(
                    () -> Component.literal("Deine Status-Farbe wurde zurückgesetzt."),
                    true
                );
                return;
            }

            // Validate hex format
            if (!ColorMapper.isValidHexColor(hex)) {
                src.sendFailure(Component.literal(
                    "Ungültiges Hexadezimalformat. Bitte verwende #RRGGBB oder #RGB (z.B. #FF0000 für rot)."
                ));
                return;
            }

            // Save the hex color
            settings.color = hex;
            StatusMod.storage.put(uuid, settings);

            src.sendSuccess(
                () -> Component.literal("Deine Status-Farbe wurde auf " + hex + " gesetzt."),
                true
            );

        } catch (Exception e) {
            try {
                src.sendFailure(Component.literal("Fehler beim Setzen der Farbe."));
            } catch (Exception ignored) {}
            e.printStackTrace();
        }
    }
}
