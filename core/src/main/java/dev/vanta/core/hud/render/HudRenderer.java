package dev.vanta.core.hud.render;

import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.hud.CpsCounter;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudRect;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.widgets.HudWidgetRenderers;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TextMetrics;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Renders the active {@link HudLayout} every frame while the player is in a world.
 * <p>
 * Driving it from the client:
 * <ul>
 *   <li>{@link #tick()} once per client tick: captures the {@link HudData} snapshot (only the values the enabled
 *       widgets display) and refreshes the {@link HudPaint} from the settings (HUD theme, text shadow,
 *       accessibility).</li>
 *   <li>{@link #render(Canvas, int, int, float)} every frame from the HUD element: draws every enabled widget of
 *       {@code services.hud().layout()} except the crosshair (which {@code CrosshairRenderer} draws in the vanilla
 *       crosshair slot).</li>
 *   <li>{@link #onMouseClick(int)} for every mouse press the game receives, so the CPS and keystroke widgets can
 *       count clicks. VANTA never generates clicks.</li>
 * </ul>
 * The render pass does no settings lookups beyond the master switch and the global scale/opacity, no bridge calls
 * apart from {@code isInWorld}, and allocates only the short strings the widgets format. Frame times are not sampled
 * here: the client's {@code FrameTimer} feeds {@code PerformanceCenter.onFrame} exactly once per frame (from the HUD
 * element in a world, from {@code VantaScreen} otherwise), so recording them again in the render pass would count
 * every in-world frame twice.
 */
public final class HudRenderer {

    private final VantaServices services;
    private final GameBridge source;
    private final boolean sampleSource;
    private final HudWidgetRenderers renderers;
    private final CpsCounter leftCps = new CpsCounter();
    private final CpsCounter rightCps = new CpsCounter();
    private HudData data = HudData.EMPTY;
    private boolean captured;
    private HudPaint paint;

    /** Renderer fed by the live game. */
    public HudRenderer(VantaServices services) {
        this(services, Objects.requireNonNull(services, "services").game());
    }

    /**
     * Renderer fed by another data source, e.g. {@link SampleGameData} for the HUD editor when no world is loaded.
     */
    public HudRenderer(VantaServices services, GameBridge source) {
        this.services = Objects.requireNonNull(services, "services");
        this.source = Objects.requireNonNull(source, "source");
        this.sampleSource = source instanceof SampleGameData;
        this.renderers = HudWidgetRenderers.standard();
    }

    // ---- per tick ------------------------------------------------------------------------------------------------

    /**
     * Called once per client tick (20/s): snapshots the game data the enabled widgets need and refreshes the paint
     * from the settings.
     */
    public void tick() {
        tick(enabledTypes(services.hud().layout()));
    }

    /**
     * Once per client tick with an explicit set of widget types to capture. The HUD editor passes every type because
     * its preview also shows widgets that are disabled or absent in the live layout.
     */
    public void tick(Set<HudWidgetType> types) {
        long now = services.clock().millis();
        data = sampleSource
                ? HudData.sample()
                : HudData.capture(source, leftCps.cps(now), rightCps.cps(now), services.performance().frameTimes(),
                        services.clock().getZone(), types);
        captured = true;
        refreshPaint();
    }

    /** The widget types enabled in a layout (what a tick has to capture). */
    public static Set<HudWidgetType> enabledTypes(HudLayout layout) {
        Set<HudWidgetType> types = EnumSet.noneOf(HudWidgetType.class);
        for (HudWidgetState widget : layout.widgets()) {
            if (widget.enabled()) {
                types.add(widget.type());
            }
        }
        return types;
    }

    /** Records a mouse press for the click counters ({@link Keys#MOUSE_LEFT} / {@link Keys#MOUSE_RIGHT}). */
    public void onMouseClick(int button) {
        long now = services.clock().millis();
        if (button == Keys.MOUSE_LEFT) {
            leftCps.click(now);
        } else if (button == Keys.MOUSE_RIGHT) {
            rightCps.click(now);
        }
    }

    private void refreshPaint() {
        paint = HudPaint.of(services.cosmetics().selection().hudTheme(),
                services.settings().get(VantaSettings.HUD_TEXT_SHADOW), services.accessibility().state(),
                services.crosshair().style());
    }

    // ---- per frame -----------------------------------------------------------------------------------------------

    /**
     * Renders the whole HUD (all enabled widgets; the custom crosshair is NOT included). Called every frame while
     * in a world; does nothing on the title screen or when the HUD is disabled.
     */
    public void render(Canvas canvas, int screenWidth, int screenHeight, float deltaTicks) {
        if (!services.settings().get(VantaSettings.HUD_ENABLED) || !source.isInWorld()) {
            return;
        }
        if (!captured) {
            tick();
        }
        renderLayout(canvas, services.hud().layout(), screenWidth, screenHeight, data, false);
    }

    /**
     * Renders every enabled widget of {@code layout} with the global scale and opacity settings.
     *
     * @param includeCrosshair true to draw the crosshair widget too (the HUD editor preview); false in-game
     */
    public void renderLayout(Canvas canvas, HudLayout layout, int screenWidth, int screenHeight, HudData snapshot,
                             boolean includeCrosshair) {
        renderLayout(canvas, layout, screenWidth, screenHeight, snapshot, includeCrosshair, globalScale(),
                (float) globalOpacity());
    }

    /**
     * Renders every enabled widget of {@code layout} with an explicit global scale and opacity. The HUD editor uses
     * a global scale of 1 so the preview matches {@code HudEditorModel.rects()} exactly.
     */
    public void renderLayout(Canvas canvas, HudLayout layout, int screenWidth, int screenHeight, HudData snapshot,
                             boolean includeCrosshair, double scale, float opacity) {
        List<HudWidgetState> widgets = layout.widgets();
        for (int i = 0; i < widgets.size(); i++) {
            HudWidgetState widget = widgets.get(i);
            if (!widget.enabled() || (!includeCrosshair && widget.type() == HudWidgetType.CROSSHAIR)) {
                continue;
            }
            HudRect rect = layout.resolveRect(widget, screenWidth, screenHeight, scale);
            renderWidget(canvas, widget, rect, snapshot, opacity, scale);
        }
    }

    /**
     * Renders one widget inside its resolved screen rectangle: translates to the rectangle, scales by the widget
     * and global scale, multiplies the canvas alpha by the widget opacity and {@code extraOpacity}.
     */
    public void renderWidget(Canvas canvas, HudWidgetState widget, HudRect rect, HudData snapshot,
                             float extraOpacity) {
        renderWidget(canvas, widget, rect, snapshot, extraOpacity, globalScale());
    }

    /**
     * Renders one widget with an explicit global scale (the rectangle must have been resolved with the same scale).
     */
    public void renderWidget(Canvas canvas, HudWidgetState widget, HudRect rect, HudData snapshot,
                             float extraOpacity, double globalScale) {
        HudWidgetRenderer renderer = renderers.forType(widget.type());
        float scale = (float) (widget.scale() * Math.max(0.1, globalScale));
        float previousAlpha = canvas.partialAlpha();
        canvas.setPartialAlpha(previousAlpha * (float) widget.opacity() * Math.max(0f, Math.min(1f, extraOpacity)));
        canvas.pushTranslate(rect.x(), rect.y());
        canvas.pushScale(scale, scale);
        try {
            renderer.render(canvas, widget, snapshot, paint());
        } finally {
            canvas.pop();
            canvas.pop();
            canvas.setPartialAlpha(previousAlpha);
        }
    }

    /** Content-based size of a widget in unscaled GUI pixels. */
    public Size preferredSize(TextMetrics metrics, HudWidgetState widget, HudData snapshot) {
        return renderers.forType(widget.type()).preferredSize(metrics, widget, snapshot, paint());
    }

    // ---- accessors -----------------------------------------------------------------------------------------------

    /** The snapshot the current frame renders from. */
    public HudData data() {
        return data;
    }

    /** Current paint (built from the settings on the last tick). */
    public HudPaint paint() {
        if (paint == null) {
            refreshPaint();
        }
        return paint;
    }

    /** Scale multiplier applied to every widget ({@code hud.globalScale}). */
    public double globalScale() {
        return services.settings().get(VantaSettings.HUD_GLOBAL_SCALE);
    }

    /** Opacity multiplier applied to every widget ({@code hud.globalOpacity}). */
    public double globalOpacity() {
        return services.settings().get(VantaSettings.HUD_GLOBAL_OPACITY);
    }

    /** The widget renderer registry. */
    public HudWidgetRenderers renderers() {
        return renderers;
    }

    /** The data source this renderer snapshots. */
    public GameBridge source() {
        return source;
    }

    /** True when the renderer shows {@link SampleGameData}. */
    public boolean isSample() {
        return sampleSource;
    }
}
