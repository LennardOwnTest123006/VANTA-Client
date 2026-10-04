package dev.vanta.core.notifications;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;

/**
 * Severity of a notification; decides icon and accent colour.
 */
public enum NotificationKind implements LangKeyed {
    INFO("info"),
    SUCCESS("success"),
    WARNING("warning"),
    ERROR("error");

    private final String iconId;

    NotificationKind(String iconId) {
        this.iconId = iconId;
    }

    /** Default icon id for the kind. */
    public String iconId() {
        return iconId;
    }

    @Override
    public String langKey() {
        return "vanta.notifications.kind." + name().toLowerCase(Locale.ROOT);
    }
}
