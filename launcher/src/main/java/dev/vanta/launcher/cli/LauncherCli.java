package dev.vanta.launcher.cli;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.LauncherServices;
import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.auth.AccountType;
import dev.vanta.launcher.core.auth.AuthException;
import dev.vanta.launcher.core.auth.AuthNotConfiguredException;
import dev.vanta.launcher.core.auth.OfflineAccountPolicy;
import dev.vanta.launcher.core.install.InstallException;
import dev.vanta.launcher.core.install.InstalledClient;
import dev.vanta.launcher.core.install.InstallListener;
import dev.vanta.launcher.core.install.InstallProgress;
import dev.vanta.launcher.core.install.InstallRequest;
import dev.vanta.launcher.core.install.InsufficientDiskSpaceException;
import dev.vanta.launcher.core.install.NotPublishedException;
import dev.vanta.launcher.core.install.OfficialLauncher;
import dev.vanta.launcher.core.install.OfficialLauncherNotFoundException;
import dev.vanta.launcher.core.install.OfficialProfileService;
import dev.vanta.launcher.core.install.ReleasesNotConfiguredException;
import dev.vanta.launcher.core.java.JavaInstall;
import dev.vanta.launcher.core.java.UnsafeArchiveException;
import dev.vanta.launcher.core.launch.GameProcess;
import dev.vanta.launcher.core.launch.LaunchCommand;
import dev.vanta.launcher.core.launch.LaunchRequest;
import dev.vanta.launcher.core.launch.RestartRequest;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.net.HttpStatusException;
import dev.vanta.launcher.core.net.IntegrityException;
import dev.vanta.launcher.core.net.NetworkErrors;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.core.settings.ReleasesBaseUrl;
import dev.vanta.launcher.core.settings.Resolution;
import dev.vanta.launcher.core.update.UpdateInfo;
import dev.vanta.launcher.core.update.UpdateService;
import dev.vanta.launcher.core.util.ByteSizes;
import dev.vanta.launcher.core.util.OsInfo;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Command line interface. CI uses it to integration-test the real install → launch pipeline; users can use it to
 * script installs or diagnose Java problems.
 *
 * <pre>
 * vanta-launcher --install [--client-jar path] [--without-client] [--no-assets]
 * vanta-launcher --install-official-profile [--minecraft-dir path] [--client-jar path]
 * vanta-launcher --open-official-launcher [--minecraft-dir path]
 * vanta-launcher --launch [--dev-offline --username X] [--world name | --server host] [--exit-after seconds]
 * vanta-launcher --check-java [--java path]
 * vanta-launcher --install-java
 * vanta-launcher --check-update [--releases-url url]
 * vanta-launcher --print-command [--dev-offline --username X]
 * vanta-launcher --version | --help
 * common: --data-dir path, --memory mb, --java path, --resolution WxH
 * </pre>
 */
public final class LauncherCli {

    private final Function<LauncherPaths, LauncherServices> servicesFactory;
    private final OsInfo os;
    private final java.util.Map<String, String> env;
    private final Path userHome;

    /**
     * Production CLI.
     */
    public LauncherCli() {
        this(LauncherServices::create, OsInfo.detect(), System.getenv(), Path.of(System.getProperty("user.home", ".")));
    }

    /**
     * @param servicesFactory creates services for a data directory (tests inject fakes)
     * @param os              host platform
     * @param env             environment variables
     * @param userHome        user home (for the default data directory)
     */
    public LauncherCli(final Function<LauncherPaths, LauncherServices> servicesFactory, final OsInfo os,
                       final java.util.Map<String, String> env, final Path userHome) {
        this.servicesFactory = Objects.requireNonNull(servicesFactory, "servicesFactory");
        this.os = Objects.requireNonNull(os, "os");
        this.env = java.util.Map.copyOf(Objects.requireNonNull(env, "env"));
        this.userHome = Objects.requireNonNull(userHome, "userHome");
    }

