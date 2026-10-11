package dev.vanta.client.screen;

import dev.vanta.client.VantaClient;
import dev.vanta.core.screen.ScreenId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Opens the "Time is up" window ({@link ScreenId#TIMER_ALARM}) for the core timer
 * ({@code TimerService.setAlarmWindow}). The timer calls it when the time is up and again on every tick while it rings,
 * so the window comes back whenever the game replaced it.
 * <p>
 * It opens through the same path as every other VANTA screen ({@link VantaScreens#open}), with the current screen as
 * its parent so Stop returns there (or to the game when no screen was open), and only when:
 * <ul>
 *   <li>the alarm window is not already showing;</li>
 *   <li>the current screen is none (in the game), a VANTA screen, the vanilla title screen, or a vanilla screen that
 *       Escape may close (the pause menu, the inventory, chat, the options). Screens that Escape may not close are
 *       loading, progress, connecting and death screens: replacing one could break what it is doing, so the window
 *       waits until the game moves on (the bell keeps ringing meanwhile).</li>
 * </ul>
 */
public final class TimerAlarmOpener implements Runnable {

    @Override
    public void run() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || VantaScreens.isOpen(ScreenId.TIMER_ALARM)) {
            return;
        }
        Screen current = minecraft.screen;
        if (!mayOpenOver(current)) {
            return;
        }
        if (VantaScreens.open(ScreenId.TIMER_ALARM, current)) {
            VantaClient.LOGGER.info("Timer: the time is up, alarm window opened over {}",
                    current == null ? "the game" : current.getClass().getSimpleName());
        }
    }

    /** Whether the alarm window may replace {@code current} (see the class comment). */
    static boolean mayOpenOver(Screen current) {
        return current == null || current instanceof VantaScreen || current instanceof TitleScreen
                || current.shouldCloseOnEsc();
    }
}
