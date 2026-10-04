package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.ui.model.UpdateViewModel;
import dev.vanta.launcher.ui.model.VersionsViewModel;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Versions: component table, SHA-256 of the client jar, kept versions with roll back.
 */
public final class VersionsPage extends VBox {

    private final AppContext ctx;
    private final VersionsViewModel vm;
    private final GridPane table = new GridPane();
    private final VBox keptList = new VBox(0);

    /**
     * @param ctx context
     */
    public VersionsPage(final AppContext ctx) {
        this.ctx = ctx;
        this.vm = ctx.versions();
        getStyleClass().add("page");
        setSpacing(20);

        final Button refresh = Ui.button(ctx.t("versions.refresh"), Icons.Icon.REFRESH, "secondary", "small");
        refresh.setOnAction(e -> {
            vm.refresh();
            ctx.updates().check(false);
        });
        final HBox header = Ui.pageHeader(ctx.t("versions.title"), ctx.t("versions.subtitle"), refresh);

        final VBox empty = Ui.card(Ui.emptyState(Icons.Icon.PACKAGE, ctx.t("versions.empty.title"),
            ctx.t("versions.empty.text", LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER)));
        Ui.bindVisible(empty, Bindings.createBooleanBinding(() -> ctx.session().instance().isEmpty(), ctx.session().instanceProperty()));

        table.getStyleClass().add("data-table");
        table.setMaxWidth(Double.MAX_VALUE);
        final ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(22);
        final ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(20);
        final ColumnConstraints c3 = new ColumnConstraints();
        c3.setPercentWidth(18);
        final ColumnConstraints c4 = new ColumnConstraints();
        c4.setPercentWidth(40);
        c4.setHalignment(HPos.LEFT);
        table.getColumnConstraints().addAll(c1, c2, c3, c4);
        vm.rows().addListener((ListChangeListener<VersionsViewModel.Row>) c -> renderTable());
        renderTable();

        final VBox notices = new VBox(12);
        final VBox notPublished = Ui.callout("accent", ctx.t("versions.notPublished.title"), ctx.t("versions.notPublished.text"), null);
        Ui.bindVisible(notPublished, Bindings.createBooleanBinding(() -> ctx.updates().clientAvailabilityProperty().get()
            == UpdateViewModel.ClientAvailability.NOT_PUBLISHED, ctx.updates().clientAvailabilityProperty()));
        final VBox notConfigured = Ui.callout("", ctx.t("versions.notConfigured.title"), ctx.t("versions.notConfigured.text"), null);
        Ui.bindVisible(notConfigured, Bindings.createBooleanBinding(() -> ctx.updates().clientAvailabilityProperty().get()
            == UpdateViewModel.ClientAvailability.NOT_CONFIGURED && ctx.session().instance().isEmpty(),
            ctx.updates().clientAvailabilityProperty(), ctx.session().instanceProperty()));
        notices.getChildren().addAll(notPublished, notConfigured);

        final HBox lower = new HBox(20, shaCard(), keptCard());
        lower.setAlignment(Pos.TOP_LEFT);

        getChildren().addAll(header, empty, table, notices, lower);
    }

    private void renderTable() {
        table.getChildren().clear();
        final String[] headers = {ctx.t("versions.column.component"), ctx.t("versions.column.version"), ctx.t("versions.column.status"),
            ctx.t("versions.column.detail")};
        for (int i = 0; i < headers.length; i++) {
            final Label h = Ui.label(headers[i].toUpperCase(ctx.messages().locale()), "table-header-cell");
            table.add(h, i, 0);
        }
        final List<VersionsViewModel.Row> rows = vm.rows();
        for (int r = 0; r < rows.size(); r++) {
            final VersionsViewModel.Row row = rows.get(r);
            final int gridRow = r + 1;
            final Region line = new Region();
            line.getStyleClass().add("table-row");
            line.setMaxWidth(Double.MAX_VALUE);
            GridPane.setColumnSpan(line, 4);
            GridPane.setValignment(line, javafx.geometry.VPos.TOP);
            line.setMinHeight(1);
            line.setPrefHeight(1);
            line.setMaxHeight(1);
            table.add(line, 0, gridRow);
            table.add(Ui.label(row.component(), "table-cell", "text-medium"), 0, gridRow);
            table.add(Ui.label(row.version(), "table-cell", "mono"), 1, gridRow);
            table.add(statusBadge(row.status()), 2, gridRow);
            final Label detail = Ui.label(row.detail(), "table-cell", "secondary");
            detail.setMaxWidth(Double.MAX_VALUE);
            detail.setTooltip(row.detail().isEmpty() ? null : Ui.tooltip(row.detail()));
            table.add(detail, 3, gridRow);
        }
    }

