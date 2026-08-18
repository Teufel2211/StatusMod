package com.teufel.statusmod.storage;

import com.google.gson.Gson;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ModConfig {
    public int adminOpLevel = 2;
    public String statusPermissionNode = "statusmod.use";
    public String adminPermissionNode = "statusmod.admin";
    public boolean enableAdminOverrides = true;
    public String defaultColor = "reset";
    public int statusReapplyTicks = 100;
    public int statusCooldownSeconds = 2;
    public int statusHistorySize = 5;
    public boolean enableStaffBadge = false;
    public String staffBadgeText = "STAFF";
    public String staffBadgeColor = "red";
    public int staffBadgeBrackets = 1;
    public Map<String, StaffBadge> staffBadges = new HashMap<>();

    public Map<String, Boolean> features = new HashMap<>();

    private static final String[] FEATURE_KEYS = {
        "status", "badge", "afk", "block", "mute", "presets", "code", "sync"
    };

    public boolean isEnabled(String key) {
        if (key == null) return false;
        Boolean v = features == null ? null : features.get(key);
        return v == null || v;
    }

    public void setFeature(String key, boolean enabled) {
        if (features == null) features = new HashMap<>();
        features.put(key, enabled);
    }

    public boolean enableAutoAfk = true;
    public int afkTimeoutSeconds = 300;

    public String dashboardUrl = "https://statusmod-dashboard.vercel.app";
    public String setupSecret = "";
    public String serverId = "";
    public String apiKey = "";
    public long lastSyncAtMs = 0L;

    private static final Gson GSON = new Gson();
    private static final int MIN_REAPPLY_TICKS = 20;
    private static final int MAX_REAPPLY_TICKS = 20 * 300;
    private static final int MAX_HISTORY = 20;

    public static class StaffBadge {
        public String text = "STAFF";
        public String color = "red";
        public int brackets = 1;

        public StaffBadge() {}
    }

    public static ModConfig load() {
        try {
            File configDir = new File("config/statusmod");
            configDir.mkdirs();
            File f = new File(configDir, "config.json");
            if (!f.exists()) {
                ModConfig cfg = new ModConfig();
                cfg.normalize();
                cfg.save();
                return cfg;
            }
            ModConfig cfg;
            try (Reader reader = Files.newBufferedReader(f.toPath())) {
                cfg = GSON.fromJson(reader, ModConfig.class);
            }
            if (cfg == null) cfg = new ModConfig();
            cfg.normalize();
            return cfg;
        } catch (Exception e) {
            System.err.println("[StatusMod] Failed to load config, using defaults");
            e.printStackTrace();
            ModConfig cfg = new ModConfig();
            cfg.normalize();
            return cfg;
        }
    }

    public void save() {
        try {
            File configDir = new File("config/statusmod");
            configDir.mkdirs();
            normalize();
            File f = new File(configDir, "config.json");
            Path target = f.toPath();
            Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(this, writer);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("[StatusMod] Failed to save config");
            e.printStackTrace();
        }
    }

    private void normalize() {
        if (adminOpLevel < 1) adminOpLevel = 1;
        if (adminOpLevel > 4) adminOpLevel = 4;
        if (statusPermissionNode == null || statusPermissionNode.trim().isEmpty()) statusPermissionNode = "statusmod.use";
        else statusPermissionNode = statusPermissionNode.trim();
        if (adminPermissionNode == null || adminPermissionNode.trim().isEmpty()) adminPermissionNode = "statusmod.admin";
        else adminPermissionNode = adminPermissionNode.trim();
        if (defaultColor == null || defaultColor.trim().isEmpty()) defaultColor = "reset";
        else defaultColor = defaultColor.trim();
        if (statusReapplyTicks < MIN_REAPPLY_TICKS) statusReapplyTicks = MIN_REAPPLY_TICKS;
        if (statusReapplyTicks > MAX_REAPPLY_TICKS) statusReapplyTicks = MAX_REAPPLY_TICKS;
        if (statusCooldownSeconds < 0) statusCooldownSeconds = 0;
        if (statusCooldownSeconds > 300) statusCooldownSeconds = 300;
        if (statusHistorySize < 0) statusHistorySize = 0;
        if (statusHistorySize > MAX_HISTORY) statusHistorySize = MAX_HISTORY;
        if (staffBadgeText == null || staffBadgeText.trim().isEmpty()) staffBadgeText = "STAFF";
        else staffBadgeText = staffBadgeText.trim();
        if (staffBadgeBrackets > 0 && staffBadgeText.startsWith("[") && staffBadgeText.endsWith("]")) {
            staffBadgeText = staffBadgeText.substring(1, staffBadgeText.length() - 1);
        }
        if (staffBadgeColor == null || staffBadgeColor.trim().isEmpty()) staffBadgeColor = "red";
        else staffBadgeColor = staffBadgeColor.trim();
        if (features == null) features = new HashMap<>();
        for (String key : FEATURE_KEYS) {
            if (!features.containsKey(key)) {
                features.put(key, Boolean.TRUE);
            }
        }
        if (staffBadges == null) staffBadges = new HashMap<>();
        Iterator<Map.Entry<String, StaffBadge>> it = staffBadges.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, StaffBadge> e = it.next();
            if (e.getKey() == null || e.getKey().isBlank()) { it.remove(); continue; }
            StaffBadge b = e.getValue();
            if (b == null) { it.remove(); continue; }
            if (b.text == null) b.text = "STAFF";
            else b.text = b.text.trim();
            if (b.text.length() > 32) b.text = b.text.substring(0, 32);
            if (b.brackets > 0 && b.text.startsWith("[") && b.text.endsWith("]")) {
                b.text = b.text.substring(1, b.text.length() - 1);
            }
            if (b.color == null || b.color.trim().isEmpty()) b.color = "red";
            else b.color = b.color.trim();
            if (b.text.isEmpty()) { it.remove(); }
        }
        if (afkTimeoutSeconds < 30) afkTimeoutSeconds = 30;
        if (afkTimeoutSeconds > 3600) afkTimeoutSeconds = 3600;
        if (dashboardUrl == null || dashboardUrl.trim().isEmpty()) dashboardUrl = "";
        else dashboardUrl = dashboardUrl.trim();
        if (setupSecret == null) setupSecret = "";
        else setupSecret = setupSecret.trim();
        if (serverId == null) serverId = "";
        else serverId = serverId.trim();
        if (apiKey == null) apiKey = "";
        else apiKey = apiKey.trim();
    }
}
