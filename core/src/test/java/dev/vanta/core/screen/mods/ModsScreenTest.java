package dev.vanta.core.screen.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.modrinth.FakeModPlatform;
import dev.vanta.core.modrinth.FakeModrinthApi;
import dev.vanta.core.modrinth.ModrinthException;
import dev.vanta.core.modrinth.ModrinthSearchHit;
import dev.vanta.core.modrinth.ModrinthService;
import dev.vanta.core.modrinth.RestartMarker;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Dialog;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModsScreenTest {
    @TempDir
    Path dir;

    private ScreenTestSupport t;
    private FakeModrinthApi api;
    private FakeModPlatform platform;
    private ModrinthService service;

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
        t.game.onTitleScreen();
        api = FakeModrinthApi.standard();
        platform = new FakeModPlatform(dir);
        service = platform.installInto(t.services, api);
    }

    @AfterEach
    void clearProperty() {
        System.clearProperty(RestartMarker.RESTARTABLE_PROPERTY);
    }

    private ModsScreen open() {
        return t.show(ScreenId.MODS, 854, 480);
    }

    private static ModrinthSearchHit hit(ModsScreen screen, String slug) {
        return screen.results().hits().stream().filter(h -> h.slug().equals(slug)).findFirst().orElseThrow();
    }

    private void click(ModsScreen screen, String id) {
        t.frame(screen, -1000, -1000);
        UiNode node = screen.root().findById(id);
        assertNotNull(node, "no node " + id);
        ((Button) node).click(screen.context());
        t.frame(screen, -1000, -1000);
    }

    @Test
    void opensOnTheModsTabWithThePerformancePackAndPopularMods() {
        ModsScreen screen = open();
        assertTrue(screen.isAvailable());
        assertEquals(ModsTab.MODS, screen.tab());
        assertEquals(7, screen.results().hits().size());
        assertEquals("sodium", screen.results().hits().get(0).slug(), "most downloaded first");
        assertEquals(7, screen.rows().size());
        assertNotNull(screen.root().findById("mods.pack"));
        assertEquals(6, screen.packToggles().size());
        assertTrue(screen.packToggles().stream().allMatch(PackItemToggle::isChecked));
        assertEquals("Not installed", screen.packToggles().get(0).stateText());
        assertEquals("Install performance pack", screen.packInstallButton().label());
        assertTrue(screen.packInstallButton().isEnabled());
        assertFalse(screen.restartBanner().isVisible());
        assertTrue(api.calls().contains("search:mod:"));
    }

    @Test
    void typingSearchesAfterAPauseAndEnterSearchesAtOnce() {
        ModsScreen screen = open();
        screen.searchField().requestFocus(screen.context());
        ScreenTestSupport.type(screen, "iris");
        assertFalse(api.calls().contains("search:mod:iris"), "debounced");
        for (int i = 0; i < ModsScreen.SEARCH_DEBOUNCE_TICKS; i++) {
            screen.tick();
        }
        assertTrue(api.calls().contains("search:mod:iris"));
        assertEquals(1, screen.results().hits().size());
        ScreenTestSupport.type(screen, " x");
        ScreenTestSupport.key(screen, Keys.ENTER);
        assertTrue(api.calls().contains("search:mod:iris x"));
        assertTrue(screen.results().hits().isEmpty());
    }

    @Test
    void installFromTheDetailPanelInstallsWithDependenciesAndAsksForARestart() {
        ModsScreen screen = open();
        screen.selectHit(hit(screen, "iris"));
        assertEquals("YL57xq9U", screen.selectedKey().orElseThrow());
        click(screen, "mods.action.install");
        assertTrue(Files.exists(dir.resolve("mods/iris-1.0.0.jar")));
        assertTrue(Files.exists(dir.resolve("mods/sodium-1.0.0.jar")), "required dependency");
        assertTrue(screen.restartBanner().isVisible());
        assertEquals("Quit game", screen.restartButton().label());
        assertTrue(screen.restartButton().isEnabled());
        assertEquals("Installed", screen.row("AANobbMI").orElseThrow().model().badge());
        assertNotNull(screen.root().findById("mods.action.remove"), "installed project offers Remove");
        assertNotNull(screen.root().findById("mods.action.disable"));
        assertNull(screen.root().findById("mods.action.install"));
    }

    @Test
    void performancePackInstallsInOneClick() {
        ModsScreen screen = open();
        click(screen, "mods.pack.install");
        for (String slug : new String[] {"sodium", "lithium", "ferrite-core", "immediatelyfast", "entityculling", "iris"}) {
            assertTrue(Files.exists(dir.resolve("mods/" + slug + "-1.0.0.jar")), slug);
        }
        assertTrue(screen.packToggles().stream().allMatch(PackItemToggle::isInstalled));
        assertEquals("Installed · restart", screen.packToggles().get(0).stateText());
        assertEquals("Everything installed", screen.packInstallButton().label());
        assertFalse(screen.packInstallButton().isEnabled());
        assertTrue(service.restartRequired());
    }

    @Test
    void untickedPackMembersAreNotInstalled() {
        ModsScreen screen = open();
        PackItemToggle iris = screen.packToggles().get(5);
        assertEquals("iris", iris.item().slug());
        iris.toggle(screen.context());
        assertEquals("Install 5 selected", screen.packInstallButton().label());
        screen.installPack();
        assertFalse(Files.exists(dir.resolve("mods/iris-1.0.0.jar")));
        assertTrue(Files.exists(dir.resolve("mods/sodium-1.0.0.jar")));
        assertEquals("Install performance pack", screen.packInstallButton().label(), "only the unticked Iris is left");
        assertFalse(screen.packInstallButton().isEnabled());
        screen.packToggles().get(5).toggle(screen.context());
        assertTrue(screen.packInstallButton().isEnabled());
    }

    @Test
    void loadedModsShowAsActive() {
        platform.loaded.add("sodium");
        ModsScreen screen = open();
        assertEquals("Active", screen.packToggles().get(0).stateText());
        assertFalse(screen.packToggles().get(0).isChecked());
    }

    @Test
    void shaderInstallOffersTheIrisShaderScreen() {
        platform.loaded.add("iris");
        ModsScreen screen = open();
        screen.selectTab(ModsTab.SHADERS);
        assertEquals(2, screen.results().hits().size());
        assertNull(screen.root().findById("mods.installIris"), "Iris is loaded");
        screen.install(hit(screen, "complementary-reimagined"));
        assertTrue(Files.exists(dir.resolve("shaderpacks/complementary-reimagined-1.0.0.zip")));
        assertFalse(service.restartRequired(), "shader packs work without a restart");
        assertNotNull(ScreenTestSupport.openDialog(screen));
        ScreenTestSupport.confirmDialog(screen);
        assertEquals(1, platform.calls.size());
    }

    @Test
    void searchShortcutWaitsWhileADialogIsOpen() {
        platform.loaded.add("iris");
        ModsScreen screen = open();
        screen.selectTab(ModsTab.SHADERS);
        screen.install(hit(screen, "complementary-reimagined"));
        t.frame(screen, -1000, -1000);
        Dialog dialog = ScreenTestSupport.openDialog(screen);
        assertNotNull(dialog);
        UiNode focused = screen.context().focus().focused();
        assertNotNull(focused);
        assertTrue(dialog.isAncestorOf(focused), "a dialog button has focus");

        assertFalse(ScreenTestSupport.key(screen, Keys.F, Keys.MOD_CONTROL), "Ctrl+F is not a dialog key");
        assertFalse(screen.searchField().isFocused(), "the search field behind the dialog stays unfocused");
        assertSame(focused, screen.context().focus().focused());
        t.frame(screen, -1000, -1000);
        assertSame(focused, screen.context().focus().focused(), "focus survives the next frame");
        assertNotNull(ScreenTestSupport.openDialog(screen));

        assertTrue(ScreenTestSupport.key(screen, Keys.ENTER), "Enter still activates the dialog's default button");
        assertNull(ScreenTestSupport.openDialog(screen));
        assertEquals(1, platform.calls.size());

        assertTrue(ScreenTestSupport.key(screen, Keys.F, Keys.MOD_CONTROL), "the shortcut works again afterwards");
        assertTrue(screen.searchField().isFocused());
    }

    @Test
    void shaderScreenFallbackExplainsTheIrisKey() {
        platform.loaded.add("iris");
        platform.shaderScreenWorks = false;
        ModsScreen screen = open();
        screen.openShaderSettings();
        assertEquals("Open the shader settings in game", t.services.notifications().history().get(0).title());
    }

    @Test
    void shadersWithoutIrisOfferToInstallIt() {
        ModsScreen screen = open();
        screen.selectTab(ModsTab.SHADERS);
        t.frame(screen, -1000, -1000);
        assertNotNull(screen.root().findById("mods.installIris"));
        click(screen, "mods.installIris");
        assertTrue(Files.exists(dir.resolve("mods/iris-1.0.0.jar")));
    }

    @Test
    void resourcePackInstallOffersTheVanillaPackScreen() {
        ModsScreen screen = open();
        screen.selectTab(ModsTab.RESOURCE_PACKS);
        screen.install(hit(screen, "fresh-animations"));
        assertTrue(Files.exists(dir.resolve("resourcepacks/fresh-animations-1.0.0.zip")));
        ScreenTestSupport.confirmDialog(screen);
        assertEquals("openVanillaScreen:RESOURCE_PACKS", t.game.actions.get(t.game.actions.size() - 1));
    }

    @Test
    void installedTabDisablesRemovesAndLeavesManualFilesAlone() throws Exception {
        ModsScreen screen = open();
        screen.install(hit(screen, "lithium"));
        Files.writeString(dir.resolve("mods/handmade.jar"), "mine");
        screen.selectTab(ModsTab.INSTALLED);
        assertEquals(2, screen.rows().size());
        assertTrue(screen.row("file:mods/handmade.jar").isPresent());
        assertEquals("Added by hand", screen.row("file:mods/handmade.jar").orElseThrow().model().badge());

        screen.row("project:LITHIUM1").orElseThrow().select(screen.context());
        click(screen, "mods.action.disable");
        assertTrue(Files.exists(dir.resolve("mods/lithium-1.0.0.jar.disabled")));
        assertEquals("Disabled", screen.row("project:LITHIUM1").orElseThrow().model().badge());
        click(screen, "mods.action.enable");
        assertTrue(Files.exists(dir.resolve("mods/lithium-1.0.0.jar")));

        click(screen, "mods.action.remove");
        ScreenTestSupport.confirmDialog(screen);
        assertFalse(Files.exists(dir.resolve("mods/lithium-1.0.0.jar")));
        assertEquals(1, screen.rows().size());

        screen.row("file:mods/handmade.jar").orElseThrow().select(screen.context());
        t.frame(screen, -1000, -1000);
        assertNull(screen.root().findById("mods.action.remove"), "files VANTA did not install cannot be removed here");
        assertTrue(Files.exists(dir.resolve("mods/handmade.jar")));
    }

    @Test
    void installedTabFiltersLocally() throws Exception {
        ModsScreen screen = open();
        screen.install(hit(screen, "lithium"));
        screen.install(hit(screen, "sodium"));
        screen.selectTab(ModsTab.INSTALLED);
        int searches = (int) api.calls().stream().filter(c -> c.startsWith("search:")).count();
        screen.setQuery("lith");
        assertEquals(1, screen.rows().size());
        assertEquals(searches, api.calls().stream().filter(c -> c.startsWith("search:")).count(), "no network");
    }

    @Test
    void restartWritesTheMarkerWhenTheLauncherCanRelaunch() {
        System.setProperty(RestartMarker.RESTARTABLE_PROPERTY, "true");
        ModsScreen screen = open();
        screen.install(hit(screen, "sodium"));
        assertEquals("Restart game", screen.restartButton().label());
        click(screen, "mods.restart");
        assertTrue(Files.exists(dir.resolve("config/vanta/restart.request")));
        assertEquals("quit", t.game.actions.get(t.game.actions.size() - 1));
    }

    @Test
    void restartIsBlockedInsideAWorld() {
        t.game.inWorld = true;
        ModsScreen screen = open();
        screen.install(hit(screen, "sodium"));
        assertFalse(screen.restartButton().isEnabled());
        screen.restart();
        assertFalse(t.game.actions.contains("quit"));
    }

    @Test
    void searchErrorsShowRetry() {
        api.failSearch(new ModrinthException(ModrinthException.Kind.NETWORK, "offline"));
        ModsScreen screen = open();
        assertEquals(ModrinthException.Kind.NETWORK, screen.searchError().orElseThrow().kind());
        api.failSearch(null);
        click(screen, "mods.retry");
        assertTrue(screen.searchError().isEmpty());
        assertEquals(7, screen.results().hits().size());
    }

    @Test
    void withoutTheModrinthServiceTheScreenExplainsWhy() {
        t.services.setModrinth(null);
        ModsScreen screen = open();
        assertFalse(screen.isAvailable());
        t.advance(screen, 50);
        screen.tick();
    }

    @Test
    void rendersOnSmallWindows() {
        ModsScreen screen = t.show(ScreenId.MODS, 427, 240);
        t.advance(screen, 50);
        screen.selectHit(hit(screen, "sodium"));
        t.advance(screen, 50);
        screen.selectTab(ModsTab.INSTALLED);
        t.advance(screen, 50);
    }

    @Test
    void reachableFromTheMainMenuSettingsPerformanceCenterAndSearch() {
        var menu = t.show(ScreenId.MAIN_MENU, 854, 480);
        ((Button) menu.root().findById("menu.mods")).click(menu.context());
        assertTrue(t.host.events().contains("openScreen:MODS"));
        assertTrue(t.services.search().search("shaders", 10).stream()
                .anyMatch(r -> r.toString().contains("open_mods")));
    }
}
