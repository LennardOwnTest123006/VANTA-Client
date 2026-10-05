package dev.vanta.launcher;

import dev.vanta.launcher.cli.LauncherCli;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.ui.LauncherLinks;

import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Process entry point.
 *
 * <p>The class deliberately does not extend {@code javafx.application.Application}: JavaFX refuses to start an
 * {@code Application} subclass that lives on the class path without the JavaFX modules being resolved, so the
 * UI ({@code dev.vanta.launcher.ui.LauncherApp}) is started reflectively.</p>
 *
 * <ul>
 *   <li>Any command line flag (other than {@code --ui}) runs the {@link LauncherCli}, which needs no JavaFX: it works
 *       with every jar on every platform.</li>
 *   <li>Without flags the UI starts, but first the JavaFX platform this jar was built for ({@code javafx.platform}) is
 *       compared with the running OS and architecture ({@link PlatformCheck}). On a mismatch the launcher prints which
 *       file to download instead, also shows it in a Swing dialog when a display is available (a double-clicked jar has
 *       no console), and exits with code 1.</li>
 *   <li>Every other failed UI start (JavaFX missing, toolkit error, any exception on the way) also explains itself and
 *       exits with code 1, never 0.</li>
 *   <li>Before the UI starts, {@link StartupDiagnostics} opens {@code <data>/logs/launcher-0.log} (the Windows program has
 *       no console); a failed start is also written to {@code <data>/logs/startup-error.txt}, whose path the error
 *       window names.</li>
 * </ul>
 */
public final class Main {

    /** Fully qualified name of the JavaFX application class built by the UI module. */
    static final String UI_CLASS = "dev.vanta.launcher.ui.LauncherApp";
    /** Exit code of a failed UI start. */
    static final int UI_FAILED = 1;

    private static final String DIALOG_TITLE = "VANTA Launcher";

    private Main() {
    }

    /** Everything {@link #start} needs from the outside world; tests replace it. */
    interface Host {
        /** @return the running platform */
        OsInfo os();

        /** @return whether no display is available ({@code GraphicsEnvironment.isHeadless()}) */
        boolean headless();

        /** @return whether the UI class and the JavaFX runtime are present */
        boolean uiAvailable();

        /**
         * Starts the JavaFX UI and returns when it exits.
         *
         * @param args command line
         * @throws Throwable when the UI cannot start
         */
        void launchUi(String[] args) throws Throwable;

        /**
         * Shows an error dialog (only called when not headless).
         *
         * @param title   title
         * @param message text
         * @throws Throwable when no dialog can be shown
         */
        void showError(String title, String message) throws Throwable;

        /**
         * Records a failed UI start in the launcher log and in {@code startup-error.txt}.
         *
         * @param message what failed
         * @param cause   the cause (may be null)
         * @return the report file, empty when none could be written
         */
        default Optional<Path> recordFailure(final String message, final Throwable cause) {
            return Optional.empty();
        }

        /**
         * Runs the command line interface.
         *
         * @param args command line
         * @param out  standard output
         * @param err  standard error
         * @return exit code
         */
        int runCli(String[] args, PrintStream out, PrintStream err);
    }

    /**
     * Starts the launcher.
     *
     * @param args command line arguments; any argument starting with {@code -} other than {@code --ui} selects the CLI
     */
    public static void main(final String[] args) {
        StartupDiagnostics diagnostics = null;
        if (!LauncherCli.wantsCli(args)) {
            // The first thing the UI start does: a log file, because the Windows program has no console.
            diagnostics = StartupDiagnostics.forDefaultDataDir();
            diagnostics.begin();
            StartupDiagnostics.installUncaughtHandler();
        }
        final OptionalInt code = start(args, System.out, System.err, LauncherVersion.JAVAFX_PLATFORM, new SystemHost(diagnostics));
        if (code.isPresent()) {
            System.exit(code.getAsInt());
        }
    }

