package dev.vanta.core.screen.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.modrinth.FakeModPlatform;
import dev.vanta.core.modrinth.FakeModrinthApi;
import dev.vanta.core.modrinth.RestartMarker;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.TextureRef;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Dialog;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainMenuScreenTest {
    @TempDir
    Path dir;
    private ScreenTestSupport t;

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
        t.game.onTitleScreen();
    }

    @Test
    void playLabelContinuesTheLastWorldWhenKnown() {
        t.game.lastWorldName = Optional.of("Survival World");
        assertEquals("Continue · Survival World", MainMenuScreen.playLabel(t.game));
        t.game.lastWorldName = Optional.empty();
        assertEquals("Play", MainMenuScreen.playLabel(t.game));
    }

    @Test
    void playContinuesTheLastWorldOrOpensTheWorldList() {
        MainMenuScreen menu = t.show(ScreenId.MAIN_MENU, 854, 480);
        ScreenTestSupport.click(menu, menu.playButton());
        assertEquals("continueLastWorld", t.game.actions.get(t.game.actions.size() - 1));

        t.game.lastWorldName = Optional.empty();
        MainMenuScreen fresh = t.show(ScreenId.MAIN_MENU, 854, 480);
        assertEquals("Play", fresh.playButton().label());
        ScreenTestSupport.click(fresh, fresh.playButton());
        assertEquals("openVanillaScreen:SINGLEPLAYER", t.game.actions.get(t.game.actions.size() - 1));
    }

    @Test
    void buttonsNavigateToVantaAndVanillaScreens() {
        MainMenuScreen menu = t.show(ScreenId.MAIN_MENU, 854, 480);
        ScreenTestSupport.click(menu, menu.root().findById("menu.options"));
        assertTrue(t.host.events().contains("openScreen:SETTINGS"));
        ScreenTestSupport.click(menu, menu.root().findById("menu.multiplayer"));
        assertEquals("openVanillaScreen:MULTIPLAYER", t.game.actions.get(t.game.actions.size() - 1));
        ScreenTestSupport.click(menu, menu.root().findById("menu.language"));
        assertEquals("openVanillaScreen:LANGUAGE", t.game.actions.get(t.game.actions.size() - 1));
        ScreenTestSupport.click(menu, menu.root().findById("menu.quick.search"));
        assertTrue(t.host.events().contains("openScreen:SEARCH"));
        ScreenTestSupport.click(menu, menu.root().findById("menu.quick.about"));
        assertTrue(t.host.events().contains("openScreen:ABOUT"));
        ScreenTestSupport.click(menu, menu.root().findById("menu.profile"));
        assertTrue(t.host.events().contains("openScreen:PROFILES"));
    }

    @Test
    void firstShowOffersThePerformancePackOnceAndNotNowSwitchesItOff() {
        new FakeModPlatform(dir).installInto(t.services, FakeModrinthApi.standard());
        t.services.packOffer().setSuppressed(false);
        MainMenuScreen menu = t.show(ScreenId.MAIN_MENU, 854, 480);
        Dialog dialog = ScreenTestSupport.openDialog(menu);
        assertNotNull(dialog, "the one-time offer opens with the main menu");
        assertEquals("Boost your FPS?", dialog.title());
        assertTrue(t.services.settings().get(VantaSettings.MODS_PACK_OFFER));
        ScreenTestSupport.cancelDialog(menu);
        assertNull(ScreenTestSupport.openDialog(menu));
        assertFalse(t.services.settings().get(VantaSettings.MODS_PACK_OFFER), "Not now turns the offer off");
        MainMenuScreen again = t.show(ScreenId.MAIN_MENU, 854, 480);
        assertNull(ScreenTestSupport.openDialog(again), "shown once per session at most");
        // The menu itself still works with the dialog gone.
        ScreenTestSupport.click(again, again.root().findById("menu.options"));
        assertTrue(t.host.events().contains("openScreen:SETTINGS"));
    }

    @Test
    void noOfferForSuppressedHostsOrLauncherStartedGames() {
        new FakeModPlatform(dir).installInto(t.services, FakeModrinthApi.standard());
        assertNull(ScreenTestSupport.openDialog(t.show(ScreenId.MAIN_MENU, 854, 480)), "the fixture suppresses it");
        t.services.packOffer().setSuppressed(false);
        System.setProperty(RestartMarker.RESTARTABLE_PROPERTY, "true");
        try {
            assertNull(ScreenTestSupport.openDialog(t.show(ScreenId.MAIN_MENU, 854, 480)),
                    "the VANTA launcher installs the pack itself");
        } finally {
            System.clearProperty(RestartMarker.RESTARTABLE_PROPERTY);
        }
        assertFalse(t.services.packOffer().wasShown());
    }

    @Test
    void quitAsksForConfirmationFirst() {
        MainMenuScreen menu = t.show(ScreenId.MAIN_MENU, 854, 480);
        ScreenTestSupport.click(menu, menu.root().findById("menu.quit"));
        assertNotNull(ScreenTestSupport.openDialog(menu), "confirmation dialog");
        assertFalse(t.game.actions.contains("quit"));
        ScreenTestSupport.cancelDialog(menu);
        assertFalse(t.game.actions.contains("quit"));
        ScreenTestSupport.click(menu, menu.root().findById("menu.quit"));
        ScreenTestSupport.confirmDialog(menu);
        assertTrue(t.game.actions.contains("quit"));
    }

    @Test
    void shortWindowsUseTwoColumns() {
        MainMenuScreen menu = t.show(ScreenId.MAIN_MENU, 427, 240);
        assertTrue(menu.isCompact());
        UiNode single = menu.root().findById("menu.singleplayer");
        UiNode multi = menu.root().findById("menu.multiplayer");
        assertEquals(single.bounds().y(), multi.bounds().y(), "side by side");
        assertTrue(multi.bounds().x() > single.bounds().right());
        assertTrue(menu.root().findById("menu.quit").bounds().bottom() < 240);
        MainMenuScreen tall = t.show(ScreenId.MAIN_MENU, 854, 480);
        assertFalse(tall.isCompact());
        assertNotEquals(tall.root().findById("menu.singleplayer").bounds().y(),
                tall.root().findById("menu.multiplayer").bounds().y());
    }

    @Test
    void entranceIsStaggeredAndSkippedUnderReducedMotion() {
        MainMenuScreen menu = t.show(ScreenId.MAIN_MENU, 854, 480);
        MenuButton quit = (MenuButton) menu.root().findById("menu.quit");
        float early = quit.enterProgress(menu.context());
        assertTrue(early < 1f, "last button still entering right after open");
        t.advance(menu, MainMenuScreen.ENTER_TOTAL_MS + 1);
        assertEquals(1f, quit.enterProgress(menu.context()), 1e-6f);

        t.services.settings().set(VantaSettings.ACCESSIBILITY_REDUCED_MOTION, true);
        MainMenuScreen still = t.show(ScreenId.MAIN_MENU, 854, 480);
        assertEquals(1f, ((MenuButton) still.root().findById("menu.quit")).enterProgress(still.context()), 1e-6f);
        assertEquals(MenuParticles.NONE, still.particleKind(), "particles off under reduced motion");
    }

    @Test
    void backgroundFollowsTheSettingAndPanoramaIsDelegatedToTheHost() {
        t.services.settings().set(VantaSettings.MENU_BACKGROUND, MenuBackground.VANILLA_PANORAMA);
        MainMenuScreen menu = t.show(ScreenId.MAIN_MENU, 854, 480);
        assertTrue(menu.wantsVanillaPanorama());
        assertFalse(menu.wantsBlur());
        t.services.settings().set(VantaSettings.MENU_BACKGROUND, MenuBackground.GRAPHITE_GRID);
        assertFalse(menu.wantsVanillaPanorama());
        TestCanvas canvas = t.frame(menu, -1000, -1000);
        assertTrue(canvas.fills().size() > 50, "grid background is painted from fills");
        assertTrue(canvas.images().stream().anyMatch(i -> i.texture().equals(TextureRef.VANTA_LOGO)));
        assertTrue(canvas.images().stream().anyMatch(i -> i.texture().equals(TextureRef.VANTA_WORDMARK)));
        assertTrue(canvas.hasTextContaining("VANTA Client"), "version label");
    }

    @Test
    void particlesAnimateDeterministically() {
        t.services.settings().set(VantaSettings.MENU_PARTICLES, MenuParticles.EMBERS);
        MainMenuScreen menu = t.show(ScreenId.MAIN_MENU, 854, 480);
        for (int i = 0; i < 20; i++) {
            t.advance(menu, 50);
        }
        assertTrue(menu.particleCount() > 0);
        t.services.settings().set(VantaSettings.MENU_PARTICLES, MenuParticles.NONE);
        t.advance(menu, 50);
        assertEquals(0, menu.particleCount());
    }

    @Test
    void keyboardReachesEveryButtonAndEscapeDoesNotClose() {
        MainMenuScreen menu = t.show(ScreenId.MAIN_MENU, 854, 480);
        assertFalse(ScreenTestSupport.key(menu, Keys.ESCAPE));
        assertFalse(menu.isClosing());
        ScreenTestSupport.key(menu, Keys.TAB);
        assertEquals(menu.playButton(), menu.context().focus().focused());
        int focusable = menu.context().focus().traversal().size();
        assertTrue(focusable >= 15, "play + 6 stack + quit + 6 quick + profile chip");
        for (int i = 0; i < focusable - 1; i++) {
            ScreenTestSupport.key(menu, Keys.TAB);
        }
        assertEquals("menu.profile", menu.context().focus().focused().id());
        ScreenTestSupport.key(menu, Keys.TAB);
        assertEquals(menu.playButton(), menu.context().focus().focused(), "wraps around");
        Button options = (Button) menu.root().findById("menu.options");
        menu.context().focus().focus(menu.context(), options);
        ScreenTestSupport.key(menu, Keys.ENTER);
        assertTrue(t.host.events().contains("openScreen:SETTINGS"));
    }
}
