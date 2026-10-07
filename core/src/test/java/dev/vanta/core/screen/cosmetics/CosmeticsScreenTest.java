package dev.vanta.core.screen.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.cosmetics.Badge;
import dev.vanta.core.cosmetics.HudTheme;
import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.layout.ScrollPanel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    /** The card grid of the current section (the first one under the content scroll panel). */
    private static CardGrid grid(UiNode node) {
        if (node instanceof CardGrid g) {
            return g;
        }
        for (UiNode child : node.children()) {
            CardGrid found = grid(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The scroll panel the grid lives in (the content panel, not the rail's). */
    private static ScrollPanel scrollOf(UiNode node) {
        for (UiNode n = node.parent(); n != null; n = n.parent()) {
            if (n instanceof ScrollPanel s) {
                return s;
            }
        }
        throw new AssertionError("no scroll panel above " + node);
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

    /** Every card of the current section lies inside its grid, within scroll reach and clear of the other cards. */
    private static void assertCardsReachable(CosmeticsScreen s, String label) {
        CardGrid grid = grid(s.root());
        assertNotNull(grid, label + " shows a card grid");
        ScrollPanel scroll = scrollOf(grid);
        assertEquals(grid.preferredSize(s.context()).h(), grid.bounds().h(),
                label + ": the grid is as tall as the rows it laid out");
        int reach = scroll.bounds().bottom() + Math.round(scroll.maxScroll());
        List<CosmeticCard> cards = s.cards();
        for (CosmeticCard card : cards) {
            assertTrue(grid.bounds().contains(card.bounds()), label + ": " + card.title() + " inside the grid");
            assertTrue(card.bounds().bottom() <= reach,
                    label + ": " + card.title() + " ends at " + card.bounds().bottom() + ", reachable to " + reach);
        }
        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) {
                assertFalse(cards.get(i).bounds().intersects(cards.get(j).bounds()),
                        label + ": " + cards.get(i).title() + " overlaps " + cards.get(j).title());
            }
        }
    }

    @Test
    void sectionSwitchOnASmallScreenKeepsEveryCardReachable() {
        CosmeticsScreen small = fx.open(ScreenId.COSMETICS, 427, 240);
        for (CosmeticsScreen.Section section : List.of(CosmeticsScreen.Section.PARTICLES,
                CosmeticsScreen.Section.BACKGROUNDS, CosmeticsScreen.Section.BADGES, CosmeticsScreen.Section.HUD)) {
            small.showSection(section);
            fx.frame(small);
            assertCardsReachable(small, section.toString());
        }

        small.showSection(CosmeticsScreen.Section.PARTICLES);
        fx.frame(small);
        ScrollPanel scroll = scrollOf(grid(small.root()));
        CosmeticCard dust = small.card(Lang.tr(MenuParticles.DUST.langKey())).orElseThrow();
        scroll.setScrollY(small.context(), scroll.maxScroll(), false);
        fx.frame(small);
        assertTrue(dust.bounds().bottom() <= scroll.bounds().bottom(), "scrolled into view: " + dust.bounds());
        fx.click(small, dust);
        assertEquals(MenuParticles.DUST, fx.services.settings().get(VantaSettings.MENU_PARTICLES));
    }

    /**
     * The settled layout holds for every card section at every supported size, with and without large text, right
     * after a section switch and right after a resize (before the next frame): the second grid pass runs inside
     * the same layout, so no frame ever shows cards outside their grid.
     */
    @Test
    void everySectionAtEverySizeKeepsCardsReachable() {
        int[][] sizes = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}};
        for (boolean large : new boolean[]{false, true}) {
            ServicesFixture f = new ServicesFixture(dir.resolve(large ? "large" : "normal"));
            f.services.settings().set(VantaSettings.ACCESSIBILITY_LARGE_TEXT, large);
            for (int[] size : sizes) {
                CosmeticsScreen s = f.open(ScreenId.COSMETICS, size[0], size[1]);
                String at = size[0] + "x" + size[1] + (large ? " large text " : " ");
                for (CosmeticsScreen.Section section : CosmeticsScreen.Section.values()) {
                    if (section == CosmeticsScreen.Section.PACKS) {
                        continue;
                    }
                    s.showSection(section);
                    f.frame(s);
                    assertCardsReachable(s, at + section);
                }
                s.showSection(CosmeticsScreen.Section.BADGES);
                f.frame(s);
                for (int[] to : new int[][]{{854, 480}, {320, 240}, {640, 360}}) {
                    s.resize(to[0], to[1]);
                    assertCardsReachable(s, at + "resized to " + to[0] + "x" + to[1] + " before the next frame");
                    f.frame(s);
                    assertCardsReachable(s, at + "resized to " + to[0] + "x" + to[1]);
                }
            }
        }
    }
}
