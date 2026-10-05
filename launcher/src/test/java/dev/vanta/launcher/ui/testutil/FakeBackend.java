package dev.vanta.launcher.ui.testutil;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.auth.AccountType;
import dev.vanta.launcher.core.auth.AuthException;
import dev.vanta.launcher.core.auth.AuthNotConfiguredException;
import dev.vanta.launcher.core.auth.DeviceCode;
import dev.vanta.launcher.core.install.InstallException;
import dev.vanta.launcher.core.install.InstallListener;
import dev.vanta.launcher.core.install.InstallPlan;
import dev.vanta.launcher.core.install.InstallProgress;
import dev.vanta.launcher.core.install.InstallRequest;
import dev.vanta.launcher.core.install.InstallStep;
import dev.vanta.launcher.core.install.InstalledClient;
import dev.vanta.launcher.core.install.NotPublishedException;
import dev.vanta.launcher.core.install.OfficialProfileService;
import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.core.java.JavaInstall;
import dev.vanta.launcher.core.launch.GameProcess;
import dev.vanta.launcher.core.launch.LaunchRequest;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.core.settings.ReleasesBaseUrl;
import dev.vanta.launcher.core.update.SemVer;
import dev.vanta.launcher.core.update.UpdateInfo;
import dev.vanta.launcher.core.update.UpdateService;
import dev.vanta.launcher.core.util.ByteSizes;
import dev.vanta.launcher.core.util.LauncherPackaging;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.ui.backend.LauncherBackend;
import dev.vanta.launcher.ui.backend.RunningGame;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Scriptable {@link LauncherBackend} for view model tests and the screenshot harness. Every behaviour is a public
 * field or setter so a test reads like a story: "no Java, then install fails with NotPublished".
 */
public final class FakeBackend implements LauncherBackend {

    /** A controllable running game. */
    public static final class FakeGame implements RunningGame {
        private final CompletableFuture<Integer> exit = new CompletableFuture<>();
        private final long pid;
        private final Path log;

        FakeGame(final long pid, final Path log) {
            this.pid = pid;
            this.log = log;
        }

        @Override
        public CompletableFuture<Integer> exitCode() {
            return exit;
        }

        @Override
        public boolean isAlive() {
            return !exit.isDone();
        }

        @Override
        public long pid() {
            return pid;
        }

        @Override
        public Path logFile() {
            return log;
        }

        @Override
        public void stop() {
            exit.complete(0);
        }

        @Override
        public void kill() {
            exit.complete(137);
        }

        /**
         * Ends the game.
         *
         * @param code exit code
         */
        public void exit(final int code) {
            exit.complete(code);
        }
    }

