package dev.vanta.core.screen.nexus;

import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.ScreenRegistry;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenNavigator;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Registers the Vanta Nexus screens with the {@link ScreenRegistry}: {@link ScreenId#NEXUS} and
 * {@link ScreenId#LOCAL_AI_SETUP}. Called once from {@code ScreenBootstrap.registerAll}.
 */
public final class NexusScreens {
    /** Screens this class registers. */
    public static final Set<ScreenId> SCREENS = EnumSet.of(ScreenId.NEXUS, ScreenId.LOCAL_AI_SETUP);

    private NexusScreens() {
    }

    /** Registers (or replaces) the factories of every screen in {@link #SCREENS}. */
    public static void register(VantaServices services) {
        Objects.requireNonNull(services, "services");
        ScreenNavigator navigator = ScreenNavigator.forServices(services);
        ScreenRegistry registry = services.screens();
        registry.register(ScreenId.NEXUS, () -> new NexusScreen(services, navigator));
        registry.register(ScreenId.LOCAL_AI_SETUP, () -> new LocalAiSetupScreen(services, navigator));
    }
}
