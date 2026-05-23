package com.teufel.statusmod;

import com.teufel.statusmod.command.BlockCommand;
import com.teufel.statusmod.command.ColorCommand;
import com.teufel.statusmod.command.SettingsCommand;
import com.teufel.statusmod.command.StatusCommand;
import com.teufel.statusmod.event.PlayerLoginHandler;
import com.teufel.statusmod.storage.BlockedPlayers;
import com.teufel.statusmod.storage.ModConfig;
import com.teufel.statusmod.storage.SettingsStorage;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@Mod("status-mod")
@Mod.EventBusSubscriber(modid = "status-mod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StatusMod {
    public static SettingsStorage storage;
    public static BlockedPlayers blockedPlayers;
    /** Global configuration loaded from config/statusmod/config.json */
    public static ModConfig config;

    public StatusMod() {
        // load global configuration (creating file with defaults if necessary)
        config = ModConfig.load();
        System.out.println("[StatusMod] config loaded: adminOpLevel=" + config.adminOpLevel +
                           ", adminOverrides=" + config.enableAdminOverrides +
                           ", defaultColor=" + config.defaultColor +
                           ", statusReapplyTicks=" + config.statusReapplyTicks);

        storage = new SettingsStorage();
        blockedPlayers = new BlockedPlayers();

        // Register player login event to restore statuses
        PlayerLoginHandler.register();
        System.out.println("[StatusMod] initialized");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        StatusCommand.register(event.getDispatcher());
        // register mod info command (website/issues)
        com.teufel.statusmod.command.ModInfoCommand.register(event.getDispatcher());
        SettingsCommand.register(event.getDispatcher());
        // register block/unblock commands
        BlockCommand.register(event.getDispatcher());
        // register color command
        ColorCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (storage != null) {
            storage.save();
        }
    }
}
