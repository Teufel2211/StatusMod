package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.ColorMapper;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;



public class StatusCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("status")
                .then(Commands.argument("status", StringArgumentType.greedyString())
                        .then(Commands.argument("color", StringArgumentType.word())
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    String status = StringArgumentType.getString(ctx, "status");
                                    String color = StringArgumentType.getString(ctx, "color");
                                    setStatus(src, status, color);
                                    return 1;
                                })
                        )
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            String status = StringArgumentType.getString(ctx, "status");
                            setStatus(src, status, "reset");
                            return 1;
                        })
                )
        );
    }

    private static void setStatus(CommandSourceStack src, String status, String colorKey) {
        try {
            ServerPlayer player = src.getPlayer();
            String uuid = player.nameAndId().id().toString();

            PlayerSettings settings = StatusMod.storage.forPlayer(uuid);
            // Determine parsing based on player's configured status word count
            int n = settings.statusWords <= 0 ? 1 : settings.statusWords;

            // If caller didn't supply an explicit color argument (uses default "reset"),
            // try to parse the color as the token after the configured number of words.
            if (colorKey == null || "reset".equalsIgnoreCase(colorKey)) {
                String[] tokens = status == null ? new String[0] : status.trim().split("\\s+");
                if (tokens.length < n) {
                    src.sendFailure(Component.literal("Bitte mindestens " + n + " Wörter für den Status angeben."));
                    return;
                }
                if (tokens.length > n) {
                    // next token after the n status words is treated as the color key
                    colorKey = tokens[n];
                } else {
                    colorKey = "reset";
                }
                // rebuild status from the first n tokens to ensure consistent spacing
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(tokens[i]);
                }
                status = sb.toString();
            } else {
                // explicit color argument provided: ensure there are at least n words
                String[] tokens = status == null ? new String[0] : status.trim().split("\\s+");
                if (tokens.length < n) {
                    src.sendFailure(Component.literal("Bitte mindestens " + n + " Wörter für den Status angeben."));
                    return;
                }
                // keep the full status as-is when color is provided separately
            }

            settings.status = status;
            settings.color = colorKey;
            StatusMod.storage.put(uuid, settings);

            // make final copies for lambda usage later
            final String finalStatus = status;
            final String finalColor = colorKey;

            MinecraftServer server = src.getServer();
            net.minecraft.server.ServerScoreboard scoreboard = server.getScoreboard();
            String teamName = "status_" + uuid.substring(0, 8);
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) team = scoreboard.addPlayerTeam(teamName);

            ChatFormatting f = ColorMapper.get(finalColor);
            Component base = Component.literal((settings.brackets ? "[" : "") + finalStatus + (settings.brackets ? "]" : ""));
            Component colored = base.copy().withStyle(s -> s.withColor(f == null ? ChatFormatting.RESET : f));

            if (settings.beforeName) {
                // prefix should have trailing space to separate from name
                team.setPlayerPrefix(colored.copy().append(Component.literal(" ")));
                team.setPlayerSuffix(Component.empty());
            } else {
                // suffix should have leading space to separate from name
                team.setPlayerPrefix(Component.empty());
                team.setPlayerSuffix(Component.literal(" ").append(colored));
            }

            // add player to team (applies prefix/suffix)
            String playerName = player.getScoreboardName();
            scoreboard.removePlayerFromTeam(playerName, team); // safe
            scoreboard.addPlayerToTeam(playerName, team);

            src.sendSuccess(() -> Component.literal("Status gesetzt: " + finalStatus + " (" + finalColor + ")"), false);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Setzen des Status.")); } catch(Exception ignore){}
            e.printStackTrace();
        }
    }
}
package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.ColorMapper;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;


