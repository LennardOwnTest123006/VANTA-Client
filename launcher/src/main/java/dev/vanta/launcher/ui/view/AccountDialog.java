package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.core.auth.Account;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * Stored accounts: switch the active one, sign out, or add another through the sign-in dialog.
 */
public final class AccountDialog implements DialogLayer.Dialog {

    private final AppContext ctx;
    private final Runnable close;
    private final Runnable openSignIn;
    private final VBox card = new VBox();
    private final VBox list = new VBox(0);

    /**
     * @param ctx        context
     * @param close      closes the dialog
     * @param openSignIn opens the sign-in dialog
     */
    public AccountDialog(final AppContext ctx, final Runnable close, final Runnable openSignIn) {
        this.ctx = ctx;
        this.close = close;
        this.openSignIn = openSignIn;
        card.getStyleClass().add("dialog");
        final Label title = Ui.label(ctx.t("account.card.title"), "dialog-title");
        final Button closeButton = Ui.iconButton(Icons.Icon.CLOSE, ctx.t("common.close"), "ghost");
        closeButton.setOnAction(e -> close.run());
        final HBox header = new HBox(12, title, Ui.spacer(), closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        final Button add = Ui.button(ctx.t("account.signIn"), Icons.Icon.KEY, "primary");
        add.setOnAction(e -> {
            close.run();
            openSignIn.run();
        });
        final Button done = Ui.button(ctx.t("common.close"), "secondary");
        done.setOnAction(e -> close.run());
        done.setCancelButton(true);
        final HBox footer = new HBox(10, add, Ui.spacer(), done);
        footer.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(header, list, footer);
    }

    @Override
    public Node node() {
        return card;
    }

    @Override
    public void onShown() {
        list.getChildren().setAll(Ui.label(ctx.t("common.loading"), "text-muted"));
        ctx.session().loadAccounts(this::render);
    }

    private void render(final List<Account> accounts) {
        list.getChildren().clear();
        final Optional<Account> active = ctx.session().account();
        if (accounts.isEmpty()) {
            list.getChildren().add(Ui.paragraph(ctx.t("account.card.signedOut"), "dialog-text"));
            return;
        }
        boolean first = true;
        for (Account a : accounts) {
            final boolean isActive = active.isPresent() && active.get().uuid().equals(a.uuid());
            final InitialsAvatar avatar = new InitialsAvatar(a.name(), 36);
            final Label name = Ui.label(a.name(), "account-name");
            final Label type = Ui.label(ctx.home().accountTypeLabel(a), "account-sub");
            final VBox text = new VBox(2, name, type);
            HBox.setHgrow(text, Priority.ALWAYS);
            final HBox row = new HBox(12, avatar, text);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("list-row");
            if (first) {
                row.getStyleClass().add("first");
                first = false;
            }
            if (isActive) {
                row.getChildren().add(Ui.badge(ctx.t("versions.previous.active"), "accent"));
                final Button signOut = Ui.button(ctx.t("account.signOut"), Icons.Icon.LOG_OUT, "danger", "small");
                signOut.setOnAction(e -> ctx.session().signOut(() -> {
                    ctx.toasts().info(ctx.t("account.signedOut.toast"), "");
                    ctx.session().loadAccounts(this::render);
                }));
                row.getChildren().add(signOut);
            } else {
                final Button use = Ui.button(ctx.t("account.switch"), "secondary", "small");
                use.setOnAction(e -> {
                    ctx.session().switchAccount(a.uuid());
                    close.run();
                });
                row.getChildren().add(use);
            }
            list.getChildren().add(row);
        }
    }
}
