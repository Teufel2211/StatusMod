package com.teufel.fleet.neoforge;

import com.teufel.fleet.FleetCommand;
import com.teufel.fleet.FleetMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(FleetMod.MOD_ID)
public final class FleetModNeoForge {
    public FleetModNeoForge() {
        FleetMod.init();
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FleetCommand.register(event.getDispatcher());
    }
}
