package dev.vanta.core.screen.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.vanta.core.screen.ReachabilityWalker;
import dev.vanta.core.screen.ScreenBootstrap;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.screen.nexus.HudDesignerPanel;
import dev.vanta.core.screen.nexus.NexusScreen;
import dev.vanta.core.screen.nexus.NexusSection;
import dev.vanta.core.screen.search.SearchOverlay;
import dev.vanta.core.search.ActionEntry;
import dev.vanta.core.search.GlobalSearchResult;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.timer.TimerService;
import dev.vanta.core.timer.TimerState;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.ScrollIntoView;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.widget.Button;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Timer screen and the "Time is up" alarm window: registration, rendering and reachability at 427x240 and with
 * large text, the presets, Start with the custom fields, Pause/Resume, Cancel, the once-per-second countdown, the
 * alarm's Stop button, Enter and Escape, and the entry points (command palette, Nexus HUD Designer).
 * <p>
 * The services clock starts at 2026-10-04 12:00:00 UTC; the screens show times of day in UTC here.
 */
class TimerScreensTest {
    private static final ZoneId ZONE = ZoneOffset.UTC;
    private static final int[][] SMALL = {{320, 240}, {427, 240}, {480, 270}, {854, 480}};

    @TempDir
    Path dir;

    private ScreenTestSupport t;
    private TimerService timer;

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
        ScreenBootstrap.registerAll(t.services);
        timer = t.services.timer();
    }

    private TimerScreen timerScreen(int w, int h) {
        TimerScreen screen = t.show(new TimerScreen(t.services, t.navigator, () -> ZONE), w, h);
        settle(screen);
        return screen;
    }

    private TimerAlarmScreen alarmScreen(int w, int h) {
        TimerAlarmScreen screen = t.show(new TimerAlarmScreen(t.services, t.navigator, () -> ZONE), w, h);
        // One frame without ticks: the screen closes itself on its first tick when the timer does not ring.
        t.frame(screen, -1000, -1000);
        return screen;
    }

    private static void settle(UiScreen screen) {
        for (int i = 0; i < 3; i++) {
            screen.tick();
        }
        screen.render(new TestCanvas(screen.width(), screen.height()), -1000, -1000, 0f);
        screen.render(new TestCanvas(screen.width(), screen.height()), -1000, -1000, 0f);
    }

    /** A full left click through the screen's input routing (scrolled into view first), then a settle. */
    private void click(UiScreen screen, UiNode node) {
        assertNotNull(node, "node to click");
        ScrollIntoView.reveal(screen.context(), node);
        // Let a smooth scroll finish before the click lands on the node's new position.
        t.advance(screen, 1_000L);
        t.advance(screen, 1_000L);
        ScreenTestSupport.click(screen, node);
        settle(screen);
    }

    /** Rings a 15 minute "Pizza" timer that started at 12:00 (it ends at 12:15 PM). */
    private void ringPizza() {
        timer.startMinutes(15, "Pizza");
        t.clock.advance(15 * 60_000L);
        timer.tick();
        assertTrue(timer.isRinging());
    }

    private static List<ReachabilityWalker.Failure> walk(UiScreen screen, String name) {
        return ReachabilityWalker.walk(screen, screen.getClass().getSimpleName(), name).failures();
    }

    private static void assertNoFailures(List<ReachabilityWalker.Failure> failures) {
        if (!failures.isEmpty()) {
            fail(failures.size() + " unreachable node(s):\n" + String.join("\n",
                    failures.stream().map(Object::toString).toList()));
        }
    }

    // ---- registration ------------------------------------------------------------------------------------------

    @Test
    void bothScreensAreRegisteredFromTheBootstrap() {
        for (ScreenId id : TimerScreens.SCREENS) {
            assertTrue(t.services.screens().isRegistered(id), id.toString());
        }
        assertTrue(t.create(ScreenId.TIMER) instanceof TimerScreen);
        assertTrue(t.create(ScreenId.TIMER_ALARM) instanceof TimerAlarmScreen);
        assertEquals("Timer", t.<TimerScreen>create(ScreenId.TIMER).title());
        assertEquals("Time is up", t.<TimerAlarmScreen>create(ScreenId.TIMER_ALARM).title());
    }

    // ---- the Timer screen --------------------------------------------------------------------------------------

    @Test
    void idleScreenShowsNoTimerAndOnlyTheStartControls() {
        TimerScreen screen = timerScreen(854, 480);
        assertEquals("0:00", screen.display().text());
        assertEquals("No timer is set.", screen.status().text());
        assertFalse(screen.pauseButton().isEnabled(), "nothing to pause");
        assertFalse(screen.cancelButton().isEnabled(), "nothing to cancel");
        assertTrue(screen.startButton().isEnabled());
        assertEquals(TimerService.PRESET_MINUTES.size(), screen.presetButtons().size());
        List<String> labels = screen.presetButtons().stream().map(Button::label).toList();
        assertEquals(List.of("1 min", "5 min", "10 min", "15 min", "30 min", "60 min"), labels);
        assertEquals("10", screen.minutesField().text(), "the custom fields start at 10:00");
        assertEquals("0", screen.secondsField().text());
        TestCanvas canvas = t.frame(screen, -1000, -1000);
        assertTrue(canvas.hasText("0:00"), "the big display is drawn");
        assertTrue(canvas.hasText("Presets"));
        assertTrue(canvas.hasText("Your own time"));
    }

    @Test
    void presetButtonsStartAtOnceWithTheTypedLabel() {
        TimerScreen screen = timerScreen(854, 480);
        screen.labelField().setText("Tea");
        click(screen, screen.root().findById("timer.preset.5"));
        assertEquals(TimerState.RUNNING, timer.state());
        assertEquals(5 * 60_000L, timer.durationMillis());
        assertEquals("Tea", timer.label());
        assertEquals("5:00", screen.display().text());
        assertEquals("Tea: Rings at 12:05 PM", screen.status().text());
        assertTrue(screen.pauseButton().isEnabled());
        assertTrue(screen.cancelButton().isEnabled());

        click(screen, screen.root().findById("timer.preset.60"));
        assertEquals(60 * 60_000L, timer.durationMillis(), "a preset replaces the running timer");
        assertEquals("1:00:00", screen.display().text());
    }

    @Test
    void startUsesTheCustomMinutesSecondsAndLabel() {
        TimerScreen screen = timerScreen(854, 480);
        screen.minutesField().setText("2");
        screen.secondsField().setText("30");
        screen.labelField().setText("  Eggs ");
        click(screen, screen.startButton());
        assertEquals(TimerState.RUNNING, timer.state());
        assertEquals(150_000L, timer.durationMillis());
        assertEquals("Eggs", timer.label(), "the label is trimmed");
        assertEquals("2:30", screen.display().text());
        assertEquals("Eggs: Rings at 12:02 PM", screen.status().text());
        assertFalse(screen.error().isVisible());

        // Enter in a field starts too.
        screen.minutesField().setText("0");
        screen.secondsField().setText("45");
        screen.secondsField().requestFocus(screen.context());
        ScreenTestSupport.key(screen, Keys.ENTER);
        settle(screen);
        assertEquals(45_000L, timer.durationMillis());
        assertEquals("0:45", screen.display().text());
    }

    @Test
    void invalidCustomInputExplainsWhyAndStartsNothing() {
        TimerScreen screen = timerScreen(854, 480);
        screen.minutesField().setText("5");
        screen.secondsField().setText("75");
        click(screen, screen.startButton());
        assertEquals(TimerState.IDLE, timer.state());
        assertTrue(screen.error().isVisible());
        assertEquals("Seconds go from 0 to 59.", screen.error().text());

        screen.minutesField().setText("0");
        assertFalse(screen.error().isVisible(), "typing clears the error");
        screen.secondsField().setText("0");
        click(screen, screen.startButton());
        assertEquals("The timer needs at least 1 second.", screen.error().text());

        screen.minutesField().setText("1441");
        click(screen, screen.startButton());
        assertEquals("The longest timer is 24 hours (1440 minutes).", screen.error().text());
        assertEquals(TimerState.IDLE, timer.state());
    }

    @Test
    void fieldsOnlyAcceptDigits() {
        TimerScreen screen = timerScreen(854, 480);
        screen.minutesField().setText("");
        screen.minutesField().requestFocus(screen.context());
        ScreenTestSupport.type(screen, "1a2-");
        assertEquals("12", screen.minutesField().text());
    }

    @Test
    void theCountdownChangesOncePerSecond() {
        TimerScreen screen = timerScreen(854, 480);
        click(screen, screen.root().findById("timer.preset.10"));
        assertEquals("10:00", screen.display().text());
        t.clock.advance(400L);
        screen.tick();
        assertEquals("10:00", screen.display().text(),
                "partial seconds round up: 599.6 s left still reads 10:00");
        t.clock.advance(600L);
        screen.tick();
        assertEquals("9:59", screen.display().text());
        t.clock.advance(59_000L);
        screen.tick();
        assertEquals("9:00", screen.display().text());
    }

    @Test
    void pauseFreezesTheCountdownAndResumeContinues() {
        TimerScreen screen = timerScreen(854, 480);
        click(screen, screen.root().findById("timer.preset.1"));
        t.clock.advance(20_000L);
        screen.tick();
        assertEquals("0:40", screen.display().text());

        click(screen, screen.pauseButton());
        assertEquals(TimerState.PAUSED, timer.state());
        assertEquals("Resume", screen.pauseButton().label());
        assertEquals("Paused", screen.status().text());
        t.clock.advance(30_000L);
        screen.tick();
        assertEquals("0:40", screen.display().text(), "paused: the time left stays");

        click(screen, screen.pauseButton());
        assertEquals(TimerState.RUNNING, timer.state());
        assertEquals("Pause", screen.pauseButton().label());
        t.clock.advance(10_000L);
        screen.tick();
        assertEquals("0:30", screen.display().text());
    }

    @Test
    void cancelDropsTheTimer() {
        TimerScreen screen = timerScreen(854, 480);
        click(screen, screen.root().findById("timer.preset.15"));
        click(screen, screen.cancelButton());
        assertEquals(TimerState.IDLE, timer.state());
        assertEquals("0:00", screen.display().text());
        assertEquals("No timer is set.", screen.status().text());
        assertFalse(screen.cancelButton().isEnabled());
    }

    @Test
    void whileRingingTheScreenOffersStop() {
        TimerScreen screen = timerScreen(854, 480);
        ringPizza();
        screen.tick();
        assertEquals("Stop", screen.cancelButton().label());
        assertEquals("Pizza: Time is up", screen.status().text());
        assertFalse(screen.pauseButton().isEnabled());
        click(screen, screen.cancelButton());
        assertEquals(TimerState.IDLE, timer.state(), "Stop on the Timer screen ends the alarm");
    }

    @Test
    void timerScreenIsReachableInSmallWindowsAndWithLargeText() {
        List<ReachabilityWalker.Failure> failures = new ArrayList<>();
        for (boolean largeText : new boolean[] {false, true}) {
            t.services.settings().set(VantaSettings.ACCESSIBILITY_LARGE_TEXT, largeText);
            for (boolean running : new boolean[] {false, true}) {
                if (running) {
                    timer.startMinutes(10, "A rather long label for a timer");
                    timer.pause();
                } else {
                    timer.cancel();
                }
                for (int[] size : SMALL) {
                    TimerScreen screen = timerScreen(size[0], size[1]);
                    String name = size[0] + "x" + size[1] + (largeText ? " large text" : "")
                            + (running ? " paused" : " idle");
                    failures.addAll(walk(screen, name));
                    assertTrue(screen.display().bounds().w() > 0, name + ": the display has room");
                }
            }
        }
        t.services.settings().set(VantaSettings.ACCESSIBILITY_LARGE_TEXT, false);
        assertNoFailures(failures);
    }

    // ---- the alarm window --------------------------------------------------------------------------------------

    @Test
    void alarmShowsTheLabelTheEndTimeAndALargeStopButton() {
        ringPizza();
        TimerAlarmScreen screen = alarmScreen(854, 480);
        assertEquals("Time is up", screen.headline().text());
        assertEquals("Pizza", screen.label().text());
        assertTrue(screen.label().isVisible());
        assertEquals("Ended at 12:15 PM", screen.ended().text(), "12-hour time with AM/PM in the screen's zone");
        assertEquals(TimerAlarmScreen.STOP_WIDTH, screen.stopButton().bounds().w());
        assertEquals(TimerAlarmScreen.STOP_HEIGHT, screen.stopButton().bounds().h());
        assertTrue(screen.stopButton().isFocused(), "Stop has the keyboard focus");
        TestCanvas canvas = t.frame(screen, -1000, -1000);
        assertTrue(canvas.hasText("Time is up"));
        assertTrue(canvas.hasText("Pizza"));
        assertTrue(canvas.hasText("Ended at 12:15 PM"));
        assertTrue(canvas.hasText("Stop"));
    }

    @Test
    void alarmEndTimeFollowsTheZoneItIsGiven() {
        ringPizza();
        TimerAlarmScreen screen = t.show(new TimerAlarmScreen(t.services, t.navigator,
                () -> ZoneId.of("Europe/Berlin")), 854, 480);
        assertEquals("Ended at 2:15 PM", screen.ended().text(), "12:15 UTC is 2:15 PM in Berlin summer time");
    }

    @Test
    void alarmWithoutALabelHidesTheLabelLine() {
        timer.start(2_000L, "");
        t.clock.advance(2_000L);
        timer.tick();
        TimerAlarmScreen screen = alarmScreen(854, 480);
        assertFalse(screen.label().isVisible());
        assertEquals("Ended at 12:00 PM", screen.ended().text());
    }

    @Test
    void stopEndsTheAlarmAndClosesTheWindow() {
        ringPizza();
        TimerAlarmScreen screen = alarmScreen(854, 480);
        click(screen, screen.stopButton());
        assertEquals(TimerState.IDLE, timer.state());
        assertTrue(screen.isClosing(), "Stop closes the window");
        t.advance(screen, UiScreen.TRANSITION_MS + 1);
        assertTrue(t.host.closed(), "the host returns to the parent screen or the game");
        assertEquals("Pizza", timer.lastLabel(), "the stopped alarm is remembered");
    }

    @Test
    void enterKeypadEnterAndEscapeStopTheAlarmToo() {
        for (int key : new int[] {Keys.ENTER, Keys.KP_ENTER, Keys.ESCAPE}) {
            ringPizza();
            TimerAlarmScreen screen = alarmScreen(854, 480);
            assertTrue(ScreenTestSupport.key(screen, key), "key " + key + " is handled");
            assertEquals(TimerState.IDLE, timer.state(), "key " + key + " stops the alarm");
            assertTrue(screen.isClosing());
        }
    }

    @Test
    void escapeStopsEvenWhenTheButtonIsNotFocused() {
        ringPizza();
        TimerAlarmScreen screen = alarmScreen(854, 480);
        screen.context().focus().clear(screen.context());
        assertTrue(ScreenTestSupport.key(screen, Keys.ESCAPE));
        assertEquals(TimerState.IDLE, timer.state());
    }

    @Test
    void theSearchShortcutDoesNotLeaveTheRingingAlarm() {
        ringPizza();
        TimerAlarmScreen screen = alarmScreen(854, 480);
        ScreenTestSupport.key(screen, Keys.K, Keys.MOD_CONTROL);
        assertFalse(t.host.events().contains("openScreen:SEARCH"));
        assertTrue(timer.isRinging(), "only Stop, Enter and Escape end the alarm");
        assertFalse(screen.isClosing());
    }

    @Test
    void alarmClosesItselfWhenTheTimerStopsAnotherWay() {
        ringPizza();
        TimerAlarmScreen screen = alarmScreen(854, 480);
        screen.tick();
        assertFalse(screen.isClosing(), "still ringing: the window stays");
        timer.cancel();
        screen.tick();
        assertTrue(screen.isClosing(), "Cancel elsewhere (or /vanta timer stop) closes the window");
    }

    @Test
    void alarmShownWithoutARingingTimerClosesOnItsFirstTick() {
        TimerAlarmScreen screen = alarmScreen(854, 480);
        assertFalse(screen.ended().isVisible(), "no alarm rang yet");
        screen.tick();
        assertTrue(screen.isClosing());
    }

    @Test
    void alarmIsReachableInSmallWindowsAndWithLargeText() {
        List<ReachabilityWalker.Failure> failures = new ArrayList<>();
        for (boolean largeText : new boolean[] {false, true}) {
            t.services.settings().set(VantaSettings.ACCESSIBILITY_LARGE_TEXT, largeText);
            timer.cancel();
            timer.startMinutes(15, "A rather long label for a timer");
            t.clock.advance(15 * 60_000L);
            timer.tick();
            for (int[] size : SMALL) {
                TimerAlarmScreen screen = alarmScreen(size[0], size[1]);
                String name = size[0] + "x" + size[1] + (largeText ? " large text" : "");
                failures.addAll(walk(screen, name));
                assertTrue(screen.stopButton().bounds().bottom() <= screen.height(), name + ": Stop fits");
                assertTrue(screen.headline().bounds().y() >= 0, name + ": the headline fits");
            }
        }
        t.services.settings().set(VantaSettings.ACCESSIBILITY_LARGE_TEXT, false);
        assertNoFailures(failures);
    }

    // ---- entry points ------------------------------------------------------------------------------------------

    @Test
    void theCommandPaletteFindsAndOpensTheTimer() {
        ActionEntry entry = ActionEntry.builtIns().stream().filter(a -> a.id().equals(ActionEntry.OPEN_TIMER))
                .findFirst().orElseThrow();
        assertEquals(Optional.of(ScreenId.TIMER), entry.screen());
        for (String keyword : List.of("timer", "alarm", "countdown", "minutes", "wecker", "stoppuhr")) {
            assertTrue(entry.keywords().contains(keyword) || "timer".equals(keyword), keyword);
            assertTrue(t.services.search().search(keyword, 10).stream()
                    .anyMatch(h -> h.toString().contains(ActionEntry.OPEN_TIMER)), "search finds it by " + keyword);
        }
        SearchOverlay overlay = t.show(ScreenId.SEARCH, 854, 480);
        overlay.field().setText("wecker");
        GlobalSearchResult hit = overlay.results().stream()
                .filter(r -> r instanceof GlobalSearchResult.ActionResult a
                        && a.action().id().equals(ActionEntry.OPEN_TIMER))
                .findFirst().orElseThrow();
        assertEquals("Open timer", hit.title());
        overlay.activate(hit);
        assertTrue(t.host.events().contains("openScreen:TIMER"));
    }

    @Test
    void theHudDesignerOfNexusOpensTheTimer() {
        NexusScreen nexus = t.show(ScreenId.NEXUS, 854, 480);
        settle(nexus);
        nexus.select(NexusSection.HUD_DESIGNER);
        settle(nexus);
        HudDesignerPanel panel = nexus.panel(NexusSection.HUD_DESIGNER);
        assertSame(panel.openTimerButton(), nexus.root().findById("nexus.hud.openTimer"));
        assertEquals("Open timer", panel.openTimerButton().label());
        click(nexus, panel.openTimerButton());
        assertTrue(t.host.events().contains("openScreen:TIMER"));
    }
}
