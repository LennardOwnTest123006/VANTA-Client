package dev.vanta.client.screen;

import dev.vanta.core.bridge.VanillaScreen;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Opens vanilla screens and external links on behalf of the core. The current screen becomes the parent so the
 * vanilla "Done" button returns to the VANTA screen the user came from.
 */
public final class VanillaScreenOpener {
    private VanillaScreenOpener() {
    }

    /** Opens the vanilla screen on top of the current one. */
    public static void open(VanillaScreen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        Screen next = switch (screen) {
            case SINGLEPLAYER -> new SelectWorldScreen(parent);
            case MULTIPLAYER -> new JoinMultiplayerScreen(parent);
            case OPTIONS -> new OptionsScreen(parent, minecraft.options);
            case LANGUAGE -> new LanguageSelectScreen(parent, minecraft.options, minecraft.getLanguageManager());
            case RESOURCE_PACKS -> new PackSelectionScreen(minecraft.getResourcePackRepository(), repository -> {
                minecraft.options.updateResourcePacks(repository);
                minecraft.setScreen(parent);
            }, minecraft.getResourcePackDirectory(), Component.translatable("resourcePack.title"));
            case ACCESSIBILITY -> new AccessibilityOptionsScreen(parent, minecraft.options);
            case CONTROLS -> new KeyBindsScreen(parent, minecraft.options);
            case VIDEO -> new VideoSettingsScreen(parent, minecraft, minecraft.options);
            case AUDIO -> new SoundOptionsScreen(parent, minecraft.options);
        };
        minecraft.setScreen(next);
    }

    /** Opens a vanilla screen by its {@link VanillaScreen} constant name (case-insensitive); unknown names are ignored. */
    public static void openByName(String name) {
        if (name == null) {
            return;
        }
        for (VanillaScreen screen : VanillaScreen.values()) {
            if (screen.name().equalsIgnoreCase(name.trim())) {
                open(screen);
                return;
            }
        }
    }

    /**
     * Opens a link. {@code file:} URIs (the config folder, the screenshot folder) open directly in the system file
     * browser; web links go through the vanilla confirmation screen first, exactly like chat links.
     */
    public static void openUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("file:")) {
            try {
                Util.getPlatform().openUri(new URI(url));
            } catch (URISyntaxException e) {
                Util.getPlatform().openUri(url);
            }
            return;
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            Minecraft minecraft = Minecraft.getInstance();
            ConfirmLinkScreen.confirmLinkNow(minecraft.screen, url, true);
        }
    }
}
