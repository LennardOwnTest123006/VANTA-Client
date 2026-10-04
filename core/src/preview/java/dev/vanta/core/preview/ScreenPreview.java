package dev.vanta.core.preview;

import dev.vanta.core.ui.ManualClock;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiEnvironment;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Renders a {@link UiScreen} to PNG with the Java2D canvas at a given GUI size and scale. The clock is manual so
 * hover animations, carets and the open transition are captured at exact times.
 */
public final class ScreenPreview {

    /** How long after init a "settled" frame is taken (all transitions finished). */
    public static final long SETTLED_MS = 1000L;

    /**
     * Render options.
     *
     * @param width     GUI width
     * @param height    GUI height
     * @param scale     device pixels per GUI pixel
     * @param mouseX    simulated cursor x (host pixels), negative for no cursor
     * @param mouseY    simulated cursor y (host pixels)
     * @param theme     theme to render with
     * @param backdrop  color painted behind the screen (stands in for the game world)
     * @param lang      translation lookup used by the screen
     * @param mouseOver id of a node to center the cursor on after init (overrides mouseX/mouseY), or {@code null}
     */
    public record Options(int width, int height, int scale, int mouseX, int mouseY, Theme theme, int backdrop,
                          Function<String, String> lang, String mouseOver) {

        /** 640x360 at 2x, no cursor, default theme, graphite backdrop. */
        public static Options standard() {
            return new Options(640, 360, 2, -1, -1, Theme.DEFAULT, 0xFF14141C, Function.identity(), null);
        }

        public Options size(int w, int h) {
            return new Options(w, h, scale, mouseX, mouseY, theme, backdrop, lang, mouseOver);
        }

        public Options scale(int s) {
            return new Options(width, height, s, mouseX, mouseY, theme, backdrop, lang, mouseOver);
        }

        /** Cursor position in GUI pixels. */
        public Options mouse(int x, int y) {
            return new Options(width, height, scale, x, y, theme, backdrop, lang, null);
        }

        /** Centers the cursor on the node with this id after layout (hover + tooltip in the capture). */
        public Options mouseOver(String nodeId) {
            return new Options(width, height, scale, mouseX, mouseY, theme, backdrop, lang, nodeId);
        }

        public Options theme(Theme t) {
            return new Options(width, height, scale, mouseX, mouseY, t, backdrop, lang, mouseOver);
        }

        public Options backdrop(int argb) {
            return new Options(width, height, scale, mouseX, mouseY, theme, argb, lang, mouseOver);
        }

        public Options lang(Function<String, String> l) {
            return new Options(width, height, scale, mouseX, mouseY, theme, backdrop, l, mouseOver);
        }
    }

    private ScreenPreview() {
    }

    /**
     * Renders a settled frame: the screen is initialised at t=0, warmed up once so hover animations start, then
     * drawn at {@link #SETTLED_MS}.
     */
    public static BufferedImage render(UiScreen screen, Options options) {
        return frames(screen, options, new long[] {SETTLED_MS}).get(0);
    }

    /**
     * Renders the screen at each timestamp (ms after init) in order, returning one image per time. A warm-up
     * frame at t=0 is rendered first so hover state exists before the first captured frame.
     */
    public static List<BufferedImage> frames(UiScreen screen, Options options, long[] timesMs) {
        PreviewFonts fonts = new PreviewFonts();
        ManualClock clock = new ManualClock();
        PreviewHost host = new PreviewHost();
        Java2DCanvas metrics = new Java2DCanvas(1, 1, 1, fonts);
        UiEnvironment env = new UiEnvironment(host, options.theme(), clock, metrics, options.lang());
        screen.attach(env);
        screen.init(options.width(), options.height());
        int mx = options.mouseX() < 0 ? -1000 : options.mouseX();
        int my = options.mouseY() < 0 ? -1000 : options.mouseY();
        if (options.mouseOver() != null) {
            UiNode target = screen.root().findById(options.mouseOver());
            if (target != null) {
                float s = screen.effectiveScale();
                mx = Math.round(target.bounds().centerX() * s);
                my = Math.round(target.bounds().centerY() * s);
            }
        }
        // Warm-up: establishes hover state so animations have a start time.
        Java2DCanvas warm = new Java2DCanvas(options.width(), options.height(), 1, fonts);
        screen.render(warm, mx, my, 0f);
        warm.dispose();
        List<BufferedImage> out = new ArrayList<>();
        for (long t : timesMs) {
            clock.set(t);
            screen.tick();
            Java2DCanvas canvas = new Java2DCanvas(options.width(), options.height(), options.scale(), fonts);
            canvas.clear(options.backdrop());
            screen.render(canvas, mx, my, 0f);
            canvas.dispose();
            out.add(canvas.image());
        }
        metrics.dispose();
        return out;
    }

    /** Saves an image as PNG and returns the path. */
    public static Path save(BufferedImage image, Path file) throws IOException {
        java.nio.file.Files.createDirectories(file.toAbsolutePath().getParent());
        javax.imageio.ImageIO.write(image, "png", file.toFile());
        return file;
    }
}
