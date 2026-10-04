package dev.vanta.core.screen.cosmetics;

import dev.vanta.core.cosmetics.Badge;
import dev.vanta.core.cosmetics.HudTheme;
import dev.vanta.core.cosmetics.HudThemeStyle;
import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.ParticleSystem;
import dev.vanta.core.crosshair.CrosshairGeometry;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.VantaMark;
import java.util.List;

/**
 * Small painters for the cosmetic cards: a palette mock-up of a UI theme, the five menu backgrounds (compact
 * versions of the main-menu renderers), a sample HUD widget per HUD theme, a crosshair over a world backdrop, the
 * particle layer and the badge glyphs. Everything is plain fills so the cards look identical in-game and in the
 * Java2D previews.
 */
public final class Thumbnails {
    private static final int SKY_TOP = 0xFF4F7FC2;
    private static final int SKY_BOTTOM = 0xFF8FB6E6;
    private static final int GRASS = 0xFF5E9A45;
    private static final int DIRT = 0xFF6B4A32;

    private Thumbnails() {
    }

    // ---- UI theme ---------------------------------------------------------------------------------------------------

    /** A miniature VANTA screen (top bar, rail, card with text lines and a primary button) in the theme's colours. */
    public static void palette(Canvas canvas, Rect r, Theme t) {
        canvas.fillRounded(r.x(), r.y(), r.w(), r.h(), Theme.RADIUS_MD, t.bgBase());
        // Top bar with the accent hairline.
        canvas.fill(r.x(), r.y(), r.w(), 8, t.surface1());
        canvas.fillGradientH(r.x(), r.y(), r.w(), 1, t.gradientStart(), t.gradientEnd());
        canvas.fill(r.x() + 4, r.y() + 3, 10, 2, t.textPrimary());
        // Rail.
        int railW = Math.max(10, r.w() / 5);
        canvas.fill(r.x(), r.y() + 8, railW, r.h() - 8, Colors.withAlpha(t.surface1(), 0.8f));
        canvas.fillRounded(r.x() + 2, r.y() + 11, railW - 4, 5, Theme.RADIUS_SM, Colors.withAlpha(t.accent(), 0.3f));
        canvas.fill(r.x() + 3, r.y() + 19, railW - 6, 1, t.textMuted());
        canvas.fill(r.x() + 3, r.y() + 23, railW - 6, 1, t.textMuted());
        // Content card with text lines and a primary button.
        int cx = r.x() + railW + 4;
        int cy = r.y() + 11;
        int cw = r.right() - 4 - cx;
        int ch = r.bottom() - 4 - cy;
        if (cw > 8 && ch > 8) {
            canvas.fillRounded(cx, cy, cw, ch, Theme.RADIUS_SM, t.surface2());
            canvas.strokeRounded(cx, cy, cw, ch, Theme.RADIUS_SM, t.borderSubtle());
            canvas.fill(cx + 4, cy + 4, Math.max(4, cw / 2), 2, t.textPrimary());
            canvas.fill(cx + 4, cy + 8, Math.max(4, cw - 8), 1, t.textSecondary());
            canvas.fill(cx + 4, cy + 11, Math.max(4, cw * 2 / 3), 1, t.textMuted());
            int bw = Math.min(cw - 8, 22);
            int by = cy + ch - 10;
            if (by > cy + 12) {
                canvas.fillRoundedGradientH(cx + 4, by, bw, 6, Theme.RADIUS_SM, t.gradientStart(), t.gradientEnd());
                int tx = cx + 4 + bw + 3;
                if (tx + 12 < cx + cw) {
                    canvas.fillRounded(tx, by, 12, 6, Theme.RADIUS_LG, t.surface3());
                    canvas.strokeRounded(tx, by, 12, 6, Theme.RADIUS_LG, t.borderStrong());
                    canvas.fillRounded(tx + 7, by + 1, 4, 4, Theme.RADIUS_LG, t.accentHover());
                }
            }
        }
        canvas.strokeRounded(r.x(), r.y(), r.w(), r.h(), Theme.RADIUS_MD, t.borderStrong());
    }

    // ---- menu backgrounds -----------------------------------------------------------------------------------------------

