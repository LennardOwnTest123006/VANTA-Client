package dev.vanta.core.screen.mods;

import dev.vanta.core.bridge.VanillaScreen;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.modrinth.InstallRequest;
import dev.vanta.core.modrinth.InstallResult;
import dev.vanta.core.modrinth.InstalledEntry;
import dev.vanta.core.modrinth.LocalItem;
import dev.vanta.core.modrinth.ModrinthConstants;
import dev.vanta.core.modrinth.ModrinthException;
import dev.vanta.core.modrinth.ModrinthProjectType;
import dev.vanta.core.modrinth.ModrinthSearchHit;
import dev.vanta.core.modrinth.ModrinthSearchResult;
import dev.vanta.core.modrinth.ModrinthService;
import dev.vanta.core.modrinth.PerformancePack;
import dev.vanta.core.modrinth.PlanNote;
import dev.vanta.core.modrinth.SearchRequest;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.EmptyState;
import dev.vanta.core.screen.common.InfoBanner;
import dev.vanta.core.screen.common.ResponsiveGrid;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.VantaUiScreen;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
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
import dev.vanta.core.ui.widget.Dialog;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.SearchField;
import dev.vanta.core.ui.widget.Tabs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Mods &amp; Shaders: browse and install Modrinth projects for Minecraft 1.21.11.
 * <ul>
 *   <li>Tabs Mods (Fabric), Shaders (Iris), Resource packs and Installed; the search field queries Modrinth (debounced,
 *       Enter searches at once) or filters the Installed list. An empty query lists the most downloaded projects.</li>
 *   <li>Rows show a letter avatar, title, author, downloads, the description and whether the project is installed; the
 *       detail panel on the right shows the selected project with Install / Remove / Disable / Enable.</li>
 *   <li>The Performance pack card (Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling, Iris) installs the
 *       ticked members in one click.</li>
 *   <li>After a mod change a banner asks for a restart; "Restart game" appears when the VANTA launcher started the
 *       game (it relaunches after the restart marker is written), otherwise "Quit game".</li>
 * </ul>
 * All network work runs in {@link ModrinthService}; results arrive on the client thread. Progress and errors are shown
 * as VANTA notifications.
 */
public final class ModsScreen extends VantaUiScreen {
    /** Ticks of typing pause before a search is sent. */
    public static final int SEARCH_DEBOUNCE_TICKS = 6;
    private static final int DETAIL_W = 216;
    private static final int DETAIL_W_NARROW = 150;
    private static final int NARROW_WIDTH = 640;
    /**
     * Below this logical height the screen saves room: the detail description is cut to fewer lines, the detail
     * card has tighter padding, its actions share a line where they fit and the restart banner drops its body text.
     */
    private static final int SHORT_HEIGHT = 300;

    private final Optional<ModrinthService> service;
    private ModsTab tab = ModsTab.MODS;
    private String query = "";
    private int debounce = -1;
    private int searchSeq;
    private boolean searching;
    private ModrinthException searchError;
    private ModrinthSearchResult results = ModrinthSearchResult.empty();
    private SearchRequest lastRequest;
    private ModrinthSearchHit selectedHit;
    private String selectedKey;
    private List<LocalItem> localItems = List.of();
    private Set<String> installedSlugs = Set.of();
    private Set<String> installedIds = Set.of();
    private long seenChanges = -1;
    private boolean lastBusy;
    private final Set<String> packSelection = new LinkedHashSet<>(PerformancePack.slugs());

    private VantaShell shell;
    private SearchField search;
    private Tabs tabs;
    private InfoBanner restartBanner;
    private Button restartButton;
    private Column listColumn;
    private ScrollPanel listScroll;
    private Card detailCard;
    private Label scopeLabel;
    private Column detailBody;
    private ScrollPanel detailScroll;
    private Column detailFooter;
    private ScrollPanel detailFooterScroll;
    private final List<UiNode> pendingActions = new ArrayList<>();
    private Card packCard;
    private final List<PackItemToggle> packToggles = new ArrayList<>();
    private Button packInstall;
    private final List<ProjectRow> rows = new ArrayList<>();
    private Button loadMore;

