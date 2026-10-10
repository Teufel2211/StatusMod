package com.teufel.fleet.forge;

import com.teufel.fleet.FleetCommand;
import com.teufel.fleet.FleetMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(FleetMod.MOD_ID)
public final class FleetModForge {
    public FleetModForge() {
        FleetMod.init();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FleetCommand.register(event.getDispatcher());
    }
}
