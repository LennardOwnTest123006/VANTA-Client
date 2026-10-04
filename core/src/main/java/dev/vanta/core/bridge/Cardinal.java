package dev.vanta.core.bridge;

import dev.vanta.core.i18n.LangKeyed;

/**
 * The four horizontal directions a player can face, derived from the yaw angle with Minecraft's convention
 * (yaw 0 = south, 90 = west, 180 = north, 270 = east).
 */
public enum Cardinal implements LangKeyed {
    NORTH("N", "vanta.direction.north", "vanta.direction.north.axis"),
    EAST("E", "vanta.direction.east", "vanta.direction.east.axis"),
    SOUTH("S", "vanta.direction.south", "vanta.direction.south.axis"),
    WEST("W", "vanta.direction.west", "vanta.direction.west.axis");

    private final String letter;
    private final String langKey;
    private final String axisKey;

    Cardinal(String letter, String langKey, String axisKey) {
        this.letter = letter;
        this.langKey = langKey;
        this.axisKey = axisKey;
    }

    /** Single letter abbreviation (N/E/S/W). */
    public String letter() {
        return letter;
    }

    @Override
    public String langKey() {
        return langKey;
    }

    /** Translation key of the axis description, e.g. "Towards negative Z". */
    public String axisKey() {
        return axisKey;
    }

    /** Minecraft direction name ({@code north}, …) as used by vanilla F3. */
    public String minecraftName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Converts a yaw angle (any range, degrees) to the nearest cardinal direction.
     */
    public static Cardinal fromYaw(double yaw) {
        double normalized = ((yaw % 360.0) + 360.0) % 360.0;
        if (normalized >= 315.0 || normalized < 45.0) {
            return SOUTH;
        }
        if (normalized < 135.0) {
            return WEST;
        }
        if (normalized < 225.0) {
            return NORTH;
        }
        return EAST;
    }

    /** Yaw normalised to [-180, 180) as shown by vanilla F3. */
    public static double normalizeYaw(double yaw) {
        double n = ((yaw % 360.0) + 540.0) % 360.0 - 180.0;
        return n;
    }
}
