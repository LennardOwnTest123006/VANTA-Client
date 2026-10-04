package dev.vanta.core.crosshair.editor;

import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.crosshair.render.CrosshairRenderer;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

import java.util.List;
import java.util.function.Supplier;

/**
 * Four preview tiles of the current crosshair: a dark scene, a bright scene, a noisy grass-like scene and a 3x
 * magnification. Dynamic styles pulse their expansion so the effect is visible without a world (static under
 * reduced motion).
 */
public final class CrosshairPreviewStrip extends UiNode {

    /** Tile width. */
    public static final int TILE_W = 64;
    /** Tile height. */
    public static final int TILE_H = 44;
    /** Gap between tiles. */
    public static final int GAP = Theme.SPACE_3;
    /** Magnification of the last tile. */
    public static final int ZOOM = 3;
    /** Largest pulsing expansion in pixels. */
    public static final int PULSE_PX = 3;
    private static final long PULSE_PERIOD_MS = 1600L;

    private static final List<String> LABEL_KEYS = List.of("vanta.crosshair.preview.dark",
            "vanta.crosshair.preview.light", "vanta.crosshair.preview.noisy", "vanta.crosshair.preview.zoom");

    private final Supplier<CrosshairStyle> style;

    /** Strip reading the style from {@code style} every frame. */
    public CrosshairPreviewStrip(Supplier<CrosshairStyle> style) {
        this.style = style;
        setId("crosshair.preview");
    }

    /** Number of tiles. */
    public int tileCount() {
        return LABEL_KEYS.size();
    }

    /** Rectangle of tile {@code index} (0 dark, 1 light, 2 noisy, 3 zoom). Tiles wrap onto a second row when narrow. */
    public Rect tileRect(int index) {
        Rect b = bounds();
        int perRow = Math.max(1, Math.min(tileCount(), (b.w() + GAP) / (TILE_W + GAP)));
        int row = index / perRow;
        int column = index % perRow;
        int rowH = TILE_H + FontKind.UI.lineHeight() + Theme.SPACE_2;
        return new Rect(b.x() + column * (TILE_W + GAP), b.y() + row * rowH, TILE_W, TILE_H);
    }

    /** Current pulse expansion for dynamic styles (0 when static or under reduced motion). */
    public int pulseExpansion(UiContext ctx) {
        if (!style.get().dynamic()) {
            return 0;
        }
        float phase = ctx.animator().phase(PULSE_PERIOD_MS);
        float tri = phase < 0.5f ? phase * 2f : (1f - phase) * 2f;
        return Math.round(tri * PULSE_PX);
    }

    @Override
    protected Size measure(UiContext ctx) {
        int width = bounds().w() > 0 ? bounds().w() : tileCount() * (TILE_W + GAP) - GAP;
        int perRow = Math.max(1, Math.min(tileCount(), (width + GAP) / (TILE_W + GAP)));
        int rows = (tileCount() + perRow - 1) / perRow;
        int rowH = TILE_H + ctx.lineHeight(FontKind.UI) + Theme.SPACE_2;
        return new Size(Math.min(width, tileCount() * (TILE_W + GAP) - GAP), rows * rowH - Theme.SPACE_2);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        CrosshairStyle current = style.get();
        int expansion = pulseExpansion(ctx);
        for (int i = 0; i < tileCount(); i++) {
            Rect tile = tileRect(i);
            drawBackground(canvas, i, tile);
            canvas.pushScissor(tile);
            if (i == tileCount() - 1) {
                canvas.pushTranslate(tile.centerX(), tile.centerY());
                canvas.pushScale(ZOOM, ZOOM);
                CrosshairRenderer.draw(canvas, current, 0, 0, expansion);
                canvas.pop();
                canvas.pop();
            } else {
                CrosshairRenderer.draw(canvas, current, tile.centerX(), tile.centerY(), expansion);
            }
            canvas.popScissor();
            canvas.strokeRounded(tile.x(), tile.y(), tile.w(), tile.h(), Theme.RADIUS_MD, theme.borderStrong());
            String label = Lang.tr(LABEL_KEYS.get(i));
            canvas.textCentered(canvas.textClipped(label, tile.w(), FontKind.UI), tile.centerX(),
                    tile.bottom() + Theme.SPACE_1, theme.textMuted(), FontKind.UI, false);
        }
    }

    private static void drawBackground(Canvas canvas, int index, Rect t) {
        switch (index) {
            case 0 -> canvas.fillGradientV(t.x(), t.y(), t.w(), t.h(), 0xFF2B3140, 0xFF12151C);
            case 1 -> canvas.fillGradientV(t.x(), t.y(), t.w(), t.h(), 0xFFF2F6FA, 0xFFB7C3D2);
            case 2 -> drawNoise(canvas, t);
            default -> {
                canvas.fill(t.x(), t.y(), t.w(), t.h(), 0xFF1C2230);
                int cell = ZOOM;
                int c = Colors.withAlpha(0xFFFFFFFF, 0.05f);
                for (int y = t.y(); y < t.bottom(); y += cell * 2) {
                    canvas.fill(t.x(), y, t.w(), 1, c);
                }
                for (int x = t.x(); x < t.right(); x += cell * 2) {
                    canvas.fill(x, t.y(), 1, t.h(), c);
                }
            }
        }
    }

    /** Deterministic grass-and-dirt noise: 4 px cells coloured from a hash so every frame looks the same. */
    private static void drawNoise(Canvas canvas, Rect t) {
        int cell = 4;
        int[] palette = {0xFF4F7A33, 0xFF5B8A3A, 0xFF446B2C, 0xFF6B5A3E, 0xFF7B6A48, 0xFF3F5F27, 0xFF8AA35A};
        for (int y = 0; y < t.h(); y += cell) {
            for (int x = 0; x < t.w(); x += cell) {
                int hash = (x * 73856093) ^ (y * 19349663);
                hash ^= hash >>> 13;
                hash *= 0x5bd1e995;
                hash ^= hash >>> 15;
                int color = palette[Math.floorMod(hash, palette.length)];
                canvas.fill(t.x() + x, t.y() + y, Math.min(cell, t.w() - x), Math.min(cell, t.h() - y), color);
            }
        }
    }
}
