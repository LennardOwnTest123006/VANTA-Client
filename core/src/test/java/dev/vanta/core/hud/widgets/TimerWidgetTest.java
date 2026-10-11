package dev.vanta.core.hud.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ai.NexusHudPreset;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.hud.HudAnchor;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudPreset;
import dev.vanta.core.hud.HudPresets;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.editor.HudEditorSession;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.hud.render.HudRenderer;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.timer.TimerReading;
import dev.vanta.core.timer.TimerState;
import dev.vanta.core.ui.TestCanvas;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The TIMER HUD widget (from client 1.6.0 on): the label and the time left while a timer runs, "Paused" next to a
 * paused timer, "Time is up" in the accent colour while it rings, nothing at all while no timer is set, the 9:59
 * sample in the HUD editor, and never part of a built-in layout.
 */
class TimerWidgetTest {
    private static final TimerWidget WIDGET = new TimerWidget();
    private static final HudPaint PAINT = HudPaint.defaults();

    @TempDir
    Path dir;

    private static HudWidgetState state() {
        return HudWidgetState.defaults("timer", HudWidgetType.TIMER);
    }

    /** A live snapshot (not sample data) carrying the reading. */
    private static HudData live(TimerReading reading) {
        HudData data = HudData.capture(new FakeGameBridge(), 0, 0, null, ZoneOffset.UTC,
                EnumSet.of(HudWidgetType.TIMER));
        return data.withTimer(reading);
    }

    private static TestCanvas draw(HudWidgetState state, HudData data) {
        TestCanvas canvas = new TestCanvas(200, 50);
        WIDGET.render(canvas, state, data, PAINT);
        return canvas;
    }

    @Test
    void theTypeIsMovableTopRightAndShowsTheLabelByDefault() {
        HudWidgetType type = HudWidgetType.TIMER;
        assertEquals("timer", type.id());
        assertEquals(HudAnchor.TOP_RIGHT, type.defaultAnchor());
        assertTrue(state().propBool("showLabel"));
        assertSame(HudWidgetType.TIMER, WIDGET.type());
        assertTrue(HudWidgetRenderers.standard().forType(HudWidgetType.TIMER) instanceof TimerWidget);
    }

    @Test
    void idleDrawsNothingAtAll() {
        TestCanvas canvas = draw(state(), live(TimerReading.IDLE));
        assertTrue(canvas.fills().isEmpty(), "no panel while no timer is set");
        assertTrue(canvas.texts().isEmpty(), "no text while no timer is set");
        HudData unattached = HudData.capture(new FakeGameBridge(), 0, 0, null, ZoneOffset.UTC,
                EnumSet.of(HudWidgetType.TIMER));
        assertEquals(TimerReading.IDLE, unattached.timer(), "live data without a reading reads as idle");
        assertTrue(draw(state(), unattached).texts().isEmpty());
    }

    @Test
    void runningShowsTheLabelAndTheTimeLeft() {
        TestCanvas canvas = draw(state(), live(new TimerReading(TimerState.RUNNING, 125_400L, "Tea")));
        assertTrue(canvas.hasText("Tea"));
        assertTrue(canvas.hasText("2:06"), "partial seconds round up");
        assertFalse(canvas.fills().isEmpty(), "the panel is drawn");
        assertTrue(canvas.texts().stream().anyMatch(t -> t.text().equals("2:06") && t.argb() == state().textColor()));

        TestCanvas unnamed = draw(state(), live(new TimerReading(TimerState.RUNNING, 3_600_000L, "")));
        assertTrue(unnamed.hasText("Timer"), "without a label the widget says Timer");
        assertTrue(unnamed.hasText("1:00:00"), "h:mm:ss from one hour on");

        HudWidgetState noLabel = state().withProp("showLabel", "false");
        TestCanvas bare = draw(noLabel, live(new TimerReading(TimerState.RUNNING, 59_000L, "Tea")));
        assertFalse(bare.hasText("Tea"));
        assertTrue(bare.hasText("0:59"));
    }

    @Test
    void pausedSaysSoInTheMutedColour() {
        HudWidgetState s = state();
        TestCanvas canvas = draw(s, live(new TimerReading(TimerState.PAUSED, 40_000L, "Eggs")));
        assertTrue(canvas.hasText("0:40"));
        assertTrue(canvas.hasText("Paused"));
        assertTrue(canvas.texts().stream().anyMatch(t -> t.text().equals("0:40") && t.argb() == PAINT.muted(s)),
                "the frozen time is muted");
    }

