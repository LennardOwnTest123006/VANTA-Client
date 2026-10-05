package dev.vanta.launcher.core.paths;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.util.OsInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Directory layout of the launcher data directory.
 *
 * <pre>
 * &lt;data&gt;/
 * ├── instances/vanta-1.21.11/      game directory (mods/, config/, saves/, instance.json)
 * ├── libraries/                    Maven layout, shared between vanilla and Fabric
 * ├── assets/{indexes,objects,log_configs}/
 * ├── versions/&lt;id&gt;/&lt;id&gt;.json       version JSONs and the client jar; versions/vanta-client/&lt;v&gt;/ keeps rollback jars
 * ├── runtimes/                     Java runtimes installed by the launcher
 * ├── logs/                         launcher.log and game-&lt;timestamp&gt;.log
 * ├── cache/                        temporary downloads (updates/)
 * ├── settings.json
 * ├── accounts.dat                  encrypted account store
 * └── key.bin                       AES key for accounts.dat on non-Windows platforms (owner-only)
 * </pre>
 *
 * <p>Platform defaults: Windows {@code %APPDATA%\VANTA Launcher}, macOS
 * {@code ~/Library/Application Support/VANTA Launcher}, Linux {@code $XDG_DATA_HOME/vanta-launcher} or
 * {@code ~/.local/share/vanta-launcher}. The environment variable {@code VANTA_LAUNCHER_HOME} overrides all of them.</p>
 */
public final class LauncherPaths {

    /** Environment variable that overrides the data directory. */
    public static final String HOME_ENV = "VANTA_LAUNCHER_HOME";

    private final Path dataDir;
    private final String instanceId;

    /**
     * Creates paths rooted at a data directory with the default instance id.
     *
     * @param dataDir data directory
     */
    public LauncherPaths(final Path dataDir) {
        this(dataDir, LauncherVersion.INSTANCE_ID);
    }

    /**
     * Creates paths rooted at a data directory.
     *
     * @param dataDir    data directory
     * @param instanceId instance directory name
     */
    public LauncherPaths(final Path dataDir, final String instanceId) {
        this.dataDir = Objects.requireNonNull(dataDir, "dataDir").toAbsolutePath().normalize();
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
    }

    /**
     * Resolves the platform default data directory.
     *
     * @param os       host os
     * @param env      environment variables
     * @param userHome user home directory
     * @return paths
     */
    public static LauncherPaths detect(final OsInfo os, final Map<String, String> env, final Path userHome) {
        return new LauncherPaths(defaultDataDir(os, env, userHome));
    }

    /**
     * Resolves the platform default data directory for the host.
     *
     * @return paths
     */
    public static LauncherPaths detect() {
        return detect(OsInfo.detect(), System.getenv(), Path.of(System.getProperty("user.home")));
    }

    /**
     * Computes the platform default data directory.
     *
     * @param os       host os
     * @param env      environment variables
     * @param userHome user home directory
     * @return directory (not created)
     */
    public static Path defaultDataDir(final OsInfo os, final Map<String, String> env, final Path userHome) {
        final String override = env.get(HOME_ENV);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        if (os.isWindows()) {
            final String appData = env.get("APPDATA");
            final Path base = appData != null && !appData.isBlank() ? Path.of(appData) : userHome.resolve("AppData").resolve("Roaming");
            return base.resolve("VANTA Launcher");
        }
        if (os.isMac()) {
            return userHome.resolve("Library").resolve("Application Support").resolve("VANTA Launcher");
        }
        final String xdg = env.get("XDG_DATA_HOME");
        final Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : userHome.resolve(".local").resolve("share");
        return base.resolve("vanta-launcher");
    }

