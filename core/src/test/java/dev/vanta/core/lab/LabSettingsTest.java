package dev.vanta.core.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingCategory;
import dev.vanta.core.settings.SettingKind;
import dev.vanta.core.settings.SettingsRegistry;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LabSettingsTest {
    @TempDir
    Path dir;

    private SettingsStore settings;
    private LabSettings lab;

    @BeforeEach
    void setUp() {
        settings = new SettingsStore(VantaSettings.registry(), new JsonStore(MutableClock.standard()),
                dir.resolve("settings.json"), Optional.of(new FakeOptionsBridge()));
        settings.load();
        lab = new LabSettings(settings);
    }

    @Test
    void everyFeatureIsABooleanLabSettingThatIsOffByDefault() {
        SettingsRegistry registry = VantaSettings.registry();
        assertEquals(5, LabFeature.values().length);
        for (LabFeature feature : LabFeature.values()) {
            Setting<Boolean> setting = LabSettings.setting(feature);
            assertEquals(feature.settingId(), setting.id());
            assertEquals(SettingCategory.LAB, setting.category());
            assertEquals(SettingKind.BOOL, setting.kind());
            assertEquals(Boolean.FALSE, setting.defaultValue(), feature + " must default to off");
            assertTrue(registry.contains(setting.id()), setting.id() + " is registered");
            assertEquals(Optional.of(feature), LabSettings.featureOf(setting));
            assertFalse(lab.isEnabled(feature));
        }
        assertEquals(Optional.empty(), LabSettings.featureOf(VantaSettings.HUD_ENABLED));
        assertEquals(Set.of(), lab.enabled());
        assertEquals(LabFeature.values().length, lab.snapshot().size());
        assertTrue(lab.snapshot().values().stream().noneMatch(Boolean::booleanValue));
        assertEquals(LabFeature.values().length, registry.byCategory(SettingCategory.LAB).size(),
                "the LAB category holds exactly the lab toggles");
    }

    @Test
    void idsAndLangKeys() {
        assertEquals("dynamic_hud", LabFeature.DYNAMIC_HUD.id());
        assertEquals("lab.dynamicHud", LabFeature.DYNAMIC_HUD.settingId());
        assertEquals("vanta.lab.dynamic_hud.title", LabFeature.DYNAMIC_HUD.langKey());
        assertEquals("vanta.lab.dynamic_hud.description", LabFeature.DYNAMIC_HUD.descriptionKey());
        assertEquals("vanta.lab.screen_transitions.title", LabFeature.SCREEN_TRANSITIONS.langKey());
        for (LabFeature feature : LabFeature.values()) {
            assertTrue(Lang.has(feature.langKey()), feature.langKey());
            assertTrue(Lang.has(feature.descriptionKey()), feature.descriptionKey());
            assertEquals(Lang.tr(LabSettings.setting(feature).titleKey()), Lang.tr(feature.langKey()),
                    "the Lab row and the Settings row say the same thing");
        }
        assertEquals(Optional.of(LabFeature.WAYPOINT_BEAMS), LabFeature.fromId("waypoint_beams"));
        assertEquals(Optional.of(LabFeature.WAYPOINT_BEAMS), LabFeature.fromId(" WAYPOINT_BEAMS "));
        assertEquals(Optional.of(LabFeature.WAYPOINT_BEAMS), LabFeature.fromId("lab.waypointBeams"));
        assertEquals(Optional.empty(), LabFeature.fromId("beams"));
        assertEquals(Optional.empty(), LabFeature.fromId(null));
    }

    @Test
    void setToggleAndSnapshot() {
        assertTrue(lab.set(LabFeature.DYNAMIC_HUD, true));
        assertFalse(lab.set(LabFeature.DYNAMIC_HUD, true), "no change");
        assertTrue(lab.isEnabled(LabFeature.DYNAMIC_HUD));
        assertEquals(Boolean.TRUE, settings.get(VantaSettings.LAB_DYNAMIC_HUD), "the setting is the storage");
        assertTrue(settings.isDirty());

        assertTrue(lab.toggle(LabFeature.FRAMETIME_GRAPH));
        assertFalse(lab.toggle(LabFeature.FRAMETIME_GRAPH));
        assertFalse(lab.isEnabled(LabFeature.FRAMETIME_GRAPH));

        lab.set(LabFeature.SCREEN_TRANSITIONS, true);
        assertEquals(EnumSet.of(LabFeature.DYNAMIC_HUD, LabFeature.SCREEN_TRANSITIONS), lab.enabled());
        Map<LabFeature, Boolean> snapshot = lab.snapshot();
        assertEquals(Boolean.TRUE, snapshot.get(LabFeature.DYNAMIC_HUD));
        assertEquals(Boolean.FALSE, snapshot.get(LabFeature.ANIMATED_CROSSHAIR));
    }

    @Test
    void listenersFireForEveryWayTheValueChanges() {
        List<String> events = new ArrayList<>();
        Runnable one = lab.onChange(LabFeature.ANIMATED_CROSSHAIR, enabled -> events.add("crosshair=" + enabled));
        Runnable any = lab.onAnyChange((feature, enabled) -> events.add(feature.id() + "=" + enabled));

        lab.set(LabFeature.ANIMATED_CROSSHAIR, true);
        assertEquals(List.of("crosshair=true", "animated_crosshair=true"), events);
        events.clear();

        lab.set(LabFeature.DYNAMIC_HUD, true);
        assertEquals(List.of("dynamic_hud=true"), events, "the single-feature listener ignores other features");
        events.clear();

        // changed through the settings store directly (Settings screen, profile, assistant setting.set)
        settings.set(VantaSettings.LAB_ANIMATED_CROSSHAIR, false);
        assertEquals(List.of("crosshair=false", "animated_crosshair=false"), events);
        events.clear();

        settings.set(VantaSettings.HUD_ENABLED, false);
        assertEquals(List.of(), events, "unrelated settings are ignored");

        settings.set(VantaSettings.LAB_ANIMATED_CROSSHAIR, true);
        settings.resetCategory(SettingCategory.LAB);
        assertTrue(events.contains("dynamic_hud=false"));
        assertTrue(events.contains("animated_crosshair=false"));
        events.clear();

        one.run();
        any.run();
        lab.set(LabFeature.ANIMATED_CROSSHAIR, true);
        assertEquals(List.of(), events, "unsubscribed");
    }

    @Test
    void aFailingListenerDoesNotStopTheOthers() {
        List<Boolean> seen = new ArrayList<>();
        lab.onChange(LabFeature.WAYPOINT_BEAMS, enabled -> {
            throw new IllegalStateException("boom");
        });
        lab.onChange(LabFeature.WAYPOINT_BEAMS, seen::add);
        assertTrue(lab.set(LabFeature.WAYPOINT_BEAMS, true));
        assertEquals(List.of(true), seen);
        assertTrue(lab.isEnabled(LabFeature.WAYPOINT_BEAMS), "the value was stored before listeners ran");
    }

    @Test
    void persistsThroughTheSettingsFile() {
        lab.set(LabFeature.FRAMETIME_GRAPH, true);
        settings.save();
        SettingsStore reloaded = new SettingsStore(VantaSettings.registry(), new JsonStore(MutableClock.standard()),
                dir.resolve("settings.json"), Optional.of(new FakeOptionsBridge()));
        reloaded.load();
        LabSettings labAgain = new LabSettings(reloaded);
        assertTrue(labAgain.isEnabled(LabFeature.FRAMETIME_GRAPH));
        assertFalse(labAgain.isEnabled(LabFeature.DYNAMIC_HUD));
        assertSame(LabSettings.setting(LabFeature.FRAMETIME_GRAPH), VantaSettings.LAB_FRAMETIME_GRAPH);
    }
}
