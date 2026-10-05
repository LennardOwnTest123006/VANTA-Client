package dev.vanta.launcher.ui.screenshots;

import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.ui.LauncherApp;
import dev.vanta.launcher.ui.model.LogsViewModel;
import dev.vanta.launcher.ui.model.NavigationModel;
import dev.vanta.launcher.ui.model.SettingsViewModel;
import dev.vanta.launcher.ui.testutil.FakeBackend;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Renders every launcher screen with a scripted {@link FakeBackend} and saves PNGs, so the UI can be reviewed
 * without Minecraft, Microsoft or a network. Run through {@code ./gradlew screenshots} (under {@code xvfb-run -a} on a
 * headless Linux box, or with {@code -Pheadless} to use the Monocle headless platform).
 *
 * <p>Nothing in these images is real product data: the account, Java runtime, release manifests and log lines are
 * test fixtures. The output lives under {@code build/} and is never committed.</p>
 */
public final class ScreenshotMain {

    private static final PrintStream OUT = System.out;

    private ScreenshotMain() {
    }

    /**
     * @param args {@code [outputDir]}
     * @throws Exception on failure
     */
    public static void main(final String[] args) throws Exception {
        final Path out = Path.of(args.length > 0 ? args[0] : "build/screenshots").toAbsolutePath();
        Files.createDirectories(out);
        final Path data = Files.createTempDirectory("vanta-screenshots");
        final FakeBackend backend = scriptedBackend(data);
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        LauncherApp.configureForTesting(() -> backend, app -> new Script(app, backend, out, done, failure).start());
        final Thread fx = new Thread(() -> {
            try {
                LauncherApp.launch(new String[0]);
            } catch (Throwable t) {
                failure.set(t);
                done.countDown();
            }
        }, "javafx-launcher");
        fx.setDaemon(true);
        fx.start();
        if (!done.await(180, TimeUnit.SECONDS)) {
            failure.compareAndSet(null, new IllegalStateException("Screenshot script did not finish in time"));
        }
        Platform.exit();
        if (failure.get() != null) {
            failure.get().printStackTrace(System.err);
            System.exit(1);
        }
        OUT.println("Screenshots written to " + out);
        System.exit(0);
    }

