package dev.vanta.client.gametest;

import static dev.vanta.client.gametest.VantaClientGameTest.check;
import static dev.vanta.client.gametest.VantaClientGameTest.step;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vanta.client.VantaRuntime;
import dev.vanta.client.bridge.MinecraftGameBridge;
import dev.vanta.client.screen.VantaScreen;
import dev.vanta.client.screen.VantaScreens;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.timer.TimerAlarmScreen;
import dev.vanta.core.screen.timer.TimerScreen;
import dev.vanta.core.timer.TimerCommand;
import dev.vanta.core.timer.TimerFormats;
import dev.vanta.core.timer.TimerReading;
import dev.vanta.core.timer.TimerService;
import dev.vanta.core.timer.TimerState;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.widget.Button;
import java.nio.file.Path;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;

/**
 * The timer in the real game (from client 1.6.0 on; runs inside the test world, no screen open at the start):
 * <ol>
 *   <li>a 2 second timer started through the core service rings on time: after about 3 seconds the "Time is up"
 *       window is open over the game and the client bridge counted at least one alarm sound
 *       ({@link MinecraftGameBridge#alarmSoundsRequested()}) → {@code 70_timer_alarm};</li>
 *   <li>its Stop button (the core widget, the same code path as a mouse click) ends the alarm: the window closes back
 *       to the game, the timer is IDLE and no further alarm sound is requested;</li>
 *   <li>a second alarm, rung while the Timer screen is open, opens over it as its parent and is stopped with the Enter
 *       key through the game's real keyboard input, which returns to the Timer screen; a third one is stopped with
 *       {@code /vanta timer stop}'s core path ({@link TimerCommand#execute}), after which the window closes itself;</li>
 *   <li>the Timer screen opens through the registry, its 10 minute preset starts a labelled timer → {@code 71_timer},
 *       the TIMER HUD widget shows it in the world → {@code 72_timer_hud}, and Cancel ends it.</li>
 * </ol>
 * The timer counts on the wall clock while the game test advances ticks in lock-step with rendering, so every wait
 * here polls the game once per tick against a wall-clock deadline instead of counting ticks.
 */
final class TimerStep {
    /** The first timer: 2 seconds. */
    static final long SHORT_MS = 2_000L;
    /** "Wait about 3 seconds" after the start before the alarm is checked. */
    static final long CHECK_AFTER_MS = 3_000L;
    /** Longest wait for a window to open or close (wall clock). */
    static final long DEADLINE_MS = 15_000L;
    /** How long the sound counter must stay unchanged after Stop. */
    static final long QUIET_MS = 2_500L;
    private static final String LABEL = "Game test";

    private TimerStep() {
    }

