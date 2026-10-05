package dev.vanta.client;

import dev.vanta.client.bridge.FabricModPlatform;
import dev.vanta.client.bridge.MinecraftClipboardBridge;
import dev.vanta.client.bridge.MinecraftGameBridge;
import dev.vanta.client.bridge.MinecraftKeybindBridge;
import dev.vanta.client.bridge.MinecraftOptionsBridge;
import dev.vanta.client.bridge.MinecraftResourcePackBridge;
import dev.vanta.client.bridge.MinecraftScreenshotBridge;
import dev.vanta.client.command.VantaCommands;
import dev.vanta.client.event.GameEvents;
import dev.vanta.client.hud.VantaCrosshairElement;
import dev.vanta.client.hud.VantaHudElement;
import dev.vanta.client.keys.VantaKeyMappings;
import dev.vanta.client.lang.MinecraftLangProvider;
import dev.vanta.client.log.Slf4jCoreLogSink;
import dev.vanta.client.screen.ScreenOverlays;
import dev.vanta.core.VantaVersion;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.modrinth.ModrinthService;
import dev.vanta.core.screen.ScreenBootstrap;
import dev.vanta.core.screen.VantaServices;
import java.time.Clock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric client entrypoint. Wires the pure-Java {@code core} to Minecraft: logging and translations, the bridge
 * implementations, the composition root ({@link VantaServices}), the screens, key mappings, HUD elements, events and
 * the {@code /vanta} command. Everything Minecraft-specific lives in small adapters under {@code dev.vanta.client};
 * all behaviour lives in {@code core}.
 */
public final class VantaClient implements ClientModInitializer {
    /** Mod id as declared in {@code fabric.mod.json}. */
    public static final String MOD_ID = "vanta";
    /** The mod's logger. */
    public static final Logger LOGGER = LoggerFactory.getLogger("VANTA");

    /** Identifier of the VANTA HUD element. */
    public static final Identifier HUD_ELEMENT = Identifier.fromNamespaceAndPath(MOD_ID, "hud");

    @Override
    public void onInitializeClient() {
        String loader = FabricLoader.getInstance().getModContainer("fabricloader")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        LOGGER.info("{} {} starting for Minecraft {} (Fabric Loader {}, Java {})",
                VantaVersion.BRAND, VantaVersion.CLIENT, VantaVersion.MINECRAFT, loader,
                Runtime.version().feature());

        CoreLog.install(new Slf4jCoreLogSink(LOGGER));
        Lang.install(new MinecraftLangProvider());

        // Key mappings must exist before the game reads options.txt.
        VantaKeyMappings keys = VantaKeyMappings.register();

        VantaPaths paths = new VantaPaths(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID));
        MinecraftResourcePackBridge resourcePacks = new MinecraftResourcePackBridge();
        MinecraftScreenshotBridge screenshots = new MinecraftScreenshotBridge();
        VantaServices services = VantaServices.create(paths, new MinecraftGameBridge(), new MinecraftOptionsBridge(),
                new MinecraftKeybindBridge(), resourcePacks, screenshots, new MinecraftClipboardBridge(),
                Clock.systemUTC());
        services.load();
        ScreenBootstrap.registerAll(services);

        VantaRuntime runtime = new VantaRuntime(services, resourcePacks, screenshots, keys);
        VantaRuntime.install(runtime);

        // Mods & Shaders: Modrinth work runs on its own daemon thread; results come back through the tick scheduler.
        ModrinthService modrinth = ModrinthService.create(new FabricModPlatform(), services.notifications(),
                runtime.scheduler()::nextTick, VantaVersion.CLIENT, Clock.systemUTC());
        services.setModrinth(modrinth);

        GameEvents.register(runtime);
        ScreenOverlays.register(runtime);
        VantaCommands.register(runtime);
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, HUD_ELEMENT, new VantaHudElement());
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, VantaCrosshairElement::new);

        LOGGER.info("VANTA ready: {} screens registered, config in {}", services.screens().registered().size(),
                paths.root());
    }
}
