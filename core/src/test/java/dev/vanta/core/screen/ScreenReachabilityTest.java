package dev.vanta.core.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.vanta.core.modrinth.FakeModPlatform;
import dev.vanta.core.modrinth.FakeModrinthApi;
import dev.vanta.core.modrinth.InstallRequest;
import dev.vanta.core.modrinth.ModrinthException;
import dev.vanta.core.modrinth.ModrinthSearchHit;
import dev.vanta.core.modrinth.ModrinthService;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.screen.mods.ModsScreen;
import dev.vanta.core.screen.mods.ModsTab;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every registered screen, laid out at common window sizes at effective scale 1 and with large text (1.15):
 * every visible, enabled interactive node must be clickable and no two of them may share pixels (see
 * {@link ReachabilityWalker}). A node that is rendered but can never receive a click is exactly the "Install only
 * works in fullscreen" bug.
 */
class ScreenReachabilityTest {
    /** Host sizes in GUI pixels (scale 1). 320x240 is the vanilla minimum, 427x240 is 854x480 at GUI scale 2. */
    static final int[][] SIZES = {
        {320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}, {960, 540}, {1280, 720}, {1920, 1080},
    };

    /**
     * Every window and GUI scale combination a player is likely to use, from the vanilla minimum up: 1366x768 at
     * GUI scale 3 (455x256), 1280x720 at scale 2 (640x360) or 1 (1280x720), 1920x1080 at scale 4 / 3 / 2 / 1 and
     * the odd sizes of a freely resized window.
     */
    static final int[][] ALL_SIZES = {
        {320, 240}, {340, 250}, {360, 270}, {400, 225}, {427, 240}, {455, 256}, {480, 270}, {512, 288},
        {568, 320}, {640, 360}, {683, 384}, {711, 400}, {800, 450}, {854, 480}, {960, 540}, {1067, 600},
        {1280, 720}, {1707, 960}, {1920, 1080},
    };

    @TempDir
    Path dir;

