package dev.vanta.core.ui;

/**
 * The four fonts the UI kit draws with. Each kind maps to a Minecraft font provider id and carries the metrics
 * the two canvases (GuiGraphics in-game, Java2D in previews) must agree on.
 * <p>
 * The {@code size} and {@code baselineShift} values mirror the TTF provider JSON in
 * {@code assets/vanta/font/*.json}; a unit test keeps them in sync.
 */
public enum FontKind {

    /** Inter Medium, 10 GUI px provider size, {@code vanta:ui}. The body font. */
    UI("vanta:ui", 10f, 9, -1f),
    /** Inter Bold, same metrics as {@link #UI}, {@code vanta:ui_bold}. Titles, buttons, emphasis. */
    UI_BOLD("vanta:ui_bold", 10f, 9, -1f),
    /** Space Grotesk Bold, 14 GUI px provider size, {@code vanta:display}. Screen titles and the wordmark. */
    DISPLAY("vanta:display", 14f, 14, 0f),
    /** The vanilla Minecraft font ({@code minecraft:default}); 8 px glyphs, line height 9. */
    MINECRAFT("minecraft:default", 8f, 9, 0f);

    private final String fontId;
    private final float size;
    private final int lineHeight;
    private final float baselineShift;

    FontKind(String fontId, float size, int lineHeight, float baselineShift) {
        this.fontId = fontId;
        this.size = size;
        this.lineHeight = lineHeight;
        this.baselineShift = baselineShift;
    }

    /** Minecraft font identifier, e.g. {@code vanta:ui}. */
    public String fontId() {
        return fontId;
    }

    /** TTF provider size in GUI pixels (ascent + descent of the font). */
    public float size() {
        return size;
    }

    /** Line advance used for layout, in GUI pixels. */
    public int lineHeight() {
        return lineHeight;
    }

    /** Vertical shift applied by the provider (the {@code shift[1]} entry of the font JSON). */
    public float baselineShift() {
        return baselineShift;
    }

    /** Whether this is one of the two Inter body fonts. */
    public boolean isBody() {
        return this == UI || this == UI_BOLD;
    }
}
