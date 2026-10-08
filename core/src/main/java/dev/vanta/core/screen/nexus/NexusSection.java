package dev.vanta.core.screen.nexus;

import dev.vanta.core.i18n.LangKeyed;
import dev.vanta.core.ui.Icons;
import java.util.Locale;
import java.util.Optional;

/** The sections of the Vanta Nexus screen, in rail order. */
public enum NexusSection implements LangKeyed {
    /** The Local AI assistant: transcript, prompt, Local AI card. */
    ASSISTANT(Icons.NEXUS),
    /** Named HUD layouts, the preset buttons and the link to the HUD editor. */
    HUD_DESIGNER(Icons.GRID),
    /** The profiles list and its actions. */
    PROFILES(Icons.PROFILE),
    /** Live performance values, presets and Boost FPS. */
    PERFORMANCE(Icons.CHART),
    /** Waypoints of the current world (and every world). */
    WAYPOINTS(Icons.PIN),
    /** The Vanta Lab toggles. */
    LAB(Icons.FLASK),
    /** The nexus.* settings and the Local AI status card. */
    SETTINGS(Icons.GEAR);

    private final Icons icon;

    NexusSection(Icons icon) {
        this.icon = icon;
    }

    /** Lower-case id used by the rail and by deep links. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Rail icon. */
    public Icons icon() {
        return icon;
    }

    @Override
    public String langKey() {
        return "vanta.nexus.section." + id();
    }

    /** Translation key of the one-line description shown under the section title. */
    public String descriptionKey() {
        return "vanta.nexus.section." + id() + ".description";
    }

    /** Finds a section by id or enum name (case-insensitive). */
    public static Optional<NexusSection> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (NexusSection section : values()) {
            if (section.id().equalsIgnoreCase(id.trim()) || section.name().equalsIgnoreCase(id.trim())) {
                return Optional.of(section);
            }
        }
        return Optional.empty();
    }
}
