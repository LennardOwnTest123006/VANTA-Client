package dev.vanta.core.crosshair;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;
import java.util.Optional;

/**
 * Basic crosshair shapes. All are drawn from axis-aligned rectangles and rings so any renderer can draw them.
 */
public enum CrosshairShape implements LangKeyed {
    /** Four arms with a gap in the middle (vanilla-like). */
    CROSS,
    /** A single centred dot. */
    DOT,
    /** A ring. */
    CIRCLE,
    /** A hollow square. */
    SQUARE,
    /** Two diagonal arms below the centre forming a {@code ^}. */
    CHEVRON,
    /** Cross with a centre dot. */
    PLUS_DOT;

    /** Lower-case id used in JSON. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.crosshair.shape." + id();
    }

    /** Finds a shape by id (case-insensitive). */
    public static Optional<CrosshairShape> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (CrosshairShape shape : values()) {
            if (shape.id().equalsIgnoreCase(id) || shape.name().equalsIgnoreCase(id)) {
                return Optional.of(shape);
            }
        }
        return Optional.empty();
    }
}
