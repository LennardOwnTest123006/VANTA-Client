package dev.vanta.launcher.ui;

import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.ui.model.NavigationModel;
import javafx.animation.PauseTransition;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Scene;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UI smoke hook for CI: proves on a real system (the Windows runner, through the installed {@code .msi}, the portable
 * zip and the jar) that the window comes up and renders.
 *
 * <ul>
 *   <li>{@value #SCREENSHOT_ENV}{@code =<png path>}: once the main window is shown and the first refresh has finished
 *       (at most {@value #MAX_WAIT_SECONDS} seconds), a JavaFX snapshot of the scene is written there.</li>
 *   <li>{@value #EXIT_AFTER_ENV}{@code =<seconds>}: that long after the window was shown the launcher closes with exit
 *       code 0 (2 when the screenshot could not be written).</li>
 *   <li>{@value #PAGE_ENV}{@code =<home|mods|versions|logs|settings|about>}: the page the window shows before the
 *       screenshot is taken (case does not matter). Only together with one of the two variables above.</li>
 *   <li>{@value #SIZE_ENV}{@code =<width>x<height>}: the window size in pixels, e.g. {@code 1000x600}; a value below
 *       the minimum window size is raised to it, and a maximised window is restored first. Only together with one of the
 *       first two variables.</li>
 * </ul>
 *
 * <p>Without the first two variables nothing happens; a page or a size that cannot be read is logged and ignored.</p>
 */
public final class UiSmoke {

    /** Environment variable naming the PNG to write. */
    public static final String SCREENSHOT_ENV = "VANTA_UI_SMOKE_SCREENSHOT";
    /** Environment variable with the seconds after which the launcher exits. */
    public static final String EXIT_AFTER_ENV = "VANTA_UI_SMOKE_EXIT_AFTER";
    /** Environment variable naming the page to show before the screenshot. */
    public static final String PAGE_ENV = "VANTA_UI_SMOKE_PAGE";
    /** Environment variable with the window size ({@code <width>x<height>}). */
    public static final String SIZE_ENV = "VANTA_UI_SMOKE_SIZE";
    /** Exit code when the screenshot was requested but could not be written. */
    public static final int SCREENSHOT_FAILED = 2;
    /** Longest wait for the first refresh before the screenshot is taken anyway. */
    public static final int MAX_WAIT_SECONDS = 15;

    private static final Logger LOG = LauncherLog.get("UI.Smoke");
    private static final Pattern SIZE = Pattern.compile("\\s*(\\d{2,5})\\s*[xX×*]\\s*(\\d{2,5})\\s*");

    /**
     * A window size.
     *
     * @param width  width in pixels
     * @param height height in pixels
     */
    public record Size(double width, double height) {
    }

    private final Optional<Path> screenshot;
    private final Optional<Integer> exitAfterSeconds;
    private final Optional<NavigationModel.Page> page;
    private final Optional<Size> size;
    private boolean shot;
    private boolean shotFailed;

    private UiSmoke(final Optional<Path> screenshot, final Optional<Integer> exitAfterSeconds, final Optional<NavigationModel.Page> page,
                    final Optional<Size> size) {
        this.screenshot = screenshot;
        this.exitAfterSeconds = exitAfterSeconds;
        this.page = page;
        this.size = size;
    }

    /**
     * @param env environment variables
     * @return the hook configured by the environment, empty when neither {@value #SCREENSHOT_ENV} nor
     *     {@value #EXIT_AFTER_ENV} is set
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
        if (screenshot.isEmpty() && exitAfter.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new UiSmoke(screenshot, exitAfter, parsePage(env.get(PAGE_ENV)), parseSize(env.get(SIZE_ENV))));
    }

    /**
     * @param value value of {@value #PAGE_ENV}
     * @return the page, empty when unset or unknown
     */
    static Optional<NavigationModel.Page> parsePage(final String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        final String name = value.trim().toUpperCase(Locale.ROOT);
        for (NavigationModel.Page candidate : NavigationModel.Page.values()) {
            if (candidate.name().equals(name)) {
                return Optional.of(candidate);
            }
        }
        LOG.log(Level.WARNING, "Ignoring {0}={1}: not one of {2}", new Object[] {PAGE_ENV, value, java.util.Arrays.toString(NavigationModel.Page.values())});
        return Optional.empty();
    }

    /**
     * @param value value of {@value #SIZE_ENV}
     * @return the size, empty when unset or not {@code <width>x<height>}
     */
    static Optional<Size> parseSize(final String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        final Matcher m = SIZE.matcher(value);
        if (!m.matches()) {
            LOG.log(Level.WARNING, "Ignoring {0}={1}: expected <width>x<height>, e.g. 1000x600", new Object[] {SIZE_ENV, value});
            return Optional.empty();
        }
        return Optional.of(new Size(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))));
    }

    /** @return where the screenshot goes */
    public Optional<Path> screenshot() {
        return screenshot;
    }

    /** @return seconds after which the launcher exits */
    public Optional<Integer> exitAfterSeconds() {
        return exitAfterSeconds;
    }

    /** @return the page to show first */
    public Optional<NavigationModel.Page> page() {
        return page;
    }

    /** @return the window size to apply */
    public Optional<Size> size() {
        return size;
    }

    /**
     * Schedules the screenshot and the exit without changing the page or the window size (the page and size variables
     * are ignored).
     *
     * @param scene  the main scene
     * @param loaded becomes true when the first refresh finished
     * @param exit   closes the launcher with an exit code
     */
    public void start(final Scene scene, final ObservableBooleanValue loaded, final IntConsumer exit) {
        start(null, scene, null, loaded, exit);
    }

    /**
     * Applies the window size, shows the page, then schedules the screenshot and the exit. Call on the JavaFX thread
     * right after the window was shown.
     *
     * @param stage      the main window (null: the size variable is ignored)
     * @param scene      the main scene
     * @param navigation page navigation (null: the page variable is ignored)
     * @param loaded     becomes true when the first refresh finished
     * @param exit       closes the launcher with an exit code
     */
    public void start(final Stage stage, final Scene scene, final NavigationModel navigation, final ObservableBooleanValue loaded,
                      final IntConsumer exit) {
        Objects.requireNonNull(scene, "scene");
        LOG.log(Level.INFO, "UI smoke: window shown; screenshot {0}, exit after {1} s, page {2}, size {3}",
            new Object[] {screenshot.map(Path::toString).orElse("none"), exitAfterSeconds.map(String::valueOf).orElse("never"),
                page.map(Enum::name).orElse("unchanged"), size.map(s -> (int) s.width() + "x" + (int) s.height()).orElse("unchanged")});
        if (size.isPresent() && stage != null) {
            resize(stage, size.get());
        }
        if (page.isPresent() && navigation != null) {
            navigation.navigate(page.get());
        }
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

    private static void resize(final Stage stage, final Size size) {
        if (stage.isMaximized()) {
            stage.setMaximized(false);
        }
        final double width = Math.max(stage.getMinWidth(), size.width());
        final double height = Math.max(stage.getMinHeight(), size.height());
        stage.setWidth(width);
        stage.setHeight(height);
        LOG.log(Level.INFO, "UI smoke: window size set to {0} x {1} (asked for {2} x {3})",
            new Object[] {(int) width, (int) height, (int) size.width(), (int) size.height()});
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
