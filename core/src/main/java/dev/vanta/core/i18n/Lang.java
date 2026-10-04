package dev.vanta.core.i18n;

import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Objects;

/**
 * Static translation facade used by every VANTA string that reaches the user.
 * <p>
 * {@code Lang.tr("vanta.menu.play")} returns the translated text; unknown keys return the key itself so a missing
 * translation is visible instead of crashing. Formatting uses {@link String#format} semantics ({@code %s},
 * {@code %1$s}, {@code %d}) exactly like Minecraft's own language files.
 * <p>
 * The default provider is the bundled {@code en_us.json}; the client calls {@link #install(LangProvider)} with a
 * provider backed by Minecraft's {@code I18n}.
 */
public final class Lang {
    private static volatile LangProvider provider;

    private Lang() {
    }

    /** Replaces the active provider. */
    public static void install(LangProvider newProvider) {
        provider = Objects.requireNonNull(newProvider, "provider");
    }

    /** Restores the bundled English strings. */
    public static void reset() {
        provider = null;
    }

    /** The active provider (lazily loads the bundled English file). */
    public static LangProvider provider() {
        LangProvider current = provider;
        if (current == null) {
            synchronized (Lang.class) {
                current = provider;
                if (current == null) {
                    current = JsonLangProvider.builtIn();
                    provider = current;
                }
            }
        }
        return current;
    }

    /** Translates {@code key}; unknown keys return the key itself. */
    public static String tr(String key) {
        Objects.requireNonNull(key, "key");
        return provider().lookup(key).orElse(key);
    }

    /**
     * Translates {@code key} and formats it with {@code args}. A pattern that cannot be formatted (bad placeholder
     * count or type) falls back to the raw pattern followed by the arguments instead of throwing.
     */
    public static String tr(String key, Object... args) {
        String pattern = tr(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        return format(pattern, args);
    }

    /** True when the active provider knows {@code key}. */
    public static boolean has(String key) {
        return provider().has(key);
    }

    /** {@link String#format} with {@link Locale#ROOT} and a non-throwing fallback. */
    public static String format(String pattern, Object... args) {
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (IllegalFormatException e) {
            StringBuilder sb = new StringBuilder(pattern);
            for (Object arg : args) {
                sb.append(' ').append(arg);
            }
            return sb.toString();
        }
    }
}
