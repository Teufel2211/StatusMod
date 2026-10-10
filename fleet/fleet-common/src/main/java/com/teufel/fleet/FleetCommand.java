package com.teufel.fleet;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class FleetCommand {
    private FleetCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fleet")
            .then(Commands.literal("status").executes(ctx -> {
                showStatus(ctx.getSource());
                return 1;
            }))
            .then(Commands.literal("apply").executes(ctx -> {
                if (!FleetPerm.isAdmin(ctx.getSource())) {
                    ctx.getSource().sendFailure(Component.literal("Du hast nicht genügend Rechte."));
                    return 0;
                }
                FleetManager.requestSync();
                ctx.getSource().sendSuccess(() -> Component.literal("Fleet-Sync angestoßen (Pull in wenigen Sekunden)."), false);
                return 1;
            })));
    }

    private static void showStatus(CommandSourceStack src) {
        if (!FleetPerm.isAdmin(src)) {
            src.sendFailure(Component.literal("Du hast nicht genügend Rechte."));
            return;
        }
        src.sendSuccess(() -> Component.literal("Fleet Sync (build " + FleetMod.BUILD + "):"), false);
        src.sendSuccess(() -> Component.literal(" letzter Pull: " + FleetManager.lastPullInfo), false);
        src.sendSuccess(() -> Component.literal(" letzte Änderung: " + FleetManager.lastChangeInfo), false);
        src.sendSuccess(() -> Component.literal("Intervall: 60s + /fleet apply. serverId/apiKey bleiben je Server erhalten."), false);
    }
}
