package dev.vanta.launcher.core.install;

import java.io.IOException;

/**
 * The releases base URL in effect cannot be used: it is empty (only possible when a caller passes no default) or it
 * is not an absolute http(s) URL. The launcher then never guesses another location; the user corrects the setting
 * ({@code "releasesBaseUrl"} / {@code VANTA_RELEASES_BASE_URL}), resets it to the built-in default, or installs a
 * local build with {@code --client-jar}.
 */
public final class ReleasesNotConfiguredException extends IOException {

    private static final long serialVersionUID = 1L;

    private final String url;

    /**
     * @param url     the unusable URL (empty when none)
     * @param message details
     */
    public ReleasesNotConfiguredException(final String url, final String message) {
        super(message);
        this.url = url == null ? "" : url;
    }

    /** @return the unusable URL (empty when none) */
    public String url() {
        return url;
    }
}
