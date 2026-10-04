package dev.vanta.core.screen.packs;

import dev.vanta.core.bridge.PackInfo;
import dev.vanta.core.bridge.ResourcePackBridge;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ConfirmDialogs;
import dev.vanta.core.screen.common.EmptyState;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.VantaUiScreen;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.VantaShell;
import dev.vanta.core.ui.layout.Align;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.layout.Spacer;
import dev.vanta.core.ui.widget.Badge;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Card;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.SearchField;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resource pack manager: Available packs on the left, Selected (enabled) packs on the right with the highest
 * priority at the top; enable / disable buttons, reorder with the arrows, Shift+↑↓ or by dragging a row, a search
 * filter, shortcuts to the pack folder and the vanilla screen, and Apply, which reloads resources exactly like the
 * vanilla screen. Leaving with unapplied changes asks first.
 */
public final class ResourcePackScreen extends VantaUiScreen {
    private final ResourcePackBridge bridge;
    private final List<PackRow> availableRows = new ArrayList<>();
    private final List<PackRow> selectedRows = new ArrayList<>();
    private PackListModel model;
    private String filter = "";
    private String focusAfterRefresh;

    private VantaShell shell;
    private SearchField search;
    private Column availableColumn;
    private Column selectedColumn;
    private ScrollPanel availableScroll;
    private ScrollPanel selectedScroll;
    private Badge availableCount;
    private Badge selectedCount;
    private Badge pending;
    private Button discardButton;
    private Button applyButton;

