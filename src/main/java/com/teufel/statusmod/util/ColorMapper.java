package com.teufel.statusmod.util;

import net.minecraft.ChatFormatting;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ColorMapper {
    private static final Map<String, ChatFormatting> map = new HashMap<>();

    static {
        // englisch
    map.put("black", ChatFormatting.BLACK);
    map.put("dark_blue", ChatFormatting.DARK_BLUE); map.put("darkblue", ChatFormatting.DARK_BLUE);
    map.put("blue", ChatFormatting.BLUE);
    map.put("red", ChatFormatting.RED);
    map.put("green", ChatFormatting.GREEN);
    map.put("yellow", ChatFormatting.YELLOW);
    map.put("gold", ChatFormatting.GOLD);
    map.put("white", ChatFormatting.WHITE);
    map.put("gray", ChatFormatting.GRAY);
    map.put("dark_gray", ChatFormatting.DARK_GRAY); map.put("darkgray", ChatFormatting.DARK_GRAY);
    map.put("aqua", ChatFormatting.AQUA);
    map.put("light_purple", ChatFormatting.LIGHT_PURPLE); map.put("lp", ChatFormatting.LIGHT_PURPLE);
        // deutsch
    map.put("rot", ChatFormatting.RED);
    map.put("blau", ChatFormatting.BLUE);
    map.put("grün", ChatFormatting.GREEN); map.put("gruen", ChatFormatting.GREEN);
    map.put("gelb", ChatFormatting.YELLOW);
    map.put("gold", ChatFormatting.GOLD);
    map.put("weiß", ChatFormatting.WHITE); map.put("weiss", ChatFormatting.WHITE);
    map.put("schwarz", ChatFormatting.BLACK);
    map.put("grau", ChatFormatting.GRAY);
    map.put("dunkelgrau", ChatFormatting.DARK_GRAY);
    map.put("tuerkis", ChatFormatting.AQUA);
    }

    public static ChatFormatting get(String key) {
        if (key == null) return ChatFormatting.RESET;
        String k = key.toLowerCase(Locale.ROOT).replace(" ", "_");
        return map.getOrDefault(k, ChatFormatting.RESET);
    }
}
