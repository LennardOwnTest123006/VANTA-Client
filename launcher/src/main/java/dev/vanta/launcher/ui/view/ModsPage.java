package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.install.OfficialProfileService;
import dev.vanta.launcher.core.modrinth.ContentType;
import dev.vanta.launcher.core.modrinth.ModrinthModels;
import dev.vanta.launcher.core.modrinth.ModrinthService;
import dev.vanta.launcher.ui.model.ModsViewModel;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Mods: browse Modrinth (Mods / Shaders / Resource packs for Minecraft 1.21.11) and manage what is installed in the
 * VANTA game folder (enable, disable, remove, update all). The same folder is the game directory of the Minecraft
 * Launcher profile "VANTA 1.21.11", so everything here loads there too, after the next start of the game.
 */
public final class ModsPage extends VBox {

    private final AppContext ctx;
    private final ModsViewModel vm;
    private final DialogLayer dialogs;
    private final VBox resultsList = new VBox(0);
    private final VBox installedList = new VBox(0);
    private final Label installedTitle = Ui.label("", "card-title");
    private final Map<ContentType, ToggleButton> tabs = new EnumMap<>(ContentType.class);
    private boolean firstShow = true;

    /**
     * @param ctx     context
     * @param dialogs dialog layer (remove confirmation)
     */
    public ModsPage(final AppContext ctx, final DialogLayer dialogs) {
        this.ctx = ctx;
        this.vm = ctx.mods();
        this.dialogs = dialogs;
        getStyleClass().addAll("page", "mods-page");
        setSpacing(18);

        final Button openFolder = Ui.button(ctx.t("mods.openFolder"), Icons.Icon.FOLDER, "secondary", "small");
        openFolder.setOnAction(e -> ctx.opener().openFolder(ctx.backend().paths().instanceDir().resolve(vm.tabProperty().get().folder())));
        final Button updateAll = Ui.button(ctx.t("mods.updateAll"), Icons.Icon.REFRESH, "primary", "small");
        updateAll.getStyleClass().add("mods-update-all");
        updateAll.setTooltip(Ui.tooltip(ctx.t("mods.updateAll.tooltip")));
        updateAll.disableProperty().bind(vm.updatingProperty());
        updateAll.setOnAction(e -> vm.updateAll());
        final HBox header = Ui.pageHeader(ctx.t("mods.title"),
            ctx.t("mods.subtitle", OfficialProfileService.profileName(LauncherVersion.MINECRAFT)), openFolder, updateAll);

        final Label status = Ui.label("", "detail-text", "mods-status");
        status.textProperty().bind(vm.statusTextProperty());
        status.setMaxWidth(Double.MAX_VALUE);
        Ui.bindVisible(status, Bindings.createBooleanBinding(() -> !vm.statusTextProperty().get().isEmpty(), vm.statusTextProperty()));
        final VBox restartHint = Ui.callout("accent", null, ctx.t("mods.restartHint"), null);
        restartHint.getStyleClass().add("mods-restart-hint");
        Ui.bindVisible(restartHint, vm.changedSinceStartProperty());

        final VBox browse = browseCard();
        HBox.setHgrow(browse, Priority.ALWAYS);
        browse.setMinWidth(360);
        final VBox installed = installedCard();
        installed.setMinWidth(280);
        installed.setPrefWidth(330);
        installed.setMaxWidth(360);
        final HBox columns = new HBox(20, browse, installed);
        columns.setAlignment(Pos.TOP_LEFT);

        getChildren().addAll(header, status, restartHint, columns);

        vm.results().addListener((ListChangeListener<ModrinthModels.SearchHit>) c -> renderResults());
        vm.searchingProperty().addListener((obs, old, now) -> renderResults());
        vm.searchErrorProperty().addListener((obs, old, now) -> renderResults());
        vm.installed().addListener((ListChangeListener<ModrinthService.InstalledContent>) c -> {
            renderInstalled();
            renderResults();
        });
        vm.installing().addListener((SetChangeListener<String>) c -> renderResults());
        vm.tabProperty().addListener((obs, old, now) -> {
            tabs.forEach((type, button) -> button.setSelected(type == now));
            renderInstalled();
        });
        renderResults();
        renderInstalled();
        // First visit: load what is installed and the most downloaded projects of the selected tab.
        sceneProperty().addListener((obs, old, now) -> {
            if (now != null && firstShow) {
                firstShow = false;
                vm.refreshInstalled();
                vm.search();
            }
        });
    }

    // ---------------------------------------------------------------- browse

