package dev.vanta.core.cosmetics;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;

/**
 * Ambient particle layer drawn over the main menu background. Purely visual.
 */
public enum MenuParticles implements LangKeyed {
    /** No particles. */
    NONE,
    /** Slowly rising warm embers. */
    EMBERS,
    /** Drifting cool dust motes. */
    DUST;

    /** Lower-case id used in JSON. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.cosmetics.particles." + id();
    }
}
