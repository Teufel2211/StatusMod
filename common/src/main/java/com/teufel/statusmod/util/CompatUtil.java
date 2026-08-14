package com.teufel.statusmod.util;

import net.minecraft.server.level.ServerPlayer;

public final class CompatUtil {
    private CompatUtil() {}

    public static String getWorldKey(ServerPlayer player) {
        if (player == null) return null;
        try {
            Object dim = player.level().dimension();
            return dim == null ? null : String.valueOf(dim);
        } catch (Throwable ignored) {}
        return null;
    }
}
