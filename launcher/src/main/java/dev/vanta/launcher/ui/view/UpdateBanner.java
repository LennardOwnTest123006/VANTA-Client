package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.install.InstalledClient;
import dev.vanta.launcher.core.update.UpdateInfo;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Optional;

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
                // The same installed state the client card and the update check use. A client update is never offered
                // without an installed client; should one slip through, the text still reads as a sentence.
                final Optional<InstalledClient> installed = ctx.session().installedClient();
                sb.append(installed.isEmpty() ? ctx.t("update.banner.clientNotInstalled")
                    : ctx.t("update.banner.current", installed.get().isDevelopmentBuild() ? ctx.t("client.card.dev")
                        : "VANTA Client " + installed.get().version()));
                if (!c.isDownloadable()) {
                    sb.append(" · ").append(ctx.t("update.banner.notDownloadable"));
                }
            }
            return sb.toString();
        }, ctx.updates().launcherUpdateProperty(), ctx.updates().clientUpdateProperty(), ctx.session().installedClientProperty()));
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
