package com.teufel.statusmod.common;

import com.teufel.statusmod.common.api.IPlatform;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Loads available `IPlatform` implementations via ServiceLoader and initializes `Compat`.
 */
public final class PlatformLoader {
    private PlatformLoader() {}

    public static void loadAndInit() {
        ServiceLoader<IPlatform> loader = ServiceLoader.load(IPlatform.class);
        IPlatform best = null;
        for (IPlatform p : loader) {
            if (best == null) { best = p; continue; }
            int bp = best.priority();
            int pp = p.priority();
            if (pp > bp) best = p;
        }
        if (best != null) {
            Compat.init(best);
        } else {
            throw new IllegalStateException("No IPlatform implementation found on classpath");
        }
    }
}
