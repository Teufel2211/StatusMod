package com.teufel.statusmod;

import com.teufel.statusmod.storage.AuditLogger;
import com.teufel.statusmod.storage.BlockedPlayers;
import com.teufel.statusmod.storage.CustomPresets;
import com.teufel.statusmod.storage.ModConfig;
import com.teufel.statusmod.storage.MutedPlayers;
import com.teufel.statusmod.storage.SettingsStorage;
import com.teufel.statusmod.registry.ModRegistries;
import com.teufel.statusmod.network.ModNetworking;
import com.teufel.statusmod.platform.Platform;
import com.teufel.statusmod.platform.PlatformServices;

public final class StatusMod {
    public static final String MOD_ID = "statusmod";
    public static volatile ModConfig config;
    public static volatile SettingsStorage storage;
    public static volatile BlockedPlayers blockedPlayers;
    public static volatile MutedPlayers mutedPlayers;
    public static volatile CustomPresets customPresets;

    private StatusMod() {}

    public static void init() {
        Platform platform = PlatformServices.getPlatform();
        if (config == null) {
            config = ModConfig.load();
        }
        if (storage == null) {
            storage = new SettingsStorage();
        }
        if (blockedPlayers == null) {
            blockedPlayers = new BlockedPlayers();
        }
        if (mutedPlayers == null) {
            mutedPlayers = new MutedPlayers();
        }
        if (customPresets == null) {
            customPresets = new CustomPresets();
        }
        AuditLogger.init();

        ModRegistries.init();
        ModNetworking.init();

        System.out.println("[StatusMod] Initializing on " + platform.getName());
        platform.registerItem("status_token");
        platform.registerBlock("status_block");
    }

    public static ModConfig getConfig() {
        return config;
    }

    public static SettingsStorage getStorage() {
        return storage;
    }

    public static BlockedPlayers getBlockedPlayers() {
        return blockedPlayers;
    }

    public static MutedPlayers getMutedPlayers() {
        return mutedPlayers;
    }

    public static CustomPresets getCustomPresets() {
        return customPresets;
    }
}
