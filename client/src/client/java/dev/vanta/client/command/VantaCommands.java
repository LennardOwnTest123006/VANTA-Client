package dev.vanta.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.vanta.client.VantaRuntime;
import dev.vanta.client.screen.VantaScreens;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.stats.SessionStats;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * The client-side {@code /vanta} command: {@code menu}, {@code hud}, {@code perf}, {@code nexus},
 * {@code profiles [name]}, {@code stats} and {@code reload}. Everything runs locally; nothing is sent to the server.
 * <p>
 * Screens are opened on the next tick because the chat screen closes itself after the command ran and would
 * otherwise replace the screen we just opened.
 */
public final class VantaCommands {
    private VantaCommands() {
    }

    /** Registers the command tree; call once from the client entrypoint. */
    public static void register(VantaRuntime runtime) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("vanta")
                        .executes(ctx -> usage(ctx))
                        .then(ClientCommandManager.literal("menu")
                                .executes(ctx -> openScreen(ctx, runtime, ScreenId.SETTINGS, "vanta.command.menu")))
                        .then(ClientCommandManager.literal("hud")
                                .executes(ctx -> openScreen(ctx, runtime, ScreenId.HUD_EDITOR, "vanta.command.hud")))
                        .then(ClientCommandManager.literal("perf")
                                .executes(ctx -> openScreen(ctx, runtime, ScreenId.PERFORMANCE, "vanta.command.perf")))
                        .then(ClientCommandManager.literal("nexus")
                                .executes(ctx -> openScreen(ctx, runtime, ScreenId.NEXUS, "vanta.command.nexus")))
                        .then(ClientCommandManager.literal("profiles")
                                .executes(ctx -> listProfiles(ctx, runtime))
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> activateProfile(ctx, runtime,
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(ClientCommandManager.literal("stats")
                                .executes(ctx -> stats(ctx, runtime)))
                        .then(ClientCommandManager.literal("reload")
                                .executes(ctx -> reload(ctx, runtime)))));
    }

    private static int usage(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(text("vanta.command.usage"));
        return 1;
    }

    private static int openScreen(CommandContext<FabricClientCommandSource> ctx, VantaRuntime runtime, ScreenId id,
                                  String feedbackKey) {
        // A feedback key the language file does not know yet falls back to the screen's own title, never to the
        // raw key (vanta.command.nexus is new in 1.4.0).
        String feedback = Lang.has(feedbackKey) ? Lang.tr(feedbackKey) : Lang.tr(id.langKey());
        ctx.getSource().sendFeedback(Component.literal(feedback));
        runtime.scheduler().nextTick(() -> VantaScreens.open(id, null));
        return 1;
    }

    private static int listProfiles(CommandContext<FabricClientCommandSource> ctx, VantaRuntime runtime) {
        VantaServices services = runtime.services();
        List<Profile> profiles = services.profiles().list();
        if (profiles.isEmpty()) {
            ctx.getSource().sendFeedback(text("vanta.command.profiles.none"));
            return 1;
        }
        Optional<String> active = services.profiles().activeId();
        ctx.getSource().sendFeedback(text("vanta.command.profiles.header", profiles.size()));
        for (Profile profile : profiles) {
            boolean isActive = active.isPresent() && active.get().equals(profile.id());
            String marker = Lang.tr(isActive ? "vanta.command.profiles.active_marker"
                    : "vanta.command.profiles.inactive_marker");
            ctx.getSource().sendFeedback(text("vanta.command.profiles.entry", marker, profile.name(), profile.id()));
        }
        return profiles.size();
    }

    private static int activateProfile(CommandContext<FabricClientCommandSource> ctx, VantaRuntime runtime,
                                       String query) {
        VantaServices services = runtime.services();
        String wanted = query.trim().toLowerCase(Locale.ROOT);
        Optional<Profile> match = services.profiles().list().stream()
                .filter(p -> p.id().equalsIgnoreCase(wanted) || p.name().toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst();
        if (match.isEmpty()) {
            ctx.getSource().sendError(text("vanta.command.profiles.unknown", query.trim()));
            return 0;
        }
        if (!services.activateProfile(match.get().id())) {
            ctx.getSource().sendError(text("vanta.command.profiles.unknown", query.trim()));
            return 0;
        }
        ctx.getSource().sendFeedback(text("vanta.command.profiles.activated", match.get().name()));
        return 1;
    }

    private static int stats(CommandContext<FabricClientCommandSource> ctx, VantaRuntime runtime) {
        VantaServices services = runtime.services();
        if (!services.stats().privacy().enabled()) {
            ctx.getSource().sendFeedback(text("vanta.command.stats.disabled"));
            return 1;
        }
        Optional<SessionStats> session = services.stats().current();
        if (session.isEmpty()) {
            ctx.getSource().sendFeedback(text("vanta.command.stats.none"));
            return 1;
        }
        SessionStats s = session.get();
        ctx.getSource().sendFeedback(text("vanta.command.stats", formatDuration(s.playtimeMs()), s.blocksBroken(),
                s.blocksPlaced(), Math.round(s.distanceBlocks()), Math.round(s.averageFps())));
        return 1;
    }

    private static int reload(CommandContext<FabricClientCommandSource> ctx, VantaRuntime runtime) {
        runtime.reloadConfiguration();
        ctx.getSource().sendFeedback(text("vanta.command.reload"));
        return 1;
    }

    /** {@code h:mm:ss} or {@code m:ss}. */
    static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private static Component text(String key, Object... args) {
        return Component.literal(Lang.tr(key, args));
    }
}
