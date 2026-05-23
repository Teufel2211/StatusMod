package com.teufel.statusmod.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FontMapper {
    private static final String STYLE_NORMAL = "normal";
    private static final String STYLE_BOLD = "bold";
    private static final String STYLE_MONO = "mono";
    private static final String STYLE_SMALLCAPS = "smallcaps";
    private static final String STYLE_BUBBLE = "bubble";

    private static final Set<String> STYLES = Set.of(
            STYLE_NORMAL, STYLE_BOLD, STYLE_MONO, STYLE_SMALLCAPS, STYLE_BUBBLE
    );

    private static final Map<Integer, String> MAP_BOLD = new HashMap<>();
    private static final Map<Integer, String> MAP_MONO = new HashMap<>();
    private static final Map<Integer, String> MAP_SMALLCAPS = new HashMap<>();
    private static final Map<Integer, String> MAP_BUBBLE = new HashMap<>();

    static {
        fillRange(MAP_BOLD, 'A', 'Z', 0x1D400);
        fillRange(MAP_BOLD, 'a', 'z', 0x1D41A);
        fillRange(MAP_BOLD, '0', '9', 0x1D7CE);

        fillRange(MAP_MONO, 'A', 'Z', 0x1D670);
        fillRange(MAP_MONO, 'a', 'z', 0x1D68A);
        fillRange(MAP_MONO, '0', '9', 0x1D7F6);

        fillRange(MAP_BUBBLE, 'A', 'Z', 0x24B6);
        fillRange(MAP_BUBBLE, 'a', 'z', 0x24D0);
        fillRange(MAP_BUBBLE, '1', '9', 0x2460);
        MAP_BUBBLE.put((int) '0', new String(Character.toChars(0x24EA)));

        // Best-effort smallcaps map (not all letters exist in Unicode small caps).
        put(MAP_SMALLCAPS, 'a', "ᴀ");
        put(MAP_SMALLCAPS, 'b', "ʙ");
        put(MAP_SMALLCAPS, 'c', "ᴄ");
        put(MAP_SMALLCAPS, 'd', "ᴅ");
        put(MAP_SMALLCAPS, 'e', "ᴇ");
        put(MAP_SMALLCAPS, 'f', "ꜰ");
        put(MAP_SMALLCAPS, 'g', "ɢ");
        put(MAP_SMALLCAPS, 'h', "ʜ");
        put(MAP_SMALLCAPS, 'i', "ɪ");
        put(MAP_SMALLCAPS, 'j', "ᴊ");
        put(MAP_SMALLCAPS, 'k', "ᴋ");
        put(MAP_SMALLCAPS, 'l', "ʟ");
        put(MAP_SMALLCAPS, 'm', "ᴍ");
        put(MAP_SMALLCAPS, 'n', "ɴ");
        put(MAP_SMALLCAPS, 'o', "ᴏ");
        put(MAP_SMALLCAPS, 'p', "ᴘ");
        put(MAP_SMALLCAPS, 'q', "ǫ");
        put(MAP_SMALLCAPS, 'r', "ʀ");
        put(MAP_SMALLCAPS, 's', "s");
        put(MAP_SMALLCAPS, 't', "ᴛ");
        put(MAP_SMALLCAPS, 'u', "ᴜ");
        put(MAP_SMALLCAPS, 'v', "ᴠ");
        put(MAP_SMALLCAPS, 'w', "ᴡ");
        put(MAP_SMALLCAPS, 'x', "x");
        put(MAP_SMALLCAPS, 'y', "ʏ");
        put(MAP_SMALLCAPS, 'z', "ᴢ");
    }

    private FontMapper() {}

    public static Set<String> styles() {
        return Collections.unmodifiableSet(STYLES);
    }

    public static String normalizeStyle(String style) {
        if (style == null || style.isBlank()) return STYLE_NORMAL;
        String normalized = style.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        return STYLES.contains(normalized) ? normalized : STYLE_NORMAL;
    }

    public static String apply(String style, String text) {
        if (text == null || text.isEmpty()) return "";
        String normalized = normalizeStyle(style);
        if (STYLE_NORMAL.equals(normalized)) return text;

        Map<Integer, String> map = switch (normalized) {
            case STYLE_BOLD -> MAP_BOLD;
            case STYLE_MONO -> MAP_MONO;
            case STYLE_SMALLCAPS -> MAP_SMALLCAPS;
            case STYLE_BUBBLE -> MAP_BUBBLE;
            default -> null;
        };
        if (map == null) return text;

        StringBuilder out = new StringBuilder(text.length() * 2);
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            String mapped = map.get(Character.toLowerCase(cp));
            if (mapped == null) {
                mapped = map.get(cp);
            }
            if (mapped != null) out.append(mapped);
            else out.appendCodePoint(cp);
        }
        return out.toString();
    }

    private static void fillRange(Map<Integer, String> map, char from, char to, int base) {
        for (int c = from; c <= to; c++) {
            map.put(c, new String(Character.toChars(base + (c - from))));
        }
    }

    private static void put(Map<Integer, String> map, char c, String value) {
        map.put((int) c, value);
    }
}
