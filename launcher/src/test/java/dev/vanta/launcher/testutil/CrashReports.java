package dev.vanta.launcher.testutil;

/**
 * Crash reports as Minecraft writes them to {@code <game dir>/crash-reports/crash-<yyyy-MM-dd_HH.mm.ss>-client.txt}.
 */
public final class CrashReports {

    /**
     * What the real Minecraft 1.21.11 client reported with Smart FPS Booster 1.0.0 (Modrinth project smart-fps-booster)
     * in GitHub Actions runs 37623462001 and 37624307041 of mod-crash-repro.yml: the header, the description and the
     * exception lines exactly as reported (the stack frames in between are left out).
     */
    public static final String SMART_FPS_BOOSTER = """
        ---- Minecraft Crash Report ----

        Description: Initializing game

        java.lang.RuntimeException: Could not execute entrypoint stage 'client' due to errors, provided by 'smartfpsbooster' at 'com.smartclient.fpsbooster.SmartFPSBoosterClient'!
        Caused by: java.lang.NoSuchMethodError: 'void net.minecraft.class_304.<init>(java.lang.String, net.minecraft.class_3675$class_307, int, java.lang.String)'
        \tat knot//com.smartclient.fpsbooster.ui.KeybindManager.register(KeybindManager.java:21)
        \tat knot//com.smartclient.fpsbooster.SmartFPSBoosterClient.onInitializeClient(SmartFPSBoosterClient.java:43)
        """;

    private CrashReports() {
    }
}
