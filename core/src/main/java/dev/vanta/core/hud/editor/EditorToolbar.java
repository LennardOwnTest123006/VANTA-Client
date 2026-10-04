package dev.vanta.core.hud.editor;

import dev.vanta.core.hud.HudPreset;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.VantaMark;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Dialog;
import dev.vanta.core.ui.widget.IconButton;
import dev.vanta.core.ui.widget.Select;
import dev.vanta.core.ui.widget.Toggle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Top bar of the HUD editor: VANTA mark and title, the preset dropdown with save / delete, reset, undo / redo,
 * grid and snap switches, the panel toggles and the Done button. In compact mode the labelled switches become icon
 * buttons and the title shrinks.
 */
public final class EditorToolbar extends UiNode {

    /** Bar height on desktop sizes. */
    public static final int HEIGHT = 30;
    /** Bar height in compact mode. */
    public static final int COMPACT_HEIGHT = 24;
    /** Sentinel option shown when the layout matches no preset. */
    public static final String CUSTOM = "__custom";
    private static final int MARK = 14;
    private static final int MARK_COMPACT = 10;

    private final HudEditorScreen screen;
    private final HudEditorSession session;
    private final Select<String> presets;
    private final IconButton savePreset;
    private final IconButton deletePreset;
    private final IconButton reset;
    private final IconButton undo;
    private final IconButton redo;
    private final Toggle gridToggle;
    private final Toggle snapToggle;
    private final IconButton gridIcon;
    private final IconButton snapIcon;
    private final IconButton widgetsToggle;
    private final IconButton inspectorToggle;
    private final Button done;
    private boolean compact;
    private List<String> presetIds = List.of();
    private boolean refreshingPresets;

    /** Toolbar for the screen. */
    public EditorToolbar(HudEditorScreen screen, HudEditorSession session) {
        this.screen = Objects.requireNonNull(screen, "screen");
        this.session = Objects.requireNonNull(session, "session");
        this.presets = new Select<>(new ArrayList<>(List.of(CUSTOM)), CUSTOM, this::presetLabel);
        this.presets.setId("hud.toolbar.presets");
        this.presets.onChange(this::onPresetChosen);
        this.savePreset = icon(Icons.STAR, "vanta.hud.editor.save_preset", screen::openSavePresetDialog);
        this.savePreset.setId("hud.toolbar.savePreset");
        this.deletePreset = icon(Icons.TRASH, "vanta.hud.editor.delete_preset", this::confirmDeletePreset);
        this.deletePreset.setId("hud.toolbar.deletePreset");
        this.reset = icon(Icons.RESET, "vanta.hud.editor.reset_layout", this::confirmReset);
        this.reset.setId("hud.toolbar.reset");
        this.undo = icon(Icons.CHEVRON_LEFT, "vanta.hud.editor.undo", session::undo);
        this.undo.setId("hud.toolbar.undo");
        this.redo = icon(Icons.CHEVRON_RIGHT, "vanta.hud.editor.redo", session::redo);
        this.redo.setId("hud.toolbar.redo");
        this.gridToggle = new Toggle(Lang.tr("vanta.hud.editor.grid"), session.gridVisible(), session::setGridVisible);
        this.gridToggle.setId("hud.toolbar.grid");
        this.snapToggle = new Toggle(Lang.tr("vanta.hud.editor.snap"), session.snapEnabled(), session::setSnapEnabled);
        this.snapToggle.setId("hud.toolbar.snap");
        this.gridIcon = icon(Icons.GRID, "vanta.hud.editor.grid", () -> session.setGridVisible(!session.gridVisible()));
        this.gridIcon.setId("hud.toolbar.gridIcon");
        this.snapIcon = icon(Icons.DRAG, "vanta.hud.editor.snap", () -> session.setSnapEnabled(!session.snapEnabled()));
        this.snapIcon.setId("hud.toolbar.snapIcon");
        this.widgetsToggle = icon(Icons.LIST, "vanta.hud.editor.show_widgets", screen::toggleWidgetList);
        this.widgetsToggle.setId("hud.toolbar.widgets");
        this.inspectorToggle = icon(Icons.SLIDERS, "vanta.hud.editor.show_inspector", screen::toggleInspector);
        this.inspectorToggle.setId("hud.toolbar.inspector");
        this.done = Button.primary(Lang.tr("vanta.common.done"), screen::done);
        this.done.setId("hud.toolbar.done");
        add(presets);
        add(savePreset);
        add(deletePreset);
        add(reset);
        add(undo);
        add(redo);
        add(gridToggle);
        add(snapToggle);
        add(gridIcon);
        add(snapIcon);
        add(widgetsToggle);
        add(inspectorToggle);
        add(done);
        refresh();
    }

