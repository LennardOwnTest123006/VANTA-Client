package dev.vanta.core.screen.menu;

import dev.vanta.core.config.CoreLog;
import dev.vanta.core.screen.common.VantaUiScreen;

/**
 * What happens once when the main menu is first shown, kept out of {@link MainMenuScreen} so the screen stays a pure
 * view: today the one-time Performance pack offer ({@link dev.vanta.core.modrinth.PackOffer}). Every hook guards
 * itself (shown once per session, suppressed for game tests and fixtures) and never lets an exception reach the
 * screen.
 */
public final class MainMenuHooks {
    private MainMenuHooks() {
    }

    /** Called from {@link MainMenuScreen#onScreenInit()} after the first layout. */
    public static void onMainMenuShown(VantaUiScreen screen) {
        try {
            if (!screen.context().popups().isOpen()) {
                screen.services().packOffer().showIfWanted(screen.context());
            }
        } catch (RuntimeException e) {
            CoreLog.warn(e, "Main menu hook failed");
        }
    }
}
