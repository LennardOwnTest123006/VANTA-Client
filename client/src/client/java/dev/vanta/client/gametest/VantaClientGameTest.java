package dev.vanta.client.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.client.VantaClient;
import dev.vanta.client.VantaRuntime;
import dev.vanta.client.screen.MainMenuReplacement;
import dev.vanta.client.screen.VantaScreen;
import dev.vanta.client.screen.VantaScreens;
import dev.vanta.core.bridge.KeyBinding;
import dev.vanta.core.bridge.PackInfo;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudStore;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.keybinds.VantaKeys;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * Automated client game test. Only runs when Minecraft is started with {@code -Dfabric.client.gametest}; it is inert
 * in normal play. CI launches the real built jar with this flag and keeps the screenshots.
 * <p>
 * Steps (every check throws on failure, which fails the Gradle task):
 * <ol>
 *   <li>the VANTA main menu replaced the title screen → {@code 01_main_menu};</li>
 *   <li>every VANTA screen opens through the registry → {@code 02_settings} … {@code 13_about};</li>
 *   <li>a setting written through the core API is persisted to {@code settings.json};</li>
 *   <li>a profile can be created and activated and exists on disk;</li>
 *   <li>the keybind model lists mappings (including VANTA's) and the conflict detector runs;</li>
 *   <li>the resource pack bridge lists the vanilla "Default" pack as enabled;</li>
 *   <li>in a fresh creative world: HUD widgets enabled → {@code 20_hud_ingame}, the BALANCED preset sets render
 *       distance 10, the in-game VANTA menu → {@code 21_ingame_menu};</li>
 *   <li>the test ends on the vanilla title screen as the Fabric runner requires.</li>
 * </ol>
 */
public final class VantaClientGameTest implements FabricClientGameTest {
    /** Screens exercised after the main menu, in screenshot order. */
    private static final Map<ScreenId, String> SCREENSHOTS = Map.ofEntries(
            Map.entry(ScreenId.SETTINGS, "02_settings"),
            Map.entry(ScreenId.HUD_EDITOR, "03_hud_editor"),
            Map.entry(ScreenId.PERFORMANCE, "04_performance"),
            Map.entry(ScreenId.PROFILES, "05_profiles"),
            Map.entry(ScreenId.KEYBINDS, "06_keybinds"),
            Map.entry(ScreenId.CROSSHAIR, "07_crosshair"),
            Map.entry(ScreenId.COSMETICS, "08_cosmetics"),
            Map.entry(ScreenId.STATISTICS, "09_statistics"),
            Map.entry(ScreenId.RESOURCE_PACKS, "10_resource_packs"),
            Map.entry(ScreenId.ACCESSIBILITY, "11_accessibility"),
            Map.entry(ScreenId.SEARCH, "12_search"),
            Map.entry(ScreenId.ABOUT, "13_about"));

    private static final List<ScreenId> SCREEN_ORDER = List.of(ScreenId.SETTINGS, ScreenId.HUD_EDITOR,
            ScreenId.PERFORMANCE, ScreenId.PROFILES, ScreenId.KEYBINDS, ScreenId.CROSSHAIR, ScreenId.COSMETICS,
            ScreenId.STATISTICS, ScreenId.RESOURCE_PACKS, ScreenId.ACCESSIBILITY, ScreenId.SEARCH, ScreenId.ABOUT);

    private static final List<HudWidgetType> INGAME_WIDGETS = List.of(HudWidgetType.FPS, HudWidgetType.COORDINATES,
            HudWidgetType.BIOME, HudWidgetType.CLOCK, HudWidgetType.MEMORY, HudWidgetType.KEYSTROKES,
            HudWidgetType.ARMOR, HudWidgetType.PING);

    @Override
    public void runTest(ClientGameTestContext context) {
        VantaRuntime runtime = VantaRuntime.get();
        check(runtime != null, "VANTA runtime was not initialised by the client entrypoint");
        VantaServices services = runtime.services();
        check(services.isLoaded(), "VANTA services are not loaded");
        step("start: config root " + services.paths().root());

        mainMenu(context);
        for (ScreenId id : SCREEN_ORDER) {
            openAndCapture(context, services, id, SCREENSHOTS.get(id));
        }
        settingsRoundTrip(context, services);
        profiles(context, services);
        keybinds(context, services);
        resourcePacks(context, runtime);
        inWorld(context, services);
        finishOnVanillaTitleScreen(context);
        step("done: all steps passed");
    }

    // ---- steps -------------------------------------------------------------------------------------------------

    private static void mainMenu(ClientGameTestContext context) {
        context.waitFor(client -> client.screen instanceof VantaScreen || client.screen instanceof TitleScreen);
        context.waitTicks(20);
        boolean custom = context.computeOnClient(client -> client.screen instanceof VantaScreen vanta
                && vanta.screenId() == ScreenId.MAIN_MENU);
        check(custom, "The VANTA main menu did not replace the vanilla title screen");
        Path shot = context.takeScreenshot("01_main_menu");
        step("main menu shown → " + shot.getFileName());
    }

    private static void openAndCapture(ClientGameTestContext context, VantaServices services, ScreenId id,
                                       String screenshot) {
        boolean registered = context.computeOnClient(client -> services.screens().isRegistered(id));
        check(registered, "No screen factory registered for " + id.id());
        context.setScreen(() -> VantaScreens.create(id, null));
        context.waitFor(client -> client.screen instanceof VantaScreen vanta && vanta.screenId() == id);
        context.waitTicks(10);
        Path shot = context.takeScreenshot(screenshot);
        step("screen " + id.id() + " → " + shot.getFileName());
    }

    private static void settingsRoundTrip(ClientGameTestContext context, VantaServices services) {
        context.runOnClient(client -> {
            services.settings().set(VantaSettings.HUD_TEXT_SHADOW, false);
            services.settings().save();
        });
        Path file = services.paths().settingsFile();
        check(Files.exists(file), "settings.json was not written to " + file);
        Optional<Boolean> stored = readBoolean(file, VantaSettings.HUD_TEXT_SHADOW.id());
        check(stored.isPresent() && !stored.get(),
                "settings.json does not contain " + VantaSettings.HUD_TEXT_SHADOW.id() + " = false");
        context.runOnClient(client -> {
            services.settings().set(VantaSettings.HUD_TEXT_SHADOW, true);
            services.settings().save();
        });
        Optional<Boolean> restored = readBoolean(file, VantaSettings.HUD_TEXT_SHADOW.id());
        check(restored.isPresent() && restored.get(), "settings.json did not record the restored value");
        step("settings persisted to " + file.getFileName());
    }

    private static void profiles(ClientGameTestContext context, VantaServices services) {
        Profile created = context.computeOnClient(client ->
                services.profiles().createFromCurrent("Game test profile", "profile"));
        check(created != null, "ProfileManager.createFromCurrent returned null");
        boolean activated = context.computeOnClient(client -> services.activateProfile(created.id()));
        check(activated, "Profile " + created.id() + " could not be activated");
        context.runOnClient(client -> services.saveAll());
        Path file = services.paths().profilesDir().resolve(created.id() + ".json");
        check(Files.exists(file), "Profile file missing: " + file);
        step("profile " + created.id() + " created, activated and saved");
    }

    private static void keybinds(ClientGameTestContext context, VantaServices services) {
        List<KeyBinding> all = context.computeOnClient(client -> {
            services.keybinds().refresh();
            return services.keybinds().all();
        });
        check(!all.isEmpty(), "Keybind model lists no mappings");
        check(all.stream().anyMatch(b -> VantaKeys.OPEN_MENU.equals(b.id())),
                "Keybind model does not contain " + VantaKeys.OPEN_MENU);
        int conflicts = context.computeOnClient(client -> services.keybinds().conflicts().size());
        step(all.size() + " key mappings listed, conflict detector reports " + conflicts + " conflict(s)");
    }

    private static void resourcePacks(ClientGameTestContext context, VantaRuntime runtime) {
        List<PackInfo> packs = context.computeOnClient(client -> runtime.resourcePacks().packs());
        check(packs.stream().anyMatch(p -> "vanilla".equals(p.id()) && p.enabled()),
                "Resource pack bridge does not list the enabled vanilla pack: " + packs);
        step(packs.size() + " resource packs listed (vanilla enabled)");
    }

    private static void inWorld(ClientGameTestContext context, VantaServices services) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(ui -> ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
                .create()) {
            world.getClientWorld().waitForChunksRender();
            step("creative world loaded");
            context.runOnClient(client -> enableWidgets(services));
            context.waitTicks(20);
            Path hudShot = context.takeScreenshot("20_hud_ingame");
            step("HUD widgets enabled → " + hudShot.getFileName());

            context.runOnClient(client -> services.performance().applyPreset(PerformancePreset.BALANCED));
            context.waitTick();
            int renderDistance = context.computeOnClient(client -> client.options.renderDistance().get());
            check(renderDistance == PerformancePreset.BALANCED.renderDistance(),
                    "BALANCED preset should set render distance " + PerformancePreset.BALANCED.renderDistance()
                            + " but the game reports " + renderDistance);
            step("BALANCED preset applied (render distance " + renderDistance + ")");

            openAndCapture(context, services, ScreenId.SETTINGS, "21_ingame_menu");
            context.setScreen(() -> null);
            context.waitForScreen(null);
            step("in-game menu closed");
        }
    }

    private static void enableWidgets(VantaServices services) {
        HudStore hud = services.hud();
        HudLayout layout = hud.layout();
        for (HudWidgetType type : INGAME_WIDGETS) {
            List<HudWidgetState> existing = layout.byType(type);
            if (existing.isEmpty()) {
                layout = layout.with(HudWidgetState.defaults(layout.nextId(type), type));
            } else {
                for (HudWidgetState widget : existing) {
                    if (!widget.enabled()) {
                        layout = layout.with(widget.withEnabled(true));
                    }
                }
            }
        }
        hud.setLayout(layout);
        services.settings().set(VantaSettings.HUD_ENABLED, true);
    }

    private static void finishOnVanillaTitleScreen(ClientGameTestContext context) {
        MainMenuReplacement.setSuppressed(true);
        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
        step("returned to the vanilla title screen");
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private static void step(String message) {
        VantaClient.LOGGER.info("[VANTA gametest] {}", message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            VantaClient.LOGGER.error("[VANTA gametest] FAILED: {}", message);
            throw new AssertionError(message);
        }
    }

    /** Reads a boolean property anywhere in a JSON file (the settings file nests values under one object). */
    static Optional<Boolean> readBoolean(Path file, String key) {
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file));
            return findBoolean(root, key);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<Boolean> findBoolean(JsonElement element, String key) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement direct = object.get(key);
        if (direct != null && direct.isJsonPrimitive() && direct.getAsJsonPrimitive().isBoolean()) {
            return Optional.of(direct.getAsBoolean());
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            Optional<Boolean> nested = findBoolean(entry.getValue(), key);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }
}
