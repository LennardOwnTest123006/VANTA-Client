package dev.vanta.launcher.ui;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.StartupDiagnostics;
import dev.vanta.launcher.core.LauncherServices;
import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.ui.backend.CoreBackend;
import dev.vanta.launcher.ui.backend.LauncherBackend;
import dev.vanta.launcher.ui.model.JulLogBridge;
import dev.vanta.launcher.ui.model.LogBuffer;
import dev.vanta.launcher.ui.model.LogLevel;
import dev.vanta.launcher.ui.model.NavigationModel;
import dev.vanta.launcher.ui.model.UiExecutors;
import dev.vanta.launcher.ui.prefs.UiPreferences;
import dev.vanta.launcher.ui.prefs.UiPreferencesStore;
import dev.vanta.launcher.ui.view.AppContext;
import dev.vanta.launcher.ui.view.MainWindow;
import dev.vanta.launcher.ui.view.Typography;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaFX entry point of the launcher. {@link dev.vanta.launcher.Main} calls {@link #launch(String[])} reflectively
 * so the class path variant without JavaFX still starts the CLI.
 *
 * <p>Lifecycle: {@link #init()} builds the core services on the launcher thread, {@link #start(Stage)} loads fonts,
 * builds the window and kicks off the first refresh; {@link #stop()} closes the services. {@code Platform.exit()}
 * is only ever requested through {@link #shutdown()}, after the services are closed.</p>
 *
 * <p>Anything that fails in {@link #init()} or {@link #start(Stage)} is logged, written to
 * {@code <data>/logs/startup-error.txt} ({@link StartupDiagnostics}) and shown in an error window that names that file;
 * the process then exits with code 1. {@link UiSmoke} adds the CI screenshot / exit hook when its environment variables
 * are set.</p>
 */
public final class LauncherApp extends Application {

    /** Default window size. */
    public static final double DEFAULT_WIDTH = 1120;
    /** Default window height. */
    public static final double DEFAULT_HEIGHT = 720;
    /** Minimum window width. */
    public static final double MIN_WIDTH = 960;
    /** Minimum window height. */
    public static final double MIN_HEIGHT = 600;
    /** Stylesheet location. */
    public static final String STYLESHEET = "/dev/vanta/launcher/ui/theme/vanta.css";

    private static final Logger LOG = Logger.getLogger("VANTA.UI");
    private static final List<Integer> ICON_SIZES = List.of(16, 24, 32, 48, 64, 128, 256, 512);

    private static volatile Supplier<LauncherBackend> backendOverride;
    private static volatile Consumer<LauncherApp> readyHook;
    private static volatile IntConsumer exitHandler = System::exit;

    private LauncherBackend backend;
    private UiExecutors executors;
    private AppContext context;
    private MainWindow window;
    private Stage stage;
    private JulLogBridge logBridge;
    private boolean closed;
    private Throwable initFailure;

    /**
     * Entry point used by {@link dev.vanta.launcher.Main}.
     *
     * @param args command line arguments
     */
    public static void launch(final String... args) {
        Application.launch(LauncherApp.class, args);
    }

    /**
     * Test hook: use a different backend and get notified when the window is up. Must be called before
     * {@link #launch(String[])}.
     *
     * @param backend backend factory (null restores the production backend)
     * @param onReady receives the running application after {@link #start(Stage)} (may be null)
     */
    public static void configureForTesting(final Supplier<LauncherBackend> backend, final Consumer<LauncherApp> onReady) {
        backendOverride = backend;
        readyHook = onReady;
    }

    /**
     * Test hook: what ends the process after a failed start or a finished UI smoke run (default {@link System#exit}).
     *
     * @param handler receives the exit code (null restores {@link System#exit})
     */
    public static void setExitHandlerForTesting(final IntConsumer handler) {
        exitHandler = handler == null ? System::exit : handler;
    }

    @Override
    public void init() {
        try {
            final Supplier<LauncherBackend> override = backendOverride;
            backend = override != null ? Objects.requireNonNull(override.get(), "backend") : new CoreBackend(LauncherServices.createDefault());
            executors = UiExecutors.javafx();
        } catch (Throwable t) {
            // Shown by start(), where the toolkit can display a window.
            initFailure = t;
        }
    }

    @Override
    public void start(final Stage primaryStage) {
        this.stage = primaryStage;
        if (initFailure != null) {
            reportFatal("The launcher services could not be created", initFailure);
            return;
        }
        try {
            startWindow();
        } catch (Throwable t) {
            reportFatal("The launcher window could not be built", t);
        }
    }

    private void startWindow() {
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> LOG.log(Level.SEVERE, "Uncaught exception on the UI thread", e));
        final List<String> fonts = Typography.load();
        LOG.log(Level.FINE, "Loaded fonts: {0}", fonts);

        final LogBuffer launcherLog = LogBuffer.standard();
        final LogBuffer gameLog = LogBuffer.standard();
        logBridge = new JulLogBridge(launcherLog).attach(LauncherLog.root());
        final Messages messages = Messages.load(Locale.getDefault());
        final LauncherLinks links = LauncherLinks.load(backend.env());
        final UiPreferencesStore prefsStore = UiPreferencesStore.in(backend.paths().dataDir());
        context = new AppContext(backend, executors, messages, links, launcherLog, gameLog, prefsStore, this::openInBrowser);
        launcherLog.append(LogLevel.INFO, "VANTA Launcher " + LauncherVersion.VERSION + " · " + LauncherVersion.statusLine());
        launcherLog.append(LogLevel.INFO, "Data directory: " + backend.paths().dataDir());

        window = new MainWindow(context);
        window.setOnInstallerOpened(this::shutdown);
        final UiPreferences prefs = context.prefs();
        final Scene scene = new Scene(window, prefs.hasWindowSize() ? Math.max(MIN_WIDTH, prefs.windowWidth()) : DEFAULT_WIDTH,
            prefs.hasWindowSize() ? Math.max(MIN_HEIGHT, prefs.windowHeight()) : DEFAULT_HEIGHT);
        scene.getStylesheets().add(Objects.requireNonNull(LauncherApp.class.getResource(STYLESHEET), "stylesheet").toExternalForm());
        window.applyTheme();

        stage.setTitle(messages.get("app.title"));
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        for (int size : ICON_SIZES) {
            try (InputStream in = LauncherApp.class.getResourceAsStream("/dev/vanta/launcher/ui/icon-" + size + ".png")) {
                if (in != null) {
                    stage.getIcons().add(new Image(in));
                }
            } catch (java.io.IOException e) {
                LOG.log(Level.FINE, "Icon {0} not loadable", size);
            }
        }
        stage.setScene(scene);
        stage.setMaximized(prefs.maximized());
        stage.setOnCloseRequest(e -> {
            e.consume();
            shutdown();
        });
        Platform.setImplicitExit(false);

        restoreLastPage(prefs);
        wireGameWindowBehaviour();
        stage.show();
        window.requestFocus();
        LOG.log(Level.INFO, "Main window shown ({0} x {1})", new Object[] {(int) scene.getWidth(), (int) scene.getHeight()});
        UiSmoke.fromEnvironment(backend.env()).ifPresent(smoke -> smoke.start(stage, scene, context.navigation(),
            context.session().loadedProperty(), code -> {
                shutdown();
                exitHandler.accept(code);
            }));

        context.session().refreshAll();
        // Once per start: a player who just updated gets mods that would stop Minecraft switched off right away.
        context.home().runStartupCheck();
        context.session().loadedProperty().addListener((obs, old, now) -> {
            if (now && backend.settings().autoUpdateCheck()) {
                context.updates().check(false);
            }
        });
        final Consumer<LauncherApp> ready = readyHook;
        if (ready != null) {
            ready.accept(this);
        }
    }

    /**
     * A failed start: log it, write {@code startup-error.txt}, show it in a window, exit with 1.
     */
    private void reportFatal(final String what, final Throwable error) {
        final Path logs = backend != null ? backend.paths().logsDir() : StartupDiagnostics.forDefaultDataDir().logsDir();
        final String message = "VANTA Launcher " + LauncherVersion.VERSION + " could not start: " + what + ".\n" + error;
        final Optional<Path> report = new StartupDiagnostics(logs).writeError(message, error);
        try {
            final Alert alert = new Alert(Alert.AlertType.ERROR, "", ButtonType.CLOSE);
            alert.setTitle("VANTA Launcher");
            alert.setHeaderText("VANTA Launcher could not start");
            alert.setContentText(message + report.map(p -> "\n\nDetails were saved to " + p).orElse(""));
            final StringWriter trace = new StringWriter();
            try (PrintWriter pw = new PrintWriter(trace)) {
                error.printStackTrace(pw);
            }
            final TextArea details = new TextArea(trace.toString());
            details.setEditable(false);
            details.setWrapText(false);
            alert.getDialogPane().setExpandableContent(details);
            alert.showAndWait();
        } catch (Throwable dialogFailure) {
            LOG.log(Level.SEVERE, "The error window could not be shown either", dialogFailure);
        } finally {
            closed = true;
            closeResources();
            exitHandler.accept(1);
        }
    }

    private void restoreLastPage(final UiPreferences prefs) {
        if (prefs.lastPage().isEmpty()) {
            return;
        }
        try {
            context.navigation().navigate(NavigationModel.Page.valueOf(prefs.lastPage()));
        } catch (IllegalArgumentException ignored) {
            // unknown page id from a newer version: stay on Home
        }
    }

    private void wireGameWindowBehaviour() {
        context.home().onGameStarted(game -> {
            if (!backend.settings().keepLauncherOpen()) {
                stage.hide();
            }
        });
        context.home().onGameExited(code -> {
            if (stage.isShowing()) {
                return;
            }
            if (code == 0) {
                shutdown();
            } else {
                stage.show();
                stage.toFront();
            }
        });
    }

    private void openInBrowser(final String url) {
        getHostServices().showDocument(url);
    }

    /**
     * Saves window preferences, closes the core services and then exits the JavaFX platform.
     */
    public void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (context != null && stage != null) {
                context.savePrefs(context.prefs().withWindow(stage.getWidth(), stage.getHeight(), stage.isMaximized()));
            }
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Could not save window preferences", e);
        }
        closeResources();
        Platform.exit();
    }

    @Override
    public void stop() {
        closeResources();
    }

    private void closeResources() {
        if (context != null) {
            context.home().stopWatchingCrashReports();
        }
        if (logBridge != null) {
            logBridge.detach();
            logBridge = null;
        }
        if (executors != null) {
            executors.shutdown();
            executors = null;
        }
        if (backend != null) {
            try {
                backend.close();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Error while closing the launcher services", e);
            }
            backend = null;
        }
    }

    /** @return the application context (available after {@link #start(Stage)}) */
    public AppContext context() {
        return context;
    }

    /** @return the main window */
    public MainWindow window() {
        return window;
    }

    /** @return the primary stage */
    public Stage stage() {
        return stage;
    }
}
