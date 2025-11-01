package com.teufel.statusmod.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.server.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class SettingsStorage {
    private final File file;
    private Map<String, PlayerSettings> map = new HashMap<>();
    private final Gson gson = new Gson();

    public SettingsStorage() {
        File configDir = new File("config/statusmod");
        configDir.mkdirs();
        file = new File(configDir, "players.json");
        load();
        ServerLifecycleEvents.SERVER_STOPPING.register((MinecraftServer server) -> save());
    }

    public synchronized PlayerSettings forPlayer(String uuid) {
        return map.computeIfAbsent(uuid, k -> new PlayerSettings());
    }

    public synchronized void put(String uuid, PlayerSettings s) {
        map.put(uuid, s);
        save();
    }

    public synchronized void load() {
        try {
            if (!file.exists()) return;
            Type t = new TypeToken<Map<String, PlayerSettings>>(){}.getType();
            FileReader fr = new FileReader(file);
            map = gson.fromJson(fr, t);
            fr.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void save() {
        try {
            FileWriter fw = new FileWriter(file);
            gson.toJson(map, fw);
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