    /**
     * @param args raw arguments
     * @return whether the arguments select the CLI rather than the UI
     */
    public static boolean wantsCli(final String[] args) {
        for (String a : args) {
            if (a.startsWith("-") && !"--ui".equals(a)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs the production CLI.
     *
     * @param args raw arguments
     * @param out  standard output
     * @param err  standard error
     * @return exit code
     */
    public static int run(final String[] args, final PrintStream out, final PrintStream err) {
        return new LauncherCli().execute(args, out, err).code();
    }

    /**
     * Executes a command line.
     *
     * @param rawArgs raw arguments
     * @param out     standard output
     * @param err     standard error
     * @return exit code
     */
    public ExitCode execute(final String[] rawArgs, final PrintStream out, final PrintStream err) {
        final CliArgs args = CliArgs.parse(rawArgs);
        if (!args.isValid()) {
            args.errors().forEach(e -> err.println("error: " + e));
            err.println();
            printHelp(err);
            return ExitCode.USAGE;
        }
        switch (args.command()) {
            case HELP -> {
                printHelp(out);
                return ExitCode.OK;
            }
            case VERSION -> {
                out.println("VANTA Launcher " + LauncherVersion.VERSION);
                out.println(LauncherVersion.statusLine());
                out.println("Built for JavaFX platform " + LauncherVersion.JAVAFX_PLATFORM + " · running on " + os.name() + "/" + os.arch()
                    + " · Java " + Runtime.version());
                return ExitCode.OK;
            }
            default -> {
                // handled below with services
            }
        }
        final LauncherPaths paths = args.option("data-dir").map(Path::of).map(LauncherPaths::new)
            .orElseGet(() -> LauncherPaths.detect(os, env, userHome));
        try (LauncherServices services = servicesFactory.apply(paths)) {
            applyOverrides(services, args);
            return switch (args.command()) {
                case INSTALL -> install(services, args, out);
                case INSTALL_OFFICIAL_PROFILE -> installOfficialProfile(services, args, out);
                case OPEN_OFFICIAL_LAUNCHER -> openOfficialLauncher(services, args, out, err);
                case LAUNCH -> launch(services, args, out, err);
                case CHECK_JAVA -> checkJava(services, args, out);
                case INSTALL_JAVA -> installJava(services, out);
                case CHECK_UPDATE -> checkUpdate(services, out);
                case PRINT_COMMAND -> printCommand(services, args, out);
                default -> ExitCode.USAGE;
            };
        } catch (CancellationException e) {
            err.println("Cancelled.");
            return ExitCode.CANCELLED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println("Interrupted.");
            return ExitCode.CANCELLED;
        } catch (IllegalArgumentException e) {
            err.println("error: " + e.getMessage());
            return ExitCode.USAGE;
        } catch (Exception e) {
            return report(e, err);
        }
    }

    // --------------------------------------------------------------------------------------------------

    private void applyOverrides(final LauncherServices services, final CliArgs args) {
        LauncherSettings s = services.settings();
        if (args.option("memory").isPresent()) {
            s = s.withMemoryMb(Integer.parseInt(args.option("memory").get()));
        }
        if (args.option("java").isPresent()) {
            s = s.withJavaPath(args.option("java").get());
        }
        if (args.option("releases-url").isPresent()) {
            s = s.withReleasesBaseUrl(args.option("releases-url").get());
        }
        if (args.option("resolution").isPresent()) {
            s = s.withResolution(Resolution.parse(args.option("resolution").get()));
        }
        if (args.has("dev-offline")) {
            s = s.withDeveloperMode(true);
        }
        if (args.has("without-performance-pack")) {
            s = s.withInstallPerformancePack(false);
        }
        services.overrideSettings(s);
    }

    private ExitCode install(final LauncherServices services, final CliArgs args, final PrintStream out) throws Exception {
        final Optional<Path> localJar = args.option("client-jar").map(Path::of).map(Path::toAbsolutePath);
        final InstallRequest request = new InstallRequest(LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER,
            LauncherVersion.FABRIC_API, localJar.orElse(null), !args.has("without-client"), !args.has("no-assets"),
            services.settings().performancePack());
        out.println("Installing into " + services.paths().dataDir());
        out.println(LauncherVersion.statusLine() + " · Fabric API " + LauncherVersion.FABRIC_API);
        out.println(performancePackLine(request.includePerformancePack()));
        if (localJar.isEmpty() && !args.has("without-client")) {
            out.println("VANTA Client release manifests: " + describe(services.releasesBaseUrl()));
        }
        final InstallListener listener = progressPrinter(out);
        final InstanceInfo info = services.installer().install(request, listener, new CancellationToken());
        out.println("Installed instance '" + info.instanceId() + "': Minecraft " + info.minecraftVersion() + ", Fabric Loader "
            + info.fabricLoaderVersion() + ", Fabric API " + info.fabricApiVersion()
            + (info.hasVantaClient() ? ", VANTA Client " + info.vantaClientVersion() : ", no VANTA Client"));
        out.println("Game directory: " + services.paths().instanceDir());
        return ExitCode.OK;
    }

    private static InstallListener progressPrinter(final PrintStream out) {
        return new InstallListener() {
            private InstallProgress last;

            @Override
            public void onProgress(final InstallProgress p) {
                if (last == null || last.step() != p.step()) {
                    out.println("[" + (p.stepIndex() + 1) + "/" + p.stepCount() + "] " + p.step().label());
                } else if (p.total() > 1 && (p.done() % Math.max(1, p.total() / 10) == 0 || p.done() == p.total())) {
                    out.println("      " + p.done() + "/" + p.total() + " files");
                }
                last = p;
            }

            @Override
            public void onLog(final String message) {
                out.println("      " + message);
            }
        };
    }

    private static String performancePackLine(final boolean on) {
        return on ? "Performance pack: " + String.join(", ", dev.vanta.launcher.core.modrinth.PerformancePack.SLUGS)
            + " from Modrinth (newest versions for Minecraft " + LauncherVersion.MINECRAFT + ", SHA-512 verified; --without-performance-pack skips it)"
            : "Performance pack: off";
    }

    private ExitCode installOfficialProfile(final LauncherServices services, final CliArgs args, final PrintStream out) throws Exception {
        final Path minecraftDir = args.option("minecraft-dir").map(Path::of).map(Path::toAbsolutePath)
            .orElseGet(() -> LauncherPaths.officialMinecraftDirCandidate(os, env, userHome));
        printRunningLauncherWarning(services, out);
        final Path localJar = args.option("client-jar").map(Path::of).map(Path::toAbsolutePath).orElse(null);
        final OfficialProfileService.Request request = services.officialProfileRequest(minecraftDir, localJar);
        out.println("Setting up '" + OfficialProfileService.profileName(request.minecraftVersion()) + "' for the official Minecraft Launcher in "
            + request.minecraftDir());
        out.println(LauncherVersion.statusLine() + " · Fabric API " + LauncherVersion.FABRIC_API);
        out.println(performancePackLine(request.includePerformancePack()));
        if (localJar == null) {
            out.println("VANTA Client release manifests: " + describe(services.releasesBaseUrl()));
        }
        final OfficialProfileService.Plan plan = services.officialProfiles().plan(request);
        out.println("VANTA Client " + plan.vantaClientVersion() + (localJar == null ? " (published release)" : " (local jar " + localJar + ")"));
        printPlannedFiles(out, "Files that will be written (a file that is already identical is left as it is):", plan.written());
        printPlannedFiles(out, "Files that will be removed:", plan.removed());
        for (String note : plan.notes()) {
            out.println("Performance pack note: " + note);
        }
        final OfficialProfileService.Result result = services.officialProfiles().install(request, progressPrinter(out), new CancellationToken());
        out.println((result.created() ? "Added" : "Updated") + " the profile '" + result.profileName() + "' (" + result.profileKey()
            + ", version " + result.versionId() + ", VANTA Client " + result.vantaClientVersion() + ", -Xmx"
            + services.settings().memoryMb() + "M)");
        for (Path backup : result.backups()) {
            out.println("Backup of the original file: " + backup);
        }
        out.println("Game directory: " + result.gameDir());
        out.println(result.nextStep());
        out.println("The Minecraft Launcher downloads Minecraft " + LauncherVersion.MINECRAFT + ", its libraries, assets and Java itself and"
            + " signs you in with Microsoft. If it is open right now, restart it so it reads the new profile.");
        if (!services.officialLauncher().running().isEmpty()) {
            out.println("WARNING: the Minecraft Launcher is still running. " + OfficialProfileService.restartHint());
        }
        out.println("Start it with: --open-official-launcher");
        return ExitCode.OK;
    }

    /**
     * Prints a warning when the official Minecraft Launcher runs: it reads {@code launcher_profiles.json} only when it starts.
     */
    private static void printRunningLauncherWarning(final LauncherServices services, final PrintStream out) {
        final List<OfficialLauncher.Running> running = services.officialLauncher().running();
        if (running.isEmpty()) {
            return;
        }
        out.println("WARNING: the Minecraft Launcher is running ("
            + String.join(", ", running.stream().map(OfficialLauncher.Running::describe).toList()) + ").");
        out.println("         " + OfficialProfileService.restartHint());
        out.println("         VANTA never closes it for you; the setup continues.");
    }

    private ExitCode openOfficialLauncher(final LauncherServices services, final CliArgs args, final PrintStream out, final PrintStream err)
        throws InterruptedException {
        final Path minecraftDir = args.option("minecraft-dir").map(Path::of).map(Path::toAbsolutePath)
            .orElseGet(() -> LauncherPaths.officialMinecraftDirCandidate(os, env, userHome));
        final List<OfficialLauncher.Running> before = services.officialLauncher().running();
        if (!before.isEmpty()) {
            out.println("The Minecraft Launcher is already running (" + before.get(0).describe() + "). "
                + "If it does not show the profile '" + OfficialProfileService.profileName(LauncherVersion.MINECRAFT) + "', close it completely "
                + "(also from the system tray) and run this command again.");
            return ExitCode.OK;
        }
        final OfficialLauncher.OpenResult result = services.officialLauncher().open(minecraftDir);
        switch (result.outcome()) {
            case OPENED, STARTED_UNCONFIRMED -> {
                out.println(result.detail());
                out.println(OfficialProfileService.nextStep(OfficialProfileService.profileName(LauncherVersion.MINECRAFT)));
                return ExitCode.OK;
            }
            case NOT_FOUND -> {
                err.println(result.detail());
                return ExitCode.NOT_CONFIGURED;
            }
            default -> {
                err.println(result.detail());
                return ExitCode.FAILURE;
            }
        }
    }

    private static void printPlannedFiles(final PrintStream out, final String header, final List<OfficialProfileService.PlannedFile> files) {
        if (files.isEmpty()) {
            return;
        }
        out.println(header);
        for (OfficialProfileService.PlannedFile f : files) {
            out.println("  - " + f.location());
            out.println("      " + f.kind().description());
        }
    }

    private ExitCode launch(final LauncherServices services, final CliArgs args, final PrintStream out, final PrintStream err) throws Exception {
        final Optional<InstanceInfo> instance = services.installer().loadInstance();
        if (instance.isEmpty()) {
            err.println("Nothing is installed yet. Run --install first.");
            return ExitCode.FAILURE;
        }
        final Optional<JavaInstall> runtime = pickJava(services);
        if (runtime.isEmpty()) {
            err.println("No Java " + LauncherVersion.JAVA_MAJOR + " runtime found. Run --install-java or set --java <path>.");
            return ExitCode.JAVA_NOT_FOUND;
        }
        final Account account = resolveAccount(services, args, false);
        final LaunchRequest request = new LaunchRequest(instance.get(), account, runtime.get().executable(), services.settings(),
            args.option("world").orElse(null), args.option("server").orElse(null));
        final LaunchCommand command = services.launch().buildCommand(request);
        out.println("Launching as " + account.name() + " (" + account.type().name().toLowerCase(Locale.ROOT) + ") with "
            + runtime.get().describe());
        GameProcess process = services.launch().start(command, List.of(line -> out.println(line.text())));
        out.println("Game log: " + process.logFile());
        final Optional<Integer> exitAfter = args.option("exit-after").map(Integer::parseInt);
        if (exitAfter.isPresent()) {
            try {
                final int code = process.exitCode().get(exitAfter.get(), TimeUnit.SECONDS);
                return code == 0 ? ExitCode.OK : gameFailed(code, err);
            } catch (TimeoutException e) {
                out.println("Game still running after " + exitAfter.get() + "s; stopping it as requested by --exit-after.");
                process.destroy();
                if (!waitQuietly(process, 20)) {
                    process.destroyForcibly();
                }
                return ExitCode.OK;
            }
        }
        int code = waitForExit(process);
        // "Restart game" inside VANTA: the game wrote config/vanta/restart.request and closed itself.
        for (int restarts = 0; restarts < MAX_RESTARTS && RestartRequest.consume(services.paths().instanceDir()); restarts++) {
            out.println("The game asked for a restart; starting it again.");
            process = services.launch().start(services.launch().buildCommand(new LaunchRequest(instance.get(),
                    resolveAccount(services, args, false), runtime.get().executable(), services.settings(),
                    args.option("world").orElse(null), args.option("server").orElse(null))),
                List.of(line -> out.println(line.text())));
            out.println("Game log: " + process.logFile());
            code = waitForExit(process);
        }
        return code == 0 ? ExitCode.OK : gameFailed(code, err);
    }

    /** Restarts in a row the command line performs before it stops (a client asking on every start would loop). */
    static final int MAX_RESTARTS = 5;

    private ExitCode printCommand(final LauncherServices services, final CliArgs args, final PrintStream out) throws Exception {
        final Optional<InstanceInfo> instance = services.installer().loadInstance();
        if (instance.isEmpty()) {
            out.println("Nothing is installed yet. Run --install first.");
            return ExitCode.FAILURE;
        }
        final Path javaExecutable = pickJava(services).map(JavaInstall::executable)
            .orElse(Path.of(os.javaExecutableName()));
        final Account account = resolveAccount(services, args, true);
        final LaunchRequest request = new LaunchRequest(instance.get(), account, javaExecutable, services.settings(),
            args.option("world").orElse(null), args.option("server").orElse(null));
        final LaunchCommand command = services.launch().buildCommand(request);
        out.println("# working directory: " + command.workingDirectory());
        out.println("# account: " + account.name() + " (" + account.type().name().toLowerCase(Locale.ROOT) + ")");
        for (String token : command.redacted()) {
            out.println(token);
        }
        return ExitCode.OK;
    }

    private ExitCode checkJava(final LauncherServices services, final CliArgs args, final PrintStream out) {
        final Optional<String> explicit = args.option("java").or(() -> services.settings().javaPathOverride());
        if (explicit.isPresent()) {
            final Optional<JavaInstall> j = services.javaDetector().probeUserPath(Path.of(explicit.get()));
            if (j.isPresent()) {
                out.println("Configured: " + j.get().describe());
                return j.get().satisfies(LauncherVersion.JAVA_MAJOR) ? ExitCode.OK : ExitCode.JAVA_NOT_FOUND;
            }
            out.println("The configured Java path '" + explicit.get() + "' is not a working Java runtime.");
        }
        final List<JavaInstall> all = services.javaDetector().detect();
        if (all.isEmpty()) {
            out.println("No Java runtimes found.");
        } else {
            out.println("Detected Java runtimes:");
            for (JavaInstall j : all) {
                out.println("  - " + j.describe());
            }
        }
        final Optional<JavaInstall> pick = dev.vanta.launcher.core.java.JavaDetector.pick(all, LauncherVersion.JAVA_MAJOR);
        if (pick.isEmpty()) {
            out.println("No runtime satisfies Java " + LauncherVersion.JAVA_MAJOR + ". Run --install-java to install Eclipse Temurin 21.");
            return ExitCode.JAVA_NOT_FOUND;
        }
        out.println("Selected: " + pick.get().describe());
        return ExitCode.OK;
    }

    private ExitCode installJava(final LauncherServices services, final PrintStream out) throws Exception {
        out.println("Querying Adoptium for the latest Eclipse Temurin " + LauncherVersion.JAVA_MAJOR + " JRE ("
            + os.adoptiumOs() + "/" + os.adoptiumArch() + ")...");
        final JavaInstall install = services.adoptium().install(new DownloadProgressListener() {
            private long lastReported = -1;

            @Override
            public void onProgress(final DownloadRequest request, final long bytesDone, final long bytesTotal) {
                // One line per 10 MB (decimal units, like every size the launcher shows).
                final long step = bytesDone / 10_000_000L;
                if (step != lastReported) {
                    lastReported = step;
                    out.println("  " + ByteSizes.format(bytesDone) + (bytesTotal > 0 ? " / " + ByteSizes.format(bytesTotal) : ""));
                }
            }
        }, new CancellationToken());
        out.println("Installed " + install.describe());
        return ExitCode.OK;
    }

    private ExitCode checkUpdate(final LauncherServices services, final PrintStream out) throws Exception {
        final ReleasesBaseUrl releases = services.releasesBaseUrl();
        if (!services.updates().isConfigured()) {
            out.println("The releases URL '" + releases.url() + "' (" + describeSource(releases) + ") is not a valid http(s) URL."
                + " Update checks are disabled. Fix it, or leave \"releasesBaseUrl\" empty to use the built-in default.");
            return ExitCode.NOT_CONFIGURED;
        }
        out.println("Release manifests: " + describe(releases));
        int unpublished = 0;
        try {
            out.println("Launcher " + LauncherVersion.VERSION + ": " + describe(services.updates().checkLauncher()));
        } catch (NotPublishedException e) {
            unpublished++;
            out.println("Launcher " + LauncherVersion.VERSION + ": no published release found. " + e.getMessage());
        }
        // The same installed state the launcher UI shows: instance.json / the vanta-client jar actually in mods/.
        final Optional<InstalledClient> installed = services.vantaClient().installedClient();
        try {
            out.println(describeClient(services.updates().checkClient(installed)));
        } catch (NotPublishedException e) {
            unpublished++;
            out.println(clientLabel(installed) + ": no published release found. " + e.getMessage());
        }
        return unpublished == 2 ? ExitCode.NOT_PUBLISHED : ExitCode.OK;
    }

    /**
     * @param check client check
     * @return e.g. {@code Client 1.0.0: update available: 1.0.1 (vanta-client-1.0.1.jar)} or
     *         {@code Client: not installed (install it with --install or --install-official-profile); latest release 1.0.1}
     */
    static String describeClient(final UpdateService.ClientCheck check) {
        if (!check.isInstalled()) {
            return "Client: not installed (install it with " + CliCommand.INSTALL.flag() + " or " + CliCommand.INSTALL_OFFICIAL_PROFILE.flag()
                + "); latest release " + check.latest() + (check.downloadable() ? "" : " (announced, not downloadable yet)");
        }
        return clientLabel(check.installed()) + ": " + describe(check.update());
    }

    private static String clientLabel(final Optional<InstalledClient> installed) {
        return installed.map(c -> "Client " + (c.version().isEmpty() ? "(unknown version)" : c.version())).orElse("Client");
    }

    // --------------------------------------------------------------------------------------------------

    private Optional<JavaInstall> pickJava(final LauncherServices services) {
        final Optional<String> override = services.settings().javaPathOverride();
        if (override.isPresent()) {
            final Optional<JavaInstall> j = services.javaDetector().probeUserPath(Path.of(override.get()));
            if (j.isPresent() && j.get().satisfies(LauncherVersion.JAVA_MAJOR)) {
                return j;
            }
        }
        return services.javaDetector().pick(LauncherVersion.JAVA_MAJOR);
    }

    private Account resolveAccount(final LauncherServices services, final CliArgs args, final boolean allowPlaceholder) throws Exception {
        if (args.has("dev-offline")) {
            return OfflineAccountPolicy.createOffline(services.settings(), env, services.accounts(), args.option("username").orElse("Dev"));
        }
        final Optional<Account> active = services.accounts().load().active();
        if (active.isPresent()) {
            Account account = active.get();
            if (account.isExpired(Instant.now(services.clock())) && account.canRefresh()) {
                account = services.auth().refresh(account);
                services.accounts().put(account);
                services.accounts().save();
            }
            return account;
        }
        if (allowPlaceholder) {
            return new Account(Account.offlineUuid("Player"), "Player", "", Account.OFFLINE_TOKEN, Long.MAX_VALUE, "", AccountType.DEVELOPMENT);
        }
        throw new AuthException("No account is signed in. Sign in with your Microsoft account in the VANTA Launcher, or use "
            + "--dev-offline with VANTA_DEV_OFFLINE=1 for development.");
    }

    private static int waitForExit(final GameProcess process) throws InterruptedException {
        try {
            return process.exitCode().get();
        } catch (ExecutionException e) {
            return -1;
        }
    }

    private static boolean waitQuietly(final GameProcess process, final int seconds) {
        try {
            process.exitCode().get(seconds, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            return false;
        }
    }

    private static ExitCode gameFailed(final int code, final PrintStream err) {
        err.println("The game exited with code " + code + ".");
        return ExitCode.GAME_FAILED;
    }

    private static String describe(final Optional<UpdateInfo> update) {
        if (update.isEmpty()) {
            return "up to date";
        }
        final UpdateInfo u = update.get();
        if (!u.hasPlatformAsset()) {
            return "update available: " + u.latestVersion() + " (no download for this platform"
                + (u.releasePage().isEmpty() ? ")" : "; see " + u.releasePage() + ")");
        }
        return "update available: " + u.latestVersion() + (u.isDownloadable() ? " (" + u.file().name() + ")" : " (announced, not downloadable yet)");
    }

    private static String describe(final ReleasesBaseUrl releases) {
        return releases.url() + " (" + describeSource(releases) + ")";
    }

    private static String describeSource(final ReleasesBaseUrl releases) {
        return switch (releases.source()) {
            case SETTINGS -> "from settings or --releases-url";
            case ENVIRONMENT -> "from " + LauncherSettings.RELEASES_BASE_URL_ENV;
            case DEFAULT -> "built-in default";
        };
    }

    /**
     * Maps an exception to an exit code and prints a one line explanation.
     *
     * @param e   exception
     * @param err error stream
     * @return exit code
     */
    static ExitCode report(final Exception e, final PrintStream err) {
        final Throwable root = e instanceof InstallException ie && ie.getCause() != null ? ie.getCause() : e;
        final String prefix = e instanceof InstallException ie ? ie.step().label() + " failed: " : "";
        if (root instanceof AuthNotConfiguredException) {
            err.println(prefix + root.getMessage());
            return ExitCode.NOT_CONFIGURED;
        }
        if (root instanceof ReleasesNotConfiguredException || root instanceof OfficialLauncherNotFoundException) {
            err.println(prefix + root.getMessage());
            return ExitCode.NOT_CONFIGURED;
        }
        if (root instanceof NotPublishedException) {
            err.println(prefix + root.getMessage());
            return ExitCode.NOT_PUBLISHED;
        }
        if (root instanceof InsufficientDiskSpaceException) {
            err.println(root.getMessage());
            return ExitCode.DISK_SPACE;
        }
        if (root instanceof IntegrityException || root instanceof UnsafeArchiveException) {
            err.println(prefix + root.getMessage());
            return ExitCode.INTEGRITY;
        }
        if (root instanceof AuthException) {
            err.println(prefix + root.getMessage());
            return ExitCode.AUTH;
        }
        if (NetworkErrors.isNetworkFailure(e)) {
            // The InstallException message names the step and, where known, the URL that failed.
            err.println(e instanceof InstallException ? e.getMessage() : "Request failed: " + messageOf(e));
            err.println("network error: " + NetworkErrors.describe(e)
                + ". Check the internet connection, proxy and firewall settings, then try again.");
            return ExitCode.NETWORK;
        }
        err.println(prefix + messageOf(root));
        return ExitCode.FAILURE;
    }

    private static String messageOf(final Throwable t) {
        return t.getMessage() == null || t.getMessage().isBlank() ? t.toString() : t.getMessage();
    }

    /**
     * Prints usage.
     *
     * @param out stream
     */
    public static void printHelp(final PrintStream out) {
        out.println("VANTA Launcher " + LauncherVersion.VERSION + " — " + LauncherVersion.statusLine());
        out.println();
        out.println("Usage: vanta-launcher <command> [options]");
        out.println();
        out.println("Commands:");
        for (CliCommand c : CliCommand.values()) {
            out.printf("  %-28s %s%n", c.flag(), c.description());
        }
        out.println();
        out.println("Options:");
        out.printf("  %-28s %s%n", "--data-dir <path>", "Launcher data directory (default: platform data dir or $" + LauncherPaths.HOME_ENV + ")");
        out.printf("  %-28s %s%n", "--client-jar <path>", "--install / --install-official-profile: install a local VANTA client jar instead of the published release");
        out.printf("  %-28s %s%n", "--minecraft-dir <path>", "--install-official-profile / --open-official-launcher: the official Minecraft directory (default: the platform .minecraft)");
        out.printf("  %-28s %s%n", "--without-client", "--install: skip the VANTA client (plain Fabric instance)");
        out.printf("  %-28s %s%n", "--no-assets", "--install: skip game assets (development only; the game will lack sounds and languages)");
        out.printf("  %-28s %s%n", "--without-performance-pack", "--install / --install-official-profile: skip the performance pack (Sodium, Lithium,");
        out.printf("  %-28s %s%n", "", "FerriteCore, ImmediatelyFast, Entity Culling, Iris from Modrinth); default: Settings, on");
        out.printf("  %-28s %s%n", "--dev-offline", "--launch/--print-command: development offline account (requires " + OfflineAccountPolicy.DEV_OFFLINE_ENV + "=1)");
        out.printf("  %-28s %s%n", "--username <name>", "Player name for --dev-offline (default Dev)");
        out.printf("  %-28s %s%n", "--world <name>", "--launch: open this singleplayer world directly");
        out.printf("  %-28s %s%n", "--server <host[:port]>", "--launch: join this server directly");
        out.printf("  %-28s %s%n", "--exit-after <seconds>", "--launch: stop the game after N seconds and report success if it was running");
        out.printf("  %-28s %s%n", "--java <path>", "Java home or executable to use");
        out.printf("  %-28s %s%n", "--memory <mb>", "Maximum heap for the game");
        out.printf("  %-28s %s%n", "--resolution <WxH>", "Initial window size");
        out.printf("  %-28s %s%n", "--releases-url <url>", "Base URL of launcher-latest.json / client-latest.json for this run (default: settings,");
        out.printf("  %-28s %s%n", "", "then $" + LauncherSettings.RELEASES_BASE_URL_ENV + ", then " + LauncherSettings.DEFAULT_RELEASES_BASE_URL + ")");
        out.println();
        out.println("Exit codes:");
        for (ExitCode c : ExitCode.values()) {
            out.printf("  %-3d %s%n", c.code(), c.description());
        }
    }
}
