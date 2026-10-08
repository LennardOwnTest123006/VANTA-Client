package dev.vanta.core.ai;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;
import java.util.Objects;

/**
 * One action the assistant proposed that validation refused, with the reason the reply explains to the player.
 *
 * @param type   action type as sent by the model (may be unknown)
 * @param reason why it was refused
 * @param detail the offending value or a hint ({@code memory-3}, {@code controls.autoJump})
 */
public record RejectedAction(String type, Reason reason, String detail) {
    /** Why an action was refused; each has a lang key {@code vanta.nexus.rejected.<reason>} taking the detail. */
    public enum Reason implements LangKeyed {
        UNKNOWN_TYPE,
        MISSING_FIELD,
        INVALID_VALUE,
        UNKNOWN_ELEMENT,
        UNKNOWN_SETTING,
        FORBIDDEN_SETTING,
        UNKNOWN_PRESET,
        UNKNOWN_PROFILE,
        UNKNOWN_LAYOUT,
        UNKNOWN_WAYPOINT,
        UNKNOWN_FEATURE,
        NOT_AVAILABLE,
        FAILED;

        /** Lower-case id used in lang keys. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Override
        public String langKey() {
            return "vanta.nexus.rejected." + id();
        }
    }

    public RejectedAction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(detail, "detail");
    }

    /** Translated explanation. */
    public String summary() {
        return Lang.tr(reason.langKey(), detail);
    }
}
