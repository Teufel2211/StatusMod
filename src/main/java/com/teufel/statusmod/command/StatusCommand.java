package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.storage.ModConfig;
import com.teufel.statusmod.util.ColorMapper;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.teufel.statusmod.util.PermissionUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;


public class StatusCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // always register /status root; some children may be added conditionally
        LiteralArgumentBuilder<CommandSourceStack> statusTree = Commands.literal("status")
                // self-application commands
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            clearStatus(src);
                            return 1;
                        })
                )
                .then(Commands.argument("status", StringArgumentType.greedyString())
                        .then(Commands.argument("color", StringArgumentType.word()).suggests(com.teufel.statusmod.command.CommandSuggestions.COLOR_SUGGESTIONS)
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
                            // use default color from config when none supplied
                            setStatus(src, status, StatusMod.config.defaultColor);
                            return 1;
                        })
                );

        if (StatusMod.config.enableAdminOverrides) {
            statusTree = statusTree.then(Commands.literal("admin")
                        .then(Commands.literal("clear")
                            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> {
                                CommandSourceStack src = ctx.getSource();
                                if (!PermissionUtil.hasAdminPermission(src)) {
                                    src.sendFailure(Component.literal("Du hast nicht genügend Rechte, um andere Spieler zu verwalten."));
                                    return 0;
                                }
                                ServerPlayer player = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                adminClearStatus(src, player.getScoreboardName());
                                return 1;
                                })
                            )
                        )
                        .then(Commands.literal("set")
                            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .then(Commands.argument("status", StringArgumentType.greedyString())
                                    .then(Commands.argument("color", StringArgumentType.word()).suggests(com.teufel.statusmod.command.CommandSuggestions.COLOR_SUGGESTIONS)
                                        .executes(ctx -> {
                                        CommandSourceStack src = ctx.getSource();
                                        if (!PermissionUtil.hasAdminPermission(src)) {
                                            src.sendFailure(Component.literal("Du hast nicht genügend Rechte, um andere Spieler zu verwalten."));
                                            return 0;
                                        }
                                        ServerPlayer player = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                        String status = StringArgumentType.getString(ctx, "status");
                                        String color = StringArgumentType.getString(ctx, "color");
                                        adminSetStatus(src, player.getScoreboardName(), status, color);
                                        return 1;
                                        })
                                    )
                                    .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    if (!PermissionUtil.hasAdminPermission(src)) {
                                        src.sendFailure(Component.literal("Du hast nicht genügend Rechte, um andere Spieler zu verwalten."));
                                        return 0;
                                    }
                                    ServerPlayer player = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                    String status = StringArgumentType.getString(ctx, "status");
                                    adminSetStatus(src, player.getScoreboardName(), status, "reset");
                                    return 1;
                                    })
                                )
                            )
                        )
            );
        }

        // admin helper to list registered dispatcher root children for debugging
        statusTree = statusTree.then(Commands.literal("commands")
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            if (!PermissionUtil.hasAdminPermission(src)) {
                                src.sendFailure(Component.literal("Du hast nicht genügend Rechte, um diese Aktion auszuführen."));
                                return 0;
                            }
                            try {
                                com.mojang.brigadier.CommandDispatcher<CommandSourceStack> disp = src.getServer().getCommands().getDispatcher();
                                String[] names = new String[]{"status","block","unblock","color","settings","modinfo"};
                                for (String n : names) {
                                    boolean present = disp.getRoot().getChild(n) != null;
                                    src.sendSuccess(() -> Component.literal("command '" + n + "' -> " + (present ? "registered" : "missing")), false);
                                }
                            } catch (Exception e) {
                                src.sendFailure(Component.literal("Fehler beim Abfragen der Befehls-Dispatcher."));
                                e.printStackTrace();
                            }
                            return 1;
                        })
                )
        );

        // allow reloading the global config independently of "admin" block
        statusTree = statusTree.then(Commands.literal("config")
            .then(Commands.literal("reload")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    if (!PermissionUtil.hasAdminPermission(src)) {
                        src.sendFailure(Component.literal("Du hast nicht genügend Rechte, um diese Aktion auszuführen."));
                        return 0;
                    }
                    StatusMod.config = com.teufel.statusmod.storage.ModConfig.load();
                    src.sendSuccess(
                        () -> Component.literal("StatusMod configuration reloaded."),
                        false);
                    return 1;
                })
            )
            .then(Commands.literal("show")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    if (!PermissionUtil.hasAdminPermission(src)) {
                        src.sendFailure(Component.literal("Du hast nicht genügend Rechte, um diese Aktion auszuführen."));
                        return 0;
                    }
                    ModConfig c = StatusMod.config;
                    if (c == null) {
                    src.sendFailure(Component.literal("Keine Konfiguration geladen."));
                    return 0;
                    }
                    src.sendSuccess(() -> Component.literal("StatusMod configuration:"), false);
                    src.sendSuccess(() -> Component.literal(" adminOpLevel = " + c.adminOpLevel), false);
                    src.sendSuccess(() -> Component.literal(" statusPermissionNode = " + c.statusPermissionNode), false);
                    src.sendSuccess(() -> Component.literal(" adminPermissionNode = " + c.adminPermissionNode), false);
                    src.sendSuccess(() -> Component.literal(" enableAdminOverrides = " + c.enableAdminOverrides), false);
                    src.sendSuccess(() -> Component.literal(" defaultColor = " + c.defaultColor), false);
                    return 1;
                })
            )
        );

        dispatcher.register(statusTree);
    }

    private static void setStatus(CommandSourceStack src, String status, String colorKey) {
        try {
            ServerPlayer player = src.getPlayer();
            String uuid = player.getUUID().toString();

            // Check if player is blocked from using status mod
            if (StatusMod.blockedPlayers.isBlocked(uuid)) {
                src.sendFailure(Component.literal("Du wurdest vom Status-Mod blockiert."));
                return;
            }

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
            Component colored = applyColor(base, finalColor);

            if (settings.beforeName) {
                // prefix should have trailing space to separate from name
                team.setPlayerPrefix(colored.copy().append(Component.literal(" ")));
                team.setPlayerSuffix(Component.empty());
            } else {
                // suffix should have leading space to separate from name
                team.setPlayerPrefix(Component.empty());
                team.setPlayerSuffix(Component.literal(" ").append(colored));
            }

            // add player to team (applies prefix/suffix) carefully, since
            // removing from a different team throws an exception
            String playerName = player.getScoreboardName();
            PlayerTeam existing = scoreboard.getPlayerTeam(playerName);
            if (existing != null && existing != team) {
                scoreboard.removePlayerFromTeam(playerName, existing);
            }
            if (existing != team) {
                scoreboard.addPlayerToTeam(playerName, team);
            }

            src.sendSuccess(() -> Component.literal("Status gesetzt: " + finalStatus + " (" + finalColor + ")"), false);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Setzen des Status.")); } catch(Exception ignore){}
            e.printStackTrace();
        }
    }

    private static void clearStatus(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayer();
            String uuid = player.getUUID().toString();

            // Check if player is blocked from using status mod
            if (StatusMod.blockedPlayers.isBlocked(uuid)) {
                src.sendFailure(Component.literal("Du wurdest vom Status-Mod blockiert."));
                return;
            }

            PlayerSettings settings = StatusMod.storage.forPlayer(uuid);
            settings.status = "";
            settings.color = "reset";
            StatusMod.storage.put(uuid, settings);

            MinecraftServer server = src.getServer();
            net.minecraft.server.ServerScoreboard scoreboard = server.getScoreboard();
            String teamName = "status_" + uuid.substring(0, 8);
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            String playerName = player.getScoreboardName();
            // Instead of removing the player/team (which can change spacing in the UI),
            // keep the team and set an empty-but-spacing prefix/suffix so spacing remains stable.
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamName);
            }
            // ensure a single separating space remains where the status would be
            if (settings.beforeName) {
                team.setPlayerPrefix(net.minecraft.network.chat.Component.literal(" "));
                team.setPlayerSuffix(net.minecraft.network.chat.Component.empty());
            } else {
                team.setPlayerPrefix(net.minecraft.network.chat.Component.empty());
                team.setPlayerSuffix(net.minecraft.network.chat.Component.literal(" "));
            }
            // (re)add the player to the team so the empty spacing is applied immediately
            PlayerTeam existing2 = scoreboard.getPlayerTeam(playerName);
            if (existing2 != null && existing2 != team) {
                scoreboard.removePlayerFromTeam(playerName, existing2);
            }
            if (existing2 != team) {
                scoreboard.addPlayerToTeam(playerName, team);
            }

            src.sendSuccess(() -> Component.literal("Status gelöscht."), false);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Löschen des Status.")); } catch(Exception ignore){}
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
        ChatFormatting f = ColorMapper.get(colorKey);
        return base.copy().withStyle(s -> s.withColor(f == null ? ChatFormatting.RESET : f));
    }

    /**
     * Admin helpers -----------------------------------------------------------
     */
    private static void adminSetStatus(CommandSourceStack src, String targetName, String status, String colorKey) {
        try {
            ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(targetName);
            if (target == null) {
                src.sendFailure(Component.literal("Spieler '" + targetName + "' ist nicht online."));
                return;
            }

            String uuid = target.getUUID().toString();
            PlayerSettings settings = StatusMod.storage.forPlayer(uuid);
            settings.status = status;
            settings.color = colorKey;
            StatusMod.storage.put(uuid, settings);

            // reuse setStatus-like logic to update scoreboard now
            MinecraftServer server = src.getServer();
            net.minecraft.server.ServerScoreboard scoreboard = server.getScoreboard();
            String teamName = "status_" + uuid.substring(0, 8);
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) team = scoreboard.addPlayerTeam(teamName);

            Component base = Component.literal((settings.brackets ? "[" : "") + status + (settings.brackets ? "]" : ""));
            Component colored = applyColor(base, colorKey);
            if (settings.beforeName) {
                team.setPlayerPrefix(colored.copy().append(Component.literal(" ")));
                team.setPlayerSuffix(Component.empty());
            } else {
                team.setPlayerPrefix(Component.empty());
                team.setPlayerSuffix(Component.literal(" ").append(colored));
            }

            String playerName = target.getScoreboardName();
            PlayerTeam existing = scoreboard.getPlayerTeam(playerName);
            if (existing != null && existing != team) {
                scoreboard.removePlayerFromTeam(playerName, existing);
            }
            if (existing != team) {
                scoreboard.addPlayerToTeam(playerName, team);
            }

            src.sendSuccess(() -> Component.literal("Status von " + targetName + " gesetzt: " + status + " (" + colorKey + ")"), false);
            target.displayClientMessage(Component.literal("Dein Status wurde von einem Administrator gesetzt."), false);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Setzen des Status für '" + targetName + "'.")); } catch (Exception ignore) {}
            e.printStackTrace();
        }
    }

    private static void adminClearStatus(CommandSourceStack src, String targetName) {
        try {
            ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(targetName);
            if (target == null) {
                src.sendFailure(Component.literal("Spieler '" + targetName + "' ist nicht online."));
                return;
            }

            String uuid = target.getUUID().toString();
            PlayerSettings settings = StatusMod.storage.forPlayer(uuid);
            settings.status = "";
            settings.color = "reset";
            StatusMod.storage.put(uuid, settings);

            MinecraftServer server = src.getServer();
            net.minecraft.server.ServerScoreboard scoreboard = server.getScoreboard();
            String teamName = "status_" + uuid.substring(0, 8);
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            String playerName = target.getScoreboardName();
            if (team != null) {
                PlayerTeam existing = scoreboard.getPlayerTeam(playerName);
                if (existing != null && existing != team) {
                    scoreboard.removePlayerFromTeam(playerName, existing);
                }
                if (existing != team) {
                    scoreboard.addPlayerToTeam(playerName, team);
                }
                if (settings.beforeName) {
                    team.setPlayerPrefix(Component.literal(" "));
                    team.setPlayerSuffix(Component.empty());
                } else {
                    team.setPlayerPrefix(Component.empty());
                    team.setPlayerSuffix(Component.literal(" "));
                }
            }

            src.sendSuccess(() -> Component.literal("Status von " + targetName + " gelöscht."), false);
            target.displayClientMessage(Component.literal("Dein Status wurde von einem Administrator gelöscht."), false);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Löschen des Status für '" + targetName + "'.")); } catch (Exception ignore) {}
            e.printStackTrace();
        }
    }
}
