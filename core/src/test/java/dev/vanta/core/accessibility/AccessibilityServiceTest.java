package dev.vanta.core.accessibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccessibilityServiceTest {
    @TempDir
    Path dir;

    @Test
    void stateFollowsSettingsAndNotifies() {
        SettingsStore settings = new SettingsStore(VantaSettings.registry(), new JsonStore(MutableClock.standard()),
                dir.resolve("settings.json"), Optional.empty());
        AccessibilityService service = new AccessibilityService(settings);
        assertEquals(AccessibilityState.DEFAULT, service.state());
        List<AccessibilityState> seen = new ArrayList<>();
        Runnable unsubscribe = service.listen(seen::add);
        assertEquals(1, seen.size(), "listener receives the current state immediately");

        settings.set(VantaSettings.ACCESSIBILITY_LARGE_TEXT, true);
        settings.set(VantaSettings.ACCESSIBILITY_COLOR_BLIND_PALETTE, ColorBlindPalette.DEUTERANOPIA);
        settings.set(VantaSettings.HUD_ENABLED, false);
        assertEquals(3, seen.size(), "unrelated settings do not notify");
        AccessibilityState state = service.state();
        assertTrue(state.largeText());
        assertEquals(1.2, state.textScale(), 1e-9);
        assertEquals(ColorBlindPalette.DEUTERANOPIA.danger(), state.dangerColor());
        assertEquals(ColorBlindPalette.DEUTERANOPIA.success(), state.successColor());
        assertEquals(0.4, state.panelAlpha(0.4));
        settings.set(VantaSettings.ACCESSIBILITY_REDUCED_TRANSPARENCY, true);
        assertEquals(1.0, service.state().panelAlpha(0.4));
        unsubscribe.run();
        settings.set(VantaSettings.ACCESSIBILITY_REDUCED_MOTION, true);
        assertEquals(4, seen.size());
        assertFalse(seen.get(0).largeText());
    }

    @Test
    void palettesAreDistinct() {
        for (ColorBlindPalette palette : ColorBlindPalette.values()) {
            assertTrue(palette.success() != palette.warning());
            assertTrue(palette.warning() != palette.danger());
            assertTrue(palette.success() != palette.danger());
        }
        assertEquals(0xFF3DDC97, ColorBlindPalette.NONE.success());
    }
}
