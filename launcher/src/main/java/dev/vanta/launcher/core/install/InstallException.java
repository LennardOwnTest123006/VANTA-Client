package dev.vanta.launcher.core.install;

import java.io.IOException;

/**
 * Installation failed. The cause describes the underlying problem (network, integrity, disk space, ...).
 */
public class InstallException extends IOException {

    private static final long serialVersionUID = 1L;

    private final InstallStep step;

    /**
     * @param step    step that failed
     * @param message message
     */
    public InstallException(final InstallStep step, final String message) {
        super(message);
        this.step = step;
    }

    /**
     * @param step    step that failed
     * @param message message
     * @param cause   cause
     */
    public InstallException(final InstallStep step, final String message, final Throwable cause) {
        super(message, cause);
        this.step = step;
    }

    /** @return failed step */
    public InstallStep step() {
        return step;
    }
}
