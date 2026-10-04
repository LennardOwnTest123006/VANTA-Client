package dev.vanta.launcher.core.net;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A downloaded file did not match its expected size or digest. The offending file has already been deleted.
 */
public final class IntegrityException extends IOException {

    private static final long serialVersionUID = 1L;

    private final transient Path file;

    /**
     * @param file    file that failed verification
     * @param message details
     */
    public IntegrityException(final Path file, final String message) {
        super(message);
        this.file = file;
    }

    /** @return the file that failed verification */
    public Path file() {
        return file;
    }
}
