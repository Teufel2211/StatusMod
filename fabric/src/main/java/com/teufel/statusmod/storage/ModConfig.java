package com.teufel.statusmod.storage;

import com.google.gson.Gson;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Global configuration for the StatusMod.  Server administrators can edit
 * config/statusmod/config.json to change behaviour without recompiling the jar.
 * Fields are intentionally public for Gson and simplicity.
 */
public class ModConfig {
    /**
     * Operator permission level required to run admin commands.  Corresponds to
     * the integer passed to CommandSourceStack.hasPermission; default 2.
     */
    public int adminOpLevel = 2;

    /**
     * Permission node that players must have in LuckyPerms to use the status mod
     * (only consulted when LuckyPerms is installed).
     */
    public String statusPermissionNode = "statusmod.use";

    /**
     * Permission node that players must have to perform administrative operations.
     */
    public String adminPermissionNode = "statusmod.admin";

    /**
     * Whether the "/status admin" override commands are enabled at all.
     */
    public boolean enableAdminOverrides = true;

    /**
     * Default colour applied if a player does not specify one.  Can be either a
     * named colour ("red", "reset", etc.) or a hex code (#RRGGBB).
     */
    public String defaultColor = "reset";

    /**
     * Reapply interval for scoreboard status, in server ticks.
     * 20 ticks = 1 second.
     */
    public int statusReapplyTicks = 100;

    /**
     * Cooldown between status changes in seconds.
     */
    public int statusCooldownSeconds = 2;

    /**
     * Maximum number of entries kept in per-player history.
     */
    public int statusHistorySize = 5;

    /**
     * Staff badge settings (shown for admins/operators).
     */
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
            if (cfg == null) {
                cfg = new ModConfig();
            }
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

        if (statusPermissionNode == null || statusPermissionNode.trim().isEmpty()) {
            statusPermissionNode = "statusmod.use";
        } else {
            statusPermissionNode = statusPermissionNode.trim();
        }

        if (adminPermissionNode == null || adminPermissionNode.trim().isEmpty()) {
            adminPermissionNode = "statusmod.admin";
        } else {
            adminPermissionNode = adminPermissionNode.trim();
        }

        if (defaultColor == null || defaultColor.trim().isEmpty()) {
            defaultColor = "reset";
        } else {
            defaultColor = defaultColor.trim();
        }

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
