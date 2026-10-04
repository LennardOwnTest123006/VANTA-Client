package dev.vanta.launcher.core.install;

import dev.vanta.launcher.LauncherVersion;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * What to install.
 *
 * @param minecraftVersion    Minecraft version id
 * @param fabricLoaderVersion Fabric Loader version
 * @param fabricApiVersion    Fabric API version
 * @param localClientJar      local VANTA client jar to install instead of the published release (development)
 * @param includeVantaClient  whether to install the VANTA client at all (false = plain Fabric instance)
 * @param includeAssets       whether to download assets (always true for real installs; false speeds up tests)
 */
public record InstallRequest(String minecraftVersion, String fabricLoaderVersion, String fabricApiVersion, Path localClientJar,
                             boolean includeVantaClient, boolean includeAssets) {

    public InstallRequest {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(fabricLoaderVersion, "fabricLoaderVersion");
        Objects.requireNonNull(fabricApiVersion, "fabricApiVersion");
    }

    /** @return the standard VANTA install (pinned versions, published client) */
    public static InstallRequest standard() {
        return new InstallRequest(LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER, LauncherVersion.FABRIC_API,
            null, true, true);
    }

    /**
     * Standard install with a local client jar override.
     *
     * @param clientJar jar path
     * @return request
     */
    public static InstallRequest withLocalClient(final Path clientJar) {
        return new InstallRequest(LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER, LauncherVersion.FABRIC_API,
            Objects.requireNonNull(clientJar, "clientJar"), true, true);
    }

    /** @return local jar override when set */
    public Optional<Path> localClientJarOverride() {
        return Optional.ofNullable(localClientJar);
    }
}
