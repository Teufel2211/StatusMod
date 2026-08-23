package com.teufel.statusmod.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.List;

public final class StatusColorUtil {
    private static final long RAINBOW_STEP_MILLIS = 200L;

    private static final int[] RAINBOW_RGB = {
        0xFFFF55, // yellow
        0xFFA500, // orange
        0x55FF55, // green
        0x5555FF, // blue
        0xFF55FF, // pink
        0xFF5555, // red
        0xAA55FF, // purple
    };

    private StatusColorUtil() {}

    public static Component applyColor(Component base, String colorKey) {
        String text = base == null ? "" : base.getString();
        return applyColor(text, colorKey);
    }

    public static Component applyColor(String text, String colorKey) {
        if (text == null) text = "";

        if (ColorMapper.isAnimatedColorInput(colorKey)) {
            return applyRainbowPerChar(text);
        }

        TextColor directColor = ColorMapper.parseDirectColor(colorKey);
        if (directColor != null) {
            return Component.literal(text).withStyle(s -> s.withColor(directColor));
        }
        ChatFormatting named = ColorMapper.get(colorKey);
        return Component.literal(text).withStyle(s -> s.withColor(named == null ? ChatFormatting.RESET : named));
    }

    private static Component applyRainbowPerChar(String text) {
        MutableComponent out = Component.empty();
        if (text.isEmpty()) return out;

        long now = System.currentTimeMillis();
        long phase = now / RAINBOW_STEP_MILLIS;
        double blend = (now % RAINBOW_STEP_MILLIS) / (double) RAINBOW_STEP_MILLIS;
        int paletteSize = RAINBOW_RGB.length;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                out.append(Component.literal(" "));
                continue;
            }
            long charPhase = phase + i;
            int baseIdx = floorMod(charPhase, paletteSize);
            int nextIdx = (baseIdx + 1) % paletteSize;
            int start = RAINBOW_RGB[baseIdx];
            int end = RAINBOW_RGB[nextIdx];
            int r = lerp((start >> 16) & 0xFF, (end >> 16) & 0xFF, blend);
            int g = lerp((start >> 8) & 0xFF, (end >> 8) & 0xFF, blend);
            int b = lerp(start & 0xFF, end & 0xFF, blend);
            TextColor color = TextColor.fromRgb((r << 16) | (g << 8) | b);
            out.append(Component.literal(String.valueOf(c)).withStyle(s -> s.withColor(color)));
        }
        return out;
    }

    private static int lerp(int a, int b, double t) {
        double clamped = t < 0 ? 0 : Math.min(1, t);
        return (int) Math.round(a + (b - a) * clamped);
    }

    private static int floorMod(long value, int mod) {
        int m = (int) (value % mod);
        return m < 0 ? m + mod : m;
    }
}
