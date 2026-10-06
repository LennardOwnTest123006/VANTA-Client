package dev.vanta.core.hud.editor;

import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.hud.HudEditorModel;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudPreset;
import dev.vanta.core.hud.HudPresets;
import dev.vanta.core.hud.HudRect;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudRenderer;
import dev.vanta.core.hud.render.SampleGameData;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TextMetrics;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the HUD editor does to the domain, without any rendering: owns the {@link HudEditorModel}, the preview
 * {@link HudRenderer} (live game data in a world, {@link SampleGameData} otherwise) and the glue to the
 * {@link dev.vanta.core.hud.HudStore}, the settings and the notification centre.
 * <p>
 * Every change of the model is pushed to the store immediately so the in-game HUD follows the editor live; the
 * file is only written by {@link #save()} (the Done button, Escape, Ctrl+S or the host closing the screen).
 */
public final class HudEditorSession {

    private final VantaServices services;
    private final HudEditorModel model;
    private final HudRenderer renderer;
    private boolean syncing;

    /**
     * Opens a session on the live layout.
     *
     * @param services     composition root
     * @param screenWidth  game screen width in GUI pixels
     * @param screenHeight game screen height in GUI pixels
     */
    public HudEditorSession(VantaServices services, int screenWidth, int screenHeight) {
        this.services = Objects.requireNonNull(services, "services");
        this.model = new HudEditorModel(services.hud().layout(), screenWidth, screenHeight);
        GameBridge source = services.game().isInWorld() ? services.game() : new SampleGameData();
        this.renderer = new HudRenderer(services, source);
        this.model.setSnapEnabled(services.settings().get(VantaSettings.HUD_SNAP));
        this.model.onChange(this::pushToStore);
    }

    // ---- accessors -----------------------------------------------------------------------------------------------

    public VantaServices services() {
        return services;
    }

    public HudEditorModel model() {
        return model;
    }

    /** The renderer drawing the preview widgets (sample data outside a world). */
    public HudRenderer renderer() {
        return renderer;
    }

    /** True when the preview shows the live game rather than sample data. */
    public boolean isLive() {
        return !renderer.isSample();
    }

    /** Game screen width the model works with. */
    public int screenWidth() {
        return model.screenWidth();
    }

    /** Game screen height the model works with. */
    public int screenHeight() {
        return model.screenHeight();
    }

    // ---- lifecycle -----------------------------------------------------------------------------------------------

    /** Once per client tick: refreshes the preview data (every value, the preview shows disabled widgets too). */
    public void tick() {
        renderer.tick(EnumSet.allOf(HudWidgetType.class));
    }

    /** Updates the game screen size. */
    public void resize(int width, int height) {
        model.resize(width, height);
    }

    private void pushToStore() {
        if (syncing) {
            return;
        }
        syncing = true;
        try {
            services.hud().setLayout(model.layout());
        } finally {
            syncing = false;
        }
    }

    /** Whether the live layout differs from the file on disk. */
    public boolean isDirty() {
        return services.hud().isDirty();
    }

    /**
     * Writes the layout when it changed and shows the "Layout saved" toast.
     *
     * @return true when a file was written
     */
    public boolean save() {
        pushToStore();
        if (!services.hud().isDirty()) {
            return false;
        }
        services.hud().save();
        services.notifications().post(NotificationKind.SUCCESS, Lang.tr("vanta.hud.editor.layout_saved"),
                Lang.tr("vanta.hud.editor.layout_saved.body"));
        return true;
    }

    // ---- editor options --------------------------------------------------------------------------------------------

    public boolean gridVisible() {
        return services.settings().get(VantaSettings.HUD_EDITOR_GRID);
    }

    public void setGridVisible(boolean visible) {
        services.settings().set(VantaSettings.HUD_EDITOR_GRID, visible);
    }

    public boolean snapEnabled() {
        return model.snapEnabled();
    }

    public void setSnapEnabled(boolean enabled) {
        model.setSnapEnabled(enabled);
        services.settings().set(VantaSettings.HUD_SNAP, enabled);
    }

    // ---- presets -------------------------------------------------------------------------------------------------

    /** Built-in presets followed by the user's. */
    public List<HudPreset> presets() {
        return services.hud().allPresets();
    }

    /** The preset whose layout equals the edited layout. */
    public Optional<HudPreset> activePreset() {
        HudLayout current = model.layout();
        for (HudPreset preset : presets()) {
            if (preset.layout().equals(current)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    /** Display name of a preset (translated for built-ins, literal for user presets). */
    public static String presetName(HudPreset preset) {
        return preset.builtIn() ? Lang.tr(preset.langKey()) : preset.name();
    }

    /** Applies a preset as one undo step and shows the confirmation toast. */
    public void applyPreset(HudPreset preset) {
        Objects.requireNonNull(preset, "preset");
        if (preset.layout().equals(model.layout())) {
            return;
        }
        model.setLayout(preset.layout());
        services.notifications().hudPresetApplied(presetName(preset));
    }

    /** Resets to the Default preset (undoable). */
    public void resetLayout() {
        applyPreset(HudPresets.defaultPreset());
    }

    /**
     * Saves the edited layout as a new user preset.
     *
     * @throws IllegalArgumentException when the name is blank
     */
    public HudPreset savePreset(String name) {
        pushToStore();
        HudPreset preset = services.hud().saveUserPreset(name);
        services.notifications().post(NotificationKind.SUCCESS,
                Lang.tr("vanta.hud.editor.preset_saved", preset.name()), "");
        return preset;
    }

    /** Deletes a user preset; built-ins are refused. */
    public boolean deletePreset(HudPreset preset) {
        if (preset.builtIn()) {
            return false;
        }
        boolean deleted = services.hud().deleteUserPreset(preset.id());
        if (deleted) {
            services.notifications().post(NotificationKind.INFO,
                    Lang.tr("vanta.hud.editor.preset_deleted", preset.name()), "");
        }
        return deleted;
    }

    // ---- widget commands -------------------------------------------------------------------------------------------

    /** Selected widget, if any. */
    public Optional<HudWidgetState> selected() {
        return model.selected();
    }

    public void select(String id) {
        model.select(id);
    }

    /** Toggles the visibility of any widget by id. */
    public boolean toggleEnabled(String id) {
        Optional<HudWidgetState> widget = model.layout().find(id);
        if (widget.isEmpty()) {
            return false;
        }
        String previous = model.selectedId().orElse(null);
        model.select(id);
        boolean changed = model.toggleEnabled();
        model.select(previous);
        return changed;
    }

    /** Shows or hides the selected widget. */
    public boolean setSelectedEnabled(boolean enabled) {
        return model.update(w -> w.withEnabled(enabled));
    }

    /** Adds a widget of the type (or selects the existing instance when the type is single-use in practice). */
    public HudWidgetState addWidget(HudWidgetType type) {
        return model.addWidget(type);
    }

    /** Arrow-key nudge; Shift moves ten pixels. */
    public boolean nudge(int dx, int dy, boolean large) {
        int step = large ? 10 : 1;
        return model.nudge(dx * step, dy * step);
    }

    public boolean undo() {
        return model.undo();
    }

    public boolean redo() {
        return model.redo();
    }

    public Optional<HudWidgetState> duplicate() {
        return model.duplicate();
    }

    /** Removes the selected widget from the layout (the crosshair is only hidden). */
    public boolean removeSelected() {
        return model.delete();
    }

    /** Resets the selected widget's visuals. */
    public boolean resetSelected() {
        return model.resetWidget();
    }

    /** Moves the selected widget to the top of the draw order. */
    public boolean bringToFront() {
        Optional<String> id = model.selectedId();
        if (id.isEmpty()) {
            return false;
        }
        HudLayout next = model.layout().bringToFront(id.get());
        if (next.equals(model.layout())) {
            return false;
        }
        model.setLayout(next);
        return true;
    }

    /** Resizes the selected widget to its content size. */
    public boolean fitToContent(TextMetrics metrics) {
        Optional<HudWidgetState> selected = model.selected();
        if (selected.isEmpty() || !selected.get().type().supportsResize()) {
            return false;
        }
        Size size = renderer.preferredSize(metrics, selected.get(), renderer.data());
        return model.update(w -> w.withSize(Math.max(HudWidgetState.MIN_SIZE, size.w()),
                Math.max(HudWidgetState.MIN_SIZE, size.h())));
    }

    /** Re-anchors the selected widget to {@code anchor} without moving it on screen. */
    public boolean setSelectedAnchor(dev.vanta.core.hud.HudAnchor anchor) {
        Optional<HudWidgetState> selected = model.selected();
        if (selected.isEmpty() || selected.get().type().alwaysCentered()) {
            return false;
        }
        HudRect rect = model.rectOf(selected.get().id()).orElseThrow();
        int w = selected.get().scaledWidth();
        int h = selected.get().scaledHeight();
        int sw = model.screenWidth();
        int sh = model.screenHeight();
        return model.update(widget -> widget.withPosition(anchor, anchor.offsetXFor(sw, w, rect.x()),
                anchor.offsetYFor(sh, h, rect.y())));
    }

    /** Number of widgets in the layout. */
    public int widgetCount() {
        return model.layout().size();
    }
}
