package dev.vanta.core.cosmetics;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;

/**
 * Backgrounds available for the VANTA main menu. Purely visual.
 */
public enum MenuBackground implements LangKeyed {
    /** Deep black to graphite vertical gradient (default). */
    GRADIENT_DARK,
    /** Graphite with a faint perspective grid. */
    GRAPHITE_GRID,
    /** Dark sky with a violet→blue horizon glow. */
    VIOLET_HORIZON,
    /** The vanilla rotating panorama with a dark vignette. */
    VANILLA_PANORAMA,
    /** Flat {@code bg.void} colour. */
    SOLID;

    /** Lower-case id used in JSON. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.cosmetics.background." + id();
    }
}
