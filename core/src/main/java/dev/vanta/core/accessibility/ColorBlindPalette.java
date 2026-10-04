package dev.vanta.core.accessibility;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;

/**
 * Replacement colours for the success / warning / danger semantics.
 * <p>
 * The alternatives are drawn from the Okabe–Ito palette, which stays distinguishable under the three common forms
 * of colour vision deficiency: blue {@code #0072B2}, orange {@code #E69F00}, vermilion {@code #D55E00},
 * sky blue {@code #56B4E9}, bluish green {@code #009E73} and yellow {@code #F0E442}.
 */
public enum ColorBlindPalette implements LangKeyed {
    /** VANTA default colours. */
    NONE(0xFF3DDC97, 0xFFF5B942, 0xFFFF5C7A),
    /** Red–green (most common): success blue, warning yellow, danger vermilion. */
    DEUTERANOPIA(0xFF56B4E9, 0xFFF0E442, 0xFFD55E00),
    /** Red–green: success sky blue, warning yellow, danger strong blue (reds are hard to see). */
    PROTANOPIA(0xFF56B4E9, 0xFFF0E442, 0xFF0072B2),
    /** Blue–yellow: success bluish green, warning orange, danger vermilion. */
    TRITANOPIA(0xFF009E73, 0xFFE69F00, 0xFFD55E00);

    private final int success;
    private final int warning;
    private final int danger;

    ColorBlindPalette(int success, int warning, int danger) {
        this.success = success;
        this.warning = warning;
        this.danger = danger;
    }

    /** ARGB colour for "success". */
    public int success() {
        return success;
    }

    /** ARGB colour for "warning". */
    public int warning() {
        return warning;
    }

    /** ARGB colour for "danger". */
    public int danger() {
        return danger;
    }

    @Override
    public String langKey() {
        return "vanta.accessibility.palette." + name().toLowerCase(Locale.ROOT);
    }
}