    /**
     * Locates the official Minecraft Launcher directory ({@code .minecraft}) when it exists, for read-only reuse of
     * already downloaded libraries and assets.
     *
     * @param os       host os
     * @param env      environment variables
     * @param userHome user home directory
     * @return directory when present
     */
    public static Optional<Path> officialMinecraftDir(final OsInfo os, final Map<String, String> env, final Path userHome) {
        final Path candidate = officialMinecraftDirCandidate(os, env, userHome);
        return Files.isDirectory(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    /**
     * Where the official Minecraft Launcher keeps its data on this platform, whether or not it exists:
     * Windows {@code %APPDATA%\.minecraft}, macOS {@code ~/Library/Application Support/minecraft}, Linux and others
     * {@code ~/.minecraft}.
     *
     * @param os       host os
     * @param env      environment variables
     * @param userHome user home directory
     * @return the platform default (not checked)
     */
    public static Path officialMinecraftDirCandidate(final OsInfo os, final Map<String, String> env, final Path userHome) {
        if (os.isWindows()) {
            final String appData = env.get("APPDATA");
            return (appData != null && !appData.isBlank() ? Path.of(appData) : userHome.resolve("AppData").resolve("Roaming"))
                .resolve(".minecraft");
        }
        if (os.isMac()) {
            return userHome.resolve("Library").resolve("Application Support").resolve("minecraft");
        }
        return userHome.resolve(".minecraft");
    }

    /** @return the data directory */
    public Path dataDir() {
        return dataDir;
    }

    /** @return the instance id (directory name under {@code instances/}) */
    public String instanceId() {
        return instanceId;
    }

    /** @return {@code instances/} */
    public Path instancesDir() {
        return dataDir.resolve("instances");
    }

    /** @return the game directory of the VANTA instance */
    public Path instanceDir() {
        return instancesDir().resolve(instanceId);
    }

    /** @return {@code instance.json} */
    public Path instanceFile() {
        return instanceDir().resolve("instance.json");
    }

    /** @return {@code <instance>/mods} */
    public Path modsDir() {
        return instanceDir().resolve("mods");
    }

    /** @return {@code <instance>/config} */
    public Path configDir() {
        return instanceDir().resolve("config");
    }

    /** @return {@code <instance>/shaderpacks} (Iris shader packs) */
    public Path shaderpacksDir() {
        return instanceDir().resolve("shaderpacks");
    }

    /** @return {@code <instance>/resourcepacks} */
    public Path resourcepacksDir() {
        return instanceDir().resolve("resourcepacks");
    }

    /** @return {@code <instance>/config/vanta}: files shared between the launcher and the VANTA Client */
    public Path vantaConfigDir() {
        return configDir().resolve("vanta");
    }

    /**
     * Modrinth content installed into the instance (mods, shader packs, resource packs). The VANTA Client's in-game
     * browser reads and writes the same file.
     *
     * @return {@code <instance>/config/vanta/modrinth.json}
     */
    public Path modrinthIndexFile() {
        return vantaConfigDir().resolve("modrinth.json");
    }

    /**
     * Written by the in-game "Restart game" button of the VANTA Client; the launcher deletes it and starts the game again.
     *
     * @return {@code <instance>/config/vanta/restart.request}
     */
    public Path restartRequestFile() {
        return vantaConfigDir().resolve("restart.request");
    }

    /** @return {@code <instance>/saves} */
    public Path savesDir() {
        return instanceDir().resolve("saves");
    }

    /** @return {@code <instance>/natives} (reserved for the {@code ${natives_directory}} placeholder) */
    public Path nativesDir() {
        return instanceDir().resolve("natives");
    }

    /** @return {@code libraries/} */
    public Path librariesDir() {
        return dataDir.resolve("libraries");
    }

    /** @return {@code assets/} */
    public Path assetsDir() {
        return dataDir.resolve("assets");
    }

    /** @return {@code assets/indexes} */
    public Path assetIndexesDir() {
        return assetsDir().resolve("indexes");
    }

    /** @return {@code assets/objects} */
    public Path assetObjectsDir() {
        return assetsDir().resolve("objects");
    }

    /** @return {@code assets/log_configs} */
    public Path logConfigsDir() {
        return assetsDir().resolve("log_configs");
    }

    /** @return {@code versions/} */
    public Path versionsDir() {
        return dataDir.resolve("versions");
    }

    /**
     * @param versionId version id
     * @return {@code versions/<id>}
     */
    public Path versionDir(final String versionId) {
        return versionsDir().resolve(versionId);
    }

    /**
     * @param versionId version id
     * @return {@code versions/<id>/<id>.json}
     */
    public Path versionJson(final String versionId) {
        return versionDir(versionId).resolve(versionId + ".json");
    }

    /**
     * @param versionId version id
     * @return {@code versions/<id>/<id>.jar}
     */
    public Path versionJar(final String versionId) {
        return versionDir(versionId).resolve(versionId + ".jar");
    }

    /** @return {@code versions/vanta-client} — kept client jars for rollback */
    public Path clientVersionsDir() {
        return versionsDir().resolve("vanta-client");
    }

    /** @return {@code runtimes/} */
    public Path runtimesDir() {
        return dataDir.resolve("runtimes");
    }

    /** @return {@code logs/} */
    public Path logsDir() {
        return dataDir.resolve("logs");
    }

    /** @return {@code logs/startup-error.txt}: written when the user interface cannot start */
    public Path startupErrorFile() {
        return logsDir().resolve("startup-error.txt");
    }

    /** @return {@code cache/} */
    public Path cacheDir() {
        return dataDir.resolve("cache");
    }

    /** @return {@code cache/updates} */
    public Path updatesCacheDir() {
        return cacheDir().resolve("updates");
    }

    /** @return {@code settings.json} */
    public Path settingsFile() {
        return dataDir.resolve("settings.json");
    }

    /** @return {@code accounts.dat} */
    public Path accountsFile() {
        return dataDir.resolve("accounts.dat");
    }

    /** @return {@code key.bin} */
    public Path keyFile() {
        return dataDir.resolve("key.bin");
    }

    /** @return all directories the launcher creates on start */
    public List<Path> allDirectories() {
        return List.of(dataDir, instancesDir(), instanceDir(), modsDir(), configDir(), savesDir(), librariesDir(),
            assetsDir(), assetIndexesDir(), assetObjectsDir(), logConfigsDir(), versionsDir(), clientVersionsDir(),
            runtimesDir(), logsDir(), cacheDir(), updatesCacheDir());
    }

    /**
     * Creates every directory of the layout.
     *
     * @throws IOException on failure
     */
    public void createDirectories() throws IOException {
        for (Path p : allDirectories()) {
            Files.createDirectories(p);
        }
    }

    @Override
    public String toString() {
        return dataDir.toString();
    }
}
