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
 * Target: {@code Minecraft.setScreen(Screen)}. Two kinds of calls end on the vanilla title screen:
 * <ul>
 *   <li>{@code setScreen(new TitleScreen())} (Save and Quit, Disconnect, Back from a screen whose parent is the title
 *       screen);</li>
 *   <li>{@code setScreen(null)} while no world is loaded: vanilla turns {@code null} into {@code new TitleScreen()}
 *       inside {@code setScreen}, after this HEAD injection — for example Cancel or Escape on the Create World screen
 *       that Singleplayer opens when there are no worlds yet.</li>
 * </ul>
 * When {@link MainMenuReplacement} allows it, the call is cancelled and re-issued with the VANTA menu; the replacement
 * is never itself a title screen or {@code null}, so the re-entry runs vanilla code unchanged.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void vanta$replaceTitleScreen(Screen screen, CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        Screen replacement = MainMenuReplacement.replacementFor(screen, self.level == null);
        if (replacement != null) {
            ci.cancel();
            self.setScreen(replacement);
        }
    }
}
