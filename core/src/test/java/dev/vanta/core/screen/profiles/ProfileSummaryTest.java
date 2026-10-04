package dev.vanta.core.screen.profiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.screen.cosmetics.ServicesFixture;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Icons;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileSummaryTest {
    @TempDir
    Path dir;
    private ServicesFixture fx;

    @BeforeEach
    void setUp() {
        fx = new ServicesFixture(dir);
    }

    @Test
    void describesBuiltInAndCustomProfiles() {
        ProfileSummary pvp = ProfileSummary.of(fx.services.profiles().find("pvp").orElseThrow());
        assertTrue(pvp.builtIn());
        assertEquals(Icons.CROSSHAIR, pvp.icon());
        assertEquals(Lang.tr("vanta.profile.builtin.pvp.description"), pvp.description());
        assertEquals(Lang.tr("vanta.hud.preset.pvp"), pvp.hudPreset());
        assertEquals(Lang.tr("vanta.perf.preset.high"), pvp.perfPreset());
        assertEquals(Lang.tr("vanta.crosshair.preset.bold"), pvp.crosshair());

        fx.services.hud().setLayout(HudLayout.EMPTY.with(fx.services.hud().layout().widgets().get(0)));
        fx.services.crosshair().setStyle(fx.services.crosshair().style().withSize(13));
        Profile custom = fx.services.profiles().createFromCurrent("Mine", "unknown-icon");
        ProfileSummary summary = ProfileSummary.of(custom);
        assertFalse(summary.builtIn());
        assertEquals(Icons.PROFILE, summary.icon());
        assertEquals(Lang.tr("vanta.profiles.custom_description"), summary.description());
        assertEquals(Lang.tr("vanta.profiles.hud_custom", 1), summary.hudPreset());
        assertEquals(Lang.tr("vanta.common.custom"), summary.crosshair());
        assertEquals(Icons.CHART, ProfileSummary.iconFor("chart"));
        assertEquals(Icons.PROFILE, ProfileSummary.iconFor(null));
    }

    @Test
    void detectsUnsavedChangesPerArea() {
        assertEquals(List.of(), ProfileSummary.unsavedChanges(fx.services));
        fx.services.settings().set(VantaSettings.HUD_GLOBAL_OPACITY, 0.5);
        assertEquals(List.of(Lang.tr("vanta.profiles.area.settings")), ProfileSummary.unsavedChanges(fx.services));
        fx.services.profiles().updateActiveFromCurrent();
        assertEquals(List.of(), ProfileSummary.unsavedChanges(fx.services));

        fx.services.crosshair().applyPreset(CrosshairPresets.find(CrosshairPresets.DOT).orElseThrow());
        fx.services.hud().setLayout(HudLayout.EMPTY.with(fx.services.hud().layout().widgets().get(0)));
        fx.keys.setKey("key.vanta.zoom", KeyRef.keyboard(90, "key.keyboard.z"));
        fx.services.keybinds().refresh();
        // Activate pvp so its key override (zoom = C) is recorded, then drift the live binding.
        fx.services.profiles().activate("pvp");
        fx.keys.setKey("key.vanta.zoom", KeyRef.keyboard(90, "key.keyboard.z"));
        fx.services.keybinds().refresh();
        fx.services.crosshair().applyPreset(CrosshairPresets.find(CrosshairPresets.DOT).orElseThrow());
        fx.services.hud().setLayout(HudLayout.EMPTY.with(fx.services.hud().layout().widgets().get(0)));
        List<String> changes = ProfileSummary.unsavedChanges(fx.services);
        assertTrue(changes.contains(Lang.tr("vanta.profiles.area.hud")), changes.toString());
        assertTrue(changes.contains(Lang.tr("vanta.profiles.area.crosshair")), changes.toString());
        assertTrue(changes.contains(Lang.tr("vanta.profiles.area.keybinds")), changes.toString());
    }
}
