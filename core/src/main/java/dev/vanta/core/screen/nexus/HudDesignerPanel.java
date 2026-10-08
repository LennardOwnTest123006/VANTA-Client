package dev.vanta.core.screen.nexus;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vanta.core.ai.NexusActions;
import dev.vanta.core.ai.NexusHudPreset;
import dev.vanta.core.ai.NexusUndo;
import dev.vanta.core.ai.RejectedAction;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudPreset;
import dev.vanta.core.hud.HudStore;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.editor.HudEditorSession;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.profiles.NameDialog;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.layout.Align;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.widget.Badge;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Card;
import dev.vanta.core.ui.widget.Dialog;
import dev.vanta.core.ui.widget.IconButton;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.Select;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The HUD Designer section: the six {@link NexusHudPreset} buttons (applied through {@link NexusActions}, the same
 * code path the assistant uses, so they are undoable from the chat), the named layouts of the {@link HudStore}
 * (built-in and user presets) with Load, Duplicate and Delete, "Save current as layout", "New from preset" and the
 * button into the HUD editor, which keeps per-widget position, size, scale and opacity.
 */
public final class HudDesignerPanel extends NexusPanel {
    private final HudStore hud;
    private final NexusActions actions;
    private final NexusUndo undo;
    private final Label summary = new Label("", Label.Variant.MUTED).wrap(true);
    private final Column layoutRows = new Column(Theme.SPACE_2);
    private final List<Row> rows = new ArrayList<>();
    private final Select<NexusHudPreset> newFromPreset;
    private final Button saveCurrent;
    private final Button newLayout;
    private final Button openEditor;
    private final List<Button> presetButtons = new ArrayList<>();
    private final Runnable unsubscribe;
    private UiContext lastContext;

    public HudDesignerPanel(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, NexusSection.HUD_DESIGNER);
        this.hud = services.hud();
        this.actions = services.nexusActions();
        this.undo = services.nexusUndo();

        summary.setId("nexus.hud.summary");
        content().add(summary);

        Card presets = new Card(Lang.tr("vanta.nexus.hud.presets")).caption(Lang.tr("vanta.nexus.hud.presets.caption"));
        presets.setId("nexus.hud.presetsCard");
        ChipFlow chips = new ChipFlow();
        for (NexusHudPreset preset : NexusHudPreset.values()) {
            Button button = Button.secondary(Lang.tr(preset.langKey()), () -> applyPreset(preset)).compact(true);
            button.setTooltip(Lang.tr(preset.descriptionKey()));
            button.setId("nexus.hud.preset." + preset.id());
            presetButtons.add(button);
            chips.add(button);
        }
        presets.add(chips);
        content().add(presets);

        Card layouts = new Card(Lang.tr("vanta.nexus.hud.layouts")).caption(Lang.tr("vanta.nexus.hud.layouts.caption"));
        layouts.setId("nexus.hud.layoutsCard");
        saveCurrent = Button.primary(Lang.tr("vanta.nexus.hud.save_current"), this::saveCurrent).compact(true);
        saveCurrent.icon(Icons.PLUS);
        saveCurrent.setId("nexus.hud.save");
        layouts.headerSlot(saveCurrent);
        layouts.body().gap(Theme.SPACE_3);
        layouts.add(layoutRows);
        Row newFrom = new Row(Theme.SPACE_3).align(Align.CENTER);
        newFromPreset = new Select<>(List.of(NexusHudPreset.values()), NexusHudPreset.MINIMAL,
                p -> Lang.tr(p.langKey()));
        newFromPreset.setId("nexus.hud.newFromPreset.select");
        newFromPreset.flex(1f);
        newFrom.add(newFromPreset);
        newLayout = Button.secondary(Lang.tr("vanta.nexus.hud.new_from_preset"), this::newFromPreset).compact(true);
        newLayout.setId("nexus.hud.newFromPreset");
        newFrom.add(newLayout);
        layouts.add(newFrom);
        content().add(layouts);

        Card editor = new Card(Lang.tr("vanta.nexus.hud.editor")).caption(Lang.tr("vanta.nexus.hud.editor.caption"));
        editor.setId("nexus.hud.editorCard");
        openEditor = Button.primary(Lang.tr("vanta.nexus.hud.open_editor"),
                () -> navigator.openScreen(lastContext, ScreenId.HUD_EDITOR)).compact(true);
        openEditor.icon(Icons.GRID);
        openEditor.setId("nexus.hud.openEditor");
        editor.add(openEditor);
        content().add(editor);

