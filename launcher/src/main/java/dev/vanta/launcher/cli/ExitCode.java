package dev.vanta.launcher.cli;

/**
 * Process exit codes of the command line interface.
 */
public enum ExitCode {
    /** Success. */
    OK(0, "success"),
    /** Unexpected failure. */
    FAILURE(1, "unexpected failure"),
    /** Invalid command line. */
    USAGE(2, "invalid command line"),
    /** A required setting is missing (Microsoft client id, releases URL). */
    NOT_CONFIGURED(3, "not configured"),
    /** A download failed verification. */
    INTEGRITY(4, "integrity check failed"),
    /** Network or server failure. */
    NETWORK(5, "network failure"),
    /** No suitable Java runtime. */
    JAVA_NOT_FOUND(6, "no Java 21 runtime found"),
    /** The requested release is not published yet. */
    NOT_PUBLISHED(7, "release not published"),
    /** The operation was cancelled. */
    CANCELLED(8, "cancelled"),
    /** Sign-in failed or no account is available. */
    AUTH(9, "authentication failed"),
    /** The game exited with a non-zero status. */
    GAME_FAILED(10, "game exited with an error"),
    /** Not enough free disk space. */
    DISK_SPACE(11, "insufficient disk space");

    private final int code;
    private final String description;

    ExitCode(final int code, final String description) {
        this.code = code;
        this.description = description;
    }

    /** @return numeric exit code */
    public int code() {
        return code;
    }

    /** @return short description */
    public String description() {
        return description;
    }
}