public class StatusCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("status")
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            clearStatus(src);
                            return 1;
                        })
                )
                .then(Commands.argument("status", StringArgumentType.greedyString())
                        .then(Commands.argument("color", StringArgumentType.word())
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    String status = StringArgumentType.getString(ctx, "status");
                                    String color = StringArgumentType.getString(ctx, "color");
                                    setStatus(src, status, color);
                                    return 1;
                                })
                        )
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            String status = StringArgumentType.getString(ctx, "status");
                            setStatus(src, status, "reset");
                            return 1;
                        })
                )
        );
    }

    private static void setStatus(CommandSourceStack src, String status, String colorKey) {
        try {
            ServerPlayer player = src.getPlayer();
            String uuid = player.nameAndId().id().toString();

            PlayerSettings settings = StatusMod.storage.forPlayer(uuid);
            // Determine parsing based on player's configured status word count
            int n = settings.statusWords <= 0 ? 1 : settings.statusWords;

            // If caller didn't supply an explicit color argument (uses default "reset"),
            // try to parse the color as the token after the configured number of words.
            if (colorKey == null || "reset".equalsIgnoreCase(colorKey)) {
                String[] tokens = status == null ? new String[0] : status.trim().split("\\s+");
                if (tokens.length < n) {
                    src.sendFailure(Component.literal("Bitte mindestens " + n + " Wörter für den Status angeben."));
                    return;
                }
                if (tokens.length > n) {
                    // next token after the n status words is treated as the color key
                    colorKey = tokens[n];
                } else {
                    colorKey = "reset";
                }
                // rebuild status from the first n tokens to ensure consistent spacing
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(tokens[i]);
                }
                status = sb.toString();
            } else {
                // explicit color argument provided: ensure there are at least n words
                String[] tokens = status == null ? new String[0] : status.trim().split("\\s+");
                if (tokens.length < n) {
                    src.sendFailure(Component.literal("Bitte mindestens " + n + " Wörter für den Status angeben."));
                    return;
                }
                // keep the full status as-is when color is provided separately
            }

            settings.status = status;
            settings.color = colorKey;
            StatusMod.storage.put(uuid, settings);

            // make final copies for lambda usage later
            final String finalStatus = status;
            final String finalColor = colorKey;

            MinecraftServer server = src.getServer();
            net.minecraft.server.ServerScoreboard scoreboard = server.getScoreboard();
            String teamName = "status_" + uuid.substring(0, 8);
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) team = scoreboard.addPlayerTeam(teamName);

            ChatFormatting f = ColorMapper.get(finalColor);
            Component base = Component.literal((settings.brackets ? "[" : "") + finalStatus + (settings.brackets ? "]" : ""));
            Component colored = base.copy().withStyle(s -> s.withColor(f == null ? ChatFormatting.RESET : f));

            if (settings.beforeName) {
                // prefix should have trailing space to separate from name
                team.setPlayerPrefix(colored.copy().append(Component.literal(" ")));
                team.setPlayerSuffix(Component.empty());
            } else {
                // suffix should have leading space to separate from name
                team.setPlayerPrefix(Component.empty());
                team.setPlayerSuffix(Component.literal(" ").append(colored));
            }

            // add player to team (applies prefix/suffix)
            String playerName = player.getScoreboardName();
            scoreboard.removePlayerFromTeam(playerName, team); // safe
            scoreboard.addPlayerToTeam(playerName, team);

            src.sendSuccess(() -> Component.literal("Status gesetzt: " + finalStatus + " (" + finalColor + ")"), false);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Setzen des Status.")); } catch(Exception ignore){}
            e.printStackTrace();
        }
    }

    private static void clearStatus(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayer();
            String uuid = player.nameAndId().id().toString();

            PlayerSettings settings = StatusMod.storage.forPlayer(uuid);
            settings.status = "";
            settings.color = "reset";
            StatusMod.storage.put(uuid, settings);

            MinecraftServer server = src.getServer();
            net.minecraft.server.ServerScoreboard scoreboard = server.getScoreboard();
            String teamName = "status_" + uuid.substring(0, 8);
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team != null) {
                String playerName = player.getScoreboardName();
                scoreboard.removePlayerFromTeam(playerName, team);
                // optionally remove team entirely if empty
                if (team.getPlayers().isEmpty()) {
                    scoreboard.removePlayerTeam(team);
                }
            }

            src.sendSuccess(() -> Component.literal("Status gelöscht."), false);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Löschen des Status.")); } catch(Exception ignore){}
            e.printStackTrace();
        }
    }
}
