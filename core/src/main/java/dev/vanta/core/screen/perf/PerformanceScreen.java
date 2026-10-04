package dev.vanta.core.screen.perf;

import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.bridge.OptionsBridge;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.perf.MemorySampler;
import dev.vanta.core.perf.PerformanceCenter;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.perf.PerformanceSnapshot;
import dev.vanta.core.perf.RenderDistanceAdvisor;
import dev.vanta.core.perf.SystemInfo;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.InfoBanner;
import dev.vanta.core.screen.common.LabelValueRow;
import dev.vanta.core.screen.common.ResponsiveGrid;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.SettingFormats;
import dev.vanta.core.screen.common.SettingRow;
import dev.vanta.core.screen.common.Sparkline;
import dev.vanta.core.screen.common.VantaUiScreen;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingsListener;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.VantaShell;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.layout.Spacer;
import dev.vanta.core.ui.widget.Badge;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Card;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.Tabs;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The Performance Center: live cards (frame rate with a 60-sample sparkline, frame times, memory, render and
 * simulation distance, system), the render distance advisor's pending suggestion, the LOW / BALANCED / HIGH /
 * ULTRA presets with a table of exactly which vanilla options would change, and the safe individual video options
 * as rows. Everything written here is a vanilla option; the renderer itself is never touched.
 * <p>
 * Live values come from {@link GameBridge} through {@link PerformanceCenter#snapshot()} once per client tick.
 */
public final class PerformanceScreen extends VantaUiScreen {
    /** Frame rate samples kept for the sparkline. */
    public static final int HISTORY = 60;
    /** Cards narrower than this are stacked. */
    public static final int MIN_CARD_W = 168;

    private static final List<Setting<?>> QUICK_OPTIONS = List.of(
            VantaSettings.VIDEO_FRAMERATE_LIMIT, VantaSettings.VIDEO_VSYNC, VantaSettings.VIDEO_PARTICLES,
            VantaSettings.VIDEO_CLOUDS, VantaSettings.VIDEO_SMOOTH_LIGHTING, VantaSettings.VIDEO_ENTITY_SHADOWS,
            VantaSettings.VIDEO_ENTITY_DISTANCE, VantaSettings.VIDEO_BIOME_BLEND, VantaSettings.VIDEO_MIPMAP_LEVELS,
            VantaSettings.VIDEO_VIEW_BOBBING);

    private final PerformanceCenter perf;
    private final GameBridge game;
    private final OptionsBridge options;
    private final SystemInfo system = SystemInfo.current();
    private final int[] fpsHistory = new int[HISTORY];
    private int historyHead;
    private int historySize;
    private PerformanceSnapshot snapshot;
    private PerformancePreset selectedPreset;
    private final List<SettingRow> rows = new ArrayList<>();
    private final SettingsListener externalChange = change -> onSettingsChanged();

    private VantaShell shell;
    private ScrollPanel scroll;
    private Column content;
    private InfoBanner noWorldBanner;
    private InfoBanner suggestionBanner;
    private Sparkline sparkline;
    private FpsReadout fpsReadout;
    private LabelValueRow frameAvg;
    private LabelValueRow frameLow;
    private LabelValueRow frameMax;
    private LabelValueRow target;
    private MemoryBar memoryBar;
    private LabelValueRow memoryUsed;
    private LabelValueRow memoryAllocated;
    private LabelValueRow memoryMax;
    private LabelValueRow entities;
    private LabelValueRow cpu;
    private Tabs presetTabs;
    private Label presetDescription;
    private Badge currentPreset;
    private PresetDiffTable diffTable;
    private Label diffSummary;
    private Button applyPreset;

    public PerformanceScreen(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, ScreenId.PERFORMANCE);
        this.perf = services.performance();
        this.game = services.game();
        this.options = services.options();
        this.snapshot = perf.snapshot();
        this.selectedPreset = snapshot.activePreset().orElse(services.settings().get(VantaSettings.PERFORMANCE_PRESET));
        seedHistory();
    }

    /** Seeds the fps history from the frame-time tracker so the graph is not empty when the screen opens. */
    private void seedHistory() {
        double[] frames = perf.frameTimes().samplesOldestFirst();
        int from = Math.max(0, frames.length - HISTORY);
        for (int i = from; i < frames.length; i++) {
            if (frames[i] > 0) {
                pushFps((int) Math.round(1000.0 / frames[i]));
            }
        }
    }

    private void pushFps(int fps) {
        fpsHistory[historyHead] = Math.max(0, fps);
        historyHead = (historyHead + 1) % HISTORY;
        historySize = Math.min(HISTORY, historySize + 1);
    }

    // ---------------------------------------------------------------- accessors (tests)

    /** Frame rate samples, oldest first. */
    public int[] fpsHistory() {
        int[] out = new int[historySize];
        int start = historySize < HISTORY ? 0 : historyHead;
        for (int i = 0; i < historySize; i++) {
            out[i] = fpsHistory[(start + i) % HISTORY];
        }
        return out;
    }

    /** Latest snapshot. */
    public PerformanceSnapshot snapshot() {
        return snapshot;
    }

    /** Preset selected in the tabs (not necessarily applied). */
    public PerformancePreset selectedPreset() {
        return selectedPreset;
    }

    /** Diff of the selected preset against the current options. */
    public PresetDiff selectedDiff() {
        return PresetDiff.compute(selectedPreset, options);
    }

    public Button applyPresetButton() {
        return applyPreset;
    }

    public Tabs presetTabs() {
        return presetTabs;
    }

    public Optional<InfoBanner> suggestionBanner() {
        return suggestionBanner != null && suggestionBanner.isVisible() ? Optional.of(suggestionBanner) : Optional.empty();
    }

    /** Rows of the quick options card. */
    public List<SettingRow> quickRows() {
        return List.copyOf(rows);
    }

    /** The scroll panel holding every card (tests scroll it to reach the lower cards). */
    public ScrollPanel scrollPanel() {
        return scroll;
    }

    // ---------------------------------------------------------------- build

    @Override
    protected UiNode build(UiContext ctx) {
        Theme theme = ctx.theme();
        shell = new VantaShell(Lang.tr("vanta.perf.title"), this::close);
        shell.backTooltip(Lang.tr("vanta.common.back"));
        content = new Column(Theme.SPACE_5);

        noWorldBanner = new InfoBanner(InfoBanner.Tone.NEUTRAL, Lang.tr("vanta.perf.no_world"),
                Lang.tr("vanta.perf.no_world.description")).icon(Icons.INFO);
        noWorldBanner.setId("perf.noWorld");
        content.add(noWorldBanner);
        suggestionBanner = new InfoBanner(InfoBanner.Tone.INFO, "", "");
        suggestionBanner.setId("perf.suggestion");
        suggestionBanner.action(Button.primary(Lang.tr("vanta.perf.apply_suggestion"), this::acceptSuggestion)
                .compact(true));
        suggestionBanner.action(Button.ghost(Lang.tr("vanta.perf.dismiss"), this::dismissSuggestion).compact(true));
        content.add(suggestionBanner);

        ResponsiveGrid cards = new ResponsiveGrid(MIN_CARD_W, 3, Theme.SPACE_4);
        cards.add(fpsCard());
        cards.add(frameTimeCard());
        cards.add(memoryCard());
        cards.add(distanceCard());
        cards.add(systemCard());
        content.add(cards);

        content.add(presetCard());
        content.add(quickOptionsCard());
        content.add(new InfoBanner(InfoBanner.Tone.NEUTRAL, Lang.tr("vanta.perf.honest_note.title"),
                Lang.tr("vanta.perf.honest_note")).icon(Icons.LOCK));

        scroll = new ScrollPanel(content).edgeFade(Colors.withAlpha(theme.bgBase(), 0.92f));
        shell.content(scroll);
        applySnapshot();
        return shell;
    }

    private Card fpsCard() {
        Card card = new Card(Lang.tr("vanta.perf.fps"));
        card.setId("perf.fps");
        card.body().gap(Theme.SPACE_3);
        fpsReadout = card.add(new FpsReadout());
        sparkline = card.add(new Sparkline());
        sparkline.height(28);
        target = card.add(new LabelValueRow(Lang.tr("vanta.perf.target_label"), ""));
        return card;
    }

    private Card frameTimeCard() {
        Card card = new Card(Lang.tr("vanta.perf.frame_time"));
        card.setId("perf.frameTime");
        card.body().gap(Theme.SPACE_2);
        frameAvg = card.add(new LabelValueRow(Lang.tr("vanta.perf.average"), ""));
        frameLow = card.add(new LabelValueRow(SettingFormats.text("vanta.perf.one_percent_low"), ""));
        frameMax = card.add(new LabelValueRow(Lang.tr("vanta.perf.max_frame_time"), ""));
        return card;
    }

    private Card memoryCard() {
        Card card = new Card(Lang.tr("vanta.perf.memory"));
        card.setId("perf.memory");
        card.body().gap(Theme.SPACE_2);
        memoryBar = card.add(new MemoryBar());
        memoryUsed = card.add(new LabelValueRow(Lang.tr("vanta.perf.memory_used"), ""));
        memoryAllocated = card.add(new LabelValueRow(Lang.tr("vanta.perf.memory_allocated"), ""));
        memoryMax = card.add(new LabelValueRow(Lang.tr("vanta.perf.memory_max"), ""));
        return card;
    }

    private Card distanceCard() {
        Card card = new Card(Lang.tr("vanta.perf.distances"));
        card.setId("perf.distances");
        card.body().gap(0);
        card.add(row(VantaSettings.VIDEO_RENDER_DISTANCE, false).compact(true));
        card.add(row(VantaSettings.VIDEO_SIMULATION_DISTANCE, true).compact(true));
        entities = card.add(new LabelValueRow(Lang.tr("vanta.perf.entities"), ""));
        return card;
    }

    private Card systemCard() {
        Card card = new Card(Lang.tr("vanta.perf.system"));
        card.setId("perf.system");
        card.body().gap(Theme.SPACE_2);
        cpu = card.add(new LabelValueRow(Lang.tr("vanta.perf.cpu"), ""));
        card.add(new LabelValueRow(Lang.tr("vanta.perf.java"), system.javaVersion()));
        card.add(new LabelValueRow(Lang.tr("vanta.perf.os"), system.osName() + " " + system.osVersion()));
        card.add(new LabelValueRow(Lang.tr("vanta.perf.cpu_threads"), Lang.tr("vanta.perf.threads", system.cpuCount())));
        return card;
    }

    private Card presetCard() {
        Card card = new Card(Lang.tr("vanta.perf.presets")).caption(Lang.tr("vanta.perf.preset_hint"));
        card.setId("perf.presets");
        currentPreset = new Badge("", Badge.Tone.ACCENT);
        card.headerSlot(currentPreset);
        List<String> labels = new ArrayList<>();
        for (PerformancePreset preset : PerformancePreset.values()) {
            labels.add(Lang.tr(preset.langKey()));
        }
        presetTabs = card.add(new Tabs(labels, selectedPreset.ordinal()));
        presetTabs.setId("perf.presetTabs");
        presetTabs.onSelect(i -> selectPreset(PerformancePreset.values()[i]));
        presetDescription = card.add(new Label("", Label.Variant.CAPTION).wrap(true));
        diffTable = card.add(new PresetDiffTable(selectedDiff()));
        Row footer = new Row(Theme.SPACE_3);
        diffSummary = footer.add(new Label("", Label.Variant.MUTED));
        footer.add(Spacer.grow());
        applyPreset = footer.add(Button.primary(Lang.tr("vanta.perf.apply"), this::applySelectedPreset));
        applyPreset.icon(Icons.CHECK);
        applyPreset.setId("perf.applyPreset");
        card.add(footer);
        refreshPreset();
        return card;
    }

    private Card quickOptionsCard() {
        Card card = new Card(Lang.tr("vanta.perf.quick_options")).caption(Lang.tr("vanta.perf.quick_options.description"));
        card.setId("perf.quick");
        card.body().gap(0);
        for (int i = 0; i < QUICK_OPTIONS.size(); i++) {
            card.add(row(QUICK_OPTIONS.get(i), i == QUICK_OPTIONS.size() - 1));
        }
        return card;
    }

    private SettingRow row(Setting<?> setting, boolean last) {
        SettingRow row = new SettingRow(setting, services().settings(), id -> actions().dispatch(context(), id));
        row.last(last);
        row.onChanged(this::refreshPreset);
        rows.add(row);
        return row;
    }

    @Override
    protected void onScreenInit() {
        services().settings().addListener(externalChange);
    }

    @Override
    protected void onScreenClose() {
        services().settings().removeListener(externalChange);
    }

    private void onSettingsChanged() {
        for (SettingRow row : rows) {
            row.refresh();
        }
        refreshPreset();
    }

    // ---------------------------------------------------------------- presets

    /** Shows the diff of a preset without applying it. */
    public void selectPreset(PerformancePreset preset) {
        selectedPreset = preset;
        if (presetTabs != null && presetTabs.selected() != preset.ordinal()) {
            presetTabs.select(context(), preset.ordinal());
        }
        refreshPreset();
    }

    /** Applies the selected preset (vanilla options only) and refreshes every row. */
    public void applySelectedPreset() {
        perf.applyPreset(selectedPreset);
        onSettingsChanged();
    }

    private void refreshPreset() {
        if (diffTable == null) {
            return;
        }
        PresetDiff diff = selectedDiff();
        diffTable.setDiff(diff);
        presetDescription.setText(Lang.tr(selectedPreset.descriptionKey()));
        int changes = diff.changeCount();
        diffSummary.setText(changes == 0 ? Lang.tr("vanta.perf.diff.no_changes")
                : Lang.tr("vanta.perf.diff.changes", changes));
        applyPreset.setEnabled(changes > 0);
        Optional<PerformancePreset> active = perf.detectPreset();
        currentPreset.setText(Lang.tr("vanta.perf.current_preset",
                active.map(p -> Lang.tr(p.langKey())).orElse(Lang.tr("vanta.perf.custom"))));
        invalidateLayout();
    }

    // ---------------------------------------------------------------- suggestion

    private void acceptSuggestion() {
        perf.acceptSuggestion();
        onSettingsChanged();
        snapshot = perf.snapshot();
        applySnapshot();
    }

    private void dismissSuggestion() {
        perf.dismissSuggestion();
        snapshot = perf.snapshot();
        applySnapshot();
    }

    // ---------------------------------------------------------------- live data

    @Override
    protected void onScreenTick() {
        if (game.isInWorld()) {
            pushFps(game.fps());
        }
        snapshot = perf.snapshot();
        applySnapshot();
    }

    private void applySnapshot() {
        if (sparkline == null) {
            return;
        }
        boolean inWorld = game.isInWorld();
        boolean layoutChanged = noWorldBanner.isVisible() == inWorld;
        noWorldBanner.setVisible(!inWorld);
        sparkline.setValues(fpsHistory()).reference(perf.isFpsCapped() ? (float) perf.targetFps() : null);
        fpsReadout.set(inWorld ? snapshot.fps() : -1, snapshot.onePercentLowFps());
        target.setValue(perf.isFpsCapped() ? Lang.tr("vanta.common.fps", perf.targetFps())
                : Lang.tr("vanta.perf.fps_limit.unlimited"));
        frameAvg.setValue(ms(snapshot.frameTimeAvgMs()));
        frameLow.setValue(snapshot.frameTimeOnePercentMs() > 0
                ? ms(snapshot.frameTimeOnePercentMs()) + " · " + Lang.tr("vanta.common.fps", Math.round(snapshot.onePercentLowFps()))
                : ms(0));
        frameMax.setValue(ms(snapshot.frameTimeMaxMs()));
        MemorySampler.MemorySample mem = snapshot.memory();
        memoryBar.set(mem);
        memoryUsed.setValue(mb(mem.used()) + "  (" + Math.round(mem.usedFraction() * 100) + " %)");
        memoryAllocated.setValue(mb(mem.allocated()));
        memoryMax.setValue(mb(mem.max()));
        entities.setValue(inWorld ? Integer.toString(snapshot.entityCount()) : Lang.tr("vanta.common.not_available"));
        cpu.setValue(snapshot.cpuLoad().isPresent()
                ? Lang.tr("vanta.perf.cpu_value", Math.round(snapshot.cpuLoad().getAsDouble() * 100))
                : Lang.tr("vanta.perf.cpu_unavailable"));
        Optional<RenderDistanceAdvisor.Suggestion> suggestion = snapshot.suggestion();
        boolean hadSuggestion = suggestionBanner.isVisible();
        suggestionBanner.setVisible(suggestion.isPresent());
        if (suggestion.isPresent()) {
            RenderDistanceAdvisor.Suggestion s = suggestion.get();
            String key = s.direction() == RenderDistanceAdvisor.Direction.LOWER ? "vanta.perf.suggestion.lower"
                    : "vanta.perf.suggestion.higher";
            suggestionBanner.setTitle(Lang.tr(key, s.to()));
            suggestionBanner.setBody(Lang.tr("vanta.perf.suggestion.reason", Math.round(s.averageFps()), s.targetFps()));
        }
        if (layoutChanged || hadSuggestion != suggestion.isPresent()) {
            invalidateLayout();
        }
    }

    private static String ms(double value) {
        return Lang.tr("vanta.perf.frame_time_value", String.format(Locale.ROOT, "%.1f", value));
    }

    private static String mb(long bytes) {
        return Lang.tr("vanta.perf.mb", MemorySampler.MemorySample.toMiB(bytes));
    }

    // ---------------------------------------------------------------- nodes

    /** Big frame rate number with the 1 % low beside it. */
    private static final class FpsReadout extends UiNode {
        private int fps = -1;
        private double low;

        void set(int fpsValue, double onePercentLow) {
            this.fps = fpsValue;
            this.low = onePercentLow;
        }

        @Override
        protected Size measure(UiContext ctx) {
            return new Size(120, 18);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            String big = fps < 0 ? "—" : Integer.toString(fps);
            canvas.text(big, b.x(), b.y() + (b.h() - canvas.lineHeight(FontKind.DISPLAY)) / 2, theme.textPrimary(),
                    FontKind.DISPLAY, false);
            int x = b.x() + canvas.textWidth(big, FontKind.DISPLAY) + Theme.SPACE_2;
            int ty = b.y() + (b.h() - canvas.lineHeight(FontKind.UI)) / 2 + 2;
            canvas.text(Lang.tr("vanta.perf.fps"), x, ty, theme.textMuted(), FontKind.UI, false);
            if (low > 0) {
                String lowText = SettingFormats.text("vanta.perf.one_percent_low") + " " + Math.round(low);
                canvas.textRight(canvas.textClipped(lowText, b.w() / 2, FontKind.UI), b.right(), ty, theme.textSecondary(),
                        FontKind.UI, false);
            }
        }
    }

    /** Stacked bar: used heap over the allocated heap over the maximum heap. */
    private static final class MemoryBar extends UiNode {
        private MemorySampler.MemorySample sample = new MemorySampler.MemorySample(0, 0, 1);

        void set(MemorySampler.MemorySample s) {
            this.sample = s;
        }

        @Override
        protected Size measure(UiContext ctx) {
            return new Size(120, 8);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            int h = Math.min(6, b.h());
            int y = b.y() + (b.h() - h) / 2;
            canvas.fillRounded(b.x(), y, b.w(), h, Theme.RADIUS_SM, theme.surface3());
            long max = Math.max(1, sample.max());
            int allocated = (int) Math.round(b.w() * Math.min(1.0, sample.allocated() / (double) max));
            int used = (int) Math.round(b.w() * Math.min(1.0, sample.used() / (double) max));
            if (allocated > 0) {
                canvas.fillRounded(b.x(), y, allocated, h, Theme.RADIUS_SM, theme.surface4());
            }
            if (used > 0) {
                double fraction = sample.usedFraction();
                int end = fraction > 0.9 ? theme.danger() : fraction > 0.75 ? theme.warning() : theme.gradientEnd();
                canvas.fillRoundedGradientH(b.x(), y, used, h, Theme.RADIUS_SM, theme.gradientStart(), end);
            }
        }
    }
}
