package dev.vanta.launcher.core.install;

import java.io.IOException;

/**
 * The release manifest exists but has no download URL yet ("No public release yet"), or no releases base URL
 * is configured at all.
 */
public final class NotPublishedException extends IOException {

    private static final long serialVersionUID = 1L;

    private final String product;
    private final String version;

    /**
     * @param product product name
     * @param version version in the manifest (may be empty)
     * @param message details
     */
    public NotPublishedException(final String product, final String version, final String message) {
        super(message);
        this.product = product;
        this.version = version == null ? "" : version;
    }

    /** @return product */
    public String product() {
        return product;
    }

    /** @return version */
    public String version() {
        return version;
    }
}
