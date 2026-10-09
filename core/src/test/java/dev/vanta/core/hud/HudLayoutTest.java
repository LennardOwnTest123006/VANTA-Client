package dev.vanta.core.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HudLayoutTest {
    private static final int W = 427;
    private static final int H = 240;

    @Test
    void anchorMathForEveryAnchor() {
        HudLayout layout = HudLayout.EMPTY;
        HudWidgetState tl = HudWidgetState.defaults("a", HudWidgetType.FPS, HudAnchor.TOP_LEFT, 4, 6);
        assertEquals(new HudRect(4, 6, 44, 14), layout.resolveRect(tl, W, H));
        HudWidgetState tr = HudWidgetState.defaults("b", HudWidgetType.FPS, HudAnchor.TOP_RIGHT, 4, 6);
        assertEquals(new HudRect(W - 44 - 4, 6, 44, 14), layout.resolveRect(tr, W, H));
        HudWidgetState br = HudWidgetState.defaults("c", HudWidgetType.FPS, HudAnchor.BOTTOM_RIGHT, 4, 6);
        assertEquals(new HudRect(W - 48, H - 20, 44, 14), layout.resolveRect(br, W, H));
        HudWidgetState center = HudWidgetState.defaults("d", HudWidgetType.FPS, HudAnchor.CENTER, 10, -10);
        assertEquals(new HudRect((W - 44) / 2 + 10, (H - 14) / 2 - 10, 44, 14), layout.resolveRect(center, W, H));
        HudWidgetState tc = HudWidgetState.defaults("e", HudWidgetType.FPS, HudAnchor.TOP_CENTER, 0, 2);
        assertEquals(new HudRect((W - 44) / 2, 2, 44, 14), layout.resolveRect(tc, W, H));
        HudWidgetState ml = HudWidgetState.defaults("f", HudWidgetType.FPS, HudAnchor.MIDDLE_LEFT, 2, 0);
        assertEquals(new HudRect(2, (H - 14) / 2, 44, 14), layout.resolveRect(ml, W, H));
    }

    @Test
    void offsetsRoundTripThroughAnchors() {
        for (HudAnchor anchor : HudAnchor.values()) {
            int x = 123;
            int y = 77;
            int ox = anchor.offsetXFor(W, 44, x);
            int oy = anchor.offsetYFor(H, 14, y);
            assertEquals(x, anchor.resolveX(W, 44, ox), anchor.toString());
            assertEquals(y, anchor.resolveY(H, 14, oy), anchor.toString());
        }
    }

    @Test
    void nearestAnchorUsesScreenThirds() {
        assertEquals(HudAnchor.TOP_LEFT, HudAnchor.nearest(new HudRect(0, 0, 10, 10), W, H));
        assertEquals(HudAnchor.BOTTOM_RIGHT, HudAnchor.nearest(new HudRect(W - 10, H - 10, 10, 10), W, H));
        assertEquals(HudAnchor.CENTER, HudAnchor.nearest(new HudRect(W / 2 - 5, H / 2 - 5, 10, 10), W, H));
        assertEquals(HudAnchor.TOP_CENTER, HudAnchor.nearest(new HudRect(W / 2 - 5, 0, 10, 10), W, H));
    }

    @Test
    void scalingAndClamping() {
        HudWidgetState big = HudWidgetState.defaults("a", HudWidgetType.FPS, HudAnchor.TOP_LEFT, 400, 230).withScale(2.0);
        HudRect rect = HudLayout.EMPTY.resolveRect(big, W, H);
        assertEquals(88, rect.width());
        assertEquals(28, rect.height());
        assertEquals(W - 88, rect.x(), "clamped to the right edge");
        assertEquals(H - 28, rect.y(), "clamped to the bottom edge");
        HudRect scaled = HudLayout.EMPTY.resolveRect(big, W, H, 0.5);
        assertEquals(44, scaled.width());
        HudWidgetState negative = HudWidgetState.defaults("b", HudWidgetType.FPS, HudAnchor.TOP_LEFT, -50, -50);
        assertEquals(new HudRect(0, 0, 44, 14), HudLayout.EMPTY.resolveRect(negative, W, H));
    }

    @Test
    void crosshairIsAlwaysCentered() {
        HudWidgetState crosshair = HudWidgetState.defaults("x", HudWidgetType.CROSSHAIR, HudAnchor.TOP_LEFT, 99, 99);
        assertEquals(HudAnchor.CENTER, crosshair.anchor());
        assertEquals(0, crosshair.offsetX());
        assertEquals(new HudRect((W - 16) / 2, (H - 16) / 2, 16, 16), HudLayout.EMPTY.resolveRect(crosshair, W, H));
        assertEquals(crosshair, HudLayout.placed(crosshair, 0, 0, W, H));
    }

    @Test
    void placedAndReanchoredKeepScreenPosition() {
        HudWidgetState widget = HudWidgetState.defaults("a", HudWidgetType.FPS, HudAnchor.TOP_LEFT, 4, 4);
        HudWidgetState moved = HudLayout.placed(widget, 380, 220, W, H);
        assertEquals(new HudRect(380, 220, 44, 14), HudLayout.EMPTY.resolveRect(moved, W, H));
        assertEquals(HudAnchor.TOP_LEFT, moved.anchor());
        HudWidgetState reanchored = HudLayout.reanchored(moved, W, H);
        assertEquals(HudAnchor.BOTTOM_RIGHT, reanchored.anchor());
        assertEquals(new HudRect(380, 220, 44, 14), HudLayout.EMPTY.resolveRect(reanchored, W, H));
        assertEquals(new HudRect(1920 - 44 - 3, 1080 - 14 - 6, 44, 14),
                HudLayout.EMPTY.resolveRect(reanchored, 1920, 1080), "sticks to the corner at other resolutions");
    }

    @Test
    void layoutOperationsAndIds() {
        HudLayout layout = new HudLayout(List.of(HudWidgetState.defaults("fps", HudWidgetType.FPS)));
        assertEquals("fps-2", layout.nextId(HudWidgetType.FPS));
        assertEquals("ping", layout.nextId(HudWidgetType.PING));
        HudLayout two = layout.with(HudWidgetState.defaults("ping", HudWidgetType.PING));
        assertEquals(2, two.size());
        assertEquals("ping", two.bringToFront("fps").widgets().get(0).id());
        assertEquals(1, two.without("ping").size());
        assertEquals(1, two.byType(HudWidgetType.FPS).size());
        assertEquals(1, two.with(two.find("fps").orElseThrow().withEnabled(false)).enabled().size());
        assertThrows(IllegalArgumentException.class, () -> new HudLayout(List.of(
                HudWidgetState.defaults("fps", HudWidgetType.FPS), HudWidgetState.defaults("fps", HudWidgetType.PING))));
        Map<String, HudRect> rects = two.resolveAll(W, H, 1.0);
        assertEquals(2, rects.size());
    }

    @Test
    void widgetStateValidatesProps() {
        HudWidgetState clock = HudWidgetState.defaults("clock", HudWidgetType.CLOCK);
        assertEquals("system_12h", clock.prop("format"));
        assertEquals("game_time", clock.withProp("format", "GAME_TIME").prop("format"));
        assertEquals("system_12h", clock.withProp("format", "nonsense").prop("format"));
        // A layout saved before 1.4.1 with the 24-hour clock reads as the AM/PM system clock.
        assertEquals("system_12h", clock.withProp("format", "system_24h").prop("format"));
        assertEquals("true", clock.withProp("showSeconds", "true").prop("showSeconds"));
        assertTrue(clock.withProp("showSeconds", "true").propBool("showSeconds"));
        HudWidgetState coords = HudWidgetState.defaults("coords", HudWidgetType.COORDINATES);
        assertEquals(2, coords.withProp("decimals", "9").propInt("decimals"), "clamped to schema max");
        assertEquals(0.5, clock.withScale(0.1).scale());
        assertEquals(HudWidgetState.MIN_SIZE, clock.withSize(1, 1).width());
        assertEquals(clock, clock.withScale(1.7).withOpacity(0.2).resetVisuals());
    }

    @Test
    void codecRoundTripAndTolerance() {
        HudLayout layout = HudPresets.defaultPreset().layout();
        JsonObject json = HudLayoutCodec.write(layout, new JsonObject());
        assertEquals(layout, HudLayoutCodec.read(json));
        JsonObject bad = JsonParser.parseString("""
                {"widgets": [
                  {"id": "fps", "type": "fps"},
                  {"id": "fps", "type": "fps"},
                  {"type": "does_not_exist"},
                  {"type": "ping", "id": "ping", "anchor": "weird", "scale": "x", "textColor": "#FFFFFF", "props": {"showLabel": "false"}},
                  42
                ]}""").getAsJsonObject();
        HudLayout read = HudLayoutCodec.read(bad);
        assertEquals(2, read.size());
        HudWidgetState ping = read.find("ping").orElseThrow();
        assertEquals(HudAnchor.TOP_LEFT, ping.anchor());
        assertEquals(0xFFFFFFFF, ping.textColor());
        assertEquals("false", ping.prop("showLabel"));
        assertEquals(HudLayout.EMPTY, HudLayoutCodec.read(new JsonObject()));
    }

    @Test
    void builtInPresetsLoadAndContainCrosshair() {
        List<HudPreset> presets = HudPresets.builtIns();
        assertEquals(HudPresets.IDS, presets.stream().map(HudPreset::id).toList());
        for (HudPreset preset : presets) {
            assertTrue(preset.builtIn());
            assertTrue(preset.layout().size() >= 3, preset.id());
            assertEquals(1, preset.layout().byType(HudWidgetType.CROSSHAIR).size(), preset.id());
            Map<String, HudRect> rects = preset.layout().resolveAll(W, H, 1.0);
            for (Map.Entry<String, HudRect> e : rects.entrySet()) {
                assertTrue(e.getValue().x() >= 0 && e.getValue().right() <= W, preset.id() + "/" + e.getKey());
                assertTrue(e.getValue().y() >= 0 && e.getValue().bottom() <= H, preset.id() + "/" + e.getKey());
            }
        }
        assertEquals(10, HudPresets.defaultPreset().layout().size());
        assertTrue(HudPresets.find("pvp").orElseThrow().layout().byType(HudWidgetType.KEYSTROKES).size() == 1);
    }

    @Test
    void placedClearStacksAwayFromOccupiedCorners() {
        int w = 960;
        int h = 540;
        HudWidgetState fps = HudWidgetState.defaults("fps", HudWidgetType.FPS);
        HudWidgetState durability = HudWidgetState.defaults("item_durability", HudWidgetType.ITEM_DURABILITY);
        HudLayout layout = HudLayout.EMPTY.with(fps).with(durability);

        // Top-left: the memory widget would cover the FPS widget, so it moves below it.
        HudWidgetState memory = layout.placedClear(HudWidgetState.defaults("memory", HudWidgetType.MEMORY), w, h);
        HudRect fpsRect = layout.resolveRect(fps, w, h);
        HudRect memoryRect = layout.resolveRect(memory, w, h);
        assertTrue(!memoryRect.intersects(fpsRect));
        assertTrue(memoryRect.y() >= fpsRect.bottom(), "stacked below the blocker");
        assertEquals(fpsRect.x(), memoryRect.x(), "same column");

        // Bottom-right: keystrokes stack upwards, above the item durability widget.
        HudWidgetState keys = layout.placedClear(HudWidgetState.defaults("keystrokes", HudWidgetType.KEYSTROKES), w, h);
        HudRect durabilityRect = layout.resolveRect(durability, w, h);
        HudRect keysRect = layout.resolveRect(keys, w, h);
        assertTrue(!keysRect.intersects(durabilityRect));
        assertTrue(keysRect.bottom() <= durabilityRect.y(), "stacked above the blocker");

        // A free spot is left alone; disabled widgets and the widget itself do not block.
        HudWidgetState clock = HudWidgetState.defaults("clock", HudWidgetType.CLOCK);
        assertEquals(clock, layout.placedClear(clock, w, h));
        HudLayout withDisabled = layout.with(fps.withEnabled(false));
        HudWidgetState memory2 = HudWidgetState.defaults("memory", HudWidgetType.MEMORY);
        assertEquals(memory2, withDisabled.placedClear(memory2, w, h));
        assertEquals(fps, layout.placedClear(fps, w, h));

        // No room at all (tiny screen): the widget comes back unchanged instead of being pushed off-screen.
        HudWidgetState cramped = HudWidgetState.defaults("memory", HudWidgetType.MEMORY);
        assertEquals(cramped, layout.placedClear(cramped, 100, 20));
    }

    @Test
    void rectHelpers() {
        HudRect r = new HudRect(10, 10, 20, 10);
        assertTrue(r.contains(10, 10));
        assertTrue(!r.contains(30, 10));
        assertTrue(r.intersects(new HudRect(25, 15, 10, 10)));
        assertTrue(!r.intersects(new HudRect(30, 10, 5, 5)));
        assertEquals(new HudRect(8, 8, 24, 14), r.grow(2));
        assertEquals(new HudRect(0, 0, 20, 10), new HudRect(-5, -5, 20, 10).clampTo(100, 100));
        assertEquals(new HudRect(0, 0, 50, 50), new HudRect(10, 10, 200, 200).clampTo(50, 50));
    }
}
