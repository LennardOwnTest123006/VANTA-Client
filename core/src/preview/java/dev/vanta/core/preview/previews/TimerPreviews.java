package dev.vanta.core.preview.previews;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.preview.PreviewProvider;
import dev.vanta.core.preview.ScreenPreview;
import dev.vanta.core.preview.ScreenPreviews;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.timer.TimerAlarmScreen;
import dev.vanta.core.screen.timer.TimerScreen;
import dev.vanta.core.screen.timer.TimerScreens;
import dev.vanta.core.ui.UiScreen;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Previews of the timer screens: the Timer screen idle, running with a label, paused, with an input error and at
 * 427x240, and the "Time is up" alarm window at the desktop size and at 427x240. Times of day are shown in UTC so the
 * images stay the same on every machine (the game uses the PC's time zone).
 */
public final class TimerPreviews implements PreviewProvider {
    private static final int DESKTOP_W = 854;
    private static final int DESKTOP_H = 480;
    private static final int SMALL_W = 427;
    private static final int SMALL_H = 240;
    private static final ZoneId ZONE = ZoneOffset.UTC;

    /** Public no-arg constructor for {@link java.util.ServiceLoader}. */
    public TimerPreviews() {
    }

    private static ScreenPreview.Options desktop() {
        return ScreenPreview.Options.standard().size(DESKTOP_W, DESKTOP_H).scale(2).lang(Lang::tr);
    }

    private static ScreenPreview.Options small() {
        return ScreenPreview.Options.standard().size(SMALL_W, SMALL_H).scale(2).lang(Lang::tr);
    }

    private static PreviewServices services(Consumer<PreviewServices> setup) {
        PreviewServices p = PreviewServices.create();
        TimerScreens.register(p.services);
        setup.accept(p);
        return p;
    }

    private static UiScreen timer(PreviewServices p) {
        VantaServices services = p.services;
        return new TimerScreen(services, ScreenNavigator.forServices(services), () -> ZONE);
    }

    private static UiScreen alarm(PreviewServices p) {
        VantaServices services = p.services;
        return new TimerAlarmScreen(services, ScreenNavigator.forServices(services), () -> ZONE);
    }

    /** A 10 minute "Tea" timer, 25 seconds in. */
    private static void running(PreviewServices p) {
        p.services.timer().startMinutes(10, "Tea");
        p.clock.advance(25_000L);
    }

    /** A 15 minute "Pizza" timer whose time is up. */
    private static void ringing(PreviewServices p) {
        p.services.timer().startMinutes(15, "Pizza");
        p.clock.advance(15 * 60_000L);
        p.services.timer().tick();
    }

    @Override
    public List<ScreenPreviews.Entry> previews() {
        List<ScreenPreviews.Entry> out = new ArrayList<>();
        out.add(ScreenPreviews.Entry.of("timer", () -> timer(services(p -> { })), desktop()));
        out.add(ScreenPreviews.Entry.of("timer-running", () -> timer(services(TimerPreviews::running)), desktop()));
        out.add(ScreenPreviews.Entry.of("timer-paused", () -> timer(services(p -> {
            running(p);
            p.services.timer().pause();
        })), desktop()));
        out.add(ScreenPreviews.Entry.of("timer-error", () -> {
            TimerScreen screen = (TimerScreen) timer(services(p -> { }));
            screen.onceInitialised(() -> {
                screen.minutesField().setText("5");
                screen.secondsField().setText("75");
                screen.startButton().click(screen.context());
            });
            return screen;
        }, desktop()));
        out.add(ScreenPreviews.Entry.of("timer-small", () -> timer(services(TimerPreviews::running)), small()));
        out.add(ScreenPreviews.Entry.of("timer-alarm", () -> alarm(services(TimerPreviews::ringing)), desktop()));
        out.add(ScreenPreviews.Entry.of("timer-alarm-small", () -> alarm(services(TimerPreviews::ringing)),
                small()));
        return out;
    }
}
