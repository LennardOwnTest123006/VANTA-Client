package dev.vanta.launcher.core;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.auth.AccountStore;
import dev.vanta.launcher.core.auth.MicrosoftAuthService;
import dev.vanta.launcher.core.install.FabricApiService;
import dev.vanta.launcher.core.install.FabricService;
import dev.vanta.launcher.core.install.Installer;
import dev.vanta.launcher.core.install.MojangService;
import dev.vanta.launcher.core.install.OfficialLauncher;
import dev.vanta.launcher.core.install.OfficialProfileService;
import dev.vanta.launcher.core.install.SharedFileSource;
import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.core.java.AdoptiumService;
import dev.vanta.launcher.core.java.JavaDetector;
import dev.vanta.launcher.core.java.JavaProbe;
import dev.vanta.launcher.core.java.ProcessJavaProbe;
import dev.vanta.launcher.core.launch.LaunchService;
import dev.vanta.launcher.core.launch.StartupGuard;
import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.core.modrinth.ModrinthApi;
import dev.vanta.launcher.core.modrinth.ModrinthService;
import dev.vanta.launcher.core.modrinth.PerformancePack;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.net.HttpRequestSpec;
import dev.vanta.launcher.core.net.HttpResult;
import dev.vanta.launcher.core.net.HttpTransport;
import dev.vanta.launcher.core.net.JdkHttpTransport;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.core.settings.ReleasesBaseUrl;
import dev.vanta.launcher.core.settings.SettingsStore;
import dev.vanta.launcher.core.update.SemVer;
import dev.vanta.launcher.core.update.UpdateService;
import dev.vanta.launcher.core.util.LauncherPackaging;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.core.util.Sleeper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Composition root: wires paths, settings, HTTP, installer, Java detection, authentication, launch and update
 * services. The JavaFX UI and the CLI both obtain everything from here.
 *
 * <p>Settings are cached; {@link #settings()} returns the current value and {@link #saveSettings} persists and
 * replaces it. Services that depend on settings (releases URL, client id) read them lazily through suppliers, so
 * a settings change takes effect immediately. The releases base URL in effect is {@link #releasesBaseUrl()}:
 * settings (or {@code --releases-url}) &gt; {@code VANTA_RELEASES_BASE_URL} &gt; the built-in default.</p>
 */
public final class LauncherServices implements AutoCloseable {

    private static final Logger LOG = LauncherLog.get("Services");

    private final LauncherPaths paths;
    private final OsInfo os;
    private final Map<String, String> env;
    private final Clock clock;
    private final HttpTransport transport;
    private final Downloader downloader;
    private final SettingsStore settingsStore;
    private volatile LauncherSettings settings;
    private final MojangService mojang;
    private final FabricService fabric;
    private final FabricApiService fabricApi;
    private final VantaClientService vantaClient;
    private final JavaProbe probe;
    private final JavaDetector javaDetector;
    private final AdoptiumService adoptium;
    private final MicrosoftAuthService auth;
    private final AccountStore accounts;
    private final LaunchService launch;
    private final UpdateService updates;
    private final OfficialProfileService officialProfiles;
    private final OfficialLauncher officialLauncher;
    private final ModrinthService modrinth;
    private final PerformancePack performancePack;
    private final StartupGuard startupGuard;
    private final Optional<Path> officialMinecraftDir;
    private final Path userHome;
    private final ServiceEndpoints endpoints;

    /**
     * Services with production endpoints.
     *
     * @param paths     launcher paths
     * @param os        host platform
     * @param env       environment variables
     * @param transport HTTP transport
     * @param probe     Java probe
     * @param clock     clock
     * @param sleeper   sleeper for retries and OAuth polling
     */
    public LauncherServices(final LauncherPaths paths, final OsInfo os, final Map<String, String> env, final HttpTransport transport,
                            final JavaProbe probe, final Clock clock, final Sleeper sleeper) {
        this(paths, os, env, transport, probe, clock, sleeper, ServiceEndpoints.DEFAULT);
    }

    /**
     * @param paths     launcher paths
     * @param os        host platform
     * @param env       environment variables
     * @param transport HTTP transport
     * @param probe     Java probe
     * @param clock     clock
     * @param sleeper   sleeper for retries and OAuth polling
     * @param endpoints remote endpoints
     */
    public LauncherServices(final LauncherPaths paths, final OsInfo os, final Map<String, String> env, final HttpTransport transport,
                            final JavaProbe probe, final Clock clock, final Sleeper sleeper, final ServiceEndpoints endpoints) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.endpoints = Objects.requireNonNull(endpoints, "endpoints");
        this.os = Objects.requireNonNull(os, "os");
        this.env = Map.copyOf(Objects.requireNonNull(env, "env"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.probe = Objects.requireNonNull(probe, "probe");
        try {
            paths.createDirectories();
            LauncherLog.init(paths.logsDir());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not initialise launcher directories/logging", e);
        }
        this.downloader = new Downloader(transport, sleeper, Downloader.DEFAULT_ATTEMPTS, Downloader.DEFAULT_CONCURRENCY);
        this.settingsStore = new SettingsStore(paths.settingsFile());
        this.settings = settingsStore.load();
        this.mojang = new MojangService(downloader, paths, endpoints.mojangManifest(), endpoints.mojangResources());
        this.fabric = new FabricService(downloader, paths, endpoints.fabricMeta());
        this.fabricApi = new FabricApiService(fabric, paths, endpoints.fabricMaven());
        this.vantaClient = new VantaClientService(downloader, paths, () -> releasesBaseUrl().url());
        this.javaDetector = new JavaDetector(os, env, paths, probe, List.of());
        this.adoptium = new AdoptiumService(downloader, paths, os, probe, endpoints.adoptiumApi());
        this.auth = new MicrosoftAuthService(transport, endpoints.auth(),
            MicrosoftAuthService.clientIdFrom(() -> settings().msClientId(), env), clock, sleeper);
        this.accounts = AccountStore.open(paths, os);
        this.launch = new LaunchService(paths, os, clock);
        this.updates = new UpdateService(downloader, paths, () -> releasesBaseUrl().url(),
            SemVer.tryParse(LauncherVersion.VERSION).orElse(SemVer.of(1, 0, 0)), os, LauncherPackaging.detect(), vantaClient);
        // Modrinth asks for a descriptive User-Agent on API calls and downloads alike.
        final String modrinthAgent = ModrinthApi.userAgent(LauncherVersion.VERSION);
        final HttpTransport modrinthTransport = new UserAgentTransport(transport, modrinthAgent);
        this.modrinth = new ModrinthService(new ModrinthApi(modrinthTransport, endpoints.modrinthApi(), sleeper, modrinthAgent),
            new Downloader(modrinthTransport, sleeper, Downloader.DEFAULT_ATTEMPTS, 3), paths, clock, LauncherVersion.MINECRAFT);
        this.performancePack = new PerformancePack(modrinth, clock);
        this.startupGuard = new StartupGuard(paths, modrinth, clock);
        this.officialProfiles = new OfficialProfileService(paths, downloader, fabric, fabricApi, vantaClient, clock, performancePack);
        this.userHome = Path.of(System.getProperty("user.home", "."));
        this.officialLauncher = OfficialLauncher.system(os, env, userHome);
        this.officialMinecraftDir = LauncherPaths.officialMinecraftDir(os, env, userHome);
    }

    /**
     * Production services for the host with the default data directory.
     *
     * @return services
     */
    public static LauncherServices createDefault() {
        return create(LauncherPaths.detect());
    }

    /**
     * Production services for a specific data directory.
     *
     * @param paths paths
     * @return services
     */
    public static LauncherServices create(final LauncherPaths paths) {
        return new LauncherServices(paths, OsInfo.detect(), System.getenv(), new JdkHttpTransport(), new ProcessJavaProbe(),
            Clock.systemUTC(), Sleeper.REAL);
    }

    /** @return paths */
    public LauncherPaths paths() {
        return paths;
    }

    /** @return host platform */
    public OsInfo os() {
        return os;
    }

    /** @return environment variables */
    public Map<String, String> env() {
        return env;
    }

    /** @return clock */
    public Clock clock() {
        return clock;
    }

    /** @return settings store */
    public SettingsStore settingsStore() {
        return settingsStore;
    }

    /** @return current settings */
    public LauncherSettings settings() {
        return settings;
    }

    /**
     * Persists and activates settings.
     *
     * @param updated new settings
     * @throws IOException on failure
     */
    public void saveSettings(final LauncherSettings updated) throws IOException {
        settingsStore.save(updated);
        settings = updated;
    }

    /**
     * Replaces the in-memory settings without persisting (CLI overrides).
     *
     * @param updated settings
     */
    public void overrideSettings(final LauncherSettings updated) {
        settings = Objects.requireNonNull(updated, "updated");
    }

    /**
     * The releases base URL in effect: {@code "releasesBaseUrl"} from the settings (or {@code --releases-url}) when
     * non-empty, else {@code VANTA_RELEASES_BASE_URL}, else the built-in default of the endpoints.
     *
     * @return URL and its source
     */
    public ReleasesBaseUrl releasesBaseUrl() {
        return ReleasesBaseUrl.resolve(settings().releasesBaseUrl(), env, endpoints.defaultReleasesBaseUrl());
    }

    /** @return transport */
    public HttpTransport transport() {
        return transport;
    }

    /** @return downloader */
    public Downloader downloader() {
        return downloader;
    }

    /** @return Mojang service */
    public MojangService mojang() {
        return mojang;
    }

    /** @return Fabric service */
    public FabricService fabric() {
        return fabric;
    }

    /** @return Fabric API service */
    public FabricApiService fabricApi() {
        return fabricApi;
    }

    /** @return VANTA client service */
    public VantaClientService vantaClient() {
        return vantaClient;
    }

    /** @return a new installer honouring the current settings */
    public Installer installer() {
        final Optional<SharedFileSource> shared = settings().shareOfficialMinecraftFiles()
            ? officialMinecraftDir.map(SharedFileSource::new) : Optional.empty();
        return new Installer(paths, os, downloader, mojang, fabric, fabricApi, vantaClient, shared, clock, Optional.of(performancePack));
    }

    /** @return Java probe */
    public JavaProbe javaProbe() {
        return probe;
    }

    /** @return Java detector */
    public JavaDetector javaDetector() {
        return javaDetector;
    }

    /** @return Adoptium service */
    public AdoptiumService adoptium() {
        return adoptium;
    }

    /** @return Microsoft authentication */
    public MicrosoftAuthService auth() {
        return auth;
    }

    /** @return account store */
    public AccountStore accounts() {
        return accounts;
    }

    /** @return launch service */
    public LaunchService launch() {
        return launch;
    }

    /** @return update service */
    public UpdateService updates() {
        return updates;
    }

    /** @return the official {@code .minecraft} directory when present */
    public Optional<Path> officialMinecraftDir() {
        return officialMinecraftDir;
    }

    /** @return where the official Minecraft Launcher keeps its data on this platform (may not exist) */
    public Path officialMinecraftDirCandidate() {
        return LauncherPaths.officialMinecraftDirCandidate(os, env, userHome);
    }

    /** @return the "Use with the Minecraft Launcher" service */
    public OfficialProfileService officialProfiles() {
        return officialProfiles;
    }

    /** @return the official Minecraft Launcher as a program (running? start it) */
    public OfficialLauncher officialLauncher() {
        return officialLauncher;
    }

    /** @return Modrinth content of the VANTA instance (Mods page, performance pack) */
    public ModrinthService modrinth() {
        return modrinth;
    }

    /** @return the performance pack */
    public PerformancePack performancePack() {
        return performancePack;
    }

    /** @return the start check (switches off mods that would stop Minecraft while starting) */
    public StartupGuard startupGuard() {
        return startupGuard;
    }

    /**
     * A request for the official launcher profile with the current settings (heap).
     *
     * @param minecraftDir   official Minecraft directory
     * @param localClientJar local VANTA client jar instead of the published release (may be null)
     * @return request
     */
    public OfficialProfileService.Request officialProfileRequest(final Path minecraftDir, final Path localClientJar) {
        return OfficialProfileService.Request.standard(minecraftDir, localClientJar, settings().memoryMb(), settings().performancePack());
    }

    /** @return remote endpoints in use */
    public ServiceEndpoints endpoints() {
        return endpoints;
    }

    @Override
    public void close() {
        downloader.close();
        LauncherLog.close();
    }

    /**
     * Sends a fixed {@code User-Agent} with every request over a shared transport. Closing it leaves the shared
     * transport open (the composition root closes that one).
     */
    static final class UserAgentTransport implements HttpTransport {

        private final HttpTransport delegate;
        private final String userAgent;

        UserAgentTransport(final HttpTransport delegate, final String userAgent) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
        }

        @Override
        public HttpResult execute(final HttpRequestSpec request) throws IOException, InterruptedException {
            return delegate.execute(request.headers().containsKey("User-Agent") ? request : request.withHeader("User-Agent", userAgent));
        }

        @Override
        public void close() {
            // the shared transport is closed by its owner
        }
    }
}
