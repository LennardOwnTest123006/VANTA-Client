package dev.vanta.launcher.core.install;

/**
 * Observer of an installation. Callbacks may arrive from worker threads.
 */
public interface InstallListener {

    /** Listener that ignores everything. */
    InstallListener NONE = new InstallListener() {
    };

    /**
     * Progress changed.
     *
     * @param progress snapshot
     */
    default void onProgress(final InstallProgress progress) {
    }

    /**
     * Informational message (what was skipped, what was reused, ...).
     *
     * @param message text
     */
    default void onLog(final String message) {
    }
}
