package dev.vanta.core.screen.menu;

import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.ParticleSystem;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Theme;
import java.util.List;

/**
 * Painters for the {@link MenuBackground} styles and the ambient particle layer of the main menu. Everything is
 * composed of plain fills so it renders identically in-game and in the Java2D previews, and the number of draw
 * calls stays in the low thousands per frame even on large windows.
 */
public final class MenuBackgrounds {
    private static final int GRID_ROWS = 12;
    private static final int GRID_COLUMNS = 19;

    private MenuBackgrounds() {
    }

    /**
     * Paints the background into {@code (0, 0, width, height)}. {@link MenuBackground#VANILLA_PANORAMA} draws only a
     * vignette: the host renders the panorama underneath when
     * {@link dev.vanta.core.screen.common.VantaUiScreen#wantsVanillaPanorama()} is true.
     */
    public static void paint(Canvas canvas, Theme theme, MenuBackground background, int width, int height) {
        switch (background) {
            case GRADIENT_DARK -> gradientDark(canvas, theme, width, height);
            case GRAPHITE_GRID -> graphiteGrid(canvas, theme, width, height);
            case VIOLET_HORIZON -> violetHorizon(canvas, theme, width, height);
            case VANILLA_PANORAMA -> panoramaVignette(canvas, theme, width, height);
            case SOLID -> canvas.fill(0, 0, width, height, theme.bgVoid());
        }
    }

    private static void gradientDark(Canvas canvas, Theme theme, int w, int h) {
        canvas.fillGradientV(0, 0, w, h, theme.bgVoid(), theme.bgBase());
        // Second layer: graphite rising from the bottom third.
        int layerTop = h * 2 / 3;
        canvas.fillGradientV(0, layerTop, w, h - layerTop, Colors.withAlpha(theme.surface2(), 0f),
                Colors.withAlpha(theme.surface2(), 0.75f));
        // A faint accent haze high up keeps the dark from feeling flat.
        int hazeH = h / 3;
        canvas.fillGradientV(0, 0, w, hazeH, Colors.withAlpha(theme.accent(), 0.07f), Colors.withAlpha(theme.accent(), 0f));
        vignette(canvas, theme, w, h, 0.45f);
    }

    private static void graphiteGrid(Canvas canvas, Theme theme, int w, int h) {
        canvas.fillGradientV(0, 0, w, h, theme.bgBase(), theme.surface1());
        int horizon = Math.round(h * 0.56f);
        int lineColor = theme.borderStrong();
        // Horizontal lines, quadratically spaced so they bunch up towards the horizon (perspective floor).
        for (int i = 1; i <= GRID_ROWS; i++) {
            float t = i / (float) GRID_ROWS;
            int y = horizon + Math.round((h - horizon) * t * t);
            canvas.fill(0, Math.min(h - 1, y), w, 1, Colors.withAlpha(lineColor, 0.10f + 0.22f * t));
        }
        // Converging lines from the bottom edge to the vanishing point; drawn row by row so each is a 1 px step path.
        int vpX = w / 2;
        float spread = w * 2.4f;
        for (int k = 0; k < GRID_COLUMNS; k++) {
            float bottomX = vpX - spread / 2f + spread * k / (GRID_COLUMNS - 1);
            for (int y = horizon + 1; y < h; y++) {
                float t = (y - horizon) / (float) (h - horizon);
                int x = Math.round(vpX + (bottomX - vpX) * t);
                if (x >= 0 && x < w) {
                    canvas.fill(x, y, 1, 1, Colors.withAlpha(lineColor, 0.08f + 0.20f * t));
                }
            }
        }
        // Glow along the horizon so the floor reads as lit from behind.
        int glowH = Math.max(8, h / 9);
        canvas.fillGradientV(0, horizon - glowH, w, glowH, Colors.withAlpha(theme.accent(), 0f),
                Colors.withAlpha(theme.accent(), 0.16f));
        canvas.fillGradientV(0, horizon, w, glowH, Colors.withAlpha(theme.accentBlue(), 0.14f),
                Colors.withAlpha(theme.accentBlue(), 0f));
        canvas.fillGradientH(0, horizon, w, 1, Colors.withAlpha(theme.gradientStart(), 0.55f),
                Colors.withAlpha(theme.gradientEnd(), 0.55f));
        vignette(canvas, theme, w, h, 0.5f);
    }

