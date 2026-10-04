package dev.vanta.core.ui;

/**
 * Immutable integer rectangle in GUI-scaled pixels. Width and height are never negative.
 *
 * @param x left edge
 * @param y top edge
 * @param w width in pixels
 * @param h height in pixels
 */
public record Rect(int x, int y, int w, int h) {

    /** The empty rectangle at the origin. */
    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

    public Rect {
        if (w < 0 || h < 0) {
            throw new IllegalArgumentException("Rect size must not be negative: " + w + "x" + h);
        }
    }

    /** Creates a rectangle. */
    public static Rect of(int x, int y, int w, int h) {
        return new Rect(x, y, w, h);
    }

    /** Creates a rectangle from two corners (any order). */
    public static Rect fromCorners(int x1, int y1, int x2, int y2) {
        int left = Math.min(x1, x2);
        int top = Math.min(y1, y2);
        return new Rect(left, top, Math.abs(x2 - x1), Math.abs(y2 - y1));
    }

    /** Rectangle of the given size centered inside {@code outer}. */
    public static Rect centered(Rect outer, int w, int h) {
        return new Rect(outer.x + (outer.w - w) / 2, outer.y + (outer.h - h) / 2, Math.max(0, w), Math.max(0, h));
    }

    /** Exclusive right edge ({@code x + w}). */
    public int right() {
        return x + w;
    }

    /** Exclusive bottom edge ({@code y + h}). */
    public int bottom() {
        return y + h;
    }

    /** Horizontal center (rounded down). */
    public int centerX() {
        return x + w / 2;
    }

    /** Vertical center (rounded down). */
    public int centerY() {
        return y + h / 2;
    }

    /** Whether the rectangle has no area. */
    public boolean isEmpty() {
        return w == 0 || h == 0;
    }

    /** Whether the point is inside (right and bottom edges are exclusive). */
    public boolean contains(int px, int py) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }

    /** Whether the (sub-pixel) point is inside. */
    public boolean contains(double px, double py) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }

    /** Whether {@code other} lies completely inside this rectangle. */
    public boolean contains(Rect other) {
        return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom();
    }

    /** Whether the two rectangles share at least one pixel (empty rectangles never intersect). */
    public boolean intersects(Rect other) {
        if (isEmpty() || other.isEmpty()) {
            return false;
        }
        return other.x < right() && other.right() > x && other.y < bottom() && other.bottom() > y;
    }

    /** Intersection; an empty rectangle (zero size) when the two do not overlap. */
    public Rect intersect(Rect other) {
        int left = Math.max(x, other.x);
        int top = Math.max(y, other.y);
        int right = Math.min(right(), other.right());
        int bottom = Math.min(bottom(), other.bottom());
        if (right <= left || bottom <= top) {
            return new Rect(left, top, 0, 0);
        }
        return new Rect(left, top, right - left, bottom - top);
    }

    /** Smallest rectangle containing both. */
    public Rect union(Rect other) {
        if (isEmpty()) {
            return other;
        }
        if (other.isEmpty()) {
            return this;
        }
        int left = Math.min(x, other.x);
        int top = Math.min(y, other.y);
        int right = Math.max(right(), other.right());
        int bottom = Math.max(bottom(), other.bottom());
        return new Rect(left, top, right - left, bottom - top);
    }

    /** Shrinks every edge by {@code amount} (negative values grow). Size is clamped at zero. */
    public Rect inset(int amount) {
        return inset(amount, amount, amount, amount);
    }

    /** Shrinks by the given insets. */
    public Rect inset(Insets insets) {
        return inset(insets.left(), insets.top(), insets.right(), insets.bottom());
    }

    /** Shrinks each edge individually. Size is clamped at zero. */
    public Rect inset(int left, int top, int right, int bottom) {
        return new Rect(x + left, y + top, Math.max(0, w - left - right), Math.max(0, h - top - bottom));
    }

    /** Grows every edge by {@code amount}. */
    public Rect expand(int amount) {
        return inset(-amount);
    }

    /** Moves the rectangle. */
    public Rect translate(int dx, int dy) {
        return new Rect(x + dx, y + dy, w, h);
    }

    /** Copy with a different left edge. */
    public Rect withX(int newX) {
        return new Rect(newX, y, w, h);
    }

    /** Copy with a different top edge. */
    public Rect withY(int newY) {
        return new Rect(x, newY, w, h);
    }

    /** Copy with a different width. */
    public Rect withWidth(int newW) {
        return new Rect(x, y, newW, h);
    }

    /** Copy with a different height. */
    public Rect withHeight(int newH) {
        return new Rect(x, y, w, newH);
    }

    /** Copy with a different size. */
    public Rect withSize(int newW, int newH) {
        return new Rect(x, y, newW, newH);
    }

    /** Copy moved to a different origin. */
    public Rect at(int newX, int newY) {
        return new Rect(newX, newY, w, h);
    }

    /** Sub-rectangle taken from the top. */
    public Rect top(int height) {
        return new Rect(x, y, w, Math.min(h, Math.max(0, height)));
    }

    /** Sub-rectangle taken from the bottom. */
    public Rect bottomStrip(int height) {
        int clamped = Math.min(h, Math.max(0, height));
        return new Rect(x, bottom() - clamped, w, clamped);
    }

    /** Sub-rectangle taken from the left. */
    public Rect left(int width) {
        return new Rect(x, y, Math.min(w, Math.max(0, width)), h);
    }

    /** Sub-rectangle taken from the right. */
    public Rect rightStrip(int width) {
        int clamped = Math.min(w, Math.max(0, width));
        return new Rect(right() - clamped, y, clamped, h);
    }

    /** Rectangle scaled around the origin, rounding to the nearest pixel. */
    public Rect scale(float factor) {
        return new Rect(Math.round(x * factor), Math.round(y * factor), Math.round(w * factor), Math.round(h * factor));
    }

    /** Size of this rectangle. */
    public Size size() {
        return new Size(w, h);
    }
}