    /** Fixed clock: 2026-10-04T12:00:00Z. */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC);

    private final LauncherPaths paths;
    private final Map<String, String> env = new HashMap<>();
    /** Current settings. */
    public LauncherSettings settings = LauncherSettings.defaults(16384);
    /** Stored accounts (first = active). */
    public final List<Account> accounts = new ArrayList<>();
    /** Whether a client id is configured. */
    public boolean signInConfigured = true;
    /** Device code returned by {@link #beginSignIn()}. */
    public DeviceCode deviceCode = new DeviceCode("secret-device-code", "QW7X9K2P", "https://www.microsoft.com/link", 900, 5,
        "", CLOCK.millis());
    /** Account delivered when the fake sign-in is approved. */
    public Account signInResult;
    /** Failure thrown by sign-in (begin or complete) when set. */
    public Exception signInFailure;
    private final CountDownLatch signInGate = new CountDownLatch(1);
    /** Whether offline sessions are allowed. */
    public boolean offlineAllowed;
    /** Detected runtimes. */
    public final List<JavaInstall> javaInstalls = new ArrayList<>();
    /** Runtime "installed" by {@link #installJava}. */
    public JavaInstall temurin;
    /** Probe results by path. */
    public final Map<Path, JavaInstall> probes = new HashMap<>();
    /** Installed instance. */
    public InstanceInfo instance;
    /** Failure thrown by {@link #install}. */
    public Exception installFailure;
    /** When set, {@link #install} blocks until released (screenshots of the installing state). */
    public CountDownLatch installGate;
    /** Called while an install runs (lets tests observe the INSTALLING state synchronously). */
    public Runnable installHook = () -> { };
    /** Number of fake files reported during an install. */
    public int installFiles = 24;
    /** Lines emitted by the fake game on start. */
    public final List<String> gameOutput = new ArrayList<>(List.of(
        "[12:00:01] [main/INFO]: Loading Minecraft 1.21.11 with Fabric Loader 0.19.5",
        "[12:00:02] [main/INFO]: Loading 3 mods: fabric-api, vanta, minecraft",
        "[12:00:04] [Render thread/INFO]: Backend library: LWJGL version 3.3.3",
        "[12:00:05] [Render thread/WARN]: Shader rendertype_entity_translucent_emissive could not be found",
        "[12:00:06] [Render thread/INFO]: [VANTA] Client ready"));
    /** Failure thrown by {@link #launch}. */
    public IOException launchFailure;
    /** Last started game. */
    public FakeGame game;
    /** Active client jar. */
    public Path activeJar;
    /** SHA-256 reported for files. */
    public String sha256 = "43af029f14abd60e022d50e036ac7398856997f6d1e759c815c277cded0812e1";
    /** Kept versions. */
    public final List<VantaClientService.KeptVersion> kept = new ArrayList<>();
    /**
     * Whether {@link #installClientUpdate} keeps a rollback copy of the client it replaces, like the core does for a jar
     * with a release version that has no copy yet (false: the copy is pruned or the version is unknown).
     */
    public boolean keepReplacedClient = true;
    /** Whether a releases URL is configured. */
    public boolean updatesConfigured = true;
    /** Launcher update. */
    public Optional<UpdateInfo> launcherUpdate = Optional.empty();
    /** Client update (its version is the latest client release). */
    public Optional<UpdateInfo> clientUpdate = Optional.empty();
    /** Latest client release when {@link #clientUpdate} is empty. */
    public String latestClientVersion = "1.0.0";
    /**
     * When set, {@link #install} over an existing instance installs this VANTA Client version, like the core, whose
     * standard install always installs the latest release (null: the installed client stays).
     */
    public String installClientVersion;
    /** How the fake launcher was installed. */
    public LauncherPackaging packaging = LauncherPackaging.PLAIN_JAR;
    /** Failure thrown by the client check. */
    public IOException clientCheckFailure;
    /** Runs inside the client check, after the installed state was read (lets tests change it mid-check). */
    public Runnable clientCheckHook = () -> { };
    /** Failure thrown by the launcher check. */
    public IOException launcherCheckFailure;
    /** Official Minecraft directory used by the fake "Use with the Minecraft Launcher". */
    public Path officialMinecraftDir;
    /** Failure thrown by {@link #officialProfilePlan()}. */
    public IOException officialPlanFailure;
    /** Failure thrown by {@link #installOfficialProfile}. */
    public IOException officialInstallFailure;
    /** Whether the VANTA profile already exists in the fake official launcher. */
    public boolean officialProfileExists;
    /** Release notes by URL. */
    public final Map<URI, String> documents = new HashMap<>();
    /** Memory in MiB. */
    public OptionalLong totalMemory = OptionalLong.of(16384);
    /** Records calls for assertions. */
    public final List<String> calls = new ArrayList<>();
    private final AtomicInteger pids = new AtomicInteger(40000);
    private boolean closed;

    /**
     * @param dataDir data directory
     */
    public FakeBackend(final Path dataDir) {
        this.paths = new LauncherPaths(dataDir);
        this.officialMinecraftDir = paths.dataDir().resolveSibling(".minecraft");
    }

    // ---------------------------------------------------------------- fixtures

    /** @return a verified Microsoft account */
    public static Account microsoftAccount(final String name) {
        return new Account("1f6e6a4e-6c0e-4a3d-9f0b-2d1b4c9e7a11", name, "2535400000000001", "token-" + name,
            CLOCK.millis() + 3_600_000L, "refresh-" + name, AccountType.MICROSOFT);
    }

    /** @return a Temurin 21 install under the launcher runtimes dir */
    public JavaInstall temurin21() {
        final Path home = paths.runtimesDir().resolve("temurin-21-jdk-21.0.4+7");
        return new JavaInstall(home, home.resolve("bin/java"), "21.0.4", 21, "Eclipse Adoptium", "amd64", true);
    }

    /** @return a system Java 17 */
    public JavaInstall system17() {
        final Path home = Path.of("/usr/lib/jvm/java-17-openjdk-amd64");
        return new JavaInstall(home, home.resolve("bin/java"), "17.0.12", 17, "Ubuntu", "amd64", true);
    }

    /** @return an installed instance with VANTA Client 1.0.0 */
    public static InstanceInfo installedInstance(final String clientVersion) {
        return new InstanceInfo(1, LauncherVersion.INSTANCE_ID, LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER,
            LauncherVersion.FABRIC_API, clientVersion, "vanta-client-" + clientVersion + ".jar", LauncherVersion.MINECRAFT,
            "fabric-loader-" + LauncherVersion.FABRIC_LOADER + "-" + LauncherVersion.MINECRAFT, "net.fabricmc.loader.impl.launch.knot.KnotClient",
            "26", 21, "2026-10-03T18:42:00Z");
    }

    /**
     * @param product product
     * @param version version
     * @param downloadable whether the file has a URL
     * @return update info
     */
    public static UpdateInfo update(final String product, final String current, final String version, final boolean downloadable) {
        final String name = ReleaseManifest.PRODUCT_LAUNCHER.equals(product) ? "VANTA-Launcher-" + version + ".msi" : "vanta-client-" + version + ".jar";
        final ReleaseManifest.ReleaseFile file = new ReleaseManifest.ReleaseFile(name,
            downloadable ? "https://releases.example/" + name : "", downloadable ? 4_194_304 : 0,
            downloadable ? "2d686d63214e1d98d8ddf97ba052cd44edc3e4cea683ee83f3a035282a4e6400" : "");
        final ReleaseManifest manifest = new ReleaseManifest(1, product, version, LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER,
            LauncherVersion.FABRIC_API, 21, "2026-11-20", "stable", List.of(file), "website/content/changelog/" + product + "-" + version + ".md", "");
        return new UpdateInfo(product, SemVer.parse(current), SemVer.parse(version), manifest.changelog(), file, file.sha256(), manifest);
    }

    /** Approves the pending sign-in. */
    public void approveSignIn() {
        signInGate.countDown();
    }

    /**
     * @param key   variable
     * @param value value
     */
    public void putEnv(final String key, final String value) {
        env.put(key, value);
    }

    /** @return whether {@link #close()} was called */
    public boolean isClosed() {
        return closed;
    }

    // ---------------------------------------------------------------- LauncherBackend

    @Override
    public LauncherPaths paths() {
        return paths;
    }

    @Override
    public OsInfo os() {
        return OsInfo.fromProperties("Linux", "amd64", "6.8");
    }

    @Override
    public Map<String, String> env() {
        return Map.copyOf(env);
    }

    @Override
    public Clock clock() {
        return CLOCK;
    }

    @Override
    public OptionalLong totalMemoryMb() {
        return totalMemory;
    }

    @Override
    public LauncherSettings settings() {
        return settings;
    }

    @Override
    public LauncherSettings defaultSettings() {
        return LauncherSettings.defaults(totalMemory.orElse(0));
    }

    @Override
    public void saveSettings(final LauncherSettings updated) {
        calls.add("saveSettings");
        settings = updated;
    }

    @Override
    public List<Account> accounts() {
        return List.copyOf(accounts);
    }

    @Override
    public Optional<Account> activeAccount() {
        return accounts.isEmpty() ? Optional.empty() : Optional.of(accounts.get(0));
    }

    @Override
    public void setActiveAccount(final String uuid) {
        accounts.stream().filter(a -> a.uuid().equals(uuid)).findFirst().ifPresent(a -> {
            accounts.remove(a);
            accounts.add(0, a);
        });
    }

    @Override
    public void removeAccount(final String uuid) {
        calls.add("removeAccount");
        accounts.removeIf(a -> a.uuid().equals(uuid));
    }

    @Override
    public boolean signInConfigured() {
        return signInConfigured;
    }

    @Override
    public DeviceCode beginSignIn() throws AuthException, IOException {
        calls.add("beginSignIn");
        if (!signInConfigured) {
            throw new AuthNotConfiguredException();
        }
        if (signInFailure instanceof AuthException a && signInResult == null && deviceCode == null) {
            throw a;
        }
        return deviceCode;
    }

    @Override
    public Account completeSignIn(final DeviceCode code, final CancellationToken token) throws AuthException, IOException, InterruptedException {
        calls.add("completeSignIn");
        while (!signInGate.await(20, TimeUnit.MILLISECONDS)) {
            token.throwIfCancelled();
        }
        if (signInFailure instanceof AuthException a) {
            throw a;
        }
        if (signInFailure instanceof IOException io) {
            throw io;
        }
        if (signInResult == null) {
            throw new AuthException("No fake account configured");
        }
        accounts.remove(signInResult);
        accounts.add(0, signInResult);
        return signInResult;
    }

    @Override
    public Account refreshIfExpired(final Account account) {
        calls.add("refreshIfExpired");
        return account;
    }

    @Override
    public boolean offlineSessionAllowed() {
        return offlineAllowed;
    }

    @Override
    public Account createOfflineSession() throws AuthException {
        if (!offlineAllowed) {
            throw new AuthException("Offline play requires a Microsoft account that has signed in successfully on this computer at least once.");
        }
        final Optional<Account> verified = accounts.stream().filter(a -> a.type() == AccountType.MICROSOFT).findFirst();
        if (verified.isPresent()) {
            final Account v = verified.get();
            return new Account(v.uuid(), v.name(), v.xuid(), Account.OFFLINE_TOKEN, Long.MAX_VALUE, "", AccountType.OFFLINE);
        }
        return new Account(Account.offlineUuid("Dev"), "Dev", "", Account.OFFLINE_TOKEN, Long.MAX_VALUE, "", AccountType.DEVELOPMENT);
    }

    @Override
    public List<JavaInstall> detectJava() {
        calls.add("detectJava");
        return List.copyOf(javaInstalls);
    }

    @Override
    public Optional<JavaInstall> probeJava(final Path path) {
        return Optional.ofNullable(probes.get(path));
    }

    @Override
    public Optional<JavaInstall> pickJava(final List<JavaInstall> detected) {
        final Optional<String> override = settings.javaPathOverride();
        if (override.isPresent()) {
            final Path home = Path.of(override.get());
            final Optional<JavaInstall> configured = detected.stream().filter(j -> j.home().equals(home)).findFirst()
                .or(() -> Optional.ofNullable(probes.get(home)));
            if (configured.isPresent() && configured.get().satisfies(LauncherVersion.JAVA_MAJOR)) {
                return configured;
            }
        }
        return detected.stream().filter(j -> j.satisfies(LauncherVersion.JAVA_MAJOR)).findFirst();
    }

    @Override
    public JavaInstall installJava(final DownloadProgressListener listener, final CancellationToken token) throws IOException {
        calls.add("installJava");
        if (temurin == null) {
            throw new IOException("Adoptium lists no Temurin 21 JRE for linux/x64");
        }
        final DownloadRequest request = new DownloadRequest(URI.create("https://api.adoptium.net/fake.tar.gz"),
            paths.cacheDir().resolve("OpenJDK21U-jre.tar.gz"), 48_000_000L, null, "OpenJDK21U-jre_x64_linux_hotspot_21.0.4_7.tar.gz");
        listener.onProgress(request, 24_000_000L, 48_000_000L);
        listener.onProgress(request, 48_000_000L, 48_000_000L);
        listener.onComplete(request, false, 48_000_000L);
        if (!javaInstalls.contains(temurin)) {
            javaInstalls.add(0, temurin);
        }
        return temurin;
    }

    @Override
    public Optional<InstanceInfo> loadInstance() {
        return Optional.ofNullable(instance);
    }

    @Override
    public Optional<InstanceInfo> findInstalled(final InstallRequest request) {
        return Optional.ofNullable(instance);
    }

    @Override
    public InstanceInfo install(final InstallRequest request, final InstallListener listener, final CancellationToken token)
        throws InstallException, InterruptedException {
        calls.add("install");
        final List<InstallStep> steps = InstallPlan.stepsFor(request);
        listener.onLog("Install plan: " + steps.size() + " steps");
        installHook.run();
        long bytes = 0;
        for (int i = 0; i < steps.size(); i++) {
            final InstallStep step = steps.get(i);
            final int total = step == InstallStep.LIBRARIES || step == InstallStep.ASSETS ? installFiles : 1;
            for (int f = 0; f <= total; f++) {
                token.throwIfCancelled();
                bytes += f == 0 ? 0 : 512_000L;
                listener.onProgress(new InstallProgress(step, i, steps.size(), f, total, bytes, f == 0 ? null : "Verified file-" + f + ".jar"));
                if (installGate != null && step == InstallStep.LIBRARIES && f == total / 2) {
                    while (!installGate.await(20, TimeUnit.MILLISECONDS)) {
                        if (token.isCancelled()) {
                            throw new CancellationException("Operation cancelled");
                        }
                    }
                }
            }
        }
        if (installFailure instanceof InstallException ie) {
            throw ie;
        }
        if (installFailure != null) {
            throw new InstallException(InstallStep.VANTA_CLIENT, "Installing VANTA Client failed: " + installFailure.getMessage(), installFailure);
        }
        if (instance == null) {
            instance = installedInstance("1.0.0");
            activeJar = paths.modsDir().resolve(instance.vantaClientJar());
        } else if (installClientVersion != null) {
            instance = instance.withVantaClient(installClientVersion, "vanta-client-" + installClientVersion + ".jar");
            activeJar = paths.modsDir().resolve(instance.vantaClientJar());
        }
        listener.onLog("Installation complete: " + ByteSizes.format(bytes) + " downloaded");
        return instance;
    }

    @Override
    public RunningGame launch(final LaunchRequest request, final Consumer<GameProcess.Line> output) throws IOException {
        calls.add("launch");
        if (launchFailure != null) {
            throw launchFailure;
        }
        game = new FakeGame(pids.incrementAndGet(), paths.logsDir().resolve("game-2026-10-04_12-00-00.log"));
        for (String line : gameOutput) {
            output.accept(new GameProcess.Line("stdout", line));
        }
        return game;
    }

    @Override
    public Optional<Path> activeClientJar() {
        return Optional.ofNullable(activeJar);
    }

    @Override
    public List<VantaClientService.KeptVersion> keptClientVersions() {
        return List.copyOf(kept);
    }

    @Override
    public Path rollbackClient(final String version) throws IOException {
        calls.add("rollback:" + version);
        final VantaClientService.KeptVersion k = kept.stream().filter(v -> v.version().equals(version)).findFirst()
            .orElseThrow(() -> new IOException("VANTA Client " + version + " is not available locally for rollback"));
        if (instance != null) {
            instance = instance.withVantaClient(version, "vanta-client-" + version + ".jar");
        }
        if (activeJar != null) {
            activeJar = paths.modsDir().resolve("vanta-client-" + version + ".jar");
        }
        return k.jar();
    }

    @Override
    public boolean updatesConfigured() {
        return updatesConfigured;
    }

    @Override
    public ReleasesBaseUrl releasesBaseUrl() {
        return settings.effectiveReleasesBaseUrl(env);
    }

    @Override
    public OfficialProfileService.Plan officialProfilePlan() throws IOException {
        calls.add("officialProfilePlan");
        if (officialPlanFailure != null) {
            throw officialPlanFailure;
        }
        final String id = "fabric-loader-" + LauncherVersion.FABRIC_LOADER + "-" + LauncherVersion.MINECRAFT;
        final Path versionDir = officialMinecraftDir.resolve("versions").resolve(id);
        return new OfficialProfileService.Plan(officialMinecraftDir, paths.instanceDir(), OfficialProfileService.profileKey(LauncherVersion.MINECRAFT),
            OfficialProfileService.profileName(LauncherVersion.MINECRAFT), id, "1.0.0", List.of(
                new OfficialProfileService.PlannedFile(OfficialProfileService.Kind.FABRIC_API,
                    paths.modsDir().resolve("fabric-api-" + LauncherVersion.FABRIC_API + ".jar").toString()),
                new OfficialProfileService.PlannedFile(OfficialProfileService.Kind.VANTA_CLIENT, paths.modsDir().resolve("vanta-client-1.0.0.jar").toString()),
                new OfficialProfileService.PlannedFile(OfficialProfileService.Kind.VERSION_JSON, versionDir.resolve(id + ".json").toString()),
                new OfficialProfileService.PlannedFile(OfficialProfileService.Kind.VERSION_JAR, versionDir.resolve(id + ".jar").toString()),
                new OfficialProfileService.PlannedFile(OfficialProfileService.Kind.PROFILES,
                    officialMinecraftDir.resolve(OfficialProfileService.PROFILES_FILE).toString())), officialProfileExists);
    }

    @Override
    public OfficialProfileService.Result installOfficialProfile(final InstallListener listener, final CancellationToken token) throws IOException {
        calls.add("installOfficialProfile");
        final OfficialProfileService.Plan plan = officialProfilePlan();
        final List<InstallStep> steps = List.of(InstallStep.FABRIC_PROFILE, InstallStep.FABRIC_API, InstallStep.VANTA_CLIENT, InstallStep.FINALIZE);
        for (int i = 0; i < steps.size(); i++) {
            token.throwIfCancelled();
            listener.onProgress(new InstallProgress(steps.get(i), i, steps.size(), 1, 1, 0, null));
        }
        if (officialInstallFailure != null) {
            throw officialInstallFailure;
        }
        final boolean created = !officialProfileExists;
        officialProfileExists = true;
        final Path clientJar = paths.modsDir().resolve("vanta-client-1.0.0.jar");
        // Like the core: the jar lands in the instance's mods/, instance.json is only updated when it exists.
        activeJar = clientJar;
        if (instance != null) {
            instance = instance.withVantaClient("1.0.0", clientJar.getFileName().toString());
        }
        return new OfficialProfileService.Result(plan.minecraftDir(), plan.gameDir(), plan.profileKey(), plan.profileName(), plan.versionId(),
            created, plan.files().stream().map(f -> Path.of(f.location())).toList(), List.of(), "1.0.0", clientJar);
    }

    @Override
    public Optional<UpdateInfo> checkLauncherUpdate() throws IOException {
        calls.add("checkLauncherUpdate");
        if (!updatesConfigured) {
            throw new NotPublishedException("", "", "No releases URL is configured");
        }
        if (launcherCheckFailure != null) {
            throw launcherCheckFailure;
        }
        return launcherUpdate;
    }

    @Override
    public Optional<InstalledClient> installedClient() {
        if (activeJar != null) {
            final String name = activeJar.getFileName().toString();
            if (instance != null && instance.hasVantaClient() && instance.vantaClientJar().equals(name)) {
                return Optional.of(new InstalledClient(instance.vantaClientVersion(), Optional.of(activeJar), InstalledClient.Source.INSTANCE));
            }
            return Optional.of(new InstalledClient(VantaClientService.versionFromJarName(name), Optional.of(activeJar),
                InstalledClient.Source.MODS_JAR));
        }
        if (instance != null && instance.hasVantaClient()) {
            return Optional.of(new InstalledClient(instance.vantaClientVersion(), Optional.empty(), InstalledClient.Source.INSTANCE));
        }
        return Optional.empty();
    }

    /**
     * Like the core: the latest release is {@link #clientUpdate} (or {@link #latestClientVersion}); an update exists only
     * for an installed client that is older.
     */
    @Override
    public UpdateService.ClientCheck checkClient(final Optional<InstalledClient> installed) throws IOException {
        calls.add("checkClient");
        clientCheckHook.run();
        if (!updatesConfigured) {
            throw new NotPublishedException("client", "", "No releases URL is configured");
        }
        if (clientCheckFailure != null) {
            throw clientCheckFailure;
        }
        final SemVer latest = clientUpdate.map(UpdateInfo::latestVersion).orElse(SemVer.parse(latestClientVersion));
        final boolean downloadable = clientUpdate.map(UpdateInfo::isDownloadable).orElse(true);
        if (installed.isEmpty()) {
            return new UpdateService.ClientCheck(installed, latest, downloadable, Optional.empty());
        }
        final SemVer current = installed.get().comparisonVersion();
        return new UpdateService.ClientCheck(installed, latest, downloadable,
            clientUpdate.filter(u -> u.latestVersion().isNewerThan(current)));
    }

    @Override
    public LauncherPackaging packaging() {
        return packaging;
    }

    @Override
    public Path downloadUpdate(final UpdateInfo update, final DownloadProgressListener listener, final CancellationToken token) throws IOException {
        calls.add("downloadUpdate");
        if (!update.isDownloadable()) {
            throw new NotPublishedException(update.product(), update.latestVersion().toString(), "announced but not downloadable yet");
        }
        final Path target = paths.updatesCacheDir().resolve(update.latestVersion().toString()).resolve(update.file().name());
        final DownloadRequest request = new DownloadRequest(URI.create(update.file().downloadUrl()), target, update.file().size(), null,
            update.file().name());
        listener.onProgress(request, update.file().size() / 2, update.file().size());
        listener.onProgress(request, update.file().size(), update.file().size());
        return target;
    }

    @Override
    public Path prepareInstaller(final UpdateInfo update) {
        calls.add("prepareInstaller");
        return paths.updatesCacheDir().resolve(update.latestVersion().toString()).resolve(update.file().name());
    }

    @Override
    public Path installClientUpdate(final UpdateInfo update, final DownloadProgressListener listener, final CancellationToken token) throws IOException {
        calls.add("installClientUpdate");
        if (installedClient().isEmpty()) {
            throw new IOException("No VANTA Client is installed, so there is nothing to update.");
        }
        final InstalledClient previous = installedClient().orElseThrow();
        downloadUpdate(update, listener, token);
        final String version = update.latestVersion().toString();
        if (keepReplacedClient && previous.jar().isPresent() && previous.semVer().isPresent() && kept.stream().noneMatch(k -> k.version().equals(previous.version()))) {
            // Like the core: a jar without a kept copy is copied as a local copy (no download URL in its manifest).
            final String name = "vanta-client-" + previous.version() + ".jar";
            kept.add(new VantaClientService.KeptVersion(previous.version(), VantaClientService.localCopyManifest(previous.version(),
                new ReleaseManifest.ReleaseFile(name, "", 1L, sha256)), paths.clientVersionsDir().resolve(previous.version()).resolve(name),
                CLOCK.instant()));
        }
        kept.removeIf(k -> k.version().equals(version));
        kept.add(0, new VantaClientService.KeptVersion(version, update.manifest(),
            paths.clientVersionsDir().resolve(version).resolve("vanta-client-" + version + ".jar"), CLOCK.instant()));
        if (instance != null) {
            instance = instance.withVantaClient(version, "vanta-client-" + version + ".jar");
        }
        activeJar = paths.modsDir().resolve("vanta-client-" + version + ".jar");
        return activeJar;
    }

    @Override
    public String fetchText(final URI uri) throws IOException {
        calls.add("fetchText");
        final String text = documents.get(uri);
        if (text == null) {
            throw new IOException("HTTP 404 for " + uri);
        }
        return text;
    }

    @Override
    public String sha256(final Path file) {
        return sha256;
    }

    @Override
    public void close() {
        closed = true;
    }
}
