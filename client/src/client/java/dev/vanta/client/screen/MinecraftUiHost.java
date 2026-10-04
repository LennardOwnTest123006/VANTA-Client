package dev.vanta.client.screen;

import dev.vanta.client.VantaRuntime;
import dev.vanta.core.bridge.VanillaScreen;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.UiHost;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * {@link UiHost} for one {@link VantaScreen}: navigation, clipboard, click sound and links, all through the public
 * Minecraft API. Opening another VANTA screen makes the current one its parent so "Back" returns here.
 */
final class MinecraftUiHost implements UiHost {
    private final VantaScreen screen;
    private final VantaRuntime runtime;

    MinecraftUiHost(VantaScreen screen, VantaRuntime runtime) {
        this.screen = Objects.requireNonNull(screen, "screen");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public void closeScreen() {
        screen.closeNow();
    }

    @Override
    public void openScreen(Object screenId) {
        if (screenId instanceof ScreenId id) {
            VantaScreens.open(id, screen);
        } else if (screenId instanceof VanillaScreen vanilla) {
            VanillaScreenOpener.open(vanilla);
        } else if (screenId instanceof String text) {
            Optional<ScreenId> id = ScreenId.fromId(text);
            if (id.isPresent()) {
                VantaScreens.open(id.get(), screen);
            } else {
                VanillaScreenOpener.openByName(text);
            }
        }
    }

    @Override
    public void setClipboard(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.keyboardHandler != null) {
            minecraft.keyboardHandler.setClipboard(text == null ? "" : text);
        }
    }

    @Override
    public String getClipboard() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.keyboardHandler == null) {
            return "";
        }
        String text = minecraft.keyboardHandler.getClipboard();
        return text == null ? "" : text;
    }

    @Override
    public void playClick() {
        if (!runtime.services().settings().get(VantaSettings.GENERAL_UI_SOUNDS)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public void openUrl(String url) {
        VanillaScreenOpener.openUrl(url);
    }

    @Override
    public void requestLayout() {
        screen.requestLayout();
    }
}
