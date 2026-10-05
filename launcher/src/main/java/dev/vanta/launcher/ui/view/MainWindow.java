package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.core.install.OfficialProfileService;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.update.UpdateInfo;
import dev.vanta.launcher.ui.model.NavigationModel;
import dev.vanta.launcher.ui.model.SettingsViewModel;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Root layout: sidebar on the left, update banner and the current page on the right, dialog and toast layers on
 * top. Pages are created lazily and swapped with a short fade/slide.
 */
public final class MainWindow extends StackPane {

    private final AppContext ctx;
    private final StackPane pageHost = new StackPane();
    private final DialogLayer dialogs = new DialogLayer();
    private final Map<NavigationModel.Page, Node> pages = new EnumMap<>(NavigationModel.Page.class);
    private final Map<NavigationModel.Page, Supplier<Node>> factories = new EnumMap<>(NavigationModel.Page.class);
    private final Sidebar sidebar;
    private Node currentPage;
    private Runnable onInstallerOpened = () -> { };

    /**
     * @param ctx context
     */
    public MainWindow(final AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("app-shell");

        factories.put(NavigationModel.Page.HOME, () -> new HomePage(ctx, this::showSignIn, this::showClientUpdate, this::showOfficialProfile));
        factories.put(NavigationModel.Page.VERSIONS, () -> new VersionsPage(ctx));
        factories.put(NavigationModel.Page.LOGS, () -> new LogsPage(ctx));
        factories.put(NavigationModel.Page.SETTINGS, () -> new SettingsPage(ctx, this::applyTheme));
        factories.put(NavigationModel.Page.ABOUT, () -> new AboutPage(ctx));

        sidebar = new Sidebar(ctx, this::showSignIn, this::showAccounts);

        final UpdateBanner banner = new UpdateBanner(ctx, this::showUpdates);
        VBox.setMargin(banner, Ui.insets(18, 32, -6, 32));
        final Region glow = new Region();
        glow.getStyleClass().add("content-glow");
        glow.setMouseTransparent(true);
        pageHost.getStyleClass().add("content");
        pageHost.setMinHeight(0);
        pageHost.setMinWidth(0);
        VBox.setVgrow(pageHost, Priority.ALWAYS);
        final VBox column = new VBox(banner, pageHost);
        column.setMinHeight(0);
        column.setMinWidth(0);
        HBox.setHgrow(column, Priority.ALWAYS);
        final StackPane content = new StackPane(glow, column);
        content.setMinHeight(0);
        content.setMinWidth(0);
        HBox.setHgrow(content, Priority.ALWAYS);

        final HBox shell = new HBox(sidebar, content);
        shell.setMinHeight(0);
        shell.setMinWidth(0);
        final ToastLayer toasts = new ToastLayer(ctx.toasts(), ctx.t("common.dismiss"));
        StackPane.setAlignment(toasts, Pos.BOTTOM_RIGHT);
        toasts.setMaxHeight(Region.USE_PREF_SIZE);
        getChildren().addAll(shell, dialogs, toasts);

        ctx.navigation().currentProperty().addListener((obs, old, now) -> show(now, true));
        show(ctx.navigation().current(), false);
        applyTheme();
    }

    /**
     * @param page page
     * @return the page node (created on first use)
     */
    public Node page(final NavigationModel.Page page) {
        return pages.computeIfAbsent(page, p -> {
            final Node content = factories.get(p).get();
            if (content instanceof LogsPage) {
                // The log list is virtualised and must own the vertical space.
                return content;
            }
            final ScrollPane scroll = new ScrollPane(content);
            scroll.setFitToWidth(true);
            // Pages that fill the window (hero layout, inner scrolling) stretch to the viewport but never below
            // their preferred height; the Versions page simply flows and scrolls.
            scroll.setFitToHeight(p != NavigationModel.Page.VERSIONS);
            if (content instanceof Region region && p != NavigationModel.Page.VERSIONS) {
                region.setMinHeight(Region.USE_PREF_SIZE);
            }
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scroll.setMinHeight(0);
            scroll.setPannable(false);
            return scroll;
        });
    }

    /** @return the dialog layer */
    public DialogLayer dialogs() {
        return dialogs;
    }

    /** @return the sidebar */
    public Sidebar sidebar() {
        return sidebar;
    }

    /**
     * @param hook runs after a verified launcher installer was handed to the OS (the app then exits)
     */
    public void setOnInstallerOpened(final Runnable hook) {
        this.onInstallerOpened = hook;
    }

    /** Opens the sign-in dialog. */
    public void showSignIn() {
        dialogs.show(new SignInDialog(ctx, dialogs::close));
    }

    /** Opens the account list dialog. */
    public void showAccounts() {
        dialogs.show(new AccountDialog(ctx, dialogs::close, this::showSignIn));
    }

    /** Opens the update dialog for whatever update is available (launcher first). */
    public void showUpdates() {
        final UpdateInfo launcher = ctx.updates().launcherUpdateProperty().get();
        final UpdateInfo client = ctx.updates().clientUpdateProperty().get();
        final UpdateInfo pick = launcher != null ? launcher : client;
        if (pick != null) {
            showUpdate(pick);
        }
    }

