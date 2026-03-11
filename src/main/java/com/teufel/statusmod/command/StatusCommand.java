package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.storage.ModConfig;
import com.teufel.statusmod.util.ColorMapper;
import com.teufel.statusmod.util.FontMapper;
import com.teufel.statusmod.util.StatusColorUtil;
import com.teufel.statusmod.util.StatusTeamUtil;
import com.teufel.statusmod.util.StatusTextUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.teufel.statusmod.util.PermissionUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class StatusCommand {
    private static final Map<String, Preset> PRESETS = new HashMap<>();
    static {
        PRESETS.put("afk", new Preset("AFK", "yellow", "normal"));
        PRESETS.put("busy", new Preset("Busy", "red", "normal"));
        PRESETS.put("stream", new Preset("Stream", "light_purple", "smallcaps"));
        PRESETS.put("shop", new Preset("Shop", "gold", "normal"));
    }
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
                .then(Commands.argument("status", StringArgumentType.greedyString()).suggests(com.teufel.statusmod.command.CommandSuggestions.STATUS_SUGGESTIONS)
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
                            setStatus(src, status, null);
                            return 1;
                        })
                );

        statusTree = statusTree
                .then(Commands.literal("preset")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(com.teufel.statusmod.command.CommandSuggestions.PRESET_SUGGESTIONS)
                                .executes(ctx -> {
                                    applyPreset(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("random")
                        .then(Commands.argument("status", StringArgumentType.greedyString()).suggests(com.teufel.statusmod.command.CommandSuggestions.STATUS_SUGGESTIONS)
                                .executes(ctx -> {
                                    setRandomStatus(ctx.getSource(), StringArgumentType.getString(ctx, "status"));
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("timed")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                .then(Commands.argument("status", StringArgumentType.greedyString()).suggests(com.teufel.statusmod.command.CommandSuggestions.STATUS_SUGGESTIONS)
                                        .executes(ctx -> {
                                            setTimedStatus(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "minutes"),
                                                    StringArgumentType.getString(ctx, "status"));
                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("history")
                        .executes(ctx -> {
                            showHistory(ctx.getSource());
                            return 1;
                        })
                )
                .then(Commands.literal("world")
                        .then(Commands.literal("clear")
                                .executes(ctx -> {
                                    clearWorldStatus(ctx.getSource());
                                    return 1;
                                })
                        )
                        .then(Commands.argument("status", StringArgumentType.greedyString()).suggests(com.teufel.statusmod.command.CommandSuggestions.STATUS_SUGGESTIONS)
                                .then(Commands.argument("color", StringArgumentType.word()).suggests(com.teufel.statusmod.command.CommandSuggestions.COLOR_SUGGESTIONS)
                                        .executes(ctx -> {
                                            setWorldStatus(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "status"),
                                                    StringArgumentType.getString(ctx, "color"));
                                            return 1;
                                        })
                                )
                                .executes(ctx -> {
                                    setWorldStatus(ctx.getSource(), StringArgumentType.getString(ctx, "status"), null);
                                    return 1;
                                })
                        )
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
                                .then(Commands.argument("status", StringArgumentType.greedyString()).suggests(com.teufel.statusmod.command.CommandSuggestions.STATUS_SUGGESTIONS)
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
                                    adminSetStatus(src, player.getScoreboardName(), status, null);
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
                    src.sendSuccess(() -> Component.literal(" statusReapplyTicks = " + c.statusReapplyTicks), false);
                    src.sendSuccess(() -> Component.literal(" statusCooldownSeconds = " + c.statusCooldownSeconds), false);
                    src.sendSuccess(() -> Component.literal(" statusHistorySize = " + c.statusHistorySize), false);
                    src.sendSuccess(() -> Component.literal(" enableStaffBadge = " + c.enableStaffBadge), false);
                    src.sendSuccess(() -> Component.literal(" staffBadgeText = " + c.staffBadgeText), false);
                    src.sendSuccess(() -> Component.literal(" staffBadgeColor = " + c.staffBadgeColor), false);
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
            if (!checkCooldown(src, settings)) return;

            StatusUpdate update = parseStatusInput(status, colorKey, settings);
            if (!update.ok) {
                src.sendFailure(Component.literal(update.error));
                return;
            }

            applyStatusUpdate(src, player, settings, update, false, null, false);
            src.sendSuccess(() -> Component.literal("Status gesetzt: " + update.status + " (" + update.color + ")"), false);
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
            settings.statusExpiresAtMs = 0L;
            StatusMod.storage.put(uuid, settings);

            MinecraftServer server = src.getServer();
            StatusTeamUtil.applyStatus(server.getScoreboard(), player, settings, "", "reset", PermissionUtil.hasAdminPermission(player));
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
        return StatusColorUtil.applyColor(base, colorKey);
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
            StatusUpdate update = parseStatusInput(status, colorKey, settings);
            if (!update.ok) {
                src.sendFailure(Component.literal(update.error));
                return;
            }

            applyStatusUpdate(src, target, settings, update, false, null, false);
            src.sendSuccess(() -> Component.literal("Status von " + targetName + " gesetzt: " + update.status + " (" + update.color + ")"), false);
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
            settings.statusExpiresAtMs = 0L;
            StatusMod.storage.put(uuid, settings);

            MinecraftServer server = src.getServer();
            StatusTeamUtil.applyStatus(server.getScoreboard(), target, settings, "", "reset", PermissionUtil.hasAdminPermission(target));

            src.sendSuccess(() -> Component.literal("Status von " + targetName + " gelöscht."), false);
            target.displayClientMessage(Component.literal("Dein Status wurde von einem Administrator gelöscht."), false);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Löschen des Status für '" + targetName + "'.")); } catch (Exception ignore) {}
            e.printStackTrace();
        }
    }

    private static void applyPreset(CommandSourceStack src, String name) {
        try {
            if (name == null) name = "";
            Preset preset = PRESETS.get(name.toLowerCase());
            if (preset == null) {
                src.sendFailure(Component.literal("Unbekanntes Preset: " + name));
                return;
            }
            ServerPlayer player = src.getPlayer();
            if (isBlocked(src, player)) return;
            PlayerSettings settings = StatusMod.storage.forPlayer(player.getUUID().toString());
            if (!checkCooldown(src, settings)) return;

            StatusUpdate update = new StatusUpdate(preset.status, preset.color, preset.font, true, null);
            applyStatusUpdate(src, player, settings, update, false, null, false);
            src.sendSuccess(() -> Component.literal("Preset gesetzt: " + preset.status + " (" + preset.color + ")"), false);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Setzen des Presets.")); } catch(Exception ignore){}
            e.printStackTrace();
        }
    }

    private static void setRandomStatus(CommandSourceStack src, String statusInput) {
        try {
            ServerPlayer player = src.getPlayer();
            if (isBlocked(src, player)) return;
            PlayerSettings settings = StatusMod.storage.forPlayer(player.getUUID().toString());
            if (!checkCooldown(src, settings)) return;

            StatusUpdate update = parseStatusInput(statusInput, null, settings);
            if (!update.ok) {
                src.sendFailure(Component.literal(update.error));
                return;
            }

            String randomColor = pickStableRandomColor(player.getUUID().toString());
            update.color = randomColor;
            applyStatusUpdate(src, player, settings, update, false, null, false);
            src.sendSuccess(() -> Component.literal("Status gesetzt (random): " + update.status + " (" + update.color + ")"), false);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Setzen des random Status.")); } catch(Exception ignore){}
            e.printStackTrace();
        }
    }

    private static void setTimedStatus(CommandSourceStack src, int minutes, String statusInput) {
        try {
            ServerPlayer player = src.getPlayer();
            if (isBlocked(src, player)) return;
            PlayerSettings settings = StatusMod.storage.forPlayer(player.getUUID().toString());
            if (!checkCooldown(src, settings)) return;

            StatusUpdate update = parseStatusInput(statusInput, null, settings);
            if (!update.ok) {
                src.sendFailure(Component.literal(update.error));
                return;
            }
            long expiresAt = System.currentTimeMillis() + (minutes * 60L * 1000L);
            applyStatusUpdate(src, player, settings, update, false, expiresAt, false);
            src.sendSuccess(() -> Component.literal("Status gesetzt für " + minutes + " Minuten."), false);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Setzen des Timed-Status.")); } catch(Exception ignore){}
            e.printStackTrace();
        }
    }

    private static void setWorldStatus(CommandSourceStack src, String statusInput, String colorKey) {
        try {
            ServerPlayer player = src.getPlayer();
            if (isBlocked(src, player)) return;
            PlayerSettings settings = StatusMod.storage.forPlayer(player.getUUID().toString());
            if (!checkCooldown(src, settings)) return;

            StatusUpdate update = parseStatusInput(statusInput, colorKey, settings);
            if (!update.ok) {
                src.sendFailure(Component.literal(update.error));
                return;
            }
            applyStatusUpdate(src, player, settings, update, true, null, false);
            src.sendSuccess(() -> Component.literal("World-Status gesetzt."), false);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Setzen des World-Status.")); } catch(Exception ignore){}
            e.printStackTrace();
        }
    }

    private static void clearWorldStatus(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayer();
            if (isBlocked(src, player)) return;
            PlayerSettings settings = StatusMod.storage.forPlayer(player.getUUID().toString());
            String key = getWorldKey(player);
            if (key != null) {
                if (settings.statusByWorld != null) settings.statusByWorld.remove(key);
                if (settings.colorByWorld != null) settings.colorByWorld.remove(key);
                StatusMod.storage.put(player.getUUID().toString(), settings);
                StatusTeamUtil.applyStatus(src.getServer().getScoreboard(), player, settings,
                        StatusTextUtil.resolveStatusForPlayer(settings, player),
                        StatusTextUtil.resolveColorForPlayer(settings, player),
                        PermissionUtil.hasAdminPermission(player));
            }
            src.sendSuccess(() -> Component.literal("World-Status gelöscht."), false);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Löschen des World-Status.")); } catch(Exception ignore){}
            e.printStackTrace();
        }
    }

    private static void showHistory(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayer();
            if (isBlocked(src, player)) return;
            PlayerSettings settings = StatusMod.storage.forPlayer(player.getUUID().toString());
            src.sendSuccess(() -> Component.literal("Status-Verlauf:"), false);
            if (settings.statusHistory == null || settings.statusHistory.isEmpty()) {
                src.sendSuccess(() -> Component.literal("- (leer)"), false);
                return;
            }
            for (String h : settings.statusHistory) {
                if (h == null || h.isBlank()) continue;
                src.sendSuccess(() -> Component.literal("- " + h), false);
            }
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Anzeigen des Verlaufs.")); } catch(Exception ignore){}
            e.printStackTrace();
        }
    }

    private static boolean checkCooldown(CommandSourceStack src, PlayerSettings settings) {
        try {
            if (settings == null) return true;
            int cooldown = StatusMod.config == null ? 0 : StatusMod.config.statusCooldownSeconds;
            if (cooldown <= 0) return true;
            if (PermissionUtil.hasAdminPermission(src)) return true;
            long now = System.currentTimeMillis();
            long last = settings.lastStatusChangeAtMs;
            long remaining = (last + (cooldown * 1000L)) - now;
            if (remaining > 0) {
                long seconds = Math.max(1, remaining / 1000L);
                src.sendFailure(Component.literal("Bitte warte " + seconds + "s bevor du den Status erneut änderst."));
                return false;
            }
        } catch (Exception ignored) {}
        return true;
    }

    private static boolean isBlocked(CommandSourceStack src, ServerPlayer player) {
        if (player == null) return false;
        String uuid = player.getUUID().toString();
        if (StatusMod.blockedPlayers.isBlocked(uuid)) {
            try { src.sendFailure(Component.literal("Du wurdest vom Status-Mod blockiert.")); } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    private static void applyStatusUpdate(CommandSourceStack src, ServerPlayer player, PlayerSettings settings, StatusUpdate update,
                                          boolean perWorld, Long expiresAtMs, boolean keepFont) {
        if (player == null || settings == null || update == null) return;
        if (!keepFont && update.font != null && !update.font.isEmpty()) {
            settings.fontStyle = FontMapper.normalizeStyle(update.font);
        }
        String status = update.status;
        String color = update.color;

        if (perWorld) {
            String key = getWorldKey(player);
            if (key != null) {
                if (settings.statusByWorld != null) settings.statusByWorld.put(key, status);
                if (settings.colorByWorld != null) settings.colorByWorld.put(key, color);
            }
        } else {
            settings.status = status;
            settings.color = color;
        }

        if (expiresAtMs != null) {
            settings.statusExpiresAtMs = expiresAtMs;
        }

        settings.lastStatusChangeAtMs = System.currentTimeMillis();
        addHistory(settings, status);
        StatusMod.storage.put(player.getUUID().toString(), settings);

        MinecraftServer server = src.getServer();
        String appliedStatus = StatusTextUtil.resolveStatusForPlayer(settings, player);
        String appliedColor = StatusTextUtil.resolveColorForPlayer(settings, player);
        StatusTeamUtil.applyStatus(server.getScoreboard(), player, settings, appliedStatus, appliedColor,
                PermissionUtil.hasAdminPermission(player));
    }

    private static void addHistory(PlayerSettings settings, String status) {
        if (settings == null || status == null || status.isBlank()) return;
        if (settings.statusHistory == null) {
            settings.statusHistory = new java.util.ArrayList<>();
        }
        settings.statusHistory.remove(status);
        settings.statusHistory.add(status);
        int max = StatusMod.config == null ? 5 : StatusMod.config.statusHistorySize;
        if (max <= 0) {
            settings.statusHistory.clear();
            return;
        }
        while (settings.statusHistory.size() > max) {
            settings.statusHistory.remove(0);
        }
    }

    private static StatusUpdate parseStatusInput(String statusInput, String colorKey, PlayerSettings settings) {
        int n = settings.statusWords <= 0 ? 1 : settings.statusWords;
        String[] tokens = statusInput == null ? new String[0] : statusInput.trim().split("\\s+");
        if (tokens.length < n) {
            return StatusUpdate.error("Bitte mindestens " + n + " Wörter für den Status angeben.");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(' ');
            sb.append(tokens[i]);
        }
        String status = sb.toString();

        String resolvedColor;
        if (colorKey == null || colorKey.isEmpty()) {
            if (tokens.length > n) {
                resolvedColor = tokens[n];
            } else {
                resolvedColor = (StatusMod.config != null && StatusMod.config.defaultColor != null && !StatusMod.config.defaultColor.isEmpty())
                        ? StatusMod.config.defaultColor
                        : "reset";
            }
        } else {
            resolvedColor = colorKey.trim();
        }

        if (!ColorMapper.isValidColorInput(resolvedColor)) {
            return StatusUpdate.error("Ungültige Farbe: " + resolvedColor);
        }
        return new StatusUpdate(status, resolvedColor, settings.fontStyle, true, null);
    }

    private static String pickStableRandomColor(String uuid) {
        List<TextColor> palette = ColorMapper.rainbowPalette();
        if (palette.isEmpty()) return "reset";
        int idx = Math.abs(uuid.hashCode()) % palette.size();
        return ColorMapper.toHex(palette.get(idx));
    }

    private static String getWorldKey(ServerPlayer player) {
        try {
            return player.level().dimension().toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static class Preset {
        final String status;
        final String color;
        final String font;
        Preset(String status, String color, String font) {
            this.status = status;
            this.color = color;
            this.font = font;
        }
    }

    private static class StatusUpdate {
        String status;
        String color;
        String font;
        boolean ok;
        String error;
        StatusUpdate(String status, String color, String font, boolean ok, String error) {
            this.status = status;
            this.color = color;
            this.font = font;
            this.ok = ok;
            this.error = error;
        }
        static StatusUpdate error(String msg) {
            return new StatusUpdate("", "reset", "normal", false, msg);
        }
    }
}
