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
    private static final String[] PRESET_SAMPLES = new String[]{
            "afk",
            "busy",
            "stream",
            "shop"
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
            builder.suggest("rainbow1530");
            // reset shortcut
            builder.suggest("reset");
        } catch (Exception ignored) {}
        return CompletableFuture.completedFuture(builder.build());
    };

    public static final SuggestionProvider<CommandSourceStack> STATUS_SUGGESTIONS = (context, builder) ->
            suggestStatusWithHistory(context.getSource(), builder);

    public static final SuggestionProvider<CommandSourceStack> PRESET_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(PRESET_SAMPLES, builder);

    public static final SuggestionProvider<CommandSourceStack> FONT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(com.teufel.statusmod.util.FontMapper.styles(), builder);

    private static CompletableFuture<Suggestions> suggestStatusWithHistory(CommandSourceStack src, SuggestionsBuilder builder) {
        try {
            if (src != null) {
                try {
                    net.minecraft.server.level.ServerPlayer p = src.getPlayer();
                    if (p != null && StatusMod.storage != null) {
                        com.teufel.statusmod.storage.PlayerSettings s = StatusMod.storage.forPlayer(p.getUUID().toString());
                        if (s != null && s.statusHistory != null) {
                            for (String h : s.statusHistory) {
                                if (h != null && !h.isBlank()) builder.suggest(h);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        SharedSuggestionProvider.suggest(STATUS_SAMPLES, builder);
        return CompletableFuture.completedFuture(builder.build());
    }
}
