package com.teufel.statusmod;

import com.teufel.statusmod.command.StatusCommand;
import com.teufel.statusmod.command.SettingsCommand;
import com.teufel.statusmod.storage.SettingsStorage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class StatusMod implements ModInitializer {
    public static SettingsStorage storage;

    @Override
    public void onInitialize() {
        storage = new SettingsStorage();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            StatusCommand.register(dispatcher);
            SettingsCommand.register(dispatcher);
        });
        System.out.println("[StatusMod] initialized");
    }
}
