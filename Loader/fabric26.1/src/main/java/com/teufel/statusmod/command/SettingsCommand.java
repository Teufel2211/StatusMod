package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.CommandUtil;
import com.teufel.statusmod.util.FontMapper;
import com.teufel.statusmod.util.PermissionUtil;
import com.teufel.statusmod.util.StatusTeamUtil;
import com.teufel.statusmod.util.StatusTextUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class SettingsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("settings")
            .then(Commands.literal("brackets").then(Commands.argument("value", StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"on","off","true","false"}, builder)).executes(ctx -> { toggleBrackets(ctx.getSource(), ctx.getSource().getPlayer(), isTrue(StringArgumentType.getString(ctx, "value"))); return 1; })))
            .then(Commands.literal("position").then(Commands.argument("value", StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"before","after"}, builder)).executes(ctx -> { setPosition(ctx.getSource(), ctx.getSource().getPlayer(), isBefore(StringArgumentType.getString(ctx, "value"))); return 1; })))
            .then(Commands.literal("words").then(Commands.argument("value", IntegerArgumentType.integer(1)).executes(ctx -> { setWords(ctx.getSource(), ctx.getSource().getPlayer(), IntegerArgumentType.getInteger(ctx, "value")); return 1; })))
            .then(Commands.literal("font").then(Commands.argument("value", StringArgumentType.word()).suggests(CommandSuggestions.FONT_SUGGESTIONS).executes(ctx -> { setFont(ctx.getSource(), ctx.getSource().getPlayer(), StringArgumentType.getString(ctx, "value")); return 1; })))
        );
    }

    private static boolean isTrue(String v) { return v != null && (v.equalsIgnoreCase("on") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("an") || v.equalsIgnoreCase("ein")); }
    private static boolean isBefore(String v) { return v != null && (v.equalsIgnoreCase("before") || v.equalsIgnoreCase("vor") || v.equalsIgnoreCase("vorn")); }

    private static void toggleBrackets(CommandSourceStack src, ServerPlayer p, boolean value) { update(src, p, s -> s.brackets = value, "Eckige Klammern: " + (value ? "AN" : "AUS")); }
    private static void setPosition(CommandSourceStack src, ServerPlayer p, boolean before) { update(src, p, s -> s.beforeName = before, "Position: " + (before ? "vor dem Namen" : "hinter dem Namen")); }
    private static void setWords(CommandSourceStack src, ServerPlayer p, int words) { update(src, p, s -> s.statusWords = Math.max(1, words), "Anzahl Status-Wörter: " + Math.max(1, words)); }
    private static void setFont(CommandSourceStack src, ServerPlayer p, String style) { update(src, p, s -> s.fontStyle = FontMapper.normalizeStyle(style), "Schriftart: " + FontMapper.normalizeStyle(style)); }

    private static void update(CommandSourceStack src, ServerPlayer p, java.util.function.Consumer<PlayerSettings> mutator, String msg) {
        try {
            if (p == null) { src.sendFailure(Component.literal("Nur Spieler können diesen Befehl nutzen.")); return; }
            String uuid = p.getUUID().toString();
            PlayerSettings s = StatusMod.getStorage().forPlayer(uuid);
            mutator.accept(s);
            StatusMod.getStorage().put(uuid, s);
            p.sendSystemMessage(Component.literal(msg));
            MinecraftServer server = src.getServer();
            StatusTeamUtil.applyStatus(server.getScoreboard(), p, s, StatusTextUtil.resolveStatusForPlayer(s, p), StatusTextUtil.resolveColorForPlayer(s, p), PermissionUtil.hasAdminPermission(p));
        } catch (Exception e) { e.printStackTrace(); }
    }
}
