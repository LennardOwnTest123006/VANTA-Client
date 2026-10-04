package dev.vanta.core;

/**
 * Build-time constants describing the VANTA Client release and the exact Minecraft toolchain it targets.
 * <p>
 * These values are the single source of truth for every version label shown in the client UI and must be
 * kept in sync with {@code client/gradle.properties} and {@code shared/releases/}.
 */
public final class VantaVersion {
    /** VANTA Client semantic version. */
    public static final String CLIENT = "1.0.0";
    /** Exact Minecraft Java Edition version this client is built for. */
    public static final String MINECRAFT = "1.21.11";
    /** Fabric Loader version the client is built against. */
    public static final String FABRIC_LOADER = "0.19.5";
    /** Fabric API version the client is built against. */
    public static final String FABRIC_API = "0.141.6+1.21.11";
    /** Required Java major version. */
    public static final int JAVA = 21;
    /** Human readable brand name. */
    public static final String BRAND = "VANTA Client";

    private VantaVersion() {
    }

    /** Three-line label used by the main menu and the launcher: brand, Minecraft version, loader. */
    public static String[] menuLabel() {
        return new String[] {BRAND + " " + CLIENT, "Minecraft " + MINECRAFT, "Fabric " + FABRIC_LOADER};
    }
}
