package dev.vanta.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vanta.core.hud.CpsCounter;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;

/**
 * Clicks-per-second counters for the attack and use keys, fed by the {@code KeyMappingMixin} from the very same
 * {@code KeyMapping.click} calls the game processes. Nothing here generates input; it only observes the user's own
 * presses (which the game never registers while a screen is open).
 */
public final class ClickCounters {
    private static final CpsCounter LEFT = new CpsCounter();
    private static final CpsCounter RIGHT = new CpsCounter();

    private ClickCounters() {
    }

    /** Attack key (left mouse button by default). */
    public static CpsCounter left() {
        return LEFT;
    }

    /** Use key (right mouse button by default). */
    public static CpsCounter right() {
        return RIGHT;
    }

    /** Records a press of {@code key} when it is bound to attack or use. */
    public static void onClick(InputConstants.Key key) {
        Minecraft minecraft = Minecraft.getInstance();
        if (key == null || minecraft == null || minecraft.options == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (key.equals(KeyBindingHelper.getBoundKeyOf(minecraft.options.keyAttack))) {
            LEFT.click(now);
        }
        if (key.equals(KeyBindingHelper.getBoundKeyOf(minecraft.options.keyUse))) {
            RIGHT.click(now);
        }
    }

    /** Clears both counters (world change). */
    public static void reset() {
        LEFT.reset();
        RIGHT.reset();
    }
}
