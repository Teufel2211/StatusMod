package com.teufel.statusmod.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ColorMapper {
    private static final Set<String> KEYS = new HashSet<>(Arrays.asList(
        "black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold",
        "gray","dark_gray","blue","green","aqua","red","light_purple","yellow","white","reset"
    ));

    private ColorMapper() {}

    public static boolean isAnimatedColorInput(String color) {
        return color != null && (color.equalsIgnoreCase("rainbow") || color.equalsIgnoreCase("animated"));
    }

    public static boolean isValidColorInput(String color) {
        return color != null && !color.isBlank();
    }

    public static List<TextColor> parseColorPalette(String color) {
        return isAnimatedColorInput(color) ? rainbowPalette() : List.of();
    }

    public static TextColor parseDirectColor(String color) {
        if (color == null) return null;
        String trimmed = color.trim();
        if (trimmed.startsWith("#")) {
            try {
                String hex = trimmed.substring(1);
                if (hex.length() == 3) {
                    char r = hex.charAt(0), g = hex.charAt(1), b = hex.charAt(2);
                    hex = "" + r + r + g + g + b + b;
                }
                if (hex.length() != 6) return null;
                int rgb = Integer.parseInt(hex, 16);
                return TextColor.fromRgb(rgb);
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static ChatFormatting get(String key) {
        if (key == null) return null;
        try {
            return ChatFormatting.valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Set<String> keys() {
        return new HashSet<>(KEYS);
    }

    public static List<TextColor> rainbowPalette() {
        return List.of(TextColor.fromRgb(0xFF0000), TextColor.fromRgb(0x00FF00), TextColor.fromRgb(0x0000FF));
    }

    public static String toHex(TextColor color) {
        return color == null ? "reset" : "#" + Integer.toHexString(color.getValue());
    }
}
