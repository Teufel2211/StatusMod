package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.CommandUtil;
import com.teufel.statusmod.util.PermissionUtil;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class BlockCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("block").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
            CommandSourceStack src = ctx.getSource();
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            blockPlayer(src, target.getUUID().toString(), target.getScoreboardName());
            return 1;
        })));
        dispatcher.register(Commands.literal("unblock").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
            CommandSourceStack src = ctx.getSource();
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            unblockPlayer(src, target.getUUID().toString(), target.getScoreboardName());
            return 1;
        })));
    }

    private static void blockPlayer(CommandSourceStack src, String uuid, String playerName) {
        try {
            if (!PermissionUtil.hasAdminPermission(src)) {
                src.sendFailure(Component.literal("Du hast keine Berechtigung für diesen Befehl."));
                return;
            }
            ServerPlayer targetPlayer = src.getServer().getPlayerList().getPlayerByName(playerName);
            if (targetPlayer == null) {
                src.sendFailure(Component.literal("Spieler '" + playerName + "' nicht gefunden."));
                return;
            }
            if (StatusMod.getBlockedPlayers().isBlocked(uuid)) {
                src.sendFailure(Component.literal(playerName + " ist bereits blockiert."));
                return;
            }
            StatusMod.getBlockedPlayers().block(uuid);
            PlayerSettings settings = StatusMod.getStorage().forPlayer(uuid);
            settings.status = "";
            settings.color = StatusMod.getConfig() != null && StatusMod.getConfig().defaultColor != null ? StatusMod.getConfig().defaultColor : "reset";
            StatusMod.getStorage().put(uuid, settings);
            CommandUtil.sendSuccess(src, Component.literal(playerName + " wurde vom Status-Mod blockiert."), true);
            targetPlayer.sendSystemMessage(Component.literal("Du wurdest vom Status-Mod blockiert."));
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Blockieren des Spielers.")); } catch (Exception ignore) {}
            e.printStackTrace();
        }
    }

    private static void unblockPlayer(CommandSourceStack src, String uuid, String playerName) {
        try {
            if (!PermissionUtil.hasAdminPermission(src)) {
                src.sendFailure(Component.literal("Du hast keine Berechtigung für diesen Befehl."));
                return;
            }
            ServerPlayer targetPlayer = src.getServer().getPlayerList().getPlayerByName(playerName);
            String targetUuid = targetPlayer != null ? targetPlayer.getUUID().toString() : uuid;
            if (!StatusMod.getBlockedPlayers().isBlocked(targetUuid)) {
                src.sendFailure(Component.literal(playerName + " ist nicht blockiert."));
                return;
            }
            StatusMod.getBlockedPlayers().unblock(targetUuid);
            CommandUtil.sendSuccess(src, Component.literal(playerName + " wurde vom Status-Mod freigegeben."), true);
            if (targetPlayer != null) targetPlayer.sendSystemMessage(Component.literal("Du wurdest vom Status-Mod freigegeben."));
        } catch (Exception e) {
            try { src.sendFailure(Component.literal("Fehler beim Freigeben des Spielers.")); } catch (Exception ignore) {}
            e.printStackTrace();
        }
    }
}
