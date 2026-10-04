package dev.vanta.client.screen;

import dev.vanta.client.VantaRuntime;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.settings.VantaSettings;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Decides whether a vanilla {@link TitleScreen} about to be shown is swapped for the VANTA main menu.
 * <p>
 * The swap happens in {@code MinecraftMixin} on every {@code Minecraft.setScreen(TitleScreen)} call, so returning from
 * any vanilla sub-screen lands on the VANTA menu too. It is skipped when the setting
 * {@code menu.customMainMenu} is off, when the JVM property {@code vanta.forceVanillaMenu} is {@code true}, when the
 * client game test asks for the vanilla screen (the Fabric test runner requires every test to finish on the vanilla
 * title screen), or when the main menu screen is not registered.
 */
public final class MainMenuReplacement {
    /** JVM property that keeps the vanilla title screen (diagnostics, automated runs). */
    public static final String FORCE_VANILLA_PROPERTY = "vanta.forceVanillaMenu";

    private static final boolean FORCE_VANILLA = Boolean.getBoolean(FORCE_VANILLA_PROPERTY);
    private static volatile boolean suppressed;

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
     * @param screen the screen about to be shown (may be {@code null})
     * @return the VANTA main menu that should be shown instead, or {@code null} to keep {@code screen}
     */
    public static Screen replacementFor(Screen screen) {
        if (!(screen instanceof TitleScreen) || isSuppressed()) {
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
        return VantaScreens.create(ScreenId.MAIN_MENU, null);
    }
}
