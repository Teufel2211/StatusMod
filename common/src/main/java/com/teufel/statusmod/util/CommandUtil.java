package com.teufel.statusmod.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.function.Supplier;

public final class CommandUtil {
    private CommandUtil() {}

    public static void sendSuccess(CommandSourceStack src, Component msg, boolean broadcast) {
        if (src == null || msg == null) return;
        try {
            Method m = src.getClass().getMethod("sendSuccess", Supplier.class, boolean.class);
            m.invoke(src, (Supplier<Component>) () -> msg, broadcast);
            return;
        } catch (Throwable ignored) {}
        try {
            Method m = src.getClass().getMethod("sendSuccess", Component.class, boolean.class);
            m.invoke(src, msg, broadcast);
            return;
        } catch (Throwable ignored) {}
        try {
            Method m = src.getClass().getMethod("sendSystemMessage", Component.class);
            m.invoke(src, msg);
            return;
        } catch (Throwable ignored) {}
        // Remapped runtimes (Fabric Intermediary, Forge/NeoForge renames) do not
        // expose Mojang method names via reflection, so every attempt above fails.
        // Fall back to the console so server operators still see command output.
        System.out.println("[StatusMod] " + msg.getString());
    }
}
