package dev.vanta.core.hud.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.hud.FrameTimeTracker;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.editor.HudEditorSession;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.hud.render.HudRenderer;
import dev.vanta.core.lab.LabFeature;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TestCanvas;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Vanta Lab frame time graph: samples and percentiles from the snapshot, the bars and labels it draws, the
 * honest "n/a" without history, and the gate: drawn, listed and offered only while the Lab feature is on.
 */
class FrametimeGraphWidgetTest {
    private static final FrametimeGraphWidget WIDGET = new FrametimeGraphWidget();
    private static final HudPaint PAINT = HudPaint.defaults();

    @TempDir
    Path dir;

    private static HudWidgetState state(Map<String, String> props) {
        HudWidgetState base = HudWidgetState.defaults("graph", HudWidgetType.FRAMETIME_GRAPH);
        for (Map.Entry<String, String> e : props.entrySet()) {
            base = base.withProp(e.getKey(), e.getValue());
        }
        return base;
    }

    @Test
    void sampleDataDrawsBarsAndPercentileLabels() {
        HudData data = HudData.sample();
        assertEquals(HudWidgetType.FRAMETIME_GRAPH_SAMPLES, data.frameTimeSampleCount());
        assertTrue(data.frameTimeP50() > 5 && data.frameTimeP50() < 9, "p50 of the sample history: " + data.frameTimeP50());
        assertTrue(data.frameTimeP99() > data.frameTimeP50(), "p99 above p50");
        assertEquals(0, data.frameTimeHitches(FrametimeGraphWidget.HITCH_MS), "24 ms spikes are not hitches");
        assertEquals(3, data.frameTimeHitches(20.0), "three spikes above 20 ms in the sample history");
    }

    @Test
    void rendersLabelsBarsAndHitchesInTheDangerColour() {
        FrameTimeTracker tracker = new FrameTimeTracker();
        for (int i = 0; i < 100; i++) {
            tracker.record(i == 50 || i == 51 ? 80.0 : 8.0);
        }
        HudData data = HudData.capture(new FakeGameBridge(), 0, 0, tracker, ZoneOffset.UTC,
                EnumSet.of(HudWidgetType.FRAMETIME_GRAPH));
        assertEquals(100, data.frameTimeSampleCount());
        assertEquals(8.0, data.frameTimeP50(), 1e-9);
        assertEquals(80.0, data.frameTimeP99(), 1e-9, "nearest rank: the 99th of 100 sorted frames is a hitch");
        assertEquals(2, data.frameTimeHitches(FrametimeGraphWidget.HITCH_MS));
        HudWidgetState s = state(Map.of());
        Size size = WIDGET.preferredSize(TestCanvas.metrics(), s, data, PAINT);
        assertEquals(HudWidgetType.FRAMETIME_GRAPH.defaultWidth(), size.w());
        assertEquals(HudWidgetType.FRAMETIME_GRAPH.defaultHeight(), size.h());
        TestCanvas canvas = new TestCanvas(200, 200);
        WIDGET.render(canvas, s, data, PAINT);
        assertTrue(canvas.hasText("p50"));
        assertTrue(canvas.hasText("p99"));
        assertTrue(canvas.hasText("8.0 ms"));
        assertTrue(canvas.hasText("80.0 ms"));
        assertTrue(canvas.fills().stream().anyMatch(f -> f.argb() == PAINT.danger() && f.w() == 1),
                "the hitch bar is drawn in the danger colour");
        long accentBars = canvas.fills().stream().filter(f -> f.argb() == s.accentColor() && f.w() == 1).count();
        assertTrue(accentBars >= 90, "one bar per sample: " + accentBars);
        for (TestCanvas.Fill fill : canvas.fills()) {
            assertTrue(fill.x() >= 0 && fill.y() >= 0 && fill.right() <= s.width() && fill.bottom() <= s.height(),
                    "inside the widget: " + fill);
        }
        TestCanvas noLabels = new TestCanvas(200, 200);
        WIDGET.render(noLabels, state(Map.of("showLabels", "false")), data, PAINT);
        assertFalse(noLabels.hasText("p50"));
        assertFalse(noLabels.fills().isEmpty());
    }

