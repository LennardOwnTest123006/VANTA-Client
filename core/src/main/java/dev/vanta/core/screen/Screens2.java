package dev.vanta.core.screen;

import dev.vanta.core.screen.cosmetics.CosmeticsScreen;
import dev.vanta.core.screen.keybinds.KeybindsScreen;
import dev.vanta.core.screen.profiles.ProfilesScreen;
import dev.vanta.core.screen.stats.StatisticsScreen;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Registers the second group of VANTA screens with the {@link ScreenRegistry}: profiles, keybinds, cosmetics and
 * statistics.
 */
public final class Screens2 {
    /** Screens this class registers. */
    public static final Set<ScreenId> SCREENS = EnumSet.of(ScreenId.PROFILES, ScreenId.KEYBINDS, ScreenId.COSMETICS,
            ScreenId.STATISTICS);

    private Screens2() {
    }

    /** Registers (or replaces) the factories of every screen in {@link #SCREENS}. */
    public static void register(VantaServices services) {
        Objects.requireNonNull(services, "services");
        ScreenRegistry registry = services.screens();
        registry.register(ScreenId.PROFILES, () -> new ProfilesScreen(services));
        registry.register(ScreenId.KEYBINDS, () -> new KeybindsScreen(services));
        registry.register(ScreenId.COSMETICS, () -> new CosmeticsScreen(services));
        registry.register(ScreenId.STATISTICS, () -> new StatisticsScreen(services));
    }
}
