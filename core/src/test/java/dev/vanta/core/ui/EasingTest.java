package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EasingTest {

    private static final Easing[] ALL = {Easing.LINEAR, Easing.OUT_CUBIC, Easing.IN_CUBIC, Easing.IN_OUT_CUBIC,
            Easing.OUT_BACK, Easing.OUT_EXPO, Easing.STANDARD, Easing.EMPHASIZED};

    @Test
    void everyCurveStartsAtZeroAndEndsAtOne() {
        for (Easing e : ALL) {
            assertEquals(0f, e.apply(0f), 1e-4f);
            assertEquals(1f, e.apply(1f), 1e-4f);
            assertEquals(0f, e.apply(-1f), 1e-4f, "clamps below 0");
            assertEquals(1f, e.apply(2f), 1e-4f, "clamps above 1");
        }
    }

    @Test
    void monotonicCurvesNeverDecrease() {
        Easing[] monotonic = {Easing.LINEAR, Easing.OUT_CUBIC, Easing.IN_CUBIC, Easing.IN_OUT_CUBIC, Easing.OUT_EXPO,
                Easing.STANDARD, Easing.EMPHASIZED};
        for (Easing e : monotonic) {
            float last = -1f;
            for (int i = 0; i <= 100; i++) {
                float v = e.apply(i / 100f);
                assertTrue(v >= last - 1e-5f, e + " decreased at " + i);
                last = v;
            }
        }
    }

    @Test
    void outCubicDeceleratesAndInCubicAccelerates() {
        assertTrue(Easing.OUT_CUBIC.apply(0.5f) > 0.5f);
        assertTrue(Easing.IN_CUBIC.apply(0.5f) < 0.5f);
        assertEquals(0.5f, Easing.IN_OUT_CUBIC.apply(0.5f), 1e-5f);
        assertEquals(0.5f, Easing.LINEAR.apply(0.5f), 1e-6f);
    }

    @Test
    void outBackOvershoots() {
        float max = 0f;
        for (int i = 0; i <= 100; i++) {
            max = Math.max(max, Easing.OUT_BACK.apply(i / 100f));
        }
        assertTrue(max > 1.05f, "overshoot expected, got " + max);
        assertTrue(max < 1.2f);
    }

    @Test
    void cubicBezierIdentityIsLinear() {
        Easing e = Easing.cubicBezier(0f, 0f, 1f, 1f);
        for (int i = 0; i <= 10; i++) {
            assertEquals(i / 10f, e.apply(i / 10f), 1e-3f);
        }
        assertTrue(Easing.STANDARD.apply(0.3f) > 0.5f, "standard curve is front-loaded");
    }
}
