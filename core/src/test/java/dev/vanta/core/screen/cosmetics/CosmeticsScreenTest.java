package dev.vanta.core.screen.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.cosmetics.Badge;
import dev.vanta.core.cosmetics.HudTheme;
import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.settings.VantaSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CosmeticsScreenTest {
    @TempDir
    Path dir;
    private ServicesFixture fx;
    private CosmeticsScreen screen;

    @BeforeEach
    void setUp() {
        fx = new ServicesFixture(dir);
        screen = fx.open(ScreenId.COSMETICS);
    }

    private CosmeticCard card(String title) {
        return screen.card(title).orElseThrow(() -> new AssertionError("no card " + title + " in " + screen.cards()));
    }

    @Test
    void selectingAThemeRethemesTheScreenInstantly() {
        assertEquals(CosmeticsScreen.Section.THEMES, screen.section());
        assertEquals(5, screen.cards().size());
        assertTrue(card("VANTA Dark").isSelected());
        int before = screen.theme().accent();
        fx.click(screen, card("Aurora"));
        assertEquals("aurora", fx.services.settings().get(VantaSettings.GENERAL_THEME_ID));
        assertEquals("aurora", screen.theme().id());
        assertTrue(screen.theme().accent() != before);
        assertTrue(card("Aurora").isSelected());
        assertFalse(card("VANTA Dark").isSelected());
    }

    @Test
    void backgroundHudParticleAndBadgeSelectionsWriteTheSettings() {
        screen.showSection(CosmeticsScreen.Section.BACKGROUNDS);
        fx.frame(screen);
        fx.click(screen, card(Lang.tr(MenuBackground.GRAPHITE_GRID.langKey())));
        assertEquals(MenuBackground.GRAPHITE_GRID, fx.services.settings().get(VantaSettings.MENU_BACKGROUND));

        screen.showSection(CosmeticsScreen.Section.HUD);
        fx.frame(screen);
        fx.click(screen, card(Lang.tr(HudTheme.GLASS.langKey())));
        assertEquals(HudTheme.GLASS, fx.services.settings().get(VantaSettings.COSMETICS_HUD_THEME));
        assertTrue(card(Lang.tr(HudTheme.GLASS.langKey())).isSelected());

        screen.showSection(CosmeticsScreen.Section.PARTICLES);
        fx.frame(screen);
        assertTrue(card(Lang.tr(MenuParticles.NONE.langKey())).thumbnail() instanceof ParticlePreview);
        fx.click(screen, card(Lang.tr(MenuParticles.DUST.langKey())));
        assertEquals(MenuParticles.DUST, fx.services.settings().get(VantaSettings.MENU_PARTICLES));

        screen.showSection(CosmeticsScreen.Section.BADGES);
        fx.frame(screen);
        fx.click(screen, card(Lang.tr(Badge.SUPPORTER.langKey())));
        assertEquals(Badge.SUPPORTER, fx.services.settings().get(VantaSettings.COSMETICS_BADGE));
        assertTrue(card(Lang.tr(Badge.SUPPORTER.langKey())).isSelected());
        assertEquals(Badge.SUPPORTER, fx.services.cosmetics().selection().badge());
    }

    @Test
    void crosshairPresetAppliesToTheLiveCrosshairAndTheSelection() {
        screen.showSection(CosmeticsScreen.Section.CROSSHAIR);
        fx.frame(screen);
        assertEquals(6, screen.cards().size());
        var circle = CrosshairPresets.find(CrosshairPresets.CIRCLE).orElseThrow();
        fx.click(screen, card(Lang.tr(circle.langKey())));
        assertEquals(circle.style(), fx.services.crosshair().style());
        assertEquals("circle", fx.services.settings().get(VantaSettings.COSMETICS_CROSSHAIR_PRESET));
        assertTrue(card(Lang.tr(circle.langKey())).isSelected());
        var editor = screen.root().findById("cosmetics.open-crosshair");
        assertFalse(editor.isEnabled(), "crosshair editor is not registered in this fixture");
    }

    @Test
    void packsSectionListsInstalledPacksAndReloads() throws Exception {
        screen.showSection(CosmeticsScreen.Section.PACKS);
        fx.frame(screen);
        assertTrue(screen.cards().isEmpty());
        assertTrue(screen.root().findById("cosmetics.refresh-packs") != null);

        Path packs = fx.services.paths().cosmeticPacksDir();
        Files.createDirectories(packs);
        Files.writeString(packs.resolve("pack.json"), """
                {"schemaVersion":1,"id":"test-pack","name":"Test Pack","author":"Someone",
                 "themes":[{"id":"sunset","name":"Sunset","tokens":{"accent.violet":"#FF8A5B"}}]}
                """, StandardCharsets.UTF_8);
        Files.writeString(packs.resolve("broken.json"), "{not json", StandardCharsets.UTF_8);
        screen.reloadPacks();
        fx.frame(screen);
        assertEquals(1, screen.cards().size());
        assertEquals("Test Pack", screen.cards().get(0).title());
        assertEquals(1, fx.services.cosmetics().loadErrors().size());

        fx.click(screen, screen.cards().get(0));
        assertEquals(CosmeticsScreen.Section.THEMES, screen.section());
        assertTrue(screen.card("Sunset").isPresent());
        fx.click(screen, card("Sunset"));
        assertEquals("sunset", screen.theme().id());
    }

    @Test
    void railSwitchesSectionsAndSmallScreensStillLayOut() {
        CosmeticsScreen small = fx.open(ScreenId.COSMETICS, 427, 240);
        small.showSection(CosmeticsScreen.Section.BACKGROUNDS);
        fx.frame(small);
        assertEquals(5, small.cards().size());
        assertTrue(small.cards().get(0).bounds().w() > 60);
        var rail = small.root().findById("rail.badges");
        fx.click(small, rail);
        assertEquals(CosmeticsScreen.Section.BADGES, small.section());
    }
}
