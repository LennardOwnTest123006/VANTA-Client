package dev.vanta.core.crosshair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.crosshair.CrosshairGeometry.Primitive;
import dev.vanta.core.crosshair.CrosshairGeometry.Rect;
import dev.vanta.core.crosshair.CrosshairGeometry.Ring;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrosshairGeometryTest {
    private static final int CX = 100;
    private static final int CY = 60;

    private static List<Primitive> fills(List<Primitive> all, int color) {
        return all.stream().filter(p -> p.argb() == color).toList();
    }

    @Test
    void crossHasFourArmsAroundTheGap() {
        CrosshairStyle style = CrosshairStyle.DEFAULT.withOutline(false, 1);
        List<Primitive> prims = CrosshairGeometry.build(style, CX, CY);
        assertEquals(4, prims.size());
        assertTrue(prims.contains(new Rect(CX, CY - 1 - 5, 1, 5, 0xFFFFFFFF)), "top arm");
        assertTrue(prims.contains(new Rect(CX, CY + 1 + 1, 1, 5, 0xFFFFFFFF)), "bottom arm");
        assertTrue(prims.contains(new Rect(CX - 1 - 5, CY, 5, 1, 0xFFFFFFFF)), "left arm");
        assertTrue(prims.contains(new Rect(CX + 1 + 1, CY, 5, 1, 0xFFFFFFFF)), "right arm");
        for (Primitive p : prims) {
            assertFalse(((Rect) p).x() <= CX && CX < ((Rect) p).x() + ((Rect) p).width()
                    && ((Rect) p).y() <= CY && CY < ((Rect) p).y() + ((Rect) p).height(), "centre pixel stays empty");
        }
        Rect bounds = CrosshairGeometry.bounds(prims);
        assertEquals(13, bounds.width());
        assertEquals(13, bounds.height());
    }

    @Test
    void outlineIsDrawnFirstAndLarger() {
        List<Primitive> prims = CrosshairGeometry.build(CrosshairStyle.DEFAULT, CX, CY);
        assertEquals(8, prims.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(0xFF000000, prims.get(i).argb(), "outline primitives come first");
        }
        Rect topOutline = (Rect) prims.get(0);
        Rect topFill = (Rect) prims.get(4);
        assertEquals(topFill.x() - 1, topOutline.x());
        assertEquals(topFill.width() + 2, topOutline.width());
    }

    @Test
    void dotIsCenteredSquare() {
        CrosshairStyle dot = CrosshairStyle.DEFAULT.withShape(CrosshairShape.DOT).withSize(3).withOutline(false, 1);
        List<Primitive> prims = CrosshairGeometry.build(dot, CX, CY);
        assertEquals(List.of(new Rect(CX - 1, CY - 1, 3, 3, 0xFFFFFFFF)), prims);
    }

    @Test
    void circleIsRingWithOutlineRing() {
        CrosshairStyle circle = CrosshairStyle.DEFAULT.withShape(CrosshairShape.CIRCLE).withSize(5).withGap(1);
        List<Primitive> prims = CrosshairGeometry.build(circle, CX, CY);
        assertEquals(2, prims.size());
        assertEquals(new Ring(CX, CY, 7, 3, 0xFF000000), prims.get(0));
        assertEquals(new Ring(CX, CY, 6, 1, 0xFFFFFFFF), prims.get(1));
    }

    @Test
    void squareHasFourSidesNotOverlapping() {
        CrosshairStyle square = CrosshairStyle.DEFAULT.withShape(CrosshairShape.SQUARE).withSize(4).withGap(0)
                .withThickness(1).withOutline(false, 1);
        List<Primitive> prims = CrosshairGeometry.build(square, CX, CY);
        assertEquals(4, prims.size());
        Rect bounds = CrosshairGeometry.bounds(prims);
        assertEquals(new Rect(CX - 4, CY - 4, 8, 8, 0), bounds);
        int area = prims.stream().mapToInt(p -> ((Rect) p).width() * ((Rect) p).height()).sum();
        assertEquals(8 * 8 - 6 * 6, area, "exactly the 1px frame, no double-drawn corners");
    }

    @Test
    void chevronIsSymmetricAndBelowCenter() {
        CrosshairStyle chevron = CrosshairStyle.DEFAULT.withShape(CrosshairShape.CHEVRON).withSize(4).withGap(2)
                .withOutline(false, 1);
        List<Primitive> prims = CrosshairGeometry.build(chevron, CX, CY);
        assertEquals(8, prims.size());
        for (Primitive p : prims) {
            Rect r = (Rect) p;
            assertTrue(r.y() >= CY + 2, "all below the centre");
            int mirrored = 2 * CX - r.x();
            assertTrue(prims.stream().anyMatch(q -> ((Rect) q).x() == mirrored && ((Rect) q).y() == r.y()),
                    "mirror of " + r);
        }
    }

    @Test
    void plusDotAddsCenterAndDynamicExpandsGap() {
        CrosshairStyle plus = CrosshairStyle.DEFAULT.withShape(CrosshairShape.PLUS_DOT).withOutline(false, 1);
        List<Primitive> still = CrosshairGeometry.build(plus, CX, CY);
        assertEquals(5, still.size());
        assertTrue(still.contains(new Rect(CX, CY, 1, 1, 0xFFFFFFFF)));
        assertEquals(still, CrosshairGeometry.build(plus, CX, CY, 4), "expansion ignored when not dynamic");
        List<Primitive> expanded = CrosshairGeometry.build(plus.withDynamic(true), CX, CY, 4);
        Rect bounds = CrosshairGeometry.bounds(expanded);
        assertEquals(CrosshairGeometry.bounds(still).width() + 8, bounds.width());
    }

    @Test
    void opacityScalesAlphaOnly() {
        assertEquals(0x80FFFFFF, CrosshairGeometry.applyOpacity(0xFFFFFFFF, 0.5));
        assertEquals(0x00123456, CrosshairGeometry.applyOpacity(0xFF123456, 0));
        List<Primitive> prims = CrosshairGeometry.build(CrosshairStyle.DEFAULT.withOpacity(0.5), CX, CY);
        assertEquals(0x80FFFFFF, prims.get(prims.size() - 1).argb());
    }

    @Test
    void styleClampsAndRoundTripsJson() {
        CrosshairStyle wild = new CrosshairStyle(CrosshairShape.CROSS, 999, 0, -4, true, 9, 7.0, 1, 2, true, true);
        assertEquals(CrosshairStyle.MAX_SIZE, wild.size());
        assertEquals(1, wild.thickness());
        assertEquals(0, wild.gap());
        assertEquals(CrosshairStyle.MAX_OUTLINE, wild.outlineThickness());
        assertEquals(1.0, wild.opacity());
        JsonObject json = wild.toJson();
        assertEquals(wild, CrosshairStyle.fromJson(json));
        assertEquals("#FF000000", CrosshairStyle.DEFAULT.toJson().get("outlineColor").getAsString());
        CrosshairStyle tolerant = CrosshairStyle.fromJson(JsonParser.parseString(
                "{\"shape\":\"nope\",\"size\":\"big\",\"color\":\"#7C5CFF\"}").getAsJsonObject());
        assertEquals(CrosshairShape.CROSS, tolerant.shape());
        assertEquals(5, tolerant.size());
        assertEquals(0xFF7C5CFF, tolerant.color());
        assertEquals(CrosshairStyle.DEFAULT, CrosshairStyle.fromJson(null));
    }

    @Test
    void presetsLoadAndMatch() {
        List<CrosshairPreset> presets = CrosshairPresets.builtIns();
        assertEquals(CrosshairPresets.IDS, presets.stream().map(CrosshairPreset::id).toList());
        assertEquals(CrosshairStyle.DEFAULT, CrosshairPresets.find("default").orElseThrow().style());
        assertEquals(CrosshairShape.CIRCLE, CrosshairPresets.find("circle").orElseThrow().style().shape());
        assertEquals(CrosshairShape.PLUS_DOT, CrosshairPresets.find("precision").orElseThrow().style().shape());
        assertTrue(CrosshairPresets.find("bold").orElseThrow().style().dynamic());
        assertEquals("dot", CrosshairPresets.matching(CrosshairPresets.find("dot").orElseThrow().style())
                .orElseThrow().id());
        for (CrosshairPreset preset : presets) {
            assertFalse(CrosshairGeometry.build(preset.style(), CX, CY, 2).isEmpty(), preset.id());
        }
    }

    @Test
    void storePersists(@TempDir Path dir) throws IOException {
        VantaPaths paths = new VantaPaths(dir);
        paths.createDirectories();
        JsonStore json = new JsonStore(MutableClock.standard());
        CrosshairStore store = new CrosshairStore(json, paths);
        store.load();
        assertEquals(CrosshairStyle.DEFAULT, store.style());
        assertEquals("default", store.activePreset().orElseThrow().id());
        store.applyPreset(CrosshairPresets.find("circle").orElseThrow());
        assertTrue(store.isDirty());
        store.saveIfDirty();
        CrosshairStore reloaded = new CrosshairStore(json, paths);
        reloaded.load();
        assertEquals(CrosshairShape.CIRCLE, reloaded.style().shape());
        assertEquals("circle", reloaded.activePreset().orElseThrow().id());
        reloaded.setStyle(reloaded.style().withSize(9));
        assertTrue(reloaded.activePreset().isEmpty());
    }
}
