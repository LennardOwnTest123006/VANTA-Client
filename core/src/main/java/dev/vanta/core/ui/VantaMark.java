package dev.vanta.core.ui;

/**
 * Draws the VANTA mark (the geometric V with the diamond cut-out from {@code assets/brand/vanta-mark.svg}) with
 * plain fills so it renders identically on every canvas. The SVG geometry lives on a 64-unit grid.
 */
public final class VantaMark {

    private static final float GRID = 64f;

    private VantaMark() {
    }

    /** Draws the mark with the accent gradient of the theme. */
    public static void draw(Canvas canvas, int x, int y, int size, Theme theme) {
        draw(canvas, x, y, size, theme.gradientStart(), theme.gradientEnd());
    }

    /** Draws the mark in one color. */
    public static void draw(Canvas canvas, int x, int y, int size, int argb) {
        draw(canvas, x, y, size, argb, argb);
    }

    /**
     * Draws the mark with a diagonal gradient from {@code top} (top-left) to {@code bottom} (bottom-right).
     * Rendering is scanline based; every row's spans are computed from the SVG polygon edges.
     */
    public static void draw(Canvas canvas, int x, int y, int size, int top, int bottom) {
        if (size <= 0) {
            return;
        }
        float unit = size / GRID;
        for (int row = 0; row < size; row++) {
            float v = (row + 0.5f) / unit; // SVG y at the center of this pixel row
            if (v < 9f || v > 58f) {
                continue;
            }
            // Outer V edges: (5,9)->(32,58) and (59,9)->(32,58)
            float outerSlope = 27f / 49f;
            float outerLeft = 5f + (v - 9f) * outerSlope;
            float outerRight = 59f - (v - 9f) * outerSlope;
            // Inner notch edges: (19.5,9)->(32,36.4) and (44.5,9)->(32,36.4)
            float innerLeft;
            float innerRight;
            boolean hasNotch = v < 36.4f;
            if (hasNotch) {
                float innerSlope = 12.5f / 27.4f;
                innerLeft = 19.5f + (v - 9f) * innerSlope;
                innerRight = 44.5f - (v - 9f) * innerSlope;
            } else {
                innerLeft = innerRight = 0f;
            }
            float t = (row + 0.5f) / size;
            int color = Colors.lerp(top, bottom, t);
            if (hasNotch) {
                span(canvas, x, y + row, outerLeft, innerLeft, unit, color);
                span(canvas, x, y + row, innerRight, outerRight, unit, color);
            } else {
                // Diamond cut-out: (32,31.6) (36.6,40.2) (33.2,51.6) (28.4,42.2)
                float[] hole = diamondSpan(v);
                if (hole == null) {
                    span(canvas, x, y + row, outerLeft, outerRight, unit, color);
                } else {
                    span(canvas, x, y + row, outerLeft, hole[0], unit, color);
                    span(canvas, x, y + row, hole[1], outerRight, unit, color);
                }
            }
        }
    }

    /** Horizontal extent of the diamond at SVG row {@code v}, or {@code null} when the row misses it. */
    private static float[] diamondSpan(float v) {
        if (v <= 31.6f || v >= 51.6f) {
            return null;
        }
        float left;
        float right;
        // Left side: (32,31.6)->(28.4,42.2) then (28.4,42.2)->(33.2,51.6)
        if (v <= 42.2f) {
            left = 32f + (v - 31.6f) * ((28.4f - 32f) / (42.2f - 31.6f));
        } else {
            left = 28.4f + (v - 42.2f) * ((33.2f - 28.4f) / (51.6f - 42.2f));
        }
        // Right side: (32,31.6)->(36.6,40.2) then (36.6,40.2)->(33.2,51.6)
        if (v <= 40.2f) {
            right = 32f + (v - 31.6f) * ((36.6f - 32f) / (40.2f - 31.6f));
        } else {
            right = 36.6f + (v - 40.2f) * ((33.2f - 36.6f) / (51.6f - 40.2f));
        }
        if (right - left < 0.5f) {
            return null;
        }
        return new float[] {left, right};
    }

    private static void span(Canvas canvas, int x, int y, float fromSvg, float toSvg, float unit, int color) {
        int from = Math.round(fromSvg * unit);
        int to = Math.round(toSvg * unit);
        if (to <= from) {
            if (toSvg - fromSvg > 0.35f) {
                canvas.fill(x + from, y, 1, 1, color);
            }
            return;
        }
        canvas.fill(x + from, y, to - from, 1, color);
    }
}
