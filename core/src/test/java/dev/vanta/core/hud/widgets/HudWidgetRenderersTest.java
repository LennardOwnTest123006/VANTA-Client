package dev.vanta.core.hud.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.ItemInfo;
import dev.vanta.core.cosmetics.HudTheme;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.hud.render.HudWidgetRenderer;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TestCanvas;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class HudWidgetRenderersTest {
    private static final HudWidgetRenderers RENDERERS = HudWidgetRenderers.standard();
    private static final HudPaint PAINT = HudPaint.defaults();

    private static HudWidgetState state(HudWidgetType type, Map<String, String> props) {
        HudWidgetState base = HudWidgetState.defaults(type.id(), type);
        for (Map.Entry<String, String> e : props.entrySet()) {
            base = base.withProp(e.getKey(), e.getValue());
        }
        Size size = RENDERERS.forType(type).preferredSize(TestCanvas.metrics(), base, HudData.sample(), PAINT);
        return base.withSize(size.w(), size.h());
    }

    private static TestCanvas render(HudWidgetType type, Map<String, String> props) {
        return render(type, props, HudData.sample());
    }

    private static TestCanvas render(HudWidgetType type, Map<String, String> props, HudData data) {
        TestCanvas canvas = new TestCanvas(200, 200);
        RENDERERS.forType(type).render(canvas, state(type, props), data, PAINT);
        return canvas;
    }

    @Test
    void everyTypeHasExactlyOneRenderer() {
        for (HudWidgetType type : HudWidgetType.values()) {
            assertTrue(RENDERERS.supports(type), type.name());
            assertEquals(type, RENDERERS.forType(type).type());
        }
        assertEquals(HudWidgetType.values().length, RENDERERS.all().size());
    }

    @ParameterizedTest
    @EnumSource(HudWidgetType.class)
    void preferredSizesAreSaneAndRenderingStaysInsideTheWidget(HudWidgetType type) {
        HudWidgetState s = state(type, Map.of());
        Size size = RENDERERS.forType(type).preferredSize(TestCanvas.metrics(), s, HudData.sample(), PAINT);
        assertTrue(size.w() >= HudWidgetState.MIN_SIZE && size.h() >= HudWidgetState.MIN_SIZE, type + " " + size);
        assertTrue(size.w() <= 220 && size.h() <= 140, type + " is unreasonably large: " + size);
        TestCanvas canvas = render(type, Map.of());
        assertFalse(canvas.ops().isEmpty(), type + " drew nothing");
        for (TestCanvas.Fill fill : canvas.fills()) {
            assertTrue(fill.x() >= -1 && fill.y() >= -1 && fill.right() <= s.width() + 1 && fill.bottom() <= s.height() + 1,
                    type + " fill outside the widget: " + fill);
        }
        TestCanvas tiny = new TestCanvas(40, 40);
        RENDERERS.forType(type).render(tiny, s.withSize(HudWidgetState.MIN_SIZE, HudWidgetState.MIN_SIZE),
                HudData.sample(), PAINT);
    }

    @Test
    void fpsShowsValueLabelFrameTimeAndOnePercentLow() {
        TestCanvas canvas = render(HudWidgetType.FPS, Map.of());
        assertTrue(canvas.hasText("144"));
        assertTrue(canvas.hasText("FPS"));
        assertFalse(canvas.hasTextContaining("ms"));
        TestCanvas detailed = render(HudWidgetType.FPS, Map.of("showFrameTime", "true", "showLabel", "false"));
        assertTrue(detailed.hasText("6.9 ms"));
        assertFalse(detailed.hasText("FPS"));
        HudWidgetState tall = state(HudWidgetType.FPS, Map.of()).withSize(60, 30);
        TestCanvas twoLines = new TestCanvas();
        RENDERERS.forType(HudWidgetType.FPS).render(twoLines, tall, HudData.sample(), PAINT);
        assertTrue(twoLines.hasText("1% low 97"));
    }

    @Test
    void pingIsColourCodedAndKnowsSingleplayer() {
        TestCanvas canvas = render(HudWidgetType.PING, Map.of());
        assertTrue(canvas.hasText("32 ms"));
        assertEquals(PAINT.success(), textColor(canvas, "32 ms"));
        FakeGameBridge game = new FakeGameBridge().onServer("mc.example.invalid", "Example", 190);
        TestCanvas slow = render(HudWidgetType.PING, Map.of(), HudData.capture(game, 0, 0, null, ZoneOffset.UTC));
        assertEquals(PAINT.danger(), textColor(slow, "190 ms"));
        TestCanvas local = render(HudWidgetType.PING, Map.of(), HudData.capture(new FakeGameBridge(), 0, 0, null, null));
        assertTrue(local.hasText("Local"));
        assertEquals(PAINT.success(), PingWidget.colorFor(80, PAINT));
        assertEquals(PAINT.warning(), PingWidget.colorFor(81, PAINT));
        assertEquals(PAINT.danger(), PingWidget.colorFor(151, PAINT));
    }

    @Test
    void coordinatesSupportDecimalsLinesAndDimension() {
        TestCanvas canvas = render(HudWidgetType.COORDINATES, Map.of());
        assertTrue(canvas.hasText("128"));
        assertTrue(canvas.hasText("64"));
        assertTrue(canvas.hasText("-221"));
        assertFalse(canvas.hasText("Overworld"));
        TestCanvas detailed = render(HudWidgetType.COORDINATES,
                Map.of("decimals", "1", "singleLine", "false", "showDimension", "true"));
        assertTrue(detailed.hasText("128.5"));
        assertTrue(detailed.hasText("-220.3"));
        assertTrue(detailed.hasText("Overworld"));
        List<Integer> ys = new ArrayList<>();
        for (TestCanvas.Text t : detailed.texts()) {
            if (t.text().equals("X") || t.text().equals("Y") || t.text().equals("Z")) {
                ys.add(t.y());
            }
        }
        assertEquals(3, ys.size());
        assertTrue(ys.get(0) < ys.get(1) && ys.get(1) < ys.get(2), "one axis per line");
    }

    @Test
    void directionShowsCardinalYawAxisAndCompassStrip() {
        TestCanvas canvas = render(HudWidgetType.DIRECTION, Map.of());
        assertTrue(canvas.hasText("North"));
        assertTrue(canvas.hasText("-180°"));
        TestCanvas axis = render(HudWidgetType.DIRECTION, Map.of("showAxis", "true", "showYaw", "false"));
        assertFalse(axis.hasText("-180°"));
        assertTrue(axis.hasTextContaining("Z"));
        DirectionWidget widget = (DirectionWidget) RENDERERS.forType(HudWidgetType.DIRECTION);
        HudWidgetState tall = state(HudWidgetType.DIRECTION, Map.of()).withSize(80, 36);
        assertTrue(widget.hasStrip(tall, HudData.sample(), PAINT));
        TestCanvas strip = new TestCanvas();
        widget.render(strip, tall, HudData.sample(), PAINT);
        assertTrue(strip.hasText("N"));
        assertTrue(strip.hasText("S"));
    }

    @Test
    void biomeServerCpsClockAndVersionText() {
        assertTrue(render(HudWidgetType.BIOME, Map.of()).hasText("Cherry Grove"));
        assertTrue(render(HudWidgetType.BIOME, Map.of("showNamespace", "true")).hasTextContaining("minecraft"));

        TestCanvas server = render(HudWidgetType.SERVER, Map.of());
        assertTrue(server.hasText("Sample Server"));
        assertTrue(server.hasText("play.sample.invalid"));
        TestCanvas hidden = render(HudWidgetType.SERVER, Map.of("showAddress", "false"));
        assertTrue(hidden.hasText("Sample Server"));
        assertFalse(hidden.hasTextContaining("invalid"), "streamer mode hides the address");
        TestCanvas single = render(HudWidgetType.SERVER, Map.of(), HudData.capture(new FakeGameBridge(), 0, 0, null, null));
        assertTrue(single.hasText("Singleplayer"));

        assertTrue(render(HudWidgetType.CPS, Map.of()).hasText("6 | 3"));
        assertTrue(render(HudWidgetType.CPS, Map.of("showRight", "false")).hasText("6"));

        assertTrue(render(HudWidgetType.CLOCK, Map.of()).hasText("14:32"));
        assertTrue(render(HudWidgetType.CLOCK, Map.of("showSeconds", "true")).hasText("14:32:07"));
        assertTrue(render(HudWidgetType.CLOCK, Map.of("format", "system_12h")).hasText("2:32 PM"));
        assertTrue(render(HudWidgetType.CLOCK, Map.of("format", "game_time")).hasText("12:00"));

        assertTrue(render(HudWidgetType.MINECRAFT_VERSION, Map.of()).hasText("Minecraft 1.21.11"));
        assertTrue(render(HudWidgetType.MINECRAFT_VERSION, Map.of("showFabric", "true"))
                .hasText("Minecraft 1.21.11 · Fabric 0.19.5"));
        assertTrue(render(HudWidgetType.ENTITY_COUNT, Map.of()).hasText("87"));
    }

    @Test
    void armorListsPiecesWithDurabilityBarsAndHandlesEmptySlots() {
        TestCanvas canvas = render(HudWidgetType.ARMOR, Map.of());
        assertTrue(canvas.hasText("20"));
        assertTrue(canvas.hasText("Netherite Helmet"));
        assertTrue(canvas.hasText("93 %"));
        assertTrue(canvas.fills().stream().anyMatch(f -> f.argb() == PAINT.success()), "healthy bar is green");
        assertTrue(canvas.fills().stream().anyMatch(f -> f.argb() == PAINT.warning()), "leggings at 34 % are amber");

        FakeGameBridge bare = new FakeGameBridge();
        bare.armorValue = 0;
        bare.armorPieces = new ArrayList<>(List.of(ItemInfo.EMPTY, ItemInfo.EMPTY, ItemInfo.EMPTY, ItemInfo.EMPTY));
        TestCanvas none = render(HudWidgetType.ARMOR, Map.of(), HudData.capture(bare, 0, 0, null, null));
        assertTrue(none.hasText("No armor"));
        TestCanvas horizontal = render(HudWidgetType.ARMOR, Map.of("layout", "horizontal"));
        assertTrue(horizontal.hasText("20"));
        assertFalse(horizontal.hasText("Netherite Helmet"));
    }

    @Test
    void itemDurabilityShowsHeldItemsPercentAndEmptyHand() {
        TestCanvas canvas = render(HudWidgetType.ITEM_DURABILITY, Map.of());
        assertTrue(canvas.hasText("Diamond Pickaxe"));
        assertTrue(canvas.hasText("1203"));
        assertTrue(canvas.hasText("Shield"));
        TestCanvas percent = render(HudWidgetType.ITEM_DURABILITY, Map.of("showPercent", "true", "showOffhand", "false"));
        assertTrue(percent.hasText("77 %"));
        assertFalse(percent.hasText("Shield"));
        FakeGameBridge empty = new FakeGameBridge();
        empty.heldItems = new ArrayList<>(List.of(ItemInfo.EMPTY, ItemInfo.EMPTY));
        TestCanvas hands = render(HudWidgetType.ITEM_DURABILITY, Map.of(), HudData.capture(empty, 0, 0, null, null));
        assertTrue(hands.hasText("Empty"));
    }

    @Test
    void potionEffectsListNameLevelTimeAndInfinity() {
        TestCanvas canvas = render(HudWidgetType.POTION_EFFECTS, Map.of());
        assertTrue(canvas.hasText("Speed II"));
        assertTrue(canvas.hasText("1:30"));
        assertTrue(canvas.hasText("Night Vision"));
        assertTrue(canvas.hasText("∞"));
        assertTrue(canvas.hasText("Regeneration"));
        assertTrue(canvas.hasText("0:45"));
        assertTrue(canvas.fills().stream().anyMatch(f -> f.argb() == 0xFF33EBFF), "effect colour dot");
        TestCanvas compact = render(HudWidgetType.POTION_EFFECTS, Map.of("compact", "true", "showDuration", "false"));
        assertFalse(compact.hasText("1:30"));
        assertFalse(compact.fills().stream().anyMatch(f -> f.argb() == 0xFF33EBFF));
        FakeGameBridge none = new FakeGameBridge();
        none.effects = new ArrayList<>();
        assertTrue(render(HudWidgetType.POTION_EFFECTS, Map.of(), HudData.capture(none, 0, 0, null, null))
                .hasText("No effects"));
    }

    @Test
    void keystrokesDrawEveryKeyAndFillPressedOnes() {
        TestCanvas canvas = render(HudWidgetType.KEYSTROKES, Map.of());
        for (String key : List.of("W", "A", "S", "D", "LMB", "RMB", "———", "Shift")) {
            assertTrue(canvas.hasText(key), key);
        }
        assertEquals(0xFFFFFFFF, textColor(canvas, "W"), "pressed W is white on the accent");
        assertEquals(HudWidgetState.DEFAULT_TEXT, textColor(canvas, "A"));
        long accentFills = canvas.fills().stream().filter(f -> f.argb() == HudWidgetState.DEFAULT_ACCENT).count();
        assertTrue(accentFills >= 2, "W and LMB are pressed in the sample: " + accentFills);
        TestCanvas idle = render(HudWidgetType.KEYSTROKES, Map.of(),
                HudData.capture(new FakeGameBridge(), 0, 0, null, null));
        assertEquals(0, idle.fills().stream().filter(f -> f.argb() == HudWidgetState.DEFAULT_ACCENT).count(),
                "nothing pressed, nothing filled with the accent");
        TestCanvas minimal = render(HudWidgetType.KEYSTROKES, Map.of("showMouse", "false", "showSpace", "false"));
        assertFalse(minimal.hasText("LMB"));
        assertFalse(minimal.hasText("———"));
        TestCanvas cps = render(HudWidgetType.KEYSTROKES, Map.of("showCps", "true"));
        assertTrue(cps.hasText("6"));
        assertTrue(cps.hasText("3"));
    }

    @Test
    void memoryAndCpuFormats() {
        TestCanvas memory = render(HudWidgetType.MEMORY, Map.of());
        assertTrue(memory.hasText("75 %"));
        assertTrue(memory.hasText("1536 / 2048 MB"));
        TestCanvas max = render(HudWidgetType.MEMORY, Map.of("showPercent", "false", "showMax", "true"));
        assertTrue(max.hasText("1536 / 4096 MB"));
        TestCanvas cpu = render(HudWidgetType.CPU, Map.of());
        assertTrue(cpu.hasText("18 %"));
        FakeGameBridge noCpu = new FakeGameBridge();
        noCpu.cpuLoad = java.util.OptionalDouble.empty();
        TestCanvas unavailable = render(HudWidgetType.CPU, Map.of(), HudData.capture(noCpu, 0, 0, null, null));
        assertTrue(unavailable.hasText("n/a"));
    }

    @Test
    void themesChangePanelAndCrosshairWidgetDrawsNoPanel() {
        HudWidgetState fps = state(HudWidgetType.FPS, Map.of());
        HudWidgetRenderer renderer = RENDERERS.forType(HudWidgetType.FPS);
        TestCanvas minimal = new TestCanvas();
        renderer.render(minimal, fps, HudData.sample(), PAINT.withTheme(HudTheme.MINIMAL));
        assertTrue(minimal.fills().isEmpty(), "MINIMAL draws text only");
        TestCanvas outline = new TestCanvas();
        renderer.render(outline, fps, HudData.sample(), PAINT.withTheme(HudTheme.OUTLINE));
        assertTrue(outline.fills().stream().anyMatch(f -> (f.argb() & 0x00FFFFFF) == (HudWidgetState.DEFAULT_ACCENT & 0x00FFFFFF)),
                "OUTLINE strokes with the accent colour");
        TestCanvas crosshair = render(HudWidgetType.CROSSHAIR, Map.of());
        assertTrue(crosshair.texts().isEmpty());
        assertFalse(crosshair.fills().isEmpty());
        assertFalse(PAINT.withTextShadow(false).textShadow());
        assertTrue(PAINT.textShadow());
        assertEquals(0, HudPaint.of(HudTheme.MINIMAL, true, dev.vanta.core.accessibility.AccessibilityState.DEFAULT,
                dev.vanta.core.crosshair.CrosshairStyle.DEFAULT).panelColor(fps) >>> 24);
    }

    @Test
    void lookupsValidateTheirArgument() {
        assertThrows(NullPointerException.class, () -> RENDERERS.forType(null));
    }

    private static int textColor(TestCanvas canvas, String text) {
        for (TestCanvas.Text t : canvas.texts()) {
            if (t.text().equals(text)) {
                return t.argb();
            }
        }
        throw new AssertionError("text not drawn: " + text);
    }
}