    /** A compact rendering of a menu background with the VANTA mark and three menu buttons on top. */
    public static void background(Canvas canvas, Rect r, MenuBackground background, Theme t) {
        canvas.pushScissor(r);
        int x = r.x();
        int y = r.y();
        int w = r.w();
        int h = r.h();
        switch (background) {
            case GRADIENT_DARK -> {
                canvas.fillGradientV(x, y, w, h, t.bgVoid(), t.bgBase());
                int layerTop = y + h * 2 / 3;
                canvas.fillGradientV(x, layerTop, w, y + h - layerTop, Colors.withAlpha(t.surface2(), 0f),
                        Colors.withAlpha(t.surface2(), 0.75f));
                canvas.fillGradientV(x, y, w, h / 3, Colors.withAlpha(t.accent(), 0.10f), Colors.withAlpha(t.accent(), 0f));
            }
            case GRAPHITE_GRID -> {
                canvas.fillGradientV(x, y, w, h, t.bgBase(), t.surface1());
                int horizon = y + Math.round(h * 0.56f);
                for (int i = 1; i <= 6; i++) {
                    float f = i / 6f;
                    int ly = horizon + Math.round((y + h - horizon) * f * f);
                    canvas.fill(x, Math.min(y + h - 1, ly), w, 1, Colors.withAlpha(t.borderStrong(), 0.15f + 0.3f * f));
                }
                int vpX = x + w / 2;
                for (int k = 0; k < 9; k++) {
                    float bottomX = vpX - w * 1.2f + w * 2.4f * k / 8f;
                    for (int ly = horizon + 1; ly < y + h; ly++) {
                        float f = (ly - horizon) / (float) (y + h - horizon);
                        int lx = Math.round(vpX + (bottomX - vpX) * f);
                        if (lx >= x && lx < x + w) {
                            canvas.fill(lx, ly, 1, 1, Colors.withAlpha(t.borderStrong(), 0.12f + 0.3f * f));
                        }
                    }
                }
                int glowH = Math.max(4, h / 6);
                canvas.fillGradientV(x, horizon - glowH, w, glowH, Colors.withAlpha(t.accent(), 0f),
                        Colors.withAlpha(t.accent(), 0.22f));
                canvas.fillGradientH(x, horizon, w, 1, Colors.withAlpha(t.gradientStart(), 0.7f),
                        Colors.withAlpha(t.gradientEnd(), 0.7f));
            }
            case VIOLET_HORIZON -> {
                canvas.fillGradientV(x, y, w, h, t.bgVoid(), Colors.lerp(t.bgVoid(), t.bgBase(), 0.8f));
                int centerY = y + Math.round(h * 0.62f);
                int half = Math.max(4, Math.round(h * 0.26f));
                for (int dy = -half; dy <= half; dy++) {
                    float n = Math.abs(dy) / (float) half;
                    float falloff = (1f - n) * (1f - n);
                    float hw = w * 0.72f * (float) Math.sqrt(Math.max(0f, 1f - n * n));
                    int color = Colors.lerp(t.gradientStart(), t.gradientEnd(), (dy + half) / (float) (half * 2));
                    canvas.fill(Math.round(x + w / 2f - hw), centerY + dy, Math.round(hw * 2f), 1,
                            Colors.withAlpha(color, 0.32f * falloff));
                }
                canvas.fillGradientH(x, centerY, w, 1, Colors.withAlpha(t.gradientStart(), 0.8f),
                        Colors.withAlpha(t.gradientEnd(), 0.8f));
            }
            case VANILLA_PANORAMA -> {
                // Stand-in for the vanilla panorama: sky and ground bands under the same dark vignette the menu uses.
                int ground = y + Math.round(h * 0.68f);
                canvas.fillGradientV(x, y, w, ground - y, SKY_TOP, SKY_BOTTOM);
                canvas.fillGradientV(x, ground, w, y + h - ground, GRASS, DIRT);
                canvas.fillGradientV(x, y, w, Math.round(h * 0.42f), Colors.withAlpha(t.bgVoid(), 0.70f),
                        Colors.withAlpha(t.bgVoid(), 0f));
                int bottom = Math.round(h * 0.5f);
                canvas.fillGradientV(x, y + h - bottom, w, bottom, Colors.withAlpha(t.bgVoid(), 0f),
                        Colors.withAlpha(t.bgVoid(), 0.82f));
            }
            case SOLID -> canvas.fill(x, y, w, h, t.bgVoid());
        }
        // Edge vignette shared by every style.
        int edge = Math.max(6, w / 5);
        canvas.fillGradientH(x, y, edge, h, Colors.withAlpha(t.bgVoid(), 0.45f), Colors.withAlpha(t.bgVoid(), 0f));
        canvas.fillGradientH(x + w - edge, y, edge, h, Colors.withAlpha(t.bgVoid(), 0f),
                Colors.withAlpha(t.bgVoid(), 0.45f));
        menuMock(canvas, r, t);
        canvas.popScissor();
    }

