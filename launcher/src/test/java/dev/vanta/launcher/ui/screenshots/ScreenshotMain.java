package dev.vanta.launcher.ui.screenshots;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.modrinth.ContentType;
import dev.vanta.launcher.core.modrinth.ModrinthService;
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
import java.util.List;
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
        // A long data directory (as on Windows with a long account name) so the Settings row is checked with one.
        final Path data = Files.createTempDirectory("vanta-screenshots").resolve("Users").resolve("someone-with-a-long-account-name")
            .resolve("AppData").resolve("Roaming").resolve("VANTA Launcher");
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
        modrinth(backend);
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

    /**
     * Modrinth search results as Modrinth listed them for Minecraft 1.21.11 (sorted by downloads; recorded 2026-10-05)
     * and an instance with the performance pack installed (versions from the test fixtures).
     */
    private static void modrinth(final FakeBackend backend) {
        backend.searchPageSize = 20;
        backend.modrinthHits.put(ContentType.MOD, List.of(
            FakeBackend.hit("P7dR8mSH", "fabric-api", "Fabric API", "modmuss50", ContentType.MOD, 268_199_555L,
                "Lightweight and modular API providing common hooks and intercompatibility measures utilized by mods using the Fabric toolchain."),
            FakeBackend.hit("AANobbMI", "sodium", "Sodium", "jellysquid3", ContentType.MOD, 236_787_190L,
                "A high-performance rendering engine replacement for Minecraft, which greatly improves frame rates and reduces micro-stutter."),
            FakeBackend.hit("YL57xq9U", "iris", "Iris Shaders", "coderbot", ContentType.MOD, 184_174_507L,
                "A modern shader pack loader for Minecraft intended to be compatible with existing OptiFine shader packs"),
            FakeBackend.hit("NNAgCjsB", "entityculling", "Entity Culling", "tr7zw", ContentType.MOD, 174_814_150L,
                "Using async path-tracing to hide Block-/Entities that are not visible"),
            FakeBackend.hit("9s6osm5g", "cloth-config", "Cloth Config API", "shedaniel", ContentType.MOD, 174_186_525L,
                "Configuration Library for Minecraft Mods"),
            FakeBackend.hit("uXXizFIs", "ferrite-core", "FerriteCore", "malte0811", ContentType.MOD, 155_853_518L, "Memory usage optimizations"),
            FakeBackend.hit("mOgUt4GM", "modmenu", "Mod Menu", "Prospector", ContentType.MOD, 150_014_581L,
                "Adds a mod menu to view the list of mods you have installed."),
            FakeBackend.hit("gvQqBUqZ", "lithium", "Lithium", "jellysquid3", ContentType.MOD, 132_326_304L,
                "No-compromises game logic optimization mod, useful for both single-player games and multi-player servers.")));
        backend.modrinthHits.put(ContentType.SHADER, List.of(
            FakeBackend.hit("HVnmMxH1", "complementary-reimagined", "Complementary Shaders - Reimagined", "EminGT", ContentType.SHADER, 68_409_474L,
                "Preserving the elements of Minecraft with exceptional quality, detail, and performance."),
            FakeBackend.hit("R6NEzAwj", "complementary-unbound", "Complementary Shaders - Unbound", "EminGT", ContentType.SHADER, 44_523_363L,
                "Transforming the visuals of Minecraft with exceptional quality, detail, and performance."),
            FakeBackend.hit("Q1vvjJYV", "bsl-shaders", "BSL Shaders", "CaptTatsu", ContentType.SHADER, 30_055_865L,
                "Shaderpack for Minecraft: Java Edition. It's bright, colorful, and distinct."),
            FakeBackend.hit("lLqFfGNs", "photon-shader", "Photon Shaders", "sixthsurge", ContentType.SHADER, 27_640_765L,
                "A gameplay-focused shader pack with a semi-realistic style"),
            FakeBackend.hit("EpQFjzrQ", "solas-shader", "Solas Shader", "Septonious", ContentType.SHADER, 17_530_051L,
                "A modern fantasy stylized shaderpack with colored lighting and stunning visuals"),
            FakeBackend.hit("ZvMtQlho", "bliss-shader", "Bliss Shaders", "Xonk", ContentType.SHADER, 14_906_195L,
                "A well performing fantasy styled shaderpack with emphasis on scene variation and customization.")));
        backend.modrinthHits.put(ContentType.RESOURCE_PACK, List.of(
            FakeBackend.hit("50dA9Sha", "fresh-animations", "Fresh Animations", "FreshLX", ContentType.RESOURCE_PACK, 48_514_056L,
                "Make your game like the trailers! Dynamic animated entities to freshen your Minecraft experience."),
            FakeBackend.hit("yfDziwn1", "translations-for-sodium", "Translations for Sodium", "robotkoer", ContentType.RESOURCE_PACK, 21_727_879L,
                "Unofficial translations for the Sodium Minecraft mod"),
            FakeBackend.hit("uvpymuxq", "better-leaves", "Motschen's Better Leaves", "Motschen", ContentType.RESOURCE_PACK, 20_556_064L,
                "Improves the appearance of leaves with high mod compatibility and performance!")));
        final Path mods = backend.paths().modsDir();
        backend.content.add(new ModrinthService.InstalledContent(ContentType.MOD, "fabric-api-" + LauncherVersion.FABRIC_API + ".jar", "",
            mods.resolve("fabric-api-" + LauncherVersion.FABRIC_API + ".jar"), true, false, true, "", List.of(), false));
        backend.content.add(new ModrinthService.InstalledContent(ContentType.MOD, "vanta-client-1.0.0.jar", "",
            mods.resolve("vanta-client-1.0.0.jar"), true, false, true, "", List.of(), false));
        backend.content.add(pack(mods, "Sodium", "mc1.21.11-0.8.14-fabric", "sodium-fabric-0.8.14+mc1.21.11.jar", "AANobbMI", List.of("Iris Shaders")));
        backend.content.add(pack(mods, "Lithium", "mc1.21.11-0.21.4-fabric", "lithium-fabric-0.21.4+mc1.21.11.jar", "gvQqBUqZ", List.of()));
        backend.content.add(pack(mods, "FerriteCore", "8.2.0-fabric", "ferritecore-8.2.0-fabric.jar", "uXXizFIs", List.of()));
        backend.content.add(pack(mods, "ImmediatelyFast", "1.14.3+1.21.11-fabric", "ImmediatelyFast-Fabric-1.14.3+1.21.11.jar", "5ZwdcRci", List.of()));
        backend.content.add(pack(mods, "Entity Culling", "1.11.2", "entityculling-fabric-1.11.2-mc1.21.11.jar", "NNAgCjsB", List.of()));
        backend.content.add(pack(mods, "Iris Shaders", "1.10.8+1.21.11-fabric", "iris-fabric-1.10.8+mc1.21.11.jar", "YL57xq9U", List.of()));
    }

    private static ModrinthService.InstalledContent pack(final Path mods, final String title, final String version, final String file,
                                                         final String projectId, final List<String> requiredBy) {
        return new ModrinthService.InstalledContent(ContentType.MOD, title, version, mods.resolve(file), true, true, false, projectId, requiredBy, false);
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
            // A fresh launcher: no instance.json and no client jar, release 1.0.1 published. No update is offered for a
            // client that is not installed; the card offers the regular install and the official launcher instead.
            step("21-home-fresh-not-installed", () -> {
                backend.instance = null;
                backend.activeJar = null;
                backend.signInConfigured = true;
                backend.accounts.clear();
                backend.accounts.add(FakeBackend.microsoftAccount("NovaPlayer"));
                backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.0.1", true));
                ctx.navigation().navigate(NavigationModel.Page.HOME);
                ctx.session().refreshAll();
                ctx.updates().check(false);
            }, 900);
            step("22-home-fresh-no-sign-in", () -> {
                backend.signInConfigured = false;
                backend.accounts.clear();
                ctx.session().refreshAll();
            }, 900);
            step("23-home-fresh-no-sign-in-min-size", () -> {
                app.stage().setWidth(LauncherApp.MIN_WIDTH);
                app.stage().setHeight(LauncherApp.MIN_HEIGHT);
            }, 900);
            // Portable folder: the verified zip is shown in its folder with instructions; nothing is run.
            step("24-portable-update-verified", () -> {
                app.stage().setWidth(LauncherApp.DEFAULT_WIDTH);
                app.stage().setHeight(LauncherApp.DEFAULT_HEIGHT);
                backend.packaging = dev.vanta.launcher.core.util.LauncherPackaging.portable(Path.of("D:/Games/VANTA Launcher"));
                window.confirmInstaller(backend.paths().updatesCacheDir().resolve("1.0.1").resolve("VANTA-Launcher-1.0.1-windows-portable.zip"));
            }, 900);
            // A verified jar keeps its exact asset name (cache/updates/<version>/<asset name>); the dialog shows the path.
            step("25-jar-update-verified", () -> {
                window.dialogs().close();
                backend.packaging = dev.vanta.launcher.core.util.LauncherPackaging.PLAIN_JAR;
                window.confirmInstaller(backend.paths().updatesCacheDir().resolve("1.0.2").resolve("vanta-launcher-1.0.2-linux-all.jar"));
            }, 900);
            step("26-installer-verified", () -> {
                window.dialogs().close();
                window.confirmInstaller(backend.paths().updatesCacheDir().resolve("1.0.2").resolve("VANTA-Launcher-1.0.2.msi"));
            }, 900);
            // Fresh launcher without Microsoft sign-in: the Versions page points to "Use with Minecraft Launcher".
            step("27-versions-fresh-no-sign-in", () -> {
                window.dialogs().close();
                ctx.toasts().clear();
                backend.instance = null;
                backend.activeJar = null;
                backend.signInConfigured = false;
                backend.accounts.clear();
                ctx.session().refreshAll();
                ctx.navigation().navigate(NavigationModel.Page.VERSIONS);
            }, 900);
            step("28-versions-fresh", () -> {
                backend.signInConfigured = true;
                ctx.session().refreshAll();
            }, 900);
            step("29-home-fresh-signed-out", () -> ctx.navigation().navigate(NavigationModel.Page.HOME), 900);
            step("30-settings-advanced", () -> {
                ctx.navigation().navigate(NavigationModel.Page.SETTINGS);
                window.page(NavigationModel.Page.SETTINGS).lookupAll(".scroll-pane").forEach(n -> ((javafx.scene.control.ScrollPane) n).setVvalue(1.0));
            }, 900);
            step("31-settings-advanced-min-size", () -> {
                app.stage().setWidth(LauncherApp.MIN_WIDTH);
                app.stage().setHeight(LauncherApp.MIN_HEIGHT);
                window.page(NavigationModel.Page.SETTINGS).lookupAll(".scroll-pane").forEach(n -> ((javafx.scene.control.ScrollPane) n).setVvalue(1.0));
            }, 900);
            // A hand-placed 1.0.0 jar was copied before the update to 1.1.0 replaced it: listed as a local copy.
            step("32-versions-local-copy", () -> {
                app.stage().setWidth(LauncherApp.DEFAULT_WIDTH);
                app.stage().setHeight(LauncherApp.DEFAULT_HEIGHT);
                backend.instance = FakeBackend.installedInstance("1.1.0");
                backend.activeJar = backend.paths().modsDir().resolve("vanta-client-1.1.0.jar");
                backend.signInConfigured = true;
                backend.accounts.clear();
                backend.accounts.add(FakeBackend.microsoftAccount("NovaPlayer"));
                backend.kept.clear();
                backend.kept.add(new VantaClientService.KeptVersion("1.1.0", FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.1.0", true)
                    .manifest(), backend.activeJar, Instant.parse("2026-11-20T17:05:00Z")));
                backend.kept.add(new VantaClientService.KeptVersion("1.0.0", VantaClientService.localCopyManifest("1.0.0",
                    new ReleaseManifest.ReleaseFile("vanta-client-1.0.0.jar", "", 1L, backend.sha256)),
                    backend.paths().clientVersionsDir().resolve("1.0.0/vanta-client-1.0.0.jar"), Instant.parse("2026-11-20T17:05:00Z")));
                backend.kept.add(new VantaClientService.KeptVersion("0.9.2", FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "0.0.0", "0.9.2", true)
                    .manifest(), backend.paths().clientVersionsDir().resolve("0.9.2/vanta-client-0.9.2.jar"), Instant.parse("2026-09-12T09:15:00Z")));
                ctx.session().refreshAll();
                ctx.navigation().navigate(NavigationModel.Page.VERSIONS);
                ctx.versions().refresh();
            }, 900);
            step("33-versions-local-copy-min-size", () -> {
                app.stage().setWidth(LauncherApp.MIN_WIDTH);
                app.stage().setHeight(LauncherApp.MIN_HEIGHT);
                window.page(NavigationModel.Page.VERSIONS).lookupAll(".scroll-pane").forEach(n -> ((javafx.scene.control.ScrollPane) n).setVvalue(1.0));
            }, 900);
            // Mods page: Modrinth search (titles, authors, downloads and descriptions as Modrinth listed them for
            // Minecraft 1.21.11) and the installed performance pack.
            step("34-mods", () -> {
                app.stage().setWidth(LauncherApp.DEFAULT_WIDTH);
                app.stage().setHeight(LauncherApp.DEFAULT_HEIGHT);
                ctx.navigation().navigate(NavigationModel.Page.MODS);
                ctx.mods().search();
            }, 900);
            step("35-mods-shaders", () -> ctx.mods().tabProperty().set(ContentType.SHADER), 700);
            step("36-mods-resourcepacks", () -> ctx.mods().tabProperty().set(ContentType.RESOURCE_PACK), 700);
            step("37-mods-installed", () -> ctx.mods().tabProperty().set(ContentType.MOD), 500);
            step("38-mods-installed-scrolled", () -> window.page(NavigationModel.Page.MODS).lookupAll(".scroll-pane")
                .forEach(n -> ((javafx.scene.control.ScrollPane) n).setVvalue(1.0)), 700);
            step("39-mods-min-size", () -> {
                ctx.toasts().clear();
                app.stage().setWidth(LauncherApp.MIN_WIDTH);
                app.stage().setHeight(LauncherApp.MIN_HEIGHT);
                window.page(NavigationModel.Page.MODS).lookupAll(".scroll-pane").forEach(n -> ((javafx.scene.control.ScrollPane) n).setVvalue(0));
            }, 900);
            // No Microsoft sign-in: PLAY goes through the official Minecraft Launcher.
            step("40-home-play-via-official", () -> {
                app.stage().setWidth(LauncherApp.DEFAULT_WIDTH);
                app.stage().setHeight(LauncherApp.DEFAULT_HEIGHT);
                backend.signInConfigured = false;
                backend.accounts.clear();
                backend.officialProfileExists = false;
                ctx.session().refreshAll();
                ctx.navigation().navigate(NavigationModel.Page.HOME);
            }, 900);
            step("41-official-running", () -> {
                backend.runningLaunchers.add("MinecraftLauncher.exe (process 8124)");
                window.playViaOfficialLauncher();
            }, 900);
            step("42-official-confirm-pack", () -> {
                window.dialogs().close();
                backend.runningLaunchers.clear();
                window.playViaOfficialLauncher();
            }, 900);
            step("43-home-official-installing", () -> {
                window.dialogs().close();
                backend.officialGate = new CountDownLatch(1);
                ctx.home().installOfficialProfile(true);
            }, 900);
            step("44-home-official-opened", () -> backend.officialGate.countDown(), 1200);
            step("45-home-play-via-official-min-size", () -> {
                ctx.toasts().clear();
                app.stage().setWidth(LauncherApp.MIN_WIDTH);
                app.stage().setHeight(LauncherApp.MIN_HEIGHT);
            }, 900);
            step("46-settings-pack", () -> {
                app.stage().setWidth(LauncherApp.DEFAULT_WIDTH);
                app.stage().setHeight(LauncherApp.DEFAULT_HEIGHT);
                ctx.navigation().navigate(NavigationModel.Page.SETTINGS);
                window.page(NavigationModel.Page.SETTINGS).lookupAll(".scroll-pane").forEach(n -> ((javafx.scene.control.ScrollPane) n).setVvalue(0.15));
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
