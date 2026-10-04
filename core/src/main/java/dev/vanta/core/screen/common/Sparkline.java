package dev.vanta.core.screen.common;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import java.util.Arrays;

/**
 * Tiny area chart of a rolling series (frame rate, memory, …): the newest sample sits at the right edge, the area
 * under the line is tinted, and an optional dashed reference line marks a target value. The vertical range adapts
 * to the data (and the reference) with a small margin so the line never touches the frame.
 * <p>
 * Pure fills, so it renders identically in-game and in the Java2D previews.
 */
public class Sparkline extends UiNode {
    /** Default size. */
    public static final int DEFAULT_W = 96;
    public static final int DEFAULT_H = 26;

    private float[] values = new float[0];
    private Float reference;
    private Integer lineColor;
    private boolean baselineAtZero = true;

    /** Empty sparkline. */
    public Sparkline() {
    }

    /** Replaces the series (oldest first). */
    public Sparkline setValues(float[] series) {
        this.values = series == null ? new float[0] : series.clone();
        return this;
    }

    /** Replaces the series from ints (oldest first). */
    public Sparkline setValues(int[] series) {
        if (series == null) {
            values = new float[0];
            return this;
        }
        float[] out = new float[series.length];
        for (int i = 0; i < series.length; i++) {
            out[i] = series[i];
        }
        values = out;
        return this;
    }

    /** Horizontal reference line (for example the target frame rate); {@code null} hides it. */
    public Sparkline reference(Float value) {
        this.reference = value;
        return this;
    }

    /** Line colour override (defaults to the accent). */
    public Sparkline lineColor(Integer argb) {
        this.lineColor = argb;
        return this;
    }

    /** Whether the range always includes zero (default) or hugs the data. */
    public Sparkline baselineAtZero(boolean on) {
        this.baselineAtZero = on;
        return this;
    }

    /** Current series copy. */
    public float[] values() {
        return values.clone();
    }

    /** Lower bound of the drawn range. */
    public float rangeMin() {
        return range()[0];
    }

    /** Upper bound of the drawn range. */
    public float rangeMax() {
        return range()[1];
    }

    private float[] range() {
        float lo = baselineAtZero ? 0f : Float.POSITIVE_INFINITY;
        float hi = Float.NEGATIVE_INFINITY;
        for (float v : values) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        if (reference != null) {
            lo = Math.min(lo, reference);
            hi = Math.max(hi, reference);
        }
        if (values.length == 0 && reference == null) {
            return new float[] {0f, 1f};
        }
        if (Float.isInfinite(lo)) {
            lo = hi;
        }
        if (hi <= lo) {
            hi = lo + 1f;
        }
        float margin = (hi - lo) * 0.12f;
        return new float[] {baselineAtZero && lo == 0f ? 0f : lo - margin, hi + margin};
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(DEFAULT_W, DEFAULT_H);
    }

    private int yFor(float value, Rect r, float lo, float hi) {
        float t = (value - lo) / (hi - lo);
        t = Math.max(0f, Math.min(1f, t));
        return r.bottom() - 1 - Math.round(t * (r.h() - 1));
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect r = bounds();
        if (r.w() <= 1 || r.h() <= 1) {
            return;
        }
        canvas.fillRounded(r.x(), r.y(), r.w(), r.h(), Theme.RADIUS_SM, Colors.withAlpha(theme.bgVoid(), 0.35f));
        float[] range = range();
        float lo = range[0];
        float hi = range[1];
        int line = lineColor != null ? lineColor : theme.accentHover();
        if (reference != null) {
            int ry = yFor(reference, r, lo, hi);
            int dash = Colors.withAlpha(theme.textMuted(), 0.7f);
            for (int x = r.x(); x < r.right(); x += 4) {
                canvas.fill(x, ry, Math.min(2, r.right() - x), 1, dash);
            }
        }
        int n = values.length;
        if (n == 0) {
            return;
        }
        int w = r.w();
        int baseline = r.bottom() - 1;
        int previousY = -1;
        for (int col = 0; col < w; col++) {
            // Map the column onto the series (newest at the right edge).
            float pos = n == 1 ? 0f : (float) col / (w - 1) * (n - 1);
            int i0 = (int) Math.floor(pos);
            int i1 = Math.min(n - 1, i0 + 1);
            float f = pos - i0;
            float v = values[i0] + (values[i1] - values[i0]) * f;
            int y = yFor(v, r, lo, hi);
            int x = r.x() + col;
            if (y < baseline) {
                canvas.fillGradientV(x, y + 1, 1, baseline - y, Colors.withAlpha(line, 0.30f),
                        Colors.withAlpha(line, 0.04f));
            }
            if (previousY >= 0 && Math.abs(previousY - y) > 1) {
                int top = Math.min(previousY, y);
                int bottom = Math.max(previousY, y);
                canvas.fill(x, top, 1, bottom - top + 1, line);
            } else {
                canvas.fill(x, y, 1, 1, line);
            }
            previousY = y;
        }
        canvas.strokeRounded(r.x(), r.y(), r.w(), r.h(), Theme.RADIUS_SM, Colors.withAlpha(theme.borderSubtle(), 0.8f));
    }

    @Override
    public String toString() {
        return "Sparkline" + Arrays.toString(values);
    }
}