    /** Mark plus three button bars, centred. */
    private static void menuMock(Canvas canvas, Rect r, Theme t) {
        int mark = Math.max(8, Math.min(14, r.h() / 3));
        int cx = r.centerX();
        int top = r.y() + Math.max(3, r.h() / 6);
        VantaMark.draw(canvas, cx - mark / 2, top, mark, t);
        int bw = Math.min(r.w() - 12, 34);
        int by = top + mark + 4;
        for (int i = 0; i < 3 && by + 4 <= r.bottom() - 2; i++) {
            int color = i == 0 ? Colors.withAlpha(t.accent(), 0.85f) : Colors.withAlpha(t.surface3(), 0.9f);
            canvas.fillRounded(cx - bw / 2, by, bw, 4, Theme.RADIUS_SM, color);
            by += 6;
        }
    }

    // ---- HUD themes ---------------------------------------------------------------------------------------------------

    /** A sample "FPS 120" widget drawn with the HUD theme's panel, border, radius, padding and text shadow. */
    public static void hudWidget(Canvas canvas, Rect r, HudTheme theme, Theme t) {
        canvas.pushScissor(r);
        worldBackdrop(canvas, r, 0.55f);
        HudThemeStyle style = theme.style();
        String label = Lang.tr("vanta.cosmetics.sample_fps");
        String value = "120";
        int pad = style.padding();
        int textW = canvas.textWidth(label, FontKind.UI) + 4 + canvas.textWidth(value, FontKind.UI_BOLD);
        int w = textW + pad * 2 + 2;
        int h = 9 + pad * 2;
        int x = r.centerX() - w / 2;
        int y = r.centerY() - h / 2;
        if (style.hasPanel()) {
            if (style.backgroundAlpha() > 0.0) {
                int bg = Colors.withAlpha(HudWidgetState.DEFAULT_BACKGROUND, (float) style.backgroundAlpha());
                canvas.fillRounded(x, y, w, h, style.radius(), bg);
            }
            if (style.border()) {
                int border = theme == HudTheme.OUTLINE
                        ? Colors.withAlpha255(HudWidgetState.DEFAULT_ACCENT, Colors.alpha(style.borderColor()))
                        : style.borderColor();
                canvas.strokeRounded(x, y, w, h, style.radius(), border);
            }
        }
        int tx = x + pad + 1;
        int ty = y + pad;
        int muted = Colors.withAlpha(HudWidgetState.DEFAULT_TEXT, 0.62f);
        canvas.text(label, tx, ty, muted, FontKind.UI, style.textShadow());
        canvas.text(value, tx + canvas.textWidth(label, FontKind.UI) + 4, ty, HudWidgetState.DEFAULT_TEXT,
                FontKind.UI_BOLD, style.textShadow());
        canvas.popScissor();
    }

    // ---- crosshair --------------------------------------------------------------------------------------------------------

    /** The crosshair style centred over a sky / ground backdrop. */
    public static void crosshair(Canvas canvas, Rect r, CrosshairStyle style) {
        canvas.pushScissor(r);
        worldBackdrop(canvas, r, 0.35f);
        int cx = r.centerX();
        int cy = r.centerY();
        for (CrosshairGeometry.Primitive p : CrosshairGeometry.build(style, cx, cy)) {
            if (p instanceof CrosshairGeometry.Rect rect) {
                canvas.fill(rect.x(), rect.y(), rect.width(), rect.height(), rect.argb());
            } else if (p instanceof CrosshairGeometry.Ring ring) {
                ring(canvas, ring.cx(), ring.cy(), ring.radius(), ring.thickness(), ring.argb());
            }
        }
        canvas.popScissor();
    }

    /** Hollow circle drawn as scanline segments. */
    public static void ring(Canvas canvas, int cx, int cy, int radius, int thickness, int argb) {
        int inner = Math.max(0, radius - thickness);
        for (int dy = -radius; dy <= radius; dy++) {
            double outerHalf = Math.sqrt(Math.max(0.0, (double) radius * radius - (double) dy * dy + 0.25));
            int oh = (int) Math.floor(outerHalf);
            if (Math.abs(dy) > inner) {
                canvas.fill(cx - oh, cy + dy, 2 * oh + 1, 1, argb);
                continue;
            }
            double innerHalf = Math.sqrt(Math.max(0.0, (double) inner * inner - (double) dy * dy + 0.25));
            int ih = (int) Math.floor(innerHalf);
            int left = oh - ih;
            if (left <= 0) {
                left = 1;
            }
            canvas.fill(cx - oh, cy + dy, left, 1, argb);
            canvas.fill(cx + ih + 1, cy + dy, left, 1, argb);
        }
    }

