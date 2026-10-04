package dev.vanta.launcher.core.settings;

/**
 * Initial game window size.
 *
 * @param width  width in pixels
 * @param height height in pixels
 */
public record Resolution(int width, int height) {

    public Resolution {
        if (width < 320 || height < 240 || width > 16384 || height > 16384) {
            throw new IllegalArgumentException("Unsupported resolution " + width + "x" + height);
        }
    }

    /**
     * Parses {@code WIDTHxHEIGHT}.
     *
     * @param text e.g. {@code 1920x1080}
     * @return resolution
     * @throws IllegalArgumentException for malformed input
     */
    public static Resolution parse(final String text) {
        final String[] parts = text.trim().toLowerCase(java.util.Locale.ROOT).split("x");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected WIDTHxHEIGHT but got '" + text + "'");
        }
        try {
            return new Resolution(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected WIDTHxHEIGHT but got '" + text + "'", e);
        }
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }
}
