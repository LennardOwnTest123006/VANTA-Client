package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudPresets;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NexusHudPresetTest {
    @Test
    void everyPresetShowsExactlyItsSetPlusTheCrosshair() {
        for (NexusHudPreset preset : NexusHudPreset.values()) {
            HudLayout result = preset.apply(HudPresets.defaultPreset().layout());
            Set<HudWidgetType> visible = EnumSet.noneOf(HudWidgetType.class);
            for (HudWidgetState widget : result.enabled()) {
                visible.add(widget.type());
            }
            Set<HudWidgetType> expected = EnumSet.copyOf(preset.widgets());
            expected.add(HudWidgetType.CROSSHAIR);
            assertEquals(expected, visible, preset.id());
            for (HudWidgetState widget : result.enabled()) {
                if (widget.type().supportsScale() && preset.widgets().contains(widget.type())) {
                    assertEquals(preset.scale(), widget.scale(), 1e-9, preset.id() + " " + widget.id());
                }
            }
            assertFalse(preset.widgets().contains(HudWidgetType.CROSSHAIR));
        }
        // Every widget except the crosshair and the Vanta Lab frame time graph, which no preset adds.
        assertEquals(HudWidgetType.values().length - 2, NexusHudPreset.FULL.widgets().size());
        assertFalse(NexusHudPreset.FULL.widgets().contains(HudWidgetType.FRAMETIME_GRAPH));
    }

    @Test
    void keepsExistingWidgetsAndPositions() {
        HudLayout start = HudPresets.defaultPreset().layout();
        HudWidgetState fps = start.find("fps").orElseThrow();
        HudLayout moved = start.with(fps.withPosition(dev.vanta.core.hud.HudAnchor.BOTTOM_RIGHT, 10, 20));
        HudLayout result = NexusHudPreset.MINIMAL.apply(moved);
        HudWidgetState after = result.find("fps").orElseThrow();
        assertEquals(dev.vanta.core.hud.HudAnchor.BOTTOM_RIGHT, after.anchor());
        assertEquals(10, after.offsetX());
        assertTrue(after.enabled());
        assertTrue(result.find("armor").isPresent(), "hidden, not removed");
        assertFalse(result.find("armor").orElseThrow().enabled());
        assertEquals(start.size(), result.size());

        HudLayout pvp = NexusHudPreset.PVP.apply(result);
        assertTrue(pvp.byType(HudWidgetType.KEYSTROKES).get(0).enabled(), "missing widgets are added");
        assertTrue(pvp.byType(HudWidgetType.CPS).get(0).enabled());
        assertTrue(pvp.find("armor").orElseThrow().enabled(), "existing hidden widgets are shown again");

        HudLayout fromEmpty = NexusHudPreset.BUILDING.apply(HudLayout.EMPTY);
        assertEquals(5, fromEmpty.size(), "four widgets plus the crosshair");
        assertEquals(1, fromEmpty.byType(HudWidgetType.CROSSHAIR).size());
    }

    @Test
    void idsAndLookups() {
        assertEquals(List.of("minimal", "pvp", "recording", "survival", "building", "full"), NexusHudPreset.ids());
        assertEquals(NexusHudPreset.PVP, NexusHudPreset.fromId(" PvP ").orElseThrow());
        assertTrue(NexusHudPreset.fromId("streamer").isEmpty());
        assertTrue(NexusHudPreset.fromId(null).isEmpty());
        assertEquals("vanta.nexus.hud_preset.recording", NexusHudPreset.RECORDING.langKey());
        assertEquals("vanta.nexus.hud_preset.recording.description", NexusHudPreset.RECORDING.descriptionKey());
        assertEquals(0.9, NexusHudPreset.RECORDING.scale(), 1e-9);
        assertFalse(NexusHudPreset.RECORDING.widgets().contains(HudWidgetType.SERVER), "no server address on stream");
    }
}
