package dev.vanta.launcher.ui;

import dev.vanta.launcher.core.log.LauncherLog;
import javafx.animation.PauseTransition;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Scene;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UI smoke hook for CI: proves on a real system (the Windows runner, through the installed {@code .msi}, the portable
 * zip and the jar) that the window comes up and renders.
 *
 * <ul>
 *   <li>{@value #SCREENSHOT_ENV}{@code =<png path>}: once the main window is shown and the first refresh has finished
 *       (at most {@value #MAX_WAIT_SECONDS} seconds), a JavaFX snapshot of the scene is written there.</li>
 *   <li>{@value #EXIT_AFTER_ENV}{@code =<seconds>}: that long after the window was shown the launcher closes with exit
 *       code 0 (2 when the screenshot could not be written).</li>
 * </ul>
 *
 * <p>Without these variables nothing happens.</p>
 */
public final class UiSmoke {

    /** Environment variable naming the PNG to write. */
    public static final String SCREENSHOT_ENV = "VANTA_UI_SMOKE_SCREENSHOT";
    /** Environment variable with the seconds after which the launcher exits. */
    public static final String EXIT_AFTER_ENV = "VANTA_UI_SMOKE_EXIT_AFTER";
    /** Exit code when the screenshot was requested but could not be written. */
    public static final int SCREENSHOT_FAILED = 2;
    /** Longest wait for the first refresh before the screenshot is taken anyway. */
    public static final int MAX_WAIT_SECONDS = 15;

    private static final Logger LOG = LauncherLog.get("UI.Smoke");

    private final Optional<Path> screenshot;
    private final Optional<Integer> exitAfterSeconds;
    private boolean shot;
    private boolean shotFailed;

    private UiSmoke(final Optional<Path> screenshot, final Optional<Integer> exitAfterSeconds) {
        this.screenshot = screenshot;
        this.exitAfterSeconds = exitAfterSeconds;
    }

    /**
     * @param env environment variables
     * @return the hook configured by the environment, empty when neither variable is set
     */
    public static Optional<UiSmoke> fromEnvironment(final Map<String, String> env) {
        final String png = env.get(SCREENSHOT_ENV);
        final String exit = env.get(EXIT_AFTER_ENV);
        final Optional<Path> screenshot = png == null || png.isBlank() ? Optional.empty() : Optional.of(Path.of(png.trim()).toAbsolutePath());
        Optional<Integer> exitAfter = Optional.empty();
        if (exit != null && !exit.isBlank()) {
            try {
                exitAfter = Optional.of(Math.max(1, Integer.parseInt(exit.trim())));
            } catch (NumberFormatException e) {
                LOG.log(Level.WARNING, "Ignoring {0}={1}: not a number of seconds", new Object[] {EXIT_AFTER_ENV, exit});
            }
        }
        return screenshot.isEmpty() && exitAfter.isEmpty() ? Optional.empty() : Optional.of(new UiSmoke(screenshot, exitAfter));
    }

    /** @return where the screenshot goes */
    public Optional<Path> screenshot() {
        return screenshot;
    }

    /** @return seconds after which the launcher exits */
    public Optional<Integer> exitAfterSeconds() {
        return exitAfterSeconds;
    }

    /**
     * Schedules the screenshot and the exit. Call on the JavaFX thread right after the window was shown.
     *
     * @param scene  the main scene
     * @param loaded becomes true when the first refresh finished
     * @param exit   closes the launcher with an exit code
     */
    public void start(final Scene scene, final ObservableBooleanValue loaded, final IntConsumer exit) {
        Objects.requireNonNull(scene, "scene");
        LOG.log(Level.INFO, "UI smoke: window shown; screenshot {0}, exit after {1} s",
            new Object[] {screenshot.map(Path::toString).orElse("none"), exitAfterSeconds.map(String::valueOf).orElse("never")});
        if (screenshot.isPresent()) {
            final Runnable take = () -> after(Duration.millis(800), () -> snapshot(scene));
            if (loaded.get()) {
                take.run();
            } else {
                loaded.addListener((obs, old, now) -> {
                    if (now) {
                        take.run();
                    }
                });
                after(Duration.seconds(MAX_WAIT_SECONDS), () -> snapshot(scene));
            }
        }
        exitAfterSeconds.ifPresent(seconds -> after(Duration.seconds(seconds), () -> {
            if (screenshot.isPresent() && !shot) {
                snapshot(scene);
            }
            final int code = shotFailed ? SCREENSHOT_FAILED : 0;
            LOG.log(Level.INFO, "UI smoke: exiting with code {0}", code);
            exit.accept(code);
        }));
    }

    private void snapshot(final Scene scene) {
        if (shot || screenshot.isEmpty()) {
            return;
        }
        shot = true;
        final Path file = screenshot.get();
        try {
            final WritableImage image = scene.snapshot(null);
            write(image, file);
            LOG.log(Level.INFO, "UI smoke: wrote {0} ({1} x {2})", new Object[] {file, (int) image.getWidth(), (int) image.getHeight()});
        } catch (IOException | RuntimeException e) {
            shotFailed = true;
            LOG.log(Level.SEVERE, "UI smoke: the screenshot could not be written to " + file, e);
        }
    }

    /**
     * Writes a JavaFX image as PNG (without javafx.swing, which the launcher runtime does not include).
     *
     * @param image image
     * @param file  PNG file
     * @throws IOException on failure
     */
    public static void write(final WritableImage image, final Path file) throws IOException {
        final int w = (int) image.getWidth();
        final int h = (int) image.getHeight();
        final int[] pixels = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        final BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, w, h, pixels, 0, w);
        final Path dir = file.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        if (!ImageIO.write(buffered, "png", file.toFile())) {
            throw new IOException("No PNG writer available");
        }
    }

    private static void after(final Duration delay, final Runnable action) {
        final PauseTransition pause = new PauseTransition(delay);
        pause.setOnFinished(e -> action.run());
        pause.play();
    }
}
