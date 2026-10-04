package dev.vanta.client.mixin;

import dev.vanta.client.VantaRuntime;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hold-to-zoom: narrows the field of view while the zoom key is held.
 * <p>
 * Target: {@code GameRenderer.getFov(Camera, float partialTick, boolean useFovSetting)}. Only the world camera
 * ({@code useFovSetting == true}) is affected; the hand/item pass keeps its fixed FOV exactly like the spyglass.
 * The divisor comes from {@link VantaRuntime#zoomFovDivisor()} and is {@code 1.0} whenever zoom is inactive.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void vanta$applyZoom(Camera camera, float partialTick, boolean useFovSetting,
                                 CallbackInfoReturnable<Float> cir) {
        if (!useFovSetting) {
            return;
        }
        double divisor = VantaRuntime.zoomFovDivisor();
        if (divisor > 1.0005) {
            cir.setReturnValue((float) (cir.getReturnValueF() / divisor));
        }
    }
}
