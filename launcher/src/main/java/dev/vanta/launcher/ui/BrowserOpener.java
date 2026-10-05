package dev.vanta.launcher.ui;

import dev.vanta.launcher.core.util.OsInfo;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Opens a web page in the user's browser and reports whether that worked.
 *
 * <p>Launcher 1.0.0 and 1.0.1 used JavaFX {@code HostServices.showDocument} alone, which fails silently (it only prints
 * to stderr), so a click on "Website" or "How to configure" could do nothing at all. The order now is:</p>
 *
 * <ol>
 *   <li>{@link java.awt.Desktop#browse} when the platform supports it;</li>
 *   <li>the operating system's own opener, checked by its exit code: {@code xdg-open} on Linux, {@code open} on
 *       macOS, {@code rundll32 url.dll,FileProtocolHandler} on Windows (http(s) addresses only; the address is one
 *       argument, no shell is involved);</li>
 *   <li>JavaFX {@code HostServices.showDocument}, which cannot report a failure ({@link Result#confirmed()} is false);</li>
 *   <li>when everything failed: {@link Method#NONE}. The caller copies the address to the clipboard and shows it.</li>
 * </ol>
 *
 * <p>{@link #open} blocks (an opener may take a moment) and must not run on the JavaFX application thread: AWT's
 * {@code Desktop} must not be driven from it.</p>
 */
public final class BrowserOpener {

    /** How long the operating system opener may run before it counts as started (xdg-open can wait for the browser). */
    public static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);

    /** How the page was opened. */
    public enum Method {
        /** {@code java.awt.Desktop#browse}. */
        DESKTOP,
        /** {@code xdg-open}, {@code open} or {@code rundll32 url.dll,FileProtocolHandler}. */
        COMMAND,
        /** JavaFX {@code HostServices}: handed over, but success cannot be confirmed. */
        HOST_SERVICES,
        /** Nothing worked. */
        NONE
    }

    /**
     * Outcome of {@link #open}.
     *
     * @param method   how the page was opened ({@link Method#NONE}: not at all)
     * @param failures what was tried and failed before, in order (for the log)
     */
    public record Result(Method method, List<String> failures) {

        public Result {
            Objects.requireNonNull(method, "method");
            failures = List.copyOf(failures);
        }

        /** @return whether any opener accepted the address */
        public boolean opened() {
            return method != Method.NONE;
        }

        /** @return whether the opener confirmed that the browser got the address (not so for HostServices) */
        public boolean confirmed() {
            return method == Method.DESKTOP || method == Method.COMMAND;
        }
    }

    /** {@code java.awt.Desktop}, replaceable in tests. */
    public interface DesktopBrowser {
        /** @return whether {@code Desktop.Action.BROWSE} is supported here */
        boolean supported();

        /**
         * @param uri address
         * @throws IOException when the browser could not be started
         */
        void browse(URI uri) throws IOException;
    }

    /** Runs an operating system command, replaceable in tests. */
    public interface CommandRunner {
        /**
         * @param command command and arguments (no shell)
         * @param timeout how long to wait for it to exit
         * @return its exit code, or empty when it was still running after the timeout (it keeps running)
         * @throws IOException          when it cannot be started (for example not installed)
         * @throws InterruptedException when interrupted while waiting
         */
        OptionalInt run(List<String> command, Duration timeout) throws IOException, InterruptedException;
    }

    private final OsInfo os;
    private final DesktopBrowser desktop;
    private final CommandRunner runner;
    private final Consumer<String> hostServices;
    private final Duration timeout;

    /**
     * @param os           platform (selects the command)
     * @param desktop      {@code java.awt.Desktop}
     * @param runner       command runner
     * @param hostServices JavaFX {@code HostServices::showDocument}
     * @param timeout      command timeout
     */
    public BrowserOpener(final OsInfo os, final DesktopBrowser desktop, final CommandRunner runner, final Consumer<String> hostServices,
                         final Duration timeout) {
        this.os = Objects.requireNonNull(os, "os");
        this.desktop = Objects.requireNonNull(desktop, "desktop");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.hostServices = Objects.requireNonNull(hostServices, "hostServices");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * @param os           platform
     * @param hostServices JavaFX {@code HostServices::showDocument}
     * @return an opener over the real {@code java.awt.Desktop} and {@link ProcessBuilder}
     */
    public static BrowserOpener system(final OsInfo os, final Consumer<String> hostServices) {
        return new BrowserOpener(os, new AwtDesktop(), BrowserOpener::runProcess, hostServices, COMMAND_TIMEOUT);
    }

    /**
     * The operating system's own command for opening an address.
     *
     * @param os  platform
     * @param uri address
     * @return the command, empty for other platforms and for addresses that are not http(s)
     */
    public static Optional<List<String>> command(final OsInfo os, final URI uri) {
        final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return Optional.empty();
        }
        final String url = uri.toString();
        if (os.isWindows()) {
            return Optional.of(List.of("rundll32", "url.dll,FileProtocolHandler", url));
        }
        if (os.isMac()) {
            return Optional.of(List.of("open", url));
        }
        if (os.isLinux()) {
            return Optional.of(List.of("xdg-open", url));
        }
        return Optional.empty();
    }

    /**
     * Opens the address with the first opener that works (blocking; never on the JavaFX application thread).
     *
     * @param uri address
     * @return how it was opened
     */
    public Result open(final URI uri) {
        final List<String> failures = new ArrayList<>();
        try {
            if (desktop.supported()) {
                desktop.browse(uri);
                return new Result(Method.DESKTOP, failures);
            }
            failures.add("java.awt.Desktop cannot browse on this system");
        } catch (IOException | RuntimeException | LinkageError e) {
            failures.add("java.awt.Desktop: " + e);
        }
        final Optional<List<String>> command = command(os, uri);
        if (command.isPresent()) {
            final String name = command.get().get(0);
            try {
                final OptionalInt exit = runner.run(command.get(), timeout);
                if (exit.isEmpty() || exit.getAsInt() == 0) {
                    // Still running after the timeout: it started the browser and waits for it (xdg-open may do that).
                    return new Result(Method.COMMAND, failures);
                }
                failures.add(name + " exited with code " + exit.getAsInt());
            } catch (IOException | RuntimeException e) {
                failures.add(name + ": " + e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.add(name + ": interrupted");
                return new Result(Method.NONE, failures);
            }
        }
        try {
            hostServices.accept(uri.toString());
            return new Result(Method.HOST_SERVICES, failures);
        } catch (RuntimeException e) {
            failures.add("HostServices: " + e);
        }
        return new Result(Method.NONE, failures);
    }

    private static OptionalInt runProcess(final List<String> command, final Duration timeout) throws IOException, InterruptedException {
        final Process process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
        process.getOutputStream().close();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(process.exitValue());
    }

    /** {@link java.awt.Desktop}; any failure to load AWT counts as "not supported". */
    private static final class AwtDesktop implements DesktopBrowser {
        @Override
        public boolean supported() {
            try {
                return java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE);
            } catch (RuntimeException | LinkageError e) {
                return false;
            }
        }

        @Override
        public void browse(final URI uri) throws IOException {
            java.awt.Desktop.getDesktop().browse(uri);
        }
    }
}