    @Test
    void withoutHistoryItSaysNotAvailable() {
        HudData data = HudData.capture(new FakeGameBridge(), 0, 0, null, ZoneOffset.UTC,
                EnumSet.of(HudWidgetType.FRAMETIME_GRAPH));
        assertEquals(0, data.frameTimeSampleCount());
        assertEquals(0.0, data.frameTimeP50(), 1e-9);
        TestCanvas canvas = new TestCanvas(200, 200);
        WIDGET.render(canvas, state(Map.of()), data, PAINT);
        assertTrue(canvas.hasText("n/a"));
        assertTrue(canvas.hasText("p50"), "labels still name what is missing");
    }

    @Test
    void captureKeepsOnlyTheLastHundredAndTwentyFrames() {
        FrameTimeTracker tracker = new FrameTimeTracker(240);
        for (int i = 0; i < 240; i++) {
            tracker.record(1.0 + i);
        }
        HudData data = HudData.capture(new FakeGameBridge(), 0, 0, tracker, ZoneOffset.UTC,
                EnumSet.of(HudWidgetType.FRAMETIME_GRAPH));
        assertEquals(120, data.frameTimeSampleCount());
        assertEquals(121.0, data.frameTimeSample(0), 1e-9, "oldest kept sample");
        assertEquals(240.0, data.frameTimeSample(119), 1e-9, "newest sample");
        HudData other = HudData.capture(new FakeGameBridge(), 0, 0, tracker, ZoneOffset.UTC,
                EnumSet.of(HudWidgetType.FPS));
        assertEquals(0, other.frameTimeSampleCount(), "captured only for the graph widget");
    }

    @Test
    void theLabFeatureGatesDrawingListingAndTheAssistant() {
        FakeGameBridge game = new FakeGameBridge();
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), MutableClock.standard());
        services.load();
        for (int i = 0; i < 60; i++) {
            services.performance().onFrame(7.0);
        }
        HudLayout layout = services.hud().layout().with(HudWidgetState.defaults("graph", HudWidgetType.FRAMETIME_GRAPH));
        services.hud().setLayout(new HudLayout(List.of(layout.find("graph").orElseThrow())));
        HudRenderer renderer = new HudRenderer(services);
        HudEditorSession session = new HudEditorSession(services, 854, 480);

        assertFalse(services.lab().isEnabled(LabFeature.FRAMETIME_GRAPH));
        assertFalse(renderer.isDrawable(HudWidgetType.FRAMETIME_GRAPH));
        assertFalse(session.isOffered(HudWidgetType.FRAMETIME_GRAPH));
        assertFalse(services.nexusActions().addableWidgetTypes().contains("frametime_graph"));
        assertFalse(services.nexusActions().hudElementIds().contains("frametime_graph"),
                "not offered to the model while the feature is off (the layout instance id is 'graph')");
        renderer.tick();
        TestCanvas off = new TestCanvas(854, 480);
        renderer.render(off, 854, 480, 0f);
        assertTrue(off.ops().isEmpty(), "the widget stays in the layout but is not drawn while the feature is off");
        assertEquals(1, services.hud().layout().size(), "the layout keeps the widget");

        services.lab().set(LabFeature.FRAMETIME_GRAPH, true);
        assertTrue(renderer.isDrawable(HudWidgetType.FRAMETIME_GRAPH));
        assertTrue(session.isOffered(HudWidgetType.FRAMETIME_GRAPH));
        renderer.tick();
        TestCanvas on = new TestCanvas(854, 480);
        renderer.render(on, 854, 480, 0f);
        assertFalse(on.ops().isEmpty());
        assertTrue(on.hasText("7.0 ms"));
        services.hud().setLayout(HudLayout.EMPTY);
        assertTrue(services.nexusActions().addableWidgetTypes().contains("frametime_graph"),
                "offered for adding once the feature is on");
        assertTrue(renderer.isDrawable(HudWidgetType.FPS), "other widgets never depend on the Lab");
    }
}
