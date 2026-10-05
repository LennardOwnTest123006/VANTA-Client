package dev.vanta.launcher.core.settings;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The releases base URL the launcher actually uses, and where it came from.
 *
 * <p>Precedence: a non-empty {@code "releasesBaseUrl"} in {@code settings.json} (or {@code --releases-url} on the
 * command line, which overrides the setting for that run) &gt; the environment variable
 * {@value LauncherSettings#RELEASES_BASE_URL_ENV} &gt; the built-in default
 * {@link LauncherSettings#DEFAULT_RELEASES_BASE_URL}. The URL points at the directory that holds
 * {@code client-latest.json} and {@code launcher-latest.json}.</p>
 *
 * @param url    the URL in effect (trimmed, never null)
 * @param source where the URL came from
 */
public record ReleasesBaseUrl(String url, Source source) {

    /** Origin of the URL in effect. */
    public enum Source {
        /** {@code settings.json} or {@code --releases-url}. */
        SETTINGS,
        /** The environment variable {@value LauncherSettings#RELEASES_BASE_URL_ENV}. */
        ENVIRONMENT,
        /** The built-in default. */
        DEFAULT
    }

    public ReleasesBaseUrl {
        url = url == null ? "" : url.trim();
        Objects.requireNonNull(source, "source");
    }

    /**
     * Resolves the URL in effect with the built-in default.
     *
     * @param configured value of {@code "releasesBaseUrl"} in the settings (may be empty or null)
     * @param env        environment variables
     * @return URL in effect
     */
    public static ReleasesBaseUrl resolve(final String configured, final Map<String, String> env) {
        return resolve(configured, env, LauncherSettings.DEFAULT_RELEASES_BASE_URL);
    }

    /**
     * Resolves the URL in effect.
     *
     * @param configured     value of {@code "releasesBaseUrl"} in the settings (may be empty or null)
     * @param env            environment variables
     * @param builtInDefault the default used when neither the settings nor the environment provide a value
     * @return URL in effect
     */
    public static ReleasesBaseUrl resolve(final String configured, final Map<String, String> env, final String builtInDefault) {
        if (configured != null && !configured.isBlank()) {
            return new ReleasesBaseUrl(configured, Source.SETTINGS);
        }
        final String fromEnv = env == null ? null : env.get(LauncherSettings.RELEASES_BASE_URL_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return new ReleasesBaseUrl(fromEnv, Source.ENVIRONMENT);
        }
        return new ReleasesBaseUrl(builtInDefault, Source.DEFAULT);
    }

    /** @return whether the URL is an absolute http(s) URL with a host */
    public boolean isValid() {
        return isHttpUrl(url);
    }

    /** @return whether the built-in default is in effect */
    public boolean isDefault() {
        return source == Source.DEFAULT;
    }

    /**
     * @param value candidate
     * @return whether the value is an absolute http(s) URL with a host
     */
    public static boolean isHttpUrl(final String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        final String trimmed = value.trim();
        final String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        try {
            final URI uri = URI.create(trimmed);
            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
