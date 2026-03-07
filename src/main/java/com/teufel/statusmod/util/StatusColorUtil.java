package com.teufel.statusmod.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.List;

public final class StatusColorUtil {
    private StatusColorUtil() {}

    public static Component applyColor(Component base, String colorKey) {
        String text = base == null ? "" : base.getString();
        return applyColor(text, colorKey);
    }

    public static Component applyColor(String text, String colorKey) {
        if (text == null) text = "";

        List<TextColor> palette = ColorMapper.parseColorPalette(colorKey);
        if (palette.size() >= 2) {
            MutableComponent out = Component.empty();
            int i = 0;
            for (int off = 0; off < text.length();) {
                int cp = text.codePointAt(off);
                off += Character.charCount(cp);
                TextColor c = palette.get(i % palette.size());
                out.append(Component.literal(new String(Character.toChars(cp))).withStyle(s -> s.withColor(c)));
                i++;
            }
            return out;
        }

        TextColor directColor = ColorMapper.parseDirectColor(colorKey);
        if (directColor != null) {
            return Component.literal(text).withStyle(s -> s.withColor(directColor));
        }

        ChatFormatting named = ColorMapper.get(colorKey);
        return Component.literal(text).withStyle(s -> s.withColor(named == null ? ChatFormatting.RESET : named));
    }
}
