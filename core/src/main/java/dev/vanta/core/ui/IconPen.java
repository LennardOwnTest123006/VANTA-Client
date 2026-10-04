package dev.vanta.core.ui;

/**
 * Tiny vector-ish pen used by {@link Icons}: icons are designed on a 16 x 16 unit grid and rasterised with
 * {@link Canvas#fill} into any pixel size. Strokes scale with the icon size (1 px up to 11 px, 2 px up to 19 px...).
 */
final class IconPen {

    private final Canvas canvas;
    private final int originX;
    private final int originY;
    private final float unit;
    private final int stroke;
    private final int color;

    IconPen(Canvas canvas, int x, int y, int size, int color) {
        this.canvas = canvas;
        this.originX = x;
        this.originY = y;
        this.unit = size / 16f;
        this.stroke = Math.max(1, Math.round(size / 9f));
        this.color = color;
    }

    /** Stroke thickness in pixels. */
    int stroke() {
        return stroke;
    }

    int px(float u) {
        return originX + Math.round(u * unit);
    }

    int py(float v) {
        return originY + Math.round(v * unit);
    }

    /** Filled rectangle in grid units (at least 1 px in each dimension). */
    void rect(float u, float v, float w, float h) {
        int x1 = px(u);
        int y1 = py(v);
        int x2 = px(u + w);
        int y2 = py(v + h);
        canvas.fill(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1), color);
    }

    /** Rectangle outline in grid units with the pen's stroke. */
    void rectOutline(float u, float v, float w, float h) {
        int x1 = px(u);
        int y1 = py(v);
        int x2 = px(u + w);
        int y2 = py(v + h);
        canvas.strokeRect(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1), stroke, color);
    }

    /** Horizontal stroke centred on {@code v} from {@code u1} to {@code u2}. */
    void hline(float u1, float u2, float v) {
        int x1 = px(Math.min(u1, u2));
        int x2 = px(Math.max(u1, u2));
        int y = py(v) - stroke / 2;
        canvas.fill(x1, y, Math.max(1, x2 - x1), stroke, color);
    }

    /** Vertical stroke centred on {@code u} from {@code v1} to {@code v2}. */
    void vline(float u, float v1, float v2) {
        int y1 = py(Math.min(v1, v2));
        int y2 = py(Math.max(v1, v2));
        int x = px(u) - stroke / 2;
        canvas.fill(x, y1, stroke, Math.max(1, y2 - y1), color);
    }

    /** Any-angle stroke rasterised as a chain of stroke-sized squares. */
    void line(float u1, float v1, float u2, float v2) {
        int x1 = px(u1);
        int y1 = py(v1);
        int x2 = px(u2);
        int y2 = py(v2);
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        int half = stroke / 2;
        if (steps == 0) {
            canvas.fill(x1 - half, y1 - half, stroke, stroke, color);
            return;
        }
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            int x = Math.round(x1 + (x2 - x1) * t);
            int y = Math.round(y1 + (y2 - y1) * t);
            if (x == lastX && y == lastY) {
                continue;
            }
            canvas.fill(x - half, y - half, stroke, stroke, color);
            lastX = x;
            lastY = y;
        }
    }

    /** Filled disc. */
    void disc(float cu, float cv, float r) {
        int cx = px(cu);
        int cy = py(cv);
        int radius = Math.max(0, Math.round(r * unit) - 1);
        canvas.circle(cx, cy, radius, color);
    }

    /** Circle outline with the pen's stroke (midpoint algorithm; stroke squares at each point). */
    void ring(float cu, float cv, float r) {
        arc(cu, cv, r, 0f, 360f);
    }

    /**
     * Arc outline from {@code fromDeg} to {@code toDeg} (degrees, clockwise from +x in screen space where +y is
     * down). Covers the full circle when the span is 360 or more.
     */
    void arc(float cu, float cv, float r, float fromDeg, float toDeg) {
        float cx = cu * unit;
        float cy = cv * unit;
        float radius = Math.max(0.5f, r * unit - stroke / 2f);
        int half = stroke / 2;
        int samples = Math.max(12, Math.round(radius * 8f));
        float span = toDeg - fromDeg;
        boolean full = span >= 360f;
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        for (int i = 0; i <= samples; i++) {
            float t = (float) i / samples;
            float deg = full ? 360f * t : fromDeg + span * t;
            double rad = Math.toRadians(deg);
            int x = originX + Math.round((float) (cx + Math.cos(rad) * radius));
            int y = originY + Math.round((float) (cy + Math.sin(rad) * radius));
            if (x == lastX && y == lastY) {
                continue;
            }
            canvas.fill(x - half, y - half, stroke, stroke, color);
            lastX = x;
            lastY = y;
        }
    }

    /** Filled triangle in grid units. */
    void triangle(float u1, float v1, float u2, float v2, float u3, float v3) {
        canvas.triangle(px(u1), py(v1), px(u2), py(v2), px(u3), py(v3), color);
    }
}
