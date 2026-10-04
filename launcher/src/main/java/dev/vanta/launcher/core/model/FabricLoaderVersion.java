package dev.vanta.launcher.core.model;

/**
 * One entry of Fabric meta {@code /v2/versions/loader/{game}}.
 *
 * @param loader       loader info
 * @param intermediary intermediary mappings info
 */
public record FabricLoaderVersion(Loader loader, Intermediary intermediary) {

    /**
     * Loader info.
     *
     * @param separator version separator
     * @param build     build number
     * @param maven     Maven coordinate
     * @param version   version string
     * @param stable    stability flag
     */
    public record Loader(String separator, int build, String maven, String version, boolean stable) {
    }

    /**
     * Intermediary info.
     *
     * @param maven   Maven coordinate
     * @param version game version
     * @param stable  stability flag
     */
    public record Intermediary(String maven, String version, boolean stable) {
    }
}
