package com.teufel.statusmod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;

public class ModInfoCommand {
    private static final String WEBSITE = "https://example.com/your-mod-homepage";
    private static final String ISSUES = "https://example.com/your-mod-issues";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("modinfo")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    try {
                        Component title = Component.literal("StatusMod — Informationen");
                        src.sendSuccess(() -> title, false);

                        Component web = Component.literal("[Website]")
                                .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, WEBSITE))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Open website"))));

                        Component issues = Component.literal("[Issues]")
                                .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ISSUES))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Open issue tracker"))));

                        src.sendSuccess(() -> Component.literal(" ").append(web).append(Component.literal(" ")).append(issues), false);
                    } catch (Exception e) {
                        src.sendFailure(Component.literal("Fehler beim Anzeigen der Mod-Informationen."));
                        e.printStackTrace();
                    }
                    return 1;
                })
        );
    }
}
