package com.teufel.statusmod;

import com.teufel.statusmod.storage.BlockedPlayers;
import com.teufel.statusmod.storage.ModConfig;
import com.teufel.statusmod.storage.SettingsStorage;
import com.teufel.statusmod.network.ModNetworking;
import com.teufel.statusmod.registry.ModRegistries;
import com.teufel.statusmod.platform.PlatformServices;
import com.teufel.statusmod.platform.Platform;

public final class StatusMod {
    public static ModConfig config;
    public static SettingsStorage storage;
    public static BlockedPlayers blockedPlayers;

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

        try {
            ModRegistries.init();
        } catch (Throwable e) {
            System.err.println("[StatusMod] Registry init failed: " + e.getMessage());
        }

        try {
            ModNetworking.init();
        } catch (Throwable e) {
            System.err.println("[StatusMod] Networking init failed: " + e.getMessage());
        }

        System.out.println("[StatusMod] Initialized on " + platform.getName());
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
}