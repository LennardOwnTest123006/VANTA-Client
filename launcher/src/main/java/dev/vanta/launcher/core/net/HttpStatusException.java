package dev.vanta.launcher.core.net;

import java.io.IOException;
import java.net.URI;

/**
 * Thrown when a server answers with an unexpected status code.
 */
public final class HttpStatusException extends IOException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final URI uri;

    /**
     * @param status status code
     * @param uri    requested URI
     */
    public HttpStatusException(final int status, final URI uri) {
        super("HTTP " + status + " for " + uri);
        this.status = status;
        this.uri = uri;
    }

    /** @return status code */
    public int status() {
        return status;
    }

    /** @return requested URI */
    public URI uri() {
        return uri;
    }

    /** @return whether a retry might succeed (5xx, 408, 429) */
    public boolean isRetryable() {
        return status >= 500 || status == 408 || status == 429;
    }
}