    private VBox browseCard() {
        final ToggleGroup group = new ToggleGroup();
        final HBox segmented = new HBox();
        segmented.getStyleClass().add("segmented");
        for (ContentType type : ContentType.values()) {
            final ToggleButton b = new ToggleButton(ctx.t(tabKey(type)));
            b.getStyleClass().add("mods-tab-" + type.id());
            b.setToggleGroup(group);
            b.setOnAction(e -> {
                b.setSelected(true);
                vm.tabProperty().set(type);
            });
            b.setSelected(vm.tabProperty().get() == type);
            tabs.put(type, b);
            segmented.getChildren().add(b);
        }
        segmented.setMinWidth(Region.USE_PREF_SIZE);

        final TextField search = new TextField();
        search.getStyleClass().addAll("search-field", "mods-search");
        search.setPromptText(ctx.t("mods.search.prompt"));
        search.textProperty().bindBidirectional(vm.queryProperty());
        search.setOnAction(e -> vm.search());
        final StackPane searchBox = new StackPane(search, Icons.of(Icons.Icon.SEARCH, 14, "search-icon"));
        StackPane.setAlignment(searchBox.getChildren().get(1), Pos.CENTER_LEFT);
        StackPane.setMargin(searchBox.getChildren().get(1), Ui.insets(0, 0, 0, 12));
        HBox.setHgrow(searchBox, Priority.ALWAYS);
        final Button go = Ui.button(ctx.t("mods.search"), "secondary", "small");
        go.setMinWidth(Region.USE_PREF_SIZE);
        go.setOnAction(e -> vm.search());
        final HBox searchRow = new HBox(8, searchBox, go);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        final Label caption = Ui.paragraph(ctx.t("mods.browse.caption", LauncherVersion.MINECRAFT), "card-caption");
        final VBox card = Ui.card(Ui.cardHeader(ctx.t("mods.browse.title"), null), segmented, searchRow, caption, resultsList);
        card.getStyleClass().add("mods-browse");
        card.setSpacing(12);
        return card;
    }

    private static String tabKey(final ContentType type) {
        return switch (type) {
            case MOD -> "mods.tab.mods";
            case SHADER -> "mods.tab.shaders";
            case RESOURCE_PACK -> "mods.tab.resourcepacks";
        };
    }

    private void renderResults() {
        resultsList.getChildren().clear();
        if (vm.searchingProperty().get() && vm.results().isEmpty()) {
            final ProgressIndicator spinner = new ProgressIndicator();
            spinner.setMaxSize(28, 28);
            final HBox row = new HBox(10, spinner, Ui.label(ctx.t("mods.search.loading"), "text-muted"));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("mods-loading");
            resultsList.getChildren().add(row);
            return;
        }
        if (!vm.searchErrorProperty().get().isEmpty()) {
            final Button retry = Ui.button(ctx.t("common.retry"), Icons.Icon.REFRESH, "secondary", "small");
            retry.setOnAction(e -> vm.search());
            resultsList.getChildren().add(Ui.callout("danger", ctx.t("mods.search.failed"), vm.searchErrorProperty().get(), retry));
            return;
        }
        if (vm.results().isEmpty()) {
            if (vm.searchedProperty().get()) {
                resultsList.getChildren().add(Ui.label(ctx.t("mods.search.empty"), "text-muted", "text-small"));
            }
            return;
        }
        boolean first = true;
        for (ModrinthModels.SearchHit hit : vm.results()) {
            final Node row = resultRow(hit);
            if (first) {
                row.getStyleClass().add("first");
                first = false;
            }
            resultsList.getChildren().add(row);
        }
        if (vm.hasMore()) {
            final Button more = Ui.button(ctx.t("mods.search.more"), "ghost", "small");
            more.setDisable(vm.searchingProperty().get());
            more.setOnAction(e -> vm.loadMore());
            final HBox moreRow = new HBox(more);
            moreRow.setAlignment(Pos.CENTER);
            moreRow.setPadding(Ui.insets(8, 0, 0, 0));
            resultsList.getChildren().add(moreRow);
        }
    }

