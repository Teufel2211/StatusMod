package com.example.statusmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class StatusMod implements ModInitializer {
    public static SettingsStorage storage;

    @Override
    public void onInitialize() {
        storage = new SettingsStorage(); // lädt / persistiert JSON
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            com.example.statusmod.command.StatusCommand.register(dispatcher);
            com.example.statusmod.command.SettingsCommand.register(dispatcher);
        });
        System.out.println("[StatusMod] initialized");
    }
}