    private static IconButton icon(Icons glyph, String tooltipKey, Runnable action) {
        IconButton button = new IconButton(glyph, action).sizes(20, 10);
        button.setTooltip(Lang.tr(tooltipKey));
        return button;
    }

    // ---- accessors ------------------------------------------------------------------------------------------------

    public Select<String> presetSelect() {
        return presets;
    }

    public IconButton undoButton() {
        return undo;
    }

    public IconButton redoButton() {
        return redo;
    }

    public Button doneButton() {
        return done;
    }

    public IconButton widgetsToggleButton() {
        return widgetsToggle;
    }

    public IconButton inspectorToggleButton() {
        return inspectorToggle;
    }

    public Toggle gridToggle() {
        return gridToggle;
    }

    public IconButton gridIconButton() {
        return gridIcon;
    }

    public IconButton savePresetButton() {
        return savePreset;
    }

    public IconButton resetButton() {
        return reset;
    }

    /** Whether the compact arrangement is active. */
    public boolean isCompact() {
        return compact;
    }

    /** Switches between the desktop and compact arrangements. */
    public void setCompact(boolean on) {
        this.compact = on;
    }

    /** Height for the current mode. */
    public int barHeight() {
        return compact ? COMPACT_HEIGHT : HEIGHT;
    }

    // ---- presets ------------------------------------------------------------------------------------------------

    private String presetLabel(String id) {
        if (CUSTOM.equals(id)) {
            return Lang.tr("vanta.hud.editor.custom_layout");
        }
        return session.services().hud().findPreset(id).map(HudEditorSession::presetName).orElse(id);
    }

    private void onPresetChosen(String id) {
        if (refreshingPresets || CUSTOM.equals(id)) {
            return;
        }
        session.services().hud().findPreset(id).ifPresent(session::applyPreset);
    }

    private void confirmDeletePreset() {
        Optional<HudPreset> active = session.activePreset().filter(p -> !p.builtIn());
        if (active.isEmpty()) {
            return;
        }
        UiContext ctx = screen.context();
        HudPreset preset = active.get();
        Dialog.confirm(ctx, Lang.tr("vanta.hud.editor.delete_preset"),
                Lang.tr("vanta.hud.editor.delete_preset.body", preset.name()), Lang.tr("vanta.common.delete"),
                Lang.tr("vanta.common.cancel"), true, () -> {
                    session.deletePreset(preset);
                    refresh();
                });
    }

    private void confirmReset() {
        UiContext ctx = screen.context();
        Dialog.confirm(ctx, Lang.tr("vanta.hud.editor.reset_layout.confirm.title"),
                Lang.tr("vanta.hud.editor.reset_layout.confirm.body"), Lang.tr("vanta.common.reset"),
                Lang.tr("vanta.common.cancel"), false, session::resetLayout);
    }

    /** Re-reads undo/redo availability, the preset list and the switch states from the session. */
    public void refresh() {
        undo.setEnabled(session.model().canUndo());
        redo.setEnabled(session.model().canRedo());
        gridToggle.setOn(session.gridVisible());
        snapToggle.setOn(session.snapEnabled());
        gridIcon.active(session.gridVisible());
        snapIcon.active(session.snapEnabled());
        widgetsToggle.active(screen.isWidgetListOpen());
        inspectorToggle.active(screen.isInspectorOpen());
        Optional<HudPreset> active = session.activePreset();
        List<String> ids = new ArrayList<>();
        if (active.isEmpty()) {
            ids.add(CUSTOM);
        }
        for (HudPreset preset : session.presets()) {
            ids.add(preset.id());
        }
        refreshingPresets = true;
        try {
            if (!ids.equals(presetIds)) {
                presetIds = ids;
                presets.setOptions(ids);
            }
            presets.setValue(active.map(HudPreset::id).orElse(CUSTOM));
        } finally {
            refreshingPresets = false;
        }
        deletePreset.setEnabled(active.isPresent() && !active.get().builtIn());
    }

