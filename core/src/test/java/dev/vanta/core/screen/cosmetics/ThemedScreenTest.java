package dev.vanta.core.screen.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.accessibility.AccessibilityState;
import dev.vanta.core.accessibility.ColorBlindPalette;
import dev.vanta.core.cosmetics.UiThemeDefinition;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.VantaShell;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemedScreenTest {
    @TempDir
    Path dir;
    private ServicesFixture fx;

    /** Minimal themed screen for the base-class behaviour. */
    private static final class Probe extends ThemedScreen {
        int themeChanges;
        boolean closed;

        Probe(ServicesFixture fx) {
            super(fx.services, ScreenId.COSMETICS);
        }

        @Override
        protected UiNode build(UiContext ctx) {
            return newShell().content(new UiNode() { });
        }

        @Override
        protected void onThemeChanged(Theme theme) {
            themeChanges++;
        }

        @Override
        protected void onScreenClose() {
            closed = true;
        }
    }

    @BeforeEach
    void setUp() {
        fx = new ServicesFixture(dir);
    }

    @Test
    void themeResolverAppliesCosmeticThemeAccessibilityAndPalette() {
        UiThemeDefinition midnight = fx.services.cosmeticsRegistry().findTheme("midnight").orElseThrow();
        Theme theme = ThemeResolver.build(midnight, new AccessibilityState(1.5, true, true, true, true,
                ColorBlindPalette.DEUTERANOPIA));
        assertEquals("midnight", theme.id());
        assertEquals(Lang.tr(midnight.langKey()), theme.name());
        assertEquals(midnight.tokenOverrides().get("accent.violet").intValue(), theme.accent());
        assertEquals(1.5f, theme.scale());
        assertTrue(theme.reducedMotion());
        assertTrue(theme.isHighContrast());
        assertTrue(theme.largeText());
        assertTrue(theme.reducedTransparency());
        assertEquals(ColorBlindPalette.DEUTERANOPIA.success(), theme.success());

        Theme preview = ThemeResolver.preview(midnight, Theme.DEFAULT.withScale(2f));
        assertEquals(2f, preview.scale());
        assertEquals(midnight.tokenOverrides().get("accent.violet").intValue(), preview.accent());
        assertEquals(Theme.DEFAULT.accent(), ThemeResolver.themeFor(fx.services).accent());
    }

    @Test
    void screenRethemesLiveAndSavesOnClose() {
        Probe probe = new Probe(fx);
        probe.attach(fx.env());
        probe.init(400, 300);
        assertEquals(Lang.tr(ScreenId.COSMETICS.langKey()), probe.title());
        assertTrue(probe.root() instanceof VantaShell);
        int before = probe.theme().accent();

        fx.services.cosmetics().selectTheme("aurora");
        assertEquals(1, probe.themeChanges);
        assertNotEquals(before, probe.theme().accent());
        assertEquals("aurora", probe.theme().id());

        fx.services.settings().set(VantaSettings.ACCESSIBILITY_HIGH_CONTRAST, true);
        assertEquals(2, probe.themeChanges);
        assertTrue(probe.theme().isHighContrast());

        assertFalse(probe.openScreen(ScreenId.ABOUT), "ABOUT is not registered by Screens2");
        assertTrue(probe.openScreen(ScreenId.PROFILES));
        assertTrue(fx.ui.host.events().contains("openScreen:PROFILES"));

        probe.onClose();
        assertTrue(probe.closed);
        assertTrue(Files.exists(fx.services.paths().settingsFile()));
        // No further re-theming after close.
        fx.services.cosmetics().selectTheme("ember");
        assertEquals(2, probe.themeChanges);
    }

    @Test
    void onceInitialisedRunsAfterFirstLayoutOrImmediately() {
        Probe probe = new Probe(fx);
        boolean[] ran = {false};
        probe.onceInitialised(() -> ran[0] = probe.isInitialised());
        probe.attach(fx.env());
        assertFalse(ran[0]);
        probe.init(400, 300);
        assertTrue(ran[0]);
        boolean[] again = {false};
        probe.onceInitialised(() -> again[0] = true);
        assertTrue(again[0]);
    }

    @Test
    void displayPathIsRelativeToTheGameDirectory() {
        Path file = fx.services.paths().root().resolve("exports").resolve("x.json");
        assertEquals("config/vanta/exports/x.json", ThemedScreen.displayPath(fx.services, file));
        Path outside = dir.getParent() == null ? Path.of("/other.json") : dir.getParent().resolve("other-file.json");
        assertTrue(ThemedScreen.displayPath(fx.services, outside).endsWith("other-file.json")
                || ThemedScreen.displayPath(fx.services, outside).endsWith("other.json"));
    }

    @Test
    void pluralsUseSingularKeyWhenPresent() {
        assertEquals("1 profile", Plurals.count(1, "vanta.profiles.count"));
        assertEquals("3 profiles", Plurals.count(3, "vanta.profiles.count"));
        assertEquals("1 h ago", Plurals.count(1, "vanta.stats.ago.hours"));
    }
}
