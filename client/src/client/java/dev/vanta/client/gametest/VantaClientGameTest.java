package dev.vanta.client.gametest;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.platform.InputConstants;
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
import dev.vanta.core.modrinth.PerformancePack;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.mods.ModsScreen;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.widget.Button;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * Automated client game test. Only runs when Minecraft is started with {@code -Dfabric.client.gametest}; it is inert
 * in normal play. CI launches the real built jar with this flag and keeps the screenshots.
 * <p>
 * Steps (every check throws on failure, which fails the Gradle task):
 * <ol>
 *   <li>the VANTA main menu replaced the title screen → {@code 01_main_menu};</li>
 *   <li>before any world exists, every way back from the vanilla screens the menu opens ends on the VANTA menu again:
 *       Singleplayer opens Create New World (no worlds yet) → Escape, and → Cancel; Multiplayer → Escape, and → Back;
 *       {@code setScreen(null)} without a world;</li>
 *   <li>every VANTA screen opens through the registry → {@code 02_settings} … {@code 14_mods} (Mods &amp; Shaders
 *       waits for its first Modrinth answer);</li>
 *   <li>the Performance pack mods the run was started with are loaded ({@code -Dvanta.gametest.expectMods}); with Iris
 *       loaded its shader pack screen opens from VANTA → {@code 15_iris_shader_packs};</li>
 *   <li>a setting written through the core API is persisted to {@code settings.json};</li>
 *   <li>a profile can be created and activated and exists on disk;</li>
 *   <li>the keybind model lists mappings (including VANTA's) and the conflict detector runs;</li>
 *   <li>the resource pack bridge lists the vanilla "Default" pack as enabled;</li>
 *   <li>in a fresh creative world: HUD widgets enabled → {@code 20_hud_ingame}, the BALANCED preset sets render
 *       distance 10, the in-game VANTA menu → {@code 21_ingame_menu};</li>
 *   <li>with a world on disk, Singleplayer opens the world list → Escape, and → Back, both back on the VANTA menu;</li>
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
            Map.entry(ScreenId.ABOUT, "13_about"),
            Map.entry(ScreenId.MODS, "14_mods"));

    private static final List<ScreenId> SCREEN_ORDER = List.of(ScreenId.SETTINGS, ScreenId.HUD_EDITOR,
            ScreenId.PERFORMANCE, ScreenId.PROFILES, ScreenId.KEYBINDS, ScreenId.CROSSHAIR, ScreenId.COSMETICS,
            ScreenId.STATISTICS, ScreenId.RESOURCE_PACKS, ScreenId.ACCESSIBILITY, ScreenId.SEARCH, ScreenId.ABOUT,
            ScreenId.MODS);

    /** Ticks a vanilla screen may take to appear (Create New World loads the data packs first). */
    private static final int SCREEN_TIMEOUT_TICKS = 600;
    /** Ticks the Mods &amp; Shaders screen may wait for Modrinth before the screenshot is taken anyway. */
    private static final int MODRINTH_TIMEOUT_TICKS = 400;
    /** JVM property with the Fabric mod ids that must be loaded (set by the Performance pack CI job). */
    private static final String EXPECT_MODS_PROPERTY = "vanta.gametest.expectMods";

    private static final int CAPTURE_WIDTH = 1920;
    private static final int CAPTURE_HEIGHT = 1080;
    private static final int CAPTURE_GUI_SCALE = 2;

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

        // Capture at 1920x1080 with GUI scale 2 (960x540 logical pixels): the desktop layout of every screen, which is
        // what the published screenshots show. The framework restores the window size when the test ends.
        context.getInput().resizeWindow(CAPTURE_WIDTH, CAPTURE_HEIGHT);
        context.runOnClient(client -> {
            client.options.guiScale().set(CAPTURE_GUI_SCALE);
            client.resizeDisplay();
        });
        context.waitTicks(2);
        int guiWidth = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());
        step("window " + CAPTURE_WIDTH + "x" + CAPTURE_HEIGHT + " at GUI scale " + CAPTURE_GUI_SCALE
                + " → " + guiWidth + " logical px wide");

        mainMenu(context);
        backPathsBeforeAnyWorld(context);
        for (ScreenId id : SCREEN_ORDER) {
            openAndCapture(context, services, id, SCREENSHOTS.get(id));
        }
        performanceMods(context, services);
        settingsRoundTrip(context, services);
        profiles(context, services);
        keybinds(context, services);
        resourcePacks(context, runtime);
        inWorld(context, services);
        backPathsWithAWorld(context);
        finishOnVanillaTitleScreen(context);
        step("done: all steps passed");
    }

    // ---- steps -------------------------------------------------------------------------------------------------

    private static void mainMenu(ClientGameTestContext context) {
        context.waitFor(client -> client.screen instanceof VantaScreen || client.screen instanceof TitleScreen);
        parkCursor(context);
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
        parkCursor(context);
        context.waitTicks(10);
        if (id == ScreenId.MODS) {
            // The first list comes from Modrinth; capture it once it arrived (or failed: the test does not depend on
            // the network, the error state is a valid screen too).
            boolean answered = pollFor(context, client -> client.screen instanceof VantaScreen vanta
                    && vanta.ui() instanceof ModsScreen mods && !mods.isSearching(), MODRINTH_TIMEOUT_TICKS);
            String state = context.computeOnClient(client -> client.screen instanceof VantaScreen vanta
                    && vanta.ui() instanceof ModsScreen mods
                    ? mods.searchError().map(e -> "Modrinth error " + e.kind())
                    .orElse(mods.results().hits().size() + " Modrinth results") : "not the Mods screen");
            step("Mods & Shaders: " + (answered ? state : "Modrinth did not answer in time"));
            context.waitTicks(5);
        }
        Path shot = context.takeScreenshot(screenshot);
        step("screen " + id.id() + " → " + shot.getFileName());
    }

    /**
     * The flow players reported: with no world yet, Singleplayer on the VANTA menu opens Create New World; leaving it
     * with Escape or Cancel used to end on the vanilla title screen. Multiplayer's Escape / Back and a plain
     * {@code setScreen(null)} without a world must also land on the VANTA menu.
     */
    private static void backPathsBeforeAnyWorld(ClientGameTestContext context) {
        int worlds = countWorlds(context);
        Class<? extends Screen> singleplayer = worlds == 0 ? CreateWorldScreen.class : SelectWorldScreen.class;
        step(worlds == 0 ? "no worlds yet: Singleplayer must open Create New World"
                : worlds + " world(s) already on disk (reused run directory): Singleplayer opens the world list");

        clickMainMenuButton(context, "menu.singleplayer");
        waitForScreen(context, singleplayer, "Singleplayer");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        expectVantaMainMenu(context, "Singleplayer → " + singleplayer.getSimpleName() + " → Escape");

        clickMainMenuButton(context, "menu.singleplayer");
        waitForScreen(context, singleplayer, "Singleplayer");
        pressBackButton(context, worlds == 0 ? "gui.cancel" : "gui.back", "gui.cancel", "gui.back");
        expectVantaMainMenu(context, "Singleplayer → " + singleplayer.getSimpleName() + " → Cancel");

        clickMainMenuButton(context, "menu.multiplayer");
        waitForScreen(context, JoinMultiplayerScreen.class, "Multiplayer");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        expectVantaMainMenu(context, "Multiplayer → JoinMultiplayerScreen → Escape");

        clickMainMenuButton(context, "menu.multiplayer");
        waitForScreen(context, JoinMultiplayerScreen.class, "Multiplayer");
        pressBackButton(context, "gui.back", "gui.cancel", "gui.done");
        expectVantaMainMenu(context, "Multiplayer → JoinMultiplayerScreen → Back");

        context.setScreen(() -> null);
        expectVantaMainMenu(context, "setScreen(null) without a world");
    }

    /** With a world on disk Singleplayer opens the world list; Escape and Back return to the VANTA menu. */
    private static void backPathsWithAWorld(ClientGameTestContext context) {
        expectVantaMainMenu(context, "leaving the world");
        check(countWorlds(context) > 0, "the game test world was not saved to disk");
        clickMainMenuButton(context, "menu.singleplayer");
        waitForScreen(context, SelectWorldScreen.class, "Singleplayer (with a world)");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        expectVantaMainMenu(context, "Singleplayer → SelectWorldScreen → Escape");

        clickMainMenuButton(context, "menu.singleplayer");
        waitForScreen(context, SelectWorldScreen.class, "Singleplayer (with a world)");
        pressBackButton(context, "gui.back", "gui.cancel");
        expectVantaMainMenu(context, "Singleplayer → SelectWorldScreen → Back");
    }

    /**
     * Logs which Performance pack mods are loaded, checks the ones the run was started with
     * ({@value #EXPECT_MODS_PROPERTY}) and, when Iris is loaded, opens its shader pack screen the way the Mods &amp;
     * Shaders "Open shader settings" button does.
     */
    private static void performanceMods(ClientGameTestContext context, VantaServices services) {
        FabricLoader loader = FabricLoader.getInstance();
        List<String> loaded = new ArrayList<>();
        for (PerformancePack.Item item : PerformancePack.ITEMS) {
            if (loader.isModLoaded(item.modId())) {
                loaded.add(item.modId());
            }
        }
        step("performance mods loaded: " + (loaded.isEmpty() ? "none" : String.join(", ", loaded)));
        String expected = System.getProperty(EXPECT_MODS_PROPERTY, "");
        for (String modId : expected.split(",")) {
            String id = modId.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) {
                check(loader.isModLoaded(id), "expected mod " + id + " is not loaded");
            }
        }
        if (!loader.isModLoaded("iris")) {
            return;
        }
        context.setScreen(() -> VantaScreens.create(ScreenId.MAIN_MENU, null));
        expectVantaMainMenu(context, "VANTA main menu before opening Iris");
        boolean opened = context.computeOnClient(client -> services.modrinth()
                .map(modrinth -> modrinth.platform().openShaderPackScreen()).orElse(false));
        check(opened, "Iris is loaded but its shader pack screen could not be opened from VANTA");
        waitForScreen(context, client -> client.screen != null
                && client.screen.getClass().getName().startsWith("net.irisshaders."), "the Iris shader pack screen");
        parkCursor(context);
        context.waitTicks(10);
        Path shot = context.takeScreenshot("15_iris_shader_packs");
        step("Iris shader pack screen opened from VANTA → " + shot.getFileName());
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        expectVantaMainMenu(context, "Iris shader pack screen → Escape");
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
            context.runOnClient(client -> enableWidgets(services, client.getWindow().getGuiScaledWidth(),
                    client.getWindow().getGuiScaledHeight()));
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

    private static void enableWidgets(VantaServices services, int guiWidth, int guiHeight) {
        HudStore hud = services.hud();
        HudLayout layout = hud.layout();
        for (HudWidgetType type : INGAME_WIDGETS) {
            List<HudWidgetState> existing = layout.byType(type);
            if (existing.isEmpty()) {
                // Same placement rule as the HUD editor's add button: clear of the widgets already there.
                layout = layout.with(layout.placedClear(HudWidgetState.defaults(layout.nextId(type), type), guiWidth,
                        guiHeight));
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

    /** Presses a button of the VANTA main menu through the core widget (the same code path as a mouse click). */
    private static void clickMainMenuButton(ClientGameTestContext context, String id) {
        expectVantaMainMenu(context, "before pressing " + id);
        boolean clicked = context.computeOnClient(client -> {
            if (!(client.screen instanceof VantaScreen vanta) || vanta.screenId() != ScreenId.MAIN_MENU) {
                return false;
            }
            UiNode node = vanta.ui().root().findById(id);
            if (!(node instanceof Button button)) {
                return false;
            }
            button.click(vanta.ui().context());
            return true;
        });
        check(clicked, "VANTA main menu button " + id + " was not found");
    }

    /** Polls once per tick; true when the predicate held within {@code timeoutTicks}. */
    private static boolean pollFor(ClientGameTestContext context, Predicate<Minecraft> predicate, int timeoutTicks) {
        for (int i = 0; i < timeoutTicks; i++) {
            if (context.computeOnClient(predicate::test)) {
                return true;
            }
            context.waitTick();
        }
        return context.computeOnClient(predicate::test);
    }

    private static String currentScreen(ClientGameTestContext context) {
        return context.computeOnClient(client -> client.screen == null ? "no screen"
                : client.screen instanceof VantaScreen vanta ? "VantaScreen(" + vanta.screenId().id() + ")"
                : client.screen.getClass().getName());
    }

    private static void waitForScreen(ClientGameTestContext context, Class<? extends Screen> type, String after) {
        waitForScreen(context, client -> type.isInstance(client.screen), after + " → " + type.getSimpleName());
    }

    private static void waitForScreen(ClientGameTestContext context, Predicate<Minecraft> predicate, String what) {
        boolean shown = pollFor(context, predicate, SCREEN_TIMEOUT_TICKS);
        check(shown, "expected " + what + " but the screen is " + currentScreen(context));
    }

    /** The current screen must become the VANTA main menu (never the vanilla title screen). */
    private static void expectVantaMainMenu(ClientGameTestContext context, String path) {
        boolean shown = pollFor(context, client -> client.screen instanceof VantaScreen vanta
                && vanta.screenId() == ScreenId.MAIN_MENU, 100);
        check(shown, path + " ended on " + currentScreen(context) + " instead of the VANTA main menu");
        step(path + " → VANTA main menu");
    }

    /** Presses the first vanilla button found with one of the translation keys. */
    private static void pressBackButton(ClientGameTestContext context, String... translationKeys) {
        for (String key : translationKeys) {
            if (context.tryClickScreenButton(key)) {
                return;
            }
        }
        check(false, "no button " + String.join(" / ", translationKeys) + " on " + currentScreen(context));
    }

    /** Worlds in the saves folder (directories with a level.dat). */
    private static int countWorlds(ClientGameTestContext context) {
        Path saves = context.computeOnClient(client -> client.getLevelSource().getBaseDir());
        if (saves == null || !Files.isDirectory(saves)) {
            return 0;
        }
        try (Stream<Path> dirs = Files.list(saves)) {
            return (int) dirs.filter(dir -> Files.isRegularFile(dir.resolve("level.dat"))).count();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Moves the mouse into the bottom-right corner so captures show no hover highlight or tooltip. */
    private static void parkCursor(ClientGameTestContext context) {
        context.getInput().setCursorPos(CAPTURE_WIDTH - 2, CAPTURE_HEIGHT - 2);
    }

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
