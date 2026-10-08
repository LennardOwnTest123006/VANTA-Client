package dev.vanta.core.profiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.vanta.core.ai.NexusHudPreset;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.perf.FpsLimitPreset;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.cosmetics.ServicesFixture;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The seven built-in profiles: each applies without error and changes what its description claims (HUD layout from
 * the matching Nexus preset, performance preset, frame-rate choice, FOV, opacity), and the content bump adds the two
 * new ones to an older install once.
 */
class BuiltInProfilesTest {
    @TempDir
    Path dir;
    private ServicesFixture fx;
    private VantaServices services;

    @BeforeEach
    void setUp() {
        fx = new ServicesFixture(dir);
        services = fx.services;
    }

    private static Set<HudWidgetType> visible(HudLayout layout) {
        Set<HudWidgetType> out = EnumSet.noneOf(HudWidgetType.class);
        for (HudWidgetState widget : layout.enabled()) {
            out.add(widget.type());
        }
        out.remove(HudWidgetType.CROSSHAIR);
        return out;
    }

    @Test
    void sevenProfilesShipInOrderWithTheirVersions() {
        List<Profile> created = BuiltInProfiles.create(services.settingsRegistry(), 1L);
        assertEquals(BuiltInProfiles.IDS, created.stream().map(Profile::id).toList());
        assertEquals(List.of("default", "pvp", "survival", "building", "recording", "performance", "minimal"),
                BuiltInProfiles.IDS);
        assertEquals(3, BuiltInProfiles.CONTENT_VERSION);
        assertEquals(3, BuiltInProfiles.addedInVersion(BuiltInProfiles.SURVIVAL));
        assertEquals(3, BuiltInProfiles.addedInVersion(BuiltInProfiles.MINIMAL));
        assertEquals(1, BuiltInProfiles.addedInVersion(BuiltInProfiles.PVP));
        for (Profile profile : created) {
            assertFalse(profile.hud().isEmpty(), profile.id() + " carries a HUD layout");
            assertEquals(1, profile.hud().byType(HudWidgetType.CROSSHAIR).size(), profile.id());
        }
    }

    @Test
    void everyProfileActivatesAndChangesWhatItClaims() {
        for (String id : BuiltInProfiles.IDS) {
            assertTrue(services.activateProfile(id), id + " activates");
            assertEquals(id, services.profiles().activeId().orElseThrow());
        }

        services.activateProfile("pvp");
        assertEquals(NexusHudPreset.PVP.widgets(), visible(services.hud().layout()));
        assertEquals(PerformancePreset.HIGH, services.settings().get(VantaSettings.PERFORMANCE_PRESET));
        assertEquals(FpsLimitPreset.UNLIMITED, services.settings().get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
        assertEquals(260, fx.options.getInt(VanillaOption.FRAMERATE_LIMIT, 0));

        services.activateProfile("survival");
        assertEquals(NexusHudPreset.SURVIVAL.widgets(), visible(services.hud().layout()));
        assertTrue(visible(services.hud().layout()).contains(HudWidgetType.BIOME));
        assertTrue(visible(services.hud().layout()).contains(HudWidgetType.ARMOR));
        assertEquals(PerformancePreset.BALANCED, services.settings().get(VantaSettings.PERFORMANCE_PRESET));
        assertEquals(75, fx.options.getInt(VanillaOption.FOV, 0));

        services.activateProfile("building");
        assertEquals(NexusHudPreset.BUILDING.widgets(), visible(services.hud().layout()));
        assertEquals(PerformancePreset.ULTRA, services.settings().get(VantaSettings.PERFORMANCE_PRESET));
        assertEquals(85, fx.options.getInt(VanillaOption.FOV, 0));
        assertEquals(0.8, services.settings().get(VantaSettings.HUD_GLOBAL_OPACITY), 1e-9);
        for (HudWidgetState widget : services.hud().layout().enabled()) {
            if (widget.type().supportsScale()) {
                assertEquals(0.9, widget.scale(), 1e-9, "building widgets are slightly smaller: " + widget.id());
            }
        }

        services.activateProfile("recording");
        assertEquals(NexusHudPreset.RECORDING.widgets(), visible(services.hud().layout()));
        assertFalse(visible(services.hud().layout()).contains(HudWidgetType.SERVER), "no server address on stream");
        assertEquals(0.85, services.settings().get(VantaSettings.HUD_GLOBAL_OPACITY), 1e-9);
        assertEquals(2500, services.settings().get(VantaSettings.GENERAL_NOTIFICATION_DURATION));
        assertFalse(services.settings().get(VantaSettings.MENU_SHOW_VERSION_LABEL));

        services.activateProfile("performance");
        assertEquals(PerformancePreset.BOOST, services.settings().get(VantaSettings.PERFORMANCE_PRESET));
        assertEquals(FpsLimitPreset.UNLIMITED, services.settings().get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
        assertTrue(visible(services.hud().layout()).contains(HudWidgetType.MEMORY));
        assertTrue(visible(services.hud().layout()).contains(HudWidgetType.CPU));

        services.activateProfile("minimal");
        assertEquals(EnumSet.of(HudWidgetType.FPS, HudWidgetType.COORDINATES), visible(services.hud().layout()));
        assertEquals(0.9, services.settings().get(VantaSettings.HUD_GLOBAL_OPACITY), 1e-9);
        assertEquals(PerformancePreset.BALANCED, services.settings().get(VantaSettings.PERFORMANCE_PRESET));

        services.activateProfile("default");
        assertEquals(1.0, services.settings().get(VantaSettings.HUD_GLOBAL_OPACITY), 1e-9);
        assertEquals(PerformancePreset.BALANCED, services.settings().get(VantaSettings.PERFORMANCE_PRESET));
    }

    @Test
    void theNexusPresetAndTheProfileProduceTheSameHud() {
        services.activateProfile("default");
        HudLayout fromPreset = NexusHudPreset.SURVIVAL.apply(services.hud().layout());
        services.activateProfile("survival");
        assertEquals(fromPreset, services.hud().layout(), "hud.preset survival and the Survival profile agree");
    }

    @Test
    void newBuiltInsAreAddedToAnOlderInstallOnceAndDeletedOnesStayDeleted() throws IOException {
        Path profiles = services.paths().profilesDir();
        Path state = services.paths().profilesStateFile();
        services.saveAll();
        JsonStore json = services.jsonStore();
        JsonObject stateJson = json.readObject(state).orElseThrow();
        assertEquals(3, stateJson.get("builtInContent").getAsInt());

        // A 1.3.0 install: five profiles and content version 2.
        Files.delete(profiles.resolve("survival.json"));
        Files.delete(profiles.resolve("minimal.json"));
        stateJson.addProperty("builtInContent", 2);
        json.writeObject(state, stateJson);
        ProfileManager upgraded = reload();
        assertEquals(7, upgraded.size(), "Survival and Minimal are added once");
        assertTrue(upgraded.find("survival").isPresent());
        assertTrue(upgraded.find("minimal").isPresent());
        assertEquals(3, json.readObject(state).orElseThrow().get("builtInContent").getAsInt());

        // The player deletes Minimal on 1.4.0: it is not brought back.
        assertTrue(upgraded.delete("minimal"));
        upgraded.saveAll();
        ProfileManager again = reload();
        assertEquals(6, again.size());
        assertTrue(again.find("minimal").isEmpty());
    }

    private ProfileManager reload() {
        ProfileManager fresh = new ProfileManager(services.jsonStore(), services.paths(), fx.clock, services.settings(),
                services.hud(), services.crosshair(), fx.keys);
        fresh.load();
        return fresh;
    }
}
