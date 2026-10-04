package dev.vanta.client.zoom;

import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * State of the hold-to-zoom feature. Pure logic: the FOV mixin asks for {@link #update(long)} every frame and divides
 * the game's field of view by the result; the mouse wheel mixin offers scroll input through {@link #onScroll(double)}.
 * <p>
 * Zoom is a client-side camera effect identical to looking through a spyglass: it narrows the field of view and
 * nothing else. The factor comes from the {@code zoom.factor} setting, optionally animated ({@code zoom.smooth}) and
 * adjusted with the mouse wheel while the key is held ({@code zoom.scrollAdjust}); wheel adjustments are temporary and
 * reset the next time the key is pressed.
 */
public final class ZoomController {
    /** Wheel step: each notch multiplies the factor by this amount. */
    public static final double SCROLL_STEP = 1.15;
    /** Smoothing time constant in nanoseconds (about 90 ms to settle). */
    private static final double SMOOTH_TAU_NANOS = 45_000_000.0;

    private final SettingsStore settings;
    private final BooleanSupplier keyDown;
    private boolean active;
    private double adjust = 1.0;
    private double current = 1.0;
    private long lastNanos;

    /**
     * @param settings the settings store (zoom.* settings)
     * @param keyDown  whether the zoom key is currently held
     */
    public ZoomController(SettingsStore settings, BooleanSupplier keyDown) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.keyDown = Objects.requireNonNull(keyDown, "keyDown");
    }

    /**
     * Advances the animation and returns the FOV divisor for this frame ({@code 1.0} when not zooming).
     *
     * @param nowNanos monotonic time
     */
    public double update(long nowNanos) {
        boolean held = settings.get(VantaSettings.ZOOM_ENABLED) && keyDown.getAsBoolean();
        if (held && !active) {
            active = true;
            adjust = 1.0;
        } else if (!held && active) {
            active = false;
        }
        double goal = active ? clampFactor(settings.get(VantaSettings.ZOOM_FACTOR) * adjust) : 1.0;
        if (!settings.get(VantaSettings.ZOOM_SMOOTH) || lastNanos == 0L) {
            current = goal;
        } else {
            double dt = Math.max(0.0, nowNanos - lastNanos);
            double blend = 1.0 - Math.exp(-dt / SMOOTH_TAU_NANOS);
            current += (goal - current) * blend;
            if (Math.abs(goal - current) < 0.002) {
                current = goal;
            }
        }
        lastNanos = nowNanos;
        return current;
    }

    /** True while the zoom key is held (and zoom is enabled). */
    public boolean isActive() {
        return active;
    }

    /** Current divisor as of the last {@link #update(long)}. */
    public double currentFactor() {
        return current;
    }

    /** Effective target factor (setting × wheel adjustment) while zooming. */
    public double targetFactor() {
        return active ? clampFactor(settings.get(VantaSettings.ZOOM_FACTOR) * adjust) : 1.0;
    }

    /**
     * Mouse wheel input while zooming.
     *
     * @param scrollY positive = wheel up = zoom in
     * @return true when the scroll was consumed (the hotbar must not scroll)
     */
    public boolean onScroll(double scrollY) {
        if (!active || !settings.get(VantaSettings.ZOOM_SCROLL_ADJUST) || scrollY == 0.0) {
            return false;
        }
        double base = settings.get(VantaSettings.ZOOM_FACTOR);
        double next = clampFactor(base * adjust * Math.pow(SCROLL_STEP, scrollY));
        adjust = next / base;
        return true;
    }

    private static double clampFactor(double factor) {
        double min = VantaSettings.ZOOM_FACTOR.min();
        double max = VantaSettings.ZOOM_FACTOR.max();
        return Math.max(min, Math.min(max, factor));
    }
}