    /** Opens the update dialog for the client update. */
    public void showClientUpdate() {
        final UpdateInfo client = ctx.updates().clientUpdateProperty().get();
        if (client != null) {
            showUpdate(client);
        }
    }

    /**
     * Opens the update dialog for a specific update.
     *
     * @param update update
     */
    public void showUpdate(final UpdateInfo update) {
        dialogs.show(new UpdateDialog(ctx, update, dialogs::close, this::confirmInstaller));
    }

    /**
     * Asks before handing a verified installer to the operating system. A Linux app image archive or a macOS jar is
     * not an installer: the launcher shows it in its folder with instructions instead and keeps running.
     *
     * @param installer verified installer path
     */
    public void confirmInstaller(final Path installer) {
        final String name = installer.getFileName().toString();
        final String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".msi") || lower.endsWith(".exe")) {
            dialogs.show(new ConfirmDialog(ctx.t("update.confirm.title"), ctx.t("update.confirm.text", name),
                ctx.t("update.confirm.open"), ctx.t("update.confirm.cancel"),
                () -> ctx.opener().openFile(installer, () -> {
                    ctx.toasts().info(ctx.t("update.toast.opened.title"), ctx.t("update.toast.opened.message"));
                    onInstallerOpened.run();
                }), () -> { }, dialogs::close));
            return;
        }
        final String text = lower.endsWith(".jar") ? ctx.t("update.confirm.jar.text", name) : ctx.t("update.confirm.archive.text", name);
        dialogs.show(new ConfirmDialog(ctx.t("update.confirm.manual.title"), text, ctx.t("update.confirm.showFolder"),
            ctx.t("update.confirm.cancel"), () -> ctx.opener().openFolder(installer.getParent()), () -> { }, dialogs::close));
    }

    /**
     * "Use with the Minecraft Launcher": reads what would be written, lists it in a confirmation dialog and runs the
     * setup only after the user confirmed.
     */
    public void showOfficialProfile() {
        ctx.home().prepareOfficialProfile(plan -> dialogs.show(new ConfirmDialog(ctx.t("official.confirm.title", plan.profileName()),
            ctx.t("official.confirm.text", plan.profileName(), plan.minecraftDir().toString(), plan.vantaClientVersion()), officialPlanDetails(plan),
            ctx.t("official.confirm.install"), ctx.t("official.confirm.cancel"), () -> ctx.home().installOfficialProfile(), () -> { },
            dialogs::close)));
    }

    private Node officialPlanDetails(final OfficialProfileService.Plan plan) {
        final VBox list = new VBox(8);
        for (OfficialProfileService.PlannedFile file : plan.files()) {
            final String label = ctx.home().officialPlanLabel(file.kind());
            final javafx.scene.control.Label what = Ui.paragraph(file.removal() ? ctx.t("official.plan.removedPrefix", label) : label, "field-label");
            final javafx.scene.control.Label where = Ui.paragraph(file.location(), "mono", "text-secondary");
            where.setStyle("-fx-font-size: 11px;");
            list.getChildren().add(new VBox(2, what, where));
        }
        final ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(310);
        scroll.setMaxHeight(330);
        final VBox box = new VBox(10, scroll, Ui.paragraph(ctx.t("official.confirm.note"), "text-muted", "text-small"));
        box.getStyleClass().add("official-plan");
        return box;
    }

    /** Applies the theme variant from the saved settings (high contrast on/off). */
    public void applyTheme() {
        final boolean contrast = SettingsViewModel.THEME_HIGH_CONTRAST.equals(ctx.backend().settings().theme());
        final Parent root = getScene() == null ? this : getScene().getRoot();
        root.getStyleClass().remove("high-contrast");
        getStyleClass().remove("high-contrast");
        if (contrast) {
            root.getStyleClass().add("high-contrast");
        }
    }

    private void show(final NavigationModel.Page page, final boolean animate) {
        final Node next = page(page);
        if (next == currentPage) {
            return;
        }
        final Node previous = currentPage;
        currentPage = next;
        if (page == NavigationModel.Page.VERSIONS) {
            ctx.versions().refresh();
        }
        pageHost.getChildren().setAll(next);
        if (animate && previous != null) {
            Motion.enter(next, null);
        } else {
            next.setOpacity(1);
        }
        ctx.rememberPage(page.name());
    }

    /**
     * Updates the game-related state of the window: the host decides whether to hide the window while playing.
     *
     * @return whether the player asked to keep the launcher open
     */
    public boolean keepLauncherOpen() {
        return ctx.backend().settings().keepLauncherOpen();
    }

    /**
     * @param product {@link ReleaseManifest#PRODUCT_LAUNCHER} or {@link ReleaseManifest#PRODUCT_CLIENT}
     * @return the pending update of that product, or null
     */
    public UpdateInfo pendingUpdate(final String product) {
        return ReleaseManifest.PRODUCT_LAUNCHER.equals(product) ? ctx.updates().launcherUpdateProperty().get()
            : ctx.updates().clientUpdateProperty().get();
    }
}
