package dev.vanta.core.lab;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;
import java.util.Optional;

/**
 * The Vanta Lab features: optional behaviours the player switches on one by one, all off by default. Each is backed
 * by one boolean setting ({@link #settingId()}, category {@code LAB}) so profiles, the Settings screen, the Nexus
 * Lab section and the assistant's {@code lab.set} action all flip the same value.
 */
public enum LabFeature implements LangKeyed {
    /** The HUD fades out after 10 seconds without input while in a world and returns on any input. */
    DYNAMIC_HUD("lab.dynamicHud"),
    /** The crosshair spreads with movement and on attack. */
    ANIMATED_CROSSHAIR("lab.animatedCrosshair"),
    /** 3D beams in the world at enabled waypoints (the screen markers never depend on this). */
    WAYPOINT_BEAMS("lab.waypointBeams"),
    /** A HUD element drawing the last 120 frame times as a sparkline with p50/p99 labels. */
    FRAMETIME_GRAPH("lab.frametimeGraph"),
    /** Fade/slide when VANTA screens open and close. */
    SCREEN_TRANSITIONS("lab.screenTransitions");

    private final String settingId;

    LabFeature(String settingId) {
        this.settingId = settingId;
    }

    /** Lower-case id used by the assistant and in lang keys, e.g. {@code dynamic_hud}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Id of the backing setting in {@code VantaSettings}, e.g. {@code lab.dynamicHud}. */
    public String settingId() {
        return settingId;
    }

    @Override
    public String langKey() {
        return "vanta.lab." + id() + ".title";
    }

    /** Translation key of the one-line honest description. */
    public String descriptionKey() {
        return "vanta.lab." + id() + ".description";
    }

    /** Finds a feature by {@link #id()}, enum name or {@link #settingId()} (case-insensitive). */
    public static Optional<LabFeature> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String needle = id.strip();
        for (LabFeature feature : values()) {
            if (feature.id().equalsIgnoreCase(needle) || feature.name().equalsIgnoreCase(needle)
                    || feature.settingId.equalsIgnoreCase(needle)) {
                return Optional.of(feature);
            }
        }
        return Optional.empty();
    }
}
