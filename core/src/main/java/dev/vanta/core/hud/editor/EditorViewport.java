package dev.vanta.core.hud.editor;

import dev.vanta.core.hud.HudRect;
import dev.vanta.core.ui.Rect;

import java.util.Objects;

/**
 * Maps the game screen (host GUI pixels, the coordinate space of {@link dev.vanta.core.hud.HudLayout}) into the
 * free area of the editor (logical screen pixels). The whole HUD is always visible: when panels take space the
 * screen is shown as a proportionally scaled miniature, never cropped and never upscaled.
 *
 * @param area       rectangle of the editor (logical pixels) the game screen is drawn into
 * @param hostWidth  game screen width in GUI pixels
 * @param hostHeight game screen height in GUI pixels
 * @param scale      logical pixels per host pixel ({@code <= 1})
 */
public record EditorViewport(Rect area, int hostWidth, int hostHeight, float scale) {

    /** Smallest viewport the editor will produce. */
    public static final int MIN_SIDE = 16;

    public EditorViewport {
        Objects.requireNonNull(area, "area");
        if (hostWidth <= 0 || hostHeight <= 0) {
            throw new IllegalArgumentException("host size must be positive");
        }
        if (!(scale > 0f)) {
            throw new IllegalArgumentException("scale must be positive");
        }
    }

    /**
     * Fits a {@code hostWidth x hostHeight} screen into {@code free}, centred, keeping the aspect ratio and never
     * scaling above 1.
     */
    public static EditorViewport fit(Rect free, int hostWidth, int hostHeight) {
        int hw = Math.max(1, hostWidth);
        int hh = Math.max(1, hostHeight);
        int availW = Math.max(MIN_SIDE, free.w());
        int availH = Math.max(MIN_SIDE, free.h());
        float s = Math.min(1f, Math.min(availW / (float) hw, availH / (float) hh));
        int w = Math.max(1, Math.round(hw * s));
        int h = Math.max(1, Math.round(hh * s));
        int x = free.x() + (free.w() - w) / 2;
        int y = free.y() + (free.h() - h) / 2;
        return new EditorViewport(new Rect(x, y, w, h), hw, hh, s);
    }

    /** Whether the screen is shown at its real size. */
    public boolean isExact() {
        return scale >= 0.999f;
    }

    /** Host x for a logical x (floored; may lie outside the screen). */
    public int toHostX(double logicalX) {
        return (int) Math.floor((logicalX - area.x()) / scale);
    }

    /** Host y for a logical y (floored; may lie outside the screen). */
    public int toHostY(double logicalY) {
        return (int) Math.floor((logicalY - area.y()) / scale);
    }

    /** Logical x of a host x. */
    public int toLogicalX(int hostX) {
        return area.x() + Math.round(hostX * scale);
    }

    /** Logical y of a host y. */
    public int toLogicalY(int hostY) {
        return area.y() + Math.round(hostY * scale);
    }

    /** Logical rectangle covering a host rectangle (at least 1x1). */
    public Rect toLogical(HudRect r) {
        int x1 = toLogicalX(r.x());
        int y1 = toLogicalY(r.y());
        int x2 = toLogicalX(r.right());
        int y2 = toLogicalY(r.bottom());
        return new Rect(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1));
    }

    /** Whether a logical point lies inside the drawn screen. */
    public boolean contains(double logicalX, double logicalY) {
        return area.contains(logicalX, logicalY);
    }

    /** Whether a host point lies inside the game screen. */
    public boolean containsHost(int hostX, int hostY) {
        return hostX >= 0 && hostY >= 0 && hostX < hostWidth && hostY < hostHeight;
    }
}
