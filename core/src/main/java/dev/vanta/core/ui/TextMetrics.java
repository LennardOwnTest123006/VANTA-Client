package dev.vanta.core.ui;

/**
 * Text measuring, available during layout (before any canvas exists). {@link Canvas} extends it.
 */
public interface TextMetrics {

    /** Width of the string in GUI pixels when drawn with the given font. */
    int textWidth(String text, FontKind font);

    /** Line advance of the font in GUI pixels. */
    default int lineHeight(FontKind font) {
        return font.lineHeight();
    }

    /**
     * Fixed-advance metrics (useful in tests): every character is {@code charWidth} px wide.
     */
    static TextMetrics fixed(int charWidth) {
        return (text, font) -> text == null ? 0 : text.length() * charWidth;
    }
}
