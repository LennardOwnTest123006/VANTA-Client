package dev.vanta.core.screen.common;

import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.search.ActionEntry;
import dev.vanta.core.ui.UiContext;
import java.util.Objects;
import java.util.Optional;

/**
 * Runs an action id from a settings row, a search result or a menu button: navigation actions open their screen
 * through the {@link ScreenNavigator}, destructive commands ask for confirmation first, everything else is handed to
 * {@link VantaServices#runAction(String)}.
 */
public final class ActionDispatcher {
    private final VantaServices services;
    private final ScreenNavigator navigator;

    public ActionDispatcher(VantaServices services, ScreenNavigator navigator) {
        this.services = Objects.requireNonNull(services, "services");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
    }

    /** The screen a navigation action opens, if the id is one. */
    public static Optional<ScreenId> screenOf(String actionId) {
        for (ActionEntry entry : ActionEntry.builtIns()) {
            if (entry.id().equals(actionId) && entry.screen().isPresent()) {
                return entry.screen();
            }
        }
        return ScreenId.fromId(actionId.startsWith("open_") ? actionId.substring("open_".length()) : actionId);
    }

    /** True for commands that irreversibly discard user data and therefore confirm first. */
    public static boolean isDestructive(String actionId) {
        return ActionEntry.RESET_SETTINGS.equals(actionId) || ActionEntry.CLEAR_STATISTICS.equals(actionId);
    }

    /**
     * Dispatches the action.
     *
     * @return true when the id was recognised (navigation, confirmation shown or command executed)
     */
    public boolean dispatch(UiContext ctx, String actionId) {
        Objects.requireNonNull(actionId, "actionId");
        Optional<ScreenId> screen = screenOf(actionId);
        if (screen.isPresent()) {
            navigator.openScreen(ctx, screen.get());
            return true;
        }
        if (ActionEntry.RESET_SETTINGS.equals(actionId)) {
            ConfirmDialogs.resetAllSettings(ctx, () -> services.runAction(actionId));
            return true;
        }
        if (ActionEntry.CLEAR_STATISTICS.equals(actionId)) {
            ConfirmDialogs.clearStatistics(ctx, () -> services.runAction(actionId));
            return true;
        }
        return services.runAction(actionId);
    }
}
