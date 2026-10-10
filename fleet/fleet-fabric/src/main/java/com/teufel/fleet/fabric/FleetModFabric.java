package com.teufel.fleet.fabric;

import com.teufel.fleet.FleetCommand;
import com.teufel.fleet.FleetMod;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public final class FleetModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FleetMod.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            FleetCommand.register(dispatcher);
        });
    }
}