    // ---- layout -------------------------------------------------------------------------------------------------

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(bounds().w(), barHeight());
    }

    private int titleWidth(UiContext ctx) {
        if (compact) {
            return MARK_COMPACT + Theme.SPACE_2;
        }
        return MARK + Theme.SPACE_3 + ctx.textWidth(Lang.tr("vanta.hud.editor.title"), FontKind.DISPLAY);
    }

    @Override
    public void layout(UiContext ctx) {
        Rect b = bounds();
        int h = barHeight();
        int control = 20;
        int cy = b.y() + (h - control) / 2;
        int gap = Theme.SPACE_1;
        gridToggle.setVisible(!compact);
        snapToggle.setVisible(!compact);
        gridIcon.setVisible(compact);
        snapIcon.setVisible(compact);
        deletePreset.setVisible(!compact);

        // Right side: Done, then the panel toggles.
        Size doneSize = done.preferredSize(ctx);
        int right = b.right() - Theme.SPACE_4;
        done.setBounds(right - doneSize.w(), cy, doneSize.w(), control);
        done.layout(ctx);
        right = done.bounds().x() - Theme.SPACE_4;
        inspectorToggle.setBounds(right - control, cy, control, control);
        inspectorToggle.layout(ctx);
        right = inspectorToggle.bounds().x() - gap;
        widgetsToggle.setBounds(right - control, cy, control, control);
        widgetsToggle.layout(ctx);
        right = widgetsToggle.bounds().x() - Theme.SPACE_4;

        // Left side.
        int x = b.x() + Theme.SPACE_4 + titleWidth(ctx) + Theme.SPACE_4;
        int available = Math.max(0, right - x);
        int presetW = Math.min(compact ? 96 : 132, Math.max(60, available / 3));
        presets.setBounds(x, cy, presetW, control);
        presets.layout(ctx);
        x += presetW + gap;
        List<UiNode> middle = new ArrayList<>();
        middle.add(savePreset);
        if (!compact) {
            middle.add(deletePreset);
        }
        middle.add(reset);
        middle.add(undo);
        middle.add(redo);
        for (UiNode node : middle) {
            node.setBounds(x, cy, control, control);
            node.layout(ctx);
            x += control + gap;
        }
        x += Theme.SPACE_3;
        if (compact) {
            gridIcon.setBounds(x, cy, control, control);
            gridIcon.layout(ctx);
            x += control + gap;
            snapIcon.setBounds(x, cy, control, control);
            snapIcon.layout(ctx);
        } else {
            Size gs = gridToggle.preferredSize(ctx);
            gridToggle.setBounds(x, cy, gs.w(), control);
            gridToggle.layout(ctx);
            x += gs.w() + Theme.SPACE_4;
            Size ss = snapToggle.preferredSize(ctx);
            snapToggle.setBounds(x, cy, ss.w(), control);
            snapToggle.layout(ctx);
        }
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        canvas.fill(b.x(), b.y(), b.w(), b.h(), Colors.withAlpha(theme.surface1(), theme.panelAlpha()));
        canvas.fill(b.x(), b.bottom() - 1, b.w(), 1, theme.borderSubtle());
        canvas.fillGradientH(b.x(), b.y(), b.w(), 1, Colors.withAlpha(theme.gradientStart(), 0.9f),
                Colors.withAlpha(theme.gradientEnd(), 0.9f));
        int markSize = compact ? MARK_COMPACT : MARK;
        int markX = b.x() + Theme.SPACE_4;
        VantaMark.draw(canvas, markX, b.y() + (b.h() - markSize) / 2, markSize, theme);
        if (!compact) {
            String title = Lang.tr("vanta.hud.editor.title");
            canvas.text(title, markX + MARK + Theme.SPACE_3, b.y() + (b.h() - canvas.lineHeight(FontKind.DISPLAY)) / 2,
                    theme.textPrimary(), FontKind.DISPLAY, false);
        }
    }
}
