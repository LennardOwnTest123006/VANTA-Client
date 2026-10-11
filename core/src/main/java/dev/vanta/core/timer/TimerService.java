package dev.vanta.core.timer;

import dev.vanta.core.config.CoreLog;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * VANTA's countdown timer: one timer at a time, started from a preset or a custom duration of up to 24 hours, with
 * an optional label, pause/resume and cancel. When the time is up the timer rings: the service asks the host to play
 * the alarm sound at once and then every {@value #RING_INTERVAL_MS} ms, and to show the alarm window, until the player
 * stops it.
 * <p>
 * Time comes from the services {@link Clock} (wall-clock milliseconds), so the timer keeps counting in menus, in the
 * pause screen, without a world and while the game window is in the background. {@link #tick()} runs from
 * {@code VantaServices.tick()} on every client tick, also in menus; the alarm therefore starts within one tick (50 ms)
 * of the end time. A running timer is not kept when the game closes: it lives in memory only.
 * <p>
 * Render thread only, like every core service.
 */
public final class TimerService {
    /** Shortest timer: one second. */
    public static final long MIN_DURATION_MS = 1_000L;
    /** Longest timer: 24 hours. */
    public static final long MAX_DURATION_MS = 24L * 60L * 60L * 1_000L;
    /** Longest timer in whole minutes (1440). */
    public static final int MAX_MINUTES = (int) (MAX_DURATION_MS / 60_000L);
    /** Pause between two alarm sounds while the timer rings. */
    public static final long RING_INTERVAL_MS = 1_000L;
    /** Longest label the player can type. */
    public static final int MAX_LABEL_LENGTH = 32;
    /** The preset buttons, in minutes. */
    public static final List<Integer> PRESET_MINUTES = List.of(1, 5, 10, 15, 30, 60);

    private final Clock clock;
    private final Runnable alarmSound;
    private Runnable alarmWindow = () -> { };
    private TimerState state = TimerState.IDLE;
    private long durationMs;
    private long endsAt;
    private long pausedRemaining;
    private String label = "";
    private long rangAt = -1L;
    private long nextSoundAt;
    private int soundsPlayed;
    private long lastRangAt = -1L;
    private String lastLabel = "";
    private boolean soundFailureLogged;

    /**
     * @param clock      the services clock (wall-clock milliseconds)
     * @param alarmSound plays the alarm sound once (the services pass {@code GameBridge::playAlarmSound})
     */
    public TimerService(Clock clock, Runnable alarmSound) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.alarmSound = Objects.requireNonNull(alarmSound, "alarmSound");
    }

    /**
     * Installs the host's way of showing the alarm window. It is called when the time is up and on every tick while
     * the timer rings, so the host opens the window whenever it is not showing (the game may have replaced it, for
     * example when a world finished loading). The client installs one that opens {@code ScreenId.TIMER_ALARM}; until
     * then nothing is shown.
     */
    public void setAlarmWindow(Runnable showAlarmWindow) {
        this.alarmWindow = Objects.requireNonNull(showAlarmWindow, "showAlarmWindow");
    }

    // ---- commands ------------------------------------------------------------------------------------------------

    /**
     * Starts a timer, replacing the current one (a ringing alarm is stopped).
     *
     * @param millis duration from {@link #MIN_DURATION_MS} to {@link #MAX_DURATION_MS}
     * @param label  optional label (trimmed, at most {@link #MAX_LABEL_LENGTH} characters, {@code null} for none)
     * @return false (and nothing changes) when the duration is out of range
     */
    public boolean start(long millis, String label) {
        if (millis < MIN_DURATION_MS || millis > MAX_DURATION_MS) {
            return false;
        }
        if (state == TimerState.RINGING) {
            remember();
        }
        long now = clock.millis();
        this.durationMs = millis;
        this.label = cleanLabel(label);
        this.endsAt = now + millis;
        this.pausedRemaining = 0L;
        this.rangAt = -1L;
        this.soundsPlayed = 0;
        this.state = TimerState.RUNNING;
        return true;
    }

    /** Starts a timer of whole minutes (1 to {@link #MAX_MINUTES}); false when out of range. */
    public boolean startMinutes(int minutes, String label) {
        if (minutes < 1 || minutes > MAX_MINUTES) {
            return false;
        }
        return start(minutes * 60_000L, label);
    }

    /** Pauses a running timer; false in any other state. */
    public boolean pause() {
        if (state != TimerState.RUNNING) {
            return false;
        }
        pausedRemaining = Math.max(0L, Math.min(durationMs, endsAt - clock.millis()));
        state = TimerState.PAUSED;
        return true;
    }

    /** Resumes a paused timer with the time it had left; false in any other state. */
    public boolean resume() {
        if (state != TimerState.PAUSED) {
            return false;
        }
        endsAt = clock.millis() + pausedRemaining;
        state = TimerState.RUNNING;
        return true;
    }

    /** Pauses a running timer or resumes a paused one; false while idle or ringing. */
    public boolean togglePause() {
        return state == TimerState.RUNNING ? pause() : resume();
    }

    /**
     * Cancels the timer: a running or paused one is dropped, a ringing one is stopped.
     *
     * @return false when there was no timer
     */
    public boolean cancel() {
        if (state == TimerState.RINGING) {
            return stop();
        }
        if (state == TimerState.IDLE) {
            return false;
        }
        reset();
        return true;
    }

    /**
     * Stops the ringing alarm: no further sound, and the alarm window closes itself because the timer is idle again.
     *
     * @return false when the timer was not ringing
     */
    public boolean stop() {
        if (state != TimerState.RINGING) {
            return false;
        }
        remember();
        reset();
        return true;
    }

    // ---- ticking -------------------------------------------------------------------------------------------------

    /**
     * Once per client tick (also in menus): rings when the time is up, plays the alarm sound when it is due and asks
     * the host to show the alarm window while ringing.
     */
    public void tick() {
        long now = clock.millis();
        if (state == TimerState.RUNNING && now >= endsAt) {
            state = TimerState.RINGING;
            rangAt = endsAt;
            soundsPlayed = 0;
            nextSoundAt = now;
        }
        if (state != TimerState.RINGING) {
            return;
        }
        if (now >= nextSoundAt) {
            playSound();
            nextSoundAt += RING_INTERVAL_MS;
            if (nextSoundAt <= now) {
                // The game stalled for longer than an interval: ring once now, not once per missed second.
                nextSoundAt = now + RING_INTERVAL_MS;
            }
        }
        try {
            alarmWindow.run();
        } catch (RuntimeException e) {
            CoreLog.warn(e, "The alarm window could not be shown");
        }
    }

    private void playSound() {
        soundsPlayed++;
        try {
            alarmSound.run();
        } catch (RuntimeException e) {
            // The host must not throw (no sound device in CI); if it does anyway the window still shows.
            if (!soundFailureLogged) {
                soundFailureLogged = true;
                CoreLog.warn(e, "The alarm sound could not be played");
            }
        }
    }

    // ---- queries -------------------------------------------------------------------------------------------------

    public TimerState state() {
        return state;
    }

    /** True while a timer runs, is paused or rings. */
    public boolean isActive() {
        return state.isActive();
    }

    /** True while the alarm rings. */
    public boolean isRinging() {
        return state == TimerState.RINGING;
    }

    /** Time left: counting down while running, frozen while paused, 0 while idle or ringing. */
    public long remainingMillis() {
        return switch (state) {
            case RUNNING -> Math.max(0L, Math.min(durationMs, endsAt - clock.millis()));
            case PAUSED -> pausedRemaining;
            default -> 0L;
        };
    }

    /** The duration the current timer was started with (0 while idle). */
    public long durationMillis() {
        return state == TimerState.IDLE ? 0L : durationMs;
    }

    /** The label of the current timer (empty while idle or when none was typed). */
    public String label() {
        return state == TimerState.IDLE ? "" : label;
    }

    /** When a running timer will ring (epoch ms); empty unless running. */
    public OptionalLong endsAt() {
        return state == TimerState.RUNNING ? OptionalLong.of(endsAt) : OptionalLong.empty();
    }

    /** When the ringing timer's time was up (epoch ms); empty unless ringing. */
    public OptionalLong rangAt() {
        return state == TimerState.RINGING ? OptionalLong.of(rangAt) : OptionalLong.empty();
    }

    /** Alarm sounds requested for the current ring (0 unless ringing). */
    public int soundsPlayed() {
        return state == TimerState.RINGING ? soundsPlayed : 0;
    }

    /** When the last stopped alarm rang (epoch ms); empty before the first alarm of this game session. */
    public OptionalLong lastRangAt() {
        return lastRangAt < 0L ? OptionalLong.empty() : OptionalLong.of(lastRangAt);
    }

    /** The label of the last stopped alarm (empty when none). */
    public String lastLabel() {
        return lastLabel;
    }

    /** The snapshot the HUD draws. */
    public TimerReading reading() {
        return state == TimerState.IDLE ? TimerReading.IDLE : new TimerReading(state, remainingMillis(), label);
    }

    /** The clock the timer counts on. */
    public Clock clock() {
        return clock;
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** Trims the label, drops control characters and cuts it to {@link #MAX_LABEL_LENGTH} characters. */
    public static String cleanLabel(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), MAX_LABEL_LENGTH));
        raw.trim().codePoints().filter(cp -> !Character.isISOControl(cp)).forEach(cp -> {
            if (sb.codePointCount(0, sb.length()) < MAX_LABEL_LENGTH) {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString().trim();
    }

    private void remember() {
        lastRangAt = rangAt;
        lastLabel = label;
    }

    private void reset() {
        state = TimerState.IDLE;
        durationMs = 0L;
        endsAt = 0L;
        pausedRemaining = 0L;
        label = "";
        rangAt = -1L;
        soundsPlayed = 0;
    }
}
