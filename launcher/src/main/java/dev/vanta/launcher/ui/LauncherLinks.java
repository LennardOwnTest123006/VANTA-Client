package dev.vanta.launcher.ui;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * External links used by the UI, read from {@code dev/vanta/launcher/ui/links.properties}.
 *
 * <p>Links are configuration, not constants in code: an empty value means "not configured" and the UI disables the
 * corresponding button with an explanation. {@code VANTA_WEBSITE_URL} and {@code VANTA_SUPPORT_URL} override the
 * website and support links at run time so a distribution can point them elsewhere without a rebuild.</p>
 */
public final class LauncherLinks {

    /** Environment variable overriding the website link. */
    public static final String WEBSITE_ENV = "VANTA_WEBSITE_URL";
    /** Environment variable overriding the support link. */
    public static final String SUPPORT_ENV = "VANTA_SUPPORT_URL";

    private final Properties props;
    private final Map<String, String> env;

    /**
     * @param props link properties
     * @param env   environment variables (overrides)
     */
    public LauncherLinks(final Properties props, final Map<String, String> env) {
        this.props = Objects.requireNonNull(props, "props");
        this.env = Map.copyOf(Objects.requireNonNull(env, "env"));
    }

    /**
     * Loads the bundled links.
     *
     * @param env environment variables
     * @return links
     */
    public static LauncherLinks load(final Map<String, String> env) {
        final Properties props = new Properties();
        try (InputStream in = LauncherLinks.class.getResourceAsStream("/dev/vanta/launcher/ui/links.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // defaults below cover the essentials
        }
        return new LauncherLinks(props, env);
    }

    /** @return source repository */
    public URI source() {
        return required("source.url", "https://github.com/LennardOwnTest123006/VANTA-Client");
    }

    /** @return launcher documentation page */
    public URI launcherDocs() {
        return required("docs.launcher.url", source().toString() + "/blob/main/docs/launcher.md");
    }

    /** @return documentation section explaining the Microsoft client id */
    public URI clientIdDocs() {
        return required("docs.client-id.url", launcherDocs().toString() + "#microsoft-client-id");
    }

    /** @return Microsoft device login page */
    public URI microsoftLink() {
        return required("microsoft.link.url", "https://www.microsoft.com/link");
    }

    /** @return support link (issue tracker) when configured */
    public Optional<URI> support() {
        return optional("support.url", SUPPORT_ENV);
    }

    /** @return website when configured */
    public Optional<URI> website() {
        return optional("website.url", WEBSITE_ENV);
    }

    /**
     * Resolves a release manifest changelog reference to a fetchable URL.
     *
     * @param changelog manifest value: absolute URL, repository-relative path or empty
     * @return URL of the raw markdown when the reference can be resolved
     */
    public Optional<URI> changelogRaw(final String changelog) {
        return resolveChangelog(changelog, "changelog.raw.base", source().toString() + "/raw/main/");
    }

    /**
     * Resolves a changelog reference to a page a browser can show.
     *
     * @param changelog manifest value
     * @return browser URL
     */
    public Optional<URI> changelogPage(final String changelog) {
        return resolveChangelog(changelog, "changelog.browse.base", source().toString() + "/blob/main/");
    }

    private Optional<URI> resolveChangelog(final String changelog, final String baseKey, final String fallbackBase) {
        if (changelog == null || changelog.isBlank()) {
            return Optional.empty();
        }
        final String value = changelog.trim();
        if (isHttp(value)) {
            return parse(value);
        }
        if (value.contains("\n") || value.contains(" ") || !value.endsWith(".md")) {
            return Optional.empty();
        }
        final String base = props.getProperty(baseKey, fallbackBase).trim();
        return parse((base.endsWith("/") ? base : base + "/") + value.replaceFirst("^/+", ""));
    }

    private URI required(final String key, final String fallback) {
        return parse(props.getProperty(key, fallback).trim()).orElseGet(() -> URI.create(fallback));
    }

    private Optional<URI> optional(final String key, final String envName) {
        final String fromEnv = env.get(envName);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return parse(fromEnv.trim()).filter(u -> isHttp(u.toString()));
        }
        final String value = props.getProperty(key, "").trim();
        return value.isEmpty() ? Optional.empty() : parse(value).filter(u -> isHttp(u.toString()));
    }

    private static boolean isHttp(final String value) {
        final String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static Optional<URI> parse(final String value) {
        try {
            final URI uri = URI.create(value);
            return uri.getScheme() == null ? Optional.empty() : Optional.of(uri);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
