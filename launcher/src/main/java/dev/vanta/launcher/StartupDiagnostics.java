package dev.vanta.launcher;

import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.core.paths.LauncherPaths;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Makes a failed start visible. The Windows launcher ({@code VANTA Launcher.exe}, made by jpackage) is a GUI program
 * without a console, so anything printed is lost; instead:
 *
 * <ul>
 *   <li>{@link #begin()} opens {@code <data>/logs/launcher-0.log} before anything else happens and records the
 *       environment (launcher and Java version, operating system, how the launcher was started, data directory);</li>
 *   <li>{@link #installUncaughtHandler()} sends exceptions of every thread to that log;</li>
 *   <li>{@link #writeError(String, Throwable)} writes {@code <data>/logs/startup-error.txt} (message, environment and the
 *       full stack trace) when the user interface cannot start; {@link Main} and
 *       {@code dev.vanta.launcher.ui.LauncherApp} then show the message and the path of that file in a window.</li>
 * </ul>
 *
 * <p>Nothing here may throw: diagnostics must never be the reason the launcher does not start.</p>
 */
public final class StartupDiagnostics {

    /** Name of the error report. */
    public static final String ERROR_FILE = "startup-error.txt";

    private static final Logger LOG = LauncherLog.get("Startup");

    private final Path logsDir;

    /**
     * @param logsDir {@code <data>/logs}
     */
    public StartupDiagnostics(final Path logsDir) {
        this.logsDir = Objects.requireNonNull(logsDir, "logsDir");
    }

    /**
     * @return diagnostics in the data directory the UI uses ({@code VANTA_LAUNCHER_HOME} or the platform default)
     */
    public static StartupDiagnostics forDefaultDataDir() {
        Path logs;
        try {
            logs = LauncherPaths.detect().logsDir();
        } catch (RuntimeException e) {
            logs = Path.of(System.getProperty("java.io.tmpdir", "."), "vanta-launcher-logs");
        }
        return new StartupDiagnostics(logs);
    }

    /** @return {@code <data>/logs} */
    public Path logsDir() {
        return logsDir;
    }

    /** @return {@code <data>/logs/startup-error.txt} */
    public Path errorFile() {
        return logsDir.resolve(ERROR_FILE);
    }

    /**
     * Starts file logging and records the environment. A previous {@code startup-error.txt} is renamed to
     * {@code startup-error.previous.txt} so the file always describes the latest start.
     */
    public void begin() {
        try {
            LauncherLog.init(logsDir);
        } catch (IOException | RuntimeException e) {
            System.err.println("VANTA Launcher: could not open the log in " + logsDir + ": " + e);
            return;
        }
        try {
            final Path previous = errorFile();
            if (Files.isRegularFile(previous)) {
                Files.move(previous, logsDir.resolve("startup-error.previous.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.FINE, "Could not rotate the previous startup error", e);
        }
        LOG.log(Level.INFO, "VANTA Launcher {0} starting ({1})", new Object[] {LauncherVersion.VERSION, environmentLine()});
        environment().forEach((k, v) -> LOG.log(Level.INFO, "  {0} = {1}", new Object[] {k, v}));
    }

    /** Logs uncaught exceptions of every thread (they would otherwise vanish in a GUI program without a console). */
    public static void installUncaughtHandler() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                LOG.log(Level.SEVERE, "Uncaught exception in thread " + thread.getName(), error);
            } catch (RuntimeException ignored) {
                // logging must not fail
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                error.printStackTrace(System.err);
            }
        });
    }

    /**
     * Records a failed start in the log and in {@code startup-error.txt} (falling back to the temporary directory when
     * the data directory cannot be written).
     *
     * @param message what failed, in words
     * @param error   the cause (may be null)
     * @return the file that was written, empty when no file could be written
     */
    public Optional<Path> writeError(final String message, final Throwable error) {
        try {
            LOG.log(Level.SEVERE, message, error);
        } catch (RuntimeException ignored) {
            // logging must not fail
        }
        final String report = report(message, error);
        for (Path target : new Path[] {errorFile(), Path.of(System.getProperty("java.io.tmpdir", "."), "vanta-launcher-" + ERROR_FILE)}) {
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, report, StandardCharsets.UTF_8);
                return Optional.of(target);
            } catch (IOException | RuntimeException e) {
                System.err.println("VANTA Launcher: could not write " + target + ": " + e);
            }
        }
        return Optional.empty();
    }

    /**
     * @param message what failed
     * @param error   cause (may be null)
     * @return the text of {@code startup-error.txt}
     */
    static String report(final String message, final Throwable error) {
        final StringBuilder sb = new StringBuilder();
        sb.append("VANTA Launcher ").append(LauncherVersion.VERSION).append(" could not start (").append(Instant.now()).append(")\n\n");
        sb.append(message == null ? "" : message.trim()).append("\n\n");
        sb.append("Environment\n");
        environment().forEach((k, v) -> sb.append("  ").append(k).append(" = ").append(v).append('\n'));
        if (error != null) {
            final StringWriter sw = new StringWriter();
            try (PrintWriter pw = new PrintWriter(sw)) {
                error.printStackTrace(pw);
            }
            sb.append("\nStack trace\n").append(sw);
        }
        sb.append("\nPlease attach this file and the launcher-0.log next to it when you report the problem:\n")
            .append("https://github.com/LennardOwnTest123006/VANTA-Client/issues\n");
        return sb.toString();
    }

    private static String environmentLine() {
        return "Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name") + " " + System.getProperty("os.version")
            + " " + System.getProperty("os.arch") + ", JavaFX platform " + LauncherVersion.JAVAFX_PLATFORM;
    }

    /** @return the facts that explain most start problems (no secrets, no user data beyond paths) */
    static Map<String, String> environment() {
        final Map<String, String> m = new TreeMap<>();
        for (String key : new String[] {"java.version", "java.vendor", "java.home", "java.runtime.version", "os.name", "os.version",
            "os.arch", "user.dir", "file.encoding", "sun.jnu.encoding", "jpackage.app-path", "vanta.launcher.packaged", "prism.order",
            "javafx.cachedir"}) {
            final String value = System.getProperty(key);
            if (value != null) {
                m.put(key, value);
            }
        }
        m.put("launcher.version", LauncherVersion.VERSION);
        m.put("javafx.platform (build)", LauncherVersion.JAVAFX_PLATFORM);
        m.put("module", String.valueOf(StartupDiagnostics.class.getModule()));
        final java.security.CodeSource source = StartupDiagnostics.class.getProtectionDomain().getCodeSource();
        m.put("code source", source == null || source.getLocation() == null ? "unknown" : source.getLocation().toString());
        return m;
    }
}
