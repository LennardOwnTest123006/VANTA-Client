package dev.vanta.core.crosshair;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a {@link CrosshairStyle} into axis-aligned rectangles and rings around a centre point so the renderer only
 * has to fill shapes. Outline primitives come first (drawn behind), then the fill primitives.
 */
public final class CrosshairGeometry {

    /** A drawable primitive. */
    public sealed interface Primitive permits Rect, Ring {
        /** ARGB colour with opacity applied. */
        int argb();
    }

    /** Filled rectangle. */
    public record Rect(int x, int y, int width, int height, int argb) implements Primitive {
        /** Copy grown by {@code amount} on all sides with another colour. */
        Rect outline(int amount, int color) {
            return new Rect(x - amount, y - amount, width + 2 * amount, height + 2 * amount, color);
        }
    }

    /**
     * Ring (hollow circle).
     *
     * @param cx        centre x
     * @param cy        centre y
     * @param radius    outer radius
     * @param thickness ring width (radius − thickness = inner radius)
     */
    public record Ring(int cx, int cy, int radius, int thickness, int argb) implements Primitive {
    }

    private CrosshairGeometry() {
    }

    /** Primitives for the style centred at {@code (cx, cy)} without dynamic expansion. */
    public static List<Primitive> build(CrosshairStyle style, int cx, int cy) {
        return build(style, cx, cy, 0);
    }

    /**
     * Primitives for the style centred at {@code (cx, cy)}.
     *
     * @param expansion extra gap in pixels (dynamic crosshair while moving/attacking); ignored unless
     *                  {@link CrosshairStyle#dynamic()} is set
     */
    public static List<Primitive> build(CrosshairStyle style, int cx, int cy, int expansion) {
        int fill = applyOpacity(style.color(), style.opacity());
        int outlineColor = applyOpacity(style.outlineColor(), style.opacity());
        int gap = style.gap() + (style.dynamic() ? Math.max(0, expansion) : 0);
        int t = style.thickness();
        int half = t / 2;
        int s = style.size();
        List<Rect> rects = new ArrayList<>();
        List<Ring> rings = new ArrayList<>();
        switch (style.shape()) {
            case CROSS -> addCross(rects, cx, cy, s, t, half, gap, fill);
            case PLUS_DOT -> {
                addCross(rects, cx, cy, s, t, half, gap, fill);
                rects.add(new Rect(cx - half, cy - half, t, t, fill));
            }
            case DOT -> rects.add(new Rect(cx - s / 2, cy - s / 2, s, s, fill));
            case CIRCLE -> rings.add(new Ring(cx, cy, s + gap, t, fill));
            case SQUARE -> {
                int r = s + gap;
                rects.add(new Rect(cx - r, cy - r, 2 * r, t, fill));
                rects.add(new Rect(cx - r, cy + r - t, 2 * r, t, fill));
                rects.add(new Rect(cx - r, cy - r + t, t, 2 * r - 2 * t, fill));
                rects.add(new Rect(cx + r - t, cy - r + t, t, 2 * r - 2 * t, fill));
            }
            case CHEVRON -> {
                // Two stair-stepped arms below the centre forming a "^"; the centre column stays empty and the
                // arms mirror each other pixel-for-pixel around cx.
                for (int i = 0; i < s; i++) {
                    int y = cy + gap + i;
                    rects.add(new Rect(cx - gap - i - t, y, t, t, fill));
                    rects.add(new Rect(cx + gap + 1 + i, y, t, t, fill));
                }
            }
        }
        List<Primitive> out = new ArrayList<>();
        if (style.outline()) {
            int o = style.outlineThickness();
            for (Rect rect : rects) {
                out.add(rect.outline(o, outlineColor));
            }
            for (Ring ring : rings) {
                out.add(new Ring(ring.cx(), ring.cy(), ring.radius() + o, ring.thickness() + 2 * o, outlineColor));
            }
        }
        out.addAll(rects);
        out.addAll(rings);
        return out;
    }

    /**
     * Four arms around the centre block. The centre block is the {@code t × t} square at {@code (cx - t/2, cy - t/2)};
     * each arm starts {@code gap} pixels away from it, so with gap 0 the arms touch the (empty) centre.
     */
    private static void addCross(List<Rect> rects, int cx, int cy, int s, int t, int half, int gap, int fill) {
        int left = cx - half;
        int top = cy - half;
        rects.add(new Rect(left, top - gap - s, t, s, fill));
        rects.add(new Rect(left, top + t + gap, t, s, fill));
        rects.add(new Rect(left - gap - s, top, s, t, fill));
        rects.add(new Rect(left + t + gap, top, s, t, fill));
    }

    /** Bounding box of a primitive list (empty list → zero-size box at origin). */
    public static Rect bounds(List<Primitive> primitives) {
        if (primitives.isEmpty()) {
            return new Rect(0, 0, 0, 0, 0);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Primitive p : primitives) {
            int x0;
            int y0;
            int x1;
            int y1;
            if (p instanceof Rect r) {
                x0 = r.x();
                y0 = r.y();
                x1 = r.x() + r.width();
                y1 = r.y() + r.height();
            } else {
                Ring ring = (Ring) p;
                x0 = ring.cx() - ring.radius();
                y0 = ring.cy() - ring.radius();
                x1 = ring.cx() + ring.radius();
                y1 = ring.cy() + ring.radius();
            }
            minX = Math.min(minX, x0);
            minY = Math.min(minY, y0);
            maxX = Math.max(maxX, x1);
            maxY = Math.max(maxY, y1);
        }
        return new Rect(minX, minY, maxX - minX, maxY - minY, 0);
    }

    /** Multiplies the alpha channel of an ARGB colour. */
    public static int applyOpacity(int argb, double opacity) {
        int alpha = (argb >>> 24) & 0xFF;
        int scaled = (int) Math.round(alpha * Math.max(0.0, Math.min(1.0, opacity)));
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }
}
