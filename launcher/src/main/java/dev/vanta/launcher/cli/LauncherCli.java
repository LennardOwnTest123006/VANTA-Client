package dev.vanta.launcher.cli;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.LauncherServices;
import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.auth.AccountType;
import dev.vanta.launcher.core.auth.AuthException;
import dev.vanta.launcher.core.auth.AuthNotConfiguredException;
import dev.vanta.launcher.core.auth.OfflineAccountPolicy;
import dev.vanta.launcher.core.install.InstallException;
import dev.vanta.launcher.core.install.InstallListener;
import dev.vanta.launcher.core.install.InstallProgress;
import dev.vanta.launcher.core.install.InstallRequest;
import dev.vanta.launcher.core.install.InsufficientDiskSpaceException;
import dev.vanta.launcher.core.install.NotPublishedException;
import dev.vanta.launcher.core.java.JavaInstall;
import dev.vanta.launcher.core.java.UnsafeArchiveException;
import dev.vanta.launcher.core.launch.GameProcess;
import dev.vanta.launcher.core.launch.LaunchCommand;
import dev.vanta.launcher.core.launch.LaunchRequest;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.net.HttpStatusException;
import dev.vanta.launcher.core.net.IntegrityException;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.core.settings.Resolution;
import dev.vanta.launcher.core.update.SemVer;
import dev.vanta.launcher.core.update.UpdateInfo;
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
        services.overrideSettings(s);
    }

    private ExitCode install(final LauncherServices services, final CliArgs args, final PrintStream out) throws Exception {
        final Optional<Path> localJar = args.option("client-jar").map(Path::of).map(Path::toAbsolutePath);
        final InstallRequest request = new InstallRequest(LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER,
            LauncherVersion.FABRIC_API, localJar.orElse(null), !args.has("without-client"), !args.has("no-assets"));
        out.println("Installing into " + services.paths().dataDir());
        out.println(LauncherVersion.statusLine() + " · Fabric API " + LauncherVersion.FABRIC_API);
        final InstallListener listener = new InstallListener() {
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
        final InstanceInfo info = services.installer().install(request, listener, new CancellationToken());
        out.println("Installed instance '" + info.instanceId() + "': Minecraft " + info.minecraftVersion() + ", Fabric Loader "
            + info.fabricLoaderVersion() + ", Fabric API " + info.fabricApiVersion()
            + (info.hasVantaClient() ? ", VANTA Client " + info.vantaClientVersion() : ", no VANTA Client"));
        out.println("Game directory: " + services.paths().instanceDir());
        return ExitCode.OK;
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
        final GameProcess process = services.launch().start(command, List.of(line -> out.println(line.text())));
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
        final int code = waitForExit(process);
        return code == 0 ? ExitCode.OK : gameFailed(code, err);
    }

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
                final long mb = bytesDone / (1024L * 1024L);
                if (mb != lastReported && mb % 10 == 0) {
                    lastReported = mb;
                    out.println("  " + mb + " MB" + (bytesTotal > 0 ? " / " + (bytesTotal / (1024L * 1024L)) + " MB" : ""));
                }
            }
        }, new CancellationToken());
        out.println("Installed " + install.describe());
        return ExitCode.OK;
    }

    private ExitCode checkUpdate(final LauncherServices services, final PrintStream out) throws Exception {
        if (!services.updates().isConfigured()) {
            out.println("No releases URL is configured (settings \"releasesBaseUrl\" or --releases-url). Update checks are disabled.");
            return ExitCode.NOT_CONFIGURED;
        }
        out.println("Launcher " + LauncherVersion.VERSION + ": " + describe(services.updates().checkLauncher()));
        final Optional<SemVer> client = services.installer().loadInstance().map(InstanceInfo::vantaClientVersion).flatMap(SemVer::tryParse);
        out.println("Client " + client.map(SemVer::toString).orElse("(not installed)") + ": " + describe(services.updates().checkClient(client)));
        return ExitCode.OK;
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
        return "update available: " + u.latestVersion() + (u.isDownloadable() ? " (" + u.file().name() + ")" : " (announced, not downloadable yet)");
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
        if (root instanceof NotPublishedException) {
            err.println(prefix + root.getMessage());
            return root.getMessage() != null && root.getMessage().contains("not configured") || root.getMessage().startsWith("No releases URL")
                ? ExitCode.NOT_CONFIGURED : ExitCode.NOT_PUBLISHED;
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
        if (root instanceof HttpStatusException || root instanceof java.net.ConnectException
            || root instanceof java.net.UnknownHostException || root instanceof java.net.http.HttpTimeoutException
            || root instanceof java.net.http.HttpConnectTimeoutException) {
            err.println(prefix + "network error: " + root.getMessage());
            return ExitCode.NETWORK;
        }
        err.println(prefix + (root.getMessage() == null ? root.toString() : root.getMessage()));
        return ExitCode.FAILURE;
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
            out.printf("  %-18s %s%n", c.flag(), c.description());
        }
        out.println();
        out.println("Options:");
        out.printf("  %-28s %s%n", "--data-dir <path>", "Launcher data directory (default: platform data dir or $" + LauncherPaths.HOME_ENV + ")");
        out.printf("  %-28s %s%n", "--client-jar <path>", "--install: install a local VANTA client jar instead of the published release");
        out.printf("  %-28s %s%n", "--without-client", "--install: skip the VANTA client (plain Fabric instance)");
        out.printf("  %-28s %s%n", "--no-assets", "--install: skip game assets (development only; the game will lack sounds and languages)");
        out.printf("  %-28s %s%n", "--dev-offline", "--launch/--print-command: development offline account (requires " + OfflineAccountPolicy.DEV_OFFLINE_ENV + "=1)");
        out.printf("  %-28s %s%n", "--username <name>", "Player name for --dev-offline (default Dev)");
        out.printf("  %-28s %s%n", "--world <name>", "--launch: open this singleplayer world directly");
        out.printf("  %-28s %s%n", "--server <host[:port]>", "--launch: join this server directly");
        out.printf("  %-28s %s%n", "--exit-after <seconds>", "--launch: stop the game after N seconds and report success if it was running");
        out.printf("  %-28s %s%n", "--java <path>", "Java home or executable to use");
        out.printf("  %-28s %s%n", "--memory <mb>", "Maximum heap for the game");
        out.printf("  %-28s %s%n", "--resolution <WxH>", "Initial window size");
        out.printf("  %-28s %s%n", "--releases-url <url>", "Base URL of launcher-latest.json / client-latest.json");
        out.println();
        out.println("Exit codes:");
        for (ExitCode c : ExitCode.values()) {
            out.printf("  %-3d %s%n", c.code(), c.description());
        }
    }
}
