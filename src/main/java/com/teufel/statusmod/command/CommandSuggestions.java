package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.util.ColorMapper;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.concurrent.CompletableFuture;

public class CommandSuggestions {
    private static final String[] STATUS_SAMPLES = new String[]{
            "AFK",
            "Busy",
            "Building",
            "Trading",
            ":)",
            ":D",
            ";)",
            "<3",
            "^_^",
            "😊",
            "😎",
            "🔥"
    };

    public static final SuggestionProvider<CommandSourceStack> COLOR_SUGGESTIONS = (CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) -> {
        try {
            // suggest configured default first
            if (StatusMod.config != null && StatusMod.config.defaultColor != null) {
                builder.suggest(StatusMod.config.defaultColor);
            }
            // suggest known named colors
            for (String k : ColorMapper.keys()) {
                builder.suggest(k);
            }
            // sample hex placeholder
            builder.suggest("#RRGGBB");
            builder.suggest("#RGB");
            // sample rgb placeholders
            builder.suggest("rgb(255,0,0)");
            builder.suggest("rgb(0,255,0)");
            builder.suggest("rgb(0,128,255)");
            builder.suggest("rgb(255,0,0)|rgb(0,255,0)|rgb(0,0,255)");
            builder.suggest("rainbow");
            // reset shortcut
            builder.suggest("reset");
        } catch (Exception ignored) {}
        return CompletableFuture.completedFuture(builder.build());
    };

    public static final SuggestionProvider<CommandSourceStack> STATUS_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(STATUS_SAMPLES, builder);

    public static final SuggestionProvider<CommandSourceStack> FONT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(com.teufel.statusmod.util.FontMapper.styles(), builder);
}
