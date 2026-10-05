package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.install.InstalledClient;
import dev.vanta.launcher.core.java.JavaInstall;
import dev.vanta.launcher.ui.model.HomeViewModel;
import dev.vanta.launcher.ui.model.LogLine;
import dev.vanta.launcher.ui.model.NavigationModel;
import dev.vanta.launcher.ui.model.UpdateViewModel;
import javafx.animation.Animation;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Home: hero card with PLAY, status and progress; side column with account, Java, client version and recent logs.
 * "Use with Minecraft Launcher" sets VANTA up as a profile of the official Minecraft Launcher; while Microsoft
 * sign-in is not configured a callout in the hero offers it as the way to play.
 */
public final class HomePage extends VBox {

    private static final int SNIPPET_LINES = 5;

    private final AppContext ctx;
    private final HomeViewModel vm;
    private final Runnable openSignIn;
    private final Runnable openUpdateDialog;
    private final Runnable openOfficialProfile;
    private final Circle statusDot = new Circle(4.5);
    private Animation pulse;

    /**
     * @param ctx                 context
     * @param openSignIn          opens the sign-in dialog
     * @param openUpdateDialog    opens the client update dialog
     * @param openOfficialProfile starts "Use with the Minecraft Launcher" (confirmation dialog first)
     */
    public HomePage(final AppContext ctx, final Runnable openSignIn, final Runnable openUpdateDialog, final Runnable openOfficialProfile) {
        this.ctx = ctx;
        this.vm = ctx.home();
        this.openSignIn = openSignIn;
        this.openUpdateDialog = openUpdateDialog;
        this.openOfficialProfile = openOfficialProfile;
        getStyleClass().add("page");
        setSpacing(20);

        final StackPane hero = hero();
        VBox.setVgrow(hero, Priority.ALWAYS);
        final VBox left = new VBox(14, hero, recentLogsCard());
        left.setMinWidth(380);
        HBox.setHgrow(left, Priority.ALWAYS);
        final VBox right = new VBox(14, accountCard(), javaCard(), clientCard());
        right.setMinWidth(270);
        right.setPrefWidth(318);
        right.setMaxWidth(330);
        final HBox columns = new HBox(22, left, right);
        columns.setAlignment(Pos.TOP_LEFT);
        columns.setMinHeight(0);
        VBox.setVgrow(columns, Priority.ALWAYS);
        getChildren().add(columns);
    }

    // ---------------------------------------------------------------- hero

