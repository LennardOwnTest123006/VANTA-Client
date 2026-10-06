package dev.vanta.client.hud;

import dev.vanta.client.VantaRuntime;
import dev.vanta.client.render.GuiGraphicsCanvas;
import dev.vanta.client.screen.VantaScreens;
import dev.vanta.core.screen.ScreenId;
import java.util.Objects;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Replaces the vanilla crosshair element: draws the custom VANTA crosshair when it is enabled and delegates to the
 * vanilla element otherwise.
 * <p>
 * Vanilla keeps the crosshair in every situation where it carries information the custom style cannot (third-person
 * camera, spectator mode, the F3 debug axes) and while the crosshair editor previews the style itself.
 */
public final class VantaCrosshairElement implements HudElement {
    private final HudElement vanilla;

    /**
     * @param vanilla the element being replaced (Fabric passes it to the replacer)
     */
    public VantaCrosshairElement(HudElement vanilla) {
        this.vanilla = Objects.requireNonNull(vanilla, "vanilla");
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        VantaRuntime runtime = VantaRuntime.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (runtime == null || minecraft == null || !useCustom(minecraft)) {
            vanilla.render(graphics, deltaTracker);
            return;
        }
        // Evaluated once per frame; the renderer takes the flag so it does not scan the HUD layout a second time.
        boolean customEnabled = runtime.crosshair().isCustomEnabled();
        if (!customEnabled) {
            vanilla.render(graphics, deltaTracker);
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        GuiGraphicsCanvas canvas = new GuiGraphicsCanvas(graphics, minecraft.font, width, height, false);
        try {
            runtime.crosshair().render(canvas, width, height, deltaTracker.getRealtimeDeltaTicks(), customEnabled);
        } finally {
            canvas.finish();
        }
    }

    private static boolean useCustom(Minecraft minecraft) {
        return minecraft.player != null
                && !minecraft.options.hideGui
                && !minecraft.debugEntries.isOverlayVisible()
                && minecraft.options.getCameraType().isFirstPerson()
                && !minecraft.player.isSpectator()
                && !VantaScreens.isOpen(ScreenId.CROSSHAIR);
    }
}
