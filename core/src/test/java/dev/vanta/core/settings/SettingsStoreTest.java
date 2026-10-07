package dev.vanta.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.cosmetics.MenuBackground;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsStoreTest {
    @TempDir
    Path dir;

    private JsonStore jsonStore;
    private FakeOptionsBridge options;
    private SettingsStore store;

    @BeforeEach
    void setUp() {
        jsonStore = new JsonStore(MutableClock.standard());
        options = new FakeOptionsBridge();
        store = new SettingsStore(VantaSettings.registry(), jsonStore, dir.resolve("settings.json"),
                Optional.of(options));
    }

    @Test
    void defaultsApplyBeforeLoadAndAfterMissingFile() {
        store.load();
        assertEquals(Boolean.TRUE, store.get(VantaSettings.HUD_ENABLED));
        assertEquals(1.0, store.get(VantaSettings.HUD_GLOBAL_SCALE));
        assertEquals(MenuBackground.VIOLET_HORIZON, store.get(VantaSettings.MENU_BACKGROUND));
        assertTrue(store.isDefault(VantaSettings.HUD_ENABLED));
        assertFalse(store.isDirty());
    }

    @Test
    void setNormalisesAndNotifies() {
        List<SettingChange> changes = new ArrayList<>();
        store.addListener(changes::add);
        assertTrue(store.set(VantaSettings.HUD_GLOBAL_SCALE, 9.0));
        assertEquals(2.0, store.get(VantaSettings.HUD_GLOBAL_SCALE), "clamped to max");
        assertFalse(store.set(VantaSettings.HUD_GLOBAL_SCALE, 2.0), "no change, no event");
        assertEquals(1, changes.size());
        assertEquals(1.0, changes.get(0).oldValue());
        assertEquals(2.0, changes.get(0).newValueAs(VantaSettings.HUD_GLOBAL_SCALE));
        assertTrue(store.isDirty());
    }

    @Test
    void perSettingListenerCanUnsubscribe() {
        List<Boolean> seen = new ArrayList<>();
        Runnable unsubscribe = store.onChange(VantaSettings.HUD_ENABLED, (oldV, newV) -> seen.add(newV));
        store.set(VantaSettings.HUD_ENABLED, false);
        store.set(VantaSettings.HUD_SNAP, false);
        unsubscribe.run();
        store.set(VantaSettings.HUD_ENABLED, true);
        assertEquals(List.of(false), seen);
    }

    @Test
    void resetSettingCategoryAndAll() {
        store.set(VantaSettings.HUD_ENABLED, false);
        store.set(VantaSettings.HUD_SNAP, false);
        store.set(VantaSettings.GENERAL_UI_SOUNDS, false);
        assertTrue(store.reset(VantaSettings.HUD_ENABLED));
        assertTrue(store.get(VantaSettings.HUD_ENABLED));
        assertEquals(1, store.resetCategory(SettingCategory.HUD));
        assertTrue(store.get(VantaSettings.HUD_SNAP));
        assertFalse(store.get(VantaSettings.GENERAL_UI_SOUNDS));
        store.resetAll();
        assertTrue(store.get(VantaSettings.GENERAL_UI_SOUNDS));
        assertTrue(store.reset("hud.enabled") || store.isDefault(VantaSettings.HUD_ENABLED));
    }

    @Test
    void persistenceRoundTripKeepsUnknownKeysAndIgnoresInvalid() throws IOException {
        Path file = dir.resolve("settings.json");
        Files.writeString(file, """
                {"schemaVersion": 1, "values": {
                  "hud.enabled": false,
                  "hud.globalScale": "1.5",
                  "menu.background": "graphite_grid",
                  "general.notificationDurationMs": "not a number",
                  "future.setting": {"nested": true}
                }}
                """, StandardCharsets.UTF_8);
        store.load();
        assertFalse(store.get(VantaSettings.HUD_ENABLED));
        assertEquals(1.5, store.get(VantaSettings.HUD_GLOBAL_SCALE));
        assertEquals(MenuBackground.GRAPHITE_GRID, store.get(VantaSettings.MENU_BACKGROUND));
        assertEquals(4000, store.get(VantaSettings.GENERAL_NOTIFICATION_DURATION), "invalid value → default");
        assertEquals(List.of("future.setting"), store.unknownIds());

        store.set(VantaSettings.HUD_SNAP, false);
        store.save();
        JsonObject written = jsonStore.readObject(file).orElseThrow();
        JsonObject values = written.getAsJsonObject("values");
        assertEquals(1, written.get("schemaVersion").getAsInt());
        assertFalse(values.get("hud.snap").getAsBoolean());
        assertTrue(values.has("future.setting"), "unknown ids preserved");
        assertFalse(values.has("video.renderDistance"), "vanilla-bound settings are not persisted");
        assertFalse(values.has("general.resetAll"), "actions are not persisted");

        SettingsStore reloaded = new SettingsStore(VantaSettings.registry(), jsonStore, file, Optional.empty());
        reloaded.load();
        assertFalse(reloaded.get(VantaSettings.HUD_SNAP));
        assertFalse(reloaded.get(VantaSettings.HUD_ENABLED));
    }

    @Test
    void vanillaBoundSettingsGoThroughOptionsBridge() {
        @SuppressWarnings("unchecked")
        Setting<Integer> renderDistance = (Setting<Integer>) VantaSettings.VIDEO_RENDER_DISTANCE;
        assertEquals(12, store.get(renderDistance));
        assertTrue(store.set(renderDistance, 20));
        assertEquals(20, options.getInt(VanillaOption.RENDER_DISTANCE, 0));
        assertTrue(store.isDirty());
        store.save();
        assertEquals(1, options.saveCount);

        @SuppressWarnings("unchecked")
        Setting<String> graphics = (Setting<String>) VantaSettings.VIDEO_GRAPHICS_MODE;
        assertTrue(store.set(graphics, "fabulous"));
        assertEquals("FABULOUS", options.getEnum(VanillaOption.GRAPHICS_MODE, ""));
        assertEquals(List.of("FAST", "FANCY", "FABULOUS", "CUSTOM"), graphics.options(),
                "Custom is listed so the row can show the game's real state; it cannot be chosen");
    }

    @Test
    void unsupportedVanillaOptionFallsBackToDefault() {
        options.withoutSupportFor(VanillaOption.PANORAMA_SPEED);
        @SuppressWarnings("unchecked")
        Setting<Double> panorama = (Setting<Double>) VantaSettings.VIDEO_PANORAMA_SPEED;
        assertEquals(1.0, store.get(panorama));
        assertTrue(store.set(panorama, 0.5));
        assertEquals(0.5, store.get(panorama), "stored locally when the game lacks the option");
    }

    @Test
    void snapshotsRoundTrip() {
        store.set(VantaSettings.HUD_ENABLED, false);
        @SuppressWarnings("unchecked")
        Setting<Integer> fov = (Setting<Integer>) VantaSettings.VIDEO_FOV;
        store.set(fov, 90);
        Map<String, JsonElement> snapshot = store.snapshot(true);
        assertFalse(snapshot.get("hud.enabled").getAsBoolean());
        assertEquals(90, snapshot.get("video.fov").getAsInt());
        assertFalse(snapshot.containsKey("general.resetAll"));
        assertFalse(store.snapshot(false).containsKey("video.fov"));

        store.resetAll();
        assertTrue(store.get(VantaSettings.HUD_ENABLED));
        snapshot.put("unknown.id", snapshot.get("hud.enabled"));
        int changed = store.applySnapshot(snapshot);
        assertTrue(changed >= 2);
        assertFalse(store.get(VantaSettings.HUD_ENABLED));
        assertEquals(90, store.get(fov));
    }

    // ---- graphics preset persistence (Minecraft 1.21.11 Fast / Fancy / Fabulous / Custom) ---------------------------

    @SuppressWarnings("unchecked")
    private static Setting<String> graphics() {
        return (Setting<String>) VantaSettings.VIDEO_GRAPHICS_MODE;
    }

    @Test
    void customGraphicsPresetReadsAsCustomNotAsTheFancyFallback() {
        options.graphicsPresetBundle = true;
        options.setFromGame(VanillaOption.GRAPHICS_MODE, "FAST"); // the player picks Fast in Video Settings
        assertEquals("FAST", store.get(graphics()));
        options.setFromGame(VanillaOption.RENDER_DISTANCE, 5); // ...then moves a bundled slider
        assertEquals("CUSTOM", options.values().get(VanillaOption.GRAPHICS_MODE), "the game now reports Custom");
        assertEquals("CUSTOM", store.get(graphics()), "VANTA shows the real state, never the Fancy default");
        assertFalse(store.isDefault(graphics()));
    }

    @Test
    void choosingFancyWhileTheGameIsCustomApplies() {
        options.graphicsPresetBundle = true;
        options.setFromGame(VanillaOption.GRAPHICS_MODE, "FANCY");
        options.setFromGame(VanillaOption.CLOUDS, "OFF");
        assertEquals("CUSTOM", store.get(graphics()));
        List<SettingChange> changes = new ArrayList<>();
        store.addListener(changes::add);
        assertTrue(store.set(graphics(), "FANCY"), "used to be a silent no-op: Custom read back as Fancy");
        assertEquals("FANCY", options.values().get(VanillaOption.GRAPHICS_MODE));
        assertEquals("FANCY", options.values().get(VanillaOption.CLOUDS), "the Fancy bundle was applied");
        assertEquals(1, changes.size());
        assertEquals("CUSTOM", changes.get(0).oldValue());
        store.saveIfDirty();
        assertEquals(1, options.saveCount, "options.txt is written once for the player's choice");
    }

    @Test
    void selectingCustomIsRefusedAndStoresNothing() {
        options.graphicsPresetBundle = true;
        options.setFromGame(VanillaOption.GRAPHICS_MODE, "FAST");
        List<SettingChange> changes = new ArrayList<>();
        store.addListener(changes::add);
        assertFalse(store.set(graphics(), "CUSTOM"));
        assertEquals("FAST", store.get(graphics()));
        assertEquals("FAST", options.values().get(VanillaOption.GRAPHICS_MODE));
        assertTrue(changes.isEmpty());
        assertFalse(store.isDirty());
        assertTrue(options.setOrder.isEmpty(), "nothing was written to the game");

        // Without a game (tests, previews) Custom is refused as well instead of being remembered.
        SettingsStore offline = new SettingsStore(VantaSettings.registry(), jsonStore, dir.resolve("other.json"),
                Optional.empty());
        assertFalse(offline.set(graphics(), "CUSTOM"));
        assertEquals("FANCY", offline.get(graphics()));
    }

    @Test
    void aRefusedVanillaWriteIsNotRememberedAsAStaleValue() {
        @SuppressWarnings("unchecked")
        Setting<Integer> renderDistance = (Setting<Integer>) VantaSettings.VIDEO_RENDER_DISTANCE;
        options.refusing.add(VanillaOption.RENDER_DISTANCE);
        List<SettingChange> changes = new ArrayList<>();
        store.addListener(changes::add);
        assertFalse(store.set(renderDistance, 20), "the game refused the value");
        assertEquals(12, store.get(renderDistance), "the game's value is shown");
        assertTrue(changes.isEmpty());
        assertFalse(store.isDirty());
        // Later the game reports nothing for the option (it went away): the refused 20 must not surface.
        options.values().remove(VanillaOption.RENDER_DISTANCE);
        assertEquals(12, store.get(renderDistance), "the default, never the refused value");
    }

    @Test
    void snapshotKeepsCustomAndApplyingItLeavesTheGraphicsPresetAlone() {
        options.graphicsPresetBundle = true;
        options.setFromGame(VanillaOption.GRAPHICS_MODE, "FANCY");
        options.setFromGame(VanillaOption.RENDER_DISTANCE, 9);
        Map<String, JsonElement> snapshot = store.snapshot(true);
        assertEquals("CUSTOM", snapshot.get("video.graphicsMode").getAsString(), "no fake Fancy is captured");

        options.setFromGame(VanillaOption.GRAPHICS_MODE, "FAST");
        store.applySnapshot(snapshot);
        assertFalse(options.setOrder.contains(VanillaOption.GRAPHICS_MODE), "Custom is never written back");
        assertEquals(9, options.getInt(VanillaOption.RENDER_DISTANCE, 0), "the captured render distance applies");
    }

    @Test
    void snapshotsApplyTheGraphicsPresetBeforeTheOptionsItBundles() {
        options.graphicsPresetBundle = true;
        Map<String, JsonElement> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("video.renderDistance", new com.google.gson.JsonPrimitive(5)); // hand-edited / imported order
        snapshot.put("video.graphicsMode", new com.google.gson.JsonPrimitive("fast"));
        store.applySnapshot(snapshot);
        assertEquals(5, options.getInt(VanillaOption.RENDER_DISTANCE, 0), "the bundle does not overwrite it");
        assertEquals(VanillaOption.GRAPHICS_MODE, options.setOrder.get(0));
    }

    @Test
    void readingLoadingSnapshottingAndSavingNeverWriteTheGame() {
        options.graphicsPresetBundle = true;
        options.setFromGame(VanillaOption.GRAPHICS_MODE, "FAST");
        store.load();
        for (Setting<?> setting : VantaSettings.registry().all()) {
            store.get(setting);
            store.isDefault(setting);
        }
        store.snapshot(true);
        store.saveIfDirty();
        store.save();
        assertTrue(options.setOrder.isEmpty(), "no vanilla option was written: " + options.setOrder);
        assertEquals(0, options.saveCount, "options.txt was not saved without a change");
        assertEquals("FAST", options.values().get(VanillaOption.GRAPHICS_MODE));
    }

    @Test
    void registryIsCompleteAndConsistent() {
        SettingsRegistry registry = VantaSettings.registry();
        assertTrue(registry.size() >= 80, "expected a rich settings set, got " + registry.size());
        for (SettingCategory category : SettingCategory.values()) {
            assertFalse(registry.byCategory(category).isEmpty(), category + " has no settings");
        }
        assertEquals(Optional.of(VantaSettings.VIDEO_RENDER_DISTANCE),
                registry.forVanillaOption(VanillaOption.RENDER_DISTANCE));
        assertTrue(registry.find("hud.enabled").isPresent());
        assertTrue(registry.find("nope.nope").isEmpty());
    }
}
