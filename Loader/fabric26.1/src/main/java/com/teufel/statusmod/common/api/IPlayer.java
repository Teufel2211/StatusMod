package com.teufel.statusmod.common.api;

/**
 * Abstraction for a player across Minecraft versions.
 * Core code must only use this interface.
 */
public interface IPlayer {
    String getUuid();
    String getName();
    void sendMessage(String message);
}
