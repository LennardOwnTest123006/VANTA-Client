package dev.vanta.client.bridge;

import dev.vanta.client.VantaClient;
import dev.vanta.core.bridge.ModPlatformBridge;
import dev.vanta.core.modrinth.ModrinthConstants;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * {@link ModPlatformBridge} on Fabric Loader: the game directory, loaded mods, and the Iris shader pack screen.
 * <p>
 * Iris is an optional mod, so it is reached by reflection only: first through its public API
 * ({@code net.irisshaders.iris.api.v0.IrisApi#openMainIrisScreenObj(Object)}), then through the screen class
 * {@code net.irisshaders.iris.gui.screen.ShaderPackScreen(Screen parent)}. Both names were checked against the Iris
 * sources for Minecraft 1.21.11. When neither works the caller tells the player to use the Iris key instead.
 */
public final class FabricModPlatform implements ModPlatformBridge {
    static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    static final String IRIS_SHADER_PACK_SCREEN = "net.irisshaders.iris.gui.screen.ShaderPackScreen";

    @Override
    public Path gameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean openShaderPackScreen() {
        if (!isModLoaded(ModrinthConstants.IRIS_MOD_ID)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        Screen screen = viaApi(parent);
        if (screen == null) {
            screen = viaConstructor(parent);
        }
        if (screen == null) {
            return false;
        }
        minecraft.setScreen(screen);
        return true;
    }

    private static Screen viaApi(Screen parent) {
        try {
            Class<?> api = Class.forName(IRIS_API);
            Object instance = api.getMethod("getInstance").invoke(null);
            Object screen = api.getMethod("openMainIrisScreenObj", Object.class).invoke(instance, parent);
            return screen instanceof Screen s ? s : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            VantaClient.LOGGER.debug("Iris API screen not available: {}", e.toString());
            return null;
        }
    }

    private static Screen viaConstructor(Screen parent) {
        try {
            Class<?> type = Class.forName(IRIS_SHADER_PACK_SCREEN);
            Object screen = type.getConstructor(Screen.class).newInstance(parent);
            return screen instanceof Screen s ? s : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            VantaClient.LOGGER.warn("Could not open the Iris shader pack screen: {}", e.toString());
            return null;
        }
    }
}
