package com.teufel.statusmod.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorMapper {
    private static final Map<String, ChatFormatting> map = new HashMap<>();
    private static final Pattern RGB_PATTERN = Pattern.compile("(?i)^rgb\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)$");
    private static final Pattern RGB_COLON_PATTERN = Pattern.compile("(?i)^rgb\\s*:\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*$");
    private static final Pattern RGB_PLAIN_PATTERN = Pattern.compile("^\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*$");

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

    /**
     * Parse a hex color code (#RRGGBB or #RGB) and return as TextColor.
     * Returns null if invalid format.
     */
    public static TextColor parseHexColor(String hex) {
        if (hex == null || !hex.startsWith("#")) {
            return null;
        }
        
        String colorCode = hex.substring(1).toUpperCase();
        
        try {
            if (colorCode.length() == 6) {
                // #RRGGBB format
                int rgb = Integer.parseInt(colorCode, 16);
                return TextColor.fromRgb(rgb);
            } else if (colorCode.length() == 3) {
                // #RGB format - expand to #RRGGBB
                StringBuilder expanded = new StringBuilder(6);
                for (char c : colorCode.toCharArray()) {
                    expanded.append(c).append(c);
                }
                int rgb = Integer.parseInt(expanded.toString(), 16);
                return TextColor.fromRgb(rgb);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        
        return null;
    }

    /**
     * Parse CSS-like rgb color: rgb(255,0,128)
     */
    public static TextColor parseRgbColor(String rgbString) {
        if (rgbString == null) return null;
        String in = rgbString.trim();
        Matcher m = RGB_PATTERN.matcher(in);
        if (!m.matches()) {
            m = RGB_COLON_PATTERN.matcher(in);
        }
        if (!m.matches()) {
            m = RGB_PLAIN_PATTERN.matcher(in);
        }
        if (!m.matches()) return null;

        try {
            int r = Integer.parseInt(m.group(1));
            int g = Integer.parseInt(m.group(2));
            int b = Integer.parseInt(m.group(3));
            if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
                return null;
            }
            return TextColor.fromRgb((r << 16) | (g << 8) | b);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse any direct color format this mod supports without named palette fallback.
     */
    public static TextColor parseDirectColor(String color) {
        TextColor hex = parseHexColor(color);
        if (hex != null) return hex;
        return parseRgbColor(color);
    }

    /**
     * Check if a string is a valid hex color code.
     */
    public static boolean isValidHexColor(String hex) {
        return parseHexColor(hex) != null;
    }

    public static boolean isValidColorInput(String color) {
        if (color == null || color.isBlank()) return false;
        if ("reset".equalsIgnoreCase(color)) return true;
        if ("rainbow".equalsIgnoreCase(color)) return true;

        if (color.contains("|") || color.contains(";")) {
            List<TextColor> palette = parseColorPalette(color);
            return palette.size() >= 2;
        }

        if (parseDirectColor(color) != null) return true;
        String k = color.toLowerCase(Locale.ROOT).replace(" ", "_");
        return map.containsKey(k);
    }

    public static boolean isAnimatedColorInput(String color) {
        if (color == null || color.isBlank()) return false;
        if ("rainbow".equalsIgnoreCase(color.trim())) return true;
        return parseColorPalette(color).size() >= 2;
    }

    public static List<TextColor> parseColorPalette(String input) {
        List<TextColor> colors = new ArrayList<>();
        if (input == null || input.isBlank()) return colors;
        if ("rainbow".equalsIgnoreCase(input.trim())) {
            // Green -> Blue -> Pink -> Yellow (loop)
            addDirect(colors, "#00FF66");
            addDirect(colors, "#00A2FF");
            addDirect(colors, "#FF4FD8");
            addDirect(colors, "#FFE600");
            return colors;
        }
        if (!(input.contains("|") || input.contains(";"))) return colors;

        String[] parts = input.split("[|;]");
        for (String part : parts) {
            if (part == null) continue;
            String token = part.trim();
            if (token.isEmpty()) continue;
            TextColor tc = parseDirectColor(token);
            if (tc != null) {
                colors.add(tc);
            }
        }
        return colors;
    }

    private static void addDirect(List<TextColor> out, String token) {
        TextColor tc = parseDirectColor(token);
        if (tc != null) out.add(tc);
    }

    /**
     * Return a copy of all known color keys (for command suggestions).
     */
    public static java.util.Set<String> keys() {
        return new java.util.HashSet<>(map.keySet());
    }
}
