package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.update.UpdateInfo;
import dev.vanta.launcher.ui.model.MiniMarkdown;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Release notes and the Update / Download action for one {@link UpdateInfo}.
 */
public final class UpdateDialog implements DialogLayer.Dialog {

    private final AppContext ctx;
    private final UpdateInfo update;
    private final VBox card = new VBox();
    private final VBox notes = new VBox(6);
    private final Button action;

    /**
     * @param ctx                context
     * @param update             the update to describe
     * @param close              closes the dialog
     * @param onInstallerReady   receives a verified launcher installer (the caller confirms and opens it)
     */
    public UpdateDialog(final AppContext ctx, final UpdateInfo update, final Runnable close, final Consumer<Path> onInstallerReady) {
        this.ctx = ctx;
        this.update = update;
        card.getStyleClass().addAll("dialog", "wide");
        card.setSpacing(14);
        final boolean launcher = ReleaseManifest.PRODUCT_LAUNCHER.equals(update.product());

        final Label title = Ui.label(ctx.t(launcher ? "update.dialog.title.launcher" : "update.dialog.title.client",
            update.latestVersion().toString()), "dialog-title");
        final Button closeButton = Ui.iconButton(Icons.Icon.CLOSE, ctx.t("update.dialog.close"), "ghost");
        closeButton.setOnAction(e -> close.run());
        final HBox header = new HBox(12, title, Ui.spacer(), closeButton);
        header.setAlignment(Pos.CENTER_LEFT);

        final ReleaseManifest manifest = update.manifest();
        final Label meta = Ui.label(ctx.t("update.dialog.released", manifest == null || manifest.releaseDate() == null ? ctx.t("common.unknown")
            : manifest.releaseDate(), manifest == null ? "stable" : manifest.channel()), "text-muted", "text-small");
        final Label file = Ui.label(update.isDownloadable() ? ctx.updates().describeFile(update) : ctx.t("update.banner.notDownloadable"),
            "text-secondary", "text-small");
        final HBox metaRow = new HBox(14, meta, file);

        notes.getChildren().add(Ui.label(ctx.t("update.dialog.changelog.loading"), "text-muted"));
        final ScrollPane scroll = new ScrollPane(notes);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(260);
        scroll.setMaxHeight(300);
        VBox.setVgrow(scroll, Priority.SOMETIMES);
        notes.setPadding(Ui.insets(0, 12, 0, 0));

        final VBox known = manifest != null && manifest.hasNotes()
            ? Ui.callout("warning", ctx.t("update.dialog.notes"), manifest.notes(), null) : null;

        final Label verify = Ui.paragraph(ctx.t(update.isDownloadable() ? "update.dialog.verify" : "update.dialog.notDownloadable"),
            "text-muted", "text-small");

        final ProgressBar progress = new ProgressBar(-1);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.progressProperty().bind(ctx.updates().progressProperty());
        final Label progressText = Ui.label("", "detail-text");
        progressText.textProperty().bind(ctx.updates().progressTextProperty());
        final VBox progressBox = new VBox(6, progress, progressText);
        Ui.bindVisible(progressBox, ctx.updates().busyProperty());

        action = Ui.button(ctx.t(launcher ? "update.dialog.downloadInstaller" : "update.dialog.installClient"),
            launcher ? Icons.Icon.DOWNLOAD : Icons.Icon.ARROW_UP_CIRCLE, "primary");
        action.setDisable(!update.isDownloadable());
        action.disableProperty().bind(Bindings.createBooleanBinding(() -> !update.isDownloadable() || ctx.updates().busyProperty().get(),
            ctx.updates().busyProperty()));
        action.setOnAction(e -> {
            if (launcher) {
                ctx.updates().downloadLauncherUpdate(installer -> {
                    close.run();
                    onInstallerReady.accept(installer);
                });
            } else {
                ctx.updates().installClientUpdate(close);
            }
        });
        final Button browse = Ui.button(ctx.t("update.dialog.changelog.open"), Icons.Icon.EXTERNAL, "ghost");
        browse.setOnAction(e -> ctx.updates().changelogPage(update).ifPresent(ctx.opener()::browse));
        Ui.show(browse, ctx.updates().changelogPage(update).isPresent());
        final Button cancel = Ui.button(ctx.t("update.dialog.close"), "secondary");
        cancel.setOnAction(e -> close.run());
        cancel.setCancelButton(true);
        final HBox footer = new HBox(10, browse, Ui.spacer(), cancel, action);
        footer.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(header, metaRow, scroll);
        if (known != null) {
            card.getChildren().add(known);
        }
        card.getChildren().addAll(verify, progressBox, footer);
    }

    @Override
    public Node node() {
        return card;
    }

    @Override
    public void onShown() {
        ctx.updates().loadChangelog(update, this::renderMarkdown, error -> {
            notes.getChildren().setAll(Ui.paragraph(ctx.t("update.dialog.changelog.unavailable"), "text-muted"));
        });
        if (!action.isDisabled()) {
            action.requestFocus();
        }
    }

    @Override
    public boolean closable() {
        return !ctx.updates().busyProperty().get();
    }

    private void renderMarkdown(final String markdown) {
        final List<MiniMarkdown.Block> blocks = MiniMarkdown.parse(markdown);
        notes.getChildren().clear();
        if (blocks.isEmpty()) {
            notes.getChildren().add(Ui.paragraph(ctx.t("update.dialog.changelog.unavailable"), "text-muted"));
            return;
        }
        for (MiniMarkdown.Block block : blocks) {
            switch (block.kind()) {
                case HEADING_1 -> notes.getChildren().add(Ui.paragraph(block.text(), "markdown-h1"));
                case HEADING_2 -> notes.getChildren().add(Ui.paragraph(block.text(), "markdown-h2"));
                case HEADING_3 -> notes.getChildren().add(Ui.paragraph(block.text(), "markdown-h3"));
                case BULLET -> {
                    final Label dot = Ui.label("•", "markdown-bullet-dot");
                    final Label text = Ui.paragraph(block.text(), "markdown-bullet");
                    HBox.setHgrow(text, Priority.ALWAYS);
                    final HBox row = new HBox(8, dot, text);
                    row.setAlignment(Pos.TOP_LEFT);
                    notes.getChildren().add(row);
                }
                case CODE -> notes.getChildren().add(Ui.paragraph(block.text(), "markdown-code"));
                default -> notes.getChildren().add(Ui.paragraph(block.text(), "markdown-paragraph"));
            }
        }
    }
}
