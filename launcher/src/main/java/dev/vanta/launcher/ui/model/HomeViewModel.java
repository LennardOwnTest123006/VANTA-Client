package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.auth.AccountType;
import dev.vanta.launcher.core.install.InstallListener;
import dev.vanta.launcher.core.install.InstallProgress;
import dev.vanta.launcher.core.install.InstallRequest;
import dev.vanta.launcher.core.install.OfficialLauncher;
import dev.vanta.launcher.core.install.OfficialProfileService;
import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.core.java.JavaInstall;
import dev.vanta.launcher.core.launch.LaunchRequest;
import dev.vanta.launcher.core.launch.StartupGuard;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.ui.Messages;
import dev.vanta.launcher.ui.backend.LauncherBackend;
import dev.vanta.launcher.ui.backend.RunningGame;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * State machine behind the Home screen.
 *
 * <pre>
 *            sign in / Java found            PLAY
 *  NOT_READY ──────────────────► READY ──────────► INSTALLING|VERIFYING ──► (launch) ──► RUNNING ──► READY
 *      ▲                           ▲                    │ failure                             │ exit
 *      └── account/Java lost ──────┘◄───── retry ───── ERROR ◄──────────────────────────────────┘ non-zero
 * </pre>
 *
 * <p>The launch flow is: ensure Java → ensure installed/verified → refresh the account token → start the game →
 * stream its output into the game log → {@code RUNNING} → exit code notification. Every blocking step runs on the
 * background executor; properties change only on the UI thread.</p>
 *
 * <p>"Use with the Minecraft Launcher" ({@link #checkOfficialLauncher} → {@link #prepareOfficialProfile} → confirmation
 * in the view → {@link #installOfficialProfile}) runs through {@code INSTALLING} as well and needs neither an account
 * nor Java: the official Minecraft Launcher signs in and provides Java itself. While PLAY cannot work here
 * ({@link #officialPlayModeProperty()}) the primary button is "PLAY via Minecraft Launcher": the same flow, followed by
 * {@link #openOfficialLauncher()}.</p>
 *
 * <p>When the game the launcher started exits after writing {@code config/vanta/restart.request} ("Restart game" in
 * VANTA), it is started again right away with the same instance, account and Java (at most {@value #MAX_RESTARTS} times
 * per PLAY).</p>
 *
 * <p>The start check ({@link LauncherBackend#startupCheck()}) switches off mods that would stop Minecraft while starting:
 * PLAY runs it after the install/verify step and before launching (and before a restart the game asked for), "PLAY via
 * Minecraft Launcher" / "Use with Minecraft Launcher" after the files are in place and before the Minecraft Launcher
 * opens, and the window runs it once when it opens ({@link #runStartupCheck()}). After the game PLAY started exits, the newest crash report is checked; after the
 * Minecraft Launcher was opened (or set up), {@code crash-reports/} is watched every {@link #WATCH_PERIOD} for
 * {@link #WATCH_LIMIT} ({@link #watchCrashReports()}). Every mod switched off gets a toast and a launcher log line.</p>
 */
public final class HomeViewModel {

    /** Restarts in a row after one PLAY (a client that asks on every start must not loop forever). */
    public static final int MAX_RESTARTS = 5;
    /** How often {@code crash-reports/} is looked at while the Minecraft Launcher plays. */
    public static final Duration WATCH_PERIOD = Duration.ofSeconds(3);
    /** How long it is watched after the Minecraft Launcher was opened. */
    public static final Duration WATCH_LIMIT = Duration.ofMinutes(30);
    /** How long a "switched off" toast stays: long enough to read what to do next. */
    static final Duration SWITCHED_OFF_TOAST = Duration.ofSeconds(20);

    /** Runs a task repeatedly (production: a daemon thread; tests: by hand). */
    @FunctionalInterface
    public interface Scheduler {
        /**
         * @param period time between runs
         * @param task   task
         * @return stops the repetition
         */
        Runnable every(Duration period, Runnable task);

        /** @return a scheduler with its own daemon thread per repetition */
        static Scheduler daemon() {
            return (period, task) -> {
                final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(r -> {
                    final Thread t = new Thread(r, "vanta-crash-report-watch");
                    t.setDaemon(true);
                    return t;
                });
                final ScheduledFuture<?> f = ses.scheduleWithFixedDelay(task, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
                return () -> {
                    f.cancel(false);
                    ses.shutdownNow();
                };
            };
        }
    }

    /** Screen state. */
    public enum State {
        /** Missing account or Java. */
        NOT_READY,
        /** PLAY available. */
        READY,
        /** Downloading missing files. */
        INSTALLING,
        /** Re-checking existing files (no or few downloads). */
        VERIFYING,
        /** The game process is alive. */
        RUNNING,
        /** The last operation failed; PLAY retries. */
        ERROR
    }

    private final SessionModel session;
    private final LauncherBackend backend;
    private final UiExecutors executors;
    private final Messages messages;
    private final Formats formats;
    private final ErrorMessages errors;
    private final ToastModel toasts;
    private final LogBuffer launcherLog;
    private final LogBuffer gameLog;
    private final LocalAiViewModel localAi;

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.NOT_READY);
    private final StringProperty errorText = new SimpleStringProperty("");
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final BooleanProperty progressVisible = new SimpleBooleanProperty(false);
    private final StringProperty stepText = new SimpleStringProperty("");
    private final StringProperty detailText = new SimpleStringProperty("");
    private final StringProperty bytesText = new SimpleStringProperty("");
    private final StringProperty phaseText = new SimpleStringProperty("");
    private final ReadOnlyObjectWrapper<RunningGame> game = new ReadOnlyObjectWrapper<>();
    private final BooleanProperty javaInstalling = new SimpleBooleanProperty(false);
    private final BooleanProperty officialBusy = new SimpleBooleanProperty(false);
    private final StringProperty officialDoneText = new SimpleStringProperty("");
    private final BooleanBinding playEnabled;
    private final BooleanBinding officialPlayMode;
    private final BooleanBinding verifyAvailable;
    private final StringBinding blockReason;
    private final StringBinding idleBlockReason;
    private final StringBinding statusText;
    private final StringBinding titleText;
    private final StringBinding leadText;
    private final StringBinding factsText;

    private final AtomicReference<CancellationToken> cancellation = new AtomicReference<>();
    private Future<?> running;
    private Consumer<RunningGame> gameStartedHook = g -> { };
    private Consumer<Integer> gameExitedHook = code -> { };
    private Launched lastLaunch;
    private JavaInstall lastJava;
    private int restarts;
    private Scheduler watchScheduler = Scheduler.daemon();
    private Runnable stopWatch;
    private long watchTicks;
    private boolean watchBusy;

    /**
     * @param session     shared session state
     * @param backend     backend
     * @param executors   executors
     * @param messages    messages
     * @param formats     formats
     * @param toasts      notifications
     * @param launcherLog launcher log buffer (install messages are appended here)
     * @param gameLog     game log buffer (process output is appended here)
     */
    public HomeViewModel(final SessionModel session, final LauncherBackend backend, final UiExecutors executors, final Messages messages,
                         final Formats formats, final ToastModel toasts, final LogBuffer launcherLog, final LogBuffer gameLog) {
        this(session, backend, executors, messages, formats, toasts, launcherLog, gameLog,
            new LocalAiViewModel(session, backend, executors, messages, formats, toasts, launcherLog));
    }

    /**
     * @param session     shared session state
     * @param backend     backend
     * @param executors   executors
     * @param messages    messages
     * @param formats     formats
     * @param toasts      notifications
     * @param launcherLog launcher log buffer (install messages are appended here)
     * @param gameLog     game log buffer (process output is appended here)
     * @param localAi     the Local AI state shared with the Settings page (first-start offer, status line)
     */
    public HomeViewModel(final SessionModel session, final LauncherBackend backend, final UiExecutors executors, final Messages messages,
                         final Formats formats, final ToastModel toasts, final LogBuffer launcherLog, final LogBuffer gameLog,
                         final LocalAiViewModel localAi) {
        this.localAi = Objects.requireNonNull(localAi, "localAi");
        this.session = Objects.requireNonNull(session, "session");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.formats = Objects.requireNonNull(formats, "formats");
        this.errors = new ErrorMessages(messages, formats);
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.launcherLog = Objects.requireNonNull(launcherLog, "launcherLog");
        this.gameLog = Objects.requireNonNull(gameLog, "gameLog");

        playEnabled = Bindings.createBooleanBinding(() -> (state.get() == State.READY || state.get() == State.ERROR)
            && session.account().isPresent() && session.java().isPresent(), state, session.accountProperty(), session.javaProperty());
        officialPlayMode = Bindings.createBooleanBinding(() -> session.loadedProperty().get() && !session.playPossible(),
            session.loadedProperty(), session.accountProperty(), session.signInConfiguredProperty());
        verifyAvailable = Bindings.createBooleanBinding(() -> session.instance().isPresent(), session.instanceProperty());
        blockReason = Bindings.createStringBinding(this::computeBlockReason, state, session.accountProperty(), session.javaProperty(),
            session.detectingJavaProperty(), session.signInConfiguredProperty());
        idleBlockReason = Bindings.createStringBinding(() -> isBusy() ? "" : idleBlockReason(), state, session.accountProperty(),
            session.javaProperty(), session.detectingJavaProperty(), session.signInConfiguredProperty(), session.loadedProperty());
        statusText = Bindings.createStringBinding(this::computeStatusText, state, phaseText, officialPlayMode);
        titleText = Bindings.createStringBinding(this::computeTitle, state, session.accountProperty(), session.javaProperty(),
            session.signInConfiguredProperty(), session.loadedProperty());
        leadText = Bindings.createStringBinding(this::computeLead, state, session.accountProperty(), session.javaProperty(),
            session.instanceProperty(), session.signInConfiguredProperty());
        factsText = Bindings.createStringBinding(this::computeFacts, session.javaProperty());

        session.accountProperty().addListener((obs, old, now) -> recomputeIdleState());
        session.javaProperty().addListener((obs, old, now) -> recomputeIdleState());
        recomputeIdleState();
    }

    // ---------------------------------------------------------------- properties

    /** @return state */
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /** @return state */
    public State state() {
        return state.get();
    }

    /** @return whether PLAY is enabled */
    public BooleanBinding playEnabledProperty() {
        return playEnabled;
    }

    /**
     * PLAY cannot sign in here (no stored account and no Microsoft client id): the primary button becomes "PLAY via
     * Minecraft Launcher", which sets the profile up (or updates it) and opens the official launcher.
     *
     * @return whether the primary button plays through the official Minecraft Launcher
     */
    public BooleanBinding officialPlayModeProperty() {
        return officialPlayMode;
    }

    /**
     * "Verify files" checks an existing installation only (instance.json present). On a fresh launcher it is hidden:
     * a full install is never started by it; PLAY or the client card's "Install now" installs deliberately.
     *
     * @return whether "Verify files" is offered
     */
    public BooleanBinding verifyAvailableProperty() {
        return verifyAvailable;
    }

    /**
     * @return what to say when nothing is installed: press PLAY when PLAY can work here (an account is present or
     *     Microsoft sign-in is configured, {@link SessionModel#playPossible()}), otherwise use "Use with Minecraft
     *     Launcher" (PLAY cannot be enabled then)
     */
    public String nothingInstalledText() {
        return messages.get(session.playPossible() ? "error.nothingInstalled" : "error.nothingInstalled.official");
    }

    /** @return localised reason PLAY is disabled, or empty */
    public StringBinding blockReasonProperty() {
        return blockReason;
    }

    /** @return localised status ("Ready", "Installing…") */
    public StringBinding statusTextProperty() {
        return statusText;
    }

    /** @return hero title */
    public StringBinding titleTextProperty() {
        return titleText;
    }

    /** @return hero lead paragraph */
    public StringBinding leadTextProperty() {
        return leadText;
    }

    /** @return the facts line */
    public StringBinding factsTextProperty() {
        return factsText;
    }

    /** @return last error text */
    public ReadOnlyStringProperty errorTextProperty() {
        return errorText;
    }

    /** @return progress fraction (negative = indeterminate) */
    public ReadOnlyDoubleProperty progressProperty() {
        return progress;
    }

    /** @return whether the progress block is shown */
    public ReadOnlyBooleanProperty progressVisibleProperty() {
        return progressVisible;
    }

    /** @return current step ("Step 3 of 9 · Downloading libraries") */
    public ReadOnlyStringProperty stepTextProperty() {
        return stepText;
    }

    /** @return current file / message */
    public ReadOnlyStringProperty detailTextProperty() {
        return detailText;
    }

    /** @return bytes downloaded so far */
    public ReadOnlyStringProperty bytesTextProperty() {
        return bytesText;
    }

    /** @return the running game, or null */
    public ReadOnlyObjectProperty<RunningGame> gameProperty() {
        return game.getReadOnlyProperty();
    }

    /** @return whether a Temurin install is running */
    public ReadOnlyBooleanProperty javaInstallingProperty() {
        return javaInstalling;
    }

    /** @return whether "Use with the Minecraft Launcher" is running */
    public ReadOnlyBooleanProperty officialBusyProperty() {
        return officialBusy;
    }

    /** @return confirmation shown after the official launcher profile was set up (empty otherwise) */
    public ReadOnlyStringProperty officialDoneTextProperty() {
        return officialDoneText;
    }

    /** @return whether a long-running operation (install, verify, official profile, game) is active */
    public boolean busy() {
        return isBusy();
    }

    /** @return whether an install/verify is cancellable right now */
    public boolean canCancel() {
        return state.get() == State.INSTALLING || state.get() == State.VERIFYING;
    }

    /**
     * @param hook runs on the UI thread when the game process started
     */
    public void onGameStarted(final Consumer<RunningGame> hook) {
        this.gameStartedHook = Objects.requireNonNull(hook, "hook");
    }

    /**
     * @param hook runs on the UI thread when the game exited (receives the exit code)
     */
    public void onGameExited(final Consumer<Integer> hook) {
        this.gameExitedHook = Objects.requireNonNull(hook, "hook");
    }

    // ---------------------------------------------------------------- actions

    /** PLAY: ensure Java → install/verify → refresh account → launch. */
    public void play() {
        if (!playEnabled.get() || isBusy()) {
            return;
        }
        final Account account = session.account().orElseThrow();
        final JavaInstall java = session.java().orElseThrow();
        final InstallRequest request = standardRequest();
        final CancellationToken token = new CancellationToken();
        cancellation.set(token);
        errorText.set("");
        officialDoneText.set("");
        beginProgress(session.instance().isPresent() ? State.VERIFYING : State.INSTALLING);
        running = Async.run(executors, () -> {
            final InstanceInfo installed = backend.install(request, installListener(), token);
            token.throwIfCancelled();
            // The files are in place: switch off what would stop Minecraft while starting, before it starts.
            final StartupGuard.Report guard = startupCheckQuietly();
            token.throwIfCancelled();
            executors.onUi(() -> {
                announce(guard, false);
                session.setInstance(installed);
                phaseText.set(messages.get("home.status.refreshingAccount"));
                stepText.set(messages.get("home.status.refreshingAccount"));
                detailText.set("");
                progress.set(-1);
            });
            final Account fresh = backend.refreshIfExpired(account);
            executors.onUi(() -> {
                phaseText.set(messages.get("home.status.launching"));
                stepText.set(messages.get("home.status.launching"));
            });
            final LaunchRequest launch = LaunchRequest.of(installed, fresh, java.executable(), backend.settings());
            final RunningGame started = backend.launch(launch, line -> gameLog.append(line.text()));
            return new Launched(installed, fresh, started);
        }, launched -> {
            if (!launched.account().equals(account)) {
                session.setAccount(launched.account());
            }
            endProgress();
            game.set(launched.game());
            state.set(State.RUNNING);
            lastLaunch = launched;
            lastJava = java;
            restarts = 0;
            launcherLog.append(LogLevel.INFO, "Game started (pid " + launched.game().pid() + "), log " + launched.game().logFile());
            toasts.info(messages.get("home.toast.gameStarted.title"), messages.format("home.toast.gameStarted.message",
                Long.toString(launched.game().pid())));
            gameStartedHook.accept(launched.game());
            launched.game().exitCode().whenComplete((code, failure) -> executors.onUi(() -> onExit(code == null ? -1 : code)));
        }, this::fail);
    }

    /**
     * The regular install offered by the client card while no VANTA client is installed: exactly what PLAY installs
     * (Minecraft, Fabric Loader, Fabric API, VANTA Client, {@code instance.json}), without launching. Needs neither an
     * account nor Java. With an existing instance it verifies it, like {@link #verify()}.
     */
    public void install() {
        installOrVerify();
    }

    /**
     * Verify files: re-checks an existing installation without launching (missing or damaged files are downloaded
     * again). Like PLAY it runs the regular install, so an older VANTA Client is also replaced by the latest release
     * (the toast then says so). It never starts a first install: without {@code instance.json} it only says how to
     * install (the button is hidden then, see {@link #verifyAvailableProperty()}).
     */
    public void verify() {
        if (isBusy()) {
            return;
        }
        if (session.instance().isEmpty()) {
            toasts.info(messages.get("home.toast.nothingToVerify.title"), nothingInstalledText());
            return;
        }
        installOrVerify();
    }

    private void installOrVerify() {
        if (isBusy()) {
            return;
        }
        final CancellationToken token = new CancellationToken();
        cancellation.set(token);
        errorText.set("");
        officialDoneText.set("");
        beginProgress(session.instance().isPresent() ? State.VERIFYING : State.INSTALLING);
        final boolean hadInstance = session.instance().isPresent();
        final String clientBefore = session.instance().map(InstanceInfo::vantaClientVersion).orElse("");
        running = Async.run(executors, () -> backend.install(standardRequest(), installListener(), token), installed -> {
            session.setInstance(installed);
            endProgress();
            settle();
            if (hadInstance) {
                toasts.success(messages.get("home.toast.verified.title"), verifiedMessage(clientBefore, installed.vantaClientVersion()));
            } else {
                toasts.success(messages.get("home.toast.installed.title"), messages.format("home.toast.installed.message",
                    installed.minecraftVersion(), installed.fabricLoaderVersion()));
            }
        }, this::fail);
    }

    /**
     * @return the standard install, with the performance pack as Settings say and the Local AI step when its automatic
     *     install is on and the player agreed to the download once
     */
    private InstallRequest standardRequest() {
        final dev.vanta.launcher.core.settings.LauncherSettings settings = backend.settings();
        return InstallRequest.standard().withPerformancePack(settings.performancePack()).withLocalAi(settings.localAiWithInstalls());
    }

    /** @return the Local AI state and actions shared with the Settings page */
    public LocalAiViewModel localAi() {
        return localAi;
    }

    /**
     * First start, after the start check: when the automatic Local AI install is on, nothing is installed and the player
     * has not been asked yet, the view gets the consent dialog content (once per session). With consent given earlier
     * and files missing, the install runs right away. See {@link LocalAiViewModel#checkOffer}.
     *
     * @param onOffer receives the offer on the UI thread
     */
    public void offerLocalAi(final Consumer<LocalAiViewModel.Offer> onOffer) {
        localAi.checkOffer(onOffer);
    }

    /**
     * @param before VANTA Client version in {@code instance.json} before "Verify files" (or PLAY's verify) ran
     * @param after  VANTA Client version it installed
     * @return "nothing was missing or damaged", or, when the install also replaced the VANTA Client with the latest
     *     release (the standard install always installs that one), which version it installed
     */
    String verifiedMessage(final String before, final String after) {
        if (before == null || before.isEmpty() || after == null || after.isEmpty() || before.equals(after)) {
            return messages.get("home.toast.verified.message");
        }
        return messages.format("home.toast.verified.clientUpdated", after);
    }

    /**
     * Before the profile is written: is the official Minecraft Launcher running? It reads its profiles only when it starts,
     * so a running one (often only in the system tray) would not show "VANTA 1.21.11" until it is restarted. The view
     * asks the player to close it ("Check again" / "Continue anyway"); the launcher never closes it.
     *
     * @param onResult receives the running launcher processes on the UI thread (empty: none runs)
     */
    public void checkOfficialLauncher(final Consumer<List<String>> onResult) {
        Async.run(executors, backend::runningOfficialLaunchers, running -> {
            if (!running.isEmpty()) {
                launcherLog.append(LogLevel.INFO, "The Minecraft Launcher is running: " + String.join(", ", running));
            }
            onResult.accept(running);
        }, error -> {
            launcherLog.append(LogLevel.WARN, "Could not check whether the Minecraft Launcher runs: " + errors.describe(error));
            onResult.accept(List.of());
        });
    }

    /**
     * First half of "Use with the Minecraft Launcher": reads what would be written into the official Minecraft
     * directory (nothing is changed) and hands it to the view, which asks for confirmation. A missing or unreadable
     * {@code launcher_profiles.json} is reported as an error toast instead.
     *
     * @param onPlan receives the plan on the UI thread
     */
    public void prepareOfficialProfile(final Consumer<OfficialProfileService.Plan> onPlan) {
        if (isBusy()) {
            return;
        }
        // Reading the release manifest and asking Modrinth for the performance pack takes a moment: show it.
        errorText.set("");
        beginProgress(State.INSTALLING);
        stepText.set(messages.get("official.preparing"));
        phaseText.set(messages.get("home.status.official"));
        running = Async.run(executors, backend::officialProfilePlan, plan -> {
            endProgress();
            settle();
            onPlan.accept(plan);
        }, error -> {
            endProgress();
            settle();
            if (Async.isCancellation(error)) {
                return;
            }
            final String text = errors.describe(error);
            launcherLog.append(LogLevel.WARN, text);
            toasts.error(messages.get("official.toast.failed.title"), text);
        });
    }

    /**
     * Second half of "Use with the Minecraft Launcher", after the user confirmed: installs Fabric API and the VANTA
     * client into the instance and adds the profile to the official launcher. Needs neither an account nor Java.
     */
    public void installOfficialProfile() {
        installOfficialProfile(false);
    }

    /**
     * Installs Fabric API, the VANTA client and (when enabled) the performance pack into the instance and adds the
     * profile to the official launcher; with {@code openAfter} ("PLAY via Minecraft Launcher") the official launcher
     * is started afterwards.
     *
     * @param openAfter whether to open the Minecraft Launcher when the profile is ready
     */
    public void installOfficialProfile(final boolean openAfter) {
        if (isBusy()) {
            return;
        }
        final CancellationToken token = new CancellationToken();
        cancellation.set(token);
        errorText.set("");
        officialDoneText.set("");
        officialBusy.set(true);
        beginProgress(State.INSTALLING);
        phaseText.set(messages.get("home.status.official"));
        running = Async.run(executors, () -> {
            final OfficialProfileService.Result installed = backend.installOfficialProfile(installListener(), token);
            // The files are in place and the Minecraft Launcher is not open yet: switch off what would stop Minecraft there.
            return new OfficialSetUp(installed, startupCheckQuietly());
        }, setUp -> {
            final OfficialProfileService.Result result = setUp.result();
            announce(setUp.guard(), false);
            officialBusy.set(false);
            endProgress();
            settle();
            // The client jar is now in the instance's mods/: the client card and the update check must see it.
            session.refreshInstance();
            final String done = messages.format("official.toast.done.message", result.profileName());
            officialDoneText.set(done);
            launcherLog.append(LogLevel.INFO, (result.created() ? "Added" : "Updated") + " the Minecraft Launcher profile '"
                + result.profileName() + "' (" + result.versionId() + ", game directory " + result.gameDir() + ")");
            for (String note : result.notes()) {
                launcherLog.append(LogLevel.WARN, "Performance pack: " + note);
            }
            if (openAfter) {
                openOfficialLauncher(false);
            } else {
                toasts.success(messages.format("official.toast.done.title", result.profileName()), done);
                // The player opens the Minecraft Launcher next: a crash there is caught while this window is open.
                watchCrashReports();
            }
        }, error -> {
            officialBusy.set(false);
            fail(error);
        });
    }

    /**
     * Starts the official Minecraft Launcher and tells the player which profile to pick. Never stops a running one.
     */
    public void openOfficialLauncher() {
        openOfficialLauncher(true);
    }

    /**
     * @param checkFirst whether to run the start check first (false: it has just run)
     */
    private void openOfficialLauncher(final boolean checkFirst) {
        final String profile = OfficialProfileService.profileName(LauncherVersion.MINECRAFT);
        Async.run(executors, () -> {
            final StartupGuard.Report guard = checkFirst ? startupCheckQuietly() : StartupGuard.Report.EMPTY;
            return new Opened(guard, backend.openOfficialLauncher());
        }, opened -> {
            announce(opened.guard(), false);
            final OfficialLauncher.OpenResult result = opened.result();
            launcherLog.append(result.started() ? LogLevel.INFO : LogLevel.WARN, result.detail());
            switch (result.outcome()) {
                case OPENED, STARTED_UNCONFIRMED -> {
                    officialDoneText.set(messages.format("official.opened.message", profile));
                    toasts.success(messages.get("official.opened.title"), messages.format("official.opened.message", profile));
                    watchCrashReports();
                }
                case NOT_FOUND -> toasts.error(messages.get("official.open.failed.title"), messages.get("official.open.notFound"));
                default -> toasts.error(messages.get("official.open.failed.title"), messages.format("official.open.failed", result.detail()));
            }
        }, error -> toasts.error(messages.get("official.open.failed.title"), errors.describe(error)));
    }

    /**
     * @param file a file the official launcher setup writes or removes
     * @return localised description for the confirmation dialog
     */
    public String officialPlanLabel(final OfficialProfileService.PlannedFile file) {
        return switch (file.kind()) {
            case FABRIC_API -> messages.format("official.plan.fabricApi", LauncherVersion.FABRIC_API);
            case VANTA_CLIENT -> messages.get("official.plan.vantaClient");
            case VANTA_CLIENT_LOCAL -> messages.get("official.plan.vantaClientLocal");
            case PERFORMANCE_MOD -> messages.format("official.plan.performanceMod", file.detail());
            case CLIENT_ROLLBACK_COPY -> messages.get("official.plan.rollbackCopy");
            case INSTANCE_RECORD -> messages.get("official.plan.instanceRecord");
            case REPLACED_JAR -> file.detail().isEmpty() ? messages.get("official.plan.replacedJar")
                : messages.format("official.plan.replacedJar.named", file.detail());
            case PRUNED_ROLLBACK_COPY -> messages.format("official.plan.prunedRollbackCopy", Integer.toString(VantaClientService.KEEP_VERSIONS));
            case VERSION_JSON -> messages.format("official.plan.versionJson", LauncherVersion.FABRIC_LOADER);
            case VERSION_JAR -> messages.get("official.plan.versionJar");
            case PROFILES_BACKUP -> messages.get("official.plan.profilesBackup");
            case PROFILES -> messages.get("official.plan.profiles");
            case STORE_PROFILES_BACKUP -> messages.get("official.plan.storeProfilesBackup");
            case STORE_PROFILES -> messages.get("official.plan.storeProfiles");
            case MINECRAFT_FOLDER_NOTE -> messages.get("official.plan.minecraftFolderNote");
            case LOCAL_AI_NOTE -> messages.get("official.plan.localAiNote");
        };
    }

    /** Cancels a running install/verify. */
    public void cancel() {
        final CancellationToken token = cancellation.get();
        if (token != null) {
            token.cancel();
        }
        if (running != null) {
            running.cancel(true);
        }
    }

    /** Installs Eclipse Temurin 21 through the backend and selects it. */
    public void installJava() {
        if (javaInstalling.get()) {
            return;
        }
        javaInstalling.set(true);
        final CancellationToken token = new CancellationToken();
        final DownloadProgressListener listener = new DownloadProgressListener() {
            @Override
            public void onProgress(final DownloadRequest request, final long done, final long total) {
                executors.onUi(() -> {
                    if (state.get() == State.NOT_READY || state.get() == State.READY) {
                        progressVisible.set(true);
                        progress.set(total > 0 ? done / (double) total : -1);
                        stepText.set(messages.format("java.card.installing", Integer.toString(LauncherVersion.JAVA_MAJOR)));
                        detailText.set(request.description());
                        bytesText.set(total > 0 ? formats.bytes(done) + " / " + formats.bytes(total) : formats.bytes(done));
                    }
                });
            }
        };
        Async.run(executors, () -> backend.installJava(listener, token), installed -> {
            javaInstalling.set(false);
            endProgress();
            session.setJava(installed);
            session.selectJava(installed, () -> { });
            launcherLog.append(LogLevel.INFO, "Installed " + installed.describe());
            toasts.success(messages.format("java.toast.installed.title", Integer.toString(installed.major())),
                messages.format("java.toast.installed.message", installed.version()));
        }, error -> {
            javaInstalling.set(false);
            endProgress();
            if (!Async.isCancellation(error)) {
                final String text = errors.describe(error);
                launcherLog.append(LogLevel.ERROR, "Java installation failed: " + text);
                toasts.error(messages.get("java.toast.installFailed.title"), text);
            }
        });
    }

    /** Starts an offline session when the core policy allows it. */
    public void startOfflineSession() {
        Async.run(executors, backend::createOfflineSession, account -> {
            session.setAccount(account);
            toasts.info(messages.format("account.offline.started", account.name()), messages.get("home.block.offline"));
        }, error -> toasts.error(messages.get("home.toast.error.title"), errors.describe(error)));
    }

    /** Asks the running game to exit. */
    public void stopGame() {
        final RunningGame g = game.get();
        if (g != null) {
            g.stop();
        }
    }

    // ---------------------------------------------------------------- internals

    private record Launched(InstanceInfo instance, Account account, RunningGame game) {
    }

    private record OfficialSetUp(OfficialProfileService.Result result, StartupGuard.Report guard) {
    }

    private record Opened(StartupGuard.Report guard, OfficialLauncher.OpenResult result) {
    }

    // ---------------------------------------------------------------- start check

    /**
     * The start check once when the window opens: a player who just updated the launcher gets mods that would stop
     * Minecraft switched off without pressing anything (also the mod a crash report from before names).
     */
    public void runStartupCheck() {
        Async.run(executors, backend::startupCheck, report -> announce(report, false),
            error -> launcherLog.append(LogLevel.WARN, "Startup check could not run: " + errors.describe(error)));
    }

    /**
     * Watches {@code crash-reports/} of the VANTA instance while the Minecraft Launcher plays the VANTA profile: every
     * {@link #WATCH_PERIOD} for {@link #WATCH_LIMIT}, or until {@link #stopWatchingCrashReports()} (the window closes). A
     * new report that names a mod switches it off, and a toast asks the player to press Play in the Minecraft Launcher
     * again. Starting it again restarts the time limit.
     */
    public void watchCrashReports() {
        stopWatchingCrashReports();
        watchTicks = 0;
        watchBusy = false;
        final long maxTicks = Math.max(1, WATCH_LIMIT.toMillis() / WATCH_PERIOD.toMillis());
        final Runnable[] self = new Runnable[1];
        final Runnable stop = watchScheduler.every(WATCH_PERIOD, () -> executors.onUi(() -> watchTick(self[0], maxTicks)));
        self[0] = stop;
        stopWatch = stop;
    }

    private void watchTick(final Runnable owner, final long maxTicks) {
        if (owner == null || stopWatch != owner) {
            return;
        }
        if (++watchTicks > maxTicks) {
            launcherLog.append(LogLevel.INFO, "Stopped watching for crash reports after " + WATCH_LIMIT.toMinutes() + " minutes");
            stopWatchingCrashReports();
            return;
        }
        if (watchBusy) {
            return;
        }
        watchBusy = true;
        Async.run(executors, backend::crashReportCheck, report -> {
            watchBusy = false;
            announce(report, true);
        }, error -> {
            watchBusy = false;
            launcherLog.append(LogLevel.WARN, "Crash report check could not run: " + errors.describe(error));
        });
    }

    /** Stops {@link #watchCrashReports()} (the window closes). */
    public void stopWatchingCrashReports() {
        final Runnable stop = stopWatch;
        stopWatch = null;
        if (stop != null) {
            stop.run();
        }
    }

    /** @return whether {@code crash-reports/} is being watched */
    public boolean watchingCrashReports() {
        return stopWatch != null;
    }

    /**
     * Test hook: how the crash report watch repeats (default {@link Scheduler#daemon()}).
     *
     * @param scheduler scheduler
     */
    public void setWatchScheduler(final Scheduler scheduler) {
        this.watchScheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** @return the start check's report; a failure is logged and never stops PLAY or the Minecraft Launcher */
    private StartupGuard.Report startupCheckQuietly() {
        try {
            return backend.startupCheck();
        } catch (java.io.IOException | RuntimeException e) {
            launcherLog.append(LogLevel.WARN, "Startup check could not run: " + errors.describe(e));
            return StartupGuard.Report.EMPTY;
        }
    }

    /** The crash report part after the game PLAY started exited. */
    private void checkCrashReportAfterExit() {
        Async.run(executors, backend::crashReportCheck, report -> announce(report, false),
            error -> launcherLog.append(LogLevel.WARN, "Crash report check could not run: " + errors.describe(error)));
    }

    /**
     * One toast and one launcher log line per mod the start check switched off (or only reported).
     *
     * @param report        report
     * @param playAgainHint whether to ask the player to press Play in the Minecraft Launcher again
     */
    private void announce(final StartupGuard.Report report, final boolean playAgainHint) {
        for (StartupGuard.Action a : report.actions()) {
            final String reason = a.source() == StartupGuard.Source.CHECK
                ? messages.format("startup.reason.olderMinecraft", LauncherVersion.MINECRAFT)
                : messages.format("startup.reason.crashReport", a.crashReport());
            if (a.switchedOff()) {
                launcherLog.append(LogLevel.WARN, "Startup check: switched off " + a.fileName() + " (" + a.label() + "): " + a.reason()
                    + "; it can be switched on again on the Mods page");
                final String text = messages.format("startup.toast.switchedOff.message", a.label(), reason)
                    + (playAgainHint ? " " + messages.get("startup.toast.playAgain") : "");
                toasts.show(ToastModel.Kind.WARNING, messages.format("startup.toast.switchedOff.title", a.modName()), text, SWITCHED_OFF_TOAST);
            } else {
                launcherLog.append(LogLevel.WARN, "Startup check: did not switch off " + a.fileName() + " (" + a.label() + "): " + a.reason());
                toasts.show(ToastModel.Kind.WARNING, messages.get("startup.toast.notSwitchedOff.title"),
                    messages.format("startup.toast.notSwitchedOff.message", a.label(), reason), SWITCHED_OFF_TOAST);
            }
        }
    }

    private boolean isBusy() {
        final State s = state.get();
        return s == State.INSTALLING || s == State.VERIFYING || s == State.RUNNING;
    }

    private void beginProgress(final State busyState) {
        state.set(busyState);
        progressVisible.set(true);
        progress.set(-1);
        stepText.set(messages.get("home.progress.preparing"));
        detailText.set("");
        bytesText.set("");
        phaseText.set("");
    }

    private void endProgress() {
        progressVisible.set(false);
        progress.set(0);
        stepText.set("");
        detailText.set("");
        bytesText.set("");
        phaseText.set("");
        cancellation.set(null);
        running = null;
    }

    private InstallListener installListener() {
        return new InstallListener() {
            @Override
            public void onProgress(final InstallProgress p) {
                executors.onUi(() -> {
                    if (!progressVisible.get()) {
                        return;
                    }
                    progress.set(p.fraction());
                    stepText.set(messages.format("home.progress.step", Integer.toString(p.stepIndex() + 1),
                        Integer.toString(p.stepCount()), p.step().label()));
                    final String files = p.total() > 1 ? messages.format("home.progress.files", Long.toString(p.done()), Long.toString(p.total())) : "";
                    final String message = p.message() == null || p.message().equals(p.step().label()) ? "" : p.message();
                    detailText.set(files.isEmpty() ? message : message.isEmpty() ? files : files + " · " + message);
                    bytesText.set(p.bytes() > 0 ? messages.format("home.progress.bytes", formats.bytes(p.bytes())) : "");
                });
            }

            @Override
            public void onLog(final String message) {
                launcherLog.append(LogLevel.INFO, message);
            }
        };
    }

    private void fail(final Throwable error) {
        endProgress();
        if (Async.isCancellation(error)) {
            settle();
            launcherLog.append(LogLevel.INFO, "Cancelled by the user");
            toasts.info(messages.get("home.toast.cancelled.title"), messages.get("home.toast.cancelled.message"));
            return;
        }
        final String text = errors.describe(error);
        errorText.set(text);
        state.set(State.ERROR);
        launcherLog.append(LogLevel.ERROR, text);
        toasts.error(messages.get("home.toast.error.title"), text);
    }

    private void onExit(final int code) {
        game.set(null);
        final Launched previous = lastLaunch;
        if (previous != null && lastJava != null) {
            // "Restart game" in VANTA writes config/vanta/restart.request before the game closes itself. The marker is
            // consumed (deleted) even once the restart limit is reached, so it cannot restart the game after a later PLAY.
            Async.run(executors, backend::consumeRestartRequest, restart -> {
                if (restart && restarts < MAX_RESTARTS) {
                    restarts++;
                    relaunch(previous);
                } else {
                    if (restart) {
                        launcherLog.append(LogLevel.WARN, "The game asked for a restart again; after " + MAX_RESTARTS
                            + " restarts in a row it stays closed");
                    }
                    finishExit(code);
                }
            }, error -> {
                launcherLog.append(LogLevel.WARN, "Could not read the restart request: " + errors.describe(error));
                finishExit(code);
            });
            return;
        }
        finishExit(code);
    }

    /** Starts the game again after a restart request, with the same instance, account and Java. */
    private void relaunch(final Launched previous) {
        final JavaInstall java = lastJava;
        launcherLog.append(LogLevel.INFO, "The game asked for a restart; starting it again");
        phaseText.set(messages.get("home.status.restarting"));
        Async.run(executors, () -> {
            // The game may have installed mods before it asked for the restart (VANTA's in-game Mods screen): check them
            // before it starts again.
            final StartupGuard.Report guard = startupCheckQuietly();
            executors.onUi(() -> announce(guard, false));
            final Account fresh = backend.refreshIfExpired(previous.account());
            final RunningGame started = backend.launch(LaunchRequest.of(previous.instance(), fresh, java.executable(), backend.settings()),
                line -> gameLog.append(line.text()));
            return new Launched(previous.instance(), fresh, started);
        }, launched -> {
            phaseText.set("");
            lastLaunch = launched;
            game.set(launched.game());
            launcherLog.append(LogLevel.INFO, "Game restarted (pid " + launched.game().pid() + "), log " + launched.game().logFile());
            toasts.info(messages.get("home.toast.gameRestarted.title"), messages.format("home.toast.gameStarted.message",
                Long.toString(launched.game().pid())));
            gameStartedHook.accept(launched.game());
            launched.game().exitCode().whenComplete((c, failure) -> executors.onUi(() -> onExit(c == null ? -1 : c)));
        }, error -> {
            phaseText.set("");
            lastLaunch = null;
            fail(error);
            gameExitedHook.accept(-1);
        });
    }

    private void finishExit(final int code) {
        lastLaunch = null;
        // A crash while starting leaves a report that names the mod: switch it off before the next PLAY.
        checkCrashReportAfterExit();
        if (code == 0) {
            launcherLog.append(LogLevel.INFO, "Game exited normally");
            toasts.success(messages.get("home.toast.gameExited.title"), messages.get("home.toast.gameExited.message"));
        } else {
            launcherLog.append(LogLevel.WARN, "Game exited with code " + code);
            toasts.warning(messages.format("home.toast.gameCrashed.title", Integer.toString(code)), messages.get("home.toast.gameCrashed.message"));
        }
        if (state.get() == State.RUNNING) {
            settle();
        }
        gameExitedHook.accept(code);
    }

    /** Leaves a busy state: READY when account and Java are present, otherwise NOT_READY. */
    private void settle() {
        state.set(session.account().isPresent() && session.java().isPresent() ? State.READY : State.NOT_READY);
    }

    private void recomputeIdleState() {
        final State s = state.get();
        if (s == State.INSTALLING || s == State.VERIFYING || s == State.RUNNING) {
            return;
        }
        if (s == State.ERROR && !errorText.get().isEmpty() && session.account().isPresent() && session.java().isPresent()) {
            return;
        }
        state.set(session.account().isPresent() && session.java().isPresent() ? State.READY : State.NOT_READY);
    }

    private String computeBlockReason() {
        return switch (state.get()) {
            case INSTALLING -> messages.get("home.block.installing");
            case VERIFYING -> messages.get("home.block.verifying");
            case RUNNING -> messages.get("home.block.running");
            default -> idleBlockReason();
        };
    }

    /** @return why PLAY is disabled in an idle state ("" when it is enabled); shown under the button */
    public String idleBlockReason() {
        if (session.loadedProperty().get() && !session.playPossible()) {
            // The button plays through the official Minecraft Launcher: say what it does instead of what is missing.
            return messages.format("home.block.viaOfficial", OfficialProfileService.profileName(LauncherVersion.MINECRAFT));
        }
        if (session.account().isEmpty()) {
            return session.signInConfiguredProperty().get() ? messages.get("home.block.noAccount") : messages.get("home.block.noSignIn");
        }
        if (session.java().isEmpty()) {
            return session.detectingJavaProperty().get() ? messages.get("java.card.detecting")
                : messages.format("home.block.noJava", Integer.toString(LauncherVersion.JAVA_MAJOR));
        }
        return "";
    }

    /** @return the idle block reason, or empty while busy (the status line already explains busy states) */
    public StringBinding idleBlockReasonProperty() {
        return idleBlockReason;
    }

    private String computeStatusText() {
        if (!phaseText.get().isEmpty()) {
            return phaseText.get();
        }
        return switch (state.get()) {
            case NOT_READY -> officialPlayMode.get() ? messages.get("home.status.viaOfficial") : messages.get("home.status.notReady");
            case READY -> messages.get("home.status.ready");
            case INSTALLING -> messages.get("home.status.installing");
            case VERIFYING -> messages.get("home.status.verifying");
            case RUNNING -> messages.get("home.status.running");
            case ERROR -> messages.get("home.status.error");
        };
    }

    private String computeTitle() {
        return switch (state.get()) {
            case NOT_READY -> session.loadedProperty().get() && !session.playPossible() ? messages.get("home.title.viaOfficial")
                : messages.get("home.title.notReady");
            case READY -> messages.get("home.title.ready");
            case INSTALLING -> messages.get("home.title.installing");
            case VERIFYING -> messages.get("home.title.verifying");
            case RUNNING -> messages.get("home.title.running");
            case ERROR -> messages.get("home.title.error");
        };
    }

    private String computeLead() {
        return switch (state.get()) {
            case NOT_READY -> session.account().isEmpty()
                ? session.signInConfiguredProperty().get() ? messages.get("home.lead.noAccount")
                    : messages.format("home.lead.noSignIn", OfficialProfileService.profileName(LauncherVersion.MINECRAFT))
                : messages.format("home.lead.noJava", LauncherVersion.MINECRAFT, Integer.toString(LauncherVersion.JAVA_MAJOR));
            case READY -> session.instance().isPresent() ? messages.get("home.lead.ready")
                : messages.format("home.lead.readyNotInstalled", LauncherVersion.MINECRAFT);
            case INSTALLING -> messages.get("home.lead.installing");
            case VERIFYING -> messages.get("home.lead.verifying");
            case RUNNING -> messages.get("home.lead.running");
            case ERROR -> messages.get("home.lead.error");
        };
    }

    private String computeFacts() {
        final Optional<JavaInstall> java = session.java();
        final String detected = java.map(JavaInstall::version).filter(v -> !v.isBlank()).orElse(messages.get("home.facts.javaNotFound"));
        return messages.format("home.facts", LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER,
            Integer.toString(LauncherVersion.JAVA_MAJOR), detected);
    }

    /**
     * @param account account
     * @return short account type label for narrow places (sidebar chip)
     */
    public String accountChipLabel(final Account account) {
        if (account.type() == AccountType.OFFLINE) {
            return messages.get("account.chip.offline");
        }
        if (account.type() == AccountType.DEVELOPMENT) {
            return messages.get("account.chip.development");
        }
        return messages.get("account.chip.microsoft");
    }

    /**
     * @param account account
     * @return localised account type label
     */
    public String accountTypeLabel(final Account account) {
        if (account.type() == AccountType.OFFLINE) {
            return messages.get("account.type.offline");
        }
        if (account.type() == AccountType.DEVELOPMENT) {
            return messages.get("account.type.development");
        }
        return messages.get("account.type.microsoft");
    }
}
