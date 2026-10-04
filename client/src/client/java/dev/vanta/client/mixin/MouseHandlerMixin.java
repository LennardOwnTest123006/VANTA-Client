package dev.vanta.client.mixin;

import dev.vanta.client.VantaRuntime;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mouse wheel while zooming adjusts the zoom level instead of scrolling the hotbar.
 * <p>
 * Target: {@code MouseHandler.onScroll(long window, double xOffset, double yOffset)}, the GLFW scroll callback. The
 * event is cancelled only while the zoom key is held, no screen is open and the {@code zoom.scrollAdjust} setting is
 * on; otherwise vanilla handles it untouched.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void vanta$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (VantaRuntime.onMouseScroll(yOffset)) {
            ci.cancel();
        }
    }
}
