package dev.vanta.client;

import dev.vanta.core.VantaVersion;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric client entrypoint. Wires the pure-Java {@code core} to Minecraft through the bridge implementations.
 */
public final class VantaClient implements ClientModInitializer {
    public static final String MOD_ID = "vanta";
    public static final Logger LOGGER = LoggerFactory.getLogger("VANTA");

    @Override
    public void onInitializeClient() {
        String loader = FabricLoader.getInstance().getModContainer("fabricloader")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        LOGGER.info("{} {} starting for Minecraft {} (Fabric Loader {}, Java {})",
                VantaVersion.BRAND, VantaVersion.CLIENT, VantaVersion.MINECRAFT, loader,
                Runtime.version().feature());
    }
}
