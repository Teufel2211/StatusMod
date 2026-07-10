package com.teufel.statusmod.fabric;

import net.fabricmc.api.ModInitializer;
import com.teufel.statusmod.StatusMod;

public class StatusModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("[StatusMod] Fabric Initializer starting...");
        try {
            StatusMod.init();
            System.out.println("[StatusMod] Successfully initialized!");
        } catch (Throwable e) {
            System.err.println("[StatusMod] Initialization failed (but mod loaded): " + e.getMessage());
            e.printStackTrace();
        }
    }
}