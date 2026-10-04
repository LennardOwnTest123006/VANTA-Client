package dev.vanta.core.profiles;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;

/**
 * Thrown when a profile file cannot be imported. {@link #reason()} maps to a translated message.
 */
public final class ProfileImportException extends Exception {
    private static final long serialVersionUID = 1L;

    /** Why the import failed. */
    public enum Reason implements LangKeyed {
        /** File larger than the 1 MiB limit. */
        TOO_LARGE,
        /** Not valid JSON / not an object. */
        NOT_JSON,
        /** schemaVersion newer than this VANTA understands. */
        UNSUPPORTED_SCHEMA,
        /** A required field is missing or malformed. */
        INVALID_FIELD,
        /** Could not read the file. */
        IO_ERROR;

        @Override
        public String langKey() {
            return "vanta.profiles.import_error." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final Reason reason;

    public ProfileImportException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ProfileImportException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /** Failure category. */
    public Reason reason() {
        return reason;
    }
}
