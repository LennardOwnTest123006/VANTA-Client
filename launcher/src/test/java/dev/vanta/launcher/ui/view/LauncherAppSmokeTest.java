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
        data = Files.createTempDirectory("vanta-smoke");
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
