package dev.vanta.core.notifications;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;

/**
 * Screen corner where toasts appear.
 */
public enum NotificationPosition implements LangKeyed {
    TOP_RIGHT,
    TOP_LEFT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT;

    /** True for the two top corners. */
    public boolean isTop() {
        return this == TOP_RIGHT || this == TOP_LEFT;
    }

    /** True for the two right corners. */
    public boolean isRight() {
        return this == TOP_RIGHT || this == BOTTOM_RIGHT;
    }

    @Override
    public String langKey() {
        return "vanta.notifications.position." + name().toLowerCase(Locale.ROOT);
    }
}