    /**
     * Decides between CLI and UI and runs it.
     *
     * @param args     command line
     * @param out      standard output
     * @param err      standard error
     * @param builtFor JavaFX platform this jar was built for ({@link LauncherVersion#JAVAFX_PLATFORM})
     * @param host     environment
     * @return the exit code, or empty when the UI ran and exited normally (the JVM then ends on its own)
     */
    static OptionalInt start(final String[] args, final PrintStream out, final PrintStream err, final String builtFor, final Host host) {
        Objects.requireNonNull(host, "host");
        if (LauncherCli.wantsCli(args)) {
            return OptionalInt.of(host.runCli(args, out, err));
        }
        try {
            final Optional<String> mismatch = PlatformCheck.mismatch(builtFor, host.os(), LauncherVersion.VERSION, releasesPage());
            if (mismatch.isPresent()) {
                return OptionalInt.of(fail("VANTA Launcher " + LauncherVersion.VERSION + " cannot start here.\n\n" + mismatch.get(), null, err, host));
            }
            if (!host.uiAvailable()) {
                return OptionalInt.of(fail("VANTA Launcher " + LauncherVersion.VERSION + " could not start its user interface: JavaFX is not"
                    + " available in this Java runtime (this jar was built for " + PlatformCheck.describePlatform(builtFor) + ").\n"
                    + "Use the release file for your system, or run with --help for the command line.", null, err, host));
            }
            host.launchUi(args);
            return OptionalInt.empty();
        } catch (Throwable t) {
            final Throwable cause = t instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
            return OptionalInt.of(fail("VANTA Launcher " + LauncherVersion.VERSION + " could not start its user interface: " + cause
                + "\nRun with --help for the command line, which works without a user interface.", cause, err, host));
        }
    }

    private static int fail(final String message, final Throwable cause, final PrintStream err, final Host host) {
        Optional<Path> report = Optional.empty();
        try {
            report = host.recordFailure(message, cause);
        } catch (RuntimeException e) {
            err.println("(The error report could not be written: " + e + ")");
        }
        final String shown = report.map(p -> message + "\n\nDetails were saved to " + p).orElse(message);
        err.println(shown);
        if (cause != null) {
            cause.printStackTrace(err);
        }
        if (!host.headless()) {
            try {
                host.showError(DIALOG_TITLE, shown);
            } catch (Throwable dialogFailure) {
                err.println("(The message could not be shown in a window either: " + dialogFailure + ")");
            }
        }
        return UI_FAILED;
    }

    private static String releasesPage() {
        final LauncherLinks links = LauncherLinks.load(System.getenv());
        return links.releases().map(URI::toString).orElse(links.source() + "/releases");
    }

    /**
     * @return whether the JavaFX UI class and the JavaFX runtime are present on the class path / module graph
     */
    public static boolean uiAvailable() {
        try {
            Class.forName(UI_CLASS);
            Class.forName("javafx.application.Application");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** The real environment: system properties, AWT, JavaFX through reflection. */
    private static final class SystemHost implements Host {

        private final StartupDiagnostics diagnostics;

        SystemHost(final StartupDiagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public Optional<Path> recordFailure(final String message, final Throwable cause) {
            return (diagnostics == null ? StartupDiagnostics.forDefaultDataDir() : diagnostics).writeError(message, cause);
        }

        @Override
        public OsInfo os() {
            return OsInfo.detect();
        }

        @Override
        public boolean headless() {
            try {
                return GraphicsEnvironment.isHeadless();
            } catch (Throwable t) {
                return true;
            }
        }

        @Override
        public boolean uiAvailable() {
            return Main.uiAvailable();
        }

        @Override
        public void launchUi(final String[] args) throws Throwable {
            final Class<?> app = Class.forName(UI_CLASS);
            final Method launch = app.getMethod("launch", String[].class);
            try {
                launch.invoke(null, (Object) args);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }

        @Override
        public void showError(final String title, final String message) throws Throwable {
            EventQueue.invokeAndWait(() -> {
                // A wrapping, read-only text area: long sentences stay inside the dialog and the file name can be copied.
                final javax.swing.JTextArea text = new javax.swing.JTextArea(message);
                text.setLineWrap(true);
                text.setWrapStyleWord(true);
                text.setEditable(false);
                text.setOpaque(false);
                text.setBorder(null);
                text.setFont(javax.swing.UIManager.getFont("Label.font"));
                text.setColumns(60);
                // With a fixed width the wrapped height is computed correctly before the dialog is laid out.
                text.setSize(text.getPreferredSize().width, Short.MAX_VALUE);
                javax.swing.JOptionPane.showMessageDialog(null, text, title, javax.swing.JOptionPane.ERROR_MESSAGE);
            });
        }

        @Override
        public int runCli(final String[] args, final PrintStream out, final PrintStream err) {
            return LauncherCli.run(args, out, err);
        }
    }
}
