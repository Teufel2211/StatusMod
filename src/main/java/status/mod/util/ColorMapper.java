package com.teufel.statusmod.util;

import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ColorMapper {
    private static final Map<String, Formatting> map = new HashMap<>();

    static {
        // englisch
        map.put("black", Formatting.BLACK);
        map.put("dark_blue", Formatting.DARK_BLUE); map.put("darkblue", Formatting.DARK_BLUE);
        map.put("blue", Formatting.BLUE);
        map.put("red", Formatting.RED);
        map.put("green", Formatting.GREEN);
        map.put("yellow", Formatting.YELLOW);
        map.put("gold", Formatting.GOLD);
        map.put("white", Formatting.WHITE);
        map.put("gray", Formatting.GRAY);
        map.put("dark_gray", Formatting.DARK_GRAY); map.put("darkgray", Formatting.DARK_GRAY);
        map.put("aqua", Formatting.AQUA);
        map.put("light_purple", Formatting.LIGHT_PURPLE); map.put("lp", Formatting.LIGHT_PURPLE);
        // deutsch
        map.put("rot", Formatting.RED);
        map.put("blau", Formatting.BLUE);
        map.put("grün", Formatting.GREEN); map.put("gruen", Formatting.GREEN);
        map.put("gelb", Formatting.YELLOW);
        map.put("gold", Formatting.GOLD);
        map.put("weiß", Formatting.WHITE); map.put("weiss", Formatting.WHITE);
        map.put("schwarz", Formatting.BLACK);
        map.put("grau", Formatting.GRAY);
        map.put("dunkelgrau", Formatting.DARK_GRAY);
        map.put("tuerkis", Formatting.AQUA);
    }

    public static Formatting get(String key) {
        if (key == null) return Formatting.RESET;
        String k = key.toLowerCase(Locale.ROOT).replace(" ", "_");
        return map.getOrDefault(k, Formatting.RESET);
    }
}
