package dev.vanta.core.bridge;

import java.util.Objects;

/**
 * Snapshot of an active status effect.
 *
 * @param name          translated effect name
 * @param color         effect colour (RGB, alpha ignored)
 * @param durationTicks remaining ticks (20 per second); ignored when {@code infinite}
 * @param amplifier     0 for level I, 1 for level II, …
 * @param infinite      true for effects without an end (beacons, commands)
 */
public record EffectInfo(String name, int color, int durationTicks, int amplifier, boolean infinite) {
    public EffectInfo {
        Objects.requireNonNull(name, "name");
        durationTicks = Math.max(0, durationTicks);
        amplifier = Math.max(0, amplifier);
    }

    /** Remaining duration as {@code m:ss}, or the infinity sign for infinite effects. */
    public String formatDuration() {
        if (infinite) {
            return "∞";
        }
        int seconds = durationTicks / 20;
        int minutes = seconds / 60;
        seconds %= 60;
        if (minutes >= 60) {
            int hours = minutes / 60;
            minutes %= 60;
            return hours + ":" + pad(minutes) + ":" + pad(seconds);
        }
        return minutes + ":" + pad(seconds);
    }

    /** Roman numeral for the level ("" for level I, "II", "III", …; numbers above X use digits). */
    public String levelLabel() {
        return switch (amplifier) {
            case 0 -> "";
            case 1 -> "II";
            case 2 -> "III";
            case 3 -> "IV";
            case 4 -> "V";
            case 5 -> "VI";
            case 6 -> "VII";
            case 7 -> "VIII";
            case 8 -> "IX";
            case 9 -> "X";
            default -> Integer.toString(amplifier + 1);
        };
    }

    private static String pad(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
