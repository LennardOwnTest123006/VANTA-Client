package dev.vanta.core.modrinth;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Every failure of the Modrinth integration, classified so the UI can show a precise, translated message.
 */
public final class ModrinthException extends IOException {
    private static final long serialVersionUID = 1L;

    /** What went wrong. */
    public enum Kind {
        /** Modrinth could not be reached (DNS, connection, timeout). */
        NETWORK,
        /** Modrinth answered with an unexpected HTTP status. */
        HTTP,
        /** The project, version or file does not exist. */
        NOT_FOUND,
        /** Too many requests (HTTP 429) even after waiting as asked. */
        RATE_LIMITED,
        /** A download did not match the SHA-512 Modrinth published for it; the file was deleted. */
        HASH_MISMATCH,
        /** The response could not be parsed or misses required fields. */
        INVALID_RESPONSE,
        /** A file Modrinth returned is unusable here (unsafe name, no checksum, insecure URL, wrong extension). */
        UNSAFE_FILE,
        /** A file with the same name exists and was not installed by VANTA; it is never overwritten. */
        FILE_EXISTS,
        /** Reading or writing a local file failed. */
        LOCAL_IO,
        /** The operation was interrupted. */
        CANCELLED;

        /** Translation key of the player-facing message. */
        public String langKey() {
            return "vanta.mods.error." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final Kind kind;
    private final int status;
    private final Duration retryAfter;

    public ModrinthException(Kind kind, String message) {
        this(kind, message, 0, null, null);
    }

    public ModrinthException(Kind kind, String message, Throwable cause) {
        this(kind, message, 0, null, cause);
    }

    public ModrinthException(Kind kind, String message, int status, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.status = status;
        this.retryAfter = retryAfter;
    }

    /** The classification. */
    public Kind kind() {
        return kind;
    }

    /** HTTP status when the failure came from a response, otherwise 0. */
    public int status() {
        return status;
    }

    /** How long Modrinth asked to wait (rate limits). */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    /** Wraps any throwable into a {@link ModrinthException} (unchanged when it already is one). */
    public static ModrinthException wrap(Throwable error) {
        if (error instanceof ModrinthException m) {
            return m;
        }
        if (error instanceof IOException io) {
            return new ModrinthException(Kind.LOCAL_IO, String.valueOf(io.getMessage()), io);
        }
        return new ModrinthException(Kind.INVALID_RESPONSE, String.valueOf(error), error);
    }
}
