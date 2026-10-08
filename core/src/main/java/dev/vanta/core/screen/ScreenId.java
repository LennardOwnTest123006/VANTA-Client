package dev.vanta.core.screen;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;
import java.util.Optional;

/**
 * Every VANTA screen. Screens are created through {@link ScreenRegistry}; this enum is the dependency-free handle
 * used by actions, search results and the HUD.
 */
public enum ScreenId implements LangKeyed {
    MAIN_MENU,
    SETTINGS,
    HUD_EDITOR,
    PERFORMANCE,
    PROFILES,
    KEYBINDS,
    CROSSHAIR,
    COSMETICS,
    STATISTICS,
    RESOURCE_PACKS,
    MODS,
    ACCESSIBILITY,
    SEARCH,
    ABOUT,
    /** Vanta Nexus: the assistant, HUD Designer, profiles, performance, waypoints, Vanta Lab and its settings. */
    NEXUS,
    /** The Local AI download and setup steps (opened from Nexus; nothing downloads before Install is clicked). */
    LOCAL_AI_SETUP;

    /** Lower-case id used in JSON and lang keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.screen." + id();
    }

    /** Translation key of the one-line description shown in search results and the hub. */
    public String descriptionKey() {
        return "vanta.screen." + id() + ".description";
    }

    /** Finds a screen by id (case-insensitive). */
    public static Optional<ScreenId> fromId(String id) {
        for (ScreenId screen : values()) {
            if (screen.id().equalsIgnoreCase(id) || screen.name().equalsIgnoreCase(id)) {
                return Optional.of(screen);
            }
        }
        return Optional.empty();
    }
}
