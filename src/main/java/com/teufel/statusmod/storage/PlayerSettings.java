package com.teufel.statusmod.storage;

public class PlayerSettings {
    public boolean brackets = false;
    public boolean beforeName = false; // default: behind name (false)
    public String status = "";
    public String color = "reset";
    // How many words the status should contain. Example: 2 => status is two words and the following token is the color.
    // Default: 1 (single-word status)
    public int statusWords = 1;
}
