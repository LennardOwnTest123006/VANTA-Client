package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.core.install.OfficialProfileService;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.update.UpdateInfo;
import dev.vanta.launcher.ui.model.NavigationModel;
import dev.vanta.launcher.ui.model.SettingsViewModel;
import dev.vanta.launcher.ui.model.UpdateViewModel;
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
    private final Map<NavigationModel.Page, Node> headers = new EnumMap<>(NavigationModel.Page.class);
    private final Map<NavigationModel.Page, Supplier<Node>> factories = new EnumMap<>(NavigationModel.Page.class);
    private final Sidebar sidebar;
    private final UpdateBanner banner;
    private final ToastLayer toasts;
    private final Runnable placeToasts = this::placeToasts;
    private Node currentPage;
    private Node currentHeader;
    private Runnable onInstallerOpened = () -> { };

    /**
     * @param ctx context
     */
    public MainWindow(final AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("app-shell");

        factories.put(NavigationModel.Page.HOME, () -> new HomePage(ctx, this::showSignIn, this::showClientUpdate, this::showOfficialProfile,
            this::playViaOfficialLauncher));
        factories.put(NavigationModel.Page.MODS, () -> new ModsPage(ctx, dialogs));
        factories.put(NavigationModel.Page.VERSIONS, () -> new VersionsPage(ctx));
        factories.put(NavigationModel.Page.LOGS, () -> new LogsPage(ctx));
        factories.put(NavigationModel.Page.SETTINGS, () -> new SettingsPage(ctx, this::applyTheme));
        factories.put(NavigationModel.Page.ABOUT, () -> new AboutPage(ctx));

        sidebar = new Sidebar(ctx, this::showSignIn, this::showAccounts);

        banner = new UpdateBanner(ctx, this::showUpdates);
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
        toasts = new ToastLayer(ctx.toasts(), ctx.t("common.dismiss"));
        getChildren().addAll(shell, dialogs, toasts);
        // In a small window the toasts lie top-right, below the banner and the page header. Both move (the banner
        // comes and goes, the header scrolls with its page and reflows with the window), so their lower edge is read
        // after every layout pass and handed to the toast layer; it is ignored while the toasts lie bottom-right.
        sceneProperty().addListener((obs, old, now) -> {
            if (old != null) {
                old.removePostLayoutPulseListener(placeToasts);
            }
            if (now != null) {
                now.addPostLayoutPulseListener(placeToasts);
            }
        });

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
            // Looked up on the page itself: through the ScrollPane the header is reachable only once its skin exists.
            headers.put(p, content.lookup(".page-header"));
            if (content instanceof LogsPage) {
                // The log list is virtualised and must own the vertical space.
                return content;
            }
            final ScrollPane scroll = new ScrollPane(content);
            scroll.setFitToWidth(true);
            // Pages that fill the window (hero layout, inner scrolling) stretch to the viewport but never below
            // their preferred height; the Versions and Mods pages simply flow and scroll.
            final boolean flows = p == NavigationModel.Page.VERSIONS || p == NavigationModel.Page.MODS;
            scroll.setFitToHeight(!flows);
            if (content instanceof Region region && !flows) {
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

    /** @return the toast layer */
    public ToastLayer toasts() {
        return toasts;
    }

    /**
     * @return the y coordinate (in this window's coordinates) below which the toasts lie when they are anchored
     *     top-right: just under the update banner and the current page's header, whichever reaches lower; the window edge
     *     distance when neither is shown
     */
    double toastTopInset() {
        double inset = ToastLayer.EDGE;
        if (banner.isVisible() && banner.getScene() != null) {
            inset = Math.max(inset, bottomOf(banner) + ToastLayer.EDGE / 2);
        }
        if (currentHeader != null && currentHeader.isVisible() && currentHeader.getScene() != null) {
            // The header scrolls with its page: once it has left the viewport its bottom lies above the banner's.
            inset = Math.max(inset, bottomOf(currentHeader) + ToastLayer.EDGE / 2);
        }
        return inset;
    }

    private double bottomOf(final Node node) {
        return sceneToLocal(node.localToScene(node.getBoundsInLocal())).getMaxY();
    }

    private void placeToasts() {
        final double inset = toastTopInset();
        if (Math.abs(inset - toasts.topInsetProperty().get()) > 0.5) {
            toasts.topInsetProperty().set(inset);
        }
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

    /** Opens the update dialog for the client update (only ever for an installed client). */
    public void showClientUpdate() {
        final UpdateInfo client = ctx.updates().clientUpdateProperty().get();
        if (client != null && ctx.session().installedClient().isPresent()) {
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
     * Asks before handing a verified installer to the operating system. A Linux app image archive, a Windows portable
     * zip or a jar is not an installer: the launcher shows it in its folder with instructions instead (for the portable
     * zip: close the launcher, then extract it into the folder that contains the portable folder, see
     * {@link dev.vanta.launcher.ui.model.UpdateViewModel#portableUpdateInstructions(String)}) and keeps running. Nothing but a Windows installer is
     * ever opened, and only after confirmation. Every variant shows the full path of the file
     * ({@code cache/updates/<version>/<asset name>}) and offers "Show in folder".
     *
     * @param installer verified installer path
     */
    public void confirmInstaller(final Path installer) {
        final String text = ctx.updates().downloadedInstructions(installer);
        final Runnable showFolder = () -> ctx.opener().openFolder(installer.getParent());
        if (UpdateViewModel.kindOf(installer) == UpdateViewModel.DownloadedFile.INSTALLER) {
            dialogs.show(new ConfirmDialog(ctx.t("update.confirm.title"), text, savedTo(installer),
                ctx.t("update.confirm.open"), ctx.t("update.confirm.cancel"),
                () -> ctx.opener().openFile(installer, () -> {
                    ctx.toasts().info(ctx.t("update.toast.opened.title"), ctx.t("update.toast.opened.message"));
                    onInstallerOpened.run();
                }), () -> { }, dialogs::close)
                .withExtraAction(ctx.t("update.confirm.showFolder"), Icons.Icon.FOLDER, showFolder));
            return;
        }
        // Portable folder, app image or jar: never unpacked or started by the launcher.
        dialogs.show(new ConfirmDialog(ctx.t("update.confirm.manual.title"), text, savedTo(installer), ctx.t("update.confirm.showFolder"),
            ctx.t("update.confirm.cancel"), showFolder, () -> { }, dialogs::close));
    }

    /**
     * @param file verified download
     * @return "Saved to" with the full path in a read-only field, so it can be selected and copied
     */
    private Node savedTo(final Path file) {
        final javafx.scene.control.TextField path = new javafx.scene.control.TextField(file.toAbsolutePath().toString());
        path.setEditable(false);
        path.getStyleClass().addAll("mono", "download-path");
        path.setAccessibleText(ctx.t("update.confirm.savedTo") + " " + file.toAbsolutePath());
        final VBox box = new VBox(6, Ui.label(ctx.t("update.confirm.savedTo"), "field-label"), path);
        box.getStyleClass().add("download-location");
        return box;
    }

    /**
     * "Use with the Minecraft Launcher": checks that the official launcher is closed (it reads profiles only when it
     * starts), reads what would be written, lists it in a confirmation dialog and runs the setup only after the user
     * confirmed.
     */
    public void showOfficialProfile() {
        startOfficialFlow(false);
    }

    /**
     * "PLAY via Minecraft Launcher": the same checks, then the profile is set up or updated (confirmation only the first
     * time, when the profile does not exist yet) and the official launcher is opened.
     */
    public void playViaOfficialLauncher() {
        startOfficialFlow(true);
    }

    private void startOfficialFlow(final boolean play) {
        ctx.home().checkOfficialLauncher(running -> {
            if (running.isEmpty()) {
                continueOfficialFlow(play);
                return;
            }
            showOfficialLauncherRunning(running, play);
        });
    }

    /**
     * The official launcher is open: ask the player to close it completely. "Check again" looks again; "Continue anyway"
     * writes the profile now (it appears after the next start of the Minecraft Launcher). Nothing is ever closed for them.
     *
     * @param running running launcher processes
     * @param play    whether the flow ends by opening the Minecraft Launcher
     */
    void showOfficialLauncherRunning(final java.util.List<String> running, final boolean play) {
        final String profile = OfficialProfileService.profileName(dev.vanta.launcher.LauncherVersion.MINECRAFT);
        final VBox processes = new VBox(4);
        for (String process : running) {
            processes.getChildren().add(Ui.label(process, "mono", "text-secondary"));
        }
        final VBox details = new VBox(10, processes, Ui.paragraph(ctx.t(ctx.backend().os().isWindows() ? "official.running.tip.windows"
            : ctx.backend().os().isMac() ? "official.running.tip.mac" : "official.running.tip"), "field-help"));
        details.getStyleClass().add("official-running-details");
        final ConfirmDialog dialog = new ConfirmDialog(ctx.t("official.running.title"),
            ctx.t("official.running.text", String.join(", ", running), profile), details,
            ctx.t("official.running.checkAgain"), ctx.t("official.confirm.cancel"), () -> startOfficialFlow(play), () -> { }, dialogs::close);
        dialog.withExtraAction(ctx.t("official.running.continue"), Icons.Icon.ALERT, () -> {
            dialogs.close();
            continueOfficialFlow(play);
        });
        dialog.node().getStyleClass().add("official-running");
        dialogs.show(dialog);
    }

    private void continueOfficialFlow(final boolean play) {
        ctx.home().prepareOfficialProfile(plan -> {
            if (play && plan.profileExists()) {
                // Already set up once and confirmed then: update it (only changed files are downloaded) and open the launcher.
                ctx.home().installOfficialProfile(true);
                return;
            }
            dialogs.show(new ConfirmDialog(ctx.t("official.confirm.title", plan.profileName()),
                ctx.t("official.confirm.text", plan.profileName(), plan.minecraftDir().toString(), plan.vantaClientVersion()), officialPlanDetails(plan),
                ctx.t(play ? "official.confirm.installAndOpen" : "official.confirm.install"), ctx.t("official.confirm.cancel"),
                () -> ctx.home().installOfficialProfile(play), () -> { }, dialogs::close));
        });
    }

    private Node officialPlanDetails(final OfficialProfileService.Plan plan) {
        final VBox list = new VBox(8);
        for (OfficialProfileService.PlannedFile file : plan.files()) {
            final String label = ctx.home().officialPlanLabel(file);
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
        final VBox box = new VBox(10, scroll);
        for (String note : plan.notes()) {
            box.getChildren().add(Ui.paragraph(ctx.t("official.confirm.packNote", note), "field-error", "text-small"));
        }
        box.getChildren().add(Ui.paragraph(ctx.t("official.confirm.note"), "text-muted", "text-small"));
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
        currentHeader = headers.get(page);
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
