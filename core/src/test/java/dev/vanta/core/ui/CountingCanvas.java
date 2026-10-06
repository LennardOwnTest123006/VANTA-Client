package dev.vanta.core.ui;

import java.util.Locale;

/**
 * {@link Canvas} that only counts what a frame asks of the backend, without recording it. Used by the render cost
 * tests to document how many primitives one frame submits: in the Minecraft client every {@link #fill} becomes one
 * {@code GuiGraphics.fill} (one rectangle render state), every {@link #text} one {@code drawString} with a styled
 * {@code Component}, and every {@link #textWidth} one {@code Font.width} of a freshly allocated {@code Component}.
 * <p>
 * Text metrics match {@link TestCanvas} (UI/UI_BOLD 5 px per character, DISPLAY 8 px, MINECRAFT 6 px) so layouts
 * are identical to the other core tests. {@link #metrics()} returns a {@link TextMetrics} whose calls are counted
 * on this canvas too, so measurements made through a {@link UiContext} during layout show up in {@link #textWidths}.
 */
public final class CountingCanvas extends AbstractCanvas {

    /** Plain rectangle fills (one draw-state element each in game). */
    public int fills;
    /** Fills of exactly one pixel. */
    public int fills1x1;
    /** Fills one pixel wide and taller than one pixel (gradient / line columns). */
    public int fillColumns;
    /** Fills one pixel tall and wider than one pixel (gradient / rounded-corner rows). */
    public int fillRows;
    /** Pixels covered by all fills together (overdraw included). */
    public long fillPixels;
    /** Text draws. */
    public int texts;
    /** Characters drawn. */
    public long textChars;
    /** Texture blits. */
    public int images;
    /** {@link #pushScissor} calls. */
    public int scissorPushes;
    /** Native scissor changes (push + pop each change the active clip). */
    public int scissorChanges;
    /** Transform pushes (translate + scale). */
    public int transformPushes;
    /** Width measurements (canvas and {@link #metrics()} together). */
    public int textWidths;
    /** Line-height queries. */
    public int lineHeights;

    public CountingCanvas(int width, int height) {
        super(width, height);
    }

    /** Resets every counter (keeps the surface size). */
    public void reset() {
        fills = 0;
        fills1x1 = 0;
        fillColumns = 0;
        fillRows = 0;
        fillPixels = 0L;
        texts = 0;
        textChars = 0L;
        images = 0;
        scissorPushes = 0;
        scissorChanges = 0;
        transformPushes = 0;
        textWidths = 0;
        lineHeights = 0;
    }

    /** Text metrics that count into this canvas (for the {@link UiEnvironment} of a screen under test). */
    public TextMetrics metrics() {
        return new TextMetrics() {
            @Override
            public int textWidth(String text, FontKind font) {
                return CountingCanvas.this.textWidth(text, font);
            }

            @Override
            public int lineHeight(FontKind font) {
                return CountingCanvas.this.lineHeight(font);
            }
        };
    }

    @Override
    public int textWidth(String text, FontKind font) {
        textWidths++;
        return TestCanvas.width(text, font);
    }

    @Override
    public int lineHeight(FontKind font) {
        lineHeights++;
        return (font == null ? FontKind.UI : font).lineHeight();
    }

    @Override
    protected void fillImpl(int x, int y, int w, int h, int argb) {
        fills++;
        fillPixels += (long) w * h;
        if (w == 1 && h == 1) {
            fills1x1++;
        } else if (w == 1) {
            fillColumns++;
        } else if (h == 1) {
            fillRows++;
        }
    }

    @Override
    protected void textImpl(String text, int x, int y, int argb, FontKind font, boolean shadow) {
        texts++;
        textChars += text.length();
    }

    @Override
    protected void imageImpl(TextureRef texture, int x, int y, int w, int h, float u, float v, float uw, float vh,
                             int texW, int texH, float alpha) {
        images++;
    }

    @Override
    public void pushScissor(Rect clip) {
        scissorPushes++;
        super.pushScissor(clip);
    }

    @Override
    protected void onPushTranslate(float dx, float dy) {
        transformPushes++;
    }

    @Override
    protected void onPushScale(float sx, float sy) {
        transformPushes++;
    }

    @Override
    protected void onPopTransform(Transform restored) {
    }

    @Override
    protected void onScissorChanged(Rect deviceClip) {
        scissorChanges++;
    }

    /** Column header matching {@link #row(String)}. */
    public static String header() {
        return String.format(Locale.ROOT, "%-44s %7s %6s %6s %6s %6s %5s %7s %5s %6s %6s",
                "frame", "fills", "1x1", "cols", "rows", "texts", "imgs", "scissor", "xform", "widths", "lineH");
    }

    /** One table row with the counters. */
    public String row(String label) {
        return String.format(Locale.ROOT, "%-44s %7d %6d %6d %6d %6d %5d %7d %5d %6d %6d", label, fills, fills1x1,
                fillColumns, fillRows, texts, images, scissorPushes, transformPushes, textWidths, lineHeights);
    }
}
