package com.teufel.statusmod.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
        // If a player does not yet have settings, create defaults, persist them immediately
        PlayerSettings s = map.get(uuid);
        if (s == null) {
            s = new PlayerSettings();
            map.put(uuid, s);
            save();
        }
        return s;
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
            if (map == null) map = new HashMap<>();
            fr.close();
            // Migration: ensure fields have sensible defaults when older JSON is missing fields
            boolean migrated = false;
            for (Map.Entry<String, PlayerSettings> e : map.entrySet()) {
                PlayerSettings ps = e.getValue();
                if (ps == null) continue;
                if (ps.statusWords <= 0) {
                    ps.statusWords = 1;
                    migrated = true;
                }
                if (ps.status == null) ps.status = "";
                if (ps.color == null || ps.color.isEmpty()) ps.color = "reset";
            }
            if (migrated) save();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void save() {
        try {
            // ensure parent directory exists
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (map == null) map = new HashMap<>();
            FileWriter fw = new FileWriter(file);
            gson.toJson(map, fw);
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
