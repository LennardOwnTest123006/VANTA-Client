package dev.vanta.core.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.config.MutableClock;
import org.junit.jupiter.api.Test;

/** Sodium's video settings discard pending changes on Escape; VANTA says so once per session. */
class SodiumApplyHintTest {
    @Test
    void hintIsPostedOnceAndOnlyForSodiumsScreen() {
        MutableClock clock = MutableClock.standard();
        NotificationCenter center = new NotificationCenter(clock);
        assertTrue(center.sodiumApplyHintFor("net.minecraft.client.gui.screens.options.VideoSettingsScreen")
                .isEmpty(), "the vanilla screen saves on close; no hint");
        assertTrue(center.sodiumApplyHintFor(null).isEmpty());
        Notification hint = center.sodiumApplyHintFor(NotificationCenter.SODIUM_VIDEO_SETTINGS_SCREEN).orElseThrow();
        assertEquals("Sodium video settings", hint.title());
        assertTrue(hint.body().contains("Apply (Alt+A)") && hint.body().contains("Escape"), hint.body());
        clock.advance(60_000);
        assertTrue(center.sodiumApplyHintFor(NotificationCenter.SODIUM_VIDEO_SETTINGS_SCREEN).isEmpty(),
                "once per session, not on every resize or reopen");
    }
}