    public ResourcePackScreen(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, ScreenId.RESOURCE_PACKS);
        this.bridge = services.resourcePacks();
        this.model = PackListModel.from(bridge.packs(), filter);
    }

    // ---------------------------------------------------------------- accessors (tests)

    public PackListModel model() {
        return model;
    }

    public List<PackRow> availableRows() {
        return List.copyOf(availableRows);
    }

    public List<PackRow> selectedRows() {
        return List.copyOf(selectedRows);
    }

    public Optional<PackRow> rowFor(String packId) {
        for (PackRow row : availableRows) {
            if (row.pack().id().equals(packId)) {
                return Optional.of(row);
            }
        }
        for (PackRow row : selectedRows) {
            if (row.pack().id().equals(packId)) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    public Button applyButton() {
        return applyButton;
    }

    public Button discardButton() {
        return discardButton;
    }

    public SearchField searchField() {
        return search;
    }

    public String filter() {
        return filter;
    }

    // ---------------------------------------------------------------- screen contract

    /** Escape only closes directly when nothing is pending; otherwise {@link #requestClose()} asks first. */
    @Override
    public boolean shouldCloseOnEsc() {
        return !bridge.hasPendingChanges();
    }

    /** Closes the screen, asking to discard unapplied changes first. */
    public void requestClose() {
        if (bridge.hasPendingChanges()) {
            ConfirmDialogs.discardChanges(context(), () -> {
                bridge.discard();
                close();
            });
        } else {
            close();
        }
    }

    @Override
    protected boolean onKeyDown(int key, int scancode, int mods) {
        if (key == Keys.ESCAPE) {
            requestClose();
            return true;
        }
        if (key == Keys.F && Keys.hasControl(mods)) {
            search.requestFocus(context());
            return true;
        }
        return super.onKeyDown(key, scancode, mods);
    }

    // ---------------------------------------------------------------- build

    @Override
    protected UiNode build(UiContext ctx) {
        shell = new VantaShell(Lang.tr("vanta.resourcepacks.title"), this::requestClose);
        shell.backTooltip(Lang.tr("vanta.common.back"));
        search = new SearchField(Lang.tr("vanta.resourcepacks.search_placeholder"));
        search.width(140);
        search.setId("packs.search");
        search.onChange(this::setFilter);
        shell.rightSlot(search);

        Column content = new Column(Theme.SPACE_4);
        content.add(new Label(Lang.tr("vanta.resourcepacks.top_hint"), Label.Variant.MUTED));

        Row lists = new Row(Theme.SPACE_4).align(Align.STRETCH);
        lists.flex(1f);
        availableColumn = new Column(0);
        selectedColumn = new Column(0);
        availableScroll = new ScrollPanel(availableColumn).edgeFade(Colors.withAlpha(ctx.theme().surface1(), 0.95f));
        selectedScroll = new ScrollPanel(selectedColumn).edgeFade(Colors.withAlpha(ctx.theme().surface1(), 0.95f));
        availableCount = new Badge("", Badge.Tone.NEUTRAL);
        selectedCount = new Badge("", Badge.Tone.ACCENT);
        Card available = listCard(Lang.tr("vanta.resourcepacks.available"), availableCount, availableScroll);
        available.setId("packs.available");
        Card selected = listCard(Lang.tr("vanta.resourcepacks.selected"), selectedCount, selectedScroll);
        selected.setId("packs.selected");
        available.flex(1f);
        selected.flex(1f);
        lists.add(available);
        lists.add(selected);
        content.add(lists);

        Row footer = new Row(Theme.SPACE_3);
        Button folder = Button.ghost(Lang.tr("vanta.resourcepacks.open_folder"), bridge::openPackFolder).compact(true);
        folder.icon(Icons.FOLDER);
        folder.setId("packs.openFolder");
        Button vanilla = Button.ghost(Lang.tr("vanta.resourcepacks.vanilla_screen"), bridge::openVanillaScreen).compact(true);
        vanilla.icon(Icons.EXTERNAL_LINK);
        vanilla.setId("packs.vanilla");
        pending = new Badge(Lang.tr("vanta.resourcepacks.pending"), Badge.Tone.WARNING);
        discardButton = Button.secondary(Lang.tr("vanta.resourcepacks.discard"), this::discard).compact(true);
        discardButton.setId("packs.discard");
        applyButton = Button.primary(Lang.tr("vanta.resourcepacks.apply"), this::apply);
        applyButton.icon(Icons.CHECK);
        applyButton.setId("packs.apply");
        footer.add(folder);
        footer.add(vanilla);
        footer.add(Spacer.grow());
        footer.add(pending);
        footer.add(discardButton);
        footer.add(applyButton);
        content.add(footer);

        shell.content(content);
        refresh();
        return shell;
    }

    private static Card listCard(String title, Badge count, ScrollPanel scroll) {
        Card card = new Card(title).headerSlot(count);
        card.body().padding(Theme.SPACE_2).gap(0);
        scroll.flex(1f);
        card.add(scroll);
        return card;
    }

    // ---------------------------------------------------------------- actions

    /** Narrows both columns. */
    public void setFilter(String text) {
        String next = text == null ? "" : text.trim();
        if (next.equals(filter)) {
            return;
        }
        filter = next;
        refresh();
    }

    /** Enables a disabled pack or disables an enabled one (required packs are ignored). */
    public void toggle(PackInfo pack) {
        if (pack.required()) {
            return;
        }
        if (bridge.setEnabled(pack.id(), !pack.enabled())) {
            focusAfterRefresh = pack.id();
            refresh();
        }
    }

    /** Moves an enabled pack ({@code +1} = higher priority = up). */
    public void move(PackInfo pack, int delta) {
        if (bridge.move(pack.id(), delta)) {
            focusAfterRefresh = pack.id();
            refresh();
        }
    }

    /** Applies the staged selection, reloads resources and confirms with a toast. */
    public void apply() {
        if (!bridge.hasPendingChanges()) {
            return;
        }
        bridge.apply();
        refresh();
        services().notifications().post(NotificationKind.SUCCESS, Lang.tr("vanta.resourcepacks.applied.title"),
                Lang.tr("vanta.resourcepacks.applied.body", model.selected().size()));
    }

    /** Drops the staged selection. */
    public void discard() {
        bridge.discard();
        refresh();
    }

    /** Re-reads the bridge and rebuilds both columns. */
    public void refresh() {
        model = PackListModel.from(bridge.packs(), filter);
        if (availableColumn == null) {
            return;
        }
        rebuild(availableColumn, availableRows, model.available(), false);
        rebuild(selectedColumn, selectedRows, model.selected(), true);
        availableCount.setText(Integer.toString(model.available().size()));
        selectedCount.setText(Integer.toString(model.selected().size()));
        boolean dirty = bridge.hasPendingChanges();
        pending.setVisible(dirty);
        applyButton.setEnabled(dirty);
        discardButton.setEnabled(dirty);
        invalidateLayout();
        if (focusAfterRefresh != null) {
            String id = focusAfterRefresh;
            focusAfterRefresh = null;
            UiContext ctx = context();
            if (ctx != null) {
                root().setBounds(0, 0, width(), height());
                root().layout(ctx);
                rowFor(id).ifPresent(row -> row.requestFocus(ctx));
            }
        }
    }

    private void rebuild(Column column, List<PackRow> rows, List<PackInfo> packs, boolean selectedList) {
        column.clearChildren();
        rows.clear();
        if (packs.isEmpty()) {
            String title;
            String hint;
            if (model.total() == 0) {
                title = Lang.tr("vanta.resourcepacks.none_available");
                hint = Lang.tr("vanta.resourcepacks.folder_hint");
            } else if (!filter.isEmpty()) {
                title = Lang.tr("vanta.resourcepacks.no_match", filter);
                hint = "";
            } else {
                title = Lang.tr(selectedList ? "vanta.resourcepacks.empty_selected" : "vanta.resourcepacks.empty_available");
                hint = Lang.tr(selectedList ? "vanta.resourcepacks.empty_selected_hint"
                        : "vanta.resourcepacks.empty_available_hint");
            }
            column.add(new EmptyState(Icons.PACKAGE, title, hint).height(96));
            return;
        }
        for (int i = 0; i < packs.size(); i++) {
            PackInfo pack = packs.get(i);
            PackRow row = new PackRow(pack, selectedList, model.canMoveUp(pack), model.canMoveDown(pack), this::toggle,
                    delta -> move(pack, delta));
            row.last(i == packs.size() - 1);
            rows.add(row);
            column.add(row);
        }
    }
}
