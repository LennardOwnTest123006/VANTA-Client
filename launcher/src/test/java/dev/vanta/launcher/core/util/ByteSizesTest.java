package dev.vanta.launcher.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Decimal sizes with one decimal place, matching the website's {@code formatBytes} and the release notes. */
class ByteSizesTest {

    @Test
    void decimalUnitsWithOneDecimal() {
        assertEquals("0 B", ByteSizes.format(0));
        assertEquals("999 B", ByteSizes.format(999));
        assertEquals("1.0 kB", ByteSizes.format(1_000));
        assertEquals("12.3 kB", ByteSizes.format(12_288));
        assertEquals("1.4 MB", ByteSizes.format(1_400_000));
        assertEquals("1.4 MB", ByteSizes.format(1_449_999));
        assertEquals("66.9 MB", ByteSizes.format(66_912_345));
        assertEquals("4.2 MB", ByteSizes.format(4_194_304), "a binary 4 MiB is 4.2 MB");
        assertEquals("1.0 GB", ByteSizes.format(1_000_000_000L));
        assertEquals("2.7 GB", ByteSizes.format(2_684_354_560L));
        assertEquals("1.5 TB", ByteSizes.format(1_500_000_000_000L));
        assertEquals("0 B", ByteSizes.format(-5), "negative counts never print a minus sign");
        assertEquals(new ByteSizes.Size("66.9", "MB"), ByteSizes.size(66_912_345));
    }

    /**
     * The same vectors are in {@code scripts/release/release-assets.test.mjs} ({@code formatSize}, the release notes) and
     * {@code website/src/lib/format.test.ts} ({@code formatBytes}), so a size reads the same in the release notes, on
     * the download page and in the launcher.
     */
    @Test
    void roundsHalfUpInIntegerArithmeticAndNeverPrints1000OfAUnit() {
        final Object[][] vectors = {
            {999L, "999 B"},
            {1_049L, "1.0 kB"},
            {1_050L, "1.1 kB"},
            {1_150L, "1.2 kB"},
            {1_450_000L, "1.5 MB"},
            {2_450_000L, "2.5 MB"},
            {999_949L, "999.9 kB"},
            {999_950L, "1.0 MB"},
            {999_999L, "1.0 MB"},
            {1_000_000L, "1.0 MB"},
            {1_437_322L, "1.4 MB"},
            {66_900_000L, "66.9 MB"},
            {999_949_999L, "999.9 MB"},
            {999_950_000L, "1.0 GB"},
            {999_950_000_000L, "1.0 TB"},
            {1_000_000_000_000_000L, "1000.0 TB"},
            {9_007_199_254_740_991L, "9007.2 TB"},
            {Long.MAX_VALUE, "9223372.0 TB"},
        };
        for (final Object[] vector : vectors) {
            assertEquals(vector[1], ByteSizes.format((Long) vector[0]), vector[0] + " bytes");
        }
    }
}
