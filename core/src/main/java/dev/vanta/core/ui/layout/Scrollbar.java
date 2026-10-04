package dev.vanta.core.ui.layout;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Theme;

/**
 * Geometry and painting of the thin vertical scrollbar shared by {@link ScrollPanel}, list views and dropdowns.
 * Pure functions of (track, content height, scroll offset) so they can be unit tested.
 */
public final class Scrollbar {

    /** Thumb/track width in pixels. */
    public static final int WIDTH = 3;
    /** Space reserved between content and the bar. */
    public static final int GUTTER = 3;
    /** Smallest thumb height. */
    public static final int MIN_THUMB = 12;
    /** Pixels scrolled per wheel notch. */
    public static final int WHEEL_STEP = 24;

    private Scrollbar() {
    }

    /** Total horizontal space the bar needs (gutter + width). */
    public static int reservedWidth() {
        return WIDTH + GUTTER;
    }

    /** Whether the content overflows the viewport. */
    public static boolean needed(int viewportHeight, int contentHeight) {
        return contentHeight > viewportHeight && viewportHeight > 0;
    }

    /** Largest valid scroll offset. */
    public static float maxScroll(int viewportHeight, int contentHeight) {
        return Math.max(0f, contentHeight - viewportHeight);
    }

    /** Clamps an offset to the valid range. */
    public static float clamp(float scroll, int viewportHeight, int contentHeight) {
        return Math.max(0f, Math.min(maxScroll(viewportHeight, contentHeight), scroll));
    }

    /** The track rectangle at the right edge of a viewport. */
    public static Rect track(Rect viewport) {
        return new Rect(viewport.right() - WIDTH, viewport.y(), WIDTH, viewport.h());
    }

    /** The thumb rectangle for the given state (track-relative geometry, absolute coordinates). */
    public static Rect thumb(Rect track, int contentHeight, float scroll) {
        if (contentHeight <= track.h() || track.h() <= 0) {
            return track;
        }
        int thumbH = Math.max(MIN_THUMB, Math.round((float) track.h() * track.h() / contentHeight));
        thumbH = Math.min(thumbH, track.h());
        float max = maxScroll(track.h(), contentHeight);
        float t = max <= 0f ? 0f : Math.max(0f, Math.min(1f, scroll / max));
        int y = track.y() + Math.round((track.h() - thumbH) * t);
        return new Rect(track.x(), y, track.w(), thumbH);
    }

    /**
     * Scroll offset for a thumb whose top edge is at {@code thumbTop}.
     */
    public static float scrollForThumbTop(Rect track, int contentHeight, int thumbTop) {
        Rect thumb = thumb(track, contentHeight, 0f);
        int range = track.h() - thumb.h();
        if (range <= 0) {
            return 0f;
        }
        float t = (float) (thumbTop - track.y()) / range;
        t = Math.max(0f, Math.min(1f, t));
        return t * maxScroll(track.h(), contentHeight);
    }

    /** Paints track and thumb. */
    public static void render(Canvas canvas, Theme theme, Rect track, int contentHeight, float scroll, boolean active) {
        if (!needed(track.h(), contentHeight)) {
            return;
        }
        canvas.fillRounded(track.x(), track.y(), track.w(), track.h(), 1, theme.surface3());
        Rect thumb = thumb(track, contentHeight, scroll);
        canvas.fillRounded(thumb.x(), thumb.y(), thumb.w(), thumb.h(), 1,
                active ? theme.scrollThumbActive() : theme.scrollThumb());
    }
}
