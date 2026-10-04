package dev.vanta.core.hud.editor;

import dev.vanta.core.crosshair.editor.CrosshairEditorScreen;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;

import java.util.Objects;

/**
 * Registers the two HUD-related screens with the {@link dev.vanta.core.screen.ScreenRegistry}:
 * {@link ScreenId#HUD_EDITOR} → {@link HudEditorScreen} and {@link ScreenId#CROSSHAIR} →
 * {@link CrosshairEditorScreen}. Called once from {@code ScreenBootstrap.registerAll}.
 */
public final class HudScreens {

    private HudScreens() {
    }

    /** Registers (or replaces) the HUD editor and crosshair screen factories. */
    public static void register(VantaServices services) {
        Objects.requireNonNull(services, "services");
        services.screens().register(ScreenId.HUD_EDITOR, () -> new HudEditorScreen(services));
        services.screens().register(ScreenId.CROSSHAIR, () -> new CrosshairEditorScreen(services));
    }
}