    public ModsScreen(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, ScreenId.MODS);
        this.service = services.modrinth();
    }

    // ---------------------------------------------------------------- accessors (tests, previews)

    public ModsTab tab() {
        return tab;
    }

    public String query() {
        return query;
    }

    public boolean isSearching() {
        return searching;
    }

    public ModrinthSearchResult results() {
        return results;
    }

    public Optional<ModrinthException> searchError() {
        return Optional.ofNullable(searchError);
    }

    public List<ProjectRow> rows() {
        return List.copyOf(rows);
    }

    public Optional<ProjectRow> row(String key) {
        return rows.stream().filter(r -> r.model().key().equals(key)).findFirst();
    }

    public SearchField searchField() {
        return search;
    }

    public List<PackItemToggle> packToggles() {
        return List.copyOf(packToggles);
    }

    public Button packInstallButton() {
        return packInstall;
    }

    public InfoBanner restartBanner() {
        return restartBanner;
    }

    public Button restartButton() {
        return restartButton;
    }

    public Optional<String> selectedKey() {
        return Optional.ofNullable(selectedKey);
    }

    /** True when the Modrinth integration is installed (it always is in game). */
    public boolean isAvailable() {
        return service.isPresent();
    }

    // ---------------------------------------------------------------- build

    @Override
    protected UiNode build(UiContext ctx) {
        shell = new VantaShell(Lang.tr("vanta.mods.title"), this::close);
        shell.backTooltip(Lang.tr("vanta.common.back"));
        if (service.isEmpty()) {
            shell.content(new EmptyState(Icons.PACKAGE, Lang.tr("vanta.mods.unavailable.title"),
                    Lang.tr("vanta.mods.unavailable.hint")));
            return shell;
        }
        search = new SearchField(Lang.tr("vanta.mods.search_placeholder"));
        search.width(170);
        search.setId("mods.search");
        search.onChange(this::onQueryTyped);
        search.onSubmit(text -> searchNow());
        shell.rightSlot(search);

        Column content = new Column(Theme.SPACE_4);
        Row header = new Row(Theme.SPACE_4).align(Align.CENTER);
        List<String> labels = new ArrayList<>();
        for (ModsTab t : ModsTab.values()) {
            labels.add(Lang.tr(t.langKey()));
        }
        tabs = new Tabs(labels, tab.ordinal()).onSelect(i -> selectTab(ModsTab.values()[i]));
        tabs.setId("mods.tabs");
        header.add(tabs);
        header.add(Spacer.grow());
        scopeLabel = header.add(new Label(Lang.tr("vanta.mods.scope", ModrinthConstants.GAME_VERSION),
                Label.Variant.MUTED));
        content.add(header);

        restartButton = Button.primary(Lang.tr("vanta.mods.restart.quit"), this::restart).compact(true);
        restartButton.setId("mods.restart");
        restartBanner = new InfoBanner(InfoBanner.Tone.WARNING, Lang.tr("vanta.mods.restart.title"),
                Lang.tr("vanta.mods.restart.body")).icon(Icons.RESET).action(restartButton);
        restartBanner.setId("mods.restartBanner");
        content.add(restartBanner);

        Row body = new Row(Theme.SPACE_4).align(Align.STRETCH);
        body.flex(1f);
        listColumn = new Column(Theme.SPACE_2);
        listScroll = new ScrollPanel(listColumn).edgeFade(Colors.withAlpha(ctx.theme().surface1(), 0.95f));
        listScroll.flex(1f);
        Card listCard = new Card().clip(true);
        listCard.body().padding(Theme.SPACE_2).gap(0);
        listCard.add(listScroll);
        listCard.flex(1f);
        listCard.setId("mods.list");
        // The detail text scrolls; the action buttons sit in a footer below it so they are always visible and
        // clickable, however small the window is (see DetailStack for the case where even the footer is too tall).
        detailCard = new Card().clip(true);
        detailCard.setId("mods.detail");
        int fade = Colors.withAlpha(ctx.theme().panelBackground(), 0.95f);
        detailBody = new Column(Theme.SPACE_3);
        detailScroll = new ScrollPanel(detailBody).edgeFade(fade);
        detailFooter = new Column(Theme.SPACE_3);
        detailFooterScroll = new ScrollPanel(detailFooter).edgeFade(fade);
        DetailStack stack = new DetailStack(detailScroll, detailFooterScroll, Theme.SPACE_4);
        stack.flex(1f);
        detailCard.add(stack);
        body.add(listCard);
        body.add(detailCard);
        content.add(body);
        shell.content(content);

        buildPackCard();
        applyDetailSize();
        refreshInstalled();
        return shell;
    }

    private void buildPackCard() {
        packCard = new Card(Lang.tr("vanta.mods.pack.title")).caption(Lang.tr("vanta.mods.pack.caption"))
                .headerSlot(new Badge(Lang.tr("vanta.mods.pack.badge"), Badge.Tone.ACCENT));
        packCard.setId("mods.pack");
        packCard.body().gap(Theme.SPACE_3);
        ResponsiveGrid grid = new ResponsiveGrid(150, 3, Theme.SPACE_2);
        for (PerformancePack.Item item : PerformancePack.ITEMS) {
            PackItemToggle toggle = new PackItemToggle(item, packSelection.contains(item.slug()), t -> {
                if (t.checkedFlag()) {
                    packSelection.add(t.item().slug());
                } else {
                    packSelection.remove(t.item().slug());
                }
                refreshPackCard();
            });
            toggle.setTooltip(Lang.tr(item.descriptionKey()));
            packToggles.add(toggle);
            grid.add(toggle);
        }
        packCard.add(grid);
        Row actions = new Row(Theme.SPACE_4).align(Align.CENTER);
        packInstall = Button.primary(Lang.tr("vanta.mods.pack.install"), this::installPack);
        packInstall.icon(Icons.DOWNLOAD);
        packInstall.setId("mods.pack.install");
        actions.add(packInstall);
        Label hint = new Label(Lang.tr("vanta.mods.pack.hint"), Label.Variant.MUTED);
        hint.flex(1f);
        actions.add(hint);
        packCard.add(actions);
    }

    @Override
    protected void onScreenInit() {
        if (service.isEmpty()) {
            return;
        }
        seenChanges = service.get().changeCount();
        runSearch(false);
    }

    @Override
    protected void onScreenResize() {
        applyDetailSize();
        if (service.isPresent()) {
            refreshInstalled();
        }
    }

    private void applyDetailSize() {
        if (detailCard != null) {
            detailCard.width(width() < NARROW_WIDTH ? DETAIL_W_NARROW : DETAIL_W);
            // A short window needs every pixel of the card for the text and the footer buttons.
            detailCard.body().padding(height() < SHORT_HEIGHT ? Theme.SPACE_4 : Theme.SPACE_5);
            scopeLabel.setVisible(width() >= NARROW_WIDTH);
            invalidateLayout();
        }
    }

    @Override
    protected void onScreenTick() {
        if (service.isEmpty()) {
            return;
        }
        if (debounce > 0 && --debounce == 0) {
            debounce = -1;
            runSearch(false);
        }
        long changes = service.get().changeCount();
        if (changes != seenChanges) {
            seenChanges = changes;
            refreshInstalled();
        }
        boolean busy = service.get().isBusy();
        if (busy != lastBusy) {
            lastBusy = busy;
            refreshPackCard();
            updateRestartBanner();
        }
    }

    @Override
    protected boolean onKeyDown(int key, int scancode, int mods) {
        if (search != null && key == Keys.F && Keys.hasControl(mods)) {
            search.requestFocus(context());
            search.selectAll();
            return true;
        }
        return super.onKeyDown(key, scancode, mods);
    }

    // ---------------------------------------------------------------- tabs & search

    /** Switches tab (and searches or lists installed items). */
    public void selectTab(ModsTab next) {
        if (next == tab) {
            return;
        }
        tab = next;
        if (tabs != null && tabs.selected() != next.ordinal()) {
            tabs.select(context(), next.ordinal());
        }
        selectedHit = null;
        selectedKey = null;
        searchError = null;
        results = ModrinthSearchResult.empty();
        if (listScroll != null && context() != null) {
            listScroll.setScrollY(context(), 0f, false);
        }
        if (tab == ModsTab.INSTALLED) {
            searching = false;
            refreshInstalled();
        } else {
            runSearch(false);
        }
    }

    private void onQueryTyped(String text) {
        String next = text == null ? "" : text.trim();
        if (next.equals(query)) {
            return;
        }
        query = next;
        if (tab == ModsTab.INSTALLED) {
            rebuildList();
        } else {
            debounce = SEARCH_DEBOUNCE_TICKS;
        }
    }

    /** Sets the query and searches immediately (tests, previews, Enter). */
    public void setQuery(String text) {
        query = text == null ? "" : text.trim();
        if (search != null && !search.text().equals(query)) {
            search.setText(query);
        }
        searchNow();
    }

    private void searchNow() {
        debounce = -1;
        if (search != null) {
            query = search.text().trim();
        }
        if (tab == ModsTab.INSTALLED) {
            rebuildList();
        } else {
            runSearch(false);
        }
    }

    private void runSearch(boolean nextPage) {
        if (service.isEmpty() || tab.type().isEmpty()) {
            return;
        }
        SearchRequest request = nextPage && lastRequest != null ? lastRequest.nextPage()
                : SearchRequest.of(query, tab.type().get());
        int seq = ++searchSeq;
        searching = true;
        if (!nextPage) {
            searchError = null;
        }
        rebuildList();
        service.get().search(request, result -> {
            if (seq != searchSeq) {
                return;
            }
            searching = false;
            searchError = null;
            lastRequest = request;
            results = nextPage ? results.append(result) : result;
            rebuildList();
            if (selectedHit == null) {
                rebuildDetail();
            }
        }, error -> {
            if (seq != searchSeq) {
                return;
            }
            searching = false;
            searchError = error;
            rebuildList();
        });
    }

    /** Loads the next page of results. */
    public void loadMore() {
        if (!searching && results.hasMore()) {
            runSearch(true);
        }
    }

    // ---------------------------------------------------------------- installed state

    private void refreshInstalled() {
        if (service.isEmpty()) {
            return;
        }
        seenChanges = service.get().changeCount();
        localItems = service.get().installedItems();
        Set<String> slugs = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (LocalItem item : localItems) {
            item.entry().ifPresent(e -> {
                slugs.add(e.slug());
                ids.add(e.projectId());
            });
        }
        installedSlugs = slugs;
        installedIds = ids;
        updateRestartBanner();
        refreshPackCard();
        rebuildList();
        rebuildDetail();
    }

    private void updateRestartBanner() {
        if (restartBanner == null) {
            return;
        }
        ModrinthService s = service.get();
        boolean show = s.restartRequired();
        restartBanner.setVisible(show);
        boolean inWorld = services().game().isInWorld();
        // Quitting while a download is still running would abandon it (the worker is a daemon thread) and never
        // write the index; tick() refreshes the banner when the busy state flips.
        boolean busy = s.isBusy();
        restartButton.setLabel(Lang.tr(s.launcherRestartable() ? "vanta.mods.restart.restart" : "vanta.mods.restart.quit"));
        restartButton.setEnabled(!inWorld && !busy);
        String body = Lang.tr(inWorld ? "vanta.mods.restart.body_in_world"
                : busy ? "vanta.mods.restart.body_busy"
                : s.launcherRestartable() ? "vanta.mods.restart.body_launcher" : "vanta.mods.restart.body");
        // A short window cannot spare the banner's text lines: title and button stay, the text becomes the tooltip.
        boolean shortScreen = height() < SHORT_HEIGHT;
        restartBanner.setBody(shortScreen ? "" : body);
        restartBanner.setTooltip(shortScreen ? body : null);
        invalidateLayout();
    }

    private void refreshPackCard() {
        if (packToggles.isEmpty()) {
            return;
        }
        ModrinthService s = service.get();
        int toInstall = 0;
        int present = 0;
        for (PackItemToggle toggle : packToggles) {
            PerformancePack.Item item = toggle.item();
            boolean loaded = s.platform().isModLoaded(item.modId());
            boolean installed = installedSlugs.contains(item.slug());
            String state = loaded ? Lang.tr("vanta.mods.pack.state.active")
                    : installed ? Lang.tr("vanta.mods.pack.state.installed")
                    : Lang.tr("vanta.mods.pack.state.missing");
            toggle.state(loaded || installed, state);
            if (loaded || installed) {
                present++;
            } else if (toggle.isChecked()) {
                toInstall++;
            }
        }
        if (present == packToggles.size()) {
            packInstall.setLabel(Lang.tr("vanta.mods.pack.all_installed"));
            packInstall.setEnabled(false);
        } else {
            int remaining = packToggles.size() - present;
            packInstall.setLabel(toInstall == 0 || toInstall == remaining
                    ? Lang.tr("vanta.mods.pack.install") : Lang.tr("vanta.mods.pack.install_count", toInstall));
            packInstall.setEnabled(toInstall > 0 && !s.isBusy());
        }
        invalidateLayout();
    }

    // ---------------------------------------------------------------- list

    private void rebuildList() {
        if (listColumn == null) {
            return;
        }
        listColumn.clearChildren();
        rows.clear();
        loadMore = null;
        if (tab == ModsTab.MODS) {
            listColumn.add(packCard);
        }
        if (tab == ModsTab.SHADERS && !service.get().irisLoaded()) {
            Button installIris = Button.secondary(Lang.tr("vanta.mods.iris.install"), this::installIris).compact(true);
            installIris.setId("mods.installIris");
            installIris.setEnabled(!installedSlugs.contains("iris"));
            listColumn.add(new InfoBanner(InfoBanner.Tone.INFO, Lang.tr("vanta.mods.iris.title"),
                    Lang.tr(installedSlugs.contains("iris") ? "vanta.mods.iris.restart" : "vanta.mods.iris.body"))
                    .action(installIris));
        }
        if (tab == ModsTab.INSTALLED) {
            buildInstalledRows();
        } else {
            buildResultRows();
        }
        markSelection();
        settleLayout();
    }

    private void buildResultRows() {
        String heading;
        if (searching && results.hits().isEmpty()) {
            heading = Lang.tr("vanta.mods.searching");
        } else if (query.isEmpty()) {
            heading = Lang.tr("vanta.mods.popular." + tab.type().orElseThrow().apiName());
        } else {
            heading = Lang.tr("vanta.mods.results", ModsFormats.compact(results.totalHits()), query);
        }
        Label title = new Label(heading, Label.Variant.CAPTION);
        title.setId("mods.heading");
        listColumn.add(title);
        if (searchError != null && results.hits().isEmpty()) {
            listColumn.add(new EmptyState(Icons.WARNING, Lang.tr(searchError.kind().langKey()),
                    Lang.tr("vanta.mods.search_failed_hint")).height(80));
            Button retry = Button.secondary(Lang.tr("vanta.mods.retry"), () -> runSearch(false)).compact(true);
            retry.setId("mods.retry");
            listColumn.add(retry);
            return;
        }
        if (searching && results.hits().isEmpty()) {
            listColumn.add(new EmptyState(Icons.SEARCH, Lang.tr("vanta.mods.searching"), "").height(80));
            return;
        }
        if (results.hits().isEmpty()) {
            listColumn.add(new EmptyState(Icons.SEARCH, Lang.tr("vanta.mods.no_results", query),
                    Lang.tr("vanta.mods.no_results_hint")).height(80));
            return;
        }
        List<ModrinthSearchHit> hits = results.hits();
        for (int i = 0; i < hits.size(); i++) {
            ModrinthSearchHit hit = hits.get(i);
            ProjectRow row = new ProjectRow(model(hit), r -> selectHit(hit)).last(i == hits.size() - 1);
            rows.add(row);
            listColumn.add(row);
        }
        if (results.hasMore()) {
            loadMore = Button.ghost(searching ? Lang.tr("vanta.mods.searching") : Lang.tr("vanta.mods.load_more"),
                    this::loadMore).compact(true);
            loadMore.setId("mods.loadMore");
            loadMore.setEnabled(!searching);
            listColumn.add(loadMore);
        }
    }

    private void buildInstalledRows() {
        String filter = query.toLowerCase(Locale.ROOT);
        List<LocalItem> visible = new ArrayList<>();
        for (LocalItem item : localItems) {
            if (filter.isEmpty() || item.name().toLowerCase(Locale.ROOT).contains(filter)
                    || item.relativeFile().toLowerCase(Locale.ROOT).contains(filter)) {
                visible.add(item);
            }
        }
        Label title = new Label(Lang.tr("vanta.mods.installed.heading", visible.size()), Label.Variant.CAPTION);
        title.setId("mods.heading");
        listColumn.add(title);
        if (visible.isEmpty()) {
            listColumn.add(new EmptyState(Icons.PACKAGE, Lang.tr(localItems.isEmpty() ? "vanta.mods.installed.empty"
                    : "vanta.mods.no_results", query), Lang.tr("vanta.mods.installed.empty_hint")).height(96));
            return;
        }
        for (int i = 0; i < visible.size(); i++) {
            LocalItem item = visible.get(i);
            ProjectRow row = new ProjectRow(model(item), r -> selectLocal(item)).last(i == visible.size() - 1);
            rows.add(row);
            listColumn.add(row);
        }
    }

    private ProjectRow.Model model(ModrinthSearchHit hit) {
        boolean installed = installedIds.contains(hit.projectId()) || installedSlugs.contains(hit.slug());
        String byline = hit.author().isEmpty() ? "" : Lang.tr("vanta.mods.by", hit.author());
        return new ProjectRow.Model(hit.projectId(), hit.title(), byline, hit.description(),
                ModsFormats.compact(hit.downloads()), Icons.DOWNLOAD,
                installed ? Lang.tr("vanta.mods.state.installed") : "", Badge.Tone.SUCCESS,
                ModsFormats.avatarLetter(hit.title()), ModsFormats.avatarColor(hit.color(), hit.projectId()), false);
    }

    private ProjectRow.Model model(LocalItem item) {
        String badge;
        Badge.Tone tone;
        switch (item.state()) {
            case MISSING -> {
                badge = Lang.tr("vanta.mods.state.missing");
                tone = Badge.Tone.DANGER;
            }
            case MANUAL -> {
                badge = Lang.tr("vanta.mods.state.manual");
                tone = Badge.Tone.NEUTRAL;
            }
            default -> {
                badge = Lang.tr(item.enabled() ? "vanta.mods.state.enabled" : "vanta.mods.state.disabled");
                tone = item.enabled() ? Badge.Tone.SUCCESS : Badge.Tone.WARNING;
            }
        }
        String description = item.entry().map(e -> Lang.tr("vanta.mods.installed.version", e.versionNumber(),
                e.fileName())).orElse(item.relativeFile());
        return new ProjectRow.Model(item.key(), item.name(), Lang.tr(item.type().langKey()), description, "", null,
                badge, tone, ModsFormats.avatarLetter(item.name()), ModsFormats.avatarColor(-1, item.key()),
                !item.enabled() || item.state() == LocalItem.State.MISSING);
    }

    private void markSelection() {
        for (ProjectRow row : rows) {
            row.selected(row.model().key().equals(selectedKey));
        }
    }

    // ---------------------------------------------------------------- selection & detail

    /** Selects a search result. */
    public void selectHit(ModrinthSearchHit hit) {
        selectedHit = hit;
        selectedKey = hit.projectId();
        markSelection();
        rebuildDetail();
    }

    /** Selects an installed item. */
    public void selectLocal(LocalItem item) {
        selectedHit = null;
        selectedKey = item.key();
        markSelection();
        rebuildDetail();
    }

    private Optional<LocalItem> selectedLocal() {
        if (selectedKey == null || tab != ModsTab.INSTALLED) {
            return Optional.empty();
        }
        return localItems.stream().filter(i -> i.key().equals(selectedKey)).findFirst();
    }

    private void rebuildDetail() {
        if (detailBody == null) {
            return;
        }
        detailBody.clearChildren();
        detailFooter.clearChildren();
        pendingActions.clear();
        if (context() != null && isInitialised()) {
            detailScroll.setScrollY(context(), 0f, false);
            detailFooterScroll.setScrollY(context(), 0f, false);
        }
        if (selectedHit != null && tab != ModsTab.INSTALLED) {
            detailForHit(selectedHit);
        } else if (selectedLocal().isPresent()) {
            detailForLocal(selectedLocal().get());
        } else {
            detailBody.add(new EmptyState(Icons.INFO, Lang.tr("vanta.mods.detail.empty"),
                    Lang.tr(tab == ModsTab.INSTALLED ? "vanta.mods.detail.empty_installed_hint"
                            : "vanta.mods.detail.empty_hint")).height(110));
        }
        arrangeActions();
        settleLayout();
    }

    /** Queues an action (button or note) for the detail footer. */
    private void addAction(UiNode node) {
        pendingActions.add(node);
    }

    /**
     * Puts the queued actions into the footer: one per line, except on short windows where actions narrow enough
     * to share a line sit side by side so the footer takes less of the card.
     */
    private void arrangeActions() {
        UiContext ctx = context();
        if (!pendingActions.isEmpty() && ctx != null && height() < SHORT_HEIGHT) {
            int widest = 0;
            for (UiNode action : pendingActions) {
                widest = Math.max(widest, action.preferredSize(ctx).w());
            }
            ResponsiveGrid grid = new ResponsiveGrid(widest, 2, Theme.SPACE_3);
            for (UiNode action : pendingActions) {
                grid.add(action);
            }
            detailFooter.add(grid);
        } else {
            for (UiNode action : pendingActions) {
                detailFooter.add(action);
            }
        }
        pendingActions.clear();
    }

    /**
     * Wrapped labels measure against the width of the previous layout pass, so freshly built nodes need two passes
     * before they report their real height.
     */
    private void settleLayout() {
        UiContext ctx = context();
        if (ctx != null && isInitialised()) {
            root().setBounds(0, 0, width(), height());
            root().layout(ctx);
            root().layout(ctx);
        }
        invalidateLayout();
    }

    private void header(String title, String subtitle, String letter, int color) {
        Row row = new Row(Theme.SPACE_4).align(Align.CENTER);
        row.add(new Avatar(letter, color));
        Column names = new Column(Theme.SPACE_1);
        names.flex(1f);
        names.add(new Label(title, Label.Variant.TITLE).maxLines(2).wrap(true));
        if (!subtitle.isEmpty()) {
            names.add(new Label(subtitle, Label.Variant.MUTED));
        }
        row.add(names);
        detailBody.add(row);
    }

    private void detailForHit(ModrinthSearchHit hit) {
        ModrinthService s = service.get();
        Optional<InstalledEntry> entry = s.installed(hit.projectId());
        boolean installed = entry.isPresent() || installedSlugs.contains(hit.slug());
        header(hit.title(), hit.author().isEmpty() ? "" : Lang.tr("vanta.mods.by", hit.author()),
                ModsFormats.avatarLetter(hit.title()), ModsFormats.avatarColor(hit.color(), hit.projectId()));
        Row badges = new Row(Theme.SPACE_2);
        hit.type().ifPresent(t -> badges.add(new Badge(Lang.tr(t.langKey()), Badge.Tone.NEUTRAL)));
        badges.add(installed ? new Badge(Lang.tr("vanta.mods.state.installed"), Badge.Tone.SUCCESS)
                : new Badge(Lang.tr("vanta.mods.state.not_installed"), Badge.Tone.NEUTRAL));
        detailBody.add(badges);
        detailBody.add(new Label(Lang.tr("vanta.mods.detail.stats", ModsFormats.compact(hit.downloads()),
                ModsFormats.compact(hit.follows())), Label.Variant.MUTED));
        Label description = new Label(hit.description(), Label.Variant.BODY).wrap(true)
                .maxLines(height() < SHORT_HEIGHT ? 3 : 6);
        detailBody.add(description);
        if (entry.isPresent()) {
            entryActions(entry.get(), true);
        } else if (installed) {
            addAction(new Label(Lang.tr("vanta.mods.detail.installed_elsewhere"), Label.Variant.MUTED)
                    .wrap(true));
        } else {
            Button install = Button.primary(Lang.tr("vanta.mods.action.install"), () -> install(hit));
            install.icon(Icons.DOWNLOAD);
            install.setId("mods.action.install");
            install.setEnabled(hit.type().isPresent());
            addAction(install);
        }
        hit.type().ifPresent(type -> {
            Button view = Button.ghost(Lang.tr("vanta.mods.action.view"), () -> services().game().openUrl(
                    "https://modrinth.com/" + type.apiName() + "/" + hit.slug())).compact(true);
            view.icon(Icons.EXTERNAL_LINK);
            view.setId("mods.action.view");
            addAction(view);
        });
    }

    private void detailForLocal(LocalItem item) {
        header(item.name(), Lang.tr(item.type().langKey()), ModsFormats.avatarLetter(item.name()),
                ModsFormats.avatarColor(-1, item.key()));
        Row badges = new Row(Theme.SPACE_2);
        ProjectRow.Model model = model(item);
        badges.add(new Badge(model.badge(), model.badgeTone()));
        detailBody.add(badges);
        detailBody.add(new Label(item.relativeFile(), Label.Variant.MUTED).wrap(true).maxLines(2));
        if (item.entry().isPresent()) {
            InstalledEntry entry = item.entry().get();
            detailBody.add(new Label(Lang.tr("vanta.mods.detail.version", entry.versionNumber()), Label.Variant.BODY));
            if (!entry.requiredBy().isEmpty()) {
                detailBody.add(new Label(Lang.tr("vanta.mods.detail.required_by", titles(entry.requiredBy())),
                        Label.Variant.MUTED).wrap(true).maxLines(3));
            }
            if (item.state() == LocalItem.State.MISSING) {
                Button forget = Button.secondary(Lang.tr("vanta.mods.action.forget"),
                        () -> service.get().forget(entry.projectId(), this::refreshInstalled)).compact(true);
                forget.setId("mods.action.forget");
                addAction(forget);
            } else {
                entryActions(entry, false);
            }
        } else {
            detailBody.add(new Label(Lang.tr("vanta.mods.detail.manual"), Label.Variant.MUTED).wrap(true));
            typeShortcut(item.type());
        }
    }

    private void entryActions(InstalledEntry entry, boolean fromSearch) {
        ModrinthProjectType type = entry.projectType().orElse(ModrinthProjectType.MOD);
        typeShortcut(type);
        if (type.canDisable()) {
            boolean enabled = entry.enabled();
            Button toggle = Button.secondary(Lang.tr(enabled ? "vanta.mods.action.disable" : "vanta.mods.action.enable"),
                    () -> service.get().setEnabled(entry.projectId(), !enabled, e -> refreshInstalled())).compact(true);
            toggle.setId(enabled ? "mods.action.disable" : "mods.action.enable");
            addAction(toggle);
        }
        Button remove = Button.danger(Lang.tr("vanta.mods.action.remove"), () -> confirmRemove(entry)).compact(true);
        remove.icon(Icons.TRASH);
        remove.setId("mods.action.remove");
        addAction(remove);
    }

    /** Shortcuts that make an installed shader or resource pack usable right away. */
    private void typeShortcut(ModrinthProjectType type) {
        if (type == ModrinthProjectType.SHADER && service.get().irisLoaded()) {
            Button open = Button.secondary(Lang.tr("vanta.mods.action.shader_settings"), this::openShaderSettings)
                    .compact(true);
            open.icon(Icons.SLIDERS);
            open.setId("mods.action.shaderSettings");
            addAction(open);
        } else if (type == ModrinthProjectType.RESOURCE_PACK) {
            Button open = Button.secondary(Lang.tr("vanta.mods.action.resource_packs"), this::openResourcePacks)
                    .compact(true);
            open.icon(Icons.PACKAGE);
            open.setId("mods.action.resourcePacks");
            addAction(open);
        }
    }

    private String titles(List<String> projectIds) {
        List<String> names = new ArrayList<>();
        for (String id : projectIds) {
            names.add(service.get().installed(id).map(InstalledEntry::title).orElse(id));
        }
        return String.join(", ", names);
    }

    // ---------------------------------------------------------------- actions

    /** Installs a search result with its required dependencies. */
    public void install(ModrinthSearchHit hit) {
        service.get().install(List.of(InstallRequest.of(hit)), Lang.tr("vanta.mods.progress.installing", hit.title()),
                this::afterInstall);
    }

    /** Installs the ticked, not yet installed members of the Performance pack. */
    public void installPack() {
        List<String> slugs = new ArrayList<>();
        for (PackItemToggle toggle : packToggles) {
            if (toggle.isChecked()) {
                slugs.add(toggle.item().slug());
            }
        }
        if (slugs.isEmpty()) {
            return;
        }
        packInstall.setEnabled(false);
        service.get().installPerformancePack(slugs, this::afterInstall);
    }

    private void installIris() {
        service.get().install(List.of(new InstallRequest("iris", "Iris Shaders")),
                Lang.tr("vanta.mods.progress.installing", "Iris Shaders"), this::afterInstall);
    }

    private void afterInstall(InstallResult result) {
        refreshInstalled();
        UiContext ctx = context();
        if (ctx == null || isClosing()) {
            return;
        }
        if (!result.installed(ModrinthProjectType.SHADER).isEmpty()) {
            if (service.get().irisLoaded()) {
                Dialog.confirm(ctx, Lang.tr("vanta.mods.dialog.shader.title"), Lang.tr("vanta.mods.dialog.shader.body"),
                        Lang.tr("vanta.mods.action.shader_settings"), Lang.tr("vanta.mods.dialog.later"), false,
                        this::openShaderSettings);
            } else {
                Dialog.info(ctx, Lang.tr("vanta.mods.dialog.shader.title"), Lang.tr("vanta.mods.dialog.shader.no_iris"),
                        Lang.tr("vanta.common.ok"));
            }
        } else if (!result.installed(ModrinthProjectType.RESOURCE_PACK).isEmpty()) {
            Dialog.confirm(ctx, Lang.tr("vanta.mods.dialog.pack.title"), Lang.tr("vanta.mods.dialog.pack.body"),
                    Lang.tr("vanta.mods.action.resource_packs"), Lang.tr("vanta.mods.dialog.later"), false,
                    this::openResourcePacks);
        } else if (!result.isClean() && !result.problems().isEmpty() && result.installed().isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (PlanNote note : result.problems()) {
                lines.add(note.message());
            }
            Dialog.info(ctx, Lang.tr("vanta.mods.dialog.problems.title"), String.join("\n", lines),
                    Lang.tr("vanta.common.ok"));
        }
    }

    private void confirmRemove(InstalledEntry entry) {
        String body = entry.requiredBy().isEmpty() ? Lang.tr("vanta.mods.dialog.remove.body", entry.title())
                : Lang.tr("vanta.mods.dialog.remove.required", entry.title(), titles(entry.requiredBy()));
        Dialog.confirm(context(), Lang.tr("vanta.mods.dialog.remove.title", entry.title()), body,
                Lang.tr("vanta.mods.action.remove"), Lang.tr("vanta.common.cancel"), true,
                () -> service.get().remove(entry.projectId(), removed -> {
                    selectedKey = null;
                    selectedHit = tab == ModsTab.INSTALLED ? null : selectedHit;
                    refreshInstalled();
                }));
    }

    /** Opens Iris's shader pack screen, or explains the Iris key when that is not possible. */
    public void openShaderSettings() {
        if (!service.get().platform().openShaderPackScreen()) {
            services().notifications().post(NotificationKind.INFO, Lang.tr("vanta.mods.toast.shader_key.title"),
                    Lang.tr("vanta.mods.toast.shader_key.body"), 8000);
        }
    }

    /** Opens the vanilla resource pack screen (it rescans the folder). */
    public void openResourcePacks() {
        navigator().openVanilla(VanillaScreen.RESOURCE_PACKS);
    }

    /** Quits (or restarts through the VANTA launcher) so changed mods load; not while a download is still running. */
    public void restart() {
        if (services().game().isInWorld() || service.get().isBusy()) {
            return;
        }
        service.get().restartOrQuit(services().game());
    }

    // ---------------------------------------------------------------- detail nodes

    /**
     * Body of the detail card: the scrolling text above the action footer. The footer is pinned at the bottom at its
     * full height; when the card is too short even for that (a 240 px window with the restart banner, large text)
     * the footer scrolls as well, keeping one line of text, so every action stays reachable with the wheel.
     */
    private static final class DetailStack extends UiNode {
        private static final int MIN_TEXT_H = Button.HEIGHT;
        private final ScrollPanel text;
        private final ScrollPanel actions;
        private final int gap;

        DetailStack(ScrollPanel text, ScrollPanel actions, int gap) {
            this.text = text;
            this.actions = actions;
            this.gap = gap;
            add(text);
            add(actions);
        }

        @Override
        protected Size measure(UiContext ctx) {
            Size t = text.preferredSize(ctx);
            Size a = actions.preferredSize(ctx);
            return new Size(Math.max(t.w(), a.w()), t.h() + (a.h() > 0 ? gap + a.h() : 0));
        }

        @Override
        public void layout(UiContext ctx) {
            Rect b = bounds();
            int actionsPref = actions.preferredSize(ctx).h();
            boolean hasActions = actionsPref > 0;
            int actionsH = hasActions ? Math.min(actionsPref, Math.max(0, b.h() - MIN_TEXT_H - gap)) : 0;
            actions.setVisible(hasActions);
            text.setBounds(b.x(), b.y(), b.w(), Math.max(0, b.h() - (hasActions ? actionsH + gap : 0)));
            text.layout(ctx);
            if (hasActions) {
                actions.setBounds(b.x(), b.bottom() - actionsH, b.w(), actionsH);
                actions.layout(ctx);
            }
        }
    }

    /** Large letter avatar of the detail panel. */
    private static final class Avatar extends UiNode {
        private static final int SIZE = 32;
        private final String letter;
        private final int color;

        Avatar(String letter, int color) {
            this.letter = letter;
            this.color = color;
        }

        @Override
        protected Size measure(UiContext ctx) {
            return new Size(SIZE, SIZE);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Rect b = bounds();
            ProjectRow.drawAvatar(canvas, b.x(), b.y() + (b.h() - SIZE) / 2, SIZE, letter, color, false);
        }
    }
}
