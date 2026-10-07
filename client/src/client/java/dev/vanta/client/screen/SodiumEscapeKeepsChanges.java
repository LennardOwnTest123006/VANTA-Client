package dev.vanta.client.screen;

import dev.vanta.client.VantaClient;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * Makes Escape keep the changes in Sodium's video settings screen, the way vanilla Video Settings does.
 * <p>
 * With the Performance pack, Sodium replaces Video Settings. In Sodium 0.8.x, Escape calls {@code undoChanges()} and
 * throws away every change that was not applied, and its Done button stays disabled until Apply is pressed. Players
 * change Graphics or Clouds there, press Escape and find the old value again. When Escape is pressed or released on
 * that screen with pending changes, this hook applies them exactly like Sodium's Apply button
 * ({@code ConfigManager.CONFIG.applyAllOptions()}) and closes the screen; Sodium's own Escape handling is then
 * skipped. Without pending changes, or if Sodium's classes differ from what is expected, Sodium behaves as before.
 * <p>
 * Both key events are needed: Sodium notices pending changes only when it draws the next frame, and until then its
 * {@code shouldCloseOnEsc()} lets the vanilla key press close the screen without applying them. Once the screen knows,
 * the press does nothing and Sodium undoes the changes on release. Uses Fabric API's
 * {@link ScreenKeyboardEvents#allowKeyPress} and {@link ScreenKeyboardEvents#allowKeyRelease}, which run before the
 * screen's own handlers, and public Sodium members only (looked up by name, since Sodium is optional and not on the
 * compile classpath).
 */
public final class SodiumEscapeKeepsChanges {
    /** Sodium's video settings screen. */
    public static final String SODIUM_SCREEN = "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen";
    private static final String CONFIG_MANAGER = "net.caffeinemc.mods.sodium.client.config.ConfigManager";
    private static volatile boolean warned;

    private SodiumEscapeKeepsChanges() {
    }

    /** Registers the hook on a freshly initialised screen when it is Sodium's video settings screen. */
    public static void attach(Screen screen) {
        if (!SODIUM_SCREEN.equals(screen.getClass().getName())) {
            return;
        }
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !escapeAppliedAndClosed(s, event.key()));
        ScreenKeyboardEvents.allowKeyRelease(screen).register((s, event) -> !escapeAppliedAndClosed(s, event.key()));
    }

    private static boolean escapeAppliedAndClosed(Screen screen, int key) {
        if (key != GLFW.GLFW_KEY_ESCAPE || !applyPendingChanges()) {
            return false;
        }
        VantaClient.LOGGER.info("Applied the pending changes of Sodium's video settings on Escape");
        screen.onClose();
        return true;
    }

    /**
     * Applies Sodium's pending option changes, like its Apply button.
     *
     * @return true when there were pending changes and they were applied
     */
    public static boolean applyPendingChanges() {
        try {
            Object config = Class.forName(CONFIG_MANAGER).getField("CONFIG").get(null);
            if (config == null) {
                return false;
            }
            Method changed = config.getClass().getMethod("anyOptionChanged");
            if (!Boolean.TRUE.equals(changed.invoke(config))) {
                return false;
            }
            config.getClass().getMethod("applyAllOptions").invoke(config);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                VantaClient.LOGGER.warn("Could not apply Sodium's pending video settings on Escape; Sodium's own Escape handling stays", e);
            }
            return false;
        }
    }
}
