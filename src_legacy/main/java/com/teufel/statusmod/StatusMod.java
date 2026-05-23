package com.teufel.statusmod;

import com.teufel.statusmod.command.StatusCommand;
import com.teufel.statusmod.command.SettingsCommand;
import com.teufel.statusmod.command.BlockCommand;
import com.teufel.statusmod.command.ColorCommand;
import com.teufel.statusmod.event.PlayerLoginHandler;
import com.teufel.statusmod.storage.SettingsStorage;
import com.teufel.statusmod.storage.BlockedPlayers;
import com.teufel.statusmod.storage.ModConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class StatusMod implements ModInitializer {
    public static SettingsStorage storage;
    public static BlockedPlayers blockedPlayers;
    /** Global configuration loaded from config/statusmod/config.json */
    public static ModConfig config;

    @Override
    public void onInitialize() {
        // load global configuration (creating file with defaults if necessary)
        config = ModConfig.load();
        System.out.println("[StatusMod] config loaded: adminOpLevel=" + config.adminOpLevel +
                           ", adminOverrides=" + config.enableAdminOverrides +
                           ", defaultColor=" + config.defaultColor +
                           ", statusReapplyTicks=" + config.statusReapplyTicks);

        storage = new SettingsStorage();
        blockedPlayers = new BlockedPlayers();

        // Register player login event to restore statuses
        PlayerLoginHandler.register();
        
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            StatusCommand.register(dispatcher);
            // register mod info command (website/issues)
            com.teufel.statusmod.command.ModInfoCommand.register(dispatcher);
            SettingsCommand.register(dispatcher);
            // register block/unblock commands
            BlockCommand.register(dispatcher);
            // register color command
            ColorCommand.register(dispatcher);
        });
        System.out.println("[StatusMod] initialized");
    }
}
