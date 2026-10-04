package dev.vanta.core.ui;

/**
 * Immutable paddings for the four sides of a box, in GUI pixels.
 *
 * @param left   left inset
 * @param top    top inset
 * @param right  right inset
 * @param bottom bottom inset
 */
public record Insets(int left, int top, int right, int bottom) {

    /** No insets. */
    public static final Insets ZERO = new Insets(0, 0, 0, 0);

    /** Equal insets on all sides. */
    public static Insets of(int all) {
        return new Insets(all, all, all, all);
    }

    /** Vertical (top/bottom) and horizontal (left/right) insets. */
    public static Insets of(int vertical, int horizontal) {
        return new Insets(horizontal, vertical, horizontal, vertical);
    }

    /** Individual insets. */
    public static Insets of(int left, int top, int right, int bottom) {
        return new Insets(left, top, right, bottom);
    }

    /** {@code left + right}. */
    public int horizontal() {
        return left + right;
    }

    /** {@code top + bottom}. */
    public int vertical() {
        return top + bottom;
    }

    /** Sum of two insets. */
    public Insets plus(Insets other) {
        return new Insets(left + other.left, top + other.top, right + other.right, bottom + other.bottom);
    }
}
