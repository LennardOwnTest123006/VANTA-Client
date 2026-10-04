package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.ui.model.NavigationModel;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Left navigation: brand lockup, page items, external links and the account chip.
 */
public final class Sidebar extends VBox {

    private final AppContext ctx;
    private final Map<NavigationModel.Page, ToggleButton> items = new EnumMap<>(NavigationModel.Page.class);
    private final Map<NavigationModel.Page, Region> indicators = new EnumMap<>(NavigationModel.Page.class);
    private final ToggleGroup group = new ToggleGroup();
    private final InitialsAvatar avatar = new InitialsAvatar("", 36);
    private final Label accountName = Ui.label("", "account-name");
    private final Label accountSub = Ui.label("", "account-sub");
    private final HBox chip = new HBox();
    private final Button signIn;
    private final Button accountMenu;

    /**
     * @param ctx           context
     * @param onSignIn      opens the sign-in dialog
     * @param onAccountMenu opens the account actions (sign out / switch)
     */
    public Sidebar(final AppContext ctx, final Runnable onSignIn, final Runnable onAccountMenu) {
        this.ctx = ctx;
        getStyleClass().add("sidebar");
        setSpacing(2);
        setMinHeight(0);

        final HBox lockup = BrandMark.lockup(30, 15);
        final Label tagline = Ui.label(ctx.t("app.tagline", LauncherVersion.MINECRAFT), "brand-tagline");
        final VBox brand = new VBox(8, lockup, tagline);
        brand.getStyleClass().add("brand");

        getChildren().add(brand);
        getChildren().add(navItem(NavigationModel.Page.HOME, Icons.Icon.HOME));
        getChildren().add(navItem(NavigationModel.Page.VERSIONS, Icons.Icon.LAYERS));
        getChildren().add(navItem(NavigationModel.Page.LOGS, Icons.Icon.TERMINAL));
        getChildren().add(navItem(NavigationModel.Page.SETTINGS, Icons.Icon.SLIDERS));
        getChildren().add(navItem(NavigationModel.Page.ABOUT, Icons.Icon.INFO));

        final Region divider = new Region();
        divider.getStyleClass().add("sidebar-divider");
        VBox.setMargin(divider, Ui.insets(14, 12, 6, 12));
        getChildren().add(divider);
        getChildren().add(linkItem(ctx.t("nav.website"), Icons.Icon.GLOBE, ctx.links().website()));
        getChildren().add(linkItem(ctx.t("nav.support"), Icons.Icon.LIFE_BUOY, ctx.links().support()));

        getChildren().add(Ui.spacer());

        signIn = Ui.button(ctx.t("account.signIn"), Icons.Icon.KEY, "primary", "small");
        signIn.setOnAction(e -> onSignIn.run());
        signIn.setMaxWidth(Double.MAX_VALUE);
        accountMenu = Ui.iconButton(Icons.Icon.CHEVRON_DOWN, ctx.t("account.switch"), "ghost");
        accountMenu.setOnAction(e -> onAccountMenu.run());
        accountName.setMaxWidth(112);
        accountSub.setMaxWidth(112);
        chip.getStyleClass().add("account-chip");
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setSpacing(10);
        getChildren().add(chip);

        ctx.session().accountProperty().addListener((obs, old, now) -> renderAccount(Optional.ofNullable(now)));
        renderAccount(ctx.session().account());

        ctx.navigation().currentProperty().addListener((obs, old, now) -> select(now));
        select(ctx.navigation().current());
    }

    private Node navItem(final NavigationModel.Page page, final Icons.Icon icon) {
        final ToggleButton b = new ToggleButton(ctx.t(page.titleKey()));
        b.getStyleClass().add("nav-item");
        b.setGraphic(Icons.of(icon, 17, "nav-icon"));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setToggleGroup(group);
        b.setOnAction(e -> {
            b.setSelected(true);
            ctx.navigation().navigate(page);
        });
        // A toggle in a group must not be de-selectable by clicking it again.
        b.selectedProperty().addListener((obs, old, now) -> {
            if (!now && ctx.navigation().current() == page) {
                b.setSelected(true);
            }
        });
        items.put(page, b);
        final Region indicator = new Region();
        indicator.getStyleClass().add("nav-indicator");
        indicators.put(page, indicator);
        final HBox row = new HBox(6, indicator, b);
        row.getStyleClass().add("nav-row");
        HBox.setHgrow(b, Priority.ALWAYS);
        return row;
    }

    private Node linkItem(final String text, final Icons.Icon icon, final Optional<URI> target) {
        final Button b = new Button();
        b.getStyleClass().add("nav-item");
        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        b.setMaxWidth(Double.MAX_VALUE);
        final Label label = Ui.label(text, "nav-link-label");
        final VBox labels = new VBox(0, label);
        final HBox content = new HBox(12, Icons.of(icon, 17, "nav-icon"), labels, Ui.spacer());
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxWidth(Double.MAX_VALUE);
        b.setGraphic(content);
        b.setAccessibleText(text);
        final Region indicator = new Region();
        indicator.getStyleClass().add("nav-indicator");
        final HBox row = new HBox(6, indicator, b);
        row.getStyleClass().add("nav-row");
        HBox.setHgrow(b, Priority.ALWAYS);
        if (target.isPresent()) {
            content.getChildren().add(Icons.of(Icons.Icon.EXTERNAL, 13, "nav-external"));
            final String host = target.get().getHost() == null ? target.get().toString() : target.get().getHost();
            b.setTooltip(Ui.tooltip(ctx.t("nav.external.tooltip", host)));
            b.setOnAction(e -> ctx.opener().browse(target.get()));
        } else {
            labels.getChildren().add(Ui.label(ctx.t("nav.notConfigured"), "nav-link-note"));
            b.setDisable(true);
            b.setAccessibleText(text + ", " + ctx.t("nav.notConfigured"));
            // A disabled control does not show tooltips, so the row carries it.
            Tooltip.install(row, Ui.tooltip(ctx.t("nav.notConfigured.tooltip")));
            row.setPickOnBounds(true);
        }
        return row;
    }

    private void select(final NavigationModel.Page page) {
        items.forEach((p, b) -> b.setSelected(p == page));
        indicators.forEach((p, r) -> {
            r.getStyleClass().remove("active");
            if (p == page) {
                r.getStyleClass().add("active");
            }
        });
    }

    private void renderAccount(final Optional<Account> account) {
        chip.getChildren().clear();
        final VBox names = new VBox(1, accountName, accountSub);
        HBox.setHgrow(names, Priority.ALWAYS);
        if (account.isPresent()) {
            final Account a = account.get();
            avatar.setName(a.name());
            avatar.setAccessibleText(ctx.t("account.avatar.accessible", a.name()));
            accountName.setText(a.name());
            accountSub.setText(ctx.home().accountChipLabel(a));
            chip.getChildren().addAll(avatar, names, accountMenu);
        } else {
            avatar.setName("");
            accountName.setText(ctx.t("account.signedOut.title"));
            accountSub.setText(ctx.t("account.signedOut.sub"));
            final HBox head = new HBox(10, avatar, names);
            head.setAlignment(Pos.CENTER_LEFT);
            final VBox block = new VBox(10, head, signIn);
            block.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(block, Priority.ALWAYS);
            chip.getChildren().add(block);
        }
    }

    /** @return the Home toggle (for initial focus) */
    public Node homeItem() {
        return items.get(NavigationModel.Page.HOME);
    }
}
