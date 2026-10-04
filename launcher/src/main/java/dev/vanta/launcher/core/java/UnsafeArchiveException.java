package dev.vanta.launcher.core.java;

import java.io.IOException;

/**
 * An archive entry tried to escape the extraction directory (path traversal) or used an unsupported entry type.
 */
public final class UnsafeArchiveException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message details including the offending entry name
     */
    public UnsafeArchiveException(final String message) {
        super(message);
    }
}
