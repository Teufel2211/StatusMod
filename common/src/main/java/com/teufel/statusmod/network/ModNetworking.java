package com.teufel.statusmod.network;

import dev.architectury.networking.NetworkManager;
import net.minecraft.resources.ResourceLocation;

public final class ModNetworking {
    public static final ResourceLocation PING = ResourceLocation.fromNamespaceAndPath("statusmod", "ping");

    private ModNetworking() {}

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PING, (buf, context) -> {
            // Placeholder packet; concrete message routing stays in common.
        });
    }
}
