package com.teufel.statusmod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "status-mod", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class StatusModClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Client-specific setup hook (currently unused).
    }
}
