package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.ui.LauncherApp;
import dev.vanta.launcher.ui.model.HomeViewModel;
import dev.vanta.launcher.ui.model.NavigationModel;
import dev.vanta.launcher.ui.testutil.FakeBackend;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Starts the real JavaFX application on the Monocle headless platform with a {@link FakeBackend}, visits every page
 * and dialog and checks that nothing throws. Runs inside {@code ./gradlew test} on every platform.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LauncherAppSmokeTest {

    static {
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        System.setProperty("prism.order", "sw");
        System.setProperty("java.awt.headless", "true");
        System.setProperty("javafx.animation.fullspeed", "true");
    }

    private static final List<LogRecord> SEVERE = new CopyOnWriteArrayList<>();
    private static final Handler CAPTURE = new Handler() {
        @Override
        public void publish(final LogRecord record) {
            if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                SEVERE.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    private static final List<LogRecord> CSS_WARNINGS = new CopyOnWriteArrayList<>();
    private static final Handler CSS_CAPTURE = new Handler() {
        @Override
        public void publish(final LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                CSS_WARNINGS.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    private LauncherApp app;
    private FakeBackend backend;
    private Path data;
    private boolean started;

    @BeforeAll
    void launch() throws Exception {
        // A long data directory, as on Windows with a long account name: the Settings row must still show "Open".
        data = Files.createTempDirectory("vanta-smoke").resolve("Users").resolve("someone-with-a-rather-long-account-name")
            .resolve("AppData").resolve("Roaming").resolve("VANTA Launcher with a long folder name");
        backend = new FakeBackend(data);
        backend.accounts.add(FakeBackend.microsoftAccount("Nova"));
        backend.javaInstalls.add(backend.temurin21());
        backend.instance = FakeBackend.installedInstance("1.0.0");
        backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.1.0", true));
        Logger.getLogger("VANTA").addHandler(CAPTURE);
        Logger.getLogger("javafx.css").addHandler(CSS_CAPTURE);
        Logger.getLogger("javafx.css").setLevel(Level.WARNING);
        final CountDownLatch ready = new CountDownLatch(1);
        final AtomicReference<LauncherApp> ref = new AtomicReference<>();
        final AtomicReference<Throwable> startFailure = new AtomicReference<>();
        LauncherApp.configureForTesting(() -> backend, a -> {
            ref.set(a);
            ready.countDown();
        });
        final Thread fx = new Thread(() -> {
            try {
                LauncherApp.launch(new String[0]);
            } catch (Throwable t) {
                startFailure.set(t);
                ready.countDown();
            }
        }, "javafx-smoke");
        fx.setDaemon(true);
        fx.start();
        assertTrue(ready.await(60, TimeUnit.SECONDS), "JavaFX did not start in time");
        if (startFailure.get() != null) {
            final String message = String.valueOf(startFailure.get());
            assumeTrue(false, "Headless JavaFX toolkit unavailable on this machine: " + message);
        }
        app = ref.get();
        started = app != null;
        assertTrue(started);
    }

    @AfterAll
    void shutdown() throws Exception {
        Logger.getLogger("VANTA").removeHandler(CAPTURE);
        Logger.getLogger("javafx.css").removeHandler(CSS_CAPTURE);
        if (app != null) {
            final CountDownLatch closed = new CountDownLatch(1);
            Platform.runLater(() -> {
                app.shutdown();
                closed.countDown();
            });
            closed.await(10, TimeUnit.SECONDS);
            assertTrue(backend.isClosed(), "shutdown closes the backend before exiting the platform");
        }
    }

    @Test
    void windowShowsHomeWithPlayAndFacts() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        fx(() -> {
            app.context().navigation().navigate(NavigationModel.Page.HOME);
            return null;
        });
        fx(() -> {
            assertEquals("VANTA Launcher", app.stage().getTitle());
            assertEquals(8, app.stage().getIcons().size(), "all icon sizes are registered");
            assertTrue(app.stage().getScene().getStylesheets().get(0).endsWith("vanta.css"));
            final Button play = (Button) app.window().lookup(".play-button");
            assertNotNull(play);
            assertFalse(play.isDisabled(), "signed in + Java 21 + installed → PLAY enabled");
            assertEquals(HomeViewModel.State.READY, app.context().home().state());
            final Label facts = (Label) app.window().lookup(".facts");
            assertEquals("Minecraft 1.21.11 · Fabric 0.19.5 · Java 21 (21.0.4)", facts.getText());
            return null;
        });
        waitUntil(() -> app.context().updates().checkedOnce());
        fx(() -> {
            assertTrue(app.context().updates().bannerVisibleProperty().get(), "client update banner is shown");
            assertTrue(app.window().lookup(".banner").isVisible());
            return null;
        });
    }

    @Test
    void everyPageRendersAndDialogsOpen() throws Exception {
        for (NavigationModel.Page page : NavigationModel.Page.values()) {
            fx(() -> {
                app.context().navigation().navigate(page);
                return null;
            });
            fx(() -> {
                final Node node = app.window().page(page);
                assertNotNull(node);
                assertTrue(node.getScene() != null, "page " + page + " is attached to the scene");
                return null;
            });
        }
        fx(() -> {
            app.window().showSignIn();
            assertTrue(app.window().dialogs().isOpen());
            return null;
        });
        waitUntil(() -> app.context().signIn().state() == dev.vanta.launcher.ui.model.SignInViewModel.State.WAITING);
        fx(() -> {
            final Label code = (Label) app.window().dialogs().lookup(".code-text");
            assertNotNull(code);
            assertEquals("QW7X 9K2P", code.getText());
            app.window().dialogs().close();
            assertFalse(app.window().dialogs().isOpen());
            app.window().showUpdates();
            assertTrue(app.window().dialogs().isOpen());
            app.window().dialogs().close();
            app.window().showAccounts();
            app.window().dialogs().close();
            app.context().toasts().success("Smoke", "toast");
            assertNotNull(app.window().lookup(".toast"));
            return null;
        });
        assertTrue(SEVERE.isEmpty(), "no severe UI log entries: " + SEVERE.stream().map(LogRecord::getMessage).toList());
        assertTrue(CSS_WARNINGS.isEmpty(), "stylesheet applied without warnings: " + CSS_WARNINGS.stream().map(LogRecord::getMessage).toList());
    }

    @Test
    void officialLauncherProfileIsConfirmedFirstAndOfferedWithoutSignIn() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        fx(() -> {
            app.context().navigation().navigate(NavigationModel.Page.HOME);
            return null;
        });
        fx(() -> {
            final Button official = (Button) app.window().lookup(".official-button");
            assertNotNull(official, "Home has the 'Use with Minecraft Launcher' button");
            assertEquals(app.context().t("home.official.button"), official.getText());
            app.window().showOfficialProfile();
            return null;
        });
        waitUntil(() -> app.window().dialogs().isOpen());
        // The file list sits in a ScrollPane, whose content joins the scene graph only once the pane's skin exists
        // (the next CSS/layout pulse), so wait for the listing instead of looking it up right after opening.
        waitUntil(() -> {
            final Node plan = app.window().dialogs().lookup(".official-plan");
            return plan != null && plan.lookupAll(".mono").stream().map(n -> ((Label) n).getText())
                .anyMatch(t -> t.endsWith("launcher_profiles.json"));
        });
        fx(() -> {
            final Node plan = app.window().dialogs().lookup(".official-plan");
            assertNotNull(plan, "the confirmation lists what is written");
            final boolean listsProfiles = plan.lookupAll(".mono").stream().map(n -> ((Label) n).getText())
                .anyMatch(t -> t.endsWith("launcher_profiles.json"));
            assertTrue(listsProfiles, "launcher_profiles.json is listed");
            app.window().dialogs().close();
            return null;
        });
        assertFalse(backend.calls.contains("installOfficialProfile"), "closing the dialog writes nothing");

        // Without a Microsoft client id the hero explains it and offers the official launcher as the way to play.
        final boolean wasConfigured = backend.signInConfigured;
        final var accounts = List.copyOf(backend.accounts);
        try {
            backend.signInConfigured = false;
            backend.accounts.clear();
            fx(() -> {
                app.context().session().refreshAll();
                return null;
            });
            waitUntil(() -> app.context().session().account().isEmpty() && !app.context().session().signInConfiguredProperty().get());
            fx(() -> {
                final Node callout = app.window().lookup(".official-callout");
                assertNotNull(callout);
                assertTrue(callout.isVisible(), "callout visible while sign-in is not configured");
                return null;
            });
        } finally {
            backend.signInConfigured = wasConfigured;
            backend.accounts.addAll(accounts);
            fx(() -> {
                app.context().session().refreshAll();
                return null;
            });
            waitUntil(() -> app.context().session().account().isPresent());
        }
        assertTrue(SEVERE.isEmpty(), "no severe UI log entries: " + SEVERE.stream().map(LogRecord::getMessage).toList());
    }

    @Test
    void websiteLinksAreActive() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        fx(() -> {
            final String website = app.context().t("nav.website");
            final Button sidebarLink = app.window().sidebar().lookupAll(".nav-item").stream()
                .filter(n -> n instanceof Button b && website.equals(b.getAccessibleText())).map(n -> (Button) n).findFirst().orElseThrow();
            assertFalse(sidebarLink.isDisabled(), "sidebar 'Website' opens https://vanta-client.netlify.app");
            app.context().navigation().navigate(NavigationModel.Page.ABOUT);
            return null;
        });
        waitUntil(() -> app.window().page(NavigationModel.Page.ABOUT).lookupAll(".button").stream()
            .anyMatch(n -> n instanceof Button b && app.context().t("about.links.website").equals(b.getText())));
        fx(() -> {
            final Button about = app.window().page(NavigationModel.Page.ABOUT).lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && app.context().t("about.links.website").equals(b.getText())).map(n -> (Button) n)
                .findFirst().orElseThrow();
            assertFalse(about.isDisabled(), "About 'Website' is active (no 'Not configured' suffix)");
            app.context().navigation().navigate(NavigationModel.Page.HOME);
            return null;
        });
    }

    @Test
    void freshLauncherClientCardOffersTheInstallNotAnUpdate() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        final var instance = backend.instance;
        try {
            backend.instance = null;
            backend.activeJar = null;
            fx(() -> {
                app.context().navigation().navigate(NavigationModel.Page.HOME);
                app.context().session().refreshAll();
                return null;
            });
            waitUntil(() -> app.context().session().installedClient().isEmpty() && !app.context().updates().checkingProperty().get());
            fx(() -> {
                app.context().updates().check(false);
                return null;
            });
            waitUntil(() -> app.context().updates().clientAvailabilityProperty().get()
                == dev.vanta.launcher.ui.model.UpdateViewModel.ClientAvailability.NOT_INSTALLED);
            fx(() -> {
                final Node card = app.window().lookup(".client-card");
                assertNotNull(card);
                assertNull(card.lookup(".client-update"), "no 'Update' for a client that is not installed");
                assertNotNull(card.lookup(".client-install"), "the regular install is offered");
                assertNotNull(card.lookup(".client-official"), "and 'Use with Minecraft Launcher'");
                assertEquals(app.context().t("client.card.notInstalled"), ((Label) card.lookup(".client-installed")).getText());
                final Node verify = app.window().page(NavigationModel.Page.HOME).lookup(".verify-button");
                assertNotNull(verify);
                assertFalse(verify.isVisible() || verify.isManaged(), "no 'Verify files' while nothing is installed (it started a full install)");
                assertNull(app.context().updates().clientUpdateProperty().get());
                assertFalse(app.context().updates().bannerVisibleProperty().get(), "no client update banner");
                app.window().showClientUpdate();
                assertFalse(app.window().dialogs().isOpen(), "no update dialog either");
                return null;
            });
        } finally {
            backend.instance = instance;
            fx(() -> {
                app.context().session().refreshAll();
                return null;
            });
            waitUntil(() -> app.context().session().installedClient().isPresent() && !app.context().updates().checkingProperty().get());
            fx(() -> {
                app.context().updates().check(false);
                return null;
            });
            waitUntil(() -> app.context().updates().clientUpdateProperty().get() != null);
            fx(() -> {
                assertTrue(app.window().page(NavigationModel.Page.HOME).lookup(".verify-button").isVisible(), "an installation can be verified");
                return null;
            });
        }
        assertTrue(SEVERE.isEmpty(), "no severe UI log entries: " + SEVERE.stream().map(LogRecord::getMessage).toList());
    }

    @Test
    void dataDirectoryRowKeepsTheOpenButtonReadable() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        final double width = fx(() -> app.stage().getWidth());
        try {
            fx(() -> {
                app.stage().setWidth(LauncherApp.MIN_WIDTH);
                app.context().navigation().navigate(NavigationModel.Page.SETTINGS);
                return null;
            });
            waitUntil(() -> {
                final Node open = app.window().page(NavigationModel.Page.SETTINGS).lookup(".data-dir-open");
                return open != null && open.getScene() != null && ((Button) open).getWidth() > 0;
            });
            fx(() -> {
                final Node page = app.window().page(NavigationModel.Page.SETTINGS);
                final Button open = (Button) page.lookup(".data-dir-open");
                assertEquals(app.context().t("settings.dataDir.open"), open.getText());
                assertTrue(open.getWidth() >= open.prefWidth(-1) - 0.5, "'Open' keeps its label (1.0.1 showed '...'): width "
                    + open.getWidth() + " < pref " + open.prefWidth(-1));
                final Label path = (Label) page.lookup(".data-dir-path");
                assertEquals(backend.paths().dataDir().toString(), path.getText());
                assertEquals(backend.paths().dataDir().toString(), path.getTooltip().getText(), "the full path is in the tooltip");
                assertTrue(path.getWidth() < path.prefWidth(-1), "the long path is what shrinks");
                return null;
            });
        } finally {
            fx(() -> {
                app.stage().setWidth(width);
                app.context().navigation().navigate(NavigationModel.Page.HOME);
                return null;
            });
        }
    }

    @Test
    void sidebarSaysBrieflyWhenSignInIsNotAvailable() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        final boolean wasConfigured = backend.signInConfigured;
        final var accounts = List.copyOf(backend.accounts);
        try {
            backend.signInConfigured = false;
            backend.accounts.clear();
            fx(() -> {
                app.context().session().refreshAll();
                return null;
            });
            waitUntil(() -> app.context().session().account().isEmpty() && !app.context().session().signInConfiguredProperty().get());
            fx(() -> {
                final Label sub = app.window().sidebar().accountSubLabel();
                assertEquals(app.context().t("account.signedOut.sub.notConfigured"), sub.getText(), "not 'Sign in with Microsoft to play'");
                assertEquals(app.context().t("account.signedOut.sub.notConfigured.tooltip"), sub.getTooltip().getText());
                // No primary "Sign in" right under "Sign-in not available": the Home account card's "How to configure".
                final Button button = app.window().sidebar().signedOutButton();
                assertEquals(app.context().t("account.card.howToConfigure"), button.getText());
                assertTrue(button.getStyleClass().contains("secondary"), button.getStyleClass().toString());
                assertFalse(button.getStyleClass().contains("primary"), button.getStyleClass().toString());
                return null;
            });
            backend.signInConfigured = true;
            fx(() -> {
                app.context().session().refreshAll();
                return null;
            });
            waitUntil(() -> app.context().session().signInConfiguredProperty().get());
            fx(() -> {
                final Label sub = app.window().sidebar().accountSubLabel();
                assertEquals(app.context().t("account.signedOut.sub"), sub.getText());
                assertEquals(app.context().t("account.signedOut.sub.tooltip"), sub.getTooltip().getText());
                final Button button = app.window().sidebar().signedOutButton();
                assertEquals(app.context().t("account.signIn"), button.getText());
                assertTrue(button.getStyleClass().contains("primary"), button.getStyleClass().toString());
                return null;
            });
        } finally {
            backend.signInConfigured = wasConfigured;
            backend.accounts.addAll(accounts);
            fx(() -> {
                app.context().session().refreshAll();
                return null;
            });
            waitUntil(() -> app.context().session().account().isPresent());
        }
    }

    @Test
    void modsPageSearchesAndInstalls() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        backend.modrinthHits.put(dev.vanta.launcher.core.modrinth.ContentType.MOD, List.of(
            FakeBackend.hit("AANobbMI", "sodium", "Sodium", "jellysquid3", dev.vanta.launcher.core.modrinth.ContentType.MOD, 236_787_190L,
                "The fastest and most compatible rendering optimization mod for Minecraft.")));
        try {
            fx(() -> {
                app.context().navigation().navigate(NavigationModel.Page.MODS);
                app.context().mods().search();
                return null;
            });
            waitUntil(() -> app.window().page(NavigationModel.Page.MODS).lookup(".mods-install") != null);
            fx(() -> {
                final Node page = app.window().page(NavigationModel.Page.MODS);
                assertEquals("Sodium", ((Label) page.lookup(".mods-result-title")).getText());
                ((Button) page.lookup(".mods-install")).fire();
                return null;
            });
            waitUntil(() -> app.window().page(NavigationModel.Page.MODS).lookup(".mods-installed-row") != null);
            fx(() -> {
                final Node page = app.window().page(NavigationModel.Page.MODS);
                assertTrue(backend.calls.contains("installFromModrinth:AANobbMI"));
                assertNull(page.lookup(".mods-install"), "an installed project is not offered again");
                assertTrue(page.lookup(".mods-restart-hint").isVisible(), "says the game picks it up on its next start");
                return null;
            });
        } finally {
            backend.modrinthHits.clear();
            backend.content.clear();
            fx(() -> {
                app.context().navigation().navigate(NavigationModel.Page.HOME);
                return null;
            });
        }
        assertTrue(SEVERE.isEmpty(), "no severe UI log entries: " + SEVERE.stream().map(LogRecord::getMessage).toList());
    }

    /**
     * User report: in a small (not maximised) window the Mods page's Install buttons do nothing. The page is opened at
     * 900 x 560 (below the 960 x 600 minimum; the window is clamped to whatever the platform allows and the real size is
     * used), the first and the last Install button must lie inside the scene (the last one after scrolling the page),
     * the node under the cursor at each button's centre must be that button (nothing transparent may cover it), and a
     * click there must reach the view model, i.e. the backend must be asked to install the project.
     */
    @Test
    void modsInstallButtonsAreReachableInASmallWindow() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        final dev.vanta.launcher.core.modrinth.ContentType mod = dev.vanta.launcher.core.modrinth.ContentType.MOD;
        final int pageSize = backend.searchPageSize;
        backend.searchPageSize = 20;
        backend.modrinthHits.put(mod, List.of(
            FakeBackend.hit("AANobbMI", "sodium", "Sodium", "jellysquid3", mod, 236_787_190L,
                "A high-performance rendering engine replacement for Minecraft, which greatly improves frame rates and reduces micro-stutter."),
            FakeBackend.hit("gvQqBUqZ", "lithium", "Lithium", "jellysquid3", mod, 132_326_304L,
                "No-compromises game logic optimization mod, useful for both single-player games and multi-player servers."),
            FakeBackend.hit("NNAgCjsB", "entityculling", "Entity Culling", "tr7zw", mod, 174_814_150L,
                "Using async path-tracing to hide Block-/Entities that are not visible"),
            FakeBackend.hit("uXXizFIs", "ferrite-core", "FerriteCore", "malte0811", mod, 155_853_518L, "Memory usage optimizations"),
            FakeBackend.hit("mOgUt4GM", "modmenu", "Mod Menu", "Prospector", mod, 150_014_581L,
                "Adds a mod menu to view the list of mods you have installed."),
            FakeBackend.hit("YL57xq9U", "iris", "Iris Shaders", "coderbot", mod, 184_174_507L,
                "A modern shader pack loader for Minecraft intended to be compatible with existing OptiFine shader packs")));
        final double width = fx(() -> app.stage().getWidth());
        final double height = fx(() -> app.stage().getHeight());
        final boolean maximized = fx(() -> app.stage().isMaximized());
        try {
            fx(() -> {
                app.stage().setMaximized(false);
                app.stage().setWidth(900);
                app.stage().setHeight(560);
                app.context().navigation().navigate(NavigationModel.Page.MODS);
                app.context().mods().search();
                return null;
            });
            waitUntil(() -> {
                final List<Button> installs = installButtons();
                return installs.size() == 6 && installs.stream().allMatch(b -> b.getWidth() > 0 && b.getScene() != null);
            });
            final javafx.scene.Scene scene = app.stage().getScene();
            final String first = fx(() -> {
                final double w = scene.getWidth();
                final double h = scene.getHeight();
                assertTrue(w <= 960.5 && h <= 600.5, "the window really is small (" + w + " x " + h + "), not the 1120 x 720 default");
                final Button button = installButtons().get(0);
                final javafx.geometry.Bounds bounds = button.localToScene(button.getBoundsInLocal());
                assertWithinScene(bounds, w, h, "first Install button");
                assertCovers(button, scene);
                click(button);
                return button.getTooltip().getText();
            });
            waitUntil(() -> backend.calls.contains("installFromModrinth:AANobbMI"));
            assertTrue(first.contains("Sodium"), first);
            System.out.println("modsInstallButtonsAreReachableInASmallWindow: clicked with " + CLICKED_WITH.get());
            // Sodium is installed now: the rows are rebuilt with five Install buttons; wait until they are laid out.
            waitUntil(() -> {
                final List<Button> installs = installButtons();
                return installs.size() == 5 && installs.stream().allMatch(b -> b.getWidth() > 0 && b.getScene() != null);
            });

            // The install shows a toast ("Sodium installed") in the bottom-right corner.
            waitUntil(() -> app.window().lookup(".toast") != null);

            // The last row is below the viewport in a 600 px window: the page scrolls, and after scrolling it is reachable.
            fx(() -> {
                final javafx.scene.control.ScrollPane scroll = (javafx.scene.control.ScrollPane) app.window().page(NavigationModel.Page.MODS);
                assertTrue(scroll.getContent().getLayoutBounds().getHeight() > scroll.getViewportBounds().getHeight(),
                    "the Mods page is taller than the viewport and scrolls (content " + scroll.getContent().getLayoutBounds().getHeight()
                        + " px, viewport " + scroll.getViewportBounds().getHeight() + " px)");
                final Button last = installButtons().get(installButtons().size() - 1);
                final javafx.geometry.Bounds before = last.localToScene(last.getBoundsInLocal());
                assertTrue(before.getMaxY() > scene.getHeight(), "before scrolling the last Install button is below the window: " + before);
                scroll.setVvalue(1.0);
                return null;
            });
            waitUntil(() -> {
                final Button last = installButtons().get(installButtons().size() - 1);
                return last.localToScene(last.getBoundsInLocal()).getMaxY() <= scene.getHeight();
            });
            // In this small window the toast lies over the lower Install buttons (in a maximised window the Install
            // column is nowhere near that corner). The click must not vanish into the toast: it dismisses it, and the
            // next click reaches the button. Whether the toast covers this particular button depends on the fonts, so
            // the covered case is handled when it occurs and reported.
            final boolean covered = fx(() -> {
                final Button last = installButtons().get(installButtons().size() - 1);
                final javafx.geometry.Bounds bounds = last.localToScene(last.getBoundsInLocal());
                assertWithinScene(bounds, scene.getWidth(), scene.getHeight(), "last Install button after scrolling");
                final Node under = pickAt(scene.getRoot(), bounds.getCenterX(), bounds.getCenterY());
                final boolean toastCovers = under != null && ancestorWithStyle(under, "toast") != null;
                System.out.println("modsInstallButtonsAreReachableInASmallWindow: at " + (int) scene.getWidth() + " x " + (int) scene.getHeight()
                    + " the toast " + (toastCovers ? "covers" : "does not cover") + " the last Install button " + bounds);
                if (toastCovers) {
                    click(last);
                }
                return toastCovers;
            });
            if (covered) {
                waitUntil(() -> app.window().lookup(".toast") == null);
                assertFalse(backend.calls.contains("installFromModrinth:YL57xq9U"), "the click on the toast dismissed it and did nothing else");
            }
            fx(() -> {
                final Button last = installButtons().get(installButtons().size() - 1);
                assertCovers(last, scene);
                click(last);
                return null;
            });
            waitUntil(() -> backend.calls.contains("installFromModrinth:YL57xq9U"));
        } finally {
            backend.searchPageSize = pageSize;
            backend.modrinthHits.clear();
            backend.content.clear();
            fx(() -> {
                // The view model caches what is installed; read the (now empty) list again so later tests start clean.
                app.context().mods().refreshInstalled();
                app.stage().setWidth(width);
                app.stage().setHeight(height);
                app.stage().setMaximized(maximized);
                app.context().navigation().navigate(NavigationModel.Page.HOME);
                return null;
            });
            waitUntil(() -> app.context().mods().installed().isEmpty());
        }
        assertTrue(SEVERE.isEmpty(), "no severe UI log entries: " + SEVERE.stream().map(LogRecord::getMessage).toList());
    }

    /**
     * A toast is a notification, not a wall: it sits over whatever is in the bottom-right corner (in a 960 x 600 window
     * that includes controls), so a click on it dismisses it instead of being swallowed.
     */
    @Test
    void aClickOnAToastDismissesIt() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        clearToasts();
        fx(() -> {
            app.context().toasts().success("Smoke", "a click anywhere dismisses this");
            return null;
        });
        waitUntil(() -> {
            final Node toast = app.window().lookup(".toast");
            return toast != null && toast.getScene() != null && toast.getLayoutBounds().getWidth() > 0;
        });
        fx(() -> {
            final Node toast = app.window().lookup(".toast");
            final javafx.geometry.Bounds bounds = toast.localToScene(toast.getBoundsInLocal());
            final Node under = pickAt(app.stage().getScene().getRoot(), bounds.getCenterX(), bounds.getCenterY());
            assertNotNull(under);
            assertEquals(toast, ancestorWithStyle(under, "toast"), "the toast is what the mouse hits at its centre");
            click(toast);
            return null;
        });
        waitUntil(() -> app.context().toasts().toasts().isEmpty());
        waitUntil(() -> app.window().lookup(".toast") == null);
    }

    /**
     * A dismissed toast fades out for {@link Motion#FAST}; it stays in the scene graph meanwhile. A click within that
     * time (the second click of a double-click on the control the toast covered) must reach what lies under the card,
     * not vanish into the fading toast.
     */
    @Test
    void aDismissedToastDoesNotSwallowClicksWhileItFadesOut() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        clearToasts();
        fx(() -> {
            app.context().toasts().success("Smoke", "dismissed, then clicked again at once");
            return null;
        });
        waitUntil(() -> {
            final Node toast = app.window().lookup(".toast");
            return toast != null && toast.getScene() != null && toast.getLayoutBounds().getWidth() > 0 && toast.getOpacity() == 1;
        });
        final javafx.scene.Scene scene = app.stage().getScene();
        // Every mouse event the click produces is recorded and consumed in the capturing phase: the test sees where it
        // was aimed, and whatever lies under the toast (a button, a switch) does not act on it.
        final List<javafx.event.EventTarget> targets = new CopyOnWriteArrayList<>();
        final javafx.event.EventHandler<javafx.scene.input.MouseEvent> record = e -> {
            targets.add(e.getTarget());
            e.consume();
        };
        try {
            fx(() -> {
                scene.addEventFilter(javafx.scene.input.MouseEvent.ANY, record);
                final Node toast = app.window().lookup(".toast");
                final javafx.geometry.Bounds bounds = toast.localToScene(toast.getBoundsInLocal());
                assertEquals(toast, ancestorWithStyle(pickAt(scene.getRoot(), bounds.getCenterX(), bounds.getCenterY()), "toast"));
                app.context().toasts().clear();
                if (!Motion.reduced()) {
                    assertNotNull(toast.getScene(), "the dismissed toast is still in the scene graph while it fades out");
                }
                final Node under = pickAt(scene.getRoot(), bounds.getCenterX(), bounds.getCenterY());
                assertTrue(under == null || ancestorWithStyle(under, "toast") == null,
                    "right after the dismiss the toast is transparent for the mouse; picked " + under);
                click(toast);
                return null;
            });
            waitUntil(() -> targets.stream().anyMatch(t -> t instanceof Node));
            for (javafx.event.EventTarget target : targets) {
                if (target instanceof Node node) {
                    assertNull(ancestorWithStyle(node, "toast"), "the click right after the dismiss reached " + node
                        + ", not the fading toast");
                }
            }
        } finally {
            fx(() -> {
                scene.removeEventFilter(javafx.scene.input.MouseEvent.ANY, record);
                return null;
            });
        }
        waitUntil(() -> app.window().lookup(".toast") == null);
    }

    /**
     * A toast must not lie over controls. In a small window (960 x 600) the bottom-right corner holds the Mods page's
     * lower Install buttons and the installed pane, so there the toasts sit top-right, below the update banner and the
     * page header, and a card is at most 30 percent of the window wide (it never reaches the Install column): no Install
     * button, no header action and no banner button intersects the toast card. In a wide window (1200 x 700 here) the
     * Mods page still scrolls, so the toasts stay top-right and clear of the Install buttons; on a page that fits the
     * window (About) they lie bottom-right at their full 360 px.
     */
    @Test
    void toastsKeepClearOfTheInstallButtonsInASmallWindow() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        waitUntil(() -> app.context().updates().checkedOnce());
        final dev.vanta.launcher.core.modrinth.ContentType mod = dev.vanta.launcher.core.modrinth.ContentType.MOD;
        final int pageSize = backend.searchPageSize;
        backend.searchPageSize = 20;
        backend.modrinthHits.put(mod, List.of(
            FakeBackend.hit("AANobbMI", "sodium", "Sodium", "jellysquid3", mod, 236_787_190L,
                "A high-performance rendering engine replacement for Minecraft, which greatly improves frame rates and reduces micro-stutter."),
            FakeBackend.hit("gvQqBUqZ", "lithium", "Lithium", "jellysquid3", mod, 132_326_304L,
                "No-compromises game logic optimization mod, useful for both single-player games and multi-player servers."),
            FakeBackend.hit("NNAgCjsB", "entityculling", "Entity Culling", "tr7zw", mod, 174_814_150L,
                "Using async path-tracing to hide Block-/Entities that are not visible"),
            FakeBackend.hit("uXXizFIs", "ferrite-core", "FerriteCore", "malte0811", mod, 155_853_518L, "Memory usage optimizations"),
            FakeBackend.hit("mOgUt4GM", "modmenu", "Mod Menu", "Prospector", mod, 150_014_581L,
                "Adds a mod menu to view the list of mods you have installed."),
            FakeBackend.hit("YL57xq9U", "iris", "Iris Shaders", "coderbot", mod, 184_174_507L,
                "A modern shader pack loader for Minecraft intended to be compatible with existing OptiFine shader packs")));
        final double width = fx(() -> app.stage().getWidth());
        final double height = fx(() -> app.stage().getHeight());
        final boolean maximized = fx(() -> app.stage().isMaximized());
        try {
            fx(() -> {
                app.context().toasts().clear();
                app.stage().setMaximized(false);
                app.stage().setWidth(960);
                app.stage().setHeight(600);
                app.context().navigation().navigate(NavigationModel.Page.MODS);
                app.context().mods().search();
                return null;
            });
            waitUntil(() -> {
                final List<Button> installs = installButtons();
                return installs.size() == 6 && installs.stream().allMatch(b -> b.getWidth() > 0 && b.getScene() != null);
            });
            waitUntil(() -> app.window().lookup(".toast") == null);
            fx(() -> {
                app.context().toasts().success("Sodium installed", "It loads the next time the game starts.");
                return null;
            });
            final javafx.scene.Scene scene = app.stage().getScene();
            final Node banner = app.window().lookup(".banner");
            final Node header = app.window().page(NavigationModel.Page.MODS).lookup(".page-header");
            assertNotNull(banner);
            assertNotNull(header);
            // The toast is placed below the banner and header in the layout pass after it appeared, and it slides in
            // (Motion.enter moves it 10 px); wait for both.
            waitUntil(() -> {
                final Node toast = app.window().lookup(".toast");
                return toast != null && toast.getScene() != null && toast.getLayoutBounds().getWidth() > 0 && toast.getTranslateY() == 0
                    && sceneBounds(toast).getMinY() >= sceneBounds(header).getMaxY();
            });
            fx(() -> {
                final double w = scene.getWidth();
                final double h = scene.getHeight();
                assertTrue(w <= 960.5 && h <= 600.5, "the window really is small (" + w + " x " + h + ")");
                assertTrue(ToastLayer.anchorsTop(w, false) && app.window().toasts().isAnchoredTop(), "below 1100 px the toasts are anchored top-right");
                final Node toast = app.window().lookup(".toast");
                final javafx.geometry.Bounds card = sceneBounds(toast);
                assertWithinScene(card, w, h, "toast card");
                assertEquals(ToastLayer.cardWidth(w), card.getWidth(), 1.0, "a card is min(360, 30 percent of the window) wide: " + card);
                assertTrue(card.getWidth() <= w * 0.3 + 1, "the card takes at most 30 percent of the window: " + card);
                assertTrue(card.getMaxY() < h / 2, "the toast lies in the upper half of the window: " + card);
                if (banner.isVisible()) {
                    assertFalse(card.intersects(sceneBounds(banner)), "the toast lies below the update banner: " + card + " vs " + sceneBounds(banner));
                }
                assertFalse(card.intersects(sceneBounds(header)), "the toast lies below the page header: " + card + " vs " + sceneBounds(header));
                final List<Button> installs = installButtons();
                assertEquals(6, installs.size());
                for (Button install : installs) {
                    final javafx.geometry.Bounds button = sceneBounds(install);
                    assertFalse(card.intersects(button), "the toast " + card + " does not cover the Install button of "
                        + install.getTooltip().getText() + " at " + button);
                }
                assertFalse(card.intersects(sceneBounds(app.window().sidebar())), "the toast is not over the sidebar: " + card);
                return null;
            });
            // The same toast in a wide window: the Mods page with six results still scrolls at 1200 x 700, so the toast
            // stays top-right (the bottom-right corner would hold the installed pane) and clear of the Install buttons.
            fx(() -> {
                app.stage().setWidth(1200);
                app.stage().setHeight(700);
                return null;
            });
            waitUntil(() -> {
                final Node toast = app.window().lookup(".toast");
                return toast != null && scene.getWidth() >= 1199 && scene.getHeight() >= 699 && toast.getTranslateY() == 0
                    && sceneBounds(toast).getMinY() >= sceneBounds(header).getMaxY();
            });
            fx(() -> {
                final double w = scene.getWidth();
                final double h = scene.getHeight();
                assertFalse(ToastLayer.anchorsTop(w, false), "from 1100 px on the window width alone no longer anchors the toasts top-right");
                assertTrue(app.window().pageScrolls(), "the Mods page with six results scrolls at " + w + " x " + h);
                assertTrue(app.window().toasts().isAnchoredTop(), "a scrolling page keeps the toasts top-right");
                final javafx.geometry.Bounds card = sceneBounds(app.window().lookup(".toast"));
                assertWithinScene(card, w, h, "toast card");
                assertEquals(ToastLayer.MAX_WIDTH, card.getWidth(), 1.0, "a card is 360 px wide in a wide window: " + card);
                assertTrue(card.getMaxY() < h / 2, "the toast lies in the upper half of the window: " + card);
                for (Button install : installButtons()) {
                    assertFalse(card.intersects(sceneBounds(install)), "the toast " + card + " does not cover the Install button of "
                        + install.getTooltip().getText() + " at " + sceneBounds(install));
                }
                return null;
            });
            // On a page that fits the window the toasts lie bottom-right, flush with the 24 px edges, at full width.
            fx(() -> {
                app.context().navigation().navigate(NavigationModel.Page.ABOUT);
                return null;
            });
            waitUntil(() -> {
                final Node toast = app.window().lookup(".toast");
                return toast != null && !app.window().pageScrolls() && toast.getTranslateY() == 0
                    && sceneBounds(toast).getMaxY() > scene.getHeight() / 2;
            });
            fx(() -> {
                final double w = scene.getWidth();
                final double h = scene.getHeight();
                assertFalse(app.window().toasts().isAnchoredTop(), "on a page that fits the window the toasts are anchored bottom-right");
                final javafx.geometry.Bounds card = sceneBounds(app.window().lookup(".toast"));
                assertWithinScene(card, w, h, "toast card");
                assertEquals(ToastLayer.MAX_WIDTH, card.getWidth(), 1.0, "a card is 360 px wide in a wide window: " + card);
                assertEquals(h - ToastLayer.EDGE, card.getMaxY(), 1.0, "the toast sits at the bottom edge: " + card);
                assertEquals(w - ToastLayer.EDGE, card.getMaxX(), 1.0, "the toast sits at the right edge: " + card);
                return null;
            });
        } finally {
            backend.searchPageSize = pageSize;
            backend.modrinthHits.clear();
            fx(() -> {
                app.context().toasts().clear();
                app.stage().setWidth(width);
                app.stage().setHeight(height);
                app.stage().setMaximized(maximized);
                app.context().navigation().navigate(NavigationModel.Page.HOME);
                return null;
            });
        }
        assertTrue(SEVERE.isEmpty(), "no severe UI log entries: " + SEVERE.stream().map(LogRecord::getMessage).toList());
    }

    /**
     * The installed pane's switches and remove buttons are controls too, and they sit in the bottom-right corner of every
     * window in which the Mods page scrolls, however wide it is: with six installed mods at the 1120 x 720 default size
     * and at 1100 x 600 (just above the 1100 px width threshold) the lowest rows lie under a bottom-right toast. There the
     * toasts have to sit top-right as well, so no switch, no remove button and no Install button is under the card; on a
     * page that fits its window (About at 1200 x 700) the toasts stay bottom-right.
     */
    @Test
    void toastsKeepClearOfTheInstalledSwitchesWhenTheModsPageScrolls() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        waitUntil(() -> app.context().updates().checkedOnce());
        final dev.vanta.launcher.core.modrinth.ContentType mod = dev.vanta.launcher.core.modrinth.ContentType.MOD;
        final int pageSize = backend.searchPageSize;
        backend.searchPageSize = 20;
        backend.modrinthHits.put(mod, List.of(
            FakeBackend.hit("P7dR8mSH", "fabric-api", "Fabric API", "modmuss50", mod, 268_200_000L, "Lightweight and modular API"),
            FakeBackend.hit("mOgUt4GM", "modmenu", "Mod Menu", "Prospector", mod, 150_014_581L, "Adds a mod menu to view the list of mods you have installed."),
            FakeBackend.hit("9s6osm5g", "cloth-config", "Cloth Config API", "shedaniel", mod, 174_200_000L, "Configuration Library for Minecraft Mods"),
            FakeBackend.hit("1IjD5062", "continuity", "Continuity", "PepperCode1", mod, 90_000_000L, "Connected textures"),
            FakeBackend.hit("Orvt0mRa", "indium", "Indium", "comp500", mod, 80_000_000L, "Sodium addon for the Fabric Rendering API"),
            FakeBackend.hit("YL57xq9U", "iris", "Iris Shaders", "coderbot", mod, 184_174_507L, "A modern shader pack loader")));
        final Path mods = backend.paths().modsDir();
        backend.content.add(new dev.vanta.launcher.core.modrinth.ModrinthService.InstalledContent(mod, "fabric-api.jar", "", mods.resolve("fabric-api.jar"),
            true, false, true, "", List.of(), false));
        backend.content.add(installedMod(mods, "Sodium", "mc1.21.11-0.8.14-fabric", "sodium.jar", "AANobbMI"));
        backend.content.add(installedMod(mods, "Lithium", "mc1.21.11-0.21.4-fabric", "lithium.jar", "gvQqBUqZ"));
        backend.content.add(installedMod(mods, "FerriteCore", "8.2.0-fabric", "ferritecore.jar", "uXXizFIs"));
        backend.content.add(installedMod(mods, "ImmediatelyFast", "1.14.3+1.21.11-fabric", "immediatelyfast.jar", "5ZwdcRci"));
        backend.content.add(installedMod(mods, "Entity Culling", "1.11.2", "entityculling.jar", "NNAgCjsB"));
        backend.content.add(installedMod(mods, "Iris Shaders", "1.10.8+1.21.11-fabric", "iris.jar", "YL57xq9U"));
        final double width = fx(() -> app.stage().getWidth());
        final double height = fx(() -> app.stage().getHeight());
        final boolean maximized = fx(() -> app.stage().isMaximized());
        final javafx.scene.Scene scene = app.stage().getScene();
        try {
            fx(() -> {
                app.context().toasts().clear();
                app.stage().setMaximized(false);
                app.context().navigation().navigate(NavigationModel.Page.MODS);
                app.context().mods().refreshInstalled();
                app.context().mods().search();
                return null;
            });
            // Fabric API is included and Iris installed, so four of the six results offer Install; six installed rows carry a switch.
            waitUntil(() -> installButtons().size() == 4 && switches().size() == 6
                && switches().stream().allMatch(n -> n.getScene() != null && n.getLayoutBounds().getWidth() > 0));
            for (double[] size : new double[][] {{1120, 720}, {1100, 600}}) {
                final String at = " at " + (int) size[0] + " x " + (int) size[1];
                fx(() -> {
                    app.context().toasts().clear();
                    app.stage().setWidth(size[0]);
                    app.stage().setHeight(size[1]);
                    return null;
                });
                waitUntil(() -> Math.abs(scene.getWidth() - size[0]) < 1 && Math.abs(scene.getHeight() - size[1]) < 1
                    && app.window().lookup(".toast") == null);
                fx(() -> {
                    final javafx.scene.control.ScrollPane scroll = (javafx.scene.control.ScrollPane) app.window().page(NavigationModel.Page.MODS);
                    assertTrue(scroll.getContent().getLayoutBounds().getHeight() > scroll.getViewportBounds().getHeight(), "the Mods page scrolls" + at);
                    app.context().toasts().success("Sodium installed", "It loads the next time the game starts.");
                    return null;
                });
                waitUntil(() -> {
                    final Node toast = app.window().lookup(".toast");
                    return toast != null && toast.getScene() != null && toast.getLayoutBounds().getWidth() > 0 && toast.getTranslateY() == 0;
                });
                fx(() -> {
                    final javafx.geometry.Bounds card = sceneBounds(app.window().lookup(".toast"));
                    assertWithinScene(card, scene.getWidth(), scene.getHeight(), "toast card" + at);
                    for (Node control : controls()) {
                        final javafx.geometry.Bounds bounds = sceneBounds(control);
                        assertFalse(card.intersects(bounds), "the toast " + card + at + " does not cover the " + control.getStyleClass()
                            + " (" + control.getAccessibleText() + ") at " + bounds);
                    }
                    return null;
                });
            }
            // A page that fits its window keeps the toasts bottom-right: About at 1200 x 700.
            fx(() -> {
                app.context().toasts().clear();
                app.context().navigation().navigate(NavigationModel.Page.ABOUT);
                app.stage().setWidth(1200);
                app.stage().setHeight(700);
                return null;
            });
            waitUntil(() -> Math.abs(scene.getWidth() - 1200) < 1 && Math.abs(scene.getHeight() - 700) < 1 && app.window().lookup(".toast") == null
                && !app.window().pageScrolls());
            fx(() -> {
                app.context().toasts().success("Sodium installed", "It loads the next time the game starts.");
                return null;
            });
            waitUntil(() -> {
                final Node toast = app.window().lookup(".toast");
                return toast != null && toast.getScene() != null && toast.getTranslateY() == 0 && sceneBounds(toast).getMaxY() > scene.getHeight() / 2;
            });
            fx(() -> {
                final javafx.geometry.Bounds card = sceneBounds(app.window().lookup(".toast"));
                assertFalse(app.window().toasts().isAnchoredTop(), "on a page that fits the window the toasts lie bottom-right");
                assertEquals(scene.getHeight() - ToastLayer.EDGE, card.getMaxY(), 1.0, "the toast sits at the bottom edge: " + card);
                assertEquals(scene.getWidth() - ToastLayer.EDGE, card.getMaxX(), 1.0, "the toast sits at the right edge: " + card);
                return null;
            });
        } finally {
            backend.searchPageSize = pageSize;
            backend.modrinthHits.clear();
            backend.content.clear();
            fx(() -> {
                app.context().toasts().clear();
                app.context().mods().refreshInstalled();
                app.stage().setWidth(width);
                app.stage().setHeight(height);
                app.stage().setMaximized(maximized);
                app.context().navigation().navigate(NavigationModel.Page.HOME);
                return null;
            });
            waitUntil(() -> app.context().mods().installed().isEmpty());
        }
        assertTrue(SEVERE.isEmpty(), "no severe UI log entries: " + SEVERE.stream().map(LogRecord::getMessage).toList());
    }

    private static dev.vanta.launcher.core.modrinth.ModrinthService.InstalledContent installedMod(final Path mods, final String title,
                                                                                                 final String version, final String file,
                                                                                                 final String projectId) {
        return new dev.vanta.launcher.core.modrinth.ModrinthService.InstalledContent(dev.vanta.launcher.core.modrinth.ContentType.MOD, title, version,
            mods.resolve(file), true, true, false, projectId, List.of(), false);
    }

    /** @return the installed pane's switches */
    private List<Node> switches() {
        return List.copyOf(app.window().page(NavigationModel.Page.MODS).lookupAll(".switch"));
    }

    /** @return every control of the Mods page a toast must not cover: Install buttons, switches and remove buttons */
    private List<Node> controls() {
        final Node page = app.window().page(NavigationModel.Page.MODS);
        final List<Node> all = new java.util.ArrayList<>(page.lookupAll(".mods-install"));
        all.addAll(page.lookupAll(".switch"));
        all.addAll(page.lookupAll(".mods-remove"));
        return all;
    }

    /** Dismisses every toast a previous test left behind and waits until its card has faded out of the scene graph. */
    private void clearToasts() throws Exception {
        fx(() -> {
            app.context().toasts().clear();
            return null;
        });
        waitUntil(() -> app.window().lookup(".toast") == null);
    }

    /** @return the node's layout bounds (without its drop shadow) in scene coordinates */
    private static javafx.geometry.Bounds sceneBounds(final Node node) {
        return node.localToScene(node.getLayoutBounds());
    }

    private static Node ancestorWithStyle(final Node node, final String styleClass) {
        Node n = node;
        while (n != null && !n.getStyleClass().contains(styleClass)) {
            n = n.getParent();
        }
        return n;
    }

    /** @return the Install buttons of the Mods page, top to bottom */
    private List<Button> installButtons() {
        return app.window().page(NavigationModel.Page.MODS).lookupAll(".mods-install").stream().map(n -> (Button) n)
            .sorted(java.util.Comparator.comparingDouble(b -> b.localToScene(0, 0).getY())).toList();
    }

    private static void assertWithinScene(final javafx.geometry.Bounds bounds, final double width, final double height, final String what) {
        assertTrue(bounds.getMinX() >= 0 && bounds.getMinY() >= 0 && bounds.getMaxX() <= width && bounds.getMaxY() <= height,
            what + " lies inside the " + (int) width + " x " + (int) height + " scene: " + bounds);
    }

    /** The node JavaFX would deliver a mouse event to at the button's centre is the button itself (or its label/icon). */
    private static void assertCovers(final Button button, final javafx.scene.Scene scene) {
        final javafx.geometry.Bounds bounds = button.localToScene(button.getBoundsInLocal());
        final Node picked = pickAt(scene.getRoot(), bounds.getCenterX(), bounds.getCenterY());
        assertNotNull(picked, "something is under the cursor at " + bounds.getCenterX() + ", " + bounds.getCenterY());
        Node n = picked;
        while (n != null && n != button) {
            n = n.getParent();
        }
        assertEquals(button, n, "the node under the cursor at the Install button's centre is the button, not "
            + picked.getClass().getSimpleName() + picked.getStyleClass() + " (" + picked.getId() + ")");
    }

    /**
     * Picks like JavaFX does, with public API only: children are tried front to back, invisible and mouse-transparent nodes
     * are skipped, a clip (the ScrollPane viewport) limits its node, and {@link Node#contains} applies each node's
     * {@code pickOnBounds} (true for every Region, which is why a transparent pane covering content swallows clicks).
     */
    private static Node pickAt(final Node node, final double sceneX, final double sceneY) {
        if (!node.isVisible() || node.isMouseTransparent()) {
            return null;
        }
        final javafx.geometry.Point2D local = node.sceneToLocal(sceneX, sceneY);
        if (local == null) {
            return null;
        }
        if (node.getClip() != null && !node.getClip().contains(node.getClip().parentToLocal(local))) {
            return null;
        }
        if (node instanceof javafx.scene.Parent parent) {
            if (!parent.getBoundsInLocal().contains(local)) {
                return null;
            }
            final List<Node> children = parent.getChildrenUnmodifiable();
            for (int i = children.size() - 1; i >= 0; i--) {
                final Node hit = pickAt(children.get(i), sceneX, sceneY);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return node.contains(local) ? node : null;
    }

    /**
     * Clicks the centre of a node: through the platform's {@link javafx.scene.robot.Robot} (Monocle delivers the press and
     * release through the real picking and event dispatch) when the platform has one, otherwise with synthetic press and
     * release events fired at the node itself.
     */
    private static void click(final Node node) {
        final javafx.geometry.Bounds screen = node.localToScreen(node.getBoundsInLocal());
        try {
            final javafx.scene.robot.Robot robot = new javafx.scene.robot.Robot();
            robot.mouseMove(screen.getCenterX(), screen.getCenterY());
            robot.mousePress(javafx.scene.input.MouseButton.PRIMARY);
            robot.mouseRelease(javafx.scene.input.MouseButton.PRIMARY);
            CLICKED_WITH.compareAndSet(null, "robot");
        } catch (RuntimeException noRobot) {
            CLICKED_WITH.compareAndSet(null, "synthetic events (" + noRobot + ")");
            final javafx.geometry.Bounds scene = node.localToScene(node.getBoundsInLocal());
            final javafx.geometry.Point2D local = node.sceneToLocal(scene.getCenterX(), scene.getCenterY());
            final javafx.scene.input.PickResult pick = new javafx.scene.input.PickResult(node, scene.getCenterX(), scene.getCenterY());
            javafx.event.Event.fireEvent(node, new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_PRESSED, local.getX(),
                local.getY(), screen.getCenterX(), screen.getCenterY(), javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, false, false, true, pick));
            javafx.event.Event.fireEvent(node, new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_RELEASED, local.getX(),
                local.getY(), screen.getCenterX(), screen.getCenterY(), javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                false, false, false, false, false, true, pick));
        }
    }

    private static final AtomicReference<String> CLICKED_WITH = new AtomicReference<>();

    @Test
    void aRunningMinecraftLauncherIsNamedAndNeverClosedForThePlayer() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        backend.runningLaunchers.add("MinecraftLauncher.exe (process 1234)");
        try {
            fx(() -> {
                app.context().navigation().navigate(NavigationModel.Page.HOME);
                app.window().showOfficialProfile();
                return null;
            });
            waitUntil(() -> app.window().dialogs().lookup(".official-running-details") != null);
            final long checks = backend.calls.stream().filter("runningOfficialLaunchers"::equals).count();
            fx(() -> {
                final Node details = app.window().dialogs().lookup(".official-running-details");
                assertTrue(details.lookupAll(".mono").stream().anyMatch(n -> "MinecraftLauncher.exe (process 1234)".equals(((Label) n).getText())));
                backend.runningLaunchers.clear();
                dialogButton(app.context().t("official.running.checkAgain")).fire();
                return null;
            });
            waitUntil(() -> app.window().dialogs().lookup(".official-plan") != null);
            assertTrue(backend.calls.stream().filter("runningOfficialLaunchers"::equals).count() > checks, "'Check again' looked again");
            fx(() -> {
                app.window().dialogs().close();
                return null;
            });
        } finally {
            backend.runningLaunchers.clear();
        }
        assertFalse(backend.calls.contains("installOfficialProfile"), "nothing was written");
        assertTrue(SEVERE.isEmpty(), "no severe UI log entries: " + SEVERE.stream().map(LogRecord::getMessage).toList());
    }

    @Test
    void withoutSignInPlayGoesThroughTheMinecraftLauncher() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        final boolean wasConfigured = backend.signInConfigured;
        final var accounts = List.copyOf(backend.accounts);
        try {
            backend.signInConfigured = false;
            backend.accounts.clear();
            fx(() -> {
                app.context().navigation().navigate(NavigationModel.Page.HOME);
                app.context().session().refreshAll();
                return null;
            });
            waitUntil(() -> app.context().home().officialPlayModeProperty().get());
            fx(() -> {
                final Button play = (Button) app.window().lookup(".play-button");
                assertEquals(app.context().t("home.play.official"), play.getText());
                assertTrue(play.getStyleClass().contains("official-play"));
                assertFalse(play.isDisabled(), "playing via the Minecraft Launcher needs no sign-in here");
                return null;
            });
        } finally {
            backend.signInConfigured = wasConfigured;
            backend.accounts.addAll(accounts);
            fx(() -> {
                app.context().session().refreshAll();
                return null;
            });
            waitUntil(() -> app.context().session().account().isPresent() && !app.context().home().officialPlayModeProperty().get());
        }
        fx(() -> {
            final Button play = (Button) app.window().lookup(".play-button");
            assertFalse(play.getStyleClass().contains("official-play"));
            return null;
        });
    }

    @Test
    void smokeHookWritesAScreenshotAndExitsCleanly() throws Exception {
        waitUntil(() -> app.context().session().loadedProperty().get());
        final Path png = Files.createTempDirectory("vanta-ui-smoke").resolve("smoke.png");
        final dev.vanta.launcher.ui.UiSmoke smoke = dev.vanta.launcher.ui.UiSmoke.fromEnvironment(java.util.Map.of(
            dev.vanta.launcher.ui.UiSmoke.SCREENSHOT_ENV, png.toString(), dev.vanta.launcher.ui.UiSmoke.EXIT_AFTER_ENV, "1")).orElseThrow();
        final AtomicReference<Integer> exit = new AtomicReference<>();
        fx(() -> {
            smoke.start(app.stage().getScene(), app.context().session().loadedProperty(), exit::set);
            return null;
        });
        waitUntil(() -> exit.get() != null);
        assertEquals(0, exit.get(), "exit code 0 when the screenshot was written");
        final java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(png.toFile());
        assertNotNull(image, "a readable PNG");
        assertEquals((int) Math.round(app.stage().getScene().getWidth()), image.getWidth());
    }

    private Button dialogButton(final String text) {
        return app.window().dialogs().lookupAll(".button").stream()
            .filter(n -> n instanceof Button b && text.equals(b.getText())).map(n -> (Button) n).findFirst()
            .orElseThrow(() -> new AssertionError("no dialog button '" + text + "'"));
    }

    private static <T> T fx(final Callable<T> action) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<T> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(30, TimeUnit.SECONDS), "FX action timed out");
        if (error.get() instanceof Exception e) {
            throw e;
        }
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        return result.get();
    }

    private static void waitUntil(final Callable<Boolean> condition) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (fx(condition)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("condition not met within 30 s");
    }
}
