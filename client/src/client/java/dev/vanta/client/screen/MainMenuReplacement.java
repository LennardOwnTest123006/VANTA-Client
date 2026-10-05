package dev.vanta.client.screen;

import dev.vanta.client.VantaClient;
import dev.vanta.client.VantaRuntime;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.settings.VantaSettings;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Decides whether a vanilla {@link TitleScreen} about to be shown is swapped for the VANTA main menu.
 * <p>
 * The swap happens in {@code MinecraftMixin} on every {@code Minecraft.setScreen} call that would end on the vanilla
 * title screen: {@code setScreen(TitleScreen)}, and {@code setScreen(null)} while no world is loaded (vanilla turns that
 * into a new title screen, e.g. after Cancel / Escape on the Create World screen Singleplayer opens when there are no
 * worlds). So returning from any vanilla sub-screen lands on the VANTA menu too. It is skipped when the setting
 * {@code menu.customMainMenu} is off, when the JVM property {@code vanta.forceVanillaMenu} is {@code true}, when the
 * client game test asks for the vanilla screen (the Fabric test runner requires every test to finish on the vanilla
 * title screen), or when the main menu screen is not registered.
 */
public final class MainMenuReplacement {
    /** JVM property that keeps the vanilla title screen (diagnostics, automated runs). */
    public static final String FORCE_VANILLA_PROPERTY = "vanta.forceVanillaMenu";

    private static final boolean FORCE_VANILLA = Boolean.getBoolean(FORCE_VANILLA_PROPERTY);
    private static volatile boolean suppressed;
    private static final java.util.concurrent.atomic.AtomicBoolean LOGGED_FIRST =
            new java.util.concurrent.atomic.AtomicBoolean();

    private MainMenuReplacement() {
    }

    /** Temporarily keeps the vanilla title screen (used by the client game test). */
    public static void setSuppressed(boolean value) {
        suppressed = value;
    }

    /** True while the replacement is suppressed by code or by the JVM property. */
    public static boolean isSuppressed() {
        return suppressed || FORCE_VANILLA;
    }

    /**
     * True when {@code Minecraft.setScreen(screen)} would show the vanilla title screen: an explicit
     * {@link TitleScreen}, or {@code null} while no world is loaded (vanilla replaces that with a new title screen).
     *
     * @param screen  the screen about to be shown (may be {@code null})
     * @param noLevel true when no client world is loaded
     */
    public static boolean leadsToTitleScreen(Screen screen, boolean noLevel) {
        return screen instanceof TitleScreen || (screen == null && noLevel);
    }

    /**
     * @param screen  the screen about to be shown (may be {@code null})
     * @param noLevel true when no client world is loaded ({@code Minecraft.level == null})
     * @return the VANTA main menu that should be shown instead, or {@code null} to keep {@code screen}
     */
    public static Screen replacementFor(Screen screen, boolean noLevel) {
        if (!leadsToTitleScreen(screen, noLevel) || isSuppressed()) {
            return null;
        }
        VantaRuntime runtime = VantaRuntime.get();
        if (runtime == null || !runtime.services().isLoaded()) {
            return null;
        }
        if (!runtime.services().settings().get(VantaSettings.MENU_CUSTOM_MAIN_MENU)) {
            return null;
        }
        if (!runtime.services().screens().isRegistered(ScreenId.MAIN_MENU)) {
            return null;
        }
        if (!LOGGED_FIRST.getAndSet(true)) {
            // One line per game session so headless runs (CI) can verify the menu actually came up.
            VantaClient.LOGGER.info("VANTA main menu shown (replacing the vanilla title screen)");
        }
        if (screen == null) {
            VantaClient.LOGGER.debug("Closing the last screen without a world: showing the VANTA main menu");
        }
        return VantaScreens.create(ScreenId.MAIN_MENU, null);
    }
}
