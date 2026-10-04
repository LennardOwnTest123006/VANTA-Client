package dev.vanta.core.hud;

/**
 * Integer rectangle in GUI-scaled screen pixels. Defined locally so the HUD model does not depend on the UI toolkit.
 */
public record HudRect(int x, int y, int width, int height) {
    public HudRect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    /** Right edge (exclusive). */
    public int right() {
        return x + width;
    }

    /** Bottom edge (exclusive). */
    public int bottom() {
        return y + height;
    }

    /** Horizontal centre. */
    public int centerX() {
        return x + width / 2;
    }

    /** Vertical centre. */
    public int centerY() {
        return y + height / 2;
    }

    /** True when the point lies inside. */
    public boolean contains(int px, int py) {
        return px >= x && py >= y && px < right() && py < bottom();
    }

    /** True when the rectangles overlap. */
    public boolean intersects(HudRect other) {
        return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
    }

    /** Copy moved by a delta. */
    public HudRect translate(int dx, int dy) {
        return new HudRect(x + dx, y + dy, width, height);
    }

    /** Copy at a new position. */
    public HudRect at(int nx, int ny) {
        return new HudRect(nx, ny, width, height);
    }

    /** Copy with a new size. */
    public HudRect sized(int w, int h) {
        return new HudRect(x, y, w, h);
    }

    /** Copy grown by {@code amount} on every side. */
    public HudRect grow(int amount) {
        return new HudRect(x - amount, y - amount, width + 2 * amount, height + 2 * amount);
    }

    /** Copy clamped so it lies inside {@code 0..screenW} × {@code 0..screenH} (size kept when it fits). */
    public HudRect clampTo(int screenW, int screenH) {
        int w = Math.min(width, Math.max(0, screenW));
        int h = Math.min(height, Math.max(0, screenH));
        int nx = Math.max(0, Math.min(x, screenW - w));
        int ny = Math.max(0, Math.min(y, screenH - h));
        return new HudRect(nx, ny, w, h);
    }
}
