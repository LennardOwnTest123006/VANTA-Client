package dev.vanta.core.modrinth;

import dev.vanta.core.bridge.ModPlatformBridge;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.screen.VantaServices;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link ModPlatformBridge} for tests and previews: a configurable set of loaded mod ids (Fabric API by default) and a
 * shader screen that "opens" when Iris is loaded. Also wires a synchronous {@link ModrinthService} into services.
 */
public final class FakeModPlatform implements ModPlatformBridge {
    public final Path gameDir;
    public final Set<String> loaded = new HashSet<>(Set.of(ModrinthConstants.FABRIC_API_MOD_ID, "vanta"));
    public final List<String> calls = new ArrayList<>();
    public boolean shaderScreenWorks = true;

    public FakeModPlatform(Path gameDir) {
        this.gameDir = gameDir;
    }

    @Override
    public Path gameDirectory() {
        return gameDir;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return loaded.contains(modId);
    }

    @Override
    public boolean openShaderPackScreen() {
        calls.add("openShaderPackScreen");
        return shaderScreenWorks && loaded.contains(ModrinthConstants.IRIS_MOD_ID);
    }

    /**
     * A service whose worker and main thread run tasks immediately (deterministic tests and previews), installed into
     * {@code services}.
     */
    public ModrinthService installInto(VantaServices services, ModrinthApi api) {
        ModrinthLibrary library = new ModrinthLibrary(gameDir, new JsonStore(services.clock()));
        ModrinthService service = new ModrinthService(api, library, this, services.notifications(), Runnable::run,
                Runnable::run, services.clock());
        services.setModrinth(service);
        return service;
    }
}
