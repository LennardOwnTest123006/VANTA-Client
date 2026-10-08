package dev.vanta.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vanta.client.VantaRuntime;
import dev.vanta.core.ui.Keys;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;

/**
 * Feeds the clicks-per-second widgets of the core {@link dev.vanta.core.hud.render.HudRenderer} and the Vanta Lab
 * input relay ({@link dev.vanta.client.lab.LabInputs}). Called by the {@code KeyMappingMixin} from the very same
 * {@code KeyMapping.click} calls the game processes. Nothing here generates input; it only observes the user's own
 * presses (which the game never registers while a screen is open).
 */
public final class ClickCounters {
    private ClickCounters() {
    }

    /**
     * Records a press of {@code key}: every press counts as input for the Dynamic HUD; a press of the attack key
     * also counts as an attack for the animated crosshair and as a left click, a press of the use key as a right
     * click.
     */
    public static void onClick(InputConstants.Key key) {
        VantaRuntime runtime = VantaRuntime.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (key == null || runtime == null || minecraft == null || minecraft.options == null) {
            return;
        }
        boolean attack = key.equals(KeyBindingHelper.getBoundKeyOf(minecraft.options.keyAttack));
        if (attack) {
            runtime.labInputs().onAttack();
            runtime.hud().onMouseClick(Keys.MOUSE_LEFT);
        } else {
            runtime.labInputs().onInput();
        }
        if (key.equals(KeyBindingHelper.getBoundKeyOf(minecraft.options.keyUse))) {
            runtime.hud().onMouseClick(Keys.MOUSE_RIGHT);
        }
    }
}
