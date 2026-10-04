package dev.vanta.core.screen.common;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.TextureRef;
import dev.vanta.core.ui.Theme;

/**
 * Brand drawing helpers shared by the main menu and the About screen. The shipped wordmark texture
 * ({@code vanta:textures/gui/wordmark.png}, 512x128) contains the V mark followed by the word "VANTA"; when the
 * mark is already shown as a separate logo tile, {@link #drawWordmarkText} draws only the word so the mark does
 * not appear twice.
 */
public final class Brand {
    /** Wordmark texture width in pixels. */
    public static final int WORDMARK_TEX_W = 512;
    /** Wordmark texture height in pixels. */
    public static final int WORDMARK_TEX_H = 128;
    /** Left edge of the "VANTA" word inside the wordmark texture (the mark occupies columns 15–96). */
    public static final float WORDMARK_TEXT_U = 118f;
    /** Width of the "VANTA" word region inside the wordmark texture. */
    public static final float WORDMARK_TEXT_W = 344f;
    /** Aspect ratio (width / height) of the word region. */
    public static final float WORDMARK_TEXT_ASPECT = WORDMARK_TEXT_W / WORDMARK_TEX_H;
    /** Aspect ratio of the full wordmark. */
    public static final float WORDMARK_ASPECT = (float) WORDMARK_TEX_W / WORDMARK_TEX_H;

    private Brand() {
    }

    /** Height the word region takes at a given width. */
    public static int wordmarkTextHeight(int width) {
        return Math.max(1, Math.round(width / WORDMARK_TEXT_ASPECT));
    }

    /** Height the full wordmark takes at a given width. */
    public static int wordmarkHeight(int width) {
        return Math.max(1, Math.round(width / WORDMARK_ASPECT));
    }

    /**
     * Draws only the "VANTA" word of the wordmark texture into {@code (x, y, width, wordmarkTextHeight(width))}.
     *
     * @return the drawn height
     */
    public static int drawWordmarkText(Canvas canvas, int x, int y, int width) {
        int h = wordmarkTextHeight(width);
        canvas.image(TextureRef.VANTA_WORDMARK, x, y, width, h, WORDMARK_TEXT_U, 0f, WORDMARK_TEXT_W, WORDMARK_TEX_H,
                WORDMARK_TEX_W, WORDMARK_TEX_H);
        return h;
    }

    /** Draws the full wordmark (mark + word) at the given width and returns the drawn height. */
    public static int drawWordmark(Canvas canvas, int x, int y, int width) {
        int h = wordmarkHeight(width);
        canvas.image(TextureRef.VANTA_WORDMARK, x, y, width, h);
        return h;
    }

    /** Draws the logo tile with a soft accent glow behind it. */
    public static void drawLogo(Canvas canvas, Theme theme, int x, int y, int size) {
        int pad = Math.max(3, size / 10);
        canvas.fillRounded(x - pad, y - pad, size + pad * 2, size + pad * 2, Theme.RADIUS_LG,
                Colors.withAlpha(theme.accent(), 0.10f));
        canvas.image(TextureRef.VANTA_LOGO, x, y, size, size);
    }
}
