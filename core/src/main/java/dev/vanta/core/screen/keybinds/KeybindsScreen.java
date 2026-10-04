package dev.vanta.core.screen.keybinds;

import dev.vanta.core.bridge.KeyBinding;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.bridge.VanillaScreen;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.keybinds.ConflictDetector;
import dev.vanta.core.keybinds.KeybindModel;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.cosmetics.EmptyState;
import dev.vanta.core.screen.cosmetics.InfoBanner;
import dev.vanta.core.screen.cosmetics.SectionHeader;
import dev.vanta.core.screen.cosmetics.ThemedScreen;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.VantaShell;
import dev.vanta.core.ui.layout.Align;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.layout.Spacer;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Dialog;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.SearchField;
import dev.vanta.core.ui.widget.Toggle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Keybinds screen: a search field, a conflict banner with the "show only conflicts" switch, the modified count,
 * "Reset all keys" and a link to the vanilla controls screen above the category sections (VANTA first, then the
 * vanilla categories). Every change is written through the {@link KeybindModel} (and therefore the game's key
 * mappings) immediately; the rows are rebuilt from the refreshed model afterwards so conflicts stay current.
 */
public final class KeybindsScreen extends ThemedScreen {
    private final KeybindModel model;
    private KeybindFilter filter = KeybindFilter.NONE;
    private VantaShell shell;
    private SearchField search;
    private InfoBanner conflictBanner;
    private Toggle conflictsOnly;
    private Label modifiedLabel;
    private Button resetAll;
    private Button vanillaControls;
    private Column list;
    private ScrollPanel scroll;
    private EmptyState empty;
    private final List<KeybindRow> rows = new ArrayList<>();
    private int lastConflictCount;

    public KeybindsScreen(VantaServices services) {
        super(services, ScreenId.KEYBINDS);
        this.model = services.keybinds();
    }

    @Override
    protected UiNode build(UiContext ctx) {
        model.refresh();
        lastConflictCount = model.conflicts().size();
        shell = newShell();
        search = new SearchField(Lang.tr("vanta.keybinds.search"));
        search.width(150);
        search.setId("keybinds.search");
        search.onChange(q -> {
            filter = filter.withQuery(q);
            rebuildList();
        });
        shell.rightSlot(search);

        Column content = new Column(Theme.SPACE_4);
        conflictBanner = new InfoBanner(InfoBanner.Tone.WARNING, "", "");
        conflictsOnly = new Toggle(Lang.tr("vanta.keybinds.show_only_conflicts"), false, on -> {
            filter = filter.withConflictsOnly(on);
            rebuildList();
        });
        conflictsOnly.setId("keybinds.conflicts-only");
        conflictBanner.action(conflictsOnly);
        content.add(conflictBanner);

        Row toolbar = new Row(Theme.SPACE_3).align(Align.CENTER);
        modifiedLabel = new Label("", Label.Variant.MUTED);
        toolbar.add(modifiedLabel);
        toolbar.add(Spacer.grow());
        vanillaControls = Button.ghost(Lang.tr("vanta.keybinds.open_vanilla"),
                () -> services().game().openVanillaScreen(VanillaScreen.CONTROLS)).icon(Icons.EXTERNAL_LINK)
                .compact(true);
        vanillaControls.setTooltip(Lang.tr("vanta.keybinds.open_vanilla.tooltip"));
        vanillaControls.setId("keybinds.vanilla");
        toolbar.add(vanillaControls);
        resetAll = Button.danger(Lang.tr("vanta.keybinds.reset_all"), this::confirmResetAll).icon(Icons.RESET)
                .compact(true);
        resetAll.setId("keybinds.reset-all");
        toolbar.add(resetAll);
        content.add(toolbar);

        list = new Column(Theme.SPACE_1);
        scroll = new ScrollPanel(list).edgeFade(ctx.theme().bgBase());
        scroll.flex(1f);
        content.add(scroll);
        empty = new EmptyState(Icons.SEARCH, Lang.tr("vanta.keybinds.no_matches"),
                Lang.tr("vanta.keybinds.no_matches.hint"));
        shell.content(content);
        rebuildList();
        return shell;
    }

