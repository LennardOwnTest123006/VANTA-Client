package dev.vanta.launcher.ui.screenshots;

import dev.vanta.launcher.ui.LauncherApp;
import dev.vanta.launcher.ui.UiSmoke;
import dev.vanta.launcher.ui.testutil.FakeBackend;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Starts the real launcher window with the scripted {@link FakeBackend} of {@link ScreenshotMain} (Modrinth results and
 * installed content from fixtures, no network) and drives it through the {@link UiSmoke} hook: the window is sized,
 * a page is shown, a screenshot is written and the launcher exits. Run through {@code ./gradlew uiSmoke} (under
 * {@code xvfb-run -a} on a headless Linux box, or with {@code -Pheadless} for the Monocle platform):
 *
 * <pre>./gradlew uiSmoke -PsmokePage=mods -PsmokeSize=1000x600 -PsmokeOut=build/ui-smoke/mods-1000x600.png</pre>
 *
 * <p>Exit code 0 when the screenshot was written, {@value UiSmoke#SCREENSHOT_FAILED} when it could not be.</p>
 */
public final class UiSmokeMain {

    private static final PrintStream OUT = System.out;

    private UiSmokeMain() {
    }

    /**
     * @param args {@code <png> [page] [WxH] [exitAfterSeconds] [toast]}; with {@code toast} a success toast is shown and the
     *             page is scrolled to its end, the situation in which a toast lies over the Mods page's lower Install buttons
     * @throws Exception on failure
     */
    public static void main(final String[] args) throws Exception {
        if (args.length == 0 || args[0].isBlank()) {
            System.err.println("usage: UiSmokeMain <png> [home|mods|versions|logs|settings|about] [<width>x<height>] [exitAfterSeconds] [toast]");
            System.exit(64);
            return;
        }
        final Path png = Path.of(args[0]).toAbsolutePath();
        final String page = args.length > 1 ? args[1].trim() : "";
        final String size = args.length > 2 ? args[2].trim() : "";
        final String exitAfter = args.length > 3 && !args[3].isBlank() ? args[3].trim() : "6";
        final boolean toast = args.length > 4 && "toast".equalsIgnoreCase(args[4].trim());
        final Path data = Files.createTempDirectory("vanta-ui-smoke").resolve("VANTA Launcher");
        final FakeBackend backend = ScreenshotMain.scriptedBackend(data);
        backend.putEnv(UiSmoke.SCREENSHOT_ENV, png.toString());
        backend.putEnv(UiSmoke.EXIT_AFTER_ENV, exitAfter);
        if (!page.isEmpty()) {
            backend.putEnv(UiSmoke.PAGE_ENV, page);
        }
        if (!size.isEmpty()) {
            backend.putEnv(UiSmoke.SIZE_ENV, size);
        }
        OUT.println("UI smoke: page=" + (page.isEmpty() ? "(home)" : page) + " size=" + (size.isEmpty() ? "(default)" : size) + " -> " + png);
        LauncherApp.setExitHandlerForTesting(code -> {
            OUT.println("UI smoke: exit code " + code);
            System.exit(code);
        });
        LauncherApp.configureForTesting(() -> backend, toast ? UiSmokeMain::showToastOverScrolledPage : null);
        LauncherApp.launch(new String[0]);
    }

    /** Runs once the window is up (after the smoke hook sized it and showed the page). */
    private static void showToastOverScrolledPage(final LauncherApp app) {
        app.context().toasts().success("Sodium installed", "It loads the next time the game starts.");
        // Scroll the shown page to its end once it is laid out (the next pulse), so the lowest rows are under the toast.
        javafx.application.Platform.runLater(() -> app.window().page(app.context().navigation().current()).lookupAll(".scroll-pane")
            .forEach(n -> ((javafx.scene.control.ScrollPane) n).setVvalue(1.0)));
    }
}
