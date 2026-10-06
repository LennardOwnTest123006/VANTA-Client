package dev.vanta.core.screen.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.modrinth.FakeModPlatform;
import dev.vanta.core.modrinth.FakeModrinthApi;
import dev.vanta.core.screen.ReachabilityWalker;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.widget.Button;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for the "Install only works in fullscreen" report: in a windowed 854x480 game at GUI scale 2
 * the screen is 427x240 GUI pixels; the detail panel's Install button and the Performance pack's Install button
 * must still be clickable there.
 */
class ModsScreenSmallWindowTest {
    @TempDir
    Path dir;

    private ScreenTestSupport t;

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
        t.game.onTitleScreen();
        new FakeModPlatform(dir).installInto(t.services, FakeModrinthApi.standard());
    }

    private ModsScreen open(int w, int h) {
        ModsScreen screen = t.show(ScreenId.MODS, w, h);
        screen.selectHit(screen.results().hits().get(0));
        for (int i = 0; i < 3; i++) {
            screen.tick();
        }
        t.frame(screen, -1000, -1000);
        t.frame(screen, -1000, -1000);
        return screen;
    }

    private static void assertReachable(ModsScreen screen, String id, String size) {
        UiNode node = screen.root().findById(id);
        assertNotNull(node, "no node " + id);
        assertTrue(node.isVisible() && node.isEnabled(), id + " visible and enabled");
        List<ReachabilityWalker.Failure> failures = new ArrayList<>(
                ReachabilityWalker.check(screen, node, "MODS", size));
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures.stream().map(Object::toString).toList()));
    }

    /** Full left click at the centre of the node's visible part, routed through the screen like the game does. */
    private static void clickVisibleCentre(ModsScreen screen, UiNode node) {
        Rect visible = ReachabilityWalker.visibleRect(screen, node);
        assertTrue(!visible.isEmpty(), () -> ReachabilityWalker.describe(node) + " has no visible pixel");
        double x = (visible.x() + visible.w() / 2.0) * screen.effectiveScale();
        double y = (visible.y() + visible.h() / 2.0) * screen.effectiveScale();
        screen.mouseDown(x, y, Keys.MOUSE_LEFT);
        screen.mouseUp(x, y, Keys.MOUSE_LEFT);
    }

    /**
     * 427x240 is what a windowed 854x480 game shows at GUI scale 2 (or "auto"); 320x240 is the vanilla minimum.
     * The detail column (header, badges, stats, six-line description, spacer, Install, View) is taller than the
     * detail card there, so the Install button is drawn below the card and the Column/Row/Card ancestors never
     * route a click to it.
     */
    @ParameterizedTest(name = "{0}x{1}")
    @CsvSource({"320, 240", "427, 240"})
    void detailInstallButtonIsClickableInASmallWindow(int w, int h) {
        ModsScreen screen = open(w, h);
        assertReachable(screen, "mods.action.install", w + "x" + h);
        // And the click really installs: route it through the screen like the game does.
        clickVisibleCentre(screen, screen.root().findById("mods.action.install"));
        assertTrue(Files.exists(dir.resolve("mods/sodium-1.0.0.jar")), "the click reached the Install button");
    }

    /**
     * After an install the restart banner takes room above the cards and the detail footer holds Disable and
     * Remove instead of Install: the banner's button and both actions must stay clickable, and no interactive node
     * of the screen may be unreachable.
     */
    @ParameterizedTest(name = "{0}x{1} large text {2}")
    @CsvSource({"320, 240, false", "427, 240, false", "427, 240, true", "480, 270, true"})
    void installedActionsAndRestartBannerAreClickableInASmallWindow(int w, int h, boolean largeText) {
        t.services.settings().set(VantaSettings.ACCESSIBILITY_LARGE_TEXT, largeText);
        ModsScreen screen = open(w, h);
        assertEquals(largeText ? 1.15f : 1f, screen.effectiveScale(), 1e-6f);
        screen.install(screen.results().hits().get(0));
        screen.selectTab(ModsTab.INSTALLED);
        screen.rows().get(0).select(screen.context());
        for (int i = 0; i < 3; i++) {
            screen.tick();
        }
        t.frame(screen, -1000, -1000);
        t.frame(screen, -1000, -1000);
        assertTrue(screen.restartBanner().isVisible(), "restart banner shown after the install");
        String size = w + "x" + h + (largeText ? " large text" : "");
        assertReachable(screen, "mods.restart", size);
        assertReachable(screen, "mods.action.disable", size);
        assertReachable(screen, "mods.action.remove", size);
        List<ReachabilityWalker.Failure> failures = ReachabilityWalker.walk(screen, "MODS", size).failures();
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures.stream().map(Object::toString).toList()));
    }

    /** At 480x270 the detail column still (just) fits; lock that in. */
    @ParameterizedTest(name = "{0}x{1}")
    @CsvSource({"480, 270", "640, 360"})
    void detailInstallButtonIsClickableWhenTheDetailColumnFits(int w, int h) {
        ModsScreen screen = open(w, h);
        assertReachable(screen, "mods.action.install", w + "x" + h);
        clickVisibleCentre(screen, screen.root().findById("mods.action.install"));
        assertTrue(Files.exists(dir.resolve("mods/sodium-1.0.0.jar")), "the click reached the Install button");
    }

    /**
     * The Performance pack card is the first entry of the scrolling list. Its Install button is reachable in a
     * small window once the list is scrolled to it, so this is a passing sanity check, not a reproduction.
     */
    @ParameterizedTest(name = "{0}x{1}")
    @CsvSource({"320, 240", "427, 240", "480, 270"})
    void performancePackInstallButtonIsClickableInASmallWindowAfterScrolling(int w, int h) {
        ModsScreen screen = open(w, h);
        Button install = screen.packInstallButton();
        assertEquals("mods.pack.install", install.id());
        // The pack card sits at the top of the scrolling list; scroll the button into view the way the wheel would.
        ScrollPanel list = ReachabilityWalker.enclosingScroll(install);
        assertNotNull(list, "the pack card is inside the list scroll panel");
        list.scrollIntoView(screen.context(), install.bounds());
        t.advance(screen, 1_000);
        t.frame(screen, -1000, -1000);
        assertReachable(screen, "mods.pack.install", w + "x" + h);
        clickVisibleCentre(screen, install);
        t.frame(screen, -1000, -1000);
        assertTrue(Files.exists(dir.resolve("mods/lithium-1.0.0.jar")), "the click reached the pack Install button");
    }

    /** Sanity check of the fixture: at a fullscreen-like size both buttons are reachable today. */
    @ParameterizedTest(name = "{0}x{1}")
    @CsvSource({"960, 540", "1920, 1080"})
    void bothInstallButtonsAreClickableInFullscreenSizes(int w, int h) {
        ModsScreen screen = open(w, h);
        assertReachable(screen, "mods.action.install", w + "x" + h);
        assertReachable(screen, "mods.pack.install", w + "x" + h);
        screen.render(new TestCanvas(screen.width(), screen.height()), -1000, -1000, 0f);
    }
}