    /** Re-reads the model and rebuilds banner, toolbar and rows. */
    public void rebuildList() {
        UiContext ctx = context();
        String focusedId = ctx != null && ctx.focus().focused() != null ? ctx.focus().focused().id() : null;
        List<ConflictDetector.Conflict> conflicts = model.conflicts();
        if (conflicts.isEmpty()) {
            conflictBanner.tone(InfoBanner.Tone.SUCCESS).setTitle(Lang.tr("vanta.keybinds.no_conflicts"))
                    .setBody(Lang.tr("vanta.keybinds.no_conflicts.body"));
            conflictsOnly.setEnabled(false);
            if (conflictsOnly.isOn()) {
                conflictsOnly.setOn(false);
                filter = filter.withConflictsOnly(false);
            }
        } else {
            int involved = ConflictDetector.involvedCount(conflicts);
            conflictBanner.tone(InfoBanner.Tone.WARNING)
                    .setTitle(Lang.tr("vanta.keybinds.conflicts", conflicts.size()))
                    .setBody(Lang.tr("vanta.keybinds.conflicts.body", involved));
            conflictsOnly.setEnabled(true);
        }
        int modified = model.modifiedCount();
        modifiedLabel.setText(modified == 0 ? Lang.tr("vanta.keybinds.all_default")
                : Lang.tr("vanta.keybinds.modified", modified));
        resetAll.setEnabled(modified > 0);

        list.clearChildren();
        rows.clear();
        List<KeybindModel.Category> sections = filter.sections(model);
        if (sections.isEmpty()) {
            empty.title(filter.conflictsOnly() && filter.query().isEmpty()
                    ? Lang.tr("vanta.keybinds.no_conflicts") : Lang.tr("vanta.keybinds.no_matches"));
            empty.hint(filter.conflictsOnly() && filter.query().isEmpty()
                    ? Lang.tr("vanta.keybinds.no_conflicts.body") : Lang.tr("vanta.keybinds.no_matches.hint"));
            list.add(empty);
        }
        for (KeybindModel.Category category : sections) {
            SectionHeader header = new SectionHeader(category.isVanta()
                    ? Lang.tr("vanta.keybinds.category_vanta") : category.name());
            header.trailing(Lang.tr("vanta.keybinds.key_count", category.bindings().size()));
            header.setId("keybinds.section." + category.id());
            list.add(header);
            for (KeyBinding binding : category.bindings()) {
                KeybindRow row = new KeybindRow(binding, model.conflictFor(binding.id()), this::bind, this::reset);
                rows.add(row);
                list.add(row);
            }
            list.add(Spacer.fixed(0, Theme.SPACE_2));
        }
        if (ctx != null) {
            ctx.requestLayout();
            if (focusedId != null) {
                UiNode again = list.findById(focusedId);
                if (again != null && again.canFocus()) {
                    ctx.focus().focus(ctx, again);
                }
            }
        }
    }

    /** Rebinds a mapping and refreshes; posts a toast when the change created a new conflict. */
    public void bind(String id, KeyRef key) {
        if (model.setKey(id, key)) {
            afterChange();
        }
    }

    /** Restores one default and refreshes. */
    public void reset(String id) {
        if (model.reset(id)) {
            afterChange();
        }
    }

    private void afterChange() {
        services().settings().saveIfDirty();
        int count = model.conflicts().size();
        if (count > lastConflictCount) {
            services().notifications().keybindConflict(ConflictDetector.involvedCount(model.conflicts()));
        }
        lastConflictCount = count;
        rebuildList();
    }

    /** Asks for confirmation, then restores every default. */
    public void confirmResetAll() {
        Dialog.confirm(context(), Lang.tr("vanta.keybinds.confirm_reset_all.title"),
                Lang.tr("vanta.keybinds.confirm_reset_all.body"), Lang.tr("vanta.keybinds.reset_all"),
                Lang.tr("vanta.common.cancel"), true, () -> {
                    model.resetAll();
                    afterChange();
                });
    }

    /** Clears filters, scrolls to the mapping and focuses its key field (deep link from search). */
    public boolean revealKeybind(String id) {
        if (model.find(id).isEmpty()) {
            return false;
        }
        if (!filter.isEmpty()) {
            filter = KeybindFilter.NONE;
            search.setText("");
            conflictsOnly.setOn(false);
            rebuildList();
        }
        Optional<KeybindRow> row = row(id);
        if (row.isEmpty()) {
            return false;
        }
        UiContext ctx = context();
        invalidateLayout();
        root().layout(ctx);
        row.get().field().requestFocus(ctx);
        return true;
    }

    /** Current filter. */
    public KeybindFilter filter() {
        return filter;
    }

    /** Applies a search query programmatically (hosts, previews). */
    public void setQuery(String query) {
        search.setText(query == null ? "" : query);
    }

    /** Rows currently shown. */
    public List<KeybindRow> rows() {
        return List.copyOf(rows);
    }

    /** The row of a mapping, if shown. */
    public Optional<KeybindRow> row(String id) {
        return rows.stream().filter(r -> r.binding().id().equals(id)).findFirst();
    }

    public InfoBanner conflictBanner() {
        return conflictBanner;
    }

    public Toggle conflictsOnlyToggle() {
        return conflictsOnly;
    }

    public Button resetAllButton() {
        return resetAll;
    }

    public SearchField searchField() {
        return search;
    }

    @Override
    protected void onThemeChanged(Theme theme) {
        scroll.edgeFade(theme.bgBase());
    }
}
