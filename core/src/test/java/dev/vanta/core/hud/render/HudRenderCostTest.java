package dev.vanta.core.hud.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.crosshair.CrosshairPreset;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.crosshair.CrosshairShape;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.crosshair.render.CrosshairRenderer;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudPreset;
import dev.vanta.core.hud.HudPresets;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.i18n.JsonLangProvider;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.i18n.LangProvider;
import dev.vanta.core.modrinth.FakeModPlatform;
import dev.vanta.core.modrinth.FakeModrinthApi;
import dev.vanta.core.modrinth.ModrinthSearchHit;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.notifications.render.NotificationOverlay;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.screen.common.ThemeFactory;
import dev.vanta.core.screen.mods.ModsScreen;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.CanvasText;
import dev.vanta.core.ui.CountingCanvas;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.UiEnvironment;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Documents how many backend primitives one frame of VANTA's own drawing submits (the FPS cost audit baseline).
 * <p>
 * In the Minecraft client every counted {@code fill} is one {@code GuiGraphics.fill} (one coloured-rectangle render
 * state element), every {@code text} one {@code drawString} with a styled {@code Component} and every {@code width}
 * one {@code Font.width} (both memoised per string in the client since the 2026-10 render cost work, so repeated
 * strings cost a map lookup). The numbers printed by these tests are what the core hands to the backend per frame;
 * the assertions pin the optimised state (icons replayed from a rectangle cache, clipped text found by binary search,
 * crosshair geometry cached) with a little headroom so a regression is visible in the test output.
 */
class HudRenderCostTest {
    private static final int SMALL_W = 480;
    private static final int SMALL_H = 270;
    private static final int W = 960;
    private static final int H = 540;

    @TempDir
    Path dir;

    private static void printTable(String title, List<String> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n== ").append(title).append('\n');
        sb.append(CountingCanvas.header()).append('\n');
        for (String row : rows) {
            sb.append(row).append('\n');
        }
        System.out.print(sb);
    }

    // ---- (a) HUD presets ------------------------------------------------------------------------------------------

    private static HudLayout allEnabled(HudLayout layout) {
        List<HudWidgetState> widgets = new ArrayList<>();
        for (HudWidgetState widget : layout.widgets()) {
            widgets.add(widget.withEnabled(true));
        }
        return new HudLayout(widgets);
    }

    /** Counts translation lookups (one per {@code Lang.tr} call) on top of the bundled English strings. */
    private static final class CountingLang implements LangProvider {
        private final LangProvider delegate = JsonLangProvider.builtIn();
        int lookups;

        @Override
        public Optional<String> lookup(String key) {
            lookups++;
            return delegate.lookup(key);
        }
    }