    private StackPane hero() {
        final Label eyebrow = Ui.label(ctx.t("home.eyebrow"), "eyebrow");
        final Label title = Ui.paragraph("", "hero-title");
        title.textProperty().bind(vm.titleTextProperty());
        final Label lead = Ui.paragraph("", "hero-lead");
        lead.textProperty().bind(vm.leadTextProperty());
        lead.setMaxWidth(560);

        final Button play = Ui.button(ctx.t("home.play"), Icons.Icon.PLAY, "play-button");
        play.setAccessibleText(ctx.t("home.play.accessible"));
        play.disableProperty().bind(vm.playEnabledProperty().not());
        play.setOnAction(e -> vm.play());
        play.setDefaultButton(true);
        final Button cancel = Ui.button(ctx.t("home.cancel"), Icons.Icon.STOP, "secondary");
        cancel.setMinHeight(44);
        cancel.setOnAction(e -> vm.cancel());
        Ui.bindVisible(cancel, Bindings.createBooleanBinding(vm::canCancel, vm.stateProperty()));
        final Button stop = Ui.button(ctx.t("home.stop"), Icons.Icon.STOP, "secondary");
        stop.setMinHeight(44);
        stop.setOnAction(e -> vm.stopGame());
        Ui.bindVisible(stop, Bindings.createBooleanBinding(() -> vm.state() == HomeViewModel.State.RUNNING, vm.stateProperty()));
        final HBox playRow = new HBox(14, play, cancel, stop);
        playRow.setAlignment(Pos.CENTER_LEFT);

        final Label blockReason = Ui.label("", "block-reason");
        blockReason.textProperty().bind(vm.idleBlockReasonProperty());
        blockReason.setGraphic(Icons.of(Icons.Icon.INFO, 13, "icon-muted"));
        Ui.bindVisible(blockReason, Bindings.createBooleanBinding(() -> !vm.idleBlockReasonProperty().get().isEmpty(), vm.idleBlockReasonProperty()));

        final Label facts = Ui.paragraph("", "facts");
        facts.textProperty().bind(vm.factsTextProperty());

        statusDot.getStyleClass().addAll("status-dot", "idle");
        final Label statusText = Ui.label("", "status-text");
        statusText.textProperty().bind(vm.statusTextProperty());
        final HBox statusLine = new HBox(statusDot, statusText);
        statusLine.getStyleClass().add("status-line");
        vm.stateProperty().addListener((obs, old, now) -> applyState(now));
        applyState(vm.state());

        final ProgressBar bar = new ProgressBar(-1);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.progressProperty().bind(vm.progressProperty());
        final Label step = Ui.label("", "step-text");
        step.textProperty().bind(vm.stepTextProperty());
        final Label bytes = Ui.label("", "detail-text");
        bytes.textProperty().bind(vm.bytesTextProperty());
        final HBox stepRow = new HBox(12, step, Ui.spacer(), bytes);
        stepRow.setAlignment(Pos.CENTER_LEFT);
        final Label detail = Ui.label("", "detail-text");
        detail.textProperty().bind(vm.detailTextProperty());
        detail.setMaxWidth(560);
        final VBox progress = new VBox(8, stepRow, bar, detail);
        Ui.bindVisible(progress, vm.progressVisibleProperty());

        final VBox error = Ui.callout("danger", ctx.t("home.toast.error.title"), "", null);
        final Label errorText = (Label) error.getChildren().get(1);
        errorText.textProperty().bind(vm.errorTextProperty());
        Ui.bindVisible(error, Bindings.createBooleanBinding(() -> vm.state() == HomeViewModel.State.ERROR
            && !vm.errorTextProperty().get().isEmpty(), vm.stateProperty(), vm.errorTextProperty()));

        final Button verify = Ui.button(ctx.t("home.verify"), Icons.Icon.SHIELD, "secondary");
        verify.getStyleClass().add("verify-button");
        verify.setTooltip(Ui.tooltip(ctx.t("home.verify.tooltip")));
        verify.setOnAction(e -> vm.verify());
        verify.disableProperty().bind(Bindings.createBooleanBinding(() -> vm.state() == HomeViewModel.State.INSTALLING
            || vm.state() == HomeViewModel.State.VERIFYING || vm.state() == HomeViewModel.State.RUNNING, vm.stateProperty()));
        // Only an existing installation can be verified; a fresh launcher installs with PLAY or "Install now".
        Ui.bindVisible(verify, vm.verifyAvailableProperty());
        final Button openFolder = Ui.button(ctx.t("home.openFolder"), Icons.Icon.FOLDER, "ghost");
        openFolder.setOnAction(e -> ctx.opener().openFolder(ctx.backend().paths().instanceDir()));
        final Button official = officialButton("secondary");
        official.getStyleClass().add("official-button");
        verify.setMinWidth(Region.USE_PREF_SIZE);
        openFolder.setMinWidth(Region.USE_PREF_SIZE);
        official.setMinWidth(Region.USE_PREF_SIZE);
        final javafx.scene.layout.FlowPane secondary = new javafx.scene.layout.FlowPane(10, 10, verify, official, openFolder);
        secondary.setAlignment(Pos.CENTER_LEFT);

        // Microsoft sign-in needs an application id approved by Mojang; without it the official launcher is the way to play.
        final VBox noSignIn = Ui.callout("accent", ctx.t("home.official.callout.title"),
            ctx.t("home.official.callout.text", LauncherVersion.MINECRAFT), officialButton("primary", "small"));
        noSignIn.getStyleClass().add("official-callout");
        final javafx.beans.binding.BooleanBinding calloutShown = Bindings.createBooleanBinding(() -> !ctx.session().signInConfiguredProperty().get()
                && ctx.session().loadedProperty().get() && ctx.session().account().isEmpty() && !vm.busy()
                && vm.officialDoneTextProperty().get().isEmpty(),
            ctx.session().signInConfiguredProperty(), ctx.session().loadedProperty(), ctx.session().accountProperty(), vm.stateProperty(),
            vm.officialDoneTextProperty());
        Ui.bindVisible(noSignIn, calloutShown);
        // The callout carries the same action; the secondary button returns when the callout is gone.
        Ui.bindVisible(official, calloutShown.not());
        final VBox officialDone = Ui.callout("accent", ctx.t("official.done.title"), "", null);
        final Label officialDoneText = (Label) officialDone.getChildren().get(1);
        officialDoneText.textProperty().bind(vm.officialDoneTextProperty());
        Ui.bindVisible(officialDone, Bindings.createBooleanBinding(() -> !vm.officialDoneTextProperty().get().isEmpty() && !vm.busy(),
            vm.officialDoneTextProperty(), vm.stateProperty()));

        final VBox top = new VBox(eyebrow, Ui.vgap(6), title, Ui.vgap(8), lead);
        final VBox middle = new VBox(playRow, Ui.vgap(8), blockReason, Ui.vgap(18), facts, Ui.vgap(10), statusLine);
        final VBox bottom = new VBox(progress, error, noSignIn, officialDone, Ui.vgap(14), secondary);
        final Region flexTop = Ui.spacer();
        flexTop.setMinHeight(22);
        flexTop.setMaxHeight(64);
        final Region flexBottom = Ui.spacer();
        flexBottom.setMinHeight(10);
        final VBox content = new VBox(top, flexTop, middle, flexBottom, bottom);
        content.setMinHeight(0);
        final StackPane watermark = BrandMark.mark(260);
        watermark.getStyleClass().add("hero-watermark");
        watermark.setMouseTransparent(true);
        final StackPane hero = new StackPane(watermark, content);
        hero.getStyleClass().add("hero-card");
        hero.setMinHeight(0);
        StackPane.setAlignment(watermark, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(watermark, Ui.insets(0, -70, -60, 0));
        StackPane.setAlignment(content, Pos.TOP_LEFT);
        final javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.setArcWidth(36);
        clip.setArcHeight(36);
        clip.widthProperty().bind(hero.widthProperty());
        clip.heightProperty().bind(hero.heightProperty());
        hero.setClip(clip);
        return hero;
    }

    private Button officialButton(final String... styleClasses) {
        final Button button = Ui.button(ctx.t("home.official.button"), Icons.Icon.LAYERS, styleClasses);
        button.setTooltip(Ui.tooltip(ctx.t("home.official.tooltip")));
        button.setOnAction(e -> openOfficialProfile.run());
        button.disableProperty().bind(Bindings.createBooleanBinding(vm::busy, vm.stateProperty()));
        return button;
    }

    private void applyState(final HomeViewModel.State state) {
        statusDot.getStyleClass().removeAll("idle", "ready", "busy", "running", "error");
        statusDot.getStyleClass().add(switch (state) {
            case READY -> "ready";
            case INSTALLING, VERIFYING -> "busy";
            case RUNNING -> "running";
            case ERROR -> "error";
            default -> "idle";
        });
        if (pulse != null) {
            pulse.stop();
            pulse = null;
            statusDot.setOpacity(1);
        }
        if (state == HomeViewModel.State.INSTALLING || state == HomeViewModel.State.VERIFYING || state == HomeViewModel.State.RUNNING) {
            pulse = Motion.pulse(statusDot);
        }
    }

    // ---------------------------------------------------------------- account card

    private VBox accountCard() {
        final VBox card = Ui.card();
        final Runnable render = () -> renderAccount(card);
        ctx.session().accountProperty().addListener((obs, old, now) -> render.run());
        ctx.session().signInConfiguredProperty().addListener((obs, old, now) -> render.run());
        ctx.session().offlineAllowedProperty().addListener((obs, old, now) -> render.run());
        ctx.session().settingsProperty().addListener((obs, old, now) -> render.run());
        render.run();
        return card;
    }

    private void renderAccount(final VBox card) {
        card.getChildren().clear();
        final Optional<Account> account = ctx.session().account();
        if (account.isPresent()) {
            final Account a = account.get();
            card.getChildren().add(Ui.cardHeader(ctx.t("account.card.title"), Ui.badge(vm.accountTypeLabel(a), "success")));
            final InitialsAvatar avatar = new InitialsAvatar(a.name(), 40);
            final Label name = Ui.label(a.name(), "value-medium");
            name.setMaxWidth(200);
            final Label uuid = Ui.label(a.uuid(), "text-muted", "mono");
            uuid.setStyle("-fx-font-size: 10.5px;");
            uuid.setMaxWidth(Double.MAX_VALUE);
            final VBox names = new VBox(2, name, uuid);
            names.setMinWidth(0);
            HBox.setHgrow(names, Priority.ALWAYS);
            final Button signOut = Ui.button(ctx.t("account.signOut"), Icons.Icon.LOG_OUT, "secondary", "small");
            signOut.setOnAction(e -> ctx.session().signOut(() -> ctx.toasts().info(ctx.t("account.signedOut.toast"), "")));
            final HBox row = new HBox(12, avatar, names);
            row.setAlignment(Pos.CENTER_LEFT);
            final HBox actions = new HBox(8, signOut);
            card.getChildren().addAll(row, actions);
        } else {
            card.getChildren().add(Ui.cardHeader(ctx.t("account.card.title"), null));
            if (ctx.session().signInConfiguredProperty().get()) {
                card.getChildren().add(Ui.paragraph(ctx.t("account.card.signedOut"), "card-caption"));
                final Button signIn = Ui.button(ctx.t("account.signIn"), Icons.Icon.KEY, "primary");
                signIn.setMaxWidth(Double.MAX_VALUE);
                signIn.setOnAction(e -> openSignIn.run());
                card.getChildren().add(signIn);
            } else {
                final Button how = Ui.button(ctx.t("account.card.howToConfigure"), Icons.Icon.EXTERNAL, "secondary", "small");
                how.setTooltip(Ui.tooltip(ctx.t("account.card.howToConfigure.tooltip")));
                how.setOnAction(e -> ctx.opener().browse(ctx.links().clientIdDocs()));
                final Button settings = Ui.button(ctx.t("nav.settings"), Icons.Icon.SLIDERS, "secondary", "small");
                settings.setTooltip(Ui.tooltip(ctx.t("account.card.settings.tooltip")));
                settings.setOnAction(e -> ctx.navigation().navigate(NavigationModel.Page.SETTINGS));
                card.getChildren().add(Ui.callout("warning", null, ctx.t("account.card.notConfigured"), stackedActions(how, settings)));
            }
        }
        if (ctx.session().settings().developerMode() && ctx.session().offlineAllowedProperty().get()
            && (account.isEmpty() || account.get().type() == dev.vanta.launcher.core.auth.AccountType.MICROSOFT)) {
            final Button offline = Ui.button(ctx.t("account.offlineSession"), Icons.Icon.CODE, "ghost", "small");
            offline.setTooltip(Ui.tooltip(ctx.t("account.offlineSession.tooltip")));
            offline.setOnAction(e -> vm.startOfflineSession());
            final javafx.scene.Node last = card.getChildren().get(card.getChildren().size() - 1);
            if (last instanceof HBox actions && account.isPresent()) {
                actions.getChildren().add(offline);
            } else {
                card.getChildren().add(offline);
            }
        }
    }

    // ---------------------------------------------------------------- java card

    private VBox javaCard() {
        final VBox card = Ui.card();
        final Runnable render = () -> renderJava(card);
        ctx.session().javaProperty().addListener((obs, old, now) -> render.run());
        ctx.session().javaInstalls().addListener((javafx.collections.ListChangeListener<JavaInstall>) c -> render.run());
        ctx.session().detectingJavaProperty().addListener((obs, old, now) -> render.run());
        vm.javaInstallingProperty().addListener((obs, old, now) -> render.run());
        render.run();
        return card;
    }

    private void renderJava(final VBox card) {
        card.getChildren().clear();
        final Button refresh = Ui.iconButton(Icons.Icon.REFRESH, ctx.t("java.card.refresh"), "ghost");
        refresh.setOnAction(e -> ctx.session().refreshJava());
        refresh.setDisable(ctx.session().detectingJavaProperty().get());
        card.getChildren().add(Ui.cardHeader(ctx.t("java.card.title"), refresh));
        final List<JavaInstall> all = ctx.session().javaInstalls();
        final Optional<JavaInstall> picked = ctx.session().java();
        if (ctx.session().detectingJavaProperty().get() && all.isEmpty()) {
            card.getChildren().add(Ui.paragraph(ctx.t("java.card.detecting"), "card-caption"));
            return;
        }
        if (picked.isPresent()) {
            final JavaInstall j = picked.get();
            final Label version = Ui.label("Java " + j.version(), "value-large");
            final Label vendor = Ui.label(ctx.t("java.card.vendor", j.vendor().isEmpty() ? ctx.t("common.unknown") : j.vendor(),
                ctx.t(j.is64Bit() ? "java.card.bits64" : "java.card.bits32")), "card-caption");
            final Label path = Ui.label(j.home().toString(), "text-muted", "mono");
            path.setStyle("-fx-font-size: 11px;");
            path.setMaxWidth(270);
            path.setTooltip(Ui.tooltip(j.home().toString()));
            final HBox head = new HBox(10, version, Ui.spacer(), Ui.badge(ctx.t("java.card.inUse"), "accent"));
            head.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().addAll(head, vendor, path);
        } else {
            card.getChildren().add(Ui.callout("warning", null, ctx.t("java.card.none", Integer.toString(LauncherVersion.JAVA_MAJOR)), null));
        }
        final long others = all.stream().filter(j -> picked.isEmpty() || !j.home().equals(picked.get().home())).count();
        if (others > 0) {
            final Label count = Ui.label(ctx.t(all.size() == 1 ? "java.card.detected" : "java.card.detectedPlural", Integer.toString(all.size())),
                "text-muted", "text-small");
            card.getChildren().add(count);
            int shown = 0;
            for (JavaInstall j : all) {
                if (picked.isPresent() && j.home().equals(picked.get().home())) {
                    continue;
                }
                if (shown++ >= 3) {
                    break;
                }
                final Label text = Ui.label("Java " + j.version() + " · " + (j.vendor().isEmpty() ? ctx.t("common.unknown") : j.vendor()),
                    "text-secondary", "text-small");
                text.setMaxWidth(200);
                text.setTooltip(Ui.tooltip(j.describe()));
                final Button use = Ui.button(ctx.t("java.card.pick"), "ghost", "small");
                use.setDisable(!j.satisfies(LauncherVersion.JAVA_MAJOR));
                use.setOnAction(e -> ctx.session().selectJava(j, () -> ctx.toasts().success(ctx.t("java.toast.selected.title"),
                    ctx.t("java.toast.selected.message", j.describe()))));
                final HBox row = new HBox(8, text, Ui.spacer(), use);
                row.setAlignment(Pos.CENTER_LEFT);
                card.getChildren().add(row);
            }
        }
        final Button install = Ui.button(vm.javaInstallingProperty().get()
            ? ctx.t("java.card.installing", Integer.toString(LauncherVersion.JAVA_MAJOR))
            : ctx.t("java.card.install", Integer.toString(LauncherVersion.JAVA_MAJOR)), Icons.Icon.DOWNLOAD,
            picked.isPresent() ? "secondary" : "primary", "small");
        install.setDisable(vm.javaInstallingProperty().get());
        install.setOnAction(e -> vm.installJava());
        install.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().add(install);
    }

    // ---------------------------------------------------------------- client card

    private VBox clientCard() {
        final VBox card = Ui.card();
        card.getStyleClass().add("client-card");
        final Runnable render = () -> renderClient(card);
        ctx.session().instanceProperty().addListener((obs, old, now) -> render.run());
        ctx.session().installedClientProperty().addListener((obs, old, now) -> render.run());
        ctx.session().accountProperty().addListener((obs, old, now) -> render.run());
        ctx.session().signInConfiguredProperty().addListener((obs, old, now) -> render.run());
        ctx.updates().clientAvailabilityProperty().addListener((obs, old, now) -> render.run());
        ctx.updates().checkingProperty().addListener((obs, old, now) -> render.run());
        ctx.updates().latestClientVersionProperty().addListener((obs, old, now) -> render.run());
        ctx.updates().latestClientDownloadableProperty().addListener((obs, old, now) -> render.run());
        render.run();
        return card;
    }

    private void renderClient(final VBox card) {
        card.getChildren().clear();
        // The one installed-state source shared with the update check: instance.json / the jar actually in mods/.
        final Optional<InstalledClient> installed = ctx.session().installedClient();
        final UpdateViewModel updates = ctx.updates();
        final UpdateViewModel.ClientAvailability availability = updates.clientAvailabilityProperty().get();
        final String latestVersion = updates.latestClientVersionProperty().get();

        final Node badge;
        if (installed.isEmpty()) {
            badge = Ui.badge(ctx.t("versions.status.notInstalled"), "warning");
        } else if (availability == UpdateViewModel.ClientAvailability.UPDATE_AVAILABLE) {
            badge = Ui.badge(ctx.t("client.card.updateAvailable", latestVersion), "accent");
        } else if (availability == UpdateViewModel.ClientAvailability.UP_TO_DATE) {
            badge = Ui.badge(ctx.t("client.card.upToDate"), "success");
        } else {
            badge = Ui.badge(ctx.t("versions.status.installed"), "success");
        }
        card.getChildren().add(Ui.cardHeader(ctx.t("client.card.title"), badge));

        final String installedText = installed.map(c -> c.isDevelopmentBuild() ? ctx.t("client.card.dev")
            : c.version().isEmpty() ? ctx.t("common.unknown") : c.version()).orElse(ctx.t("client.card.notInstalled"));
        final Label installedLabel = Ui.label(installedText, "kv-value");
        installedLabel.getStyleClass().add("client-installed");
        card.getChildren().add(Ui.keyValue(ctx.t("client.card.installed"), installedLabel));

        final String latest;
        if (updates.checkingProperty().get()) {
            latest = ctx.t("client.card.checking");
        } else {
            latest = switch (availability) {
                case NOT_CONFIGURED -> ctx.t("client.card.unknownLatest");
                case UP_TO_DATE, UPDATE_AVAILABLE -> latestVersion.isEmpty() ? ctx.t("common.unknown") : latestVersion;
                case NOT_INSTALLED -> latestVersion.isEmpty() ? ctx.t("common.unknown")
                    : updates.latestClientDownloadableProperty().get() ? latestVersion : ctx.t("client.card.announced", latestVersion);
                case ANNOUNCED -> ctx.t("client.card.announced", latestVersion);
                case NOT_PUBLISHED -> ctx.t("client.card.notPublished");
                case FAILED -> ctx.t("common.notAvailable");
                default -> ctx.t("common.unknown");
            };
        }
        final Label latestLabel = Ui.label(latest, "kv-value");
        latestLabel.setWrapText(true);
        latestLabel.setMaxWidth(190);
        latestLabel.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().add(Ui.keyValue(ctx.t("client.card.latest"), latestLabel));

        if (availability == UpdateViewModel.ClientAvailability.NOT_CONFIGURED) {
            final Button settings = Ui.button(ctx.t("nav.settings"), Icons.Icon.SLIDERS, "ghost", "small");
            settings.setOnAction(e -> ctx.navigation().navigate(NavigationModel.Page.SETTINGS));
            card.getChildren().add(settings);
        } else if (installed.isEmpty()) {
            // Nothing to update: offer the regular install (what PLAY installs) or the official Minecraft Launcher.
            final boolean canPlayHere = ctx.session().playPossible();
            card.getChildren().add(Ui.paragraph(canPlayHere ? ctx.t("client.card.notInstalled.hint.play", LauncherVersion.MINECRAFT)
                : ctx.t("client.card.notInstalled.hint.official", LauncherVersion.MINECRAFT), "card-caption"));
            final VBox actions = new VBox(8);
            if (canPlayHere) {
                final Button install = Ui.button(ctx.t("client.card.install"), Icons.Icon.DOWNLOAD, "primary", "small");
                install.getStyleClass().add("client-install");
                install.setTooltip(Ui.tooltip(ctx.t("client.card.install.tooltip", LauncherVersion.MINECRAFT)));
                install.setMaxWidth(Double.MAX_VALUE);
                install.setOnAction(e -> vm.install());
                install.disableProperty().bind(Bindings.createBooleanBinding(vm::busy, vm.stateProperty()));
                actions.getChildren().add(install);
            }
            final Button official = officialButton(canPlayHere ? "secondary" : "primary", "small");
            official.getStyleClass().add("client-official");
            official.setMaxWidth(Double.MAX_VALUE);
            actions.getChildren().add(official);
            card.getChildren().add(actions);
            if (availability == UpdateViewModel.ClientAvailability.FAILED && !updates.checkErrorProperty().get().isEmpty()) {
                card.getChildren().add(Ui.paragraph(updates.checkErrorProperty().get(), "field-error"));
            }
        } else if (availability == UpdateViewModel.ClientAvailability.UPDATE_AVAILABLE) {
            final Button update = Ui.button(ctx.t("client.card.update"), Icons.Icon.ARROW_UP_CIRCLE, "primary", "small");
            update.getStyleClass().add("client-update");
            update.setMaxWidth(Double.MAX_VALUE);
            update.setOnAction(e -> openUpdateDialog.run());
            card.getChildren().add(update);
        } else if (availability == UpdateViewModel.ClientAvailability.FAILED && !updates.checkErrorProperty().get().isEmpty()) {
            card.getChildren().add(Ui.paragraph(updates.checkErrorProperty().get(), "field-error"));
        }
    }

    /**
     * Small action buttons stacked at the full card width, so each keeps its full label ("How to co…" and "S…" at
     * 1120×720 in 1.0.0) at every window size; the tooltip says what the button does.
     *
     * @param buttons buttons
     * @return column of buttons
     */
    private static VBox stackedActions(final Button... buttons) {
        final VBox column = new VBox(6);
        column.getStyleClass().add("stacked-actions");
        for (Button b : buttons) {
            b.setMaxWidth(Double.MAX_VALUE);
            b.setMinWidth(Region.USE_PREF_SIZE);
            column.getChildren().add(b);
        }
        return column;
    }

    // ---------------------------------------------------------------- recent logs

    private VBox recentLogsCard() {
        final Hyperlink open = new Hyperlink(ctx.t("home.recentLogs.open"));
        open.setOnAction(e -> ctx.navigation().navigate(NavigationModel.Page.LOGS));
        final VBox lines = new VBox(2);
        lines.getStyleClass().add("log-snippet");
        final Label empty = Ui.label(ctx.t("home.recentLogs.empty"), "text-muted", "text-small");
        final VBox card = Ui.card(Ui.cardHeader(ctx.t("home.recentLogs.title"), open), lines);
        final Runnable render = () -> {
            final List<LogLine> tail = ctx.launcherLog().tail(SNIPPET_LINES);
            lines.getChildren().clear();
            if (tail.isEmpty()) {
                lines.getChildren().add(empty);
                return;
            }
            for (LogLine line : tail) {
                final Label l = Ui.label(line.text().lines().findFirst().orElse(""), "log-snippet-line",
                    "log-" + line.level().name().toLowerCase(Locale.ROOT));
                l.setMaxWidth(Double.MAX_VALUE);
                lines.getChildren().add(l);
            }
        };
        ctx.launcherLog().addListener(line -> ctx.executors().onUi(render));
        render.run();
        return card;
    }
}
