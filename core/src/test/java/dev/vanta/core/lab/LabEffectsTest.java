package dev.vanta.core.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Every Vanta Lab curve with a fake clock: off values, the shape of each curve and the reset on input. */
class LabEffectsTest {
    @TempDir
    Path dir;
    private LabSettings lab;
    private LabEffects effects;

    @BeforeEach
    void setUp() {
        MutableClock clock = MutableClock.standard();
        SettingsStore settings = new SettingsStore(VantaSettings.registry(), new JsonStore(clock),
                VantaPaths.inGameDirectory(dir).settingsFile(), Optional.empty());
        settings.load();
        lab = new LabSettings(settings);
        effects = new LabEffects(lab);
    }

    @Test
    void hudAlphaIsOneWhileDynamicHudIsOff() {
        effects.onInput(0L);
        assertEquals(1f, effects.hudAlpha(60_000L), 1e-6f);
        assertFalse(effects.isHudFaded(60_000L));
    }

    @Test
    void hudFadesAfterTenSecondsWithoutInputAndReturnsOnInput() {
        lab.set(LabFeature.DYNAMIC_HUD, true);
        assertEquals(1f, effects.hudAlpha(1_000L), 1e-6f, "the first call counts as input");
        effects.onInput(1_000L);
        assertEquals(1f, effects.hudAlpha(10_999L), 1e-6f, "9.999 s idle: still visible");
        assertEquals(1f, effects.hudAlpha(11_000L), 1e-6f, "the fade starts at 10 s");
        assertEquals(0.5f, effects.hudAlpha(11_250L), 1e-6f, "half way through the 0.5 s fade");
        assertEquals(0f, effects.hudAlpha(11_500L), 1e-6f);
        assertEquals(0f, effects.hudAlpha(90_000L), 1e-6f);
        assertTrue(effects.isHudFaded(12_000L));
        effects.onInput(12_000L);
        assertEquals(1f, effects.hudAlpha(12_000L), 1e-6f, "back immediately on input");
        assertEquals(1f, effects.hudAlpha(21_999L), 1e-6f);
        lab.set(LabFeature.DYNAMIC_HUD, false);
        assertEquals(1f, effects.hudAlpha(30_000L), 1e-6f, "switching the feature off restores the HUD");
    }

    @Test
    void crosshairSpreadIsZeroWhileAnimatedCrosshairIsOff() {
        effects.onMovement(LabEffects.SPRINT_SPEED, 0L);
        effects.onAttack(0L);
        assertEquals(0f, effects.crosshairSpread(0L), 1e-6f);
    }

    @Test
    void crosshairSpreadFollowsMovementAndDecaysSmoothly() {
        lab.set(LabFeature.ANIMATED_CROSSHAIR, true);
        assertEquals(0f, effects.crosshairSpread(0L), 1e-6f, "standing still");
        effects.onMovement(LabEffects.SPRINT_SPEED / 2, 1_000L);
        assertEquals(0.5f, effects.crosshairSpread(1_000L), 1e-6f, "half the sprint speed is half the spread");
        assertEquals(0.5f, effects.crosshairSpread(1_099L), 1e-6f, "held while reports keep coming");
        effects.onMovement(LabEffects.SPRINT_SPEED, 1_050L);
        assertEquals(1f, effects.crosshairSpread(1_050L), 1e-6f, "sprinting is the full spread");
        effects.onMovement(2.0, 1_100L);
        assertEquals(1f, effects.crosshairSpread(1_100L), 1e-6f, "faster than sprinting is clamped");
        // No more movement reports: full for the hold, then a linear fade over 400 ms.
        assertEquals(1f, effects.crosshairSpread(1_199L), 1e-6f);
        assertEquals(0.5f, effects.crosshairSpread(1_400L), 1e-6f);
        assertEquals(0f, effects.crosshairSpread(1_600L), 1e-6f);
        assertEquals(0f, effects.crosshairSpread(5_000L), 1e-6f);
        effects.onMovement(0.0, 6_000L);
        assertEquals(0f, effects.crosshairSpread(6_000L), 1e-6f, "a zero report does not restart the fade");
    }

    @Test
    void attackPulsesForAQuarterSecondAndAddsToMovement() {
        lab.set(LabFeature.ANIMATED_CROSSHAIR, true);
        effects.onAttack(2_000L);
        assertEquals(1f, effects.crosshairSpread(2_000L), 1e-6f);
        assertEquals(0.5f, effects.crosshairSpread(2_125L), 1e-6f);
        assertEquals(0f, effects.crosshairSpread(2_250L), 1e-6f);
        effects.onMovement(LabEffects.SPRINT_SPEED / 2, 3_000L);
        effects.onAttack(3_000L);
        assertEquals(1f, effects.crosshairSpread(3_000L), 1e-6f, "movement plus attack is clamped to 1");
        effects.onAttack(2_900L);
        effects.onMovement(LabEffects.SPRINT_SPEED / 4, 3_000L);
        assertEquals(0.85f, effects.crosshairSpread(3_000L), 1e-6f, "0.25 movement plus 0.6 of the older pulse");
        assertEquals(0.25f, effects.crosshairSpread(1_000L), 1e-6f,
                "a clock before the attack sees no pulse, only the held movement");
    }

    @Test
    void transitionProgressIsOneWhileScreenTransitionsAreOff() {
        effects.onScreenOpened(0L);
        assertEquals(1f, effects.transitionProgress(0L), 1e-6f);
        effects.onScreenClosed(50L);
        assertEquals(1f, effects.transitionProgress(60L), 1e-6f);
    }

    @Test
    void transitionRisesOnOpenAndFallsOnClose() {
        lab.set(LabFeature.SCREEN_TRANSITIONS, true);
        assertEquals(1f, effects.transitionProgress(0L), 1e-6f, "no screen event yet");
        effects.onScreenOpened(1_000L);
        assertEquals(0f, effects.transitionProgress(1_000L), 1e-6f);
        assertEquals(0.5f, effects.transitionProgress(1_080L), 1e-6f);
        assertEquals(1f, effects.transitionProgress(1_160L), 1e-6f);
        assertEquals(1f, effects.transitionProgress(9_000L), 1e-6f);
        effects.onScreenClosed(9_000L);
        assertEquals(1f, effects.transitionProgress(9_000L), 1e-6f);
        assertEquals(0.5f, effects.transitionProgress(9_080L), 1e-6f);
        assertEquals(0f, effects.transitionProgress(9_160L), 1e-6f);
        assertEquals(0f, effects.transitionProgress(20_000L), 1e-6f);
        effects.onScreenOpened(20_000L);
        assertEquals(0f, effects.transitionProgress(20_000L), 1e-6f, "the next open starts over");
        assertEquals(1f, effects.transitionProgress(20_200L), 1e-6f);
    }

    @Test
    void exposesTheSettingsItReads() {
        assertEquals(lab, effects.settings());
        assertEquals(10_000L, LabEffects.HUD_IDLE_MS);
        assertEquals(500L, LabEffects.HUD_FADE_MS);
        assertEquals(250L, LabEffects.ATTACK_PULSE_MS);
        assertEquals(160L, LabEffects.TRANSITION_MS);
    }
}
