package dev.vanta.launcher.core.settings;

import java.util.List;
import java.util.Map;
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
 * @param releasesBaseUrl             base URL for {@code launcher-latest.json} / {@code client-latest.json}; empty = use
 *                                    {@value #RELEASES_BASE_URL_ENV} or {@link #DEFAULT_RELEASES_BASE_URL} (see
 *                                    {@link #effectiveReleasesBaseUrl(Map)})
 * @param autoUpdateCheck             check for updates at start
 * @param developerMode               enables development features (offline accounts with {@code VANTA_DEV_OFFLINE=1})
 * @param shareOfficialMinecraftFiles reuse verified libraries/assets from the official {@code .minecraft} directory
 * @param theme                       UI theme id
 * @param installPerformancePack      install the performance pack (Sodium, Lithium, FerriteCore, ImmediatelyFast,
 *                                    Entity Culling, Iris) from Modrinth with every install; {@code null} (a
 *                                    {@code settings.json} written before launcher 1.1.0) means {@code true}
 * @param installLocalAi              install the Local AI (llama-server runtime and model, see {@code core.ai}) with the
 *                                    first start and keep it complete with every install; the download itself always
 *                                    waits for the player's consent once ({@link #localAiAccepted}); {@code null} (a
 *                                    {@code settings.json} written before launcher 1.4.0) means {@code true}
 * @param localAiAccepted             whether the player agreed to the Local AI download (what, from where, how big,
 *                                    licences) once; {@code null} means {@code false}: ask first
 */
public record LauncherSettings(int schemaVersion, int memoryMb, String javaPath, List<String> jvmArgs, Resolution resolution,
                               boolean keepLauncherOpen, String msClientId, String releasesBaseUrl, boolean autoUpdateCheck,
                               boolean developerMode, boolean shareOfficialMinecraftFiles, String theme, Boolean installPerformancePack,
                               Boolean installLocalAi, Boolean localAiAccepted) {

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
    /** Environment variable that provides the releases base URL when the settings leave it empty. */
    public static final String RELEASES_BASE_URL_ENV = "VANTA_RELEASES_BASE_URL";
    /**
     * Built-in releases base URL: the {@code shared/releases/latest} directory of the VANTA repository on its default
     * branch, which holds {@code client-latest.json} and {@code launcher-latest.json} once a release is published.
     */
    public static final String DEFAULT_RELEASES_BASE_URL =
        "https://raw.githubusercontent.com/LennardOwnTest123006/VANTA-Client/HEAD/shared/releases/latest";

    public LauncherSettings {
        schemaVersion = schemaVersion <= 0 ? SCHEMA_VERSION : schemaVersion;
        memoryMb = memoryMb < MIN_MEMORY_MB ? DEFAULT_MEMORY_MB : memoryMb;
        javaPath = javaPath == null ? "" : javaPath.trim();
        jvmArgs = jvmArgs == null ? List.of() : List.copyOf(jvmArgs);
        msClientId = msClientId == null ? "" : msClientId.trim();
        releasesBaseUrl = releasesBaseUrl == null ? "" : releasesBaseUrl.trim();
        theme = theme == null || theme.isBlank() ? DEFAULT_THEME : theme;
        // Missing in settings.json files written before 1.1.0: the performance pack is on by default.
        installPerformancePack = installPerformancePack == null ? Boolean.TRUE : installPerformancePack;
        // Missing in settings.json files written before 1.4.0: the Local AI is offered by default, downloaded only after consent.
        installLocalAi = installLocalAi == null ? Boolean.TRUE : installLocalAi;
        localAiAccepted = localAiAccepted == null ? Boolean.FALSE : localAiAccepted;
    }

    /**
     * Settings without the Local AI components (offered, not yet accepted).
     *
     * @param schemaVersion               schema version
     * @param memoryMb                    heap in MiB
     * @param javaPath                    Java path
     * @param jvmArgs                     extra JVM arguments
     * @param resolution                  resolution
     * @param keepLauncherOpen            keep the launcher open
     * @param msClientId                  client id
     * @param releasesBaseUrl             releases base URL
     * @param autoUpdateCheck             check for updates
     * @param developerMode               developer mode
     * @param shareOfficialMinecraftFiles share official files
     * @param theme                       theme
     * @param installPerformancePack      install the performance pack
     */
    public LauncherSettings(final int schemaVersion, final int memoryMb, final String javaPath, final List<String> jvmArgs,
                            final Resolution resolution, final boolean keepLauncherOpen, final String msClientId, final String releasesBaseUrl,
                            final boolean autoUpdateCheck, final boolean developerMode, final boolean shareOfficialMinecraftFiles,
                            final String theme, final Boolean installPerformancePack) {
        this(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId, releasesBaseUrl, autoUpdateCheck,
            developerMode, shareOfficialMinecraftFiles, theme, installPerformancePack, Boolean.TRUE, Boolean.FALSE);
    }

    /**
     * Settings without the performance pack component (it is on) and without the Local AI components.
     *
     * @param schemaVersion               schema version
     * @param memoryMb                    heap in MiB
     * @param javaPath                    Java path
     * @param jvmArgs                     extra JVM arguments
     * @param resolution                  resolution
     * @param keepLauncherOpen            keep the launcher open
     * @param msClientId                  client id
     * @param releasesBaseUrl             releases base URL
     * @param autoUpdateCheck             check for updates
     * @param developerMode               developer mode
     * @param shareOfficialMinecraftFiles share official files
     * @param theme                       theme
     */
    public LauncherSettings(final int schemaVersion, final int memoryMb, final String javaPath, final List<String> jvmArgs,
                            final Resolution resolution, final boolean keepLauncherOpen, final String msClientId, final String releasesBaseUrl,
                            final boolean autoUpdateCheck, final boolean developerMode, final boolean shareOfficialMinecraftFiles,
                            final String theme) {
        this(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId, releasesBaseUrl, autoUpdateCheck,
            developerMode, shareOfficialMinecraftFiles, theme, Boolean.TRUE, Boolean.TRUE, Boolean.FALSE);
    }

    /**
     * Default settings for a machine with the given memory.
     *
     * @param totalRamMb physical memory in MiB, or {@code <= 0} when unknown
     * @return defaults
     */
    public static LauncherSettings defaults(final long totalRamMb) {
        return new LauncherSettings(SCHEMA_VERSION, defaultMemoryMb(totalRamMb), "", List.of(), null, false, "", "",
            true, false, true, DEFAULT_THEME, Boolean.TRUE, Boolean.TRUE, Boolean.FALSE);
    }

    /**
     * Heap default: half of the physical memory, clamped to [2048, 8192] MiB, but never more than half of the memory
     * (so on a PC with less than 4 GiB the heap is half the memory, at least {@value #MIN_MEMORY_MB} MiB: a heap that
     * leaves the system too little memory makes Windows swap, which stutters far worse than a smaller heap); 4096 when
     * memory is unknown.
     *
     * @param totalRamMb physical memory in MiB
     * @return heap in MiB
     */
    public static int defaultMemoryMb(final long totalRamMb) {
        if (totalRamMb <= 0) {
            return DEFAULT_MEMORY_MB;
        }
        final long half = totalRamMb / 2;
        final long clamped = Math.max(MIN_DEFAULT_MEMORY_MB, Math.min(MAX_DEFAULT_MEMORY_MB, half));
        return (int) Math.max(MIN_MEMORY_MB, Math.min(clamped, half));
    }

    /** @return explicit Java path when configured */
    public Optional<String> javaPathOverride() {
        return javaPath.isEmpty() ? Optional.empty() : Optional.of(javaPath);
    }

    /** @return resolution when configured */
    public Optional<Resolution> resolutionOverride() {
        return Optional.ofNullable(resolution);
    }

    /** @return whether installs add the performance pack (never null after construction) */
    public boolean performancePack() {
        return installPerformancePack;
    }

    /** @return whether the Local AI is installed automatically (offered at the first start, kept complete by installs) */
    public boolean localAiAutoInstall() {
        return installLocalAi;
    }

    /** @return whether the player agreed to the Local AI download once */
    public boolean localAiConsent() {
        return localAiAccepted;
    }

    /**
     * @return whether installs (PLAY, "Use with Minecraft Launcher", {@code --install}) include the Local AI step: automatic
     *     installation is on and the player agreed to the download
     */
    public boolean localAiWithInstalls() {
        return installLocalAi && localAiAccepted;
    }

    /**
     * @return whether {@code settings.json} stores an explicit releases base URL; when it does not, the environment
     *     variable or the built-in default applies (see {@link #effectiveReleasesBaseUrl(Map)})
     */
    public boolean hasReleasesBaseUrl() {
        return !releasesBaseUrl.isEmpty();
    }

    /**
     * The releases base URL in effect: the stored value when non-empty, else {@value #RELEASES_BASE_URL_ENV}, else
     * {@link #DEFAULT_RELEASES_BASE_URL}.
     *
     * @param env environment variables
     * @return URL in effect and its source
     */
    public ReleasesBaseUrl effectiveReleasesBaseUrl(final Map<String, String> env) {
        return ReleasesBaseUrl.resolve(releasesBaseUrl, env, DEFAULT_RELEASES_BASE_URL);
    }

    /** @return copy */
    public LauncherSettings withMemoryMb(final int value) {
        return new LauncherSettings(schemaVersion, value, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme, installPerformancePack, installLocalAi, localAiAccepted);
    }

    /** @return copy */
    public LauncherSettings withJavaPath(final String value) {
        return new LauncherSettings(schemaVersion, memoryMb, value, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme, installPerformancePack, installLocalAi, localAiAccepted);
    }

    /** @return copy */
    public LauncherSettings withJvmArgs(final List<String> value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, value, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme, installPerformancePack, installLocalAi, localAiAccepted);
    }

    /** @return copy */
    public LauncherSettings withResolution(final Resolution value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, value, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme, installPerformancePack, installLocalAi, localAiAccepted);
    }

    /** @return copy */
    public LauncherSettings withKeepLauncherOpen(final boolean value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, value, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme, installPerformancePack, installLocalAi, localAiAccepted);
    }

    /** @return copy */
    public LauncherSettings withMsClientId(final String value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, value,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme, installPerformancePack, installLocalAi, localAiAccepted);
    }

    /** @return copy */
    public LauncherSettings withReleasesBaseUrl(final String value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            value, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme, installPerformancePack, installLocalAi, localAiAccepted);
    }

    /** @return copy */
    public LauncherSettings withAutoUpdateCheck(final boolean value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, value, developerMode, shareOfficialMinecraftFiles, theme, installPerformancePack, installLocalAi, localAiAccepted);
    }

    /** @return copy */
    public LauncherSettings withDeveloperMode(final boolean value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, value, shareOfficialMinecraftFiles, theme, installPerformancePack, installLocalAi, localAiAccepted);
    }

    /** @return copy */
    public LauncherSettings withShareOfficialMinecraftFiles(final boolean value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, value, theme, installPerformancePack, installLocalAi, localAiAccepted);
    }

    /** @return copy */
    public LauncherSettings withTheme(final String value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, value, installPerformancePack, installLocalAi, localAiAccepted);
    }

    /** @return copy */
    public LauncherSettings withInstallPerformancePack(final boolean value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme, value, installLocalAi, localAiAccepted);
    }

    /** @return copy */
    public LauncherSettings withInstallLocalAi(final boolean value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme, installPerformancePack, value, localAiAccepted);
    }

    /** @return copy */
    public LauncherSettings withLocalAiAccepted(final boolean value) {
        return new LauncherSettings(schemaVersion, memoryMb, javaPath, jvmArgs, resolution, keepLauncherOpen, msClientId,
            releasesBaseUrl, autoUpdateCheck, developerMode, shareOfficialMinecraftFiles, theme, installPerformancePack, installLocalAi, value);
    }
}
