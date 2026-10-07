package dev.vanta.core.screen.settings;

import dev.vanta.core.bridge.VanillaScreen;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.CategoryRail;
import dev.vanta.core.screen.common.ConfirmDialogs;
import dev.vanta.core.screen.common.EmptyState;
import dev.vanta.core.screen.common.LinkRow;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.SectionHeader;
import dev.vanta.core.screen.common.SettingRow;
import dev.vanta.core.screen.common.VantaUiScreen;
import dev.vanta.core.search.ActionEntry;
import dev.vanta.core.search.SearchHit;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingCategory;
import dev.vanta.core.settings.SettingsListener;
import dev.vanta.core.settings.SettingsRegistry;
import dev.vanta.core.settings.SettingsSearchIndex;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.VantaShell;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.widget.Badge;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.SearchField;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The VANTA settings screen: a category rail at the left, a live search field in the top bar that filters rows
 * across every category, a scrolling list of {@link SettingRow}s for the selected category (or the grouped search
 * results) and a footer with "Reset category", "Reset all" and a shortcut to the vanilla options screen.
 * <p>
 * Values are written through the {@link SettingsStore} as soon as a widget changes, so vanilla-bound rows edit the
 * real game options immediately; {@code settings.json} is saved with a short debounce and when the screen closes
 * (see {@link VantaUiScreen}). Deep links from the search overlay arrive through the {@link ScreenNavigator}: the
 * target category is selected, the row scrolled into view, highlighted and focused.
 */
public final class SettingsScreen extends VantaUiScreen {
    /** Most rows shown for a search query. */
    public static final int RESULT_LIMIT = 60;
    /** Width of the search field in the top bar. */
    public static final int SEARCH_W = 150;

    private final SettingsStore store;
    private final SettingsRegistry registry;
    private final SettingsSearchIndex index;
    private final SettingsListener externalChange = change -> refreshRows();
    private final List<SettingRow> currentRows = new ArrayList<>();
    private VantaShell shell;
    private SearchField search;
    private CategoryHeader header;
    private Column rows;
    private ScrollPanel scroll;
    private Button resetCategoryButton;
    private Button resetAllButton;
    private Button openVanillaButton;
    private SettingCategory category = SettingCategory.GENERAL;
    private String query = "";
    private String pendingReveal;

