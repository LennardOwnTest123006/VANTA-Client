package dev.vanta.core.settings;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;

/**
 * Top-level groups of the settings screen, in display order.
 */
public enum SettingCategory implements LangKeyed {
    GENERAL("gear"),
    VIDEO("eye"),
    AUDIO("sliders"),
    CONTROLS("keyboard"),
    HUD("grid"),
    /** Waypoint markers (the waypoints themselves live in Vanta Nexus). */
    WAYPOINTS("world"),
    PERFORMANCE("chart"),
    ACCESSIBILITY("accessibility"),
    LANGUAGE("list"),
    COSMETICS("palette"),
    NEXUS("star"),
    PRIVACY("lock"),
        /** Vanta Lab: optional features, all off by default (see {@code dev.vanta.core.lab.LabFeature}). */
    LAB("star");

    private final String iconId;

    SettingCategory(String iconId) {
        this.iconId = iconId;
    }

    /** Icon identifier understood by the UI layer's icon set. */
    public String iconId() {
        return iconId;
    }

    /** Lower-case id used in JSON and lang keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.category." + id();
    }

    /** Translation key of the short category description shown under the title. */
    public String descriptionKey() {
        return "vanta.category." + id() + ".description";
    }
}
