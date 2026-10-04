package dev.vanta.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vanta.client.hud.ClickCounters;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes key presses for the clicks-per-second HUD widget.
 * <p>
 * Target: the static {@code KeyMapping.click(InputConstants.Key)}, which the game calls once for every physical key or
 * mouse button press while no screen is open. The press is forwarded to {@link ClickCounters}; nothing is changed or
 * generated.
 */
@Mixin(KeyMapping.class)
abstract class KeyMappingMixin {
    @Inject(method = "click", at = @At("HEAD"))
    private static void vanta$countClick(InputConstants.Key key, CallbackInfo ci) {
        ClickCounters.onClick(key);
    }
}
