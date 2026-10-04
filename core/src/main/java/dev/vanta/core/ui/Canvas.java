package dev.vanta.core.ui;

/**
 * Immediate-mode drawing surface in GUI-scaled pixel coordinates. The client implements it on top of
 * {@code GuiGraphics}; the preview module implements it with Java2D; tests use a recording canvas.
 * <p>
 * Colors are packed ARGB. Every drawing call is subject to the current scissor stack, transform stack and
 * {@link #partialAlpha()} multiplier. Default methods compose the richer shapes out of {@link #fill} so an
 * implementation only needs the small set of abstract primitives.
 */
public interface Canvas extends TextMetrics {

    /** Largest radius {@link #fillRounded} honours; bigger values are clamped. */
    int MAX_RADIUS = 4;

    // ---------------------------------------------------------------- surface

    /** Width of the surface in GUI pixels. */
    int width();

    /** Height of the surface in GUI pixels. */
    int height();

    // ---------------------------------------------------------------- primitives

    /** Fills an axis-aligned rectangle. Zero or negative sizes draw nothing. */
    void fill(int x, int y, int w, int h, int argb);

    /** Draws text with its top-left corner at (x, y); shadow adds Minecraft's 1px offset copy. */
    void text(String text, int x, int y, int argb, FontKind font, boolean shadow);

    /**
     * Draws a sub-rectangle of a texture stretched into {@code (x, y, w, h)}.
     *
     * @param u    left edge of the source region in texture pixels
     * @param v    top edge of the source region in texture pixels
     * @param uw   width of the source region in texture pixels
     * @param vh   height of the source region in texture pixels
     * @param texW full texture width
     * @param texH full texture height
     */
    void image(TextureRef texture, int x, int y, int w, int h, float u, float v, float uw, float vh, int texW, int texH);

    /** Pushes a clip rectangle; nested calls intersect with the enclosing clip. */
    void pushScissor(Rect clip);

    /** Pops the innermost clip rectangle. */
    void popScissor();

    /** Pushes a translation (in current GUI units). Pop with {@link #pop()}. */
    void pushTranslate(float dx, float dy);

    /** Pushes a scale around the current origin. Pop with {@link #pop()}. */
    void pushScale(float sx, float sy);

    /** Pops the innermost transform. */
    void pop();

    /** Global alpha multiplier (0..1) applied to every subsequent draw; used for screen fades. */
    float partialAlpha();

    /** Sets the global alpha multiplier (clamped to 0..1). */
    void setPartialAlpha(float alpha);

    /** Blurs what has been drawn so far (menu background blur). Implementations may ignore it. */
    default void blurBackground() {
    }

    // ---------------------------------------------------------------- composed shapes

    /** Draws the whole texture stretched into the rectangle. */
    default void image(TextureRef texture, int x, int y, int w, int h) {
        image(texture, x, y, w, h, 0f, 0f, 1f, 1f, 1, 1);
    }

    /** Vertical gradient, one strip per pixel row. */
    default void fillGradientV(int x, int y, int w, int h, int argbTop, int argbBottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (argbTop == argbBottom) {
            fill(x, y, w, h, argbTop);
            return;
        }
        for (int i = 0; i < h; i++) {
            float t = h == 1 ? 0f : (float) i / (float) (h - 1);
            fill(x, y + i, w, 1, Colors.lerp(argbTop, argbBottom, t));
        }
    }

