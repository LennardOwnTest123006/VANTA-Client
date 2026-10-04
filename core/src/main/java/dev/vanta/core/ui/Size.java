package dev.vanta.core.ui;

/**
 * Immutable width/height pair in GUI pixels.
 *
 * @param w width
 * @param h height
 */
public record Size(int w, int h) {

    /** Zero size. */
    public static final Size ZERO = new Size(0, 0);

    /** Creates a size. */
    public static Size of(int w, int h) {
        return new Size(w, h);
    }

    /** Component-wise maximum. */
    public Size max(Size other) {
        return new Size(Math.max(w, other.w), Math.max(h, other.h));
    }

    /** Adds the insets to both dimensions. */
    public Size plus(Insets insets) {
        return new Size(w + insets.horizontal(), h + insets.vertical());
    }

    /** Adds to both dimensions. */
    public Size plus(int dw, int dh) {
        return new Size(Math.max(0, w + dw), Math.max(0, h + dh));
    }
}
