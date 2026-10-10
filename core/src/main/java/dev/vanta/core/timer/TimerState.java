package dev.vanta.core.timer;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;

/** What the timer is doing. */
public enum TimerState implements LangKeyed {
    /** No timer is set. */
    IDLE,
    /** Counting down. */
    RUNNING,
    /** Stopped part-way; the remaining time is kept until it is resumed or cancelled. */
    PAUSED,
    /** The time is up: the alarm rings (a sound every second) until the player stops it. */
    RINGING;

    /** Lower-case id used in lang keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.timer.state." + id();
    }

    /** True while a timer exists: running, paused or ringing. */
    public boolean isActive() {
        return this != IDLE;
    }
}
