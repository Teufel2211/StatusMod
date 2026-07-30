package com.teufel.statusmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.AuditLogger;
import com.teufel.statusmod.storage.ModConfig;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.ColorMapper;
import com.teufel.statusmod.util.CommandUtil;
import com.teufel.statusmod.util.FontMapper;
import com.teufel.statusmod.util.PermissionUtil;
import com.teufel.statusmod.util.StatusTeamUtil;
import com.teufel.statusmod.util.StatusTextUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatusCommand {
    private static final int MAX_STATUS_LENGTH = 64;
    private static final Map<String, Preset> PRESETS = new HashMap<>();
    static {
        PRESETS.put("afk", new Preset("AFK", "yellow", "normal"));
        PRESETS.put("busy", new Preset("Busy", "red", "normal"));
        PRESETS.put("stream", new Preset("Stream", "light_purple", "smallcaps"));
        PRESETS.put("shop", new Preset("Shop", "gold", "normal"));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> statusTree = Commands.literal("status")
            .then(Commands.literal("clear").executes(ctx -> { clearStatus(ctx.getSource()); return 1; }))
            .then(Commands.argument("status", StringArgumentType.greedyString()).suggests(CommandSuggestions.STATUS_SUGGESTIONS).executes(ctx -> { setStatus(ctx.getSource(), StringArgumentType.getString(ctx, "status"), null); return 1; }))
            .then(Commands.literal("preset")
                .then(Commands.literal("save").then(Commands.argument("preset_name", StringArgumentType.word()).then(Commands.argument("status", StringArgumentType.greedyString()).suggests(CommandSuggestions.STATUS_SUGGESTIONS).executes(ctx -> { saveCustomPreset(ctx.getSource(), StringArgumentType.getString(ctx, "preset_name"), StringArgumentType.getString(ctx, "status"), null); return 1; }))))
                .then(Commands.literal("remove").then(Commands.argument("preset_name", StringArgumentType.word()).suggests(CommandSuggestions.CUSTOM_PRESET_SUGGESTIONS).executes(ctx -> { removeCustomPreset(ctx.getSource(), StringArgumentType.getString(ctx, "preset_name")); return 1; })))
                .then(Commands.literal("list").executes(ctx -> { listCustomPresets(ctx.getSource()); return 1; }))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(CommandSuggestions.PRESET_SUGGESTIONS).executes(ctx -> { applyPreset(ctx.getSource(), StringArgumentType.getString(ctx, "name")); return 1; })))
            .then(Commands.literal("random").then(Commands.argument("status", StringArgumentType.greedyString()).suggests(CommandSuggestions.STATUS_SUGGESTIONS).executes(ctx -> { setRandomStatus(ctx.getSource(), StringArgumentType.getString(ctx, "status")); return 1; })))
            .then(Commands.literal("timed").then(Commands.argument("minutes", IntegerArgumentType.integer(1)).then(Commands.argument("status", StringArgumentType.greedyString()).suggests(CommandSuggestions.STATUS_SUGGESTIONS).executes(ctx -> { setTimedStatus(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "minutes"), StringArgumentType.getString(ctx, "status")); return 1; }))))
            .then(Commands.literal("history").executes(ctx -> { showHistory(ctx.getSource()); return 1; }))
            .then(Commands.literal("world").then(Commands.literal("clear").executes(ctx -> { clearWorldStatus(ctx.getSource()); return 1; }))
                .then(Commands.argument("status", StringArgumentType.greedyString()).suggests(CommandSuggestions.STATUS_SUGGESTIONS).executes(ctx -> { setWorldStatus(ctx.getSource(), StringArgumentType.getString(ctx, "status"), null); return 1; })));
        if (StatusMod.getConfig().enableAdminOverrides) {
            statusTree = statusTree.then(Commands.literal("admin")
                .then(Commands.literal("clear").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player()).executes(ctx -> { if (!PermissionUtil.hasAdminPermission(ctx.getSource())) { ctx.getSource().sendFailure(Component.literal("Du hast nicht genügend Rechte, um andere Spieler zu verwalten.")); return 0; } ServerPlayer player = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"); adminClearStatus(ctx.getSource(), player.getScoreboardName()); return 1; })))
                .then(Commands.literal("set").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player()).then(Commands.argument("status", StringArgumentType.greedyString()).suggests(CommandSuggestions.STATUS_SUGGESTIONS).executes(ctx -> { if (!PermissionUtil.hasAdminPermission(ctx.getSource())) { ctx.getSource().sendFailure(Component.literal("Du hast nicht genügend Rechte, um andere Spieler zu verwalten.")); return 0; } ServerPlayer player = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"); adminSetStatus(ctx.getSource(), player.getScoreboardName(), StringArgumentType.getString(ctx, "status"), null); return 1; }))))
                .then(Commands.literal("mute").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player()).then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440)).executes(ctx -> { if (!PermissionUtil.hasAdminPermission(ctx.getSource())) { ctx.getSource().sendFailure(Component.literal("Du hast nicht genügend Rechte.")); return 0; } ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"); int mins = IntegerArgumentType.getInteger(ctx, "minutes"); mutePlayer(ctx.getSource(), target, mins); return 1; }))))
                .then(Commands.literal("unmute").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player()).executes(ctx -> { if (!PermissionUtil.hasAdminPermission(ctx.getSource())) { ctx.getSource().sendFailure(Component.literal("Du hast nicht genügend Rechte.")); return 0; } ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"); unmutePlayer(ctx.getSource(), target); return 1; })))
                .then(Commands.literal("audit").executes(ctx -> { if (!PermissionUtil.hasAdminPermission(ctx.getSource())) { ctx.getSource().sendFailure(Component.literal("Du hast nicht genügend Rechte.")); return 0; } showAuditLog(ctx.getSource()); return 1; }))
            );
        }
        statusTree = statusTree.then(Commands.literal("config").then(Commands.literal("reload").executes(ctx -> { if (!PermissionUtil.hasAdminPermission(ctx.getSource())) { ctx.getSource().sendFailure(Component.literal("Du hast nicht genügend Rechte, um diese Aktion auszuführen.")); return 0; } StatusMod.config = ModConfig.load(); CommandUtil.sendSuccess(ctx.getSource(), Component.literal("StatusMod configuration reloaded."), false); return 1; }))
            .then(Commands.literal("show").executes(ctx -> { if (!PermissionUtil.hasAdminPermission(ctx.getSource())) { ctx.getSource().sendFailure(Component.literal("Du hast nicht genügend Rechte, um diese Aktion auszuführen.")); return 0; } ModConfig c = StatusMod.getConfig(); CommandUtil.sendSuccess(ctx.getSource(), Component.literal("StatusMod configuration:"), false); CommandUtil.sendSuccess(ctx.getSource(), Component.literal(" adminOpLevel = " + c.adminOpLevel), false); CommandUtil.sendSuccess(ctx.getSource(), Component.literal(" statusPermissionNode = " + c.statusPermissionNode), false); CommandUtil.sendSuccess(ctx.getSource(), Component.literal(" adminPermissionNode = " + c.adminPermissionNode), false); CommandUtil.sendSuccess(ctx.getSource(), Component.literal(" enableAdminOverrides = " + c.enableAdminOverrides), false); CommandUtil.sendSuccess(ctx.getSource(), Component.literal(" defaultColor = " + c.defaultColor), false); CommandUtil.sendSuccess(ctx.getSource(), Component.literal(" statusReapplyTicks = " + c.statusReapplyTicks), false); CommandUtil.sendSuccess(ctx.getSource(), Component.literal(" statusCooldownSeconds = " + c.statusCooldownSeconds), false); CommandUtil.sendSuccess(ctx.getSource(), Component.literal(" statusHistorySize = " + c.statusHistorySize), false); CommandUtil.sendSuccess(ctx.getSource(), Component.literal(" enableStaffBadge = " + c.enableStaffBadge), false); CommandUtil.sendSuccess(ctx.getSource(), Component.literal(" staffBadgeText = " + c.staffBadgeText), false); CommandUtil.sendSuccess(ctx.getSource(), Component.literal(" staffBadgeColor = " + c.staffBadgeColor), false); CommandUtil.sendSuccess(ctx.getSource(), Component.literal(" enableAutoAfk = " + c.enableAutoAfk), false); CommandUtil.sendSuccess(ctx.getSource(), Component.literal(" afkTimeoutSeconds = " + c.afkTimeoutSeconds), false); return 1; })));
        dispatcher.register(statusTree);
    }

    private static void setStatus(CommandSourceStack src, String status, String colorKey) {
        try {
            ServerPlayer player = src.getPlayer();
            if (player == null) { src.sendFailure(Component.literal("Nur Spieler können diesen Befehl nutzen.")); return; }
            if (!PermissionUtil.hasStatusPermission(src)) { src.sendFailure(Component.literal("Du hast keine Berechtigung, den Status-Mod zu nutzen.")); return; }
            String uuid = player.getUUID().toString();
            if (checkBlockedOrMuted(src, uuid)) return;
            PlayerSettings settings = StatusMod.getStorage().forPlayer(uuid);
            if (!checkCooldown(src, settings)) return;
            StatusUpdate update = parseStatusInput(status, colorKey, settings);
            if (!update.ok) { src.sendFailure(Component.literal(update.error)); return; }
            applyStatusUpdate(src, player, settings, update, false, null, false);
            AuditLogger.logSet(player.getScoreboardName(), player.getScoreboardName(), update.status, update.color);
            CommandUtil.sendSuccess(src, Component.literal("Status gesetzt: " + update.status + " (" + update.color + ")"), false);
        } catch (Exception e) { try { src.sendFailure(Component.literal("Fehler beim Setzen des Status.")); } catch(Exception ignore){} e.printStackTrace(); }
    }

    private static void clearStatus(CommandSourceStack src) { try { ServerPlayer player = src.getPlayer(); if (player == null) { src.sendFailure(Component.literal("Nur Spieler können diesen Befehl nutzen.")); return; } if (!PermissionUtil.hasStatusPermission(src)) { src.sendFailure(Component.literal("Du hast keine Berechtigung.")); return; } String uuid = player.getUUID().toString(); if (checkBlockedOrMuted(src, uuid)) return; PlayerSettings settings = StatusMod.getStorage().forPlayer(uuid); if (!checkCooldown(src, settings)) return; settings.status=""; settings.color="reset"; settings.statusExpiresAtMs=0L; StatusMod.getStorage().put(uuid, settings); StatusTeamUtil.applyStatus(src.getServer().getScoreboard(), player, settings, "", "reset", PermissionUtil.hasAdminPermission(player)); AuditLogger.logClear(player.getScoreboardName(), player.getScoreboardName()); CommandUtil.sendSuccess(src, Component.literal("Status gelöscht."), false);} catch (Exception e){try{src.sendFailure(Component.literal("Fehler beim Löschen des Status."));}catch(Exception ignore){} e.printStackTrace();}}

    private static void adminSetStatus(CommandSourceStack src, String targetName, String status, String colorKey) { try { ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(targetName); if (target == null) { src.sendFailure(Component.literal("Spieler '" + targetName + "' ist nicht online.")); return; } PlayerSettings settings = StatusMod.getStorage().forPlayer(target.getUUID().toString()); StatusUpdate update = parseStatusInput(status, colorKey, settings); if (!update.ok) { src.sendFailure(Component.literal(update.error)); return; } applyStatusUpdate(src, target, settings, update, false, null, false); String who = src.getTextName(); AuditLogger.logSet(who, targetName, update.status, update.color); CommandUtil.sendSuccess(src, Component.literal("Status von " + targetName + " gesetzt: " + update.status + " (" + update.color + ")"), false); target.sendSystemMessage(Component.literal("Dein Status wurde von einem Administrator gesetzt."));} catch (Exception e){try{src.sendFailure(Component.literal("Fehler beim Setzen des Status für '" + targetName + "'."));}catch(Exception ignore){} e.printStackTrace();}}
    private static void adminClearStatus(CommandSourceStack src, String targetName) { try { ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(targetName); if (target == null) { src.sendFailure(Component.literal("Spieler '" + targetName + "' ist nicht online.")); return; } PlayerSettings settings = StatusMod.getStorage().forPlayer(target.getUUID().toString()); settings.status=""; settings.color="reset"; settings.statusExpiresAtMs=0L; StatusMod.getStorage().put(target.getUUID().toString(), settings); StatusTeamUtil.applyStatus(src.getServer().getScoreboard(), target, settings, "", "reset", PermissionUtil.hasAdminPermission(target)); String who = src.getTextName(); AuditLogger.logClear(who, targetName); CommandUtil.sendSuccess(src, Component.literal("Status von " + targetName + " gelöscht."), false); target.sendSystemMessage(Component.literal("Dein Status wurde von einem Administrator gelöscht."));} catch (Exception e){try{src.sendFailure(Component.literal("Fehler beim Löschen des Status für '" + targetName + "'."));}catch(Exception ignore){} e.printStackTrace();}}
    private static void applyPreset(CommandSourceStack src, String name) {
        try {
            if (!PermissionUtil.hasStatusPermission(src)) { src.sendFailure(Component.literal("Du hast keine Berechtigung.")); return; }
            String key = (name == null ? "" : name).toLowerCase();
            Preset preset = PRESETS.get(key);
            if (preset == null) {
                var custom = StatusMod.getCustomPresets().get(name);
                if (custom != null) {
                    ServerPlayer player = src.getPlayer();
                    if (player == null) { src.sendFailure(Component.literal("Nur Spieler können diesen Befehl nutzen.")); return; }
                    if (checkBlockedOrMuted(src, player.getUUID().toString())) return;
                    PlayerSettings settings = StatusMod.getStorage().forPlayer(player.getUUID().toString());
                    if (!checkCooldown(src, settings)) return;
                    StatusUpdate update = new StatusUpdate(custom.status, custom.color, settings.fontStyle, true, null);
                    applyStatusUpdate(src, player, settings, update, false, null, false);
                    AuditLogger.logSet(player.getScoreboardName(), player.getScoreboardName(), custom.status, custom.color);
                    CommandUtil.sendSuccess(src, Component.literal("Preset '" + name + "' gesetzt: " + custom.status + " (" + custom.color + ")"), false);
                    return;
                }
                src.sendFailure(Component.literal("Unbekanntes Preset: " + name));
                return;
            }
            ServerPlayer player = src.getPlayer();
            if (player == null) { src.sendFailure(Component.literal("Nur Spieler können diesen Befehl nutzen.")); return; }
            if (checkBlockedOrMuted(src, player.getUUID().toString())) return;
            PlayerSettings settings = StatusMod.getStorage().forPlayer(player.getUUID().toString());
            if (!checkCooldown(src, settings)) return;
            StatusUpdate update = new StatusUpdate(preset.status, preset.color, preset.font, true, null);
            applyStatusUpdate(src, player, settings, update, false, null, false);
            AuditLogger.logSet(player.getScoreboardName(), player.getScoreboardName(), preset.status, preset.color);
            CommandUtil.sendSuccess(src, Component.literal("Preset gesetzt: " + preset.status + " (" + preset.color + ")"), false);
        } catch (Exception e){try{src.sendFailure(Component.literal("Fehler beim Setzen des Presets."));}catch(Exception ignore){} e.printStackTrace();}
    }
    private static void setRandomStatus(CommandSourceStack src, String statusInput) { try { ServerPlayer player = src.getPlayer(); if (player == null) { src.sendFailure(Component.literal("Nur Spieler können diesen Befehl nutzen.")); return; } if (!PermissionUtil.hasStatusPermission(src)) { src.sendFailure(Component.literal("Du hast keine Berechtigung.")); return; } if (checkBlockedOrMuted(src, player.getUUID().toString())) return; PlayerSettings settings = StatusMod.getStorage().forPlayer(player.getUUID().toString()); if (!checkCooldown(src, settings)) return; StatusUpdate update = parseStatusInput(statusInput, null, settings); if (!update.ok) { src.sendFailure(Component.literal(update.error)); return; } update.color = pickStableRandomColor(player.getUUID().toString()); applyStatusUpdate(src, player, settings, update, false, null, false); AuditLogger.logSet(player.getScoreboardName(), player.getScoreboardName(), update.status, update.color); CommandUtil.sendSuccess(src, Component.literal("Status gesetzt (random): " + update.status + " (" + update.color + ")"), false);} catch (Exception e){try{src.sendFailure(Component.literal("Fehler beim Setzen des random Status."));}catch(Exception ignore){} e.printStackTrace();}}
    private static void setTimedStatus(CommandSourceStack src, int minutes, String statusInput) { try { ServerPlayer player = src.getPlayer(); if (player == null) { src.sendFailure(Component.literal("Nur Spieler können diesen Befehl nutzen.")); return; } if (!PermissionUtil.hasStatusPermission(src)) { src.sendFailure(Component.literal("Du hast keine Berechtigung.")); return; } if (checkBlockedOrMuted(src, player.getUUID().toString())) return; PlayerSettings settings = StatusMod.getStorage().forPlayer(player.getUUID().toString()); if (!checkCooldown(src, settings)) return; StatusUpdate update = parseStatusInput(statusInput, null, settings); if (!update.ok) { src.sendFailure(Component.literal(update.error)); return; } applyStatusUpdate(src, player, settings, update, false, System.currentTimeMillis() + (minutes * 60L * 1000L), false); AuditLogger.logSet(player.getScoreboardName(), player.getScoreboardName(), update.status, update.color); CommandUtil.sendSuccess(src, Component.literal("Status gesetzt für " + minutes + " Minuten."), false);} catch (Exception e){try{src.sendFailure(Component.literal("Fehler beim Setzen des Timed-Status."));}catch(Exception ignore){} e.printStackTrace();}}
    private static void setWorldStatus(CommandSourceStack src, String statusInput, String colorKey) { try { ServerPlayer player = src.getPlayer(); if (player == null) { src.sendFailure(Component.literal("Nur Spieler können diesen Befehl nutzen.")); return; } if (!PermissionUtil.hasStatusPermission(src)) { src.sendFailure(Component.literal("Du hast keine Berechtigung.")); return; } if (checkBlockedOrMuted(src, player.getUUID().toString())) return; PlayerSettings settings = StatusMod.getStorage().forPlayer(player.getUUID().toString()); if (!checkCooldown(src, settings)) return; StatusUpdate update = parseStatusInput(statusInput, colorKey, settings); if (!update.ok) { src.sendFailure(Component.literal(update.error)); return; } applyStatusUpdate(src, player, settings, update, true, null, false); AuditLogger.logSet(player.getScoreboardName(), player.getScoreboardName(), update.status, update.color); CommandUtil.sendSuccess(src, Component.literal("World-Status gesetzt."), false);} catch (Exception e){try{src.sendFailure(Component.literal("Fehler beim Setzen des World-Status."));}catch(Exception ignore){} e.printStackTrace();}}
    private static void clearWorldStatus(CommandSourceStack src) { try { ServerPlayer player = src.getPlayer(); if (player == null) { src.sendFailure(Component.literal("Nur Spieler können diesen Befehl nutzen.")); return; } if (!PermissionUtil.hasStatusPermission(src)) { src.sendFailure(Component.literal("Du hast keine Berechtigung.")); return; } if (checkBlockedOrMuted(src, player.getUUID().toString())) return; PlayerSettings settings = StatusMod.getStorage().forPlayer(player.getUUID().toString()); if (!checkCooldown(src, settings)) return; String key = com.teufel.statusmod.util.CompatUtil.getWorldKey(player); if (key != null) { if (settings.statusByWorld != null) settings.statusByWorld.remove(key); if (settings.colorByWorld != null) settings.colorByWorld.remove(key); StatusMod.getStorage().put(player.getUUID().toString(), settings); StatusTeamUtil.applyStatus(src.getServer().getScoreboard(), player, settings, StatusTextUtil.resolveStatusForPlayer(settings, player), StatusTextUtil.resolveColorForPlayer(settings, player), PermissionUtil.hasAdminPermission(player)); } AuditLogger.logClear(player.getScoreboardName(), player.getScoreboardName()); CommandUtil.sendSuccess(src, Component.literal("World-Status gelöscht."), false);} catch (Exception e){try{src.sendFailure(Component.literal("Fehler beim Löschen des World-Status."));}catch(Exception ignore){} e.printStackTrace();}}
    private static void showHistory(CommandSourceStack src) { try { ServerPlayer player = src.getPlayer(); if (player == null) { src.sendFailure(Component.literal("Nur Spieler können diesen Befehl nutzen.")); return; } if (!PermissionUtil.hasStatusPermission(src)) { src.sendFailure(Component.literal("Du hast keine Berechtigung.")); return; } if (checkBlockedOrMuted(src, player.getUUID().toString())) return; PlayerSettings settings = StatusMod.getStorage().forPlayer(player.getUUID().toString()); CommandUtil.sendSuccess(src, Component.literal("Status-Verlauf:"), false); if (settings.statusHistory == null || settings.statusHistory.isEmpty()) { CommandUtil.sendSuccess(src, Component.literal("- (leer)"), false); return; } for (String h : settings.statusHistory) { if (h == null || h.isBlank()) continue; CommandUtil.sendSuccess(src, Component.literal("- " + h), false); } } catch (Exception e){try{src.sendFailure(Component.literal("Fehler beim Anzeigen des Verlaufs."));}catch(Exception ignore){} e.printStackTrace();}}

    private static void saveCustomPreset(CommandSourceStack src, String name, String status, String colorKey) {
        try {
            if (!PermissionUtil.hasStatusPermission(src)) { src.sendFailure(Component.literal("Du hast keine Berechtigung.")); return; }
            ServerPlayer player = src.getPlayer();
            if (player == null) { src.sendFailure(Component.literal("Nur Spieler können Presets speichern.")); return; }
            if (PRESETS.containsKey(name.toLowerCase())) { src.sendFailure(Component.literal("Ein eingebautes Preset mit dem Namen '" + name + "' existiert bereits.")); return; }
            String resolvedColor = (colorKey != null && !colorKey.isEmpty()) ? colorKey : "reset";
            if (!ColorMapper.isValidColorInput(resolvedColor)) { src.sendFailure(Component.literal("Ungültige Farbe: " + resolvedColor)); return; }
            if (StatusMod.getCustomPresets().add(name, status, resolvedColor, player.getUUID().toString())) {
                CommandUtil.sendSuccess(src, Component.literal("Preset '" + name + "' gespeichert: " + status + " (" + resolvedColor + ")"), true);
            } else {
                src.sendFailure(Component.literal("Ein Preset mit dem Namen '" + name + "' existiert bereits."));
            }
        } catch (Exception e) { try { src.sendFailure(Component.literal("Fehler beim Speichern des Presets.")); } catch(Exception ignore){} e.printStackTrace(); }
    }

    private static void removeCustomPreset(CommandSourceStack src, String name) {
        try {
            ServerPlayer player = src.getPlayer();
            String uuid = player != null ? player.getUUID().toString() : null;
            boolean isAdmin = PermissionUtil.hasAdminPermission(src);
            String who = src.getTextName();
            if (isAdmin || uuid != null) {
                if (isAdmin ? StatusMod.getCustomPresets().adminRemove(name) : StatusMod.getCustomPresets().remove(name, uuid)) {
                    CommandUtil.sendSuccess(src, Component.literal("Preset '" + name + "' entfernt."), true);
                } else {
                    src.sendFailure(Component.literal("Konnte Preset '" + name + "' nicht entfernen (nicht gefunden oder keine Berechtigung)."));
                }
            } else {
                src.sendFailure(Component.literal("Keine Berechtigung."));
            }
        } catch (Exception e) { try { src.sendFailure(Component.literal("Fehler beim Entfernen des Presets.")); } catch(Exception ignore){} e.printStackTrace(); }
    }

    private static void listCustomPresets(CommandSourceStack src) {
        try {
            var presets = StatusMod.getCustomPresets().getAll();
            CommandUtil.sendSuccess(src, Component.literal("Verfügbare Presets:"), false);
            for (var e : PRESETS.entrySet()) {
                CommandUtil.sendSuccess(src, Component.literal("§e" + e.getKey() + "§r → " + e.getValue().status + " (" + e.getValue().color + ") §7[built-in]"), false);
            }
            if (presets.isEmpty()) {
                CommandUtil.sendSuccess(src, Component.literal("- (keine benutzerdefinierten Presets)"), false);
            } else {
                for (var e : presets.entrySet()) {
                    CommandUtil.sendSuccess(src, Component.literal("§a" + e.getKey() + "§r → " + e.getValue().status + " (" + e.getValue().color + ")"), false);
                }
            }
        } catch (Exception e) { try { src.sendFailure(Component.literal("Fehler beim Anzeigen der Presets.")); } catch(Exception ignore){} e.printStackTrace(); }
    }

    private static void mutePlayer(CommandSourceStack src, ServerPlayer target, int minutes) {
        try {
            String uuid = target.getUUID().toString();
            StatusMod.getMutedPlayers().mute(uuid, minutes);
            String who = src.getTextName();
            AuditLogger.logMute(who, target.getScoreboardName(), minutes);
            CommandUtil.sendSuccess(src, Component.literal(target.getScoreboardName() + " wurde für " + minutes + " Minuten gestummt."), true);
            target.sendSystemMessage(Component.literal("Du wurdest für " + minutes + " Minuten vom Status-Mod gestummt."));
        } catch (Exception e) { try { src.sendFailure(Component.literal("Fehler beim Muten.")); } catch(Exception ignore){} e.printStackTrace(); }
    }

    private static void unmutePlayer(CommandSourceStack src, ServerPlayer target) {
        try {
            String uuid = target.getUUID().toString();
            StatusMod.getMutedPlayers().unmute(uuid);
            String who = src.getTextName();
            AuditLogger.logUnmute(who, target.getScoreboardName());
            CommandUtil.sendSuccess(src, Component.literal(target.getScoreboardName() + " wurde entstummt."), true);
            target.sendSystemMessage(Component.literal("Du wurdest vom Status-Mod entstummt."));
        } catch (Exception e) { try { src.sendFailure(Component.literal("Fehler beim Entmuten.")); } catch(Exception ignore){} e.printStackTrace(); }
    }

    private static void showAuditLog(CommandSourceStack src) {
        try {
            java.nio.file.Path path = Paths.get("config/statusmod/audit.log");
            if (!Files.exists(path)) {
                CommandUtil.sendSuccess(src, Component.literal("Audit-Log ist leer."), false);
                return;
            }
            List<String> lines = Files.readAllLines(path);
            int start = Math.max(0, lines.size() - 25);
            CommandUtil.sendSuccess(src, Component.literal("--- Letzte Einträge (max 25) ---"), false);
            for (int i = start; i < lines.size(); i++) {
                CommandUtil.sendSuccess(src, Component.literal(lines.get(i)), false);
            }
        } catch (IOException e) {
            src.sendFailure(Component.literal("Fehler beim Lesen des Audit-Logs."));
        }
    }

    private static boolean checkBlockedOrMuted(CommandSourceStack src, String uuid) {
        if (StatusMod.getBlockedPlayers().isBlocked(uuid)) {
            src.sendFailure(Component.literal("Du wurdest vom Status-Mod blockiert."));
            return true;
        }
        if (StatusMod.getMutedPlayers().isMuted(uuid)) {
            long until = StatusMod.getMutedPlayers().getMutedUntil(uuid);
            long remaining = Math.max(1, (until - System.currentTimeMillis()) / 1000L);
            src.sendFailure(Component.literal("Du bist noch " + remaining + "s vom Status-Mod gestummt."));
            return true;
        }
        return false;
    }

    static boolean checkCooldown(CommandSourceStack src, PlayerSettings settings) { try { int cooldown = StatusMod.getConfig() == null ? 0 : StatusMod.getConfig().statusCooldownSeconds; if (cooldown <= 0) return true; if (PermissionUtil.hasAdminPermission(src)) return true; long remaining = (settings.lastStatusChangeAtMs + (cooldown * 1000L)) - System.currentTimeMillis(); if (remaining > 0) { src.sendFailure(Component.literal("Bitte warte " + Math.max(1, remaining / 1000L) + "s bevor du den Status erneut änderst.")); return false; } } catch (Exception ignored) { return false; } return true; }
    private static void applyStatusUpdate(CommandSourceStack src, ServerPlayer player, PlayerSettings settings, StatusUpdate update, boolean perWorld, Long expiresAtMs, boolean keepFont) { if (player == null || settings == null || update == null) return; if (!keepFont && update.font != null && !update.font.isEmpty()) settings.fontStyle = FontMapper.normalizeStyle(update.font); if (perWorld) { String key = com.teufel.statusmod.util.CompatUtil.getWorldKey(player); if (key != null) { if (settings.statusByWorld != null) settings.statusByWorld.put(key, update.status); if (settings.colorByWorld != null) settings.colorByWorld.put(key, update.color); } } else { settings.status = update.status; settings.color = update.color; } if (expiresAtMs != null) settings.statusExpiresAtMs = expiresAtMs; settings.lastStatusChangeAtMs = System.currentTimeMillis(); addHistory(settings, update.status); StatusMod.getStorage().put(player.getUUID().toString(), settings); var server = src.getServer(); StatusTeamUtil.applyStatus(server.getScoreboard(), player, settings, StatusTextUtil.resolveStatusForPlayer(settings, player), StatusTextUtil.resolveColorForPlayer(settings, player), PermissionUtil.hasAdminPermission(player)); }
    private static void addHistory(PlayerSettings settings, String status) { if (settings == null || status == null || status.isBlank()) return; if (settings.statusHistory == null) settings.statusHistory = new java.util.ArrayList<>(); settings.statusHistory.remove(status); settings.statusHistory.add(status); int max = StatusMod.getConfig() == null ? 5 : StatusMod.getConfig().statusHistorySize; while (settings.statusHistory.size() > max && max > 0) settings.statusHistory.remove(0); if (max <= 0) settings.statusHistory.clear(); }
    private static StatusUpdate parseStatusInput(String statusInput, String colorKey, PlayerSettings settings) { if (settings == null) return StatusUpdate.error("Fehler: Keine Einstellungen."); int n = settings.statusWords <= 0 ? 1 : settings.statusWords; String[] tokens = statusInput == null ? new String[0] : statusInput.trim().split("\\s+"); if (tokens.length < n) return StatusUpdate.error("Bitte mindestens " + n + " Wörter für den Status angeben."); StringBuilder sb = new StringBuilder(); for (int i = 0; i < n; i++) { if (i > 0) sb.append(' '); sb.append(tokens[i]); }     String status = sb.toString();
    status = status.replaceAll("(?s)\u00A7.", "");
    status = status.replace("\u00A7", "");
    if (status.length() > MAX_STATUS_LENGTH) {
        status = status.substring(0, MAX_STATUS_LENGTH);
    } String resolvedColor = (colorKey == null || colorKey.isEmpty()) ? ((tokens.length > n) ? tokens[n] : (StatusMod.getConfig() != null && StatusMod.getConfig().defaultColor != null && !StatusMod.getConfig().defaultColor.isEmpty() ? StatusMod.getConfig().defaultColor : "reset")) : colorKey.trim(); if (!ColorMapper.isValidColorInput(resolvedColor)) return StatusUpdate.error("Ungültige Farbe: " + resolvedColor); return new StatusUpdate(status, resolvedColor, settings.fontStyle, true, null); }
    private static String pickStableRandomColor(String uuid) { List<TextColor> palette = ColorMapper.rainbowPalette(); if (palette.isEmpty()) return "reset";         return ColorMapper.toHex(palette.get((uuid.hashCode() & Integer.MAX_VALUE) % palette.size())); }
    private static class Preset { final String status; final String color; final String font; Preset(String status, String color, String font) { this.status = status; this.color = color; this.font = font; } }
    private static class StatusUpdate { String status; String color; String font; boolean ok; String error; StatusUpdate(String status, String color, String font, boolean ok, String error) { this.status = status; this.color = color; this.font = font; this.ok = ok; this.error = error; } static StatusUpdate error(String msg) { return new StatusUpdate("", "reset", "normal", false, msg); } }
}
