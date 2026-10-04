package dev.vanta.core.cosmetics;

/**
 * Style properties of a {@link HudTheme}.
 *
 * @param backgroundAlpha panel background alpha multiplier in [0, 1] (0 = no panel)
 * @param border          true to stroke a 1px border
 * @param borderColor     ARGB border colour (ignored without border)
 * @param radius          corner radius in GUI pixels
 * @param padding         inner padding in GUI pixels
 * @param textShadow      true to draw text with a shadow
 * @param blur            true to blur the background behind panels when the host supports it
 */
public record HudThemeStyle(double backgroundAlpha, boolean border, int borderColor, int radius, int padding,
                            boolean textShadow, boolean blur) {
    public HudThemeStyle {
        backgroundAlpha = Math.max(0.0, Math.min(1.0, backgroundAlpha));
        radius = Math.max(0, radius);
        padding = Math.max(0, padding);
    }

    /** True when the theme draws a panel behind widget content. */
    public boolean hasPanel() {
        return backgroundAlpha > 0.0 || border;
    }
}
