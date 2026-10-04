package dev.vanta.core.screen.cosmetics;

import dev.vanta.core.i18n.Lang;

/**
 * Count labels with a singular form: {@code count(1, "vanta.profiles.count")} uses the key
 * {@code vanta.profiles.count.one} when the language file defines it ("1 profile") and otherwise formats the
 * plural key with the number ("%s profiles").
 */
public final class Plurals {
    private Plurals() {
    }

    /** Formats a count with the singular variant when {@code n == 1} and {@code <key>.one} exists. */
    public static String count(long n, String key) {
        if (n == 1 && Lang.has(key + ".one")) {
            return Lang.tr(key + ".one");
        }
        return Lang.tr(key, n);
    }
}
