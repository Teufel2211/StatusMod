package com.teufel.statusmod.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.List;

public final class StatusColorUtil {
    private static final long RAINBOW_STEP_MILLIS = 180L;

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
            double phase = (System.currentTimeMillis() / (double) RAINBOW_STEP_MILLIS);
            int i = 0;
            for (int off = 0; off < text.length();) {
                int cp = text.codePointAt(off);
                off += Character.charCount(cp);
                TextColor c = interpolatePaletteColor(palette, i + phase);
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

    private static TextColor interpolatePaletteColor(List<TextColor> palette, double indexWithPhase) {
        int size = palette.size();
        if (size == 0) {
            return TextColor.fromRgb(0xFFFFFF);
        }
        if (size == 1) {
            return palette.get(0);
        }

        int baseIndex = floorMod((int) Math.floor(indexWithPhase), size);
        int nextIndex = (baseIndex + 1) % size;
        double blend = indexWithPhase - Math.floor(indexWithPhase);

        int start = colorValue(palette.get(baseIndex));
        int end = colorValue(palette.get(nextIndex));

        int r = lerp((start >> 16) & 0xFF, (end >> 16) & 0xFF, blend);
        int g = lerp((start >> 8) & 0xFF, (end >> 8) & 0xFF, blend);
        int b = lerp(start & 0xFF, end & 0xFF, blend);

        return TextColor.fromRgb((r << 16) | (g << 8) | b);
    }

    private static int colorValue(TextColor color) {
        Integer value = color == null ? null : color.getValue();
        return value == null ? 0xFFFFFF : value.intValue();
    }

    private static int lerp(int a, int b, double t) {
        double clamped = t < 0 ? 0 : (t > 1 ? 1 : t);
        return (int) Math.round(a + (b - a) * clamped);
    }

    private static int floorMod(int value, int mod) {
        int m = value % mod;
        return m < 0 ? m + mod : m;
    }
}
