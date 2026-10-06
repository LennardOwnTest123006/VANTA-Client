package dev.vanta.core.crosshair.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.CountingGameBridge;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.KeyStates;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.crosshair.CrosshairGeometry;
import dev.vanta.core.crosshair.CrosshairPreset;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.ui.TestCanvas;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cached per-frame path of {@link CrosshairRenderer} must draw exactly what the stateless
 * {@link CrosshairRenderer#draw(dev.vanta.core.ui.Canvas, CrosshairStyle, int, int, int)} painter draws, for every
 * built-in preset and every expansion step, while reading the game once per tick rather than once per frame.
 */
class CrosshairRendererCacheTest {
    private static final int W = 320;
    private static final int H = 180;

    @TempDir
    Path dir;

    /** Inputs that make a dynamic style aim for exactly {@code expansion} pixels. */
    private static KeyStates keysFor(int expansion) {
        boolean move = expansion >= CrosshairRenderer.MOVE_EXPANSION;
        boolean act = expansion % CrosshairRenderer.MOVE_EXPANSION == CrosshairRenderer.ACTION_EXPANSION;
        return new KeyStates(move, false, false, false, false, false, act, false);
    }

    @Test
    void cachedRenderMatchesTheStatelessPainterForEveryPresetAndExpansion() {
        MutableClock clock = MutableClock.standard();
        FakeGameBridge game = new FakeGameBridge();
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        services.load();
        int comparisons = 0;
        for (CrosshairPreset preset : CrosshairPresets.builtIns()) {
            for (boolean dynamic : new boolean[] {false, true}) {
                CrosshairStyle style = preset.style().withDynamic(dynamic);
                services.crosshair().setStyle(style);
                CrosshairRenderer renderer = new CrosshairRenderer(services);
                for (int expansion = 0; expansion <= 4; expansion++) {
                    // The stateless painter: what the HUD editor preview and the old per-frame path drew.
                    TestCanvas expected = new TestCanvas(W, H);
                    CrosshairRenderer.draw(expected, style, W / 2, H / 2, expansion);
                    if (expansion <= CrosshairRenderer.MAX_EXPANSION) {
                        game.keyStates = keysFor(expansion);
                        renderer.tick();
                        clock.advance(1_000);
                        TestCanvas first = new TestCanvas(W, H);
                        renderer.render(first, W, H, 0f);
                        clock.advance(1_000);
                        TestCanvas second = new TestCanvas(W, H);
                        renderer.render(second, W, H, 0f);
                        int effective = dynamic ? expansion : 0;
                        assertEquals(effective, renderer.currentExpansion(), preset.id() + " settles at " + effective);
                        TestCanvas reference = new TestCanvas(W, H);
                        CrosshairRenderer.draw(reference, style, W / 2, H / 2, effective);
                        assertEquals(reference.fills(), first.fills(), preset.id() + " dynamic=" + dynamic
                                + " expansion=" + expansion + " (first frame builds the geometry)");
                        assertEquals(reference.fills(), second.fills(), preset.id() + " dynamic=" + dynamic
                                + " expansion=" + expansion + " (second frame replays it)");
                    }
                    // The stateless painter itself is unchanged: expansion only matters for dynamic styles.
                    TestCanvas base = new TestCanvas(W, H);
                    CrosshairRenderer.draw(base, style, W / 2, H / 2, 0);
                    if (!dynamic || expansion == 0) {
                        assertEquals(base.fills(), expected.fills());
                    }
                    comparisons++;
                }
            }
        }
        assertEquals(CrosshairPresets.builtIns().size() * 2 * 5, comparisons);
    }

    @Test
    void geometryIsBuiltOncePerExpansionAndInvalidatedWhenTheStyleOrCentreChanges() {
        FakeGameBridge game = new FakeGameBridge();
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), MutableClock.standard());
        services.load();
        CrosshairRenderer renderer = new CrosshairRenderer(services);
        CrosshairStyle style = CrosshairStyle.DEFAULT.withDynamic(true);
        List<CrosshairGeometry.Primitive> zero = renderer.primitives(style, 100, 50, 0);
        assertSame(zero, renderer.primitives(style, 100, 50, 0), "same list while nothing changed");
        List<CrosshairGeometry.Primitive> two = renderer.primitives(style, 100, 50, 2);
        assertNotSame(zero, two);
        assertSame(zero, renderer.primitives(style, 100, 50, 0), "other expansion steps stay cached");
        assertSame(zero, renderer.primitives(style.withDynamic(true), 100, 50, 0), "equal style, same cache");
        assertNotSame(zero, renderer.primitives(style.withSize(7), 100, 50, 0), "style change rebuilds");
        List<CrosshairGeometry.Primitive> moved = renderer.primitives(style, 120, 50, 0);
        assertNotSame(zero, moved, "centre change rebuilds");
        assertEquals(CrosshairGeometry.build(style, 120, 50, 0), moved);
        assertEquals(CrosshairGeometry.build(style, 120, 50, 9), renderer.primitives(style, 120, 50, 9),
                "expansions outside the cached range are built directly");
    }

    @Test
    void inputsAreReadOncePerTickAndTheLayoutIsNotScannedWhenTheCallerPassesTheFlag() {
        MutableClock clock = MutableClock.standard();
        FakeGameBridge fake = new FakeGameBridge();
        CountingGameBridge game = new CountingGameBridge(fake);
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        services.load();
        services.crosshair().setStyle(CrosshairStyle.DEFAULT.withDynamic(true));
        CrosshairRenderer renderer = new CrosshairRenderer(services);
        TestCanvas canvas = new TestCanvas(W, H);
        game.reset();
        renderer.tick();
        assertEquals(1, game.count("keyStates"));
        for (int frame = 0; frame < 60; frame++) {
            clock.advance(16);
            renderer.render(canvas, W, H, 0f, true);
        }
        assertEquals(1, game.count("keyStates"), "60 frames read the inputs zero more times");
        assertEquals(60, game.count("isInWorld"), "in-world check once per frame");
        assertEquals(61, game.total(), "no other bridge calls: " + game.calls());
        assertTrue(canvas.fills().size() > 0);

        services.crosshair().setStyle(CrosshairStyle.DEFAULT);
        game.reset();
        renderer.tick();
        assertEquals(0, game.count("keyStates"), "static styles never read the inputs");
    }
}