    static void run(ClientGameTestContext context, VantaServices services) {
        TimerService timer = services.timer();
        context.setScreen(() -> null);
        context.waitForScreen(null);
        context.runOnClient(client -> timer.cancel());
        check(services.screens().isRegistered(ScreenId.TIMER) && services.screens().isRegistered(ScreenId.TIMER_ALARM),
                "timer: the Timer and alarm screens are not registered");

        // 1. A 2 s timer rings on time and the bell is requested.
        int soundsBefore = sounds(context, services);
        long startedAt = context.computeOnClient(client -> {
            timer.start(SHORT_MS, LABEL);
            return timer.clock().millis();
        });
        boolean early = context.computeOnClient(client -> isAlarm(client) || timer.isRinging());
        check(!early, "timer: rang right after the start of a 2 s timer");
        check(awaitWall(context, SHORT_MS + DEADLINE_MS, TimerStep::isAlarm),
                "timer: the alarm window did not open within " + (SHORT_MS + DEADLINE_MS) + " ms of a 2 s timer ("
                        + describe(context, timer) + ")");
        long openedAfter = context.computeOnClient(client -> timer.clock().millis()) - startedAt;
        check(openedAfter >= SHORT_MS, "timer: the alarm window opened after " + openedAfter + " ms, before the "
                + SHORT_MS + " ms were up");
        awaitWall(context, Math.max(0L, startedAt + CHECK_AFTER_MS - context.computeOnClient(client ->
                timer.clock().millis())), client -> false);
        boolean stillOpen = context.computeOnClient(TimerStep::isAlarm);
        check(stillOpen && context.computeOnClient(client -> timer.isRinging()),
                "timer: about 3 s after the start the alarm should still ring with its window open ("
                        + describe(context, timer) + ")");
        int ringing = sounds(context, services) - soundsBefore;
        check(ringing >= 1, "timer: no alarm sound was requested while the timer rang");
        String shown = context.computeOnClient(client -> {
            TimerAlarmScreen alarm = alarm(client);
            return alarm == null ? "" : alarm.headline().text() + " / " + alarm.label().text() + " / "
                    + alarm.ended().text();
        });
        check(shown.contains(LABEL), "timer: the alarm window does not show the label: " + shown);
        Path shot = context.takeScreenshot("70_timer_alarm");
        step("timer: a 2 s timer rang after " + openedAfter + " ms; window \"" + shown + "\", " + ringing
                + " alarm sound(s) requested -> " + shot.getFileName());

        // 2. Stop closes the window, the timer is idle and the bell stops.
        boolean clicked = context.computeOnClient(client -> {
            TimerAlarmScreen alarm = alarm(client);
            if (alarm == null) {
                return false;
            }
            alarm.stopButton().click(alarm.context());
            return true;
        });
        check(clicked, "timer: the alarm window disappeared before Stop was pressed");
        check(awaitWall(context, DEADLINE_MS, client -> !isAlarm(client)),
                "timer: Stop did not close the alarm window (" + describe(context, timer) + ")");
        String backTo = context.computeOnClient(client -> client.screen == null ? "the game"
                : client.screen.getClass().getSimpleName());
        check(context.computeOnClient(client -> timer.state()) == TimerState.IDLE,
                "timer: after Stop the timer is " + context.computeOnClient(client -> timer.state()));
        int afterStop = sounds(context, services);
        awaitWall(context, QUIET_MS, client -> false);
        int later = sounds(context, services);
        check(later == afterStop, "timer: alarm sounds kept coming after Stop (" + afterStop + " -> " + later + ")");
        step("timer: Stop closed the window back to " + backTo + ", the timer is IDLE, no alarm sound in the "
                + QUIET_MS + " ms after (" + (afterStop - soundsBefore) + " in total)");

        // 3. Over an open VANTA screen: Enter through the real keyboard input stops a second alarm and the window
        //    returns to that screen (its parent).
        context.setScreen(() -> VantaScreens.create(ScreenId.TIMER, null));
        context.waitFor(client -> timerScreen(client) != null);
        context.waitTicks(5);
        ring(context, timer, 1_000L, "");
        boolean overTimer = context.computeOnClient(client -> client.screen instanceof VantaScreen vanta
                && vanta.parent() instanceof VantaScreen parent && parent.screenId() == ScreenId.TIMER);
        check(overTimer, "timer: the alarm window did not open over the Timer screen as its parent ("
                + describe(context, timer) + ")");
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        check(awaitWall(context, DEADLINE_MS, client -> !isAlarm(client)),
                "timer: Enter did not close the alarm window (" + describe(context, timer) + ")");
        check(context.computeOnClient(client -> timer.state()) == TimerState.IDLE, "timer: Enter did not stop the alarm");
        check(awaitWall(context, DEADLINE_MS, client -> timerScreen(client) != null),
                "timer: after Enter the window did not return to the Timer screen (" + describe(context, timer) + ")");
        step("timer: Enter stopped a second alarm opened over the Timer screen and returned to it");
        context.setScreen(() -> null);
        context.waitForScreen(null);

        // 4. /vanta timer stop (its core path) ends a third alarm; the window closes itself.
        ring(context, timer, 1_000L, "Command");
        TimerCommand.Reply reply = context.computeOnClient(client -> TimerCommand.execute("stop", timer,
                TimerFormats.localZone()));
        check(reply.key().equals(TimerCommand.STOPPED), "timer: '/vanta timer stop' answered " + reply);
        check(awaitWall(context, DEADLINE_MS, client -> !isAlarm(client)),
                "timer: the alarm window stayed open after the alarm was stopped another way");
        step("timer: /vanta timer stop ended a third alarm and the window closed itself");

        // 5. The Timer screen: a preset starts a labelled timer, the HUD widget shows it, Cancel ends it.
        context.setScreen(() -> VantaScreens.create(ScreenId.TIMER, null));
        context.waitFor(client -> timerScreen(client) != null);
        context.waitTicks(5);
        boolean preset = context.computeOnClient(client -> {
            TimerScreen screen = timerScreen(client);
            UiNode node = screen == null ? null : screen.root().findById("timer.preset.10");
            if (!(node instanceof Button button)) {
                return false;
            }
            screen.labelField().setText("Tea");
            button.click(screen.context());
            return true;
        });
        check(preset, "timer: the 10 min preset button is missing on the Timer screen");
        context.waitTicks(5);
        TimerReading running = context.computeOnClient(client -> timer.reading());
        check(running.state() == TimerState.RUNNING && running.label().equals("Tea")
                        && timer.durationMillis() == 10 * 60_000L,
                "timer: the 10 min preset did not start a 10 minute 'Tea' timer: " + running);
        Path screenShot = context.takeScreenshot("71_timer");
        String display = context.computeOnClient(client -> {
            TimerScreen screen = timerScreen(client);
            return screen == null ? "" : screen.display().text() + " / " + screen.status().text();
        });
        step("timer: the Timer screen started a 10 min preset (" + display + ") -> " + screenShot.getFileName());

        HudLayout before = context.computeOnClient(client -> services.hud().layout());
        context.setScreen(() -> null);
        context.waitForScreen(null);
        try {
            context.runOnClient(client -> {
                HudLayout layout = services.hud().layout();
                if (layout.byType(HudWidgetType.TIMER).isEmpty()) {
                    layout = layout.with(layout.placedClear(HudWidgetState.defaults(layout.nextId(HudWidgetType.TIMER),
                            HudWidgetType.TIMER), client.getWindow().getGuiScaledWidth(),
                            client.getWindow().getGuiScaledHeight()));
                    services.hud().setLayout(layout);
                }
            });
            context.waitTicks(10);
            TimerReading onHud = context.computeOnClient(client -> VantaRuntime.get().hud().data().timer());
            check(onHud.state() == TimerState.RUNNING && onHud.label().equals("Tea"),
                    "timer: the HUD snapshot does not carry the running timer: " + onHud);
            Path hudShot = context.takeScreenshot("72_timer_hud");
            step("timer: the TIMER widget shows " + onHud.remainingText() + " 'Tea' on the HUD -> "
                    + hudShot.getFileName());
        } finally {
            context.runOnClient(client -> services.hud().setLayout(before));
        }

        context.setScreen(() -> VantaScreens.create(ScreenId.TIMER, null));
        context.waitFor(client -> timerScreen(client) != null);
        context.waitTicks(5);
        boolean cancelled = context.computeOnClient(client -> {
            TimerScreen screen = timerScreen(client);
            if (screen == null || !screen.cancelButton().isEnabled()) {
                return false;
            }
            screen.cancelButton().click(screen.context());
            return true;
        });
        check(cancelled, "timer: Cancel was not available for the running timer");
        context.waitTicks(2);
        check(context.computeOnClient(client -> timer.state()) == TimerState.IDLE, "timer: Cancel left the timer "
                + context.computeOnClient(client -> timer.state()));
        context.setScreen(() -> null);
        context.waitForScreen(null);
        step("timer: Cancel ended the timer; the Timer screen and the alarm work in the real game");
    }

