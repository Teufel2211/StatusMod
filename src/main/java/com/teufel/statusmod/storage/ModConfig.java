package com.teufel.statusmod.storage;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

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

    private static final Gson GSON = new Gson();

    public static ModConfig load() {
        try {
            File configDir = new File("config/statusmod");
            configDir.mkdirs();
            File f = new File(configDir, "config.json");
            if (!f.exists()) {
                ModConfig cfg = new ModConfig();
                cfg.save();
                return cfg;
            }
            FileReader reader = new FileReader(f);
            ModConfig cfg = GSON.fromJson(reader, ModConfig.class);
            reader.close();
            if (cfg == null) {
                cfg = new ModConfig();
            }
            return cfg;
        } catch (Exception e) {
            System.err.println("[StatusMod] Failed to load config, using defaults");
            e.printStackTrace();
            return new ModConfig();
        }
    }

    public void save() {
        try {
            File configDir = new File("config/statusmod");
            configDir.mkdirs();
            File f = new File(configDir, "config.json");
            FileWriter writer = new FileWriter(f);
            GSON.toJson(this, writer);
            writer.close();
        } catch (Exception e) {
            System.err.println("[StatusMod] Failed to save config");
            e.printStackTrace();
        }
    }
}
