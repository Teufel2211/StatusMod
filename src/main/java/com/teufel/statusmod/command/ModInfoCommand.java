package com.teufel.statusmod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;

public class ModInfoCommand {
    private static final String WEBSITE = "https://github.com/Teufel2211/StatusMod";
    private static final String ISSUES = "https://github.com/Teufel2211/StatusMod/issues";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("modinfo")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    try {
                        Component title = Component.literal("StatusMod — Informationen");
                        src.sendSuccess(() -> title, false);

                        // Send plain links so all mappings compile reliably; clients can click the link in chat
                        src.sendSuccess(() -> Component.literal("Website: " + WEBSITE), false);
                        src.sendSuccess(() -> Component.literal("Issues: " + ISSUES), false);
                    } catch (Exception e) {
                        src.sendFailure(Component.literal("Fehler beim Anzeigen der Mod-Informationen."));
                        e.printStackTrace();
                    }
                    return 1;
                })
        );
    }
}
