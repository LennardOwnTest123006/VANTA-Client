package dev.vanta.core.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.vanta.core.modrinth.FakeModPlatform;
import dev.vanta.core.modrinth.FakeModrinthApi;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.screen.mods.ModsScreen;
import dev.vanta.core.ui.UiScreen;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every registered screen, laid out at common window sizes at effective scale 1: every visible, enabled
 * interactive node must be clickable (see {@link ReachabilityWalker}). A node that is rendered but can never
 * receive a click is exactly the "Install only works in fullscreen" bug.
 */
class ScreenReachabilityTest {
    /** Host sizes in GUI pixels (scale 1). 320x240 is the vanilla minimum, 427x240 is 854x480 at GUI scale 2. */
    static final int[][] SIZES = {
        {320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}, {960, 540}, {1280, 720}, {1920, 1080},
    };

    @TempDir
    Path dir;

    private ScreenTestSupport t;

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
        t.game.onTitleScreen();
        ScreenBootstrap.registerAll(t.services);
        t.services.profiles().activate("default");
        new FakeModPlatform(dir).installInto(t.services, FakeModrinthApi.standard());
    }

    /** Shows a screen at the size and lets it settle (ticks and frames), selecting content where needed. */
    UiScreen open(ScreenId id, int w, int h) {
        UiScreen screen = t.show(id, w, h);
        assertEquals(1f, screen.effectiveScale(), "the walk assumes effective scale 1");
        if (screen instanceof ModsScreen mods) {
            assertTrue(mods.isAvailable(), "Modrinth service installed");
            assertTrue(!mods.results().hits().isEmpty(), "the fake search returned hits");
            mods.selectHit(mods.results().hits().get(0));
        }
        settle(screen);
        return screen;
    }

    static void settle(UiScreen screen) {
        for (int i = 0; i < 3; i++) {
            screen.tick();
        }
        // Two frames: wrapped labels and responsive grids measure against the previous pass.
        screen.render(new dev.vanta.core.ui.TestCanvas(screen.width(), screen.height()), -1000, -1000, 0f);
        screen.render(new dev.vanta.core.ui.TestCanvas(screen.width(), screen.height()), -1000, -1000, 0f);
    }

    @Test
    void everyScreenIsRegistered() {
        for (ScreenId id : ScreenId.values()) {
            assertTrue(t.services.screens().isRegistered(id), id.toString());
        }
    }

    @Test
    @Disabled("reproduces the small-window click bug; enabled again by the fix")
    void everyInteractiveNodeIsClickableAtEverySize() {
        List<ReachabilityWalker.Failure> failures = new ArrayList<>();
        Map<String, String> summary = new LinkedHashMap<>();
        for (ScreenId id : ScreenId.values()) {
            for (int[] size : SIZES) {
                String sizeName = size[0] + "x" + size[1];
                UiScreen screen = open(id, size[0], size[1]);
                ReachabilityWalker.Report report = ReachabilityWalker.walk(screen, id.name(), sizeName);
                summary.put(id.name() + " @ " + sizeName, report.interactive() + " interactive, "
                        + report.skippedScrolledOut() + " scrolled out, " + report.failures().size() + " unreachable");
                failures.addAll(report.failures());
            }
        }
        StringBuilder out = new StringBuilder("Reachability summary:\n");
        summary.forEach((k, v) -> out.append("  ").append(k).append(": ").append(v).append('\n'));
        out.append(failures.size()).append(" unreachable interactive node(s):\n");
        for (ReachabilityWalker.Failure f : failures) {
            out.append("  ").append(f).append('\n');
        }
        System.out.println(out);
        if (!failures.isEmpty()) {
            fail(out.toString());
        }
    }

    /**
     * The sizes every screen already passes at (480x270 and 854x480 and up): locked in so the fix cannot regress
     * them. The failing sizes are 320x240 (SETTINGS, HUD_EDITOR, MODS), 427x240 (MODS) and 640x360 (MAIN_MENU).
     */
    @Test
    void everyInteractiveNodeIsClickableAtSizesThatFitToday() {
        List<ReachabilityWalker.Failure> failures = new ArrayList<>();
        for (ScreenId id : ScreenId.values()) {
            for (int[] size : new int[][] {{480, 270}, {854, 480}, {960, 540}, {1280, 720}, {1920, 1080}}) {
                UiScreen screen = open(id, size[0], size[1]);
                failures.addAll(ReachabilityWalker.walk(screen, id.name(), size[0] + "x" + size[1]).failures());
            }
        }
        if (!failures.isEmpty()) {
            fail(failures.size() + " unreachable interactive node(s):\n" + String.join("\n",
                    failures.stream().map(Object::toString).toList()));
        }
    }
}
