package dev.vanta.core.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.cosmetics.CosmeticsScreen;
import dev.vanta.core.screen.cosmetics.ServicesFixture;
import dev.vanta.core.screen.cosmetics.ThemedScreen;
import dev.vanta.core.screen.keybinds.KeybindsScreen;
import dev.vanta.core.screen.profiles.ProfilesScreen;
import dev.vanta.core.screen.stats.StatisticsScreen;
import dev.vanta.core.ui.UiScreen;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Screens2Test {
    @TempDir
    Path dir;

    @Test
    void registersTheFourScreensWithTranslatedTitles() {
        ServicesFixture fx = new ServicesFixture(dir);
        ScreenRegistry registry = fx.services.screens();
        for (ScreenId id : Screens2.SCREENS) {
            assertTrue(registry.isRegistered(id), id.name());
            Object created = registry.create(id).orElseThrow();
            assertTrue(created instanceof ThemedScreen, id.name());
            assertEquals(Lang.tr(id.langKey()), ((UiScreen) created).title());
        }
        assertTrue(registry.create(ScreenId.PROFILES).orElseThrow() instanceof ProfilesScreen);
        assertTrue(registry.create(ScreenId.KEYBINDS).orElseThrow() instanceof KeybindsScreen);
        assertTrue(registry.create(ScreenId.COSMETICS).orElseThrow() instanceof CosmeticsScreen);
        assertTrue(registry.create(ScreenId.STATISTICS).orElseThrow() instanceof StatisticsScreen);
        // Every screen renders at desktop and compact sizes without touching anything.
        for (ScreenId id : Screens2.SCREENS) {
            fx.open(id);
            fx.open(id, 427, 240);
        }
    }

    @Test
    void keybindDeepLinkFromTheNavigatorRevealsTheMapping() {
        ServicesFixture fx = new ServicesFixture(dir);
        ScreenNavigator navigator = ScreenNavigator.forServices(fx.services);
        ProfilesScreen origin = fx.open(ScreenId.PROFILES);
        navigator.openKeybind(origin.context(), "key.jump");
        KeybindsScreen screen = fx.open(ScreenId.KEYBINDS);
        assertTrue(screen.row("key.jump").orElseThrow().field().isFocused(), "the staged mapping is focused");
        assertTrue(navigator.takePendingKeybind().isEmpty(), "the pending id is consumed");
        KeybindsScreen plain = fx.open(ScreenId.KEYBINDS);
        assertFalse(plain.row("key.jump").orElseThrow().field().isFocused(), "no deep link, no focus");
    }
}
