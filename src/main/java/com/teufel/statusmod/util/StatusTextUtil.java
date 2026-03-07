package com.teufel.statusmod.util;

import com.teufel.statusmod.storage.PlayerSettings;

public final class StatusTextUtil {
    private StatusTextUtil() {}

    public static String renderStatusText(PlayerSettings settings) {
        if (settings == null) return "";
        return renderStatusText(settings.status, settings);
    }

    public static String renderStatusText(String rawStatus, PlayerSettings settings) {
        String status = rawStatus == null ? "" : rawStatus;
        String font = settings == null ? "normal" : settings.fontStyle;
        boolean brackets = settings != null && settings.brackets;

        String transformed = FontMapper.apply(font, status);
        return (brackets ? "[" : "") + transformed + (brackets ? "]" : "");
    }
}
