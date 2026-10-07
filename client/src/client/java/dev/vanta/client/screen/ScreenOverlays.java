package dev.vanta.client.screen;

import dev.vanta.client.VantaRuntime;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

/**
 * Draws the VANTA notification overlay on top of every vanilla screen (inventory, options, chat, …).
 * <p>
 * {@link VantaScreen} draws the overlay itself and the HUD element draws it while no screen is open, so this hook only
 * registers for non-VANTA screens. Fabric recreates a screen's events on every {@code init}, which is why the
 * registration happens in {@link ScreenEvents#AFTER_INIT} each time.
 */
public final class ScreenOverlays {
    private ScreenOverlays() {
    }

    /** Registers the hook; call once from the client entrypoint. */
    public static void register(VantaRuntime runtime) {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof VantaScreen) {
                return;
            }
            runtime.services().notifications().sodiumApplyHintFor(screen.getClass().getName());
            SodiumEscapeKeepsChanges.attach(screen);
            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) ->
                    runtime.renderNotifications(graphics, s.width, s.height, tickDelta));
        });
    }
}