    public SettingsScreen(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, ScreenId.SETTINGS);
        this.store = services.settings();
        this.registry = services.settingsRegistry();
        this.index = new SettingsSearchIndex(registry);
        navigator.takePendingCategory().ifPresent(c -> category = c);
        this.pendingReveal = navigator.takePendingSetting().orElse(null);
        // A Max Framerate / VSync change made in vanilla Video Settings or Sodium shows up in the frame-rate row too
        // (settings.json only; no game option is written).
        services.performance().reconcileFpsLimitChoice();
    }

    // ---------------------------------------------------------------- accessors (tests)

    /** Selected category. */
    public SettingCategory category() {
        return category;
    }

    /** Current filter text. */
    public String query() {
        return query;
    }

    /** Rows currently shown, top to bottom. */
    public List<SettingRow> visibleRows() {
        return List.copyOf(currentRows);
    }

    /** The row of a setting if it is shown. */
    public Optional<SettingRow> rowFor(String settingId) {
        for (SettingRow row : currentRows) {
            if (row.setting().id().equals(settingId)) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    public SearchField searchField() {
        return search;
    }

    public Button resetCategoryButton() {
        return resetCategoryButton;
    }

    public Button resetAllButton() {
        return resetAllButton;
    }

    public Button openVanillaButton() {
        return openVanillaButton;
    }

    public ScrollPanel scrollPanel() {
        return scroll;
    }

    public VantaShell shell() {
        return shell;
    }

    // ---------------------------------------------------------------- build

    @Override
    protected UiNode build(UiContext ctx) {
        shell = new VantaShell(Lang.tr("vanta.settings.title"), this::close);
        shell.backTooltip(Lang.tr("vanta.common.back"));
        shell.rail(CategoryRail.items(), category.id(),
                id -> CategoryRail.category(id).ifPresent(this::selectCategory));

        search = new SearchField(Lang.tr("vanta.settings.search_placeholder"));
        search.width(SEARCH_W);
        search.setId("settings.search");
        search.onChange(this::setQuery);
        shell.rightSlot(search);

        Column content = new Column(Theme.SPACE_4);
        header = content.add(new CategoryHeader());
        rows = new Column(0);
        scroll = new ScrollPanel(rows).edgeFade(Colors.withAlpha(ctx.theme().bgBase(), 0.92f));
        scroll.flex(1f);
        content.add(scroll);
        content.add(buildFooter());
        shell.content(content);
        rebuildRows();
        return shell;
    }

    private UiNode buildFooter() {
        resetCategoryButton = Button.secondary(resetCategoryLabel(), this::resetCategory).compact(true);
        resetCategoryButton.icon(Icons.RESET);
        resetCategoryButton.setId("settings.resetCategory");
        resetAllButton = Button.danger(Lang.tr("vanta.settings.reset_all"), this::confirmResetAll).compact(true);
        resetAllButton.setId("settings.resetAll");
        openVanillaButton = Button.ghost(Lang.tr("vanta.settings.open_vanilla_options"),
                () -> navigator().openVanilla(VanillaScreen.OPTIONS)).compact(true);
        openVanillaButton.icon(Icons.EXTERNAL_LINK);
        openVanillaButton.setId("settings.openVanilla");
        Label hint = new Label(Lang.tr("vanta.settings.autosave_hint"), Label.Variant.MUTED);
        return new Footer(resetCategoryButton, resetAllButton, hint, openVanillaButton);
    }

    /**
     * The footer: reset buttons at the left, the autosave hint and the vanilla shortcut at the right. When the three
     * buttons do not fit on one line (a 320 px window, large text) the vanilla shortcut moves to a second line; the
     * hint is dropped before any button would be, so every button stays on screen and nothing overlaps.
     */
    static final class Footer extends UiNode {
        private static final int GAP = Theme.SPACE_3;
        private static final int ROW_GAP = Theme.SPACE_2;

        private final List<Button> left;
        private final Label hint;
        private final Button right;
        private int lastRows = -1;

        Footer(Button resetCategory, Button resetAll, Label hint, Button openVanilla) {
            this.left = List.of(resetCategory, resetAll);
            this.hint = hint;
            this.right = openVanilla;
            add(resetCategory);
            add(resetAll);
            add(hint);
            add(openVanilla);
        }

        private int leftWidth(UiContext ctx) {
            int w = 0;
            int visible = 0;
            for (Button button : left) {
                if (button.isVisible()) {
                    w += button.preferredSize(ctx).w();
                    visible++;
                }
            }
            return w + Math.max(0, visible - 1) * GAP;
        }

        private int rowsFor(UiContext ctx, int width) {
            return leftWidth(ctx) + GAP + right.preferredSize(ctx).w() <= width ? 1 : 2;
        }

        @Override
        protected Size measure(UiContext ctx) {
            // The width is only known once laid out; the first pass measures against the screen and asks for
            // another pass from layout() when the line count changed (like ResponsiveGrid).
            int w = bounds().w() > 0 ? bounds().w() : ctx.screenWidth();
            int rows = rowsFor(ctx, w);
            return new Size(w, rows * Button.HEIGHT + (rows - 1) * ROW_GAP);
        }

        @Override
        public void layout(UiContext ctx) {
            Rect b = bounds();
            int rows = rowsFor(ctx, b.w());
            if (rows != lastRows) {
                ctx.requestLayout();
            }
            lastRows = rows;
            int x = b.x();
            for (Button button : left) {
                if (!button.isVisible()) {
                    continue;
                }
                Size s = button.preferredSize(ctx);
                button.setBounds(x, b.y(), s.w(), Button.HEIGHT);
                button.layout(ctx);
                x += s.w() + GAP;
            }
            int lineY = rows == 1 ? b.y() : b.y() + Button.HEIGHT + ROW_GAP;
            int lineLeft = rows == 1 ? x : b.x();
            Size rs = right.preferredSize(ctx);
            right.setBounds(b.right() - rs.w(), lineY, rs.w(), Button.HEIGHT);
            right.layout(ctx);
            Size hs = hint.preferredSize(ctx);
            boolean hintFits = lineLeft + hs.w() + GAP <= right.bounds().x();
            hint.setVisible(hintFits);
            if (hintFits) {
                hint.setBounds(right.bounds().x() - GAP - hs.w(), lineY + (Button.HEIGHT - hs.h()) / 2, hs.w(), hs.h());
                hint.layout(ctx);
            }
        }
    }

    private String resetCategoryLabel() {
        return Lang.tr("vanta.settings.reset_category", Lang.tr(category.langKey()));
    }

    @Override
    protected void onScreenInit() {
        store.addListener(externalChange);
        if (pendingReveal != null) {
            reveal(pendingReveal);
            pendingReveal = null;
        }
    }

    @Override
    protected void onScreenClose() {
        store.removeListener(externalChange);
    }

    // ---------------------------------------------------------------- state changes

    /** Shows a category (clears the search filter). */
    public void selectCategory(SettingCategory next) {
        if (next == null) {
            return;
        }
        category = next;
        if (shell != null) {
            shell.selectRail(context(), next.id());
        }
        if (!query.isEmpty() && search != null) {
            // The rail click means "show me this category": leave filter mode.
            search.setText("");
            return; // onChange("") rebuilds the rows
        }
        rebuildRows();
        if (scroll != null) {
            scroll.setScrollY(context(), 0f, false);
        }
    }

    /** Sets the filter text; a blank query returns to the category view. */
    public void setQuery(String text) {
        String next = text == null ? "" : text.trim();
        if (next.equals(query)) {
            return;
        }
        query = next;
        rebuildRows();
        if (scroll != null) {
            scroll.setScrollY(context(), 0f, false);
        }
    }

    /**
     * Scrolls to the row of {@code settingId} (switching to its category first), flashes it and focuses its editor.
     *
     * @return true when the setting exists
     */
    public boolean reveal(String settingId) {
        Optional<Setting<?>> setting = registry.find(settingId);
        if (setting.isEmpty()) {
            return false;
        }
        if (!query.isEmpty() && search != null) {
            query = "";
            search.setText("");
        }
        if (category != setting.get().category()) {
            category = setting.get().category();
            if (shell != null) {
                shell.selectRail(context(), category.id());
            }
        }
        rebuildRows();
        Optional<SettingRow> row = rowFor(settingId);
        if (row.isEmpty()) {
            return false;
        }
        UiContext ctx = context();
        // Lay out now so the row has bounds to scroll to.
        root().setBounds(0, 0, width(), height());
        root().layout(ctx);
        scroll.scrollIntoView(ctx, row.get().bounds());
        row.get().flash(ctx);
        row.get().editor().requestFocus(ctx);
        return true;
    }

    private void resetCategory() {
        int changed = store.resetCategory(category);
        refreshRows();
        services().notifications().post(NotificationKind.INFO, resetCategoryLabel(),
                Lang.tr("vanta.settings.category_reset_done", changed));
    }

    private void confirmResetAll() {
        ConfirmDialogs.resetAllSettings(context(), () -> {
            services().runAction(ActionEntry.RESET_SETTINGS);
            refreshRows();
        });
    }

    /** Re-reads every visible row's value (after resets or external changes). */
    public void refreshRows() {
        for (SettingRow row : currentRows) {
            row.refresh();
        }
    }

    // ---------------------------------------------------------------- rows

    private void rebuildRows() {
        if (rows == null) {
            return;
        }
        rows.clearChildren();
        currentRows.clear();
        if (query.isEmpty()) {
            List<Setting<?>> settings = registry.byCategory(category);
            for (int i = 0; i < settings.size(); i++) {
                addRow(settings.get(i), false);
            }
            for (UiNode extra : extraRows(category)) {
                rows.add(extra);
            }
            if (!currentRows.isEmpty() && extraRows(category).isEmpty()) {
                currentRows.get(currentRows.size() - 1).last(true);
            }
        } else {
            Map<SettingCategory, List<SearchHit<Setting<?>>>> grouped = groupHits(index.search(query, RESULT_LIMIT));
            if (grouped.isEmpty()) {
                rows.add(new EmptyState(Icons.SEARCH, Lang.tr("vanta.settings.no_results", query),
                        Lang.tr("vanta.settings.no_results_hint")).height(120));
            }
            for (Map.Entry<SettingCategory, List<SearchHit<Setting<?>>>> group : grouped.entrySet()) {
                SectionHeader section = new SectionHeader(Lang.tr(group.getKey().langKey()))
                        .trailing(Lang.tr(group.getKey().descriptionKey()));
                section.height(SectionHeader.HEIGHT + Theme.SPACE_3);
                rows.add(section);
                List<SearchHit<Setting<?>>> hits = group.getValue();
                for (int i = 0; i < hits.size(); i++) {
                    addRow(hits.get(i).item(), i == hits.size() - 1);
                }
            }
        }
        header.refresh();
        if (resetCategoryButton != null) {
            resetCategoryButton.setLabel(resetCategoryLabel());
            resetCategoryButton.setVisible(query.isEmpty());
        }
        invalidateLayout();
    }

    private void addRow(Setting<?> setting, boolean last) {
        SettingRow row = new SettingRow(setting, store, id -> actions().dispatch(context(), id));
        row.last(last);
        row.onChanged(header::refresh);
        rows.add(row);
        currentRows.add(row);
    }

    /** Groups hits by category in rail order, keeping the score order inside a group. */
    static Map<SettingCategory, List<SearchHit<Setting<?>>>> groupHits(List<SearchHit<Setting<?>>> hits) {
        Map<SettingCategory, List<SearchHit<Setting<?>>>> byCategory = new EnumMap<>(SettingCategory.class);
        for (SearchHit<Setting<?>> hit : hits) {
            byCategory.computeIfAbsent(hit.item().category(), c -> new ArrayList<>()).add(hit);
        }
        Map<SettingCategory, List<SearchHit<Setting<?>>>> ordered = new LinkedHashMap<>();
        for (SettingCategory c : SettingCategory.values()) {
            List<SearchHit<Setting<?>>> list = byCategory.get(c);
            if (list != null) {
                ordered.put(c, list);
            }
        }
        return ordered;
    }

    /** Navigation rows appended below the settings of a category (deep links to editors and vanilla screens). */
    private List<UiNode> extraRows(SettingCategory c) {
        List<UiNode> out = new ArrayList<>();
        switch (c) {
            case CONTROLS -> out.add(new LinkRow(Icons.KEYBOARD, Lang.tr("vanta.settings.vanilla_controls"),
                    Lang.tr("vanta.settings.vanilla_controls.description"),
                    () -> navigator().openVanilla(VanillaScreen.CONTROLS)).external(true).id("link.vanilla_controls"));
            case PERFORMANCE, VIDEO -> out.add(new LinkRow(Icons.DOWNLOAD, Lang.tr("vanta.action.open_mods"),
                    Lang.tr("vanta.action.open_mods.description"),
                    () -> navigator().openScreen(context(), ScreenId.MODS)).id("link.mods"));
            case HUD -> out.add(new LinkRow(Icons.CROSSHAIR, Lang.tr("vanta.action.open_crosshair"),
                    Lang.tr("vanta.action.open_crosshair.description"),
                    () -> navigator().openScreen(context(), ScreenId.CROSSHAIR)).id("link.crosshair"));
            case COSMETICS -> out.add(new LinkRow(Icons.PALETTE, Lang.tr("vanta.action.open_cosmetics"),
                    Lang.tr("vanta.action.open_cosmetics.description"),
                    () -> navigator().openScreen(context(), ScreenId.COSMETICS)).id("link.cosmetics"));
            case ACCESSIBILITY -> out.add(new LinkRow(Icons.ACCESSIBILITY, Lang.tr("vanta.screen.accessibility"),
                    Lang.tr("vanta.screen.accessibility.description"),
                    () -> navigator().openScreen(context(), ScreenId.ACCESSIBILITY)).id("link.accessibility"));
            case PRIVACY -> out.add(new LinkRow(Icons.CHART, Lang.tr("vanta.screen.statistics"),
                    Lang.tr("vanta.screen.statistics.description"),
                    () -> navigator().openScreen(context(), ScreenId.STATISTICS)).id("link.statistics"));
            default -> {
            }
        }
        return out;
    }

    /** Number of settings in a category whose value differs from the default. */
    public int modifiedCount(SettingCategory c) {
        int n = 0;
        for (Setting<?> setting : registry.byCategory(c)) {
            if (setting.kind() != dev.vanta.core.settings.SettingKind.ACTION && !store.isDefault(setting)) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- keys

    @Override
    protected boolean onKeyDown(int key, int scancode, int mods) {
        if (key == Keys.F && Keys.hasControl(mods)) {
            search.requestFocus(context());
            search.selectAll();
            return true;
        }
        return super.onKeyDown(key, scancode, mods);
    }

    // ---------------------------------------------------------------- header node

    /** Title line above the rows: category name + description, or the search summary, plus a modified badge. */
    private final class CategoryHeader extends UiNode {
        private static final int H = 26;
        private final Badge modified = new Badge("", Badge.Tone.ACCENT);

        CategoryHeader() {
            add(modified);
            setId("settings.header");
        }

        void refresh() {
            if (query.isEmpty()) {
                int n = modifiedCount(category);
                modified.setText(Lang.tr("vanta.settings.modified_count", n));
                modified.setVisible(n > 0);
            } else {
                modified.setVisible(false);
            }
        }

        private String title() {
            return query.isEmpty() ? Lang.tr(category.langKey()) : Lang.tr("vanta.settings.search_results");
        }

        private String subtitle() {
            if (query.isEmpty()) {
                return Lang.tr(category.descriptionKey());
            }
            return currentRows.isEmpty() ? Lang.tr("vanta.settings.no_results", query)
                    : Lang.tr("vanta.settings.results_count", currentRows.size());
        }

        @Override
        protected Size measure(UiContext ctx) {
            return new Size(200, H);
        }

        @Override
        public void layout(UiContext ctx) {
            Rect b = bounds();
            Size s = modified.preferredSize(ctx);
            modified.setBounds(b.right() - s.w(), b.y() + (H - s.h()) / 2 - 2, s.w(), s.h());
            modified.layout(ctx);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            int reserved = modified.isVisible() ? modified.bounds().w() + Theme.SPACE_4 : 0;
            int maxW = Math.max(0, b.w() - reserved);
            int y = b.y();
            canvas.text(canvas.textClipped(title(), maxW, FontKind.DISPLAY), b.x(), y - 2, theme.textPrimary(),
                    FontKind.DISPLAY, false);
            canvas.text(canvas.textClipped(subtitle(), maxW, FontKind.UI), b.x(),
                    y + canvas.lineHeight(FontKind.DISPLAY) - 1, theme.textMuted(), FontKind.UI, false);
        }
    }
}
