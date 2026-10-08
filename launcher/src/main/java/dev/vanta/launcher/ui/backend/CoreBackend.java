package dev.vanta.launcher.ui.backend;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.LauncherServices;
import dev.vanta.launcher.core.ai.LocalAiReport;
import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.auth.AuthException;
import dev.vanta.launcher.core.auth.DeviceCode;
import dev.vanta.launcher.core.auth.OfflineAccountPolicy;
import dev.vanta.launcher.core.install.InstallException;
import dev.vanta.launcher.core.install.InstallListener;
import dev.vanta.launcher.core.install.InstalledClient;
import dev.vanta.launcher.core.install.InstallRequest;
import dev.vanta.launcher.core.install.OfficialLauncher;
import dev.vanta.launcher.core.install.OfficialProfileService;
import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.core.java.JavaDetector;
import dev.vanta.launcher.core.java.JavaInstall;
import dev.vanta.launcher.core.launch.GameProcess;
import dev.vanta.launcher.core.launch.LaunchCommand;
import dev.vanta.launcher.core.launch.LaunchRequest;
import dev.vanta.launcher.core.launch.RestartRequest;
import dev.vanta.launcher.core.launch.StartupGuard;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.modrinth.ContentType;
import dev.vanta.launcher.core.modrinth.ModrinthModels;
import dev.vanta.launcher.core.modrinth.ModrinthService;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.Checksums;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.HttpRequestSpec;
import dev.vanta.launcher.core.net.HttpResult;
import dev.vanta.launcher.core.net.HttpStatusException;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.core.settings.ReleasesBaseUrl;
import dev.vanta.launcher.core.update.UpdateInfo;
import dev.vanta.launcher.core.update.UpdateService;
import dev.vanta.launcher.core.util.LauncherPackaging;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.core.util.SystemMemory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * {@link LauncherBackend} over the real {@link LauncherServices}. Every method delegates to exactly one core
 * service; this class contains no decisions of its own.
 */
public final class CoreBackend implements LauncherBackend {

    private static final long MAX_TEXT_BYTES = 512L * 1024L;

    private final LauncherServices services;

