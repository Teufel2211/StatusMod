package com.teufel.statusmod.command;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.util.ColorMapper;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import java.util.concurrent.CompletableFuture;

public class CommandSuggestions {
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
        } catch (Exception ignored) {}
        return CompletableFuture.completedFuture(builder.build());
    };
}
