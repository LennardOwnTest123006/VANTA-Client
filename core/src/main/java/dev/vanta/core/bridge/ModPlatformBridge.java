package dev.vanta.core.bridge;

import java.nio.file.Path;

/**
 * What the Mods &amp; Shaders screen needs from the mod loader and from other mods, implemented by the Fabric client.
 */
public interface ModPlatformBridge {

    /** The game directory ({@code .minecraft} or the instance folder). */
    Path gameDirectory();

    /** True when a Fabric mod with this id is loaded in the running game. */
    boolean isModLoaded(String modId);

    /**
     * Opens the shader pack screen of Iris on top of the current screen.
     *
     * @return false when Iris is not loaded or its screen could not be opened
     */
    boolean openShaderPackScreen();

    /** Bridge for tests and previews: nothing loaded, no shader screen. */
    static ModPlatformBridge none(Path gameDirectory) {
        return new ModPlatformBridge() {
            @Override
            public Path gameDirectory() {
                return gameDirectory;
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }

            @Override
            public boolean openShaderPackScreen() {
                return false;
            }
        };
    }
}