    private HBox resultRow(final ModrinthModels.SearchHit hit) {
        final InitialsAvatar tile = new InitialsAvatar(hit.title(), 36);
        final Label title = Ui.label(hit.title(), "kv-value", "mods-result-title");
        title.setMinWidth(0);
        final Label by = Ui.label(vm.byline(hit), "text-muted", "text-small");
        by.setMinWidth(0);
        final Label description = Ui.paragraph(hit.description(), "text-secondary", "text-small", "mods-result-description");
        description.setMaxHeight(36);
        final VBox text = new VBox(2, title, by, description);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        final Node action;
        if (vm.isIncluded(hit)) {
            final Label included = Ui.badge(ctx.t("mods.result.included"), "info");
            included.setTooltip(Ui.tooltip(ctx.t("mods.result.included.tooltip")));
            action = included;
        } else if (vm.isInstalled(hit)) {
            action = Ui.badge(ctx.t("mods.result.installed"), "success");
        } else if (vm.installing().contains(hit.projectId())) {
            action = Ui.badge(ctx.t("mods.result.installing"), "accent");
        } else {
            final Button install = Ui.button(ctx.t("mods.result.install"), Icons.Icon.DOWNLOAD, "secondary", "small");
            install.getStyleClass().add("mods-install");
            install.setTooltip(Ui.tooltip(ctx.t("mods.result.install.tooltip", hit.title())));
            install.disableProperty().bind(vm.updatingProperty());
            install.setOnAction(e -> vm.install(hit));
            action = install;
        }
        if (action instanceof Region r) {
            r.setMinWidth(Region.USE_PREF_SIZE);
        }
        final HBox row = new HBox(12, tile, text, action);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("list-row", "mods-result");
        return row;
    }

    // ---------------------------------------------------------------- installed

    private VBox installedCard() {
        final Label caption = Ui.paragraph(ctx.t("mods.installed.caption"), "card-caption");
        final VBox card = Ui.card(Ui.cardHeader("", null), caption, installedList);
        ((HBox) card.getChildren().get(0)).getChildren().setAll(installedTitle);
        card.getStyleClass().add("mods-installed");
        return card;
    }

    private void renderInstalled() {
        final ContentType type = vm.tabProperty().get();
        final List<ModrinthService.InstalledContent> items = vm.installedOf(type);
        installedTitle.setText(ctx.t("mods.installed.title." + type.id(), Integer.toString(items.size())));
        installedList.getChildren().clear();
        if (items.isEmpty()) {
            installedList.getChildren().add(Ui.paragraph(ctx.t("mods.installed.empty"), "text-muted", "text-small"));
            return;
        }
        boolean first = true;
        for (ModrinthService.InstalledContent item : items) {
            final HBox row = installedRow(item);
            if (first) {
                row.getStyleClass().add("first");
                first = false;
            }
            installedList.getChildren().add(row);
        }
    }

    private HBox installedRow(final ModrinthService.InstalledContent item) {
        final Label title = Ui.label(item.title(), "kv-value");
        title.setMinWidth(0);
        title.setTooltip(Ui.tooltip(item.path().toString()));
        final String sub;
        if (item.managed()) {
            sub = ctx.t("mods.installed.managed");
        } else if (item.missing()) {
            sub = ctx.t("mods.installed.missing");
        } else if (!item.tracked()) {
            sub = ctx.t("mods.installed.untracked");
        } else if (!item.requiredBy().isEmpty()) {
            sub = ctx.t("mods.installed.requiredBy", item.version(), String.join(", ", item.requiredBy()));
        } else {
            sub = item.version();
        }
        final Label detail = Ui.label(sub, "text-muted", "text-small");
        detail.setMinWidth(0);
        detail.setTooltip(Ui.tooltip(sub));
        final VBox text = new VBox(2, title, detail);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        final HBox row = new HBox(8, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("list-row", "mods-installed-row");
        if (item.managed()) {
            final Label badge = Ui.badge(ctx.t("mods.installed.vanta"), "info");
            badge.setMinWidth(Region.USE_PREF_SIZE);
            row.getChildren().add(badge);
            return row;
        }
        final Switch enabled = new Switch();
        enabled.setSelected(item.enabled());
        enabled.setAccessibleText(ctx.t(item.enabled() ? "mods.installed.disable" : "mods.installed.enable", item.title()));
        Tooltips.install(enabled, ctx.t(item.enabled() ? "mods.installed.disable" : "mods.installed.enable", item.title()));
        enabled.selectedProperty().addListener((obs, old, now) -> vm.setEnabled(item, now));
        enabled.setDisable(item.missing());
        final Button remove = Ui.iconButton(Icons.Icon.TRASH, ctx.t("mods.installed.remove", item.title()), "ghost");
        remove.getStyleClass().add("mods-remove");
        remove.setOnAction(e -> dialogs.show(new ConfirmDialog(ctx.t("mods.remove.title", item.title()),
            ctx.t("mods.remove.text", item.path().getFileName().toString()), ctx.t("mods.remove.confirm"), ctx.t("common.cancel"),
            () -> vm.remove(item), () -> { }, dialogs::close)));
        row.getChildren().addAll(enabled, remove);
        return row;
    }

    /** Tooltip helper for controls that are not {@link javafx.scene.control.Control}s with a tooltip property. */
    private static final class Tooltips {
        private Tooltips() {
        }

        static void install(final Node node, final String text) {
            javafx.scene.control.Tooltip.install(node, Ui.tooltip(text));
        }
    }
}
