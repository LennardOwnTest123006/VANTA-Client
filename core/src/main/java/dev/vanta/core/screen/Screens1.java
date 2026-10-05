package dev.vanta.core.screen;

import dev.vanta.core.screen.about.AboutScreen;
import dev.vanta.core.screen.accessibility.AccessibilityScreen;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.menu.MainMenuScreen;
import dev.vanta.core.screen.mods.ModsScreen;
import dev.vanta.core.screen.packs.ResourcePackScreen;
import dev.vanta.core.screen.perf.PerformanceScreen;
import dev.vanta.core.screen.search.SearchOverlay;
import dev.vanta.core.screen.settings.SettingsScreen;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Registers the first group of VANTA screens with the {@link ScreenRegistry}: main menu, settings, search overlay,
 * Performance Center, accessibility, resource packs, Mods &amp; Shaders and about. All of them share the
 * {@link ScreenNavigator#forServices(VantaServices) navigator} of the services so deep links between screens work.
 */
public final class Screens1 {
    /** Screens this class registers. */
    public static final Set<ScreenId> SCREENS = EnumSet.of(ScreenId.MAIN_MENU, ScreenId.SETTINGS, ScreenId.SEARCH,
            ScreenId.PERFORMANCE, ScreenId.ACCESSIBILITY, ScreenId.RESOURCE_PACKS, ScreenId.MODS, ScreenId.ABOUT);

    private Screens1() {
    }

    /** Registers (or replaces) the factories of every screen in {@link #SCREENS}. */
    public static void register(VantaServices services) {
        Objects.requireNonNull(services, "services");
        ScreenNavigator navigator = ScreenNavigator.forServices(services);
        ScreenRegistry registry = services.screens();
        registry.register(ScreenId.MAIN_MENU, () -> new MainMenuScreen(services, navigator));
        registry.register(ScreenId.SETTINGS, () -> new SettingsScreen(services, navigator));
        registry.register(ScreenId.SEARCH, () -> new SearchOverlay(services, navigator));
        registry.register(ScreenId.PERFORMANCE, () -> new PerformanceScreen(services, navigator));
        registry.register(ScreenId.ACCESSIBILITY, () -> new AccessibilityScreen(services, navigator));
        registry.register(ScreenId.RESOURCE_PACKS, () -> new ResourcePackScreen(services, navigator));
        registry.register(ScreenId.MODS, () -> new ModsScreen(services, navigator));
        registry.register(ScreenId.ABOUT, () -> new AboutScreen(services, navigator));
    }
}