    /**
     * @param data data directory
     * @return a backend that looks like a healthy, signed-in installation with an update waiting
     */
    static FakeBackend scriptedBackend(final Path data) {
        final FakeBackend backend = new FakeBackend(data);
        // The releases URL stays empty: the Settings page shows the built-in default in effect.
        backend.settings = backend.settings.withMsClientId("00000000-0000-4000-8000-000000000000").withKeepLauncherOpen(true);
        backend.accounts.add(FakeBackend.microsoftAccount("NovaPlayer"));
        backend.javaInstalls.add(backend.temurin21());
        backend.javaInstalls.add(backend.system17());
        backend.temurin = backend.temurin21();
        backend.instance = FakeBackend.installedInstance("1.0.0");
        backend.activeJar = data.resolve("instances/vanta-1.21.11/mods/vanta-client-1.0.0.jar");
        final ReleaseManifest kept = FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "0.0.0", "1.0.0", true).manifest();
        backend.kept.add(new VantaClientService.KeptVersion("1.0.0", kept, backend.activeJar, Instant.parse("2026-10-03T18:42:00Z")));
        backend.kept.add(new VantaClientService.KeptVersion("0.9.2", kept, data.resolve("versions/vanta-client/0.9.2/vanta-client-0.9.2.jar"),
            Instant.parse("2026-09-12T09:15:00Z")));
        backend.kept.add(new VantaClientService.KeptVersion("0.9.1", kept, null, Instant.parse("2026-08-30T20:03:00Z")));
        backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.1.0", true));
        backend.documents.put(URI.create("https://raw.githubusercontent.com/LennardOwnTest123006/VANTA-Client/HEAD/website/content/changelog/client-1.1.0.md"),
            """
            ---
            product: client
            version: 1.1.0
            ---
            # VANTA Client 1.1.0

            ## Added
            - HUD editor presets can be exported and imported as JSON
            - Crosshair editor: outline thickness and gap controls
            - Performance screen shows the active preset next to the FPS graph

            ## Fixed
            - Keybind conflicts with vanilla screenshot key are reported once
            - Settings search ignores accents

            ## Notes
            - Requires Fabric API 0.141.6+1.21.11 or newer
            """);
        return backend;
    }

    /** Sequential screenshot steps on the FX thread. */
    private static final class Script {
        private final LauncherApp app;
        private final FakeBackend backend;
        private final Path out;
        private final CountDownLatch done;
        private final AtomicReference<Throwable> failure;
        private final Deque<Step> steps = new ArrayDeque<>();

        private record Step(String name, Runnable action, int waitMillis) {
        }

        Script(final LauncherApp app, final FakeBackend backend, final Path out, final CountDownLatch done, final AtomicReference<Throwable> failure) {
            this.app = app;
            this.backend = backend;
            this.out = out;
            this.done = done;
            this.failure = failure;
        }

        void start() {
            final var ctx = app.context();
            final var window = app.window();
            step("01-home-ready", () -> { }, 1200);
            step("02-home-installing", () -> {
                backend.installGate = new CountDownLatch(1);
                ctx.home().play();
            }, 900);
            step("03-home-running", () -> backend.installGate.countDown(), 900);
            step("04-versions", () -> {
                backend.game.exit(0);
                ctx.navigation().navigate(NavigationModel.Page.VERSIONS);
            }, 700);
            step("05-logs-launcher", () -> {
                ctx.toasts().clear();
                ctx.navigation().navigate(NavigationModel.Page.LOGS);
            }, 600);
            step("06-logs-game", () -> ctx.logs().sourceProperty().set(LogsViewModel.Source.GAME), 400);
            step("07-settings", () -> ctx.navigation().navigate(NavigationModel.Page.SETTINGS), 600);
            step("08-about", () -> ctx.navigation().navigate(NavigationModel.Page.ABOUT), 600);
            step("09-signin", () -> {
                ctx.navigation().navigate(NavigationModel.Page.HOME);
                window.showSignIn();
            }, 900);
            step("10-update-dialog", () -> {
                window.dialogs().close();
                window.showUpdates();
            }, 900);
            step("11-home-signed-out", () -> {
                window.dialogs().close();
                backend.accounts.clear();
                ctx.session().refreshAll();
            }, 700);
            step("12-home-high-contrast", () -> {
                backend.accounts.add(FakeBackend.microsoftAccount("NovaPlayer"));
                backend.settings = backend.settings.withTheme(SettingsViewModel.THEME_HIGH_CONTRAST);
                ctx.session().refreshAll();
                window.applyTheme();
            }, 700);
            step("13-signin-not-configured", () -> {
                backend.settings = backend.settings.withTheme("vanta-dark");
                window.applyTheme();
                backend.signInConfigured = false;
                window.showSignIn();
            }, 700);
            step("14-home-min-size", () -> {
                window.dialogs().close();
                ctx.toasts().clear();
                app.stage().setWidth(LauncherApp.MIN_WIDTH);
                app.stage().setHeight(LauncherApp.MIN_HEIGHT);
            }, 900);
            step("15-versions-min-size", () -> ctx.navigation().navigate(NavigationModel.Page.VERSIONS), 700);
            step("16-home-no-sign-in", () -> {
                app.stage().setWidth(LauncherApp.DEFAULT_WIDTH);
                app.stage().setHeight(LauncherApp.DEFAULT_HEIGHT);
                backend.signInConfigured = false;
                backend.accounts.clear();
                ctx.session().refreshAll();
                ctx.navigation().navigate(NavigationModel.Page.HOME);
            }, 900);
            step("17-official-confirm", window::showOfficialProfile, 900);
            step("18-home-official-done", () -> {
                window.dialogs().close();
                ctx.home().installOfficialProfile();
            }, 900);
            step("19-home-no-sign-in-min-size", () -> {
                app.stage().setWidth(LauncherApp.MIN_WIDTH);
                app.stage().setHeight(LauncherApp.MIN_HEIGHT);
                ctx.home().verify();
                ctx.toasts().clear();
            }, 900);
            step("20-settings-releases", () -> {
                ctx.toasts().clear();
                app.stage().setWidth(LauncherApp.DEFAULT_WIDTH);
                app.stage().setHeight(LauncherApp.DEFAULT_HEIGHT);
                ctx.navigation().navigate(NavigationModel.Page.SETTINGS);
                // Scroll the settings list to the "Releases and sign-in" section.
                window.page(NavigationModel.Page.SETTINGS).lookupAll(".scroll-pane").forEach(n -> ((javafx.scene.control.ScrollPane) n).setVvalue(0.5));
            }, 700);
            next();
        }

        private void step(final String name, final Runnable action, final int waitMillis) {
            steps.add(new Step(name, action, waitMillis));
        }

        private void next() {
            final Step step = steps.poll();
            if (step == null) {
                done.countDown();
                return;
            }
            try {
                step.action().run();
            } catch (RuntimeException e) {
                fail(e);
                return;
            }
            final PauseTransition wait = new PauseTransition(Duration.millis(step.waitMillis()));
            wait.setOnFinished(e -> {
                try {
                    snapshot(step.name());
                    next();
                } catch (IOException | RuntimeException ex) {
                    fail(ex);
                }
            });
            wait.play();
        }

        private void fail(final Throwable t) {
            failure.set(t);
            done.countDown();
        }

        private void snapshot(final String name) throws IOException {
            final Scene scene = app.stage().getScene();
            final WritableImage image = scene.snapshot(null);
            final int w = (int) image.getWidth();
            final int h = (int) image.getHeight();
            final int[] pixels = new int[w * h];
            image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
            final BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            buffered.setRGB(0, 0, w, h, pixels, 0, w);
            final Path file = out.resolve(name + ".png");
            ImageIO.write(buffered, "png", file.toFile());
            OUT.println("wrote " + file);
        }
    }
}
