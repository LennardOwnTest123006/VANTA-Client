package dev.vanta.core.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScreenRegistryTest {
    @Test
    void registersAndCreates() {
        ScreenRegistry registry = new ScreenRegistry();
        assertFalse(registry.isRegistered(ScreenId.SETTINGS));
        assertTrue(registry.create(ScreenId.SETTINGS).isEmpty());
        registry.register(ScreenId.SETTINGS, () -> "settings-screen");
        assertTrue(registry.isRegistered(ScreenId.SETTINGS));
        assertEquals(Optional.of("settings-screen"), registry.create(ScreenId.SETTINGS));
        assertEquals(1, registry.registered().size());
        registry.clear();
        assertTrue(registry.registered().isEmpty());
    }

    @Test
    void screenIdsHaveStableIds() {
        assertEquals("hud_editor", ScreenId.HUD_EDITOR.id());
        assertEquals(Optional.of(ScreenId.HUD_EDITOR), ScreenId.fromId("hud_editor"));
        assertEquals(Optional.of(ScreenId.ABOUT), ScreenId.fromId("ABOUT"));
        assertTrue(ScreenId.fromId("nope").isEmpty());
        assertEquals("vanta.screen.about", ScreenId.ABOUT.langKey());
    }
}
