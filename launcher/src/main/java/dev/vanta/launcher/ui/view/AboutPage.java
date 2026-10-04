package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.LauncherVersion;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * About: version, build information, licenses, links and the non-affiliation notice.
 */
public final class AboutPage extends VBox {

    private final AppContext ctx;

    /**
     * @param ctx context
     */
    public AboutPage(final AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("page");
        setSpacing(18);

        final HBox header = Ui.pageHeader(ctx.t("about.title"), ctx.t("about.subtitle", LauncherVersion.VERSION));

        final HBox lockup = BrandMark.lockup(44, 22);
        final Label version = Ui.label(ctx.t("about.version.label") + " " + LauncherVersion.VERSION, "text-secondary");
        final Label status = Ui.label(LauncherVersion.statusLine(), "facts");
        final Button source = Ui.button(ctx.t("about.links.source"), Icons.Icon.CODE, "secondary", "small");
        source.setOnAction(e -> ctx.opener().browse(ctx.links().source()));
        final Button docs = Ui.button(ctx.t("about.links.docs"), Icons.Icon.FILE_TEXT, "secondary", "small");
        docs.setOnAction(e -> ctx.opener().browse(ctx.links().launcherDocs()));
        final Button support = Ui.button(ctx.t("about.links.support"), Icons.Icon.LIFE_BUOY, "secondary", "small");
        ctx.links().support().ifPresentOrElse(uri -> support.setOnAction(e -> ctx.opener().browse(uri)), () -> {
            support.setDisable(true);
            support.setTooltip(Ui.tooltip(ctx.t("nav.notConfigured.tooltip")));
        });
        final Button website = Ui.button(ctx.t("about.links.website"), Icons.Icon.GLOBE, "secondary", "small");
        ctx.links().website().ifPresentOrElse(uri -> website.setOnAction(e -> ctx.opener().browse(uri)), () -> {
            website.setDisable(true);
            website.setText(ctx.t("about.links.website") + " · " + ctx.t("nav.notConfigured"));
        });
        final HBox links = new HBox(8, source, docs, support, website);
        links.setAlignment(Pos.CENTER_LEFT);
        final VBox heroCard = Ui.card(lockup, Ui.vgap(2), version, status, Ui.vgap(6), links);
        heroCard.getStyleClass().add("card-elevated");

        final Map<String, String> build = new LinkedHashMap<>();
        build.put(ctx.t("about.build.minecraft"), LauncherVersion.MINECRAFT);
        build.put(ctx.t("about.build.fabricLoader"), LauncherVersion.FABRIC_LOADER);
        build.put(ctx.t("about.build.fabricApi"), LauncherVersion.FABRIC_API);
        build.put(ctx.t("about.build.javafx"), System.getProperty("javafx.runtime.version", System.getProperty("javafx.version", ctx.t("common.unknown"))));
        build.put(ctx.t("about.build.runtime"), Runtime.version().toString());
        build.put(ctx.t("about.build.os"), System.getProperty("os.name", "") + " " + System.getProperty("os.version", "") + " ("
            + System.getProperty("os.arch", "") + ")");
        build.put(ctx.t("about.build.dataDir"), ctx.backend().paths().dataDir().toString());
        final Button copy = Ui.button(ctx.t("about.copy"), Icons.Icon.COPY, "ghost", "small");
        copy.setOnAction(e -> {
            final StringBuilder sb = new StringBuilder("VANTA Launcher ").append(LauncherVersion.VERSION).append('\n');
            build.forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));
            if (ctx.opener().copy(sb.toString())) {
                ctx.toasts().success(ctx.t("about.copied"), "");
            }
        });
        final VBox buildCard = Ui.card(Ui.cardHeader(ctx.t("about.build.title"), copy));
        buildCard.setSpacing(8);
        build.forEach((k, v) -> {
            final Label value = Ui.label(v, "kv-value");
            value.setWrapText(true);
            value.setMaxWidth(380);
            value.setAlignment(Pos.CENTER_RIGHT);
            buildCard.getChildren().add(Ui.keyValue(k, value));
        });
        HBox.setHgrow(buildCard, Priority.ALWAYS);

        final VBox licenses = Ui.card(Ui.cardHeader(ctx.t("about.licenses.title"), null));
        licenses.setSpacing(8);
        for (String key : new String[] {"about.licenses.vanta", "about.licenses.fonts", "about.licenses.javafx", "about.licenses.gson",
            "about.licenses.jna"}) {
            final HBox row = new HBox(10, Icons.of(Icons.Icon.SCALE, 14, "icon-muted"), Ui.paragraph(ctx.t(key), "text-secondary"));
            row.setAlignment(Pos.CENTER_LEFT);
            licenses.getChildren().add(row);
        }
        HBox.setHgrow(licenses, Priority.ALWAYS);

        final HBox columns = new HBox(20, buildCard, licenses);
        columns.setAlignment(Pos.TOP_LEFT);

        final VBox legit = Ui.callout("accent", null, ctx.t("about.legit"), null);
        final VBox disclaimer = Ui.callout("", null, ctx.t("about.disclaimer"), null);

        final VBox content = new VBox(18, heroCard, columns, legit, disclaimer);
        content.setPadding(Ui.insets(0, 16, 8, 0));
        final ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(320);
        scroll.setMinHeight(0);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(header, scroll);
    }
}
