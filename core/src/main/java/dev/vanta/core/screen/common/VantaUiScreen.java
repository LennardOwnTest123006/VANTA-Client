package dev.vanta.core.screen.common;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.SettingsListener;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiScreen;
import java.util.Objects;

/**
 * Base class of every screen built on {@link VantaServices}. It adds what all of them need:
 * <ul>
 *   <li>access to the services, the {@link ScreenNavigator} and an {@link ActionDispatcher};</li>
 *   <li>the translated title from the {@link ScreenId};</li>
 *   <li>live re-theming: when an accessibility setting or the cosmetic theme changes while the screen is open the
 *       theme is rebuilt with {@link ThemeFactory} and applied immediately;</li>
 *   <li>debounced persistence: a settings change schedules a save {@value #SAVE_DEBOUNCE_TICKS} ticks later, and
 *       everything is saved when the screen closes;</li>
 *   <li>the global shortcut Ctrl+K that opens the search overlay from any screen.</li>
 * </ul>
 * The host checks {@link #wantsVanillaPanorama()} to decide whether the vanilla panorama is rendered behind.
 */
public abstract class VantaUiScreen extends UiScreen {
    /** Ticks between the last setting change and the save (one second). */
    public static final int SAVE_DEBOUNCE_TICKS = 20;

    private final VantaServices services;
    private final ScreenNavigator navigator;
    private final ActionDispatcher actions;
    private final ScreenId screenId;
    private final SettingsListener dirtyListener = change -> ticksSinceChange = 0;
    private Runnable unsubscribeAccessibility;
    private Runnable unsubscribeCosmetics;
    private boolean liveTheme;
    private int ticksSinceChange = -1;
    private Runnable afterInit;

    protected VantaUiScreen(VantaServices services, ScreenNavigator navigator, ScreenId screenId) {
        this.services = Objects.requireNonNull(services, "services");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.screenId = Objects.requireNonNull(screenId, "screenId");
        this.actions = new ActionDispatcher(services, navigator);
    }

    /** The composition root. */
    public final VantaServices services() {
        return services;
    }

    /** Navigation helper shared with the other screens. */
    public final ScreenNavigator navigator() {
        return navigator;
    }

    /** Action dispatcher for action ids. */
    public final ActionDispatcher actions() {
        return actions;
    }

    /** Registry id of this screen. */
    public final ScreenId screenId() {
        return screenId;
    }

    @Override
    public String title() {
        return Lang.tr(screenId.langKey());
    }

    /** True when the host should draw the vanilla title panorama behind this screen (main menu only). */
    public boolean wantsVanillaPanorama() {
        return false;
    }

    @Override
    protected final void onInit() {
        services.settings().addListener(dirtyListener);
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
        services.labEffects().onScreenOpened(context().now());
        // Width-dependent nodes (wrapped text, responsive grids) measure against bounds from the previous pass;
        // a second pass before the first frame lets them settle.
        invalidateLayout();
        onScreenInit();
        if (afterInit != null) {
            Runnable once = afterInit;
            afterInit = null;
            once.run();
        }
    }

    /**
     * Runs {@code action} once, right after the first layout (after {@link #onScreenInit()}). Hosts use it to open a
     * dialog or focus a control as soon as the screen is ready; previews use it to capture popups.
     */
    public final void onceInitialised(Runnable action) {
        if (isInitialised()) {
            action.run();
        } else {
            afterInit = action;
        }
    }

    /** Hook after the first layout (replaces {@link UiScreen#onInit()}). */
    protected void onScreenInit() {
    }

    @Override
    protected final void onCloseStarted(long now) {
        services.labEffects().onScreenClosed(now);
    }

    @Override
    protected final float slideProgress(long now) {
        return services.labEffects().transitionProgress(now);
    }

    @Override
    protected void onResize() {
        invalidateLayout();
        onScreenResize();
    }

    /** Hook after a resize layout (replaces {@link UiScreen#onResize()}). */
    protected void onScreenResize() {
    }

    /** Rebuilds the theme from the services and applies it when it differs from the current one. */
    protected final void retheme() {
        Theme next = ThemeFactory.themeFor(services);
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
        services.settings().removeListener(dirtyListener);
        if (unsubscribeAccessibility != null) {
            unsubscribeAccessibility.run();
            unsubscribeAccessibility = null;
        }
        if (unsubscribeCosmetics != null) {
            unsubscribeCosmetics.run();
            unsubscribeCosmetics = null;
        }
        liveTheme = false;
        ticksSinceChange = -1;
        services.saveAll();
        onScreenClose();
    }

    /** Hook when the screen was removed (after everything was saved). */
    protected void onScreenClose() {
    }

    @Override
    protected final void onTick() {
        if (ticksSinceChange >= 0 && ++ticksSinceChange >= SAVE_DEBOUNCE_TICKS) {
            ticksSinceChange = -1;
            services.settings().saveIfDirty();
        }
        onScreenTick();
    }

    /** Hook once per game tick (replaces {@link UiScreen#onTick()}). */
    protected void onScreenTick() {
    }

    /** Whether a debounced save is pending (tests). */
    public final boolean hasPendingSave() {
        return ticksSinceChange >= 0;
    }

    @Override
    protected boolean onKeyDown(int key, int scancode, int mods) {
        if (key == Keys.K && Keys.hasControl(mods) && screenId != ScreenId.SEARCH) {
            navigator.openScreen(context(), ScreenId.SEARCH);
            return true;
        }
        return false;
    }
}
