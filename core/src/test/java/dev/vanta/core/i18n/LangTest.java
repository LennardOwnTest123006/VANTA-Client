package dev.vanta.core.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LangTest {
    @AfterEach
    void reset() {
        Lang.reset();
    }

    @Test
    void translatesBundledStrings() {
        assertEquals("Play", Lang.tr("vanta.menu.play"));
        assertEquals("Quit Game", Lang.tr("vanta.menu.quit"));
        assertTrue(Lang.has("vanta.menu.singleplayer"));
    }

    @Test
    void missingKeyReturnsKey() {
        assertEquals("vanta.does.not.exist", Lang.tr("vanta.does.not.exist"));
        assertFalse(Lang.has("vanta.does.not.exist"));
    }

    @Test
    void formatsArguments() {
        assertEquals("“PvP” is now active", Lang.tr("vanta.notification.profile_loaded.body", "PvP"));
        assertEquals("12 → 10 chunks", Lang.tr("vanta.notification.render_distance_applied.body", 12, 10));
    }

    @Test
    void badPatternDoesNotThrow() {
        Lang.install(JsonLangProvider.of(Map.of("k", "%d items")));
        assertEquals("%d items oops", Lang.tr("k", "oops"));
    }

    @Test
    void customProviderOverridesBundle() {
        Lang.install(key -> key.equals("vanta.menu.play") ? java.util.Optional.of("Spielen") : java.util.Optional.empty());
        assertEquals("Spielen", Lang.tr("vanta.menu.play"));
        assertEquals("vanta.menu.quit", Lang.tr("vanta.menu.quit"));
    }

    @Test
    void bundleHasNoEmptyValuesAndIsLarge() {
        JsonLangProvider bundle = JsonLangProvider.builtIn();
        assertTrue(bundle.size() > 500, "expected a comprehensive bundle, got " + bundle.size());
        for (String key : bundle.keys()) {
            assertFalse(bundle.lookup(key).orElseThrow().isBlank(), key + " is blank");
            assertFalse(bundle.lookup(key).orElseThrow().toLowerCase().contains("todo"), key + " contains TODO");
            assertFalse(bundle.lookup(key).orElseThrow().toLowerCase().contains("lorem"), key + " is placeholder");
        }
    }
}
