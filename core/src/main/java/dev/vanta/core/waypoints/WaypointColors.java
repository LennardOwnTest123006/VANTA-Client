package dev.vanta.core.waypoints;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Marker colours. New waypoints cycle through {@link #PALETTE}; the dialog offers the same chips. Colours are stored
 * as {@code #AARRGGBB} strings, the same encoding the settings file uses.
 */
public final class WaypointColors {
    /** VANTA accent violet, the first palette entry. */
    public static final int DEFAULT = 0xFF7C5CFF;

    /** Eight distinguishable marker colours (violet, blue, cyan, green, yellow, orange, red, pink). */
    public static final List<Integer> PALETTE = List.of(
            DEFAULT, 0xFF4F8DFF, 0xFF2BD4D4, 0xFF43D17A, 0xFFF5C84B, 0xFFFF8A3D, 0xFFFF5C5C, 0xFFFF6BC1);

    private WaypointColors() {
    }

    /** Palette entry for the n-th waypoint (wraps around). */
    public static int forIndex(int index) {
        return PALETTE.get(Math.floorMod(index, PALETTE.size()));
    }

    /** {@code #AARRGGBB}, upper case. */
    public static String toHex(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    /**
     * Parses {@code #AARRGGBB} / {@code #RRGGBB} (alpha becomes opaque) or a plain integer; empty for anything else.
     */
    public static Optional<Integer> parse(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        if (element.getAsJsonPrimitive().isNumber()) {
            try {
                return Optional.of((int) element.getAsLong());
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        if (!element.getAsJsonPrimitive().isString()) {
            return Optional.empty();
        }
        return parse(element.getAsString());
    }

    /** Parses {@code #AARRGGBB} or {@code #RRGGBB} (alpha becomes opaque); empty for anything else. */
    public static Optional<Integer> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String hex = text.strip();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6 && hex.length() != 8) {
            return Optional.empty();
        }
        try {
            long value = Long.parseLong(hex, 16);
            if (hex.length() == 6) {
                value |= 0xFF000000L;
            }
            return Optional.of((int) value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
