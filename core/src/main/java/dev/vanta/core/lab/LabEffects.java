package dev.vanta.core.lab;

import java.util.Objects;

/**
 * The time curves behind the Vanta Lab features, pure core and clock-free: the client feeds the events (input,
 * movement, attacks, screen open and close) with its own millisecond clock and the renderers ask for the current
 * values with the same clock. Every curve returns its "feature off" value while the matching {@link LabFeature} is
 * disabled, so callers multiply or add without checking the toggle themselves.
 * <ul>
 *   <li>{@link #hudAlpha}: {@code DYNAMIC_HUD}. 1.0 while the last input is less than {@value #HUD_IDLE_MS} ms
 *       old, then fading to 0.0 over {@value #HUD_FADE_MS} ms; back to 1.0 immediately on the next input.</li>
 *   <li>{@link #crosshairSpread}: {@code ANIMATED_CROSSHAIR}. 0..1 from the horizontal movement speed (sprinting
 *       counts as 1) that eases out over {@value #MOVEMENT_HOLD_MS} + {@value #MOVEMENT_FADE_MS} ms after the
 *       last movement report, plus a {@value #ATTACK_PULSE_MS} ms pulse per attack.</li>
 *   <li>{@link #transitionProgress}: {@code SCREEN_TRANSITIONS}. 0..1 over {@value #TRANSITION_MS} ms after a
 *       screen opened, 1..0 over the same time after it started closing, 1.0 again once that close finished;
 *       1.0 while the feature is off.</li>
 * </ul>
 * All methods are render-thread only, like every other core service.
 */
public final class LabEffects {
    /** Input older than this hides the HUD (Dynamic HUD). */
    public static final long HUD_IDLE_MS = 10_000L;
    /** Duration of the HUD fade-out. */
    public static final long HUD_FADE_MS = 500L;
    /** Movement speed (blocks per tick) that counts as full spread: sprinting on flat ground. */
    public static final double SPRINT_SPEED = 0.28;
    /** A movement report keeps its full value this long (reports arrive every tick while moving). */
    public static final long MOVEMENT_HOLD_MS = 100L;
    /** The movement contribution fades out over this time after the hold. */
    public static final long MOVEMENT_FADE_MS = 400L;
    /** Length of the attack pulse. */
    public static final long ATTACK_PULSE_MS = 250L;
    /** Length of the open and close transitions. */
    public static final long TRANSITION_MS = 160L;

    private final LabSettings lab;
    private long lastInputAt = Long.MIN_VALUE;
    private double movementLevel;
    private long movementAt = Long.MIN_VALUE;
    private long attackAt = Long.MIN_VALUE;
    private long openedAt = Long.MIN_VALUE;
    private long closedAt = Long.MIN_VALUE;

    public LabEffects(LabSettings lab) {
        this.lab = Objects.requireNonNull(lab, "lab");
    }

    // ---- events --------------------------------------------------------------------------------------------------

    /** Any key, mouse button, cursor movement or scroll. */
    public void onInput(long nowMillis) {
        lastInputAt = nowMillis;
    }

    /** The player's horizontal speed this tick, in blocks per tick (0 while standing still). */
    public void onMovement(double horizontalSpeedBlocksPerTick, long nowMillis) {
        double speed = Double.isFinite(horizontalSpeedBlocksPerTick) ? Math.abs(horizontalSpeedBlocksPerTick) : 0.0;
        double level = Math.min(1.0, speed / SPRINT_SPEED);
        if (level <= 0.0) {
            // Standing still: let the previous report fade instead of snapping to zero.
            return;
        }
        movementLevel = level;
        movementAt = nowMillis;
    }

    /** The player attacked (attack key or arm swing). */
    public void onAttack(long nowMillis) {
        attackAt = nowMillis;
    }

    /** A VANTA screen opened. */
    public void onScreenOpened(long nowMillis) {
        openedAt = nowMillis;
        closedAt = Long.MIN_VALUE;
    }

    /** A VANTA screen started closing. */
    public void onScreenClosed(long nowMillis) {
        closedAt = nowMillis;
    }

    // ---- curves --------------------------------------------------------------------------------------------------

    /**
     * Opacity multiplier of the whole HUD: 1.0 when Dynamic HUD is off or input happened less than
     * {@value #HUD_IDLE_MS} ms ago, fading to 0.0 over {@value #HUD_FADE_MS} ms afterwards. Before the first input
     * event the HUD counts as touched at the first call, so a fresh game never starts with a hidden HUD.
     */
    public float hudAlpha(long nowMillis) {
        if (!lab.isEnabled(LabFeature.DYNAMIC_HUD)) {
            return 1f;
        }
        if (lastInputAt == Long.MIN_VALUE) {
            lastInputAt = nowMillis;
            return 1f;
        }
        long idle = nowMillis - lastInputAt;
        if (idle < HUD_IDLE_MS) {
            return 1f;
        }
        return 1f - (float) clamp01((idle - HUD_IDLE_MS) / (double) HUD_FADE_MS);
    }

    /** True when {@link #hudAlpha} is below 1 (the HUD is fading or hidden). */
    public boolean isHudFaded(long nowMillis) {
        return hudAlpha(nowMillis) < 1f;
    }

    /**
     * How far the crosshair gap spreads, 0..1: 0.0 when Animated crosshair is off, otherwise the movement level
     * (eased out after the last movement report) plus the attack pulse, clamped to 1.
     */
    public float crosshairSpread(long nowMillis) {
        if (!lab.isEnabled(LabFeature.ANIMATED_CROSSHAIR)) {
            return 0f;
        }
        double movement = 0.0;
        if (movementAt != Long.MIN_VALUE) {
            long age = nowMillis - movementAt;
            if (age < MOVEMENT_HOLD_MS) {
                movement = movementLevel;
            } else {
                movement = movementLevel * (1.0 - clamp01((age - MOVEMENT_HOLD_MS) / (double) MOVEMENT_FADE_MS));
            }
        }
        double attack = 0.0;
        if (attackAt != Long.MIN_VALUE) {
            long age = nowMillis - attackAt;
            if (age >= 0 && age < ATTACK_PULSE_MS) {
                attack = 1.0 - age / (double) ATTACK_PULSE_MS;
            }
        }
        return (float) clamp01(movement + attack);
    }

    /**
     * Progress of the screen transition: 1.0 when Screen transitions are off or no screen event happened; after
     * {@link #onScreenOpened} rising 0..1 over {@value #TRANSITION_MS} ms; after {@link #onScreenClosed} falling
     * 1..0 over the same time and 1.0 again once that close finished.
     */
    public float transitionProgress(long nowMillis) {
        if (!lab.isEnabled(LabFeature.SCREEN_TRANSITIONS)) {
            return 1f;
        }
        if (closedAt != Long.MIN_VALUE && closedAt >= openedAt) {
            long age = nowMillis - closedAt;
            if (age >= TRANSITION_MS) {
                // The close finished: the closing screen is gone, and the screen the host shows next (a parent
                // re-shown without a new open event) must not stay shifted by the slide.
                return 1f;
            }
            return 1f - (float) clamp01(age / (double) TRANSITION_MS);
        }
        if (openedAt != Long.MIN_VALUE) {
            return (float) clamp01((nowMillis - openedAt) / (double) TRANSITION_MS);
        }
        return 1f;
    }

    /** The settings these curves read. */
    public LabSettings settings() {
        return lab;
    }

    private static double clamp01(double value) {
        if (!(value > 0.0)) {
            return 0.0;
        }
        return Math.min(1.0, value);
    }
}
