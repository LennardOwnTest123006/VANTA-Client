package dev.vanta.core.screen;

/**
 * Single entry point the Minecraft client calls once at start-up to register every VANTA screen factory
 * with the {@link ScreenRegistry}. Each screen package contributes a {@code register(VantaServices)} method;
 * they are wired here so the client never has to know individual screen classes.
 */
public final class ScreenBootstrap {
    private ScreenBootstrap() {
    }

    /**
     * Registers all VANTA screens. Safe to call more than once (later registrations replace earlier ones).
     *
     * @param services the composition root created by the client
     */
    public static void registerAll(VantaServices services) {
        // Screen packages register themselves here (filled in as the screen packages land):
        dev.vanta.core.screen.Screens1.register(services);         // main menu, settings, search, performance, accessibility, packs, mods, about
        dev.vanta.core.screen.Screens2.register(services);         // profiles, keybinds, cosmetics, statistics
        dev.vanta.core.hud.editor.HudScreens.register(services);   // HUD editor, crosshair editor
    }
}
