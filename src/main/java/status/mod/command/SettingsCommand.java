package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class SettingsCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("settings")
                .then(literal("brackets")
                        .then(argument("value", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayer();
                                    String v = StringArgumentType.getString(ctx, "value");
                                    boolean b = v.equalsIgnoreCase("on") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("ein") || v.equalsIgnoreCase("an");
                                    toggleBrackets(p, b);
                                    return 1;
                                })
                        )
                )
                .then(literal("position")
                        .then(argument("value", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayer();
                                    String v = StringArgumentType.getString(ctx, "value");
                                    boolean before = v.equalsIgnoreCase("before") || v.equalsIgnoreCase("vor") || v.equalsIgnoreCase("vorn");
                                    setPosition(p, before);
                                    return 1;
                                })
                        )
                )
        );
    }

    private static void toggleBrackets(ServerPlayerEntity p, boolean value) {
        String uuid = p.getUuidAsString();
        PlayerSettings s = StatusMod.storage.forPlayer(uuid);
        s.brackets = value;
        StatusMod.storage.put(uuid, s);
        p.sendMessage(Text.literal("Eckige Klammern: " + (value ? "AN" : "AUS")), false);
        applyStatusToTeam(p, s);
    }

    private static void setPosition(ServerPlayerEntity p, boolean before) {
        String uuid = p.getUuidAsString();
        PlayerSettings s = StatusMod.storage.forPlayer(uuid);
        s.beforeName = before;
        StatusMod.storage.put(uuid, s);
        p.sendMessage(Text.literal("Position: " + (before ? "vor dem Namen" : "hinter dem Namen")), false);
        applyStatusToTeam(p, s);
    }

    private static void applyStatusToTeam(ServerPlayerEntity p, PlayerSettings s) {
        try {
            MinecraftServer server = p.getServer();
            net.minecraft.scoreboard.ServerScoreboard scoreboard = server.getScoreboard();
            String teamName = "status_" + p.getUuidAsString().substring(0, 8);
            net.minecraft.scoreboard.Team team = scoreboard.getTeam(teamName);
            if (team == null) team = scoreboard.addTeam(teamName);

            net.minecraft.text.Text txt = net.minecraft.text.Text.literal((s.brackets ? "[" : "") + s.status + (s.brackets ? "]" : ""));
            net.minecraft.util.Formatting f = com.teufel.statusmod.util.ColorMapper.get(s.color);
            txt = txt.copy().styled(st -> st.withColor(f == null ? net.minecraft.util.Formatting.RESET : f));

            if (s.beforeName) {
                team.setPrefix(txt);
                team.setSuffix(net.minecraft.text.Text.empty());
            } else {
                team.setPrefix(net.minecraft.text.Text.empty());
                team.setSuffix(txt);
            }
            scoreboard.addPlayerToTeam(p.getEntityName(), team);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
