package dev.vanta.launcher.core.install;

import dev.vanta.launcher.LauncherVersion;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * What to install.
 *
 * @param minecraftVersion       Minecraft version id
 * @param fabricLoaderVersion    Fabric Loader version
 * @param fabricApiVersion       Fabric API version
 * @param localClientJar         local VANTA client jar to install instead of the published release (development)
 * @param includeVantaClient     whether to install the VANTA client at all (false = plain Fabric instance)
 * @param includeAssets          whether to download assets (always true for real installs; false speeds up tests)
 * @param includePerformancePack whether to install the performance pack from Modrinth (Settings: "Install the
 *                               performance pack"); it never makes the install fail
 */
public record InstallRequest(String minecraftVersion, String fabricLoaderVersion, String fabricApiVersion, Path localClientJar,
                             boolean includeVantaClient, boolean includeAssets, boolean includePerformancePack) {

    public InstallRequest {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(fabricLoaderVersion, "fabricLoaderVersion");
        Objects.requireNonNull(fabricApiVersion, "fabricApiVersion");
    }

    /**
     * A request without the performance pack.
     *
     * @param minecraftVersion    Minecraft version id
     * @param fabricLoaderVersion Fabric Loader version
     * @param fabricApiVersion    Fabric API version
     * @param localClientJar      local client jar (may be null)
     * @param includeVantaClient  whether to install the VANTA client
     * @param includeAssets       whether to download assets
     */
    public InstallRequest(final String minecraftVersion, final String fabricLoaderVersion, final String fabricApiVersion,
                          final Path localClientJar, final boolean includeVantaClient, final boolean includeAssets) {
        this(minecraftVersion, fabricLoaderVersion, fabricApiVersion, localClientJar, includeVantaClient, includeAssets, false);
    }

    /** @return the standard VANTA install (pinned versions, published client, performance pack) */
    public static InstallRequest standard() {
        return new InstallRequest(LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER, LauncherVersion.FABRIC_API,
            null, true, true, true);
    }

    /**
     * Standard install with a local client jar override.
     *
     * @param clientJar jar path
     * @return request
     */
    public static InstallRequest withLocalClient(final Path clientJar) {
        return new InstallRequest(LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER, LauncherVersion.FABRIC_API,
            Objects.requireNonNull(clientJar, "clientJar"), true, true, true);
    }

    /**
     * @param include whether to install the performance pack
     * @return copy
     */
    public InstallRequest withPerformancePack(final boolean include) {
        return new InstallRequest(minecraftVersion, fabricLoaderVersion, fabricApiVersion, localClientJar, includeVantaClient,
            includeAssets, include);
    }

    /** @return local jar override when set */
    public Optional<Path> localClientJarOverride() {
        return Optional.ofNullable(localClientJar);
    }
}
