package com.teufel.statusmod;

import com.teufel.statusmod.storage.AuditLogger;
import com.teufel.statusmod.storage.BlockedPlayers;
import com.teufel.statusmod.storage.CustomPresets;
import com.teufel.statusmod.storage.ModConfig;
import com.teufel.statusmod.storage.MutedPlayers;
import com.teufel.statusmod.storage.SettingsStorage;
import com.teufel.statusmod.platform.Platform;
import com.teufel.statusmod.platform.PlatformServices;
import com.teufel.statusmod.setup.SetupManager;
import com.teufel.statusmod.sync.SyncManager;

import java.util.concurrent.atomic.AtomicBoolean;

public final class StatusMod {
    public static final String MOD_ID = "statusmod";
    /** Bump on every GUI-fix round so running builds are identifiable via log + /status version. */
    public static final String BUILD = "fleetmove1";
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    public static volatile ModConfig config;
    public static volatile SettingsStorage storage;
    public static volatile BlockedPlayers blockedPlayers;
    public static volatile MutedPlayers mutedPlayers;
    public static volatile CustomPresets customPresets;

    private StatusMod() {}

    public static void init() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }

        Platform platform = PlatformServices.getPlatform();
        config = ModConfig.load();
        storage = new SettingsStorage();
        blockedPlayers = new BlockedPlayers();
        mutedPlayers = new MutedPlayers();
        customPresets = new CustomPresets();
        AuditLogger.init();

        System.out.println("[StatusMod] Initializing on " + platform.getName() + " (build " + BUILD + ")");
        if (com.teufel.statusmod.util.BedrockUtil.isFloodgatePresent()) {
            System.out.println("[StatusMod] Floodgate detected - Bedrock support enabled.");
            if (isMc26_3()) {
                System.out.println("[StatusMod] WARNING: Bedrock is not supported on MC 26.3 yet (no compatible Geyser release). Bedrock players may fail to join or see broken output.");
            }
        }

        if (platform.isDedicatedServer()) {
            SetupManager.runIfNeeded();
            SyncManager.start();
        }
    }

    public static ModConfig getConfig() {
        return config;
    }

    /** True on MC 26.3 (direct refs: old name reflection failed on intermediary). */
    private static boolean isMc26_3() {
        try {
            return net.minecraft.SharedConstants.getCurrentVersion().name().trim().startsWith("26.3");
        } catch (Throwable ignored) {
            return false;
        }
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
