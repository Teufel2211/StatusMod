package com.teufel.statusmod.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerSettings {
    public boolean brackets = false;
    public boolean beforeName = false; // default: behind name (false)
    public String status = "";
    public String color = "reset";
    public String fontStyle = "normal";
    // How many words the status should contain. Example: 2 => status is two words and the following token is the color.
    // Default: 1 (single-word status)
    public int statusWords = 1;

    // Cooldown and timed status
    public long lastStatusChangeAtMs = 0L;
    public long statusExpiresAtMs = 0L;

    // Status history (most recent last)
    public List<String> statusHistory = new ArrayList<>();

    // Per-world overrides
    public Map<String, String> statusByWorld = new HashMap<>();
    public Map<String, String> colorByWorld = new HashMap<>();
}
