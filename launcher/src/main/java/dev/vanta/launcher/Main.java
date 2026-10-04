package dev.vanta.launcher;

import dev.vanta.launcher.cli.LauncherCli;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Process entry point.
 *
 * <p>The class deliberately does not extend {@code javafx.application.Application}: JavaFX refuses to start an
 * {@code Application} subclass that lives on the class path without the JavaFX modules being resolved, so the
 * UI ({@code dev.vanta.launcher.ui.LauncherApp}) is started reflectively. When the UI class is absent (headless
 * builds, CI) or when command line flags are given, the {@link LauncherCli} runs instead.</p>
 */
public final class Main {

    /** Fully qualified name of the JavaFX application class built by the UI module. */
    static final String UI_CLASS = "dev.vanta.launcher.ui.LauncherApp";

    private Main() {
    }

    /**
     * Starts the launcher.
     *
     * @param args command line arguments; any argument starting with {@code --} other than {@code --ui} selects
     *             the CLI
     */
    public static void main(final String[] args) {
        if (LauncherCli.wantsCli(args) || !uiAvailable()) {
            final int code = LauncherCli.run(args, System.out, System.err);
            System.exit(code);
            return;
        }
        try {
            final Class<?> app = Class.forName(UI_CLASS);
            final Method launch = app.getMethod("launch", String[].class);
            launch.invoke(null, (Object) args);
        } catch (ReflectiveOperationException e) {
            final Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
            System.err.println("VANTA Launcher could not start its user interface: " + cause);
            System.err.println("Falling back to the command line interface. Run with --help for usage.");
            System.exit(LauncherCli.run(new String[] {"--help"}, System.out, System.err));
        }
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
}
