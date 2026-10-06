package dev.vanta.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the pixels of every {@link Icons} value per size as a short list of rectangles. An icon is rasterised once
 * with {@link IconPen} into a bitmap (one fill per stroke step, with plenty of overdraw); the bitmap is then cut
 * greedily into rectangles (a run of covered pixels in a row, extended downwards while the whole run stays covered,
 * and the same with rows and columns swapped, keeping the shorter list), and replaying those covers exactly the same
 * pixels with a handful of fills, each pixel drawn once.
 * <p>
 * Pixel positions only depend on the icon and its size (the pen rounds relative to the icon origin), so rectangles
 * are stored relative to the origin and translated on replay. The cache is small (icons x the sizes in use) and safe
 * to share between the render thread and the preview renderer.
 */
final class IconRaster {

    /** {@code (icon, size)} to rectangles as {@code [dx, dy, w, h, dx, dy, w, h, ...]}. */
    private static final Map<Long, int[]> RECTS = new ConcurrentHashMap<>();

    private IconRaster() {
    }

    /** Draws the icon from its cached rectangles. */
    static void draw(Canvas canvas, Icons icon, int x, int y, int size, int argb) {
        int[] rects = RECTS.computeIfAbsent(key(icon, size), k -> rasterise(icon, size));
        for (int i = 0; i < rects.length; i += 4) {
            canvas.fill(x + rects[i], y + rects[i + 1], rects[i + 2], rects[i + 3], argb);
        }
    }

    /** Number of fills the cached icon needs at this size (diagnostics). */
    static int fillCount(Icons icon, int size) {
        return RECTS.computeIfAbsent(key(icon, size), k -> rasterise(icon, size)).length / 4;
    }

    private static long key(Icons icon, int size) {
        return ((long) icon.ordinal() << 32) | (size & 0xFFFFFFFFL);
    }

    /** Rasterises the icon at the origin and cuts its pixels into rectangles. */
    static int[] rasterise(Icons icon, int size) {
        PixelCanvas pixels = new PixelCanvas();
        icon.paint(pixels, 0, 0, size, 0xFFFFFFFF);
        return pixels.rectangles();
    }

    /** Minimal canvas that only remembers which pixels fills cover. */
    private static final class PixelCanvas implements Canvas {
        private final List<int[]> rects = new ArrayList<>();

        int[] rectangles() {
            if (rects.isEmpty()) {
                return new int[0];
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int[] r : rects) {
                minX = Math.min(minX, r[0]);
                minY = Math.min(minY, r[1]);
                maxX = Math.max(maxX, r[0] + r[2]);
                maxY = Math.max(maxY, r[1] + r[3]);
            }
            int width = maxX - minX;
            int height = maxY - minY;
            boolean[] covered = new boolean[width * height];
            for (int[] r : rects) {
                for (int row = r[1]; row < r[1] + r[3]; row++) {
                    int base = (row - minY) * width - minX;
                    for (int col = r[0]; col < r[0] + r[2]; col++) {
                        covered[base + col] = true;
                    }
                }
            }
            int[] byRows = cut(covered.clone(), width, height, false);
            int[] byColumns = cut(transpose(covered, width, height), height, width, true);
            int[] shorter = byColumns.length < byRows.length ? byColumns : byRows;
            for (int i = 0; i < shorter.length; i += 4) {
                shorter[i] += minX;
                shorter[i + 1] += minY;
            }
            return shorter;
        }

        private static boolean[] transpose(boolean[] grid, int width, int height) {
            boolean[] out = new boolean[grid.length];
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    out[col * height + row] = grid[row * width + col];
                }
            }
            return out;
        }

        /**
         * Greedy cut of a bitmap into rectangles {@code [x, y, w, h, ...]} relative to the bitmap origin. With
         * {@code transposed} the bitmap is the transpose of the icon, and the rectangles are swapped back.
         */
        private static int[] cut(boolean[] covered, int width, int height, boolean transposed) {
            List<Integer> out = new ArrayList<>();
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    if (!covered[row * width + col]) {
                        continue;
                    }
                    int end = col;
                    while (end < width && covered[row * width + end]) {
                        end++;
                    }
                    int bottom = row + 1;
                    while (bottom < height && runCovered(covered, width, bottom, col, end)) {
                        bottom++;
                    }
                    for (int r = row; r < bottom; r++) {
                        for (int c = col; c < end; c++) {
                            covered[r * width + c] = false;
                        }
                    }
                    out.add(transposed ? row : col);
                    out.add(transposed ? col : row);
                    out.add(transposed ? bottom - row : end - col);
                    out.add(transposed ? end - col : bottom - row);
                    col = end - 1;
                }
            }
            int[] rectangles = new int[out.size()];
            for (int i = 0; i < rectangles.length; i++) {
                rectangles[i] = out.get(i);
            }
            return rectangles;
        }

        private static boolean runCovered(boolean[] covered, int width, int row, int from, int to) {
            for (int c = from; c < to; c++) {
                if (!covered[row * width + c]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int width() {
            return 0;
        }

        @Override
        public int height() {
            return 0;
        }

        @Override
        public void fill(int x, int y, int w, int h, int argb) {
            if (w <= 0 || h <= 0) {
                return;
            }
            rects.add(new int[] {x, y, w, h});
        }

        @Override
        public void text(String text, int x, int y, int argb, FontKind font, boolean shadow) {
        }

        @Override
        public void image(TextureRef texture, int x, int y, int w, int h, float u, float v, float uw, float vh,
                          int texW, int texH) {
        }

        @Override
        public void pushScissor(Rect clip) {
        }

        @Override
        public void popScissor() {
        }

        @Override
        public void pushTranslate(float dx, float dy) {
        }

        @Override
        public void pushScale(float sx, float sy) {
        }

        @Override
        public void pop() {
        }

        @Override
        public float partialAlpha() {
            return 1f;
        }

        @Override
        public void setPartialAlpha(float alpha) {
        }

        @Override
        public int textWidth(String text, FontKind font) {
            return 0;
        }
    }
}
