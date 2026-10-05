package dev.vanta.core.screen.mods;

import dev.vanta.core.ui.Colors;
import java.util.Locale;

/** Formatting helpers of the Mods &amp; Shaders screen. */
public final class ModsFormats {
    private static final int[] AVATAR_PALETTE = {
        0xFF7C5CFF, 0xFF3D8BFF, 0xFF1FB59A, 0xFFE0A030, 0xFFE0566B, 0xFF9B6BE8, 0xFF2BA7C9, 0xFF6BAA3C
    };

    private ModsFormats() {
    }

    /** Compact count: {@code 950}, {@code 12.3K}, {@code 236.8M}, {@code 1.2B}. */
    public static String compact(long value) {
        long v = Math.max(0L, value);
        if (v < 1_000L) {
            return Long.toString(v);
        }
        if (v < 1_000_000L) {
            return oneDecimal(v / 1_000.0) + "K";
        }
        if (v < 1_000_000_000L) {
            return oneDecimal(v / 1_000_000.0) + "M";
        }
        return oneDecimal(v / 1_000_000_000.0) + "B";
    }

    private static String oneDecimal(double value) {
        String text = String.format(Locale.ROOT, "%.1f", Math.floor(value * 10.0) / 10.0);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    /** First letter or digit of a name, upper-case ({@code ?} when there is none). */
    public static String avatarLetter(String name) {
        if (name != null) {
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (Character.isLetterOrDigit(c)) {
                    return String.valueOf(Character.toUpperCase(c));
                }
            }
        }
        return "?";
    }

    /**
     * Avatar colour: the project colour Modrinth published (darkened when it is too light for white text), otherwise
     * a stable colour derived from the key.
     */
    public static int avatarColor(int projectColor, String key) {
        if (projectColor >= 0) {
            int color = Colors.opaque(0xFF000000 | projectColor);
            return Colors.luminance(color) > 0.45 ? Colors.darken(color, 0.35f) : color;
        }
        int hash = key == null ? 0 : key.hashCode();
        return AVATAR_PALETTE[Math.floorMod(hash, AVATAR_PALETTE.length)];
    }
}
