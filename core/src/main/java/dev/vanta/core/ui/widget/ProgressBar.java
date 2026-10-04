package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.AnimatedValue;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

/**
 * Thin progress line. Determinate mode animates towards the set fraction; indeterminate mode sweeps a segment
 * across the track (static under reduced motion).
 */
public class ProgressBar extends UiNode {

    /** Default thickness. */
    public static final int HEIGHT = 4;

    private float fraction;
    private boolean indeterminate;
    private Integer color;
    private AnimatedValue shown;

    /** Determinate bar at {@code fraction} (0..1). */
    public ProgressBar(float fraction) {
        this.fraction = clamp(fraction);
    }

    /** Indeterminate bar. */
    public static ProgressBar indeterminate() {
        ProgressBar bar = new ProgressBar(0f);
        bar.indeterminate = true;
        return bar;
    }

    public float fraction() {
        return fraction;
    }

    /** Sets the target fraction (animated). */
    public ProgressBar setFraction(float f) {
        this.fraction = clamp(f);
        this.indeterminate = false;
        return this;
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }

    /** Fill color override (defaults to the accent gradient). */
    public ProgressBar color(Integer argb) {
        this.color = argb;
        return this;
    }

    private static float clamp(float f) {
        return f < 0f ? 0f : Math.min(1f, f);
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(80, HEIGHT);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        int h = Math.min(b.h(), HEIGHT);
        int y = b.y() + (b.h() - h) / 2;
        int radius = Math.min(Theme.RADIUS_SM, h / 2);
        canvas.fillRounded(b.x(), y, b.w(), h, radius, theme.surface3());
        if (indeterminate) {
            float phase = ctx.animator().phase(1400L);
            int segment = Math.max(8, b.w() / 3);
            int travel = b.w() + segment;
            int x = b.x() - segment + Math.round(phase * travel);
            int left = Math.max(b.x(), x);
            int right = Math.min(b.right(), x + segment);
            if (right > left) {
                int c0 = color != null ? color : theme.gradientStart();
                int c1 = color != null ? color : theme.gradientEnd();
                canvas.fillGradientH(left, y, right - left, h, c0, c1);
            }
            return;
        }
        if (shown == null) {
            shown = ctx.animator().value(fraction);
        }
        shown.animateTo(fraction, Theme.MOTION_BASE);
        int fill = Math.round(b.w() * shown.get());
        if (fill > 0) {
            if (color != null) {
                canvas.fillRounded(b.x(), y, fill, h, radius, color);
            } else {
                canvas.fillRoundedGradientH(b.x(), y, fill, h, radius, theme.gradientStart(),
                        Colors.lerp(theme.gradientStart(), theme.gradientEnd(), shown.get()));
            }
        }
    }
}
