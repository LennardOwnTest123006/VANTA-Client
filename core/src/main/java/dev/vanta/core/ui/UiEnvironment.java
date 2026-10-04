package dev.vanta.core.ui;

import java.util.Objects;
import java.util.function.Function;

/**
 * Everything a {@link UiScreen} needs from its host to run: services, theme, clock, text metrics and the
 * translation lookup.
 *
 * @param host    host services (clipboard, sounds, navigation)
 * @param theme   active theme
 * @param clock   time source for animations
 * @param metrics text measuring available during layout
 * @param lang    translation lookup ({@code key -> text}); identity when no bundle is available
 */
public record UiEnvironment(UiHost host, Theme theme, Clock clock, TextMetrics metrics, Function<String, String> lang) {

    public UiEnvironment {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(lang, "lang");
    }

    /** Environment without translations (keys are returned verbatim). */
    public static UiEnvironment of(UiHost host, Theme theme, Clock clock, TextMetrics metrics) {
        return new UiEnvironment(host, theme, clock, metrics, Function.identity());
    }

    /** Copy with a different theme. */
    public UiEnvironment withTheme(Theme newTheme) {
        return new UiEnvironment(host, newTheme, clock, metrics, lang);
    }
}
