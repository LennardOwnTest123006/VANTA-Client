package dev.vanta.core.ui;

/**
 * Easing curves mapping normalised time {@code t} (0..1) to progress (0..1, may overshoot for back easing).
 * All built-in curves start at 0 and end at exactly 1.
 */
@FunctionalInterface
public interface Easing {

    /** No easing. */
    Easing LINEAR = t -> clamp(t);

    /** Decelerating cubic; the default for hover/press feedback. */
    Easing OUT_CUBIC = t -> {
        float c = clamp(t);
        float inv = 1f - c;
        return 1f - inv * inv * inv;
    };

    /** Accelerating cubic. */
    Easing IN_CUBIC = t -> {
        float c = clamp(t);
        return c * c * c;
    };

    /** Symmetric cubic; used for position changes. */
    Easing IN_OUT_CUBIC = t -> {
        float c = clamp(t);
        return c < 0.5f ? 4f * c * c * c : 1f - (float) Math.pow(-2f * c + 2f, 3) / 2f;
    };

    /** Slight overshoot at the end (never below 0, overshoots to about 1.1). */
    Easing OUT_BACK = t -> {
        float c = clamp(t);
        if (c >= 1f) {
            return 1f;
        }
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float inv = c - 1f;
        return 1f + c3 * inv * inv * inv + c1 * inv * inv;
    };

    /** Very fast start, long settle. */
    Easing OUT_EXPO = t -> {
        float c = clamp(t);
        return c >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * c);
    };

    /** The design-token "standard" curve: {@code cubic-bezier(0.2, 0.8, 0.2, 1)}. */
    Easing STANDARD = cubicBezier(0.2f, 0.8f, 0.2f, 1f);

    /** The design-token "emphasized" curve: {@code cubic-bezier(0.3, 0, 0, 1)}. */
    Easing EMPHASIZED = cubicBezier(0.3f, 0f, 0f, 1f);

    /** Progress for normalised time {@code t}. */
    float apply(float t);

    /**
     * Builds a CSS-style cubic bezier easing with control points {@code (x1,y1)} and {@code (x2,y2)}.
     * Uses Newton iterations to invert the x curve; precise to about 1e-4.
     */
    static Easing cubicBezier(float x1, float y1, float x2, float y2) {
        return t -> {
            float x = clamp(t);
            if (x <= 0f) {
                return 0f;
            }
            if (x >= 1f) {
                return 1f;
            }
            float u = x;
            for (int i = 0; i < 8; i++) {
                float cx = bezier(u, x1, x2) - x;
                float dx = bezierDerivative(u, x1, x2);
                if (Math.abs(cx) < 1e-5f) {
                    break;
                }
                if (Math.abs(dx) < 1e-6f) {
                    break;
                }
                u -= cx / dx;
                u = clamp(u);
            }
            return bezier(u, y1, y2);
        };
    }

    /** Evaluates a bezier whose endpoints are 0 and 1 with the given control values. */
    private static float bezier(float u, float p1, float p2) {
        float inv = 1f - u;
        return 3f * inv * inv * u * p1 + 3f * inv * u * u * p2 + u * u * u;
    }

    private static float bezierDerivative(float u, float p1, float p2) {
        float inv = 1f - u;
        return 3f * inv * inv * p1 + 6f * inv * u * (p2 - p1) + 3f * u * u * (1f - p2);
    }

    /** Clamps to 0..1. */
    static float clamp(float t) {
        return t < 0f ? 0f : Math.min(1f, t);
    }

    /** Linear interpolation helper. */
    static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
