package dev.vanta.core.hud.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.CountingGameBridge;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudPreset;
import dev.vanta.core.hud.HudPresets;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.ui.TestCanvas;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link HudData#capture(dev.vanta.core.bridge.GameBridge, int, int, dev.vanta.core.hud.FrameTimeTracker,
 * java.time.ZoneId, Set)} must call only the bridge methods the enabled widgets display, and the renderer must
 * hand it the live layout's enabled types.
 */
class HudDataCaptureTest {
    @TempDir
    Path dir;

    @Test
    void nothingEnabledReadsOnlyTheWorldFlag() {
        CountingGameBridge game = new CountingGameBridge(new FakeGameBridge());
        HudData data = HudData.capture(game, 0, 0, null, ZoneOffset.UTC, EnumSet.noneOf(HudWidgetType.class));
        assertEquals(1, game.count("isInWorld"));
        assertEquals(1, game.total(), game.calls().toString());
        assertTrue(data.inWorld());
        assertEquals(0, data.fps());
        assertTrue(data.position().isEmpty());
        assertTrue(data.effects().isEmpty());
    }

    @Test
    void everyWidgetTypeReadsItsOwnValuesOnly() {
        for (HudWidgetType type : HudWidgetType.values()) {
            CountingGameBridge game = new CountingGameBridge(new FakeGameBridge());
            HudData.capture(game, 0, 0, null, ZoneOffset.UTC, EnumSet.of(type));
            int others = game.total() - game.count("isInWorld");
            int expected = switch (type) {
                case FPS, PING, COORDINATES, CLOCK, ARMOR -> 2;
                case SERVER, MEMORY, MINECRAFT_VERSION -> 3;
                case CPS, CROSSHAIR, FRAMETIME_GRAPH -> 0;
                default -> 1;
            };
            assertEquals(expected, others, type + " reads " + game.calls());
        }
    }

    @Test
    void allTypesMatchTheCompleteCapture() {
        FakeGameBridge fake = new FakeGameBridge().onServer("play.example.net", "Example", 42);
        CountingGameBridge game = new CountingGameBridge(fake);
        HudData complete = HudData.capture(game, 3, 1, null, ZoneOffset.UTC);
        int completeCalls = game.total();
        game.reset();
        HudData all = HudData.capture(game, 3, 1, null, ZoneOffset.UTC, EnumSet.allOf(HudWidgetType.class));
        assertEquals(completeCalls, game.total(), "the explicit all-types capture reads the same values");
        assertEquals(complete.fps(), all.fps());
        assertEquals(complete.pingMillis(), all.pingMillis());
        assertEquals(complete.position(), all.position());
        assertEquals(complete.biomeName(), all.biomeName());
        assertEquals(complete.serverAddress(), all.serverAddress());
        assertEquals(complete.armorPieces(), all.armorPieces());
        assertEquals(complete.heldItems(), all.heldItems());
        assertEquals(complete.effects(), all.effects());
        assertEquals(complete.keys(), all.keys());
        assertEquals(complete.memoryUsed(), all.memoryUsed());
        assertEquals(complete.cpuLoad(), all.cpuLoad());
        assertEquals(complete.entityCount(), all.entityCount());
        assertEquals(complete.minecraftVersion(), all.minecraftVersion());
        assertEquals(complete.clock12(true), all.clock12(true));
        assertEquals(complete.gameClock(), all.gameClock());
    }

    @Test
    void rendererTicksWithTheLiveLayoutAndRendersIdentically() {
        MutableClock clock = MutableClock.standard();
        FakeGameBridge fake = new FakeGameBridge();
        CountingGameBridge game = new CountingGameBridge(fake);
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        services.load();
        StringBuilder report = new StringBuilder("\n== HudData.capture: bridge calls per tick\n");
        for (HudPreset preset : HudPresets.builtIns()) {
            services.hud().setLayout(preset.layout());
            HudRenderer renderer = new HudRenderer(services);
            game.reset();
            renderer.tick();
            int perTick = game.total();
            Set<HudWidgetType> enabled = HudRenderer.enabledTypes(preset.layout());
            report.append(String.format(Locale.ROOT, "%-12s %2d enabled widgets -> %2d bridge calls per tick%n",
                    preset.id(), enabled.size(), perTick));
            assertTrue(perTick <= 1 + 3 * enabled.size(), preset.id() + ": " + game.calls());

            // Same pixels as a renderer fed by the complete snapshot.
            TestCanvas lean = new TestCanvas(854, 480);
            renderer.render(lean, 854, 480, 0f);
            HudRenderer full = new HudRenderer(services);
            full.tick(EnumSet.allOf(HudWidgetType.class));
            TestCanvas complete = new TestCanvas(854, 480);
            full.render(complete, 854, 480, 0f);
            assertEquals(complete.ops(), lean.ops(), preset.id() + " renders identically from the lean snapshot");
        }
        game.reset();
        services.hud().setLayout(new HudLayout(List.of(HudWidgetState.defaults("fps", HudWidgetType.FPS))));
        HudRenderer single = new HudRenderer(services);
        single.tick();
        report.append(String.format(Locale.ROOT, "%-12s %2d enabled widgets -> %2d bridge calls per tick%n",
                "fps only", 1, game.total()));
        assertEquals(3, game.total(), "fps widget: isInWorld + fps + frameTimeMillis: " + game.calls());
        System.out.print(report);
    }

    @Test
    void everyWidgetAloneRendersIdenticallyFromTheLeanSnapshot() {
        MutableClock clock = MutableClock.standard();
        FakeGameBridge fake = new FakeGameBridge().onServer("play.example.net", "Example", 42);
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), fake, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        services.load();
        // The frame time graph is a Vanta Lab widget: it only draws while its feature is on.
        services.lab().set(dev.vanta.core.lab.LabFeature.FRAMETIME_GRAPH, true);
        for (HudWidgetType type : HudWidgetType.values()) {
            services.hud().setLayout(new HudLayout(List.of(HudWidgetState.defaults("only", type))));
            HudRenderer lean = new HudRenderer(services);
            lean.tick();
            TestCanvas leanCanvas = new TestCanvas(854, 480);
            lean.render(leanCanvas, 854, 480, 0f);
            HudRenderer full = new HudRenderer(services);
            full.tick(EnumSet.allOf(HudWidgetType.class));
            TestCanvas fullCanvas = new TestCanvas(854, 480);
            full.render(fullCanvas, 854, 480, 0f);
            assertEquals(fullCanvas.ops(), leanCanvas.ops(), type + " alone renders identically from the lean data");
            if (type != HudWidgetType.CROSSHAIR) {
                assertFalse(leanCanvas.ops().isEmpty(), type + " draws something");
            }
        }
    }
}