    private ScreenTestSupport t;
    private FakeModrinthApi api;
    private FakeModPlatform platform;

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
        t.game.onTitleScreen();
        ScreenBootstrap.registerAll(t.services);
        t.services.profiles().activate("default");
        api = FakeModrinthApi.standard();
        platform = new FakeModPlatform(dir);
        platform.installInto(t.services, api);
    }

    /** Shows a screen at the size and lets it settle (ticks and frames), selecting content where needed. */
    UiScreen open(ScreenId id, int w, int h) {
        return open(id, w, h, 1f);
    }

    /** Shows a screen at the host size, checking that the theme produced the expected effective scale. */
    UiScreen open(ScreenId id, int w, int h, float expectedScale) {
        UiScreen screen = t.show(id, w, h);
        assertEquals(expectedScale, screen.effectiveScale(), 1e-6f, "effective scale of the test theme");
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
    void everyInteractiveNodeIsClickableAtEverySize() {
        walkAll(SIZES, false);
    }

    /**
     * Large text multiplies the effective scale by 1.15, so the same window leaves fewer logical pixels: 427x240
     * becomes 371x209 and 480x270 becomes 417x235. Every interactive node must still be clickable there.
     */
    @Test
    void everyInteractiveNodeIsClickableWithLargeText() {
        walkAll(new int[][] {{427, 240}, {480, 270}}, true);
    }

    /** The full matrix of window sizes at scale 1. */
    @Test
    void everyInteractiveNodeIsClickableAtEveryWindowSize() {
        walkAll(ALL_SIZES, false);
    }

    /** The full matrix of window sizes with large text (effective scale 1.15). */
    @Test
    void everyInteractiveNodeIsClickableAtEveryWindowSizeWithLargeText() {
        walkAll(ALL_SIZES, true);
    }

    private void walkAll(int[][] sizes, boolean largeText) {
        t.services.settings().set(VantaSettings.ACCESSIBILITY_LARGE_TEXT, largeText);
        float expectedScale = largeText ? 1.15f : 1f;
        List<ReachabilityWalker.Failure> failures = new ArrayList<>();
        Map<String, String> summary = new LinkedHashMap<>();
        for (ScreenId id : ScreenId.values()) {
            for (int[] size : sizes) {
                UiScreen screen = open(id, size[0], size[1], expectedScale);
                String sizeName = sizeName(size, largeText, screen);
                ReachabilityWalker.Report report = ReachabilityWalker.walk(screen, id.name(), sizeName);
                summary.put(id.name() + " @ " + sizeName, describe(report));
                failures.addAll(report.failures());
            }
        }
        report(summary, failures);
    }

    private static String sizeName(int[] size, boolean largeText, UiScreen screen) {
        return size[0] + "x" + size[1] + (largeText ? " large text" : "") + " (" + screen.width() + "x"
                + screen.height() + " logical)";
    }

    private static String describe(ReachabilityWalker.Report report) {
        return report.interactive() + " interactive, " + report.skippedScrolledOut() + " scrolled out, "
                + report.failures().size() + " unreachable";
    }

    private static void report(Map<String, String> summary, List<ReachabilityWalker.Failure> failures) {
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
     * The sizes every screen passed at before the layout fixes (480x270 and 854x480 and up), kept as a separate
     * lock so a regression there is told apart from one at the small sizes.
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

    /**
     * A window resized while a screen is open: every screen laid out at 1920x1080 and then shrunk to the small
     * sizes, and laid out at the vanilla minimum and then grown, at scale 1 and with large text. Layouts that
     * remember a previous pass (wrapped labels, responsive grids, the settings footer, the toolbar) must settle
     * on the new size.
     */
    @Test
    void everyInteractiveNodeIsClickableAfterAResize() {
        int[][] small = {{320, 240}, {427, 240}, {480, 270}, {640, 360}};
        List<ReachabilityWalker.Failure> failures = new ArrayList<>();
        Map<String, String> summary = new LinkedHashMap<>();
        for (boolean largeText : new boolean[] {false, true}) {
            t.services.settings().set(VantaSettings.ACCESSIBILITY_LARGE_TEXT, largeText);
            float expectedScale = largeText ? 1.15f : 1f;
            for (ScreenId id : ScreenId.values()) {
                UiScreen big = open(id, 1920, 1080, expectedScale);
                for (int[] size : small) {
                    big.resize(size[0], size[1]);
                    settle(big);
                    String name = "1920x1080 -> " + sizeName(size, largeText, big);
                    ReachabilityWalker.Report report = ReachabilityWalker.walk(big, id.name(), name);
                    summary.put(id.name() + " @ " + name, describe(report));
                    failures.addAll(report.failures());
                }
                for (int[] size : small) {
                    UiScreen screen = open(id, size[0], size[1], expectedScale);
                    screen.resize(1920, 1080);
                    settle(screen);
                    String name = size[0] + "x" + size[1] + " -> " + sizeName(new int[] {1920, 1080}, largeText, screen);
                    ReachabilityWalker.Report report = ReachabilityWalker.walk(screen, id.name(), name);
                    summary.put(id.name() + " @ " + name, describe(report));
                    failures.addAll(report.failures());
                }
            }
        }
        report(summary, failures);
    }

    // ---------------------------------------------------------------- Mods screen states

    /**
     * One state of the Mods screen: {@code before} prepares the service or the API before the screen opens,
     * {@code after} drives the opened screen (tab, selection, query).
     */
    record ModsState(String name, Runnable before, Consumer<ModsScreen> after) {
    }

    private static final String LONG_TITLE = "A Very Long Project Title That Keeps Going And Going Without Any End";
    private static final String LONG_AUTHOR = "an-author-with-an-extremely-long-account-name";
    private static final String LONG_DESCRIPTION = "This description is deliberately long so the detail panel has "
            + "to wrap it over many lines and the text area has to scroll in a small window. It mentions rendering, "
            + "chunk meshing, entity culling, memory usage, shader compatibility, resource pack reloads, world "
            + "generation, networking, mod compatibility and a few more words so that nothing fits on one screen.";

    private ModrinthService service() {
        return t.services.modrinth().orElseThrow();
    }

    private static ModrinthSearchHit hit(ModsScreen screen, String slug) {
        return screen.results().hits().stream().filter(h -> h.slug().equals(slug)).findFirst().orElseThrow();
    }

    private static void selectRow(ModsScreen screen, String key) {
        screen.row(key).orElseThrow(() -> new AssertionError("no row " + key)).select(screen.context());
    }

    private void write(String relative) {
        try {
            Path file = dir.resolve(relative);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "x", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void delete(String relative) {
        try {
            Files.deleteIfExists(dir.resolve(relative));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The states in the order they are applied; installs persist between them (same game directory), so the
     * states without an install come first.
     */
    private List<ModsState> modsStates() {
        List<ModsState> states = new ArrayList<>();
        states.add(new ModsState("no results", () -> { }, s -> s.setQuery("nothing-matches-this")));
        states.add(new ModsState("search error", () -> api.failSearch(
                new ModrinthException(ModrinthException.Kind.NETWORK, "offline")), s -> { }));
        states.add(new ModsState("long title and author", () -> api.add("LONGTITL", "a-very-long-slug", LONG_TITLE,
                "mod", LONG_AUTHOR, LONG_DESCRIPTION, 999_999_999_999L, -1),
                s -> s.selectHit(hit(s, "a-very-long-slug"))));
        states.add(new ModsState("shaders tab, Iris not loaded", () -> { }, s -> {
            s.selectTab(ModsTab.SHADERS);
            s.selectHit(s.results().hits().get(0));
        }));
        states.add(new ModsState("resource packs tab", () -> { }, s -> {
            s.selectTab(ModsTab.RESOURCE_PACKS);
            s.selectHit(s.results().hits().get(0));
        }));
        states.add(new ModsState("installed tab, nothing installed", () -> { }, s -> s.selectTab(ModsTab.INSTALLED)));
        states.add(new ModsState("installed from search, restart banner", () -> service().install(
                List.of(InstallRequest.of("sodium")), "Installing", null), s -> s.selectHit(hit(s, "sodium"))));
        states.add(new ModsState("installed tab, local mod selected", () -> { }, s -> {
            s.selectTab(ModsTab.INSTALLED);
            selectRow(s, "project:AANobbMI");
        }));
        states.add(new ModsState("installed tab, remove dialog open", () -> { }, s -> {
            s.selectTab(ModsTab.INSTALLED);
            selectRow(s, "project:AANobbMI");
            // Clicked on its visible part: when even the footer has to scroll, only a strip of Remove shows.
            UiNode remove = s.root().findById("mods.action.remove");
            Rect visible = ReachabilityWalker.visibleRect(s, remove);
            float scale = s.effectiveScale();
            double x = (visible.x() + visible.w() / 2.0) * scale;
            double y = (visible.y() + visible.h() / 2.0) * scale;
            s.mouseDown(x, y, Keys.MOUSE_LEFT);
            s.mouseUp(x, y, Keys.MOUSE_LEFT);
            assertTrue(s.context().popups().isOpen(), () -> "the remove confirmation is open at " + s.width() + "x"
                    + s.height() + " (remove visible at " + visible + ")");
        }));
        states.add(new ModsState("installed tab, disabled mod selected", () -> service().setEnabled("AANobbMI",
                false, e -> { }), s -> {
            s.selectTab(ModsTab.INSTALLED);
            selectRow(s, "project:AANobbMI");
        }));
        states.add(new ModsState("installed tab, manual file selected", () -> write("mods/handmade-mod-2.1.jar"),
                s -> {
                    s.selectTab(ModsTab.INSTALLED);
                    selectRow(s, "file:mods/handmade-mod-2.1.jar");
                }));
        states.add(new ModsState("installed tab, resource pack selected", () -> service().install(
                List.of(InstallRequest.of("fresh-animations")), "Installing", null), s -> {
            s.selectTab(ModsTab.INSTALLED);
            selectRow(s, "project:FRESHAN1");
        }));
        states.add(new ModsState("installed tab, missing file (forget)", () -> delete("mods/sodium-1.0.0.jar.disabled"),
                s -> {
                    s.selectTab(ModsTab.INSTALLED);
                    selectRow(s, "project:AANobbMI");
                }));
        states.add(new ModsState("installed tab, shader with Iris loaded", () -> {
            platform.loaded.add("iris");
            service().install(List.of(InstallRequest.of("complementary-reimagined")), "Installing", null);
        }, s -> {
            s.selectTab(ModsTab.INSTALLED);
            selectRow(s, "project:HVnmMxH1");
        }));
        states.add(new ModsState("shaders tab, Iris loaded, installed shader selected", () -> { }, s -> {
            s.selectTab(ModsTab.SHADERS);
            s.selectHit(hit(s, "complementary-reimagined"));
        }));
        return states;
    }

    /**
     * The Mods screen in every state the detail panel and the list can be in (empty search, failed search, every
     * tab, the restart banner, long titles, installed, disabled, missing and manual items, the shader and resource
     * pack shortcuts) at every window size, at scale 1 and with large text.
     */
    @Test
    void everyModsScreenStateIsClickableAtEveryWindowSize() {
        List<ReachabilityWalker.Failure> failures = new ArrayList<>();
        Map<String, String> summary = new LinkedHashMap<>();
        for (ModsState state : modsStates()) {
            // The service caches searches for five minutes; every state starts from fresh results.
            t.clock.advance(6 * 60_000L);
            state.before().run();
            for (boolean largeText : new boolean[] {false, true}) {
                t.services.settings().set(VantaSettings.ACCESSIBILITY_LARGE_TEXT, largeText);
                for (int[] size : ALL_SIZES) {
                    ModsScreen screen = t.show(ScreenId.MODS, size[0], size[1]);
                    assertEquals(largeText ? 1.15f : 1f, screen.effectiveScale(), 1e-6f);
                    state.after().accept(screen);
                    settle(screen);
                    String sizeName = sizeName(size, largeText, screen) + " [" + state.name() + "]";
                    ReachabilityWalker.Report report = ReachabilityWalker.walk(screen, "MODS", sizeName);
                    summary.put(sizeName, describe(report));
                    failures.addAll(report.failures());
                }
            }
            api.failSearch(null);
        }
        report(summary, failures);
    }
}