        unsubscribe = hud.onLayoutChanged(layout -> refresh());
        refresh();
    }

    // ---- accessors (tests) ---------------------------------------------------------------------------------------

    public List<Button> presetButtons() {
        return List.copyOf(presetButtons);
    }

    public Button saveCurrentButton() {
        return saveCurrent;
    }

    public Button newLayoutButton() {
        return newLayout;
    }

    public Button openEditorButton() {
        return openEditor;
    }

    public Select<NexusHudPreset> newFromPresetSelect() {
        return newFromPreset;
    }

    /** The layout rows, in {@link HudStore#allPresets()} order. */
    public List<Row> layoutRows() {
        return List.copyOf(rows);
    }

    /** The Load button of a named layout. */
    public Optional<Button> loadButton(String presetId) {
        return findButton("nexus.hud.layout.load." + presetId);
    }

    /** The Delete button of a user layout. */
    public Optional<IconButton> deleteButton(String presetId) {
        return Optional.ofNullable((IconButton) layoutRows.findById("nexus.hud.layout.delete." + presetId));
    }

    private Optional<Button> findButton(String id) {
        return Optional.ofNullable((Button) layoutRows.findById(id));
    }

    public String summaryText() {
        return summary.text();
    }

    // ---- actions -------------------------------------------------------------------------------------------------

    /** Applies a Nexus preset through {@link NexusActions} ({@code hud.preset}), recorded as an undoable turn. */
    public void applyPreset(NexusHudPreset preset) {
        JsonObject action = new JsonObject();
        action.addProperty("type", "hud.preset");
        action.addProperty("preset", preset.id());
        runThroughNexus(action, Lang.tr(preset.langKey()), outcome ->
                services().notifications().hudPresetApplied(Lang.tr(preset.langKey())));
    }

    /** Loads a named layout through {@link NexusActions} ({@code hud.layout.load}). */
    public void load(HudPreset preset) {
        String name = HudEditorSession.presetName(preset);
        JsonObject action = new JsonObject();
        action.addProperty("type", "hud.layout.load");
        action.addProperty("name", name);
        runThroughNexus(action, name, outcome -> services().notifications().hudPresetApplied(name));
    }

    private void runThroughNexus(JsonObject action, String label,
                                 java.util.function.Consumer<NexusActions.Outcome> onApplied) {
        JsonArray array = new JsonArray();
        array.add(action);
        NexusUndo.Turn turn = undo.begin();
        NexusActions.Outcome outcome = actions.execute(array, turn);
        undo.commit(turn, label);
        if (!outcome.applied().isEmpty()) {
            onApplied.accept(outcome);
        } else {
            String why = outcome.rejected().isEmpty() ? label
                    : outcome.rejected().stream().map(RejectedAction::summary).reduce((a, b) -> a + ", " + b).orElse(label);
            services().notifications().post(NotificationKind.WARNING, Lang.tr("vanta.nexus.hud.not_applied"), why);
        }
        refresh();
    }

    /** Opens the name prompt and saves the live layout as a user layout. */
    public NameDialog saveCurrent() {
        return NameDialog.open(lastContext, Lang.tr("vanta.nexus.hud.save_current.title"),
                Lang.tr("vanta.nexus.hud.save_current.hint"), "", Lang.tr("vanta.common.save"), this::validateName,
                name -> {
                    HudPreset saved = hud.saveUserPreset(name);
                    services().notifications().post(NotificationKind.SUCCESS,
                            Lang.tr("vanta.hud.editor.preset_saved", saved.name()), "");
                    refresh();
                });
    }

    /** Opens the name prompt for a copy of a named layout. */
    public NameDialog duplicate(HudPreset preset) {
        String base = HudEditorSession.presetName(preset);
        return NameDialog.open(lastContext, Lang.tr("vanta.nexus.hud.duplicate.title", base),
                Lang.tr("vanta.nexus.hud.duplicate.hint"), Lang.tr("vanta.nexus.hud.copy_name", base),
                Lang.tr("vanta.common.duplicate"), this::validateName, name -> {
                    HudPreset saved = hud.saveUserPreset(name, preset.layout());
                    services().notifications().post(NotificationKind.SUCCESS,
                            Lang.tr("vanta.hud.editor.preset_saved", saved.name()), "");
                    refresh();
                });
    }

    /** Asks before deleting a user layout. */
    public Dialog delete(HudPreset preset) {
        return Dialog.confirm(lastContext, Lang.tr("vanta.nexus.hud.delete.title", preset.name()),
                Lang.tr("vanta.nexus.hud.delete.body"), Lang.tr("vanta.common.delete"), Lang.tr("vanta.common.cancel"),
                true, () -> {
                    if (hud.deleteUserPreset(preset.id())) {
                        services().notifications().post(NotificationKind.INFO,
                                Lang.tr("vanta.hud.editor.preset_deleted", preset.name()), "");
                    }
                    refresh();
                });
    }

    /** Applies the selected Nexus preset and saves the result as a new named layout. */
    public NameDialog newFromPreset() {
        NexusHudPreset preset = newFromPreset.value();
        String base = Lang.tr(preset.langKey());
        return NameDialog.open(lastContext, Lang.tr("vanta.nexus.hud.new_from_preset.title", base),
                Lang.tr("vanta.nexus.hud.new_from_preset.hint"), base, Lang.tr("vanta.nexus.hud.create"),
                this::validateName, name -> {
                    applyPreset(preset);
                    HudPreset saved = hud.saveUserPreset(name);
                    services().notifications().post(NotificationKind.SUCCESS,
                            Lang.tr("vanta.hud.editor.preset_saved", saved.name()), "");
                    refresh();
                });
    }

    /** The translation key of a problem with a layout name, or empty when fine. */
    public Optional<String> validateName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return Optional.of("vanta.nexus.hud.name_blank");
        }
        if (trimmed.length() > HudStore.MAX_PRESET_NAME) {
            return Optional.of("vanta.nexus.hud.name_long");
        }
        for (HudPreset preset : hud.allPresets()) {
            if (HudEditorSession.presetName(preset).equalsIgnoreCase(trimmed)) {
                return Optional.of("vanta.nexus.hud.name_taken");
            }
        }
        return Optional.empty();
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    /** Rebuilds the layout rows and the summary from the store. */
    public void refresh() {
        HudLayout live = hud.layout();
        int visible = 0;
        for (HudWidgetState widget : live.widgets()) {
            if (widget.enabled() && widget.type() != HudWidgetType.CROSSHAIR) {
                visible++;
            }
        }
        Optional<HudPreset> active = hud.activePreset();
        String name = active.map(HudEditorSession::presetName).orElse(Lang.tr("vanta.common.custom"));
        summary.setText(Lang.tr("vanta.nexus.hud.summary", name, visible, Math.max(0, live.size() - live.byType(
                HudWidgetType.CROSSHAIR).size())));
        layoutRows.clearChildren();
        rows.clear();
        for (HudPreset preset : hud.allPresets()) {
            Row row = new Row(Theme.SPACE_3).align(Align.CENTER);
            row.setId("nexus.hud.layout." + preset.id());
            Label label = new Label(HudEditorSession.presetName(preset), Label.Variant.TITLE);
            label.flex(1f);
            row.add(label);
            boolean isActive = active.map(a -> a.id().equals(preset.id())).orElse(false);
            if (isActive) {
                row.add(new Badge(Lang.tr("vanta.nexus.hud.active"), Badge.Tone.SUCCESS));
            } else if (preset.builtIn()) {
                row.add(new Badge(Lang.tr("vanta.profiles.builtin"), Badge.Tone.NEUTRAL));
            }
            Button load = Button.secondary(Lang.tr("vanta.nexus.hud.load"), () -> load(preset)).compact(true);
            load.setId("nexus.hud.layout.load." + preset.id());
            load.setEnabled(!isActive);
            row.add(load);
            IconButton duplicate = new IconButton(Icons.COPY, () -> duplicate(preset)).sizes(20, 10);
            duplicate.setTooltip(Lang.tr("vanta.common.duplicate"));
            duplicate.setId("nexus.hud.layout.duplicate." + preset.id());
            row.add(duplicate);
            if (!preset.builtIn()) {
                IconButton delete = new IconButton(Icons.TRASH, () -> delete(preset)).sizes(20, 10);
                delete.setTooltip(Lang.tr("vanta.common.delete"));
                delete.setId("nexus.hud.layout.delete." + preset.id());
                row.add(delete);
            }
            rows.add(row);
            layoutRows.add(row);
        }
        if (lastContext != null) {
            lastContext.requestLayout();
        }
    }

    @Override
    public void onShown(UiContext ctx) {
        lastContext = ctx;
        refresh();
    }

    @Override
    public void layout(UiContext ctx) {
        lastContext = ctx;
        super.layout(ctx);
    }

    @Override
    public void dispose() {
        unsubscribe.run();
    }
}
