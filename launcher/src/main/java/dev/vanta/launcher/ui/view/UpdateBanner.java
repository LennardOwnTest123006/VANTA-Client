package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.update.UpdateInfo;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Slim bar above the pages announcing a newer launcher or client. "What's new" opens the {@link UpdateDialog}.
 */
public final class UpdateBanner extends HBox {

    /**
     * @param ctx        context
     * @param showDialog opens the update dialog
     */
    public UpdateBanner(final AppContext ctx, final Runnable showDialog) {
        getStyleClass().add("banner");
        final Label title = Ui.label("", "banner-title");
        title.textProperty().bind(ctx.updates().bannerTextProperty());
        final Label sub = Ui.label("", "banner-text");
        sub.textProperty().bind(Bindings.createStringBinding(() -> {
            final UpdateInfo l = ctx.updates().launcherUpdateProperty().get();
            final UpdateInfo c = ctx.updates().clientUpdateProperty().get();
            final StringBuilder sb = new StringBuilder();
            if (l != null) {
                sb.append(ctx.t("update.banner.current", "VANTA Launcher " + LauncherVersion.VERSION));
            }
            if (c != null) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                final String installed = ctx.session().instance().map(InstanceInfo::vantaClientVersion).filter(v -> !v.isEmpty())
                    .map(v -> "VANTA Client " + v).orElse(ctx.t("client.card.notInstalled"));
                sb.append(ctx.t("update.banner.current", installed));
                if (!c.isDownloadable()) {
                    sb.append(" · ").append(ctx.t("update.banner.notDownloadable"));
                }
            }
            return sb.toString();
        }, ctx.updates().launcherUpdateProperty(), ctx.updates().clientUpdateProperty(), ctx.session().instanceProperty()));
        final VBox text = new VBox(2, title, sub);
        HBox.setHgrow(text, Priority.ALWAYS);
        final Button whatsNew = Ui.button(ctx.t("update.banner.whatsNew"), "secondary", "small");
        whatsNew.setOnAction(e -> showDialog.run());
        final Button dismiss = Ui.iconButton(Icons.Icon.CLOSE, ctx.t("update.banner.dismiss.accessible"), "ghost");
        dismiss.setOnAction(e -> ctx.updates().dismissBanner());
        getChildren().addAll(Icons.of(Icons.Icon.SPARKLES, 18, "banner-icon"), text, whatsNew, dismiss);
        setAlignment(Pos.CENTER_LEFT);
        Ui.bindVisible(this, ctx.updates().bannerVisibleProperty());
    }
}
