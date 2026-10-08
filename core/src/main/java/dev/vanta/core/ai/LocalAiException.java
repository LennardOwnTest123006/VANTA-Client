package dev.vanta.core.ai;

import dev.vanta.core.i18n.LangKeyed;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/**
 * Anything that stops the Local AI from installing or answering, with a {@link Kind} the UI can translate.
 */
public final class LocalAiException extends IOException {
    private static final long serialVersionUID = 1L;

    /** Failure categories; each has a lang key {@code vanta.localai.error.<kind>}. */
    public enum Kind implements LangKeyed {
        /** Connection problem or timeout while downloading or talking to the server. */
        NETWORK,
        /** The download source answered with an error status. */
        HTTP,
        /** A downloaded file's SHA-256 differs from the manifest. */
        HASH_MISMATCH,
        /** A downloaded file's size differs from the manifest. */
        SIZE_MISMATCH,
        /** The runtime archive could not be read or lacks the server executable. */
        INVALID_ARCHIVE,
        /** The manifest is incomplete or malformed. */
        INVALID_MANIFEST,
        /** No CPU build for this os/arch. */
        UNSUPPORTED_PLATFORM,
        /** The install lives in the launcher's folder; the client never writes there. */
        READ_ONLY,
        /** A local file could not be written, moved or deleted. */
        LOCAL_IO,
        /** The server process could not be started or stopped answering. */
        PROCESS,
        /** The server's reply was not the JSON the assistant expects. */
        INVALID_RESPONSE,
        /** Runtime or model are not installed yet. */
        NOT_INSTALLED,
        /** The Nexus assistant is turned off in the settings. */
        DISABLED,
        /** A question is still being answered. */
        BUSY,
        /** The player cancelled the install. */
        CANCELLED;

        /** Lower-case id used in lang keys. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Override
        public String langKey() {
            return "vanta.localai.error." + id();
        }
    }

    private final Kind kind;

    public LocalAiException(Kind kind, String message) {
        super(message);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public LocalAiException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    /** The failure category. */
    public Kind kind() {
        return kind;
    }

    /** Wraps any throwable: a {@code LocalAiException} is returned as is, interrupts become CANCELLED. */
    public static LocalAiException wrap(Throwable t) {
        if (t instanceof LocalAiException e) {
            return e;
        }
        if (t instanceof InterruptedException) {
            return new LocalAiException(Kind.CANCELLED, "interrupted", t);
        }
        if (t instanceof IOException) {
            return new LocalAiException(Kind.LOCAL_IO, String.valueOf(t.getMessage()), t);
        }
        return new LocalAiException(Kind.PROCESS, String.valueOf(t.getMessage()), t);
    }
}
