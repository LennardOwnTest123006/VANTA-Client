package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Theme;

/** Shared paint recipes so every widget draws focus rings and disabled states the same way. */
final class WidgetPaint {

    /** Alpha multiplier applied to disabled widgets. */
    static final float DISABLED_ALPHA = 0.45f;

    private WidgetPaint() {
    }

    /** 1 px focus ring with a 1 px gap around {@code r}. */
    static void focusRing(Canvas canvas, Theme theme, Rect r, int radius) {
        Rect ring = r.expand(2);
        canvas.strokeRounded(ring.x(), ring.y(), ring.w(), ring.h(), Math.min(Canvas.MAX_RADIUS, radius + 1),
                Colors.withAlpha(theme.borderFocus(), 0.9f));
    }

    /** Runs {@code body} with the canvas alpha multiplied for a disabled look. */
    static void disabled(Canvas canvas, boolean isDisabled, Runnable body) {
        if (!isDisabled) {
            body.run();
            return;
        }
        float previous = canvas.partialAlpha();
        canvas.setPartialAlpha(previous * DISABLED_ALPHA);
        try {
            body.run();
        } finally {
            canvas.setPartialAlpha(previous);
        }
    }

    /** Top y for a single text line vertically centered in a box. */
    static int textY(Rect box, int lineHeight) {
        return box.y() + (box.h() - lineHeight) / 2;
    }
}
