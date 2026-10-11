package dev.vanta.core.screen.timer;

import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.ScreenRegistry;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenNavigator;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Registers the timer screens with the {@link ScreenRegistry}: {@link ScreenId#TIMER} ({@link TimerScreen}) and
 * {@link ScreenId#TIMER_ALARM} ({@link TimerAlarmScreen}). Called once from {@code ScreenBootstrap.registerAll}.
 */
public final class TimerScreens {
    /** Screens this class registers. */
    public static final Set<ScreenId> SCREENS = EnumSet.of(ScreenId.TIMER, ScreenId.TIMER_ALARM);

    private TimerScreens() {
    }

    /** Registers (or replaces) the factories of every screen in {@link #SCREENS}. */
    public static void register(VantaServices services) {
        Objects.requireNonNull(services, "services");
        ScreenNavigator navigator = ScreenNavigator.forServices(services);
        ScreenRegistry registry = services.screens();
        registry.register(ScreenId.TIMER, () -> new TimerScreen(services, navigator));
        registry.register(ScreenId.TIMER_ALARM, () -> new TimerAlarmScreen(services, navigator));
    }
}
