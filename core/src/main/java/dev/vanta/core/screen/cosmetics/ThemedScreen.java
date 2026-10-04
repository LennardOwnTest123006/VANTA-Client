package dev.vanta.core.screen.cosmetics;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.VantaShell;
import java.util.Objects;

/**
 * Base class of the screens registered by {@link dev.vanta.core.screen.Screens2}: profiles, keybinds, cosmetics
 * and statistics. It adds what all of them need on top of {@link UiScreen}:
 * <ul>
 *   <li>access to the {@link VantaServices} and the translated title of the {@link ScreenId};</li>
 *   <li>live re-theming: when the cosmetic theme or an accessibility setting changes while the screen is open the
 *       theme is rebuilt with {@link ThemeResolver} and applied immediately;</li>
 *   <li>navigation to other VANTA screens through the host;</li>
 *   <li>persistence: every store is saved when the screen closes.</li>
 * </ul>
 */
public abstract class ThemedScreen extends UiScreen {
    private final VantaServices services;
    private final ScreenId screenId;
    private Runnable unsubscribeAccessibility;
    private Runnable unsubscribeCosmetics;
    private boolean liveTheme;
    private Runnable afterInit;

    protected ThemedScreen(VantaServices services, ScreenId screenId) {
        this.services = Objects.requireNonNull(services, "services");
        this.screenId = Objects.requireNonNull(screenId, "screenId");
    }

    /** The composition root. */
    public final VantaServices services() {
        return services;
    }

    /** Registry id of this screen. */
    public final ScreenId screenId() {
        return screenId;
    }

    @Override
    public String title() {
        return Lang.tr(screenId.langKey());
    }

    /** A shell with this screen's title, a back button that closes the screen and the version footer. */
    protected final VantaShell newShell() {
        VantaShell shell = new VantaShell(title(), this::close);
        shell.backTooltip(Lang.tr("vanta.common.back"));
        return shell;
    }

    /** Opens another VANTA screen through the host (no-op when the screen is not registered). */
    public final boolean openScreen(ScreenId id) {
        if (!services.screens().isRegistered(id)) {
            return false;
        }
        context().host().openScreen(id);
        return true;
    }

    /**
     * Runs {@code action} once, right after the first layout. Hosts use it to open a dialog or focus a control as
     * soon as the screen is ready; previews use it to capture popups.
     */
    public final void onceInitialised(Runnable action) {
        if (isInitialised()) {
            action.run();
        } else {
            afterInit = action;
        }
    }

    @Override
    protected final void onInit() {
        unsubscribeAccessibility = services.accessibility().listen(state -> {
            if (liveTheme) {
                retheme();
            }
        });
        unsubscribeCosmetics = services.cosmetics().onSelectionChanged(selection -> {
            if (liveTheme) {
                retheme();
            }
        });
        liveTheme = true;
        // Width-dependent nodes (wrapped text, responsive grids) measure against the bounds of the previous pass;
        // a second pass before the first frame lets them settle.
        invalidateLayout();
        onScreenInit();
        if (afterInit != null) {
            Runnable once = afterInit;
            afterInit = null;
            once.run();
        }
    }

    /** Hook after the first layout. */
    protected void onScreenInit() {
    }

    @Override
    protected void onResize() {
        invalidateLayout();
    }

    /** Rebuilds the theme from the services and applies it when it differs from the current one. */
    protected final void retheme() {
        Theme next = ThemeResolver.themeFor(services);
        if (!next.equals(theme())) {
            setTheme(next);
            onThemeChanged(next);
        }
    }

    /** Hook after a live theme change. */
    protected void onThemeChanged(Theme theme) {
    }

    @Override
    public void onClose() {
        if (unsubscribeAccessibility != null) {
            unsubscribeAccessibility.run();
            unsubscribeAccessibility = null;
        }
        if (unsubscribeCosmetics != null) {
            unsubscribeCosmetics.run();
            unsubscribeCosmetics = null;
        }
        liveTheme = false;
        services.saveAll();
        onScreenClose();
    }

    /** Hook when the screen was removed (after everything was saved). */
    protected void onScreenClose() {
    }
}
