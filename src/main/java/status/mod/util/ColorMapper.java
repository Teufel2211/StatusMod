package com.example.statusmod.util;

import net.minecraft.util.Formatting;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ColorMapper {
    private static final Map<String, Formatting> map = new HashMap<>();

    static {
        // englisch
        map.put("black", Formatting.BLACK);
        map.put("dark_blue", Formatting.DARK_BLUE); map.put("darkblue","DARK_BLUE");
        map.put("dark_blue","DARK_BLUE");
        map.put("blue", Formatting.BLUE);
        map.put("red", Formatting.RED);
        map.put("green", Formatting.GREEN);
        map.put("yellow", Formatting.YELLOW);
        map.put("gold", Formatting.GOLD);
        map.put("white", Formatting.WHITE);
        map.put("gray", Formatting.GRAY);
        map.put("dark_gray", Formatting.DARK_GRAY);
        map.put("aqua", Formatting.AQUA);
        // deutsch
        map.put("rot", Formatting.RED);
        map.put("blau", Formatting.BLUE);
        map.put("grün", Formatting.GREEN);
        map.put("gruen", Formatting.GREEN);
        map.put("gelb", Formatting.YELLOW);
        map.put("weiß", Formatting.WHITE); map.put("weiss", Formatting.WHITE);
        map.put("schwarz", Formatting.BLACK);
        map.put("gold", Formatting.GOLD);
        map.put("grau", Formatting.GRAY);
        map.put("dunkelgrau", Formatting.DARK_GRAY);
        // erweitere nach Bedarf
    }

    public static Formatting get(String key) {
        if (key == null) return Formatting.RESET;
        String k = key.toLowerCase(Locale.ROOT).replace(" ", "_");
        return map.getOrDefault(k, Formatting.RESET);
    }
}
