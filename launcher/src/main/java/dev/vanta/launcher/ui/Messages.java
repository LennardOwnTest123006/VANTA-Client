package dev.vanta.launcher.ui;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Localised UI strings backed by {@code dev/vanta/launcher/ui/i18n/launcher_<lang>.properties}.
 *
 * <p>Every string shown by the launcher UI goes through this class; nothing is hard-coded in the views. A missing
 * key is a programming error, so {@link #get(String)} fails loudly with the key name instead of returning
 * {@code null}, and the unit tests verify that every key referenced from the UI sources exists.</p>
 */
public final class Messages {

    /** Base name of the resource bundle. */
    public static final String BUNDLE = "dev.vanta.launcher.ui.i18n.launcher";

    private final ResourceBundle bundle;

    /**
     * @param bundle bundle
     */
    public Messages(final ResourceBundle bundle) {
        this.bundle = Objects.requireNonNull(bundle, "bundle");
    }

    /**
     * Loads the bundle for a locale, falling back to English.
     *
     * @param locale preferred locale
     * @return messages
     */
    public static Messages load(final Locale locale) {
        try {
            return new Messages(ResourceBundle.getBundle(BUNDLE, locale, new EnglishFallback()));
        } catch (MissingResourceException e) {
            return new Messages(ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH));
        }
    }

    /** @return the English bundle */
    public static Messages english() {
        return load(Locale.ENGLISH);
    }

    /**
     * @param key message key
     * @return the raw string (no formatting)
     * @throws MissingResourceException when the key is unknown
     */
    public String get(final String key) {
        return bundle.getString(key).replace("''", "'");
    }

    /**
     * Formats a message with {@link MessageFormat}.
     *
     * @param key  message key
     * @param args arguments
     * @return formatted string
     */
    public String format(final String key, final Object... args) {
        final String pattern = bundle.getString(key);
        if (args == null || args.length == 0) {
            return pattern.replace("''", "'");
        }
        return new MessageFormat(pattern, bundle.getLocale()).format(args);
    }

    /**
     * @param key message key
     * @return whether the key exists
     */
    public boolean has(final String key) {
        return bundle.containsKey(key);
    }

    /** @return all keys of the bundle */
    public Set<String> keys() {
        return bundle.keySet();
    }

    /** @return the bundle's locale */
    public Locale locale() {
        return bundle.getLocale();
    }

    /** Bundle control that falls back to English instead of the JVM default locale. */
    private static final class EnglishFallback extends ResourceBundle.Control {
        @Override
        public Locale getFallbackLocale(final String baseName, final Locale locale) {
            return Locale.ENGLISH.equals(locale) ? null : Locale.ENGLISH;
        }
    }
}
