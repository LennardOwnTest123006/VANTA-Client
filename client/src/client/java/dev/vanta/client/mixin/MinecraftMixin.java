package dev.vanta.client.mixin;

import dev.vanta.client.screen.MainMenuReplacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the vanilla title screen for the VANTA main menu.
 * <p>
 * Target: {@code Minecraft.setScreen(Screen)}. When the incoming screen is a {@code TitleScreen} and
 * {@link MainMenuReplacement} allows it, the call is cancelled and re-issued with the VANTA menu; the replacement is
 * never itself a title screen, so the re-entry runs vanilla code unchanged.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void vanta$replaceTitleScreen(Screen screen, CallbackInfo ci) {
        Screen replacement = MainMenuReplacement.replacementFor(screen);
        if (replacement != null) {
            ci.cancel();
            ((Minecraft) (Object) this).setScreen(replacement);
        }
    }
}