    /** Sky over grass over dirt, dimmed by {@code dim} so UI drawn on top stays legible. */
    public static void worldBackdrop(Canvas canvas, Rect r, float dim) {
        int ground = r.y() + Math.round(r.h() * 0.62f);
        canvas.fillGradientV(r.x(), r.y(), r.w(), ground - r.y(), SKY_TOP, SKY_BOTTOM);
        int grassH = Math.max(2, r.h() / 8);
        canvas.fill(r.x(), ground, r.w(), grassH, GRASS);
        canvas.fillGradientV(r.x(), ground + grassH, r.w(), r.bottom() - ground - grassH, DIRT, Colors.darken(DIRT, 0.3f));
        canvas.fill(r.x(), r.y(), r.w(), r.h(), Colors.withAlpha(0xFF000000, dim));
    }

    // ---- particles ----------------------------------------------------------------------------------------------------------

    /** Particles as small soft squares (dim halo one pixel larger than the bright core). */
    public static void particles(Canvas canvas, Rect r, List<ParticleSystem.Particle> particles) {
        canvas.pushScissor(r);
        for (ParticleSystem.Particle p : particles) {
            int size = Math.max(1, (int) Math.round(p.size()));
            int x = r.x() + (int) Math.round(p.x() - size / 2.0);
            int y = r.y() + (int) Math.round(p.y() - size / 2.0);
            int core = Colors.withAlpha(Colors.opaque(p.color()), (float) p.alpha());
            int halo = Colors.withAlpha(Colors.opaque(p.color()), (float) p.alpha() * 0.28f);
            canvas.fill(x - 1, y - 1, size + 2, size + 2, halo);
            canvas.fill(x, y, size, size, core);
        }
        canvas.popScissor();
    }

    // ---- badges -----------------------------------------------------------------------------------------------------------

    /**
     * The badge as a coloured disc with its glyph drawn from primitives (a four-point star, a hexagon, a heart); the
     * {@link Badge#NONE} tile is an empty dashed ring.
     */
    public static void badge(Canvas canvas, Rect r, Badge badge, Theme t) {
        int d = Math.min(r.w(), r.h()) - 6;
        int radius = d / 2;
        int cx = r.centerX();
        int cy = r.centerY();
        if (badge == Badge.NONE) {
            ring(canvas, cx, cy, radius, 1, Colors.withAlpha(t.textMuted(), 0.8f));
            canvas.line(cx - radius / 2, cy + radius / 2, cx + radius / 2, cy - radius / 2,
                    Colors.withAlpha(t.textMuted(), 0.8f));
            return;
        }
        int color = badge.color();
        canvas.circle(cx, cy, radius + 2, Colors.withAlpha(color, 0.22f));
        canvas.circle(cx, cy, radius, color);
        int glyph = Colors.WHITE;
        int g = Math.max(3, radius * 6 / 10);
        switch (badge) {
            case FOUNDER -> {
                // Four-point star: a tall and a wide thin diamond.
                canvas.triangle(cx, cy - g, cx - g / 3, cy, cx + g / 3, cy, glyph);
                canvas.triangle(cx, cy + g, cx - g / 3, cy, cx + g / 3, cy, glyph);
                canvas.triangle(cx - g, cy, cx, cy - g / 3, cx, cy + g / 3, glyph);
                canvas.triangle(cx + g, cy, cx, cy - g / 3, cx, cy + g / 3, glyph);
            }
            case CONTRIBUTOR -> {
                int[] xs = new int[6];
                int[] ys = new int[6];
                for (int i = 0; i < 6; i++) {
                    double angle = Math.toRadians(-90 + i * 60);
                    xs[i] = cx + (int) Math.round(Math.cos(angle) * g);
                    ys[i] = cy + (int) Math.round(Math.sin(angle) * g);
                }
                for (int i = 0; i < 6; i++) {
                    int n = (i + 1) % 6;
                    canvas.triangle(cx, cy, xs[i], ys[i], xs[n], ys[n], glyph);
                }
                int hole = Math.max(1, g / 2);
                for (int i = 0; i < 6; i++) {
                    double a1 = Math.toRadians(-90 + i * 60);
                    double a2 = Math.toRadians(-90 + (i + 1) * 60);
                    canvas.triangle(cx, cy, cx + (int) Math.round(Math.cos(a1) * hole), cy + (int) Math.round(Math.sin(a1) * hole),
                            cx + (int) Math.round(Math.cos(a2) * hole), cy + (int) Math.round(Math.sin(a2) * hole), color);
                }
            }
            case SUPPORTER -> {
                int lobe = Math.max(1, g / 2);
                canvas.circle(cx - lobe + (lobe > 1 ? 0 : 0), cy - lobe / 2, lobe, glyph);
                canvas.circle(cx + lobe, cy - lobe / 2, lobe, glyph);
                canvas.triangle(cx - lobe * 2, cy - lobe / 2 + 1, cx + lobe * 2 + 1, cy - lobe / 2 + 1, cx, cy + g, glyph);
            }
            default -> {
            }
        }
    }
}
