package dev.vanta.launcher.core.util;

import java.util.List;

/**
 * File sizes in decimal units with one decimal place ({@code 1 kB = 1000 B}, {@code 1 MB = 1,000,000 B}): {@code 999 B},
 * {@code 1.4 MB}, {@code 66.9 MB}, {@code 1.2 GB}. Used by the launcher UI ({@code Formats}) and every command line
 * message. The rule is the same as {@code formatBytes()} in {@code website/src/lib/format.ts} (Download page) and
 * {@code formatSize()} in {@code scripts/release/release-assets.mjs} (release notes), so a size reads the same
 * everywhere: pick the largest unit not above the byte count, round half up to tenths of that unit with integer
 * arithmetic (no binary floating point, so 1,450,000 bytes is {@code 1.5 MB}), and if that gives 1000.0 move up one
 * unit ({@code 1.0 MB} for 999,950 bytes, never {@code 1000.0 kB}).
 */
public final class ByteSizes {

    /** Units from bytes upwards (decimal prefixes). */
    public static final List<String> UNITS = List.of("B", "kB", "MB", "GB", "TB");

    private ByteSizes() {
    }

    /**
     * A size split into its number and unit.
     *
     * @param value number text: an integer for bytes, else one decimal place ({@code "4.2"})
     * @param unit  one of {@link #UNITS}
     */
    public record Size(String value, String unit) {

        @Override
        public String toString() {
            return value + " " + unit;
        }
    }

    /**
     * Splits a byte count into number and unit.
     *
     * @param bytes byte count (negative values are treated as 0)
     * @return number and unit
     */
    public static Size size(final long bytes) {
        final long count = Math.max(0L, bytes);
        if (count < 1000) {
            return new Size(Long.toString(count), UNITS.get(0));
        }
        final int last = UNITS.size() - 1;
        int unit = 1;
        while (unit < last && count >= pow10(3 * (unit + 1))) {
            unit++;
        }
        long tenths = roundedTenths(count, unit);
        if (tenths >= 10_000 && unit < last) {
            unit++;
            tenths = roundedTenths(count, unit);
        }
        return new Size((tenths / 10) + "." + (tenths % 10), UNITS.get(unit));
    }

    /** {@code bytes} in tenths of {@code UNITS.get(unit)} (unit at least 1), rounded half up. */
    private static long roundedTenths(final long bytes, final int unit) {
        final long divisor = pow10(3 * unit - 1); // one tenth of the unit in bytes
        final long remainder = bytes % divisor;
        final long tenths = bytes / divisor;
        return remainder * 2 >= divisor ? tenths + 1 : tenths;
    }

    private static long pow10(final int exponent) {
        long result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= 10;
        }
        return result;
    }

    /**
     * @param bytes byte count
     * @return e.g. {@code 512 B}, {@code 4.2 MB}, {@code 66.9 MB}
     */
    public static String format(final long bytes) {
        return size(bytes).toString();
    }
}
