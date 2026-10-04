package dev.vanta.core.ui;

/**
 * Pixel inset tables for rounded corners. Row {@code i} (counted from the edge) of a corner with radius
 * {@code r} starts {@code insets(r)[i]} pixels in. Shared by every canvas so fills and strokes agree.
 */
public final class RoundedCorners {

    private static final int[][] TABLE = {
            {},
            {1},
            {1, 0},
            {2, 1, 0},
            {2, 1, 1, 0},
    };

    private RoundedCorners() {
    }

    /** Inset per corner row for radius {@code r} (0..{@link Canvas#MAX_RADIUS}). */
    public static int[] insets(int r) {
        int clamped = Math.max(0, Math.min(r, Canvas.MAX_RADIUS));
        return TABLE[clamped].clone();
    }
}
