package dev.vanta.client.event;

import dev.vanta.client.VantaClient;
import dev.vanta.client.VantaRuntime;
import dev.vanta.client.screen.VantaScreens;
import dev.vanta.core.keybinds.VantaKeys;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.search.ActionEntry;
import dev.vanta.core.stats.StatsTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;

/**
 * Wires the Fabric client events to the core services: ticking (services, HUD, crosshair, notifications, screenshots,
 * the Vanta Lab input relay), lifecycle saves, statistics sessions and the VANTA key mappings. All handlers are
 * observers of the user's own actions; none changes gameplay.
 */
public final class GameEvents {
    private GameEvents() {
    }

    /** Registers every event handler; call once from the client entrypoint. */
    public static void register(VantaRuntime runtime) {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> runtime.onClientStarted());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> runtime.shutdown());
        ClientTickEvents.END_CLIENT_TICK.register(client -> onEndTick(runtime, client));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(runtime, client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> runtime.services().stats().onLeave());
        ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) ->
                runtime.services().stats().onBlockBroken());
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() && player.getItemInHand(hand).getItem() instanceof BlockItem) {
                runtime.services().stats().onBlockPlaced();
            }
            return InteractionResult.PASS;
        });
    }

    private static void onEndTick(VantaRuntime runtime, Minecraft client) {
        runtime.services().tick();
        runtime.labInputs().tick(client);
        runtime.hud().tick();
        runtime.crosshair().tick();
        runtime.notifications().tick();
        runtime.screenshots().tick();
        runtime.keys().syncZoomKey(runtime.services().settings());
        runtime.keys().pollClicks(id -> onKeyClick(runtime, client, id));
        runtime.scheduler().runPending();
    }

    private static void onKeyClick(VantaRuntime runtime, Minecraft client, String id) {
        if (client.screen != null) {
            return;
        }
        switch (id) {
            case VantaKeys.OPEN_MENU -> VantaScreens.open(ScreenId.SETTINGS, null);
            case VantaKeys.TOGGLE_HUD -> runtime.services().runAction(ActionEntry.TOGGLE_HUD);
            case VantaKeys.HUD_EDITOR -> VantaScreens.open(ScreenId.HUD_EDITOR, null);
            case VantaKeys.PERFORMANCE -> VantaScreens.open(ScreenId.PERFORMANCE, null);
            case VantaKeys.OPEN_NEXUS -> VantaScreens.open(ScreenId.NEXUS, null);
            case VantaKeys.SCREENSHOT_HUD_FREE -> runtime.services().runAction(ActionEntry.SCREENSHOT_HUD_FREE);
            default -> {
                // The zoom key is read while held (see ZoomController), not on click.
            }
        }
    }

    private static void onJoin(VantaRuntime runtime, Minecraft client) {
        StatsTracker stats = runtime.services().stats();
        if (client.isLocalServer()) {
            stats.onJoinWorld(worldName(client));
            return;
        }
        ServerData server = client.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            stats.onJoinServer(server.ip);
        }
    }

    private static String worldName(Minecraft client) {
        try {
            IntegratedServer server = client.getSingleplayerServer();
            if (server != null) {
                return server.getWorldData().getLevelName();
            }
        } catch (RuntimeException e) {
            VantaClient.LOGGER.debug("Could not read the world name", e);
        }
        return "";
    }
}