    /** Horizontal gradient, one strip per pixel column. */
    default void fillGradientH(int x, int y, int w, int h, int argbLeft, int argbRight) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (argbLeft == argbRight) {
            fill(x, y, w, h, argbLeft);
            return;
        }
        for (int i = 0; i < w; i++) {
            float t = w == 1 ? 0f : (float) i / (float) (w - 1);
            fill(x + i, y, 1, h, Colors.lerp(argbLeft, argbRight, t));
        }
    }

    /**
     * Fills a rectangle with pixel-rounded corners. The radius is clamped to {@link #MAX_RADIUS} and to half the
     * smaller side. Composed of plain fills so it renders identically everywhere.
     */
    default void fillRounded(int x, int y, int w, int h, int radius, int argb) {
        int r = clampRadius(radius, w, h);
        if (w <= 0 || h <= 0) {
            return;
        }
        if (r <= 0) {
            fill(x, y, w, h, argb);
            return;
        }
        int[] insets = RoundedCorners.insets(r);
        // Center band (between the corner rows).
        fill(x, y + r, w, h - 2 * r, argb);
        for (int i = 0; i < r; i++) {
            int inset = insets[i];
            fill(x + inset, y + i, w - 2 * inset, 1, argb);
            fill(x + inset, y + h - 1 - i, w - 2 * inset, 1, argb);
        }
    }

    /**
     * Fills a rounded rectangle with a horizontal gradient (the primary button look).
     */
    default void fillRoundedGradientH(int x, int y, int w, int h, int radius, int argbLeft, int argbRight) {
        int r = clampRadius(radius, w, h);
        if (w <= 0 || h <= 0) {
            return;
        }
        if (r <= 0) {
            fillGradientH(x, y, w, h, argbLeft, argbRight);
            return;
        }
        int[] insets = RoundedCorners.insets(r);
        for (int col = 0; col < w; col++) {
            float t = w == 1 ? 0f : (float) col / (float) (w - 1);
            int color = Colors.lerp(argbLeft, argbRight, t);
            int fromLeft = col;
            int fromRight = w - 1 - col;
            int edge = Math.min(fromLeft, fromRight);
            int trim = 0;
            for (int i = 0; i < r; i++) {
                if (edge < insets[i]) {
                    trim = i + 1;
                }
            }
            if (trim * 2 >= h) {
                continue;
            }
            fill(x + col, y + trim, 1, h - 2 * trim, color);
        }
    }

    /** Strokes the inside edge of a rectangle. */
    default void strokeRect(int x, int y, int w, int h, int thickness, int argb) {
        if (w <= 0 || h <= 0 || thickness <= 0) {
            return;
        }
        int t = Math.min(thickness, Math.min(w, h) / 2 + 1);
        fill(x, y, w, t, argb);
        fill(x, y + h - t, w, t, argb);
        fill(x, y + t, t, h - 2 * t, argb);
        fill(x + w - t, y + t, t, h - 2 * t, argb);
    }

    /**
     * Strokes a pixel-rounded rectangle (1 px thick). The outline follows the same corner table as
     * {@link #fillRounded} so a stroke drawn over a fill of the same bounds lines up exactly.
     */
    default void strokeRounded(int x, int y, int w, int h, int radius, int argb) {
        int r = clampRadius(radius, w, h);
        if (w <= 0 || h <= 0) {
            return;
        }
        if (r <= 0) {
            strokeRect(x, y, w, h, 1, argb);
            return;
        }
        int[] insets = RoundedCorners.insets(r);
        // Straight edges.
        fill(x + insets[0], y, w - 2 * insets[0], 1, argb);
        fill(x + insets[0], y + h - 1, w - 2 * insets[0], 1, argb);
        fill(x, y + r, 1, h - 2 * r, argb);
        fill(x + w - 1, y + r, 1, h - 2 * r, argb);
        // Corner steps: for each corner row draw the pixels between this row's inset and the next row's inset.
        for (int i = 1; i < r; i++) {
            int inset = insets[i];
            int prev = insets[i - 1];
            int run = Math.max(1, prev - inset);
            fill(x + inset, y + i, run, 1, argb);
            fill(x + w - inset - run, y + i, run, 1, argb);
            fill(x + inset, y + h - 1 - i, run, 1, argb);
            fill(x + w - inset - run, y + h - 1 - i, run, 1, argb);
        }
    }

    /** Axis-aligned 1 px line between two points (diagonals are drawn as their bounding step path). */
    default void line(int x1, int y1, int x2, int y2, int argb) {
        if (y1 == y2) {
            int left = Math.min(x1, x2);
            fill(left, y1, Math.abs(x2 - x1) + 1, 1, argb);
        } else if (x1 == x2) {
            int top = Math.min(y1, y2);
            fill(x1, top, 1, Math.abs(y2 - y1) + 1, argb);
        } else {
            // Bresenham step path.
            int dx = Math.abs(x2 - x1);
            int dy = -Math.abs(y2 - y1);
            int sx = x1 < x2 ? 1 : -1;
            int sy = y1 < y2 ? 1 : -1;
            int err = dx + dy;
            int cx = x1;
            int cy = y1;
            while (true) {
                fill(cx, cy, 1, 1, argb);
                if (cx == x2 && cy == y2) {
                    break;
                }
                int e2 = 2 * err;
                if (e2 >= dy) {
                    err += dy;
                    cx += sx;
                }
                if (e2 <= dx) {
                    err += dx;
                    cy += sy;
                }
            }
        }
    }

    /** Filled circle (pixel scanlines) covering {@code cx - r .. cx + r}. */
    default void circle(int cx, int cy, int r, int argb) {
        if (r < 0) {
            return;
        }
        if (r == 0) {
            fill(cx, cy, 1, 1, argb);
            return;
        }
        for (int dy = -r; dy <= r; dy++) {
            double half = Math.sqrt((double) r * r - (double) dy * dy + 0.25);
            int hw = (int) Math.floor(half);
            fill(cx - hw, cy + dy, 2 * hw + 1, 1, argb);
        }
    }

    /** Filled triangle by scanline rasterisation (vertices in any order). */
    default void triangle(int x1, int y1, int x2, int y2, int x3, int y3, int argb) {
        int minY = Math.min(y1, Math.min(y2, y3));
        int maxY = Math.max(y1, Math.max(y2, y3));
        for (int y = minY; y <= maxY; y++) {
            double sampleY = y + 0.5;
            double left = Double.POSITIVE_INFINITY;
            double right = Double.NEGATIVE_INFINITY;
            double[][] edges = {{x1, y1, x2, y2}, {x2, y2, x3, y3}, {x3, y3, x1, y1}};
            for (double[] e : edges) {
                double ex1 = e[0];
                double ey1 = e[1];
                double ex2 = e[2];
                double ey2 = e[3];
                if (ey1 == ey2) {
                    if (Math.abs(sampleY - ey1) <= 0.5) {
                        left = Math.min(left, Math.min(ex1, ex2));
                        right = Math.max(right, Math.max(ex1, ex2));
                    }
                    continue;
                }
                double top = Math.min(ey1, ey2);
                double bottom = Math.max(ey1, ey2);
                if (sampleY < top || sampleY > bottom) {
                    continue;
                }
                double t = (sampleY - ey1) / (ey2 - ey1);
                double x = ex1 + (ex2 - ex1) * t;
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
            if (left <= right) {
                int from = (int) Math.round(left);
                int to = (int) Math.round(right);
                fill(from, y, Math.max(1, to - from), 1, argb);
            }
        }
    }

    // ---------------------------------------------------------------- text helpers

    /** Draws text horizontally centered on {@code cx}. */
    default void textCentered(String text, int cx, int y, int argb, FontKind font, boolean shadow) {
        text(text, cx - textWidth(text, font) / 2, y, argb, font, shadow);
    }

    /** Draws text with its right edge at {@code rightX}. */
    default void textRight(String text, int rightX, int y, int argb, FontKind font, boolean shadow) {
        text(text, rightX - textWidth(text, font), y, argb, font, shadow);
    }

    /** Returns the text, or a prefix ending in an ellipsis, that fits in {@code maxWidth}. */
    default String textClipped(String text, int maxWidth, FontKind font) {
        return CanvasText.ellipsize(text, maxWidth, font, this);
    }

    /** Clamps a corner radius to what the pixel corner tables support. */
    static int clampRadius(int radius, int w, int h) {
        int r = Math.min(radius, MAX_RADIUS);
        r = Math.min(r, Math.min(w, h) / 2);
        return Math.max(0, r);
    }
}