    @Test
    void hudPresetsPerFrame() {
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), new FakeGameBridge(),
                new FakeOptionsBridge(), new FakeKeybindBridge(), new FakeResourcePackBridge(),
                MutableClock.standard());
        services.load();
        CountingLang lang = new CountingLang();
        Lang.install(lang);
        List<String> rows = new ArrayList<>();
        int maxFills = 0;
        int maxTexts = 0;
        int maxWidths = 0;
        int maxLookups = 0;
        try {
            for (HudPreset preset : HudPresets.builtIns()) {
                services.hud().setLayout(allEnabled(preset.layout()));
                HudRenderer renderer = new HudRenderer(services, new SampleGameData());
                renderer.tick();
                for (int[] size : new int[][] {{SMALL_W, SMALL_H}, {W, H}}) {
                    CountingCanvas canvas = new CountingCanvas(size[0], size[1]);
                    int before = lang.lookups;
                    renderer.render(canvas, size[0], size[1], 0f);
                    int lookups = lang.lookups - before;
                    rows.add(canvas.row(String.format(Locale.ROOT, "hud %-12s %4dx%-4d (%d widgets)", preset.id(),
                            size[0], size[1], preset.layout().size())) + String.format(Locale.ROOT, " langTr=%d",
                            lookups));
                    assertTrue(canvas.texts > 0, preset.id() + " draws text");
                    maxFills = Math.max(maxFills, canvas.fills);
                    maxTexts = Math.max(maxTexts, canvas.texts);
                    maxWidths = Math.max(maxWidths, canvas.textWidths);
                    maxLookups = Math.max(maxLookups, lookups);
                }
            }
        } finally {
            Lang.reset();
        }
        printTable("HUD presets, sample data, all widgets enabled (crosshair widget excluded in-game)", rows);
        // Before (2026-10): pvp 152 fills / 32 texts / 92 widths, default 86 / 32 / 98, up to 16 Lang.tr lookups.
        // After: fills and texts unchanged (plain widget fills and labels), widths 62 (pvp) / 70 (default) because
        // clipped labels are now found by binary search. Bounds leave ~10 % headroom.
        assertTrue(maxFills <= 170, "HUD fills per frame: " + maxFills);
        assertTrue(maxTexts <= 36, "HUD texts per frame: " + maxTexts);
        assertTrue(maxWidths <= 80, "HUD width measurements per frame: " + maxWidths);
        assertTrue(maxLookups <= 24, "HUD Lang.tr lookups per frame: " + maxLookups);
    }

    // ---- primitives behind the numbers -----------------------------------------------------------------------------

    @Test
    void iconFillCounts() {
        List<String> rows = new ArrayList<>();
        int worst = 0;
        Icons worstIcon = null;
        int total16 = 0;
        for (Icons icon : Icons.values()) {
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%-14s", icon.name()));
            for (int size : new int[] {9, 11, 14, 16, 22}) {
                CountingCanvas canvas = new CountingCanvas(64, 64);
                icon.draw(canvas, 0, 0, size, 0xFFFFFFFF);
                sb.append(String.format(Locale.ROOT, "  %2dpx=%4d", size, canvas.fills));
                if (size == 16) {
                    total16 += canvas.fills;
                    if (canvas.fills > worst) {
                        worst = canvas.fills;
                        worstIcon = icon;
                    }
                }
            }
            rows.add(sb.toString());
        }
        StringBuilder sb = new StringBuilder("\n== Icons: fills per draw (rectangles replayed from the IconRaster "
                + "cache; the stroke path drew one fill per stroke step)\n");
        for (String row : rows) {
            sb.append(row).append('\n');
        }
        int average16 = total16 / Icons.values().length;
        sb.append(String.format(Locale.ROOT, "average at 16px: %d fills; worst: %s = %d%n", average16, worstIcon,
                worst));
        System.out.print(sb);
        // Before (2026-10): average 19 fills at 16 px, worst STAR = 48. After: average 11, worst ERROR = 21.
        assertTrue(worst <= 26, "worst icon fills at 16px: " + worst);
        assertTrue(average16 <= 13, "average icon fills at 16px: " + average16);
    }

    @Test
    void ellipsizeMeasurementCost() {
        CountingCanvas canvas = new CountingCanvas(64, 64);
        String description = "Sodium is a free and open-source rendering engine replacement for the Minecraft client";
        String fits = CanvasText.ellipsize(description, 2_000, FontKind.UI, canvas);
        int whenFitting = canvas.textWidths;
        canvas.reset();
        String clipped = CanvasText.ellipsize(description, 120, FontKind.UI, canvas);
        int whenClipped = canvas.textWidths;
        System.out.printf(Locale.ROOT, "%n== CanvasText.ellipsize: %d-char string -> fits: %d measurements; "
                + "clipped to %d chars: %d measurements (one Component allocation + Font.width each, per frame)%n",
                description.length(), whenFitting, clipped.length(), whenClipped);
        assertTrue(fits.equals(description));
        assertTrue(whenFitting == 1);
        // Baseline (2026-10): the linear scan from the end cost 65 measurements for this clip; the binary search
        // over code-point prefixes needs the full text, the ellipsis and one measurement per halving step.
        int halvings = 32 - Integer.numberOfLeadingZeros(description.length());
        assertTrue(whenClipped <= 2 + halvings, "logarithmic in the text length: " + whenClipped);
        assertTrue(whenClipped <= 10, "clip measurements per frame: " + whenClipped);
    }

    // ---- (b) crosshair styles --------------------------------------------------------------------------------------

    @Test
    void crosshairStylesPerFrame() {
        List<String> rows = new ArrayList<>();
        int maxFills = 0;
        for (CrosshairPreset preset : CrosshairPresets.builtIns()) {
            CountingCanvas canvas = new CountingCanvas(W, H);
            CrosshairRenderer.draw(canvas, preset.style(), W / 2, H / 2, 0);
            rows.add(canvas.row("crosshair preset " + preset.id()));
            maxFills = Math.max(maxFills, canvas.fills);
        }
        for (CrosshairShape shape : CrosshairShape.values()) {
            for (boolean outline : new boolean[] {false, true}) {
                CrosshairStyle style = CrosshairStyle.DEFAULT.withShape(shape).withOutline(outline, 1)
                        .withSize(shape == CrosshairShape.CIRCLE ? 8 : 5).withThickness(2).withGap(2);
                CountingCanvas canvas = new CountingCanvas(W, H);
                CrosshairRenderer.draw(canvas, style, W / 2, H / 2, 0);
                rows.add(canvas.row("crosshair " + shape.id() + (outline ? " +outline" : "")));
                maxFills = Math.max(maxFills, canvas.fills);
            }
        }
        // Largest legal style: circle at max size/thickness with an outline (scanline rings).
        CrosshairStyle biggest = new CrosshairStyle(CrosshairShape.CIRCLE, CrosshairStyle.MAX_SIZE,
                CrosshairStyle.MAX_THICKNESS, CrosshairStyle.MAX_GAP, true, CrosshairStyle.MAX_OUTLINE, 1.0,
                0xFFFFFFFF, 0xFF000000, false, false);
        CountingCanvas canvas = new CountingCanvas(W, H);
        CrosshairRenderer.draw(canvas, biggest, W / 2, H / 2, 0);
        rows.add(canvas.row("crosshair circle MAX +outline"));
        maxFills = Math.max(maxFills, canvas.fills);
        printTable("Crosshair styles (one frame each)", rows);
        // Rings are drawn as two fills per scanline: radius 48 outline + radius 32 ring = 356 fills for the largest
        // legal style (unchanged by the geometry cache, which saves the per-frame rebuild, not the fills).
        assertTrue(maxFills <= 360, "crosshair fills per frame: " + maxFills);
    }

    // ---- (c) notification toast ------------------------------------------------------------------------------------

    @Test
    void notificationToastPerFrame() {
        MutableClock clock = MutableClock.standard();
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), new FakeGameBridge(),
                new FakeOptionsBridge(), new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        services.load();
        NotificationOverlay overlay = new NotificationOverlay(services);
        services.notifications().post(NotificationKind.SUCCESS, "Profile loaded",
                "Competitive profile applied with 42 settings and 3 keybinds");
        clock.advance(NotificationOverlay.IN_MS + 50);
        List<String> rows = new ArrayList<>();
        CountingCanvas one = new CountingCanvas(W, H);
        overlay.render(one, W, H, 0f);
        rows.add(one.row("toast x1 (settled)"));
        assertTrue(one.texts >= 2, "title and body drawn");

        clock.advance(1_100);
        services.notifications().post(NotificationKind.INFO, "Render distance", "Suggestion: 12 -> 10 chunks");
        clock.advance(1_100);
        services.notifications().post(NotificationKind.WARNING, "Keybind conflict", "2 conflicts");
        clock.advance(1_100);
        services.notifications().post(NotificationKind.ERROR, "Import failed", "The file is not a VANTA profile");
        clock.advance(NotificationOverlay.IN_MS + 50);
        CountingCanvas four = new CountingCanvas(W, H);
        overlay.render(four, W, H, 0f);
        rows.add(four.row("toast x4 (settled)"));

        CountingCanvas none = new CountingCanvas(W, H);
        new NotificationOverlay(VantaServices.create(VantaPaths.inGameDirectory(dir.resolve("empty")),
                new FakeGameBridge(), new FakeOptionsBridge(), new FakeKeybindBridge(), new FakeResourcePackBridge(),
                clock)).render(none, W, H, 0f);
        rows.add(none.row("no toasts (idle overlay)"));
        printTable("Notification overlay", rows);
        // Before (2026-10): 67 fills for one toast, 257 for four (the kind icon was 42 / 154 of them).
        // After: 54 / 210 with the icons replayed as rectangles.
        assertTrue(one.fills <= 60, "one toast fills: " + one.fills);
        assertTrue(four.fills <= 232, "four toasts fills: " + four.fills);
        assertTrue(none.fills == 0 && none.texts == 0, "idle overlay draws nothing");
    }

    // ---- (d) screens -----------------------------------------------------------------------------------------------

    /** Attaches a counting environment, initialises the screen and renders settling frames. */
    private static CountingCanvas showCounting(ScreenTestSupport t, UiScreen screen, int width, int height) {
        CountingCanvas canvas = new CountingCanvas(width, height);
        screen.attach(new UiEnvironment(t.host, ThemeFactory.themeFor(t.services), t.uiClock, canvas.metrics(),
                Lang::tr));
        screen.init(width, height);
        t.uiClock.advance(2_000L);
        screen.render(canvas, -1000, -1000, 0f);
        screen.render(canvas, -1000, -1000, 0f);
        canvas.reset();
        return canvas;
    }

    private static String frame(UiScreen screen, CountingCanvas canvas, String label) {
        canvas.reset();
        screen.render(canvas, -1000, -1000, 0f);
        return canvas.row(label);
    }

    @Test
    void screensPerFrame() {
        ScreenTestSupport t = ScreenTestSupport.create(dir);
        t.game.onTitleScreen();
        FakeModrinthApi api = FakeModrinthApi.standard();
        new FakeModPlatform(dir).installInto(t.services, api);
        List<String> rows = new ArrayList<>();
        int maxFills = 0;
        int maxTexts = 0;
        int maxWidths = 0;

        // Main menu: every background with the default particle layer, then the default background without.
        for (MenuBackground background : MenuBackground.values()) {
            t.services.settings().set(VantaSettings.MENU_BACKGROUND, background);
            t.services.settings().set(VantaSettings.MENU_PARTICLES, MenuParticles.EMBERS);
            UiScreen menu = t.create(ScreenId.MAIN_MENU);
            CountingCanvas canvas = showCounting(t, menu, W, H);
            String row = frame(menu, canvas, "main menu " + background.name().toLowerCase(Locale.ROOT) + " +embers");
            rows.add(row);
            maxFills = Math.max(maxFills, canvas.fills);
            maxTexts = Math.max(maxTexts, canvas.texts);
            maxWidths = Math.max(maxWidths, canvas.textWidths);
        }
        t.services.settings().set(VantaSettings.MENU_BACKGROUND, MenuBackground.VIOLET_HORIZON);
        t.services.settings().set(VantaSettings.MENU_PARTICLES, MenuParticles.NONE);
        UiScreen plain = t.create(ScreenId.MAIN_MENU);
        CountingCanvas plainCanvas = showCounting(t, plain, W, H);
        rows.add(frame(plain, plainCanvas, "main menu violet_horizon (default), no particles"));

        // Settings screen: General category (default) and Video (vanilla-bound rows).
        UiScreen settings = t.create(ScreenId.SETTINGS);
        CountingCanvas settingsCanvas = showCounting(t, settings, W, H);
        rows.add(frame(settings, settingsCanvas, "settings general (steady)"));
        maxFills = Math.max(maxFills, settingsCanvas.fills);
        maxTexts = Math.max(maxTexts, settingsCanvas.texts);
        maxWidths = Math.max(maxWidths, settingsCanvas.textWidths);

        // Mods & Shaders with the fake Modrinth API: results loaded, Sodium selected in the detail panel.
        ModsScreen mods = t.create(ScreenId.MODS);
        CountingCanvas modsCanvas = showCounting(t, mods, W, H);
        rows.add(frame(mods, modsCanvas, "mods (results, nothing selected)"));
        ModrinthSearchHit sodium = mods.results().hits().stream().filter(h -> h.slug().equals("sodium")).findFirst()
                .orElseThrow();
        mods.selectHit(sodium);
        mods.render(modsCanvas, -1000, -1000, 0f);
        rows.add(frame(mods, modsCanvas, "mods (sodium selected, steady)"));
        maxFills = Math.max(maxFills, modsCanvas.fills);
        maxTexts = Math.max(maxTexts, modsCanvas.texts);
        maxWidths = Math.max(maxWidths, modsCanvas.textWidths);

        // Same screens at the small size for comparison (fills scale with window size for composed shapes).
        UiScreen smallMenu = t.create(ScreenId.MAIN_MENU);
        rows.add(frame(smallMenu, showCounting(t, smallMenu, SMALL_W, SMALL_H), "main menu default 480x270"));
        UiScreen smallSettings = t.create(ScreenId.SETTINGS);
        CountingCanvas smallCanvas = showCounting(t, smallSettings, SMALL_W, SMALL_H);
        rows.add(frame(smallSettings, smallCanvas, "settings general 480x270"));
        maxWidths = Math.max(maxWidths, smallCanvas.textWidths);

        printTable("Screens at 960x540 (one steady-state frame, cursor outside the window)", rows);
        // Before (2026-10): graphite-grid menu 6 081 fills (4 638 of them 1x1 grid pixels), violet horizon
        // (default) 2 795, settings 1 640, mods 2 127; 50-71 texts; 73-90 width measurements at 960x540 but 224
        // at 480x270 because clipped descriptions were ellipsised character by character.
        // After: 6 001 / 2 715 / 1 560 / 2 085 fills in core (icons as rectangles; the 960 columns of the horizon
        // line, the 384 vignette strips and the 200 columns of the PLAY button become 3 native gradients in the
        // client only), 110 widths at 480x270 (binary search). The grid pixels and the per-column gradients of the
        // test canvas remain, so the fill bound stays high; texts and widths are pinned tightly.
        assertTrue(maxFills <= 6_600, "screen fills per frame: " + maxFills);
        assertTrue(maxTexts <= 80, "screen texts per frame: " + maxTexts);
        // 1.3.0: the General category gained the "Singleplayer worlds" row (+16 widths at 480x270: 110 -> 126).
        assertTrue(maxWidths <= 135, "screen width measurements per frame: " + maxWidths);
    }

    // ---- diagnostics: Mods & Shaders detail column at small windows ------------------------------------------------

    /**
     * Prints where the Install button of the Mods &amp; Shaders detail panel lands at common window sizes. A node laid
     * out outside its parent's bounds is still rendered but never receives clicks ({@code UiNode.mouseDown} only
     * descends into children whose bounds contain the cursor), so a button below the card or below the screen edge
     * is unreachable. This test only reports; the layout bug itself is tracked separately.
     */
    @Test
    void modsInstallButtonPlacementDiagnostics() {
        StringBuilder sb = new StringBuilder("\n== Mods & Shaders: Install button placement\n");
        for (int[] size : new int[][] {{960, 540}, {854, 480}, {640, 360}, {480, 270}, {427, 240}, {320, 240}}) {
            ScreenTestSupport t = ScreenTestSupport.create(dir.resolve(size[0] + "x" + size[1]));
            t.game.onTitleScreen();
            new FakeModPlatform(dir).installInto(t.services, FakeModrinthApi.standard());
            ModsScreen mods = t.show(ScreenId.MODS, size[0], size[1]);
            ModrinthSearchHit hit = mods.results().hits().get(0);
            mods.selectHit(hit);
            t.frame(mods, -1000, -1000);
            t.frame(mods, -1000, -1000);
            UiNode install = mods.root().findById("mods.action.install");
            UiNode detail = mods.root().findById("mods.detail");
            Rect screenRect = new Rect(0, 0, mods.width(), mods.height());
            if (install == null || detail == null) {
                sb.append(String.format(Locale.ROOT, "%4dx%-4d install=%s detail=%s%n", size[0], size[1], install,
                        detail));
                continue;
            }
            Rect b = install.bounds();
            Rect d = detail.bounds();
            boolean insideCard = d.contains(b.x(), b.y()) && d.contains(b.right() - 1, b.bottom() - 1);
            boolean insideScreen = screenRect.contains(b.x(), b.y())
                    && screenRect.contains(b.right() - 1, b.bottom() - 1);
            UiNode hit1 = mods.root().hitTest(b.centerX(), b.centerY());
            sb.append(String.format(Locale.ROOT,
                    "%4dx%-4d detail=%s install=%s insideCard=%s insideScreen=%s hitTest=%s%n", size[0], size[1], d, b,
                    insideCard, insideScreen, hit1 == null ? "null" : hit1.getClass().getSimpleName()
                            + (hit1.id() != null ? "#" + hit1.id() : "")));
        }
        System.out.print(sb);
    }
}
