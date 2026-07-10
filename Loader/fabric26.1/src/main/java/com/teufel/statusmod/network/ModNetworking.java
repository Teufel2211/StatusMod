package com.teufel.statusmod.network;

import net.minecraft.resources.Identifier;

public final class ModNetworking {
    public static final Identifier PING = Identifier.fromNamespaceAndPath("statusmod", "ping");

    private ModNetworking() {}

    public static void init() {
        // Placeholder packet registration kept as a no-op for the current target.
    }
}
