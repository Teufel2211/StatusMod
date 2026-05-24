package com.teufel.statusmod;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "status-mod", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class StatusModClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Client-specific setup hook (currently unused).
    }
}
