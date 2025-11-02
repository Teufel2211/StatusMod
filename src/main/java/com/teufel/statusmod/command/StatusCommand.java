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
            settings.status = status;
            settings.color = colorKey;
            StatusMod.storage.put(uuid, settings);

            MinecraftServer server = src.getServer();
            net.minecraft.server.ServerScoreboard scoreboard = server.getScoreboard();
            String teamName = "status_" + uuid.substring(0, 8);
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) team = scoreboard.addPlayerTeam(teamName);

            ChatFormatting f = ColorMapper.get(colorKey);
            Component base = Component.literal((settings.brackets ? "[" : "") + status + (settings.brackets ? "]" : ""));
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

            src.sendSuccess(() -> Component.literal("Status gesetzt: " + status + " (" + colorKey + ")"), false);
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Setzen des Status.")); } catch(Exception ignore){}
            e.printStackTrace();
        }
    }
}
