package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnimatedValueTest {

    @Test
    void easesLinearlyAgainstTheClock() {
        ManualClock clock = new ManualClock();
        Animator animator = new Animator(clock);
        AnimatedValue v = animator.value(0f);
        assertFalse(v.isAnimating());
        v.animateTo(10f, 100L, Easing.LINEAR);
        assertTrue(v.isAnimating());
        assertEquals(0f, v.get(), 1e-5f);
        clock.advance(50L);
        assertEquals(5f, v.get(), 1e-5f);
        clock.advance(50L);
        assertEquals(10f, v.get(), 1e-5f);
        assertFalse(v.isAnimating());
        assertEquals(10f, v.target(), 1e-5f);
    }

    @Test
    void retargetingMidFlightStartsFromTheCurrentValue() {
        ManualClock clock = new ManualClock();
        Animator animator = new Animator(clock);
        AnimatedValue v = animator.value(0f);
        v.animateTo(10f, 100L, Easing.LINEAR);
        clock.advance(50L);
        v.animateTo(0f, 100L, Easing.LINEAR);
        assertEquals(5f, v.get(), 1e-5f, "no jump when retargeting");
        clock.advance(50L);
        assertEquals(2.5f, v.get(), 1e-5f);
    }

    @Test
    void sameTargetDoesNotRestart() {
        ManualClock clock = new ManualClock();
        Animator animator = new Animator(clock);
        AnimatedValue v = animator.value(0f);
        v.animateTo(1f, 100L, Easing.LINEAR);
        clock.advance(60L);
        v.animateTo(1f, 100L, Easing.LINEAR);
        assertEquals(0.6f, v.get(), 1e-5f);
    }

    @Test
    void reducedMotionSnaps() {
        ManualClock clock = new ManualClock();
        Animator animator = new Animator(clock, true);
        AnimatedValue v = animator.value(0f);
        v.animateTo(10f, 100L, Easing.LINEAR);
        assertEquals(10f, v.get(), 1e-5f);
        assertFalse(v.isAnimating());
        assertEquals(1f, animator.progress(0L, 500L, Easing.LINEAR), 1e-5f);
        assertEquals(0f, animator.phase(1000L), 1e-5f, "blink phase is static under reduced motion");
    }

    @Test
    void snapAndBooleanHelpers() {
        ManualClock clock = new ManualClock();
        Animator animator = new Animator(clock);
        AnimatedValue v = animator.value(true);
        assertEquals(1f, v.get(), 1e-5f);
        v.animateTo(false, 100L);
        v.snapTo(0.25f);
        assertEquals(0.25f, v.get(), 1e-5f);
        assertFalse(v.isAnimating());
        animator.setDefaultDurationMillis(50L);
        v.animateTo(1f);
        clock.advance(25L);
        assertTrue(v.get() > 0.25f && v.get() < 1f);
    }

    @Test
    void progressAndPhase() {
        ManualClock clock = new ManualClock(100L);
        Animator animator = new Animator(clock);
        assertEquals(0f, animator.progress(100L, 200L, Easing.LINEAR), 1e-5f);
        clock.advance(100L);
        assertEquals(0.5f, animator.progress(100L, 200L, Easing.LINEAR), 1e-5f);
        clock.advance(500L);
        assertEquals(1f, animator.progress(100L, 200L, Easing.LINEAR), 1e-5f);
        clock.set(1250L);
        assertEquals(0.25f, animator.phase(1000L), 1e-5f);
        assertThrows(IllegalArgumentException.class, () -> animator.setDefaultDurationMillis(0L));
        assertThrows(IllegalArgumentException.class, () -> clock.advance(-1L));
    }
}
