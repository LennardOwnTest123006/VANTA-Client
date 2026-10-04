package dev.vanta.core.hud;

import dev.vanta.core.i18n.LangKeyed;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Schema of one widget-specific property (stored as a string in {@link HudWidgetState#props()}).
 *
 * @param key          property key, e.g. {@code showLabel}
 * @param kind         value kind
 * @param defaultValue default as string ({@code true}, {@code 0}, {@code system_24h})
 * @param options      allowed values for {@link Kind#ENUM}, lower-case
 * @param min          minimum for {@link Kind#INT}
 * @param max          maximum for {@link Kind#INT}
 */
public record HudProp(String key, Kind kind, String defaultValue, List<String> options, int min, int max)
        implements LangKeyed {

    /** Value kind of a property. */
    public enum Kind {
        BOOL, INT, ENUM
    }

    public HudProp {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(defaultValue, "defaultValue");
        options = List.copyOf(options);
    }

    /** Boolean property. */
    public static HudProp bool(String key, boolean defaultValue) {
        return new HudProp(key, Kind.BOOL, Boolean.toString(defaultValue), List.of(), 0, 0);
    }

    /** Integer property. */
    public static HudProp integer(String key, int defaultValue, int min, int max) {
        return new HudProp(key, Kind.INT, Integer.toString(defaultValue), List.of(), min, max);
    }

    /** Enum property over lower-case option ids. */
    public static HudProp choice(String key, String defaultValue, String... options) {
        return new HudProp(key, Kind.ENUM, defaultValue.toLowerCase(Locale.ROOT), List.of(options), 0, 0);
    }

    @Override
    public String langKey() {
        return "vanta.hud.prop." + snake(key);
    }

    /** Translation key of one enum option. */
    public String optionLangKey(String option) {
        return "vanta.hud.prop." + snake(key) + "." + option.toLowerCase(Locale.ROOT);
    }

    /** Validates / normalises a raw string value; empty when invalid. */
    public Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim();
        switch (kind) {
            case BOOL -> {
                if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                    return Optional.of(value.toLowerCase(Locale.ROOT));
                }
                return Optional.empty();
            }
            case INT -> {
                try {
                    int n = Integer.parseInt(value);
                    return Optional.of(Integer.toString(Math.max(min, Math.min(max, n))));
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            }
            case ENUM -> {
                String lower = value.toLowerCase(Locale.ROOT);
                return options.contains(lower) ? Optional.of(lower) : Optional.empty();
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    /** Boolean view of a value (default when absent/invalid). */
    public boolean asBoolean(String raw) {
        return Boolean.parseBoolean(normalize(raw).orElse(defaultValue));
    }

    /** Integer view of a value (default when absent/invalid). */
    public int asInt(String raw) {
        return Integer.parseInt(normalize(raw).orElse(defaultValue));
    }

    static String snake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
