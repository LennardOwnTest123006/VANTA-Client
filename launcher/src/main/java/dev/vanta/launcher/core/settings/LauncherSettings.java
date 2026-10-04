package dev.vanta.launcher.core.settings;

import java.util.List;
import java.util.Optional;

/**
 * Persistent launcher settings ({@code settings.json}).
 *
 * @param schemaVersion               schema version (1)
 * @param memoryMb                    maximum heap for the game in MiB
 * @param javaPath                    explicit Java executable or home directory; empty = auto-detect
 * @param jvmArgs                     extra JVM arguments
 * @param resolution                  initial window size; {@code null} = game default
 * @param keepLauncherOpen            keep the launcher window open while the game runs
 * @param msClientId                  Microsoft Entra application (client) id; empty = use {@code VANTA_MS_CLIENT_ID} env
 * @param releasesBaseUrl             base URL for {@code launcher-latest.json} / {@code client-latest.json}; empty = not configured
 * @param autoUpdateCheck             check for updates at start
 * @param developerMode               enables development features (offline accounts with {@code VANTA_DEV_OFFLINE=1})
 * @param shareOfficialMinecraftFiles reuse verified libraries/assets from the official {@code .minecraft} directory
 * @param theme                       UI theme id
 */
public record LauncherSettings(int schemaVersion, int memoryMb, String javaPath, List<String> jvmArgs, Resolution resolution,
                               boolean keepLauncherOpen, String msClientId, String releasesBaseUrl, boolean autoUpdateCheck,
                               boolean developerMode, boolean shareOfficialMinecraftFiles, String theme) {

    /** Current schema version. */
    public static final int SCHEMA_VERSION = 1;
    /** Default heap when the machine memory is unknown. */
    public static final int DEFAULT_MEMORY_MB = 4096;
    /** Upper bound for the automatic heap default. */
    public static final int MAX_DEFAULT_MEMORY_MB = 8192;
    /** Lower bound for the automatic heap default. */
    public static final int MIN_DEFAULT_MEMORY_MB = 2048;
    /** Smallest heap the UI allows. */
    public static final int MIN_MEMORY_MB = 1024;
    /** Default theme id. */
    public static final String DEFAULT_THEME = "vanta-dark";
    /** Environment variable holding the Microsoft client id. */
    public static final String MS_CLIENT_ID_ENV = "VANTA_MS_CLIENT_ID";

    public LauncherSettings {
        schemaVersion = schemaVersion <= 0 ? SCHEMA_VERSION : schemaVersion;
        memoryMb = memoryMb < MIN_MEMORY_MB ? DEFAULT_MEMORY_MB : memoryMb;
        javaPath = javaPath == null ? "" : javaPath.trim();
        jvmArgs = jvmArgs == null ? List.of() : List.copyOf(jvmArgs);
        msClientId = msClientId == null ? "" : msClientId.trim();
        releasesBaseUrl = releasesBaseUrl == null ? "" : releasesBaseUrl.trim();
        theme = theme == null || theme.isBlank() ? DEFAULT_THEME : theme;
    }

    /**
     * Default settings for a machine with the given memory.
     *
     * @param totalRamMb physical memory in MiB, or {@code <= 0} when unknown
     * @return defaults
     */
    public static LauncherSettings defaults(final long totalRamMb) {
        return new LauncherSettings(SCHEMA_VERSION, defaultMemoryMb(totalRamMb), "", List.of(), null, false, "", "",
            true, false, true, DEFAULT_THEME);
    }

    /**
     * Heap default: half of the physical memory, clamped to [2048, 8192] MiB; 4096 when memory is unknown.
     *
     * @param totalRamMb physical memory in MiB
     * @return heap in MiB
     */
    public static int defaultMemoryMb(final long totalRamMb) {
        if (totalRamMb <= 0) {
            return DEFAULT_MEMORY_MB;
        }
        final long half = totalRamMb / 2;
        return (int) Math.max(MIN_DEFAULT_MEMORY_MB, Math.min(MAX_DEFAULT_MEMORY_MB, half));
    }

    /** @return explicit Java path when configured */
    public Optional<String> javaPathOverride() {
        return javaPath.isEmpty() ? Optional.empty() : Optional.of(javaPath);
    }

    /** @return resolution when configured */
    public Optional<Resolution> resolutionOverride() {
        return Optional.ofNullable(resolution);
    }

    /** @return whether a releases base URL is configured */
    public boolean hasReleasesBaseUrl() {
        return !releasesBaseUrl.isEmpty();
    }

    /** @return copy */
    public LauncherSettings withMemoryMb(final int value) {
        return new LauncherSettings(schemaVersion, value, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme);
    }

    /** @return copy */
    public LauncherSettings withJavaPath(final String value) {
        return new LauncherSettings(schemaVersion, memoryMb, value, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme);
    }

    /** @return copy */
    public LauncherSettings withJvmArgs(final List<String> value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, value, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme);
    }

    /** @return copy */
    public LauncherSettings withResolution(final Resolution value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, value, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme);
    }

    /** @return copy */
    public LauncherSettings withKeepLauncherOpen(final boolean value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, value, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme);
    }

    /** @return copy */
    public LauncherSettings withMsClientId(final String value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, value,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme);
    }

    /** @return copy */
    public LauncherSettings withReleasesBaseUrl(final String value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            value, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme);
    }

    /** @return copy */
    public LauncherSettings withAutoUpdateCheck(final boolean value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, value, developerMode, shareOfficialMinecraftFiles, theme);
    }

    /** @return copy */
    public LauncherSettings withDeveloperMode(final boolean value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, value, shareOfficialMinecraftFiles, theme);
    }

    /** @return copy */
    public LauncherSettings withShareOfficialMinecraftFiles(final boolean value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, value, theme);
    }

    /** @return copy */
    public LauncherSettings withTheme(final String value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, value);
    }
}
