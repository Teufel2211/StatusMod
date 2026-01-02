package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

public class SettingsCommand {
    private static final SuggestionProvider<CommandSourceStack> POSITION_SUGGESTIONS = (context, builder) -> {
        return SharedSuggestionProvider.suggest(new String[]{"before", "vor", "vorn", "after"}, builder);
    };
    
    private static final SuggestionProvider<CommandSourceStack> BRACKETS_SUGGESTIONS = (context, builder) -> {
        return SharedSuggestionProvider.suggest(new String[]{"on", "off", "true", "false", "ein", "an", "aus"}, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("settings")
        .then(Commands.literal("brackets")
            .then(Commands.argument("value", StringArgumentType.word())
                .suggests(BRACKETS_SUGGESTIONS)
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    String v = StringArgumentType.getString(ctx, "value");
                    boolean b = v.equalsIgnoreCase("on") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("ein") || v.equalsIgnoreCase("an");
                    toggleBrackets(p, b);
                    return 1;
                })
            )
        )
        .then(Commands.literal("position")
            .then(Commands.argument("value", StringArgumentType.word())
                .suggests(POSITION_SUGGESTIONS)
                .executes(ctx -> {
                                ServerPlayer p = ctx.getSource().getPlayer();
                    String v = StringArgumentType.getString(ctx, "value");
                    boolean before = v.equalsIgnoreCase("before") || v.equalsIgnoreCase("vor") || v.equalsIgnoreCase("vorn");
                    setPosition(p, before);
                    return 1;
                })
            )
        )
        .then(Commands.literal("words")
            .then(Commands.argument("value", IntegerArgumentType.integer(1))
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    int v = IntegerArgumentType.getInteger(ctx, "value");
                    setWords(p, v);
                    return 1;
                })
            )
        )
    );
    }

    private static void toggleBrackets(ServerPlayer p, boolean value) {
    String uuid = p.getUUID().toString();
        PlayerSettings s = StatusMod.storage.forPlayer(uuid);
        s.brackets = value;
        StatusMod.storage.put(uuid, s);
        p.displayClientMessage(Component.literal("Eckige Klammern: " + (value ? "AN" : "AUS")), false);
        applyStatusToTeam(p, s);
    }

    private static void setPosition(ServerPlayer p, boolean before) {
    String uuid = p.getUUID().toString();
        PlayerSettings s = StatusMod.storage.forPlayer(uuid);
        s.beforeName = before;
        StatusMod.storage.put(uuid, s);
        p.displayClientMessage(Component.literal("Position: " + (before ? "vor dem Namen" : "hinter dem Namen")), false);
        applyStatusToTeam(p, s);
    }

    private static void setWords(ServerPlayer p, int words) {
    String uuid = p.getUUID().toString();
        PlayerSettings s = StatusMod.storage.forPlayer(uuid);
        s.statusWords = Math.max(1, words);
        StatusMod.storage.put(uuid, s);
        p.displayClientMessage(Component.literal("Anzahl Status-Wörter: " + s.statusWords), false);
        applyStatusToTeam(p, s);
    }

    private static void applyStatusToTeam(ServerPlayer p, PlayerSettings s) {
        try {
            MinecraftServer server = p.level().getServer();
            net.minecraft.server.ServerScoreboard scoreboard = server.getScoreboard();
            String teamName = "status_" + p.getUUID().toString().substring(0, 8);
            net.minecraft.world.scores.PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) team = scoreboard.addPlayerTeam(teamName);

            net.minecraft.network.chat.Component base = net.minecraft.network.chat.Component.literal((s.brackets ? "[" : "") + s.status + (s.brackets ? "]" : ""));
            net.minecraft.ChatFormatting f = com.teufel.statusmod.util.ColorMapper.get(s.color);
            net.minecraft.network.chat.Component colored = base.copy().withStyle(st -> st.withColor(f == null ? net.minecraft.ChatFormatting.RESET : f));

            if (s.beforeName) {
                team.setPlayerPrefix(colored.copy().append(net.minecraft.network.chat.Component.literal(" ")));
                team.setPlayerSuffix(net.minecraft.network.chat.Component.empty());
            } else {
                team.setPlayerPrefix(net.minecraft.network.chat.Component.empty());
                team.setPlayerSuffix(net.minecraft.network.chat.Component.literal(" ").append(colored));
            }
            scoreboard.addPlayerToTeam(p.getScoreboardName(), team);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
