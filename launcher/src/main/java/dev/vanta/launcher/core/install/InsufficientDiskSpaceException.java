package dev.vanta.launcher.core.install;

/**
 * Not enough free disk space for the installation.
 */
public final class InsufficientDiskSpaceException extends InstallException {

    private static final long serialVersionUID = 1L;

    private final long requiredBytes;
    private final long availableBytes;

    /**
     * @param requiredBytes  bytes needed
     * @param availableBytes bytes available
     */
    public InsufficientDiskSpaceException(final long requiredBytes, final long availableBytes) {
        super(InstallStep.VERSION_JSON, "Not enough disk space: " + mb(requiredBytes) + " MB required but only "
            + mb(availableBytes) + " MB available");
        this.requiredBytes = requiredBytes;
        this.availableBytes = availableBytes;
    }

    private static long mb(final long bytes) {
        return (bytes + (1024L * 1024L) - 1) / (1024L * 1024L);
    }

    /** @return bytes needed */
    public long requiredBytes() {
        return requiredBytes;
    }

    /** @return bytes available */
    public long availableBytes() {
        return availableBytes;
    }
}
