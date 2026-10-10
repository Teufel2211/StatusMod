package com.teufel.fleet;

public final class FleetMod {
    public static final String MOD_ID = "statusmodfleet";
    public static final String BUILD = "fleet1";

    private FleetMod() {}

    public static void init() {
        System.out.println("[Fleet] Initializing fleet config sync (build " + BUILD + ")");
        FleetManager.start();
    }
}
