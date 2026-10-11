package dev.vanta.core.screen.timer;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.VantaUiScreen;
import dev.vanta.core.screen.nexus.ChipFlow;
import dev.vanta.core.timer.TimerFormats;
import dev.vanta.core.timer.TimerService;
import dev.vanta.core.timer.TimerState;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.VantaShell;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Card;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.TextField;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * The timer: a large countdown with Pause/Resume and Cancel, preset buttons (1, 5, 10, 15, 30 and 60 minutes) that
 * start at once, and a custom duration typed as minutes and seconds (up to 24 hours) with an optional label. The
 * countdown text is rebuilt only when the shown second changes, so at most once per second. The timer keeps running
 * when this screen closes; the alarm window ({@link TimerAlarmScreen}) appears when the time is up.
 */
public final class TimerScreen extends VantaUiScreen {
    private final TimerService timer;
    private final Supplier<ZoneId> zone;
    private final List<Button> presetButtons = new ArrayList<>();
    private BigText display;
    private Label status;
    private Button pauseButton;
    private Button cancelButton;
    private Button startButton;
    private TextField minutesField;
    private TextField secondsField;
    private TextField labelField;
    private Label error;
    private String shownRemaining;
    private TimerState shownState;
    private String shownStatus;

    public TimerScreen(VantaServices services, ScreenNavigator navigator) {
        this(services, navigator, TimerFormats::localZone);
    }

