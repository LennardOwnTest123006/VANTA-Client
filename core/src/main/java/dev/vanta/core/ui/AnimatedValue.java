package dev.vanta.core.ui;

/**
 * A float that eases towards a target over time. Values are evaluated lazily against the owning
 * {@link Animator}'s clock, so no per-frame update call is required and tests are deterministic.
 * <p>
 * When the animator reports reduced motion, every {@link #animateTo} snaps immediately.
 */
public final class AnimatedValue {

    private final Animator animator;
    private float from;
    private float to;
    private long startMillis;
    private long durationMillis;
    private Easing easing = Easing.STANDARD;

    AnimatedValue(Animator animator, float initial) {
        this.animator = animator;
        this.from = initial;
        this.to = initial;
        this.startMillis = animator.now();
        this.durationMillis = 0L;
    }

    /** Current eased value. */
    public float get() {
        if (durationMillis <= 0L) {
            return to;
        }
        long elapsed = animator.now() - startMillis;
        if (elapsed <= 0L) {
            return from;
        }
        if (elapsed >= durationMillis) {
            return to;
        }
        float t = (float) elapsed / (float) durationMillis;
        return from + (to - from) * easing.apply(t);
    }

    /** The value this animation ends at. */
    public float target() {
        return to;
    }

    /** Whether the value is still moving. */
    public boolean isAnimating() {
        if (durationMillis <= 0L) {
            return false;
        }
        long elapsed = animator.now() - startMillis;
        return elapsed < durationMillis;
    }

    /** Animates to {@code target} with the animator's default duration and the standard curve. */
    public AnimatedValue animateTo(float target) {
        return animateTo(target, animator.defaultDurationMillis(), Easing.STANDARD);
    }

    /** Animates to {@code target} over {@code duration} ms with the standard curve. */
    public AnimatedValue animateTo(float target, long duration) {
        return animateTo(target, duration, Easing.STANDARD);
    }

    /**
     * Animates from the current value to {@code target}. Re-targeting mid-flight starts from the current
     * eased value so there is never a visual jump.
     */
    public AnimatedValue animateTo(float target, long duration, Easing curve) {
        if (target == to && (isAnimating() || durationMillis == 0L)) {
            return this;
        }
        float current = get();
        if (animator.isReducedMotion() || duration <= 0L) {
            snapTo(target);
            return this;
        }
        this.from = current;
        this.to = target;
        this.startMillis = animator.now();
        this.durationMillis = duration;
        this.easing = curve == null ? Easing.STANDARD : curve;
        return this;
    }

    /** Jumps to the value without animating. */
    public AnimatedValue snapTo(float value) {
        this.from = value;
        this.to = value;
        this.durationMillis = 0L;
        this.startMillis = animator.now();
        return this;
    }

    /** Convenience for boolean-driven animations (0 or 1). */
    public AnimatedValue animateTo(boolean on) {
        return animateTo(on ? 1f : 0f);
    }

    /** Convenience for boolean-driven animations with a custom duration. */
    public AnimatedValue animateTo(boolean on, long duration) {
        return animateTo(on ? 1f : 0f, duration, Easing.STANDARD);
    }
}
