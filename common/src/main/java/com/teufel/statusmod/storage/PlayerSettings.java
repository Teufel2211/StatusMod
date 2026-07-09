package com.teufel.statusmod.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerSettings {
    public boolean brackets = false;
    public boolean beforeName = false;
    public String status = "";
    public String color = "reset";
    public String fontStyle = "normal";
    public int statusWords = 1;

    public long lastStatusChangeAtMs = 0L;
    public long statusExpiresAtMs = 0L;

    public List<String> statusHistory = new ArrayList<>();
    public Map<String, String> statusByWorld = new HashMap<>();
    public Map<String, String> colorByWorld = new HashMap<>();
}
