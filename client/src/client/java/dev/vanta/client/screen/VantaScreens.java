package dev.vanta.client.screen;

import dev.vanta.client.VantaClient;
import dev.vanta.client.VantaRuntime;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.ui.UiScreen;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Creates and opens VANTA screens from the core {@link dev.vanta.core.screen.ScreenRegistry}.
 */
public final class VantaScreens {
    private VantaScreens() {
    }

    /**
     * Builds the vanilla host for a VANTA screen.
     *
     * @param id     the screen
     * @param parent screen to return to, or {@code null}
     * @return the host, or {@code null} when the runtime is not ready or no factory is registered for {@code id}
     */
    public static Screen create(ScreenId id, Screen parent) {
        VantaRuntime runtime = VantaRuntime.get();
        if (runtime == null) {
            VantaClient.LOGGER.warn("Cannot open {}: VANTA has not finished initialising", id.id());
            return null;
        }
        Optional<Object> created = runtime.services().screens().create(id);
        if (created.isEmpty()) {
            VantaClient.LOGGER.warn("No screen registered for {}", id.id());
            return null;
        }
        if (!(created.get() instanceof UiScreen ui)) {
            VantaClient.LOGGER.warn("Screen factory for {} returned {}", id.id(), created.get().getClass().getName());
            return null;
        }
        return new VantaScreen(id, ui, parent, runtime);
    }

    /**
     * Opens a VANTA screen.
     *
     * @return true when the screen was shown
     */
    public static boolean open(ScreenId id, Screen parent) {
        Screen screen = create(id, parent);
        if (screen == null) {
            return false;
        }
        Minecraft.getInstance().setScreen(screen);
        return true;
    }

    /** True while a VANTA screen with the given id is the current screen. */
    public static boolean isOpen(ScreenId id) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.screen instanceof VantaScreen vanta && vanta.screenId() == id;
    }
}
