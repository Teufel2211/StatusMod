package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.storage.PlayerSettings;
import com.teufel.statusmod.util.PermissionUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Command to block/unblock players from using the status mod.
 * Usage:
 *   /block <player> - block a player from using status mod
 *   /unblock <player> - unblock a player
 */
public class BlockCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /block command
        dispatcher.register(Commands.literal("block")
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    blockPlayer(src, target.getUUID().toString(), target.getScoreboardName());
                    return 1;
                })
            )
        );

        // /unblock command
        dispatcher.register(Commands.literal("unblock")
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    unblockPlayer(src, target.getUUID().toString(), target.getScoreboardName());
                    return 1;
                })
            )
        );
    }

    private static void blockPlayer(CommandSourceStack src, String uuid, String playerName) {
        try {
            // Require admin permission but do *not* insist on a non-null player source.
            if (!PermissionUtil.hasAdminPermission(src)) {
                src.sendFailure(Component.literal("Du hast keine Berechtigung für diesen Befehl."));
                return;
            }

            // Find the target player
            ServerPlayer targetPlayer = src.getServer().getPlayerList().getPlayerByName(playerName);
            if (targetPlayer == null) {
                src.sendFailure(Component.literal("Spieler '" + playerName + "' nicht gefunden."));
                return;
            }

            String targetUuid = uuid;

            // Check if already blocked
            if (StatusMod.blockedPlayers.isBlocked(targetUuid)) {
                src.sendFailure(Component.literal(playerName + " ist bereits blockiert."));
                return;
            }

            // Block the player
            StatusMod.blockedPlayers.block(targetUuid);
            
            // Clear the target player's status
            PlayerSettings settings = StatusMod.storage.forPlayer(targetUuid);
            settings.status = "";
            try {
                settings.color = (StatusMod.config != null && StatusMod.config.defaultColor != null)
                        ? StatusMod.config.defaultColor
                        : "reset";
            } catch (Exception ignored) {
                settings.color = "reset";
            }
            StatusMod.storage.put(targetUuid, settings);

            src.sendSuccess(() -> Component.literal(playerName + " wurde vom Status-Mod blockiert."), true);
            targetPlayer.displayClientMessage(
                Component.literal("Du wurdest vom Status-Mod blockiert."),
                false
            );
        } catch (Exception e) {
            try {
                src.sendFailure(Component.literal("Fehler beim Blockieren des Spielers."));
            } catch (Exception ignore) {}
            e.printStackTrace();
        }
    }

    private static void unblockPlayer(CommandSourceStack src, String uuid, String playerName) {
        try {
            // Check if executor has admin permission. Allow null sources such as
            // console or `/player` artificial executions.
            if (!PermissionUtil.hasAdminPermission(src)) {
                src.sendFailure(Component.literal("Du hast keine Berechtigung für diesen Befehl."));
                return;
            }

            // Find the target player (or get UUID from name if offline)
            ServerPlayer targetPlayer = src.getServer().getPlayerList().getPlayerByName(playerName);
            String targetUuid;
            
            if (targetPlayer != null) {
                targetUuid = targetPlayer.getUUID().toString();
            } else {
                // Try to find UUID by querying player database (Minecraft profile)
                try {
                    // For offline players, we'd need their UUID from somewhere else
                    src.sendFailure(Component.literal("Spieler '" + playerName + "' ist nicht online. Du kannst nur online-Spieler freigeben."));
                    return;
                } catch (Exception e) {
                    src.sendFailure(Component.literal("Spieler '" + playerName + "' nicht gefunden."));
                    return;
                }
            }

            // Check if player is blocked
            if (!StatusMod.blockedPlayers.isBlocked(targetUuid)) {
                src.sendFailure(Component.literal(playerName + " ist nicht blockiert."));
                return;
            }

            // Unblock the player
            StatusMod.blockedPlayers.unblock(targetUuid);
            src.sendSuccess(() -> Component.literal(playerName + " wurde vom Status-Mod freigegeben."), true);
            
            if (targetPlayer != null) {
                targetPlayer.displayClientMessage(
                    Component.literal("Du wurdest vom Status-Mod freigegeben."),
                    false
                );
            }
        } catch (Exception e) {
            try {
                src.sendFailure(Component.literal("Fehler beim Freigeben des Spielers."));
            } catch (Exception ignore) {}
            e.printStackTrace();
        }
    }
}