    /** Starts a timer and waits until its alarm window is open. */
    private static void ring(ClientGameTestContext context, TimerService timer, long millis, String label) {
        context.runOnClient(client -> timer.start(millis, label));
        check(awaitWall(context, millis + DEADLINE_MS, TimerStep::isAlarm),
                "timer: the alarm window of a " + millis + " ms timer did not open (" + describe(context, timer) + ")");
        context.waitTicks(2);
    }

    /**
     * Polls {@code done} once per tick until it holds or {@code millis} of wall-clock time passed.
     *
     * @return whether {@code done} held
     */
    private static boolean awaitWall(ClientGameTestContext context, long millis, Predicate<Minecraft> done) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (context.computeOnClient(done::test)) {
                return true;
            }
            context.waitTick();
        }
        return context.computeOnClient(done::test);
    }

    private static int sounds(ClientGameTestContext context, VantaServices services) {
        return context.computeOnClient(client -> services.game() instanceof MinecraftGameBridge bridge
                ? bridge.alarmSoundsRequested() : -1);
    }

    private static boolean isAlarm(Minecraft client) {
        return client.screen instanceof VantaScreen vanta && vanta.screenId() == ScreenId.TIMER_ALARM;
    }

    private static TimerAlarmScreen alarm(Minecraft client) {
        if (client.screen instanceof VantaScreen vanta && vanta.screenId() == ScreenId.TIMER_ALARM) {
            UiScreen ui = vanta.ui();
            return ui instanceof TimerAlarmScreen alarm ? alarm : null;
        }
        return null;
    }

    private static TimerScreen timerScreen(Minecraft client) {
        if (client.screen instanceof VantaScreen vanta && vanta.screenId() == ScreenId.TIMER) {
            UiScreen ui = vanta.ui();
            return ui instanceof TimerScreen screen ? screen : null;
        }
        return null;
    }

    private static String describe(ClientGameTestContext context, TimerService timer) {
        return context.computeOnClient(client -> "timer " + timer.state() + ", " + timer.soundsPlayed()
                + " sound(s) this ring, screen " + (client.screen == null ? "none"
                : client.screen instanceof VantaScreen vanta ? "VantaScreen(" + vanta.screenId().id() + ")"
                : client.screen.getClass().getSimpleName()));
    }
}
