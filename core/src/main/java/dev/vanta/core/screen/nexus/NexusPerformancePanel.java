package dev.vanta.core.screen.nexus;

import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.hud.FrameTimeTracker;
import dev.vanta.core.hud.widgets.FrametimeGraphWidget;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.perf.MemorySampler;
import dev.vanta.core.perf.PerformanceCenter;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.perf.PerformanceSnapshot;
import dev.vanta.core.perf.SmartBoostState;
import dev.vanta.core.perf.SmartBoostTuner;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.LabelValueRow;
import dev.vanta.core.screen.common.ResponsiveGrid;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.search.ActionEntry;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Card;
import dev.vanta.core.ui.widget.Label;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The Performance section: live values only, refreshed every tick from {@link PerformanceCenter#snapshot()}, the
 * frame time window, the {@link GameBridge} and Smart Boost ("n/a" whenever a value is not available; nothing is
 * estimated), the preset buttons, Boost FPS and a link to the full Performance Center.
 */
public final class NexusPerformancePanel extends NexusPanel {
    /** Frames slower than this count as hitches. */
    public static final double HITCH_MS = FrametimeGraphWidget.HITCH_MS;

    private final PerformanceCenter perf;
    private final GameBridge game;
    private final LabelValueRow fps;
    private final LabelValueRow p50;
    private final LabelValueRow p99;
    private final LabelValueRow hitches;
    private final LabelValueRow ping;
    private final LabelValueRow renderDistance;
    private final LabelValueRow gpu;
    private final LabelValueRow memoryUsed;
    private final LabelValueRow memoryAllocated;
    private final LabelValueRow memoryMax;
    private final Label smartBoost;
    private final List<Button> presetButtons = new ArrayList<>();
    private final Button boost;
    private final Button openCenter;
    private String smartSignature = "";
    private UiContext lastContext;

    public NexusPerformancePanel(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, NexusSection.PERFORMANCE);
        this.perf = services.performance();
        this.game = services.game();

        ResponsiveGrid cards = new ResponsiveGrid(168, 3, Theme.SPACE_4);
        Card frames = card("vanta.nexus.perf.frames", "nexus.perf.frames");
        fps = frames.add(row("vanta.nexus.perf.fps_now"));
        p50 = frames.add(row("vanta.nexus.perf.p50"));
        p99 = frames.add(row("vanta.nexus.perf.p99"));
        hitches = frames.add(row("vanta.nexus.perf.hitches"));
        cards.add(frames);
        Card gameCard = card("vanta.nexus.perf.game", "nexus.perf.game");
        ping = gameCard.add(row("vanta.nexus.perf.ping"));
        renderDistance = gameCard.add(row("vanta.nexus.perf.render_distance"));
        gpu = gameCard.add(row("vanta.nexus.perf.gpu"));
        cards.add(gameCard);
        Card memory = card("vanta.perf.memory", "nexus.perf.memory");
        memoryUsed = memory.add(row("vanta.perf.memory_used"));
        memoryAllocated = memory.add(row("vanta.perf.memory_allocated"));
        memoryMax = memory.add(row("vanta.perf.memory_max"));
        cards.add(memory);
        content().add(cards);

        Card smart = card("vanta.perf.smart_boost.title", "nexus.perf.smartBoost");
        smartBoost = smart.add(new Label("", Label.Variant.BODY).wrap(true));
        smartBoost.setId("nexus.perf.smartBoost.status");
        content().add(smart);

        Card presets = new Card(Lang.tr("vanta.perf.presets")).caption(Lang.tr("vanta.perf.preset_hint"));
        presets.setId("nexus.perf.presets");
        ChipFlow chips = new ChipFlow();
        boost = Button.primary(Lang.tr("vanta.action." + ActionEntry.BOOST_FPS), this::boostFps).compact(true);
        boost.icon(Icons.ARROW_UP);
        boost.setId("nexus.perf.boostFps");
        chips.add(boost);
        for (PerformancePreset preset : PerformancePreset.values()) {
            Button button = Button.secondary(Lang.tr(preset.langKey()), () -> applyPreset(preset)).compact(true);
            button.setTooltip(Lang.tr(preset.descriptionKey()));
            button.setId("nexus.perf.preset." + preset.id());
            presetButtons.add(button);
            chips.add(button);
        }
        presets.add(chips);
        openCenter = Button.ghost(Lang.tr("vanta.action.open_performance"),
                () -> navigator.openScreen(lastContext, ScreenId.PERFORMANCE)).compact(true);
        openCenter.icon(Icons.EXTERNAL_LINK);
        openCenter.setId("nexus.perf.openCenter");
        presets.add(openCenter);
        content().add(presets);
        refreshValues();
    }

    private static Card card(String titleKey, String id) {
        Card card = new Card(Lang.tr(titleKey));
        card.setId(id);
        card.body().gap(Theme.SPACE_2);
        return card;
    }

    private static LabelValueRow row(String labelKey) {
        return new LabelValueRow(Lang.tr(labelKey), Lang.tr("vanta.common.not_available"));
    }

    // ---- accessors (tests) ---------------------------------------------------------------------------------------

    public List<Button> presetButtons() {
        return List.copyOf(presetButtons);
    }

    public Button boostButton() {
        return boost;
    }

    public Button openCenterButton() {
        return openCenter;
    }

    public String fpsText() {
        return fps.value();
    }

    public String p50Text() {
        return p50.value();
    }

    public String p99Text() {
        return p99.value();
    }

    public String hitchesText() {
        return hitches.value();
    }

    public String pingText() {
        return ping.value();
    }

    public String gpuText() {
        return gpu.value();
    }

    public String smartBoostText() {
        return smartBoost.text();
    }

    // ---- actions -------------------------------------------------------------------------------------------------

    /** Applies a preset (vanilla options only). */
    public void applyPreset(PerformancePreset preset) {
        perf.applyPreset(preset);
        refreshValues();
    }

    /** The one-click Boost FPS (see {@code VantaServices.boostFps()}). */
    public void boostFps() {
        services().boostFps();
        refreshValues();
    }

    // ---- live values ---------------------------------------------------------------------------------------------

    @Override
    public void tick(UiContext ctx) {
        lastContext = ctx;
        refreshValues();
    }

    @Override
    public void onShown(UiContext ctx) {
        lastContext = ctx;
        refreshValues();
    }

    @Override
    public void layout(UiContext ctx) {
        lastContext = ctx;
        super.layout(ctx);
    }

    /** Re-reads every value; nothing is estimated and missing values read "n/a". */
    public void refreshValues() {
        String na = Lang.tr("vanta.common.not_available");
        boolean inWorld = game.isInWorld();
        PerformanceSnapshot snapshot = perf.snapshot();
        fps.setValue(inWorld ? Lang.tr("vanta.common.fps", snapshot.fps()) : na);
        FrameTimeTracker frames = perf.frameTimes();
        if (frames.isEmpty()) {
            p50.setValue(na);
            p99.setValue(na);
            hitches.setValue(na);
        } else {
            p50.setValue(ms(frames.percentile(50)));
            p99.setValue(ms(frames.percentile(99)));
            int slow = 0;
            for (double sample : frames.samplesOldestFirst()) {
                if (sample > HITCH_MS) {
                    slow++;
                }
            }
            hitches.setValue(Lang.tr("vanta.nexus.perf.hitches_value", slow, frames.size()));
        }
        OptionalInt pingMs = inWorld && !game.isSingleplayer() ? game.pingMillis() : OptionalInt.empty();
        ping.setValue(pingMs.isPresent() ? Lang.tr("vanta.common.milliseconds", pingMs.getAsInt())
                : inWorld && game.isSingleplayer() ? Lang.tr("vanta.nexus.perf.ping_singleplayer") : na);
        renderDistance.setValue(Lang.tr("vanta.common.chunks", snapshot.renderDistance()));
        gpu.setValue(game.gpuRenderer().filter(g -> !g.isBlank()).orElse(na));
        MemorySampler.MemorySample mem = snapshot.memory();
        memoryUsed.setValue(mb(mem.used()) + "  (" + Math.round(mem.usedFraction() * 100) + " %)");
        memoryAllocated.setValue(mb(mem.allocated()));
        memoryMax.setValue(mb(mem.max()));
        refreshSmartBoost();
    }

    private void refreshSmartBoost() {
        SmartBoostTuner tuner = services().smartBoost();
        SmartBoostState state = tuner.state();
        String status;
        if (tuner.isRunning()) {
            status = tuner.isGated() ? Lang.tr("vanta.perf.smart_boost.status.paused")
                    : Lang.tr("vanta.perf.smart_boost.status.measuring",
                    tuner.measuringPreset().map(p -> Lang.tr(p.langKey())).orElse(""),
                    tuner.measuredMs() / 1000, tuner.windowMs() / 1000);
        } else if (tuner.isPending()) {
            status = Lang.tr("vanta.perf.smart_boost.status.waiting");
        } else if (state.graphicsPresetChosen()) {
            status = Lang.tr("vanta.perf.smart_boost.status.graphics_chosen");
        } else if (state.result().isPresent()) {
            SmartBoostState.Result r = state.result().get();
            status = Lang.tr("vanta.perf.smart_boost.status.last", Lang.tr(r.preset().langKey()),
                    Math.round(r.p50Fps()), r.targetFps(), date(state.lastRunAtMillis()));
        } else {
            status = Lang.tr("vanta.perf.smart_boost.status.never");
        }
        Optional<PerformancePreset> active = perf.detectPreset();
        String signature = status + "|" + active.map(PerformancePreset::id).orElse("");
        if (signature.equals(smartSignature)) {
            return;
        }
        smartSignature = signature;
        smartBoost.setText(status);
        for (int i = 0; i < presetButtons.size(); i++) {
            PerformancePreset preset = PerformancePreset.values()[i];
            presetButtons.get(i).variant(active.isPresent() && active.get() == preset ? Button.Variant.PRIMARY
                    : Button.Variant.SECONDARY);
        }
        if (lastContext != null) {
            lastContext.requestLayout();
        }
    }

    private static String date(long epochMillis) {
        if (epochMillis <= 0) {
            return "";
        }
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
                .format(java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()));
    }

    private static String ms(double value) {
        return Lang.tr("vanta.perf.frame_time_value", String.format(Locale.ROOT, "%.1f", value));
    }

    private static String mb(long bytes) {
        return Lang.tr("vanta.perf.mb", MemorySampler.MemorySample.toMiB(bytes));
    }
}
