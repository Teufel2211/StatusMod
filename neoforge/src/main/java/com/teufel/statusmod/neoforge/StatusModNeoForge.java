package com.teufel.statusmod.neoforge;

import com.teufel.statusmod.StatusMod;
import com.teufel.statusmod.command.BlockCommand;
import com.teufel.statusmod.command.ColorCommand;
import com.teufel.statusmod.command.ModInfoCommand;
import com.teufel.statusmod.command.SettingsCommand;
import com.teufel.statusmod.command.StatusCommand;
import com.teufel.statusmod.lifecycle.StatusLifecycle;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod(StatusMod.MOD_ID)
public final class StatusModNeoForge {
    public StatusModNeoForge() {
        StatusMod.init();
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        StatusCommand.register(event.getDispatcher());
        ModInfoCommand.register(event.getDispatcher());
        SettingsCommand.register(event.getDispatcher());
        BlockCommand.register(event.getDispatcher());
        ColorCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StatusLifecycle.onPlayerJoin(player.server, player);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        StatusLifecycle.onServerTick(event.getServer());
    }
}
