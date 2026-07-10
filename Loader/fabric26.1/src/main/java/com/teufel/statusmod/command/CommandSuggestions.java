package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.util.ColorMapper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.concurrent.CompletableFuture;

public final class CommandSuggestions {
    private static final String[] STATUS_SAMPLES = new String[]{"AFK","Busy","Building","Trading",":)",":D",";)","<3","^_^","😊","😎","🔥"};
    private static final String[] PRESET_SAMPLES = new String[]{"afk","busy","stream","shop"};

    private CommandSuggestions() {}

    public static final SuggestionProvider<CommandSourceStack> COLOR_SUGGESTIONS = (CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) -> {
        try {
            if (StatusMod.getConfig() != null && StatusMod.getConfig().defaultColor != null) builder.suggest(StatusMod.getConfig().defaultColor);
            builder.suggest("reset");
            builder.suggest("#RRGGBB");
            builder.suggest("rainbow");
        } catch (Exception ignored) {}
        return CompletableFuture.completedFuture(builder.build());
    };

    public static final SuggestionProvider<CommandSourceStack> STATUS_SUGGESTIONS = (context, builder) -> suggestStatusWithHistory(context.getSource(), builder);
    public static final SuggestionProvider<CommandSourceStack> PRESET_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggest(PRESET_SAMPLES, builder);
    public static final SuggestionProvider<CommandSourceStack> FONT_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggest(new String[]{"normal","smallcaps","bold"}, builder);

    private static CompletableFuture<Suggestions> suggestStatusWithHistory(CommandSourceStack src, SuggestionsBuilder builder) {
        try {
            if (src != null && StatusMod.getStorage() != null) {
                var p = src.getPlayer();
                if (p != null) {
                    var s = StatusMod.getStorage().forPlayer(p.getUUID().toString());
                    if (s != null && s.statusHistory != null) {
                        for (String h : s.statusHistory) {
                            if (h != null && !h.isBlank()) builder.suggest(h);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        SharedSuggestionProvider.suggest(STATUS_SAMPLES, builder);
        return CompletableFuture.completedFuture(builder.build());
    }
}
