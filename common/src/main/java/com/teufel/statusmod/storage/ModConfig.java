package com.teufel.statusmod.storage;

import com.google.gson.Gson;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ModConfig {
    public int adminOpLevel = 2;
    public String statusPermissionNode = "statusmod.use";
    public String adminPermissionNode = "statusmod.admin";
    public boolean enableAdminOverrides = true;
    public String defaultColor = "reset";
    public int statusReapplyTicks = 100;
    public int statusCooldownSeconds = 2;
    public int statusHistorySize = 5;
    public boolean enableStaffBadge = true;
    public String staffBadgeText = "[STAFF]";
    public String staffBadgeColor = "red";

    private static final Gson GSON = new Gson();
    private static final int MIN_REAPPLY_TICKS = 20;
    private static final int MAX_REAPPLY_TICKS = 20 * 300;
    private static final int MAX_HISTORY = 20;

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
        if (adminOpLevel < 0) adminOpLevel = 0;
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
        if (staffBadgeText == null || staffBadgeText.trim().isEmpty()) staffBadgeText = "[STAFF]";
        else staffBadgeText = staffBadgeText.trim();
        if (staffBadgeColor == null || staffBadgeColor.trim().isEmpty()) staffBadgeColor = "red";
        else staffBadgeColor = staffBadgeColor.trim();
    }
}