    private static void violetHorizon(Canvas canvas, Theme theme, int w, int h) {
        canvas.fillGradientV(0, 0, w, h, theme.bgVoid(), Colors.lerp(theme.bgVoid(), theme.bgBase(), 0.8f));
        int centerY = Math.round(h * 0.62f);
        int half = Math.max(12, Math.round(h * 0.24f));
        float radiusX = w * 0.72f;
        // Soft elliptical glow, violet above the horizon fading into blue below it.
        for (int dy = -half; dy <= half; dy++) {
            float n = Math.abs(dy) / (float) half;
            float falloff = (1f - n) * (1f - n);
            float hw = radiusX * (float) Math.sqrt(Math.max(0f, 1f - n * n));
            float along = (dy + half) / (float) (half * 2);
            int color = Colors.lerp(theme.gradientStart(), theme.gradientEnd(), along);
            int x = Math.round(w / 2f - hw);
            canvas.fill(x, centerY + dy, Math.round(hw * 2f), 1, Colors.withAlpha(color, 0.26f * falloff));
        }
        // Bright horizon line and a faint reflection beneath it.
        canvas.fillGradientH(0, centerY, w, 1, Colors.withAlpha(theme.gradientStart(), 0.75f),
                Colors.withAlpha(theme.gradientEnd(), 0.75f));
        canvas.fillGradientV(0, centerY + 1, w, Math.max(6, h / 14), Colors.withAlpha(theme.accentBlue(), 0.12f),
                Colors.withAlpha(theme.accentBlue(), 0f));
        vignette(canvas, theme, w, h, 0.4f);
    }

    private static void panoramaVignette(Canvas canvas, Theme theme, int w, int h) {
        int top = Math.round(h * 0.42f);
        canvas.fillGradientV(0, 0, w, top, Colors.withAlpha(theme.bgVoid(), 0.70f), Colors.withAlpha(theme.bgVoid(), 0f));
        int bottom = Math.round(h * 0.5f);
        canvas.fillGradientV(0, h - bottom, w, bottom, Colors.withAlpha(theme.bgVoid(), 0f),
                Colors.withAlpha(theme.bgVoid(), 0.82f));
        vignette(canvas, theme, w, h, 0.5f);
    }

    /** Darkens the left and right edges. */
    private static void vignette(Canvas canvas, Theme theme, int w, int h, float strength) {
        int edge = Math.max(16, w / 5);
        int dark = theme.bgVoid();
        canvas.fillGradientH(0, 0, edge, h, Colors.withAlpha(dark, strength), Colors.withAlpha(dark, 0f));
        canvas.fillGradientH(w - edge, 0, edge, h, Colors.withAlpha(dark, 0f), Colors.withAlpha(dark, strength));
    }

    /** Draws particles as small soft squares (a dim halo one pixel larger than the bright core). */
    public static void paintParticles(Canvas canvas, List<ParticleSystem.Particle> particles) {
        for (ParticleSystem.Particle p : particles) {
            int size = Math.max(1, (int) Math.round(p.size()));
            int x = (int) Math.round(p.x() - size / 2.0);
            int y = (int) Math.round(p.y() - size / 2.0);
            int core = Colors.withAlpha(Colors.opaque(p.color()), (float) p.alpha());
            int halo = Colors.withAlpha(Colors.opaque(p.color()), (float) p.alpha() * 0.28f);
            canvas.fill(x - 1, y - 1, size + 2, size + 2, halo);
            canvas.fill(x, y, size, size, core);
        }
    }
}
