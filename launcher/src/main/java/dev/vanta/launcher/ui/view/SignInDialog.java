package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.ui.model.NavigationModel;
import dev.vanta.launcher.ui.model.SignInViewModel;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URI;
import java.time.Instant;

/**
 * Microsoft device code sign-in. Shows the code large, offers Copy and "Open microsoft.com/link", counts down the
 * code's lifetime and maps every failure to a friendly explanation.
 */
public final class SignInDialog implements DialogLayer.Dialog {

    private final AppContext ctx;
    private final SignInViewModel vm;
    private final Runnable close;
    private final VBox card = new VBox();
    private final Timeline ticker;

    /**
     * @param ctx   context
     * @param close closes the dialog
     */
    public SignInDialog(final AppContext ctx, final Runnable close) {
        this.ctx = ctx;
        this.vm = ctx.signIn();
        this.close = close;
        card.getStyleClass().add("dialog");
        card.setSpacing(16);

        final Label title = Ui.label(ctx.t("signin.title"), "dialog-title");
        final Button closeButton = Ui.iconButton(Icons.Icon.CLOSE, ctx.t("signin.close"), "ghost");
        closeButton.setOnAction(e -> close.run());
        final HBox header = new HBox(12, title, Ui.spacer(), closeButton);
        header.setAlignment(Pos.CENTER_LEFT);

        // --- waiting block: intro, code, actions
        final Label intro = Ui.paragraph(ctx.t("signin.intro"), "dialog-text");
        final Label code = Ui.label("", "code-text");
        code.textProperty().bind(Bindings.createStringBinding(() -> spaced(vm.userCodeProperty().get()), vm.userCodeProperty()));
        code.accessibleTextProperty().bind(Bindings.createStringBinding(() -> ctx.t("signin.code.accessible", vm.userCodeProperty().get()),
            vm.userCodeProperty()));
        final StackPane codeBox = new StackPane(code);
        codeBox.getStyleClass().add("code-display");
        codeBox.setMaxWidth(Double.MAX_VALUE);
        final Button copy = Ui.button(ctx.t("signin.copy"), Icons.Icon.COPY, "secondary");
        copy.setOnAction(e -> {
            if (ctx.opener().copy(vm.userCodeProperty().get())) {
                ctx.toasts().success(ctx.t("signin.copied"), vm.userCodeProperty().get());
            }
        });
        final Button open = Ui.button(ctx.t("signin.openLink"), Icons.Icon.EXTERNAL, "primary");
        open.setOnAction(e -> {
            final String uri = vm.verificationUriProperty().get();
            ctx.opener().browse(URI.create(uri.isBlank() ? ctx.links().microsoftLink().toString() : uri));
        });
        open.textProperty().bind(Bindings.createStringBinding(() -> {
            final String uri = vm.verificationUriProperty().get();
            if (uri.isBlank() || uri.equals(ctx.links().microsoftLink().toString())) {
                return ctx.t("signin.openLink");
            }
            return ctx.t("signin.openLinkAt", uri.replaceFirst("^https?://", ""));
        }, vm.verificationUriProperty()));
        final HBox actions = new HBox(10, copy, open);
        actions.setAlignment(Pos.CENTER_LEFT);
        final Label countdown = Ui.label("", "text-muted", "text-small");
        countdown.textProperty().bind(vm.countdownTextProperty());
        final VBox waiting = new VBox(14, intro, codeBox, actions, countdown);
        Ui.bindVisible(waiting, Bindings.createBooleanBinding(() -> vm.state() == SignInViewModel.State.WAITING, vm.stateProperty()));

        // --- status row (spinner + text) for STARTING/WAITING/DONE/FAILED
        final ProgressIndicator spinner = new ProgressIndicator(-1);
        spinner.setPrefSize(16, 16);
        spinner.setMinSize(16, 16);
        spinner.setMaxSize(16, 16);
        Ui.bindVisible(spinner, Bindings.createBooleanBinding(() -> vm.state() == SignInViewModel.State.STARTING
            || vm.state() == SignInViewModel.State.WAITING, vm.stateProperty()));
        final Node ok = Icons.of(Icons.Icon.CHECK_CIRCLE, 16, "icon-success");
        Ui.bindVisible(ok, Bindings.createBooleanBinding(() -> vm.state() == SignInViewModel.State.DONE, vm.stateProperty()));
        final Node bad = Icons.of(Icons.Icon.ALERT, 16, "icon-danger");
        Ui.bindVisible(bad, Bindings.createBooleanBinding(() -> vm.state() == SignInViewModel.State.FAILED, vm.stateProperty()));
        final Label status = Ui.paragraph("", "dialog-text");
        status.textProperty().bind(vm.statusTextProperty());
        final HBox statusRow = new HBox(10, spinner, ok, bad, status);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        Ui.bindVisible(statusRow, Bindings.createBooleanBinding(() -> vm.state() != SignInViewModel.State.NOT_CONFIGURED
            && vm.state() != SignInViewModel.State.IDLE, vm.stateProperty()));

        // --- not configured block
        final Button openSettings = Ui.button(ctx.t("signin.notConfigured.openSettings"), Icons.Icon.SLIDERS, "primary");
        openSettings.setOnAction(e -> {
            close.run();
            ctx.navigation().navigate(NavigationModel.Page.SETTINGS);
        });
        final Button docs = Ui.button(ctx.t("signin.notConfigured.docs"), Icons.Icon.EXTERNAL, "secondary");
        docs.setOnAction(e -> ctx.opener().browse(ctx.links().clientIdDocs()));
        final HBox configActions = new HBox(10, openSettings, docs);
        final VBox notConfigured = Ui.callout("warning", ctx.t("signin.notConfigured.title"), ctx.t("signin.notConfigured.text"), configActions);
        Ui.bindVisible(notConfigured, Bindings.createBooleanBinding(() -> vm.state() == SignInViewModel.State.NOT_CONFIGURED, vm.stateProperty()));

        // --- footer
        final Button retry = Ui.button(ctx.t("signin.retry"), Icons.Icon.REFRESH, "secondary");
        retry.setOnAction(e -> vm.begin());
        Ui.bindVisible(retry, Bindings.createBooleanBinding(() -> vm.state() == SignInViewModel.State.FAILED, vm.stateProperty()));
        final Button cancel = Ui.button("", "ghost");
        cancel.textProperty().bind(Bindings.createStringBinding(() -> vm.state() == SignInViewModel.State.DONE
            || vm.state() == SignInViewModel.State.FAILED || vm.state() == SignInViewModel.State.NOT_CONFIGURED
            ? ctx.t("signin.close") : ctx.t("signin.cancel"), vm.stateProperty()));
        cancel.setOnAction(e -> close.run());
        cancel.setCancelButton(true);
        final HBox footer = new HBox(10, Ui.spacer(), retry, cancel);
        footer.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(header, notConfigured, waiting, statusRow, footer);

        ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> vm.tick(Instant.now(ctx.backend().clock()))));
        ticker.setCycleCount(Animation.INDEFINITE);

        vm.onSignedIn(this::signedIn);
    }

    private void signedIn(final Account account) {
        ctx.session().setAccount(account);
        ctx.toasts().success(ctx.t("signin.toast.title", account.name()), ctx.t("signin.toast.message"));
        final javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(Motion.reduced() ? Duration.millis(1) : Duration.millis(900));
        pause.setOnFinished(e -> close.run());
        pause.play();
    }

    @Override
    public Node node() {
        return card;
    }

    @Override
    public void onShown() {
        ticker.play();
        vm.begin();
    }

    @Override
    public void onClosed() {
        ticker.stop();
        vm.cancel();
    }

    @Override
    public boolean closable() {
        return true;
    }

    /**
     * @param code user code
     * @return code split in the middle with a wide space for readability (e.g. {@code ABCD  EFGH})
     */
    static String spaced(final String code) {
        if (code == null || code.length() < 6) {
            return code == null ? "" : code;
        }
        final int mid = (code.length() + 1) / 2;
        return code.substring(0, mid) + " " + code.substring(mid);
    }
}
