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
        // Direct calls (Mojang names, remapped per loader at build time).
        // Reflection above fails on intermediary/SRG runtimes (Fabric/Quilt
        // 1.21.11, Forge SRG), which would otherwise swallow private replies.
        try {
            // Console source has no player: vanilla logs console feedback, so
            // print it. Without this, console replies with broadcast=false
            // vanish silently (getPlayer() == null -> NPE -> swallowed, and
            // the broadcast fallback below is skipped).
            if (src.getPlayer() == null) {
                System.out.println("[StatusMod] " + msg.getString());
                return;
            }
            src.getPlayer().sendSystemMessage(msg);
            // Vanilla sendSuccess mit broadcast=true loggt zusätzlich
            // (Admin-Aktionen sichtbar); hier nachholen.
            if (broadcast) {
                System.out.println("[StatusMod] " + msg.getString());
            }
            return;
        } catch (Throwable ignored) {}
        // Remapped runtimes (Fabric Intermediary, Forge/NeoForge renames) do not
        // expose Mojang method names via reflection, so every attempt above fails.
        // Fall back to the console so server operators still see command output.
        // Mirror vanilla: only broadcast output (admin actions) lands in the log;
        // private replies (broadcast=false) stay private (may contain PII).
        if (broadcast) {
            System.out.println("[StatusMod] " + msg.getString());
        }
    }
}