    @Test
    void ringingShowsTimeIsUpInTheAccentColour() {
        HudWidgetState s = state().withTextColor(0xFFFFFFFF);
        TestCanvas canvas = draw(s, live(new TimerReading(TimerState.RINGING, 0L, "Pizza")));
        assertTrue(canvas.hasText("Pizza"));
        assertTrue(canvas.texts().stream().anyMatch(t -> t.text().equals("Time is up")
                && t.argb() == s.accentColor()), "accent colour while ringing: " + canvas.texts());
    }

    @Test
    void theSampleReadsNineFiftyNineAndIsMarkedAsAPreview() {
        HudData sample = HudData.sample();
        assertEquals(TimerReading.SAMPLE, sample.timer(), "sample data without a reading shows the sample");
        TestCanvas canvas = draw(state(), sample);
        assertTrue(canvas.hasText("9:59"));
        assertTrue(canvas.hasText("Preview"));
        assertTrue(TimerReading.SAMPLE.sample());
        assertFalse(new TimerReading(TimerState.RUNNING, 1L, "").sample(), "real readings are never samples");
    }

    @Test
    void aReadingIsAttachedOnceAndNeverToTheEmptySnapshot() {
        HudData data = live(TimerReading.IDLE);
        assertThrows(IllegalStateException.class, () -> data.withTimer(TimerReading.IDLE));
        assertThrows(IllegalStateException.class, () -> HudData.EMPTY.withTimer(TimerReading.IDLE));
    }

    @Test
    void theLiveHudDrawsTheTimerOnlyWhileOneIsSet() {
        MutableClock clock = MutableClock.standard();
        FakeGameBridge game = new FakeGameBridge();
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        services.load();
        services.hud().setLayout(new HudLayout(List.of(HudWidgetState.defaults("timer", HudWidgetType.TIMER))));
        HudRenderer renderer = new HudRenderer(services);

        renderer.tick();
        TestCanvas idle = new TestCanvas(854, 480);
        renderer.render(idle, 854, 480, 0f);
        assertTrue(idle.texts().isEmpty() && idle.fills().isEmpty(), "idle in the game: nothing drawn");

        services.timer().startMinutes(10, "Tea");
        clock.advance(1_000L);
        renderer.tick();
        TestCanvas running = new TestCanvas(854, 480);
        renderer.render(running, 854, 480, 0f);
        assertTrue(running.hasText("Tea"));
        assertTrue(running.hasText("9:59"));

        clock.advance(10 * 60_000L);
        services.tick();
        assertTrue(services.timer().isRinging());
        renderer.tick();
        TestCanvas ringing = new TestCanvas(854, 480);
        renderer.render(ringing, 854, 480, 0f);
        assertTrue(ringing.hasText("Time is up"));

        services.timer().stop();
        renderer.tick();
        TestCanvas stopped = new TestCanvas(854, 480);
        renderer.render(stopped, 854, 480, 0f);
        assertTrue(stopped.texts().isEmpty(), "stopped: the widget disappears again");
    }

    @Test
    void theHudEditorShowsTheSampleWhileNoTimerRuns() {
        FakeGameBridge game = new FakeGameBridge();
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), MutableClock.standard());
        services.load();
        assertTrue(game.isInWorld(), "the editor previews live game data in a world");
        HudEditorSession session = new HudEditorSession(services, 854, 480);
        session.tick();
        assertEquals(TimerReading.SAMPLE, session.renderer().data().timer(), "9:59 so the widget can be placed");
        assertTrue(session.isOffered(HudWidgetType.TIMER), "listed in the editor's widget list");

        services.timer().startMinutes(5, "Tea");
        session.tick();
        assertEquals("Tea", session.renderer().data().timer().label(), "a running timer shows itself instead");
        assertFalse(session.renderer().data().timer().sample());

        HudRenderer game2 = new HudRenderer(services);
        services.timer().cancel();
        assertEquals(TimerReading.IDLE, game2.timerReading(), "the in-game renderer never shows the sample");
    }

    @Test
    void noBuiltInLayoutContainsTheTimer() {
        for (HudPreset preset : HudPresets.builtIns()) {
            assertTrue(preset.layout().byType(HudWidgetType.TIMER).isEmpty(), preset.id());
        }
        for (NexusHudPreset preset : NexusHudPreset.values()) {
            if (preset != NexusHudPreset.FULL) {
                assertFalse(preset.widgets().contains(HudWidgetType.TIMER), preset.id());
            }
        }
        assertTrue(NexusHudPreset.FULL.widgets().contains(HudWidgetType.TIMER),
                "\"every widget\" includes the timer, which stays hidden until a timer is set");
    }
}