    /**
     * @param services composition root
     */
    public CoreBackend(final LauncherServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /** @return the wrapped services */
    public LauncherServices services() {
        return services;
    }

    @Override
    public LauncherPaths paths() {
        return services.paths();
    }

    @Override
    public OsInfo os() {
        return services.os();
    }

    @Override
    public Map<String, String> env() {
        return services.env();
    }

    @Override
    public Clock clock() {
        return services.clock();
    }

    @Override
    public OptionalLong totalMemoryMb() {
        return SystemMemory.totalMemoryMb();
    }

    @Override
    public LauncherSettings settings() {
        return services.settings();
    }

    @Override
    public LauncherSettings defaultSettings() {
        return services.settingsStore().defaults();
    }

    @Override
    public void saveSettings(final LauncherSettings settings) throws IOException {
        services.saveSettings(settings);
    }

    @Override
    public List<Account> accounts() {
        return services.accounts().accounts();
    }

    @Override
    public Optional<Account> activeAccount() {
        return services.accounts().active();
    }

    @Override
    public void setActiveAccount(final String uuid) throws IOException {
        services.accounts().setActive(uuid);
        services.accounts().save();
    }

    @Override
    public void removeAccount(final String uuid) throws IOException {
        services.accounts().remove(uuid);
        services.accounts().save();
    }

    @Override
    public boolean signInConfigured() {
        return services.auth().isConfigured();
    }

    @Override
    public DeviceCode beginSignIn() throws AuthException, IOException, InterruptedException {
        return services.auth().beginDeviceCode();
    }

    @Override
    public Account completeSignIn(final DeviceCode code, final CancellationToken token) throws AuthException, IOException, InterruptedException {
        final Account account = services.auth().login(code, token);
        services.accounts().put(account);
        services.accounts().save();
        return account;
    }

    @Override
    public Account refreshIfExpired(final Account account) throws AuthException, IOException, InterruptedException {
        if (!account.isExpired(Instant.now(services.clock())) || !account.canRefresh()) {
            return account;
        }
        final Account refreshed = services.auth().refresh(account);
        services.accounts().put(refreshed);
        services.accounts().save();
        return refreshed;
    }

    @Override
    public boolean offlineSessionAllowed() {
        return OfflineAccountPolicy.offlineAllowed(services.settings(), services.env(), services.accounts());
    }

    @Override
    public Account createOfflineSession() throws AuthException {
        return OfflineAccountPolicy.createOffline(services.settings(), services.env(), services.accounts(), "Dev");
    }

    @Override
    public List<JavaInstall> detectJava() {
        return services.javaDetector().detect();
    }

    @Override
    public Optional<JavaInstall> probeJava(final Path path) {
        return services.javaDetector().probeUserPath(path);
    }

    @Override
    public Optional<JavaInstall> pickJava(final List<JavaInstall> detected) {
        final Optional<String> override = services.settings().javaPathOverride();
        if (override.isPresent()) {
            final Optional<JavaInstall> configured = services.javaDetector().probeUserPath(Path.of(override.get()))
                .filter(j -> j.satisfies(LauncherVersion.JAVA_MAJOR));
            if (configured.isPresent()) {
                return configured;
            }
        }
        return JavaDetector.pick(detected, LauncherVersion.JAVA_MAJOR);
    }

    @Override
    public JavaInstall installJava(final DownloadProgressListener listener, final CancellationToken token) throws IOException, InterruptedException {
        return services.adoptium().install(listener, token);
    }

    @Override
    public Optional<InstanceInfo> loadInstance() throws IOException {
        return services.installer().loadInstance();
    }

    @Override
    public Optional<InstanceInfo> findInstalled(final InstallRequest request) throws IOException {
        return services.installer().findInstalled(request);
    }

    @Override
    public InstanceInfo install(final InstallRequest request, final InstallListener listener, final CancellationToken token)
        throws InstallException, InterruptedException {
        return services.installer().install(request, listener, token);
    }

    @Override
    public RunningGame launch(final LaunchRequest request, final Consumer<GameProcess.Line> output) throws IOException {
        final LaunchCommand command = services.launch().buildCommand(request);
        return new ProcessGame(services.launch().start(command, List.of(output)));
    }

    @Override
    public Optional<Path> activeClientJar() throws IOException {
        return services.vantaClient().activeJar();
    }

    @Override
    public List<VantaClientService.KeptVersion> keptClientVersions() throws IOException {
        return services.updates().listClientVersions();
    }

    @Override
    public Path rollbackClient(final String version) throws IOException {
        return services.updates().rollbackClient(version);
    }

    @Override
    public boolean updatesConfigured() {
        return services.updates().isConfigured();
    }

    @Override
    public ReleasesBaseUrl releasesBaseUrl() {
        return services.releasesBaseUrl();
    }

    @Override
    public OfficialProfileService.Plan officialProfilePlan() throws IOException, InterruptedException {
        return services.officialProfiles().plan(services.officialProfileRequest(services.officialMinecraftDirCandidate(), null));
    }

    @Override
    public OfficialProfileService.Result installOfficialProfile(final InstallListener listener, final CancellationToken token)
        throws IOException, InterruptedException {
        return services.officialProfiles().install(services.officialProfileRequest(services.officialMinecraftDirCandidate(), null),
            listener, token);
    }

    @Override
    public List<String> runningOfficialLaunchers() {
        return services.officialLauncher().running().stream().map(OfficialLauncher.Running::describe).toList();
    }

    @Override
    public OfficialLauncher.OpenResult openOfficialLauncher() throws InterruptedException {
        return services.officialLauncher().open(services.officialMinecraftDirCandidate());
    }

    @Override
    public ModrinthModels.SearchPage searchModrinth(final ContentType type, final String query, final int offset)
        throws IOException, InterruptedException {
        return services.modrinth().search(type, query, offset);
    }

    @Override
    public ModrinthService.ApplyResult installFromModrinth(final ContentType type, final String projectId, final DownloadProgressListener downloads,
                                                           final Consumer<String> log, final CancellationToken token)
        throws IOException, InterruptedException {
        final ModrinthService.Resolution resolution = services.modrinth().resolve(List.of(new ModrinthService.Root(projectId, type)), false, token);
        return services.modrinth().apply(resolution, downloads, log, false, token);
    }

    @Override
    public List<ModrinthService.InstalledContent> installedContent() throws IOException {
        return services.modrinth().installed();
    }

    @Override
    public void setContentEnabled(final ModrinthService.InstalledContent item, final boolean enabled) throws IOException {
        services.modrinth().setEnabled(item, enabled);
    }

    @Override
    public void removeContent(final ModrinthService.InstalledContent item) throws IOException {
        services.modrinth().remove(item);
    }

    @Override
    public ModrinthService.ApplyResult updateAllContent(final DownloadProgressListener downloads, final Consumer<String> log,
                                                        final CancellationToken token) throws IOException, InterruptedException {
        services.performancePack().invalidate();
        return services.modrinth().updateAll(downloads, log, token);
    }

    @Override
    public boolean consumeRestartRequest() throws IOException {
        return RestartRequest.consume(services.paths().instanceDir());
    }

    @Override
    public StartupGuard.Report startupCheck() throws IOException {
        return services.startupGuard().run();
    }

    @Override
    public StartupGuard.Report crashReportCheck() throws IOException {
        return services.startupGuard().recoverFromCrashReport();
    }

    @Override
    public Optional<UpdateInfo> checkLauncherUpdate() throws IOException, InterruptedException {
        return services.updates().checkLauncher();
    }

    @Override
    public Optional<InstalledClient> installedClient() throws IOException {
        return services.vantaClient().installedClient();
    }

    @Override
    public UpdateService.ClientCheck checkClient(final Optional<InstalledClient> installed) throws IOException, InterruptedException {
        return services.updates().checkClient(installed);
    }

    @Override
    public LauncherPackaging packaging() {
        return services.updates().packaging();
    }

    @Override
    public Path downloadUpdate(final UpdateInfo update, final DownloadProgressListener listener, final CancellationToken token)
        throws IOException, InterruptedException {
        return services.updates().downloadUpdate(update, listener, token);
    }

    @Override
    public Path prepareInstaller(final UpdateInfo update) throws IOException {
        return services.updates().prepareInstaller(update);
    }

    @Override
    public Path installClientUpdate(final UpdateInfo update, final DownloadProgressListener listener, final CancellationToken token)
        throws IOException, InterruptedException {
        return services.updates().installClientUpdate(update, listener, token);
    }

    @Override
    public String fetchText(final URI uri) throws IOException, InterruptedException {
        final HttpRequestSpec spec = HttpRequestSpec.get(uri).withHeader("Accept", "text/plain, text/markdown, */*")
            .withTimeout(Duration.ofSeconds(30));
        try (HttpResult result = services.transport().execute(spec)) {
            if (!result.isSuccess()) {
                throw new HttpStatusException(result.status(), uri);
            }
            if (result.contentLength() > MAX_TEXT_BYTES) {
                throw new IOException("Document too large: " + result.contentLength() + " bytes");
            }
            final byte[] bytes = result.body().readNBytes((int) MAX_TEXT_BYTES + 1);
            if (bytes.length > MAX_TEXT_BYTES) {
                throw new IOException("Document too large");
            }
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Override
    public LocalAiReport localAiStatus() throws IOException {
        return services.localAi().status();
    }

    @Override
    public LocalAiReport verifyLocalAi() throws IOException {
        return services.localAi().verify();
    }

    @Override
    public LocalAiReport installLocalAi(final InstallListener listener, final CancellationToken token) throws IOException, InterruptedException {
        return services.localAi().install(listener, token);
    }

    @Override
    public boolean removeLocalAi() throws IOException {
        return services.localAi().remove();
    }

    @Override
    public String sha256(final Path file) throws IOException {
        return Checksums.sha256Hex(file);
    }

    @Override
    public void close() {
        services.close();
    }

    /** {@link RunningGame} over a {@link GameProcess}. */
    static final class ProcessGame implements RunningGame {

        private final GameProcess process;

        ProcessGame(final GameProcess process) {
            this.process = process;
        }

        @Override
        public CompletableFuture<Integer> exitCode() {
            return process.exitCode();
        }

        @Override
        public boolean isAlive() {
            return process.isAlive();
        }

        @Override
        public long pid() {
            return process.pid();
        }

        @Override
        public Path logFile() {
            return process.logFile();
        }

        @Override
        public void stop() {
            process.destroy();
        }

        @Override
        public void kill() {
            process.destroyForcibly();
        }
    }
}
