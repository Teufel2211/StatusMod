package com.teufel.statusmod.util;

public final class FontMapper {
    private FontMapper() {}

    public static String normalizeStyle(String style) {
        if (style == null || style.isBlank()) return "normal";
        return style.trim().toLowerCase();
    }

    public static String apply(String style, String text) {
        if (text == null) return "";
        return text;
    }
}
