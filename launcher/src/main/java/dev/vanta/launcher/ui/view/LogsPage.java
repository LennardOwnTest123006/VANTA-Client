package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.ui.model.LogLevel;
import dev.vanta.launcher.ui.model.LogLine;
import dev.vanta.launcher.ui.model.LogsViewModel;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * Logs: Launcher / Game tabs over a virtualised monospace list with filter, level toggles, Copy, Open logs folder
 * and Clear view.
 */
public final class LogsPage extends VBox {

    private final AppContext ctx;
    private final LogsViewModel vm;
    private final ListView<LogLine> list = new ListView<>();

    /**
     * @param ctx context
     */
    public LogsPage(final AppContext ctx) {
        this.ctx = ctx;
        this.vm = ctx.logs();
        getStyleClass().add("page");
        setSpacing(16);

        final HBox header = Ui.pageHeader(ctx.t("logs.title"), ctx.t("logs.subtitle"));

        // tabs
        final ToggleGroup tabs = new ToggleGroup();
        final ToggleButton launcherTab = tab(ctx.t("logs.tab.launcher"), LogsViewModel.Source.LAUNCHER, tabs);
        final ToggleButton gameTab = tab(ctx.t("logs.tab.game"), LogsViewModel.Source.GAME, tabs);
        final HBox segmented = new HBox(launcherTab, gameTab);
        segmented.getStyleClass().add("segmented");
        vm.sourceProperty().addListener((obs, old, now) -> {
            launcherTab.setSelected(now == LogsViewModel.Source.LAUNCHER);
            gameTab.setSelected(now == LogsViewModel.Source.GAME);
            bindList();
        });
        launcherTab.setSelected(true);

        // filter
        final TextField filter = new TextField();
        filter.getStyleClass().add("search-field");
        filter.setPromptText(ctx.t("logs.filter.prompt"));
        filter.textProperty().bindBidirectional(vm.filterProperty());
        filter.setPrefWidth(240);
        final StackPane filterBox = new StackPane(filter, Icons.of(Icons.Icon.SEARCH, 14, "search-icon"));
        StackPane.setAlignment(filterBox.getChildren().get(1), Pos.CENTER_LEFT);
        StackPane.setMargin(filterBox.getChildren().get(1), Ui.insets(0, 0, 0, 12));

        // level chips
        final HBox chips = new HBox(6);
        for (LogLevel level : LogLevel.values()) {
            final ToggleButton chip = new ToggleButton(ctx.t("logs.level." + level.name().toLowerCase(Locale.ROOT)));
            chip.getStyleClass().add("chip");
            chip.selectedProperty().bindBidirectional(vm.levelEnabledProperty(level));
            chips.getChildren().add(chip);
        }

        final Label count = Ui.label("", "text-muted", "text-small");
        count.textProperty().bind(Bindings.createStringBinding(() -> vm.visibleCountProperty().get() == vm.totalCountProperty().get()
            ? ctx.t("logs.count", Integer.toString(vm.totalCountProperty().get()))
            : ctx.t("logs.count.filtered", Integer.toString(vm.visibleCountProperty().get()), Integer.toString(vm.totalCountProperty().get())),
            vm.visibleCountProperty(), vm.totalCountProperty()));

        final ToggleButton follow = new ToggleButton(ctx.t("logs.autoScroll"));
        follow.getStyleClass().add("chip");
        follow.selectedProperty().bindBidirectional(vm.followProperty());

        final Button copy = Ui.button(ctx.t("logs.copy"), Icons.Icon.COPY, "secondary", "small");
        copy.setOnAction(e -> {
            final int n = vm.currentLines().size();
            if (ctx.opener().copy(vm.copyText())) {
                ctx.toasts().success(ctx.t("logs.copied", Integer.toString(n)), "");
            }
        });
        final Button openFolder = Ui.button(ctx.t("logs.openFolder"), Icons.Icon.FOLDER, "secondary", "small");
        openFolder.setOnAction(e -> ctx.opener().openFolder(ctx.backend().paths().logsDir()));
        final Button clear = Ui.button(ctx.t("logs.clear"), Icons.Icon.ERASER, "ghost", "small");
        clear.setOnAction(e -> vm.clearView());

        HBox.setHgrow(filterBox, Priority.ALWAYS);
        filter.setMaxWidth(Double.MAX_VALUE);
        filter.setPrefWidth(320);
        final HBox toolbarTop = new HBox(12, segmented, filterBox, count);
        toolbarTop.setAlignment(Pos.CENTER_LEFT);
        final HBox toolbarBottom = new HBox(10, chips, Ui.spacer(), follow, copy, openFolder, clear);
        toolbarBottom.setAlignment(Pos.CENTER_LEFT);
        final VBox toolbar = new VBox(10, toolbarTop, toolbarBottom);

        // list
        list.getStyleClass().add("log-view");
        list.setCellFactory(v -> new LogCell());
        list.setFocusTraversable(true);
        VBox.setVgrow(list, Priority.ALWAYS);
        final Label empty = Ui.label("", "text-muted");
        empty.textProperty().bind(Bindings.createStringBinding(() -> vm.currentIsEmpty()
            ? ctx.t(vm.sourceProperty().get() == LogsViewModel.Source.GAME ? "logs.empty.game" : "logs.empty.launcher")
            : ctx.t("logs.empty.filtered"), vm.totalCountProperty(), vm.visibleCountProperty(), vm.sourceProperty()));
        final VBox emptyBox = new VBox(Icons.of(Icons.Icon.TERMINAL, 26, "empty-icon"), Ui.vgap(8), empty);
        emptyBox.getStyleClass().add("empty-state");
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.setMouseTransparent(true);
        Ui.bindVisible(emptyBox, Bindings.createBooleanBinding(() -> vm.visibleCountProperty().get() == 0, vm.visibleCountProperty()));
        final StackPane listStack = new StackPane(list, emptyBox);
        VBox.setVgrow(listStack, Priority.ALWAYS);
        bindList();

        getChildren().addAll(header, toolbar, listStack);
        setMinHeight(0);
    }

    private ToggleButton tab(final String text, final LogsViewModel.Source source, final ToggleGroup group) {
        final ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.setOnAction(e -> {
            b.setSelected(true);
            vm.sourceProperty().set(source);
        });
        return b;
    }

    private void bindList() {
        list.setItems(vm.currentLines());
        vm.currentLines().addListener((ListChangeListener<LogLine>) c -> {
            if (vm.followProperty().get() && !vm.currentLines().isEmpty()) {
                list.scrollTo(vm.currentLines().size() - 1);
            }
        });
        if (!vm.currentLines().isEmpty()) {
            list.scrollTo(vm.currentLines().size() - 1);
        }
    }

    /** Monospace cell coloured by level. */
    private final class LogCell extends ListCell<LogLine> {
        @Override
        protected void updateItem(final LogLine item, final boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("log-debug", "log-info", "log-warn", "log-error", "log-launcher");
            if (empty || item == null) {
                setText(null);
                return;
            }
            setText(ctx.formats().time(item.time()) + "  " + item.text());
            getStyleClass().add("log-" + item.level().name().toLowerCase(Locale.ROOT));
        }
    }
}