    private Node statusBadge(final VersionsViewModel.RowStatus status) {
        final Label badge = switch (status) {
            case INSTALLED -> Ui.badge(ctx.t("versions.status.installed"), "success");
            case DETECTED -> Ui.badge(ctx.t("versions.status.detected"), "success");
            case MISSING -> Ui.badge(ctx.t("versions.status.missing"), "danger");
            default -> Ui.badge(ctx.t("versions.status.notInstalled"), "warning");
        };
        badge.setMinWidth(Region.USE_PREF_SIZE);
        final HBox cell = new HBox(badge);
        cell.getStyleClass().add("table-cell");
        cell.setAlignment(Pos.CENTER_LEFT);
        return cell;
    }

    private VBox shaCard() {
        final Label jar = Ui.label("", "kv-value");
        jar.textProperty().bind(Bindings.createStringBinding(() -> vm.jarNameProperty().get().isEmpty()
            ? ctx.t("versions.sha.none") : vm.jarNameProperty().get(), vm.jarNameProperty()));
        final Label sha = Ui.paragraph("", "mono", "text-secondary");
        sha.textProperty().bind(Bindings.createStringBinding(() -> vm.computingShaProperty().get() ? ctx.t("versions.sha.computing")
            : vm.sha256Property().get(), vm.sha256Property(), vm.computingShaProperty()));
        sha.setStyle("-fx-font-size: 12px;");
        final Button copy = Ui.button(ctx.t("versions.sha.copy"), Icons.Icon.COPY, "secondary", "small");
        copy.disableProperty().bind(Bindings.createBooleanBinding(() -> vm.sha256Property().get().isEmpty(), vm.sha256Property()));
        copy.setOnAction(e -> {
            if (ctx.opener().copy(vm.sha256Property().get())) {
                ctx.toasts().success(ctx.t("versions.sha.copied"), vm.jarNameProperty().get());
            }
        });
        final VBox card = Ui.card(Ui.cardHeader(ctx.t("versions.sha.title"), copy), Ui.label(ctx.t("versions.sha.caption"), "card-caption"), jar, sha);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private VBox keptCard() {
        final Label caption = Ui.paragraph(ctx.t("versions.previous.caption", Integer.toString(VantaClientService.KEEP_VERSIONS)), "card-caption");
        final VBox card = Ui.card(Ui.cardHeader(ctx.t("versions.previous.title"), null), caption, keptList);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        vm.kept().addListener((ListChangeListener<VersionsViewModel.Kept>) c -> renderKept());
        vm.rollingBackProperty().addListener((obs, old, now) -> renderKept());
        renderKept();
        return card;
    }

    private void renderKept() {
        keptList.getChildren().clear();
        if (vm.kept().isEmpty()) {
            keptList.getChildren().add(Ui.label(ctx.t("versions.previous.empty"), "text-muted", "text-small"));
            return;
        }
        boolean first = true;
        for (VersionsViewModel.Kept k : vm.kept()) {
            final Label version = Ui.label("VANTA Client " + k.version(), "kv-value");
            final Label since = Ui.label(ctx.t("versions.previous.installedAt", ctx.formats().date(k.installedAt())), "text-muted", "text-small");
            final VBox text = new VBox(2, version, since);
            HBox.setHgrow(text, Priority.ALWAYS);
            final HBox row = new HBox(10, text);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("list-row");
            if (first) {
                row.getStyleClass().add("first");
                first = false;
            }
            if (k.active()) {
                row.getChildren().add(Ui.badge(ctx.t("versions.previous.active"), "accent"));
            } else if (!k.available()) {
                row.getChildren().add(Ui.badge(ctx.t("versions.previous.missingJar"), "danger"));
            } else {
                final Button rollback = Ui.button(ctx.t("versions.previous.rollback"), Icons.Icon.ROTATE_CCW, "secondary", "small");
                rollback.setDisable(vm.rollingBackProperty().get());
                rollback.setOnAction(e -> vm.rollback(k.version()));
                row.getChildren().add(rollback);
            }
            keptList.getChildren().add(row);
        }
    }
}
