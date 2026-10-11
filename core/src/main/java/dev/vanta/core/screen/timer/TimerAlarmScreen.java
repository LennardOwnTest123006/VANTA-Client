package dev.vanta.core.screen.timer;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.VantaUiScreen;
import dev.vanta.core.timer.TimerFormats;
import dev.vanta.core.timer.TimerService;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.layout.Align;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Justify;
import dev.vanta.core.ui.layout.Panel;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Label;
import java.time.ZoneId;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * "Time is up": the window the client opens over whatever is showing while the timer rings. It shows the label, the
 * time of day the timer ended (12-hour time with AM/PM in the PC's time zone) and one large Stop button; Enter and
 * Escape stop the alarm too. Stop calls {@link TimerService#stop()} and closes the screen, so the host returns to the
 * screen that was open before (or to the game). When the alarm ends another way (the Cancel button of the Timer
 * screen, {@code /vanta timer stop}, a new timer) the screen closes itself on its next tick.
 * <p>
 * The client opens it with {@code TimerService.setAlarmWindow}; shown while the timer does not ring (previews, tests)
 * it describes the last alarm and closes on its first tick.
 */
public final class TimerAlarmScreen extends VantaUiScreen {
    /** Width of the Stop button in logical pixels. */
    public static final int STOP_WIDTH = 160;
    /** Height of the Stop button in logical pixels. */
    public static final int STOP_HEIGHT = 32;
    /** Width of the card in logical pixels (fits the 320 px minimum with the screen padding). */
    public static final int CARD_WIDTH = 300;
    /** Largest scale of the "Time is up" headline. */
    static final float TITLE_SCALE = 3f;

    private final TimerService timer;
    private final Supplier<ZoneId> zone;
    private BigText headline;
    private Label label;
    private Label ended;
    private Button stopButton;
    private boolean stopped;

    public TimerAlarmScreen(VantaServices services, ScreenNavigator navigator) {
        this(services, navigator, TimerFormats::localZone);
    }

    /**
     * @param zone the time zone the end time is shown in (the PC's zone in the game, a fixed one in tests)
     */
    public TimerAlarmScreen(VantaServices services, ScreenNavigator navigator, Supplier<ZoneId> zone) {
        super(services, navigator, ScreenId.TIMER_ALARM);
        this.timer = services.timer();
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    @Override
    protected UiNode build(UiContext ctx) {
        Theme theme = ctx.theme();
        Column content = new Column(Theme.SPACE_3).align(Align.CENTER);
        headline = new BigText(Lang.tr("vanta.timer.alarm.title"), FontKind.DISPLAY, TITLE_SCALE);
        headline.setId("timer.alarm.title");
        headline.color(theme.accent());
        content.add(headline);
        label = new Label("", Label.Variant.TITLE).wrap(true).align(Label.HAlign.CENTER);
        label.setId("timer.alarm.label");
        content.add(label);
        ended = new Label("", Label.Variant.CAPTION).wrap(true).align(Label.HAlign.CENTER);
        ended.setId("timer.alarm.ended");
        content.add(ended);
        stopButton = Button.primary(Lang.tr("vanta.timer.stop"), this::stop);
        stopButton.setId("timer.alarm.stop");
        stopButton.size(STOP_WIDTH, STOP_HEIGHT);
        content.add(stopButton);
        Label hint = new Label(Lang.tr("vanta.timer.alarm.hint"), Label.Variant.MUTED).wrap(true)
                .align(Label.HAlign.CENTER);
        hint.setId("timer.alarm.hint");
        content.add(hint);

        Panel card = Panel.card(theme);
        card.setId("timer.alarm.card");
        card.width(CARD_WIDTH);
        card.add(content);
        Column root = new Column().align(Align.CENTER).justify(Justify.CENTER).padding(Theme.SPACE_4);
        root.add(card);
        describe();
        return root;
    }

    @Override
    protected void onScreenInit() {
        stopButton.requestFocus(context());
    }

    /** Fills the label and the end time from the ringing timer, or from the last alarm when it no longer rings. */
    private void describe() {
        boolean ringing = timer.isRinging();
        String text = ringing ? timer.label() : timer.lastLabel();
        label.setText(text);
        label.setVisible(!text.isEmpty());
        OptionalLong at = ringing ? timer.rangAt() : timer.lastRangAt();
        ended.setText(at.isPresent()
                ? Lang.tr("vanta.timer.alarm.ended", TimerFormats.timeOfDay(at.getAsLong(), zone.get()))
                : "");
        ended.setVisible(at.isPresent());
        invalidateLayout();
    }

    /** Stops the alarm and closes the window (the host shows the previous screen or the game again). */
    void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        timer.stop();
        close();
    }

    @Override
    protected void onScreenTick() {
        if (!stopped && !timer.isRinging()) {
            // The alarm ended another way (Cancel, /vanta timer stop, a new timer): nothing left to show.
            stopped = true;
            close();
        }
    }

    /** Escape stops the alarm instead of only closing the window (see {@link #onKeyDown}). */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected boolean onKeyDown(int key, int scancode, int mods) {
        if (key == Keys.ESCAPE || key == Keys.ENTER || key == Keys.KP_ENTER) {
            stop();
            return true;
        }
        // No other shortcut (Ctrl+K search) while the alarm rings: the window would only come back on the next tick.
        return false;
    }

    // ---- accessors (tests) ---------------------------------------------------------------------------------------

    public BigText headline() {
        return headline;
    }

    public Label label() {
        return label;
    }

    public Label ended() {
        return ended;
    }

    public Button stopButton() {
        return stopButton;
    }
}
