package dev.vanta.core.cosmetics;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;

/**
 * Visual-only profile badge shown next to the player name in VANTA menus. Badges have no gameplay effect and are
 * never shown to other players.
 */
public enum Badge implements LangKeyed {
    NONE(0x00000000, ""),
    /** Early supporter of the project. */
    FOUNDER(0xFF7C5CFF, "✦"),
    /** Code or documentation contributor. */
    CONTRIBUTOR(0xFF4F8DFF, "⬡"),
    /** Project supporter. */
    SUPPORTER(0xFF3DDC97, "♥");

    private final int color;
    private final String glyph;

    Badge(int color, String glyph) {
        this.color = color;
        this.glyph = glyph;
    }

    /** ARGB colour of the badge. */
    public int color() {
        return color;
    }

    /** Single glyph drawn inside the badge (empty for {@link #NONE}). */
    public String glyph() {
        return glyph;
    }

    /** Lower-case id used in JSON. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.cosmetics.badge." + id();
    }

    /** Looks up a badge by id, falling back to {@link #NONE}. */
    public static Badge fromId(String id) {
        for (Badge badge : values()) {
            if (badge.id().equalsIgnoreCase(id)) {
                return badge;
            }
        }
        return NONE;
    }
}