    /**
     * @param zone the time zone of the "Rings at" time (the PC's zone in the game, a fixed one in tests)
     */
    public TimerScreen(VantaServices services, ScreenNavigator navigator, Supplier<ZoneId> zone) {
        super(services, navigator, ScreenId.TIMER);
        this.timer = services.timer();
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    @Override
    protected UiNode build(UiContext ctx) {
        VantaShell shell = new VantaShell(Lang.tr("vanta.screen.timer"), this::close);
        shell.backTooltip(Lang.tr("vanta.common.back"));
        Column content = new Column(Theme.SPACE_4);
        content.add(nowCard());
        content.add(presetsCard());
        content.add(customCard());
        ScrollPanel scroll = new ScrollPanel(content).edgeFade(Colors.withAlpha(ctx.theme().bgBase(), 0.92f));
        shell.content(scroll);
        refresh();
        return shell;
    }

    private Card nowCard() {
        Card card = new Card(Lang.tr("vanta.timer.now"));
        card.setId("timer.now");
        card.body().gap(Theme.SPACE_3);
        display = new BigText("", FontKind.DISPLAY, 3f);
        display.setId("timer.display");
        card.add(display);
        status = new Label("", Label.Variant.CAPTION).wrap(true).align(Label.HAlign.CENTER);
        status.setId("timer.status");
        card.add(status);
        ChipFlow controls = new ChipFlow();
        pauseButton = Button.secondary(Lang.tr("vanta.timer.pause"), this::togglePause).compact(true);
        pauseButton.setId("timer.pause");
        controls.add(pauseButton);
        cancelButton = Button.secondary(Lang.tr("vanta.common.cancel"), this::cancelTimer).compact(true);
        cancelButton.icon(Icons.CLOSE);
        cancelButton.setId("timer.cancel");
        controls.add(cancelButton);
        card.add(controls);
        return card;
    }

    private Card presetsCard() {
        Card card = new Card(Lang.tr("vanta.timer.presets"));
        card.setId("timer.presets");
        card.body().gap(Theme.SPACE_3);
        card.add(new Label(Lang.tr("vanta.timer.presets.hint"), Label.Variant.MUTED).wrap(true));
        ChipFlow flow = new ChipFlow();
        for (int minutes : TimerService.PRESET_MINUTES) {
            Button preset = Button.secondary(Lang.tr("vanta.timer.preset", minutes), () -> startPreset(minutes))
                    .compact(true);
            preset.setId("timer.preset." + minutes);
            presetButtons.add(preset);
            flow.add(preset);
        }
        card.add(flow);
        return card;
    }

    private Card customCard() {
        Card card = new Card(Lang.tr("vanta.timer.custom"));
        card.setId("timer.custom");
        card.body().gap(Theme.SPACE_3);
        minutesField = new TextField("10").maxLength(4).charFilter(TimerScreen::isDigit)
                .placeholder(Lang.tr("vanta.timer.minutes"));
        minutesField.setId("timer.minutes");
        minutesField.onChange(t -> clearError());
        minutesField.onSubmit(t -> startCustom());
        secondsField = new TextField("0").maxLength(2).charFilter(TimerScreen::isDigit)
                .placeholder(Lang.tr("vanta.timer.seconds"));
        secondsField.setId("timer.seconds");
        secondsField.onChange(t -> clearError());
        secondsField.onSubmit(t -> startCustom());
        Row fields = new Row(Theme.SPACE_3);
        fields.add(labeled(Lang.tr("vanta.timer.minutes"), minutesField));
        fields.add(labeled(Lang.tr("vanta.timer.seconds"), secondsField));
        card.add(fields);
        labelField = new TextField("").maxLength(TimerService.MAX_LABEL_LENGTH)
                .placeholder(Lang.tr("vanta.timer.label.placeholder"));
        labelField.setId("timer.label");
        labelField.onSubmit(t -> startCustom());
        card.add(labeled(Lang.tr("vanta.timer.label"), labelField));
        error = new Label("", Label.Variant.CAPTION).wrap(true);
        error.setId("timer.error");
        error.setVisible(false);
        card.add(error);
        startButton = Button.primary(Lang.tr("vanta.timer.start"), this::startCustom).compact(true);
        startButton.icon(Icons.PLAY);
        startButton.setId("timer.start");
        Row actions = new Row(Theme.SPACE_3);
        actions.add(startButton);
        card.add(actions);
        return card;
    }

    private static boolean isDigit(int codePoint) {
        return codePoint >= '0' && codePoint <= '9';
    }

    private static Column labeled(String label, UiNode control) {
        Column c = new Column(Theme.SPACE_1);
        c.add(new Label(label, Label.Variant.CAPTION));
        c.add(control);
        c.flex(1f);
        return c;
    }

    // ---- actions -------------------------------------------------------------------------------------------------

    /** Starts a preset with the typed label. */
    void startPreset(int minutes) {
        clearError();
        timer.startMinutes(minutes, labelField.text());
        refresh();
    }

    /** Starts the custom duration of the minutes and seconds fields, or shows why it cannot. */
    void startCustom() {
        TimerFormats.Duration duration = TimerFormats.parseDuration(minutesField.text(), secondsField.text());
        if (!duration.ok()) {
            showError(duration.errorKey());
            return;
        }
        clearError();
        timer.start(duration.millis(), labelField.text());
        refresh();
    }

    private void togglePause() {
        timer.togglePause();
        refresh();
    }

    private void cancelTimer() {
        timer.cancel();
        refresh();
    }

    private void showError(String key) {
        error.setText(Lang.tr(key));
        error.color(theme().danger());
        if (!error.isVisible()) {
            error.setVisible(true);
        }
        invalidateLayout();
    }

    private void clearError() {
        if (error != null && error.isVisible()) {
            error.setVisible(false);
            invalidateLayout();
        }
    }

    // ---- refresh -------------------------------------------------------------------------------------------------

    @Override
    protected void onScreenTick() {
        refresh();
    }

    /** Updates the countdown, the status line and the buttons; texts are only rebuilt when they changed. */
    private void refresh() {
        TimerState state = timer.state();
        String remaining = TimerFormats.remaining(state == TimerState.IDLE ? 0L : timer.remainingMillis());
        if (!remaining.equals(shownRemaining)) {
            shownRemaining = remaining;
            display.setText(remaining);
        }
        if (state != shownState) {
            shownState = state;
            display.color(colorFor(state));
            pauseButton.setLabel(Lang.tr(state == TimerState.PAUSED ? "vanta.timer.resume" : "vanta.timer.pause"));
            pauseButton.icon(state == TimerState.PAUSED ? Icons.PLAY : null);
            pauseButton.setEnabled(state == TimerState.RUNNING || state == TimerState.PAUSED);
            cancelButton.setLabel(Lang.tr(state == TimerState.RINGING ? "vanta.timer.stop" : "vanta.common.cancel"));
            cancelButton.setEnabled(state != TimerState.IDLE);
            invalidateLayout();
        }
        String statusText = statusText(state);
        if (!statusText.equals(shownStatus)) {
            shownStatus = statusText;
            status.setText(statusText);
            invalidateLayout();
        }
    }

    private int colorFor(TimerState state) {
        Theme theme = theme();
        return switch (state) {
            case RUNNING -> theme.textPrimary();
            case PAUSED -> theme.warning();
            case RINGING -> theme.accent();
            case IDLE -> theme.textMuted();
        };
    }

    private String statusText(TimerState state) {
        String label = timer.label();
        String line = switch (state) {
            case IDLE -> Lang.tr("vanta.timer.status.idle");
            case RUNNING -> {
                OptionalLong ends = timer.endsAt();
                yield ends.isPresent()
                        ? Lang.tr("vanta.timer.status.running", TimerFormats.timeOfDay(ends.getAsLong(),
                                zone.get()))
                        : Lang.tr(state.langKey());
            }
            case PAUSED -> Lang.tr("vanta.timer.status.paused");
            case RINGING -> Lang.tr("vanta.timer.status.ringing");
        };
        return label.isEmpty() ? line : Lang.tr("vanta.timer.status.labelled", label, line);
    }

    // ---- accessors (tests) ---------------------------------------------------------------------------------------

    public BigText display() {
        return display;
    }

    public Label status() {
        return status;
    }

    public Button pauseButton() {
        return pauseButton;
    }

    public Button cancelButton() {
        return cancelButton;
    }

    public Button startButton() {
        return startButton;
    }

    public List<Button> presetButtons() {
        return List.copyOf(presetButtons);
    }

    public TextField minutesField() {
        return minutesField;
    }

    public TextField secondsField() {
        return secondsField;
    }

    public TextField labelField() {
        return labelField;
    }

    public Label error() {
        return error;
    }
}
