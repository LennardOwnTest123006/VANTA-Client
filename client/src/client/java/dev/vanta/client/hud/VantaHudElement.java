package dev.vanta.client.hud;

import dev.vanta.client.VantaRuntime;
import dev.vanta.client.render.GuiGraphicsCanvas;
import dev.vanta.client.screen.VantaScreens;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.settings.VantaSettings;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Fabric HUD element that draws the VANTA HUD (all enabled widgets) and, while no screen is open, the notification
 * overlay. Attached after the vanilla chat so it inherits the chat's render condition.
 * <p>
 * Skipped when the GUI is hidden (F1), while the F3 debug overlay is visible, while a HUD-free screenshot is being
 * captured and while the HUD editor is open (the editor draws its own live preview).
 */
public final class VantaHudElement implements HudElement {
    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        VantaRuntime runtime = VantaRuntime.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (runtime == null || minecraft == null || minecraft.player == null) {
            return;
        }
        runtime.frames().onHudFrame(minecraft);
        if (minecraft.options.hideGui || minecraft.debugEntries.isOverlayVisible()
                || runtime.screenshots().isHudSuppressed()) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float delta = deltaTracker.getRealtimeDeltaTicks();
        GuiGraphicsCanvas canvas = new GuiGraphicsCanvas(graphics, minecraft.font, width, height, false);
        try {
            if (runtime.services().settings().get(VantaSettings.HUD_ENABLED) && !VantaScreens.isOpen(ScreenId.HUD_EDITOR)) {
                runtime.hud().render(canvas, width, height, delta);
            }
            if (minecraft.screen == null) {
                runtime.notifications().render(canvas, width, height, delta);
            }
        } finally {
            canvas.finish();
        }
    }
}
