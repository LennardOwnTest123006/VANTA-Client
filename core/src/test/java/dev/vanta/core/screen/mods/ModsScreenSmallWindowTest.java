package dev.vanta.core.screen.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private FakeModrinthApi api;

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
        t.game.onTitleScreen();
        api = FakeModrinthApi.standard();
        new FakeModPlatform(dir).installInto(t.services, api);
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
        // Large text is 1.15 where it fits; in these windows the screen clamps it so 320x240 logical px remain.
        assertEquals(ReachabilityWalker.expectedScale(largeText ? 1.15f : 1f, w, h), screen.effectiveScale(),
                1e-6f);
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

    /**
     * The detail text scrolls above a pinned action footer. With a long title and description the text area must
     * overflow in a small window, a wheel turn over it must move the text (not the footer), the footer must never
     * share pixels with the text area or anything scrolled inside it, and Install must stay clickable afterwards.
     */
    @ParameterizedTest(name = "{0}x{1} large text {2}")
    @CsvSource({"320, 240, false", "427, 240, false", "427, 240, true", "320, 240, true"})
    void detailTextScrollsWithTheWheelAndTheFooterStaysClear(int w, int h, boolean largeText) {
        t.services.settings().set(VantaSettings.ACCESSIBILITY_LARGE_TEXT, largeText);
        api.add("LONGTITL", "a-very-long-slug", "A Very Long Project Title That Keeps Going Without Any End", "mod",
                "an-author-with-an-extremely-long-account-name", "This description is deliberately long so the "
                        + "detail panel has to wrap it over many lines and the text area has to scroll in a small "
                        + "window: rendering, chunk meshing, entity culling, memory usage, shader compatibility, "
                        + "resource pack reloads, world generation, networking and mod compatibility.",
                999_999_999_999L, -1);
        ModsScreen screen = open(w, h);
        assertEquals("a-very-long-slug", screen.results().hits().get(0).slug(), "the long project is selected");
        UiNode install = screen.root().findById("mods.action.install");
        assertNotNull(install);
        ScrollPanel footer = ReachabilityWalker.enclosingScroll(install);
        assertNotNull(footer, "the actions sit in the footer scroll panel");
        ScrollPanel text = (ScrollPanel) footer.parent().children().get(0);
        String size = w + "x" + h + (largeText ? " large text" : "");
        assertTrue(text.isScrollable(), () -> size + ": the long text overflows the text area ("
                + text.contentHeight() + " in " + text.bounds().h() + ")");
        assertFalse(footer.isScrollable(), () -> size + ": the footer fits at its full height");
        assertFalse(text.bounds().intersects(footer.bounds()), "footer pinned below the text area");
        assertEquals(0f, text.targetScrollY());
        float s = screen.effectiveScale();
        Rect tb = text.bounds();
        assertTrue(screen.mouseScroll(tb.centerX() * s, tb.centerY() * s, 0, -1), "the wheel is consumed");
        assertTrue(text.targetScrollY() > 0f, "a wheel turn over the text scrolls it");
        assertEquals(0f, footer.targetScrollY(), "the footer does not move");
        t.advance(screen, 1_000);
        assertTrue(text.scrollY() > 0f, "the scroll animation arrived");
        // Nothing scrolled inside the text area may show up over the footer.
        List<UiNode> inText = new ArrayList<>();
        collect(text.content(), inText);
        for (UiNode node : inText) {
            Rect visible = ReachabilityWalker.visibleRect(screen, node);
            assertFalse(visible.intersects(footer.bounds()), () -> ReachabilityWalker.describe(node)
                    + " visible at " + visible + " overlaps the footer " + footer.bounds());
        }
        // A wheel turn over the footer scrolls nothing (the footer fits) and the text keeps its offset.
        float before = text.targetScrollY();
        Rect fb = footer.bounds();
        screen.mouseScroll(fb.centerX() * s, fb.centerY() * s, 0, -1);
        assertEquals(before, text.targetScrollY(), "the footer does not forward the wheel to the text");
        List<ReachabilityWalker.Failure> failures = ReachabilityWalker.walk(screen, "MODS", size).failures();
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures.stream().map(Object::toString).toList()));
        clickVisibleCentre(screen, install);
        assertTrue(Files.exists(dir.resolve("mods/a-very-long-slug-1.0.0.jar")), "the click reached Install");
    }

    private static void collect(UiNode node, List<UiNode> out) {
        out.add(node);
        for (UiNode child : node.children()) {
            collect(child, out);
        }
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
