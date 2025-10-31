package com.example.statusmod.command;

import com.example.statusmod.StatusMod;
import com.example.statusmod.storage.PlayerSettings;
import com.example.statusmod.util.ColorMapper;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class StatusCommand {
    public static void register(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("status")
                .then(argument("status", StringArgumentType.greedyString())
                        .then(argument("color", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerCommandSource src = ctx.getSource();
                                    String status = StringArgumentType.getString(ctx, "status");
                                    String color = StringArgumentType.getString(ctx, "color");
                                    setStatus(src, status, color);
                                    return 1;
                                })
                        )
                        .executes(ctx -> {
                            ServerCommandSource src = ctx.getSource();
                            String status = StringArgumentType.getString(ctx, "status");
                            setStatus(src, status, "reset");
                            return 1;
                        })
                )
        );
    }

    private static void setStatus(ServerCommandSource src, String status, String colorKey) {
        try {
            ServerPlayerEntity player = src.getPlayer();
            String uuid = player.getUuidAsString();

            PlayerSettings settings = StatusMod.storage.forPlayer(uuid);
            settings.status = status;
            settings.color = colorKey;
            StatusMod.storage.put(uuid, settings);

            // apply via scoreboard team prefix/suffix
            MinecraftServer server = src.getServer();
            net.minecraft.scoreboard.ServerScoreboard scoreboard = server.getScoreboard();
            String teamName = "status_" + uuid.substring(0, 8);
            Team team = scoreboard.getTeam(teamName);
            if (team == null) team = scoreboard.addTeam(teamName);

            Formatting f = ColorMapper.get(colorKey);
            String colored = f != null ? f + "" + status : status;
            // build Text for prefix/suffix
            Text txt = Text.literal((settings.brackets ? "[" : "") + status + (settings.brackets ? "]" : ""));
            txt = txt.copy().styled(s -> s.withColor(f == null ? Formatting.RESET : f));

            if (settings.beforeName) {
                team.setPrefix(txt);
                team.setSuffix(Text.empty());
            } else {
                team.setPrefix(Text.empty());
                team.setSuffix(txt);
            }

            // add player to team (so the prefix/suffix is applied)
            scoreboard.addPlayerToTeam(player.getEntityName(), team);

            src.sendFeedback(Text.literal("Status gesetzt: " + status + " (" + colorKey + ")"), false);
        } catch (Exception e) {
            try { src.sendError(Text.literal("Fehler beim Setzen des Status.")); } catch(Exception ignore){}
            e.printStackTrace();
        }
    }
}
