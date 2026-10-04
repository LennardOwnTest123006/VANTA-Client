package dev.vanta.launcher.core.install;

/**
 * Steps of an installation in execution order.
 */
public enum InstallStep {
    /** Fetch the Mojang version manifest. */
    MANIFEST("Fetching version manifest"),
    /** Fetch and verify the version JSON. */
    VERSION_JSON("Fetching Minecraft version data"),
    /** Download the Minecraft client jar. */
    CLIENT_JAR("Downloading Minecraft client"),
    /** Download vanilla libraries. */
    LIBRARIES("Downloading libraries"),
    /** Download the asset index and objects. */
    ASSETS("Downloading assets"),
    /** Fetch the Fabric Loader profile. */
    FABRIC_PROFILE("Fetching Fabric Loader profile"),
    /** Download Fabric Loader libraries. */
    FABRIC_LIBRARIES("Downloading Fabric Loader"),
    /** Download Fabric API. */
    FABRIC_API("Downloading Fabric API"),
    /** Download the VANTA client mod. */
    VANTA_CLIENT("Installing VANTA Client"),
    /** Write instance metadata. */
    FINALIZE("Finishing installation");

    private final String label;

    InstallStep(final String label) {
        this.label = label;
    }

    /** @return human readable label */
    public String label() {
        return label;
    }
}
