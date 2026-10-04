package dev.vanta.launcher.core.model;

import java.util.Objects;

/**
 * {@code instances/vanta-1.21.11/instance.json}: what is installed in the instance.
 *
 * @param schemaVersion       schema version (1)
 * @param instanceId          instance id, also the {@code --version} value
 * @param minecraftVersion    installed Minecraft version
 * @param fabricLoaderVersion installed Fabric Loader version
 * @param fabricApiVersion    installed Fabric API version
 * @param vantaClientVersion  installed VANTA client version ({@code "dev"} for a local jar override, empty when absent)
 * @param vantaClientJar      file name of the VANTA client jar in {@code mods/} (empty when absent)
 * @param vanillaVersionId    id of the vanilla version JSON under {@code versions/}
 * @param fabricProfileId     id of the Fabric profile JSON under {@code versions/}
 * @param mainClass           main class to launch
 * @param assetIndexId        asset index id
 * @param javaMajor           required Java major
 * @param installedAt         ISO-8601 instant of the last successful install
 */
public record InstanceInfo(int schemaVersion, String instanceId, String minecraftVersion, String fabricLoaderVersion,
                           String fabricApiVersion, String vantaClientVersion, String vantaClientJar,
                           String vanillaVersionId, String fabricProfileId, String mainClass, String assetIndexId,
                           int javaMajor, String installedAt) {

    /** Current schema version. */
    public static final int SCHEMA_VERSION = 1;

    public InstanceInfo {
        Objects.requireNonNull(instanceId, "instanceId");
        vantaClientVersion = vantaClientVersion == null ? "" : vantaClientVersion;
        vantaClientJar = vantaClientJar == null ? "" : vantaClientJar;
    }

    /** @return whether a VANTA client jar is installed in the instance */
    public boolean hasVantaClient() {
        return !vantaClientJar.isEmpty();
    }

    /**
     * Returns a copy with a different VANTA client.
     *
     * @param version client version
     * @param jarName jar file name in {@code mods/}
     * @return copy
     */
    public InstanceInfo withVantaClient(final String version, final String jarName) {
        return new InstanceInfo(schemaVersion, instanceId, minecraftVersion, fabricLoaderVersion, fabricApiVersion,
            version, jarName, vanillaVersionId, fabricProfileId, mainClass, assetIndexId, javaMajor, installedAt);
    }
}
