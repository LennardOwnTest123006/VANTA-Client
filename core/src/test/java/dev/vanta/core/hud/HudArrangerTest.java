package dev.vanta.core.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The {@code hud.arrange} packing and the overlap repair: no two visible widgets overlap, every widget stays inside
 * the screen (also at every larger size), sizes are kept, and nothing ends up on one spot.
 */
class HudArrangerTest {
    /** Screen sizes every arrangement is checked at: the compact minimum, common GUI-scaled sizes, a big window. */
    private static final int[][] SIZES = {{427, 240}, {480, 270}, {640, 360}, {854, 480}, {960, 540}, {1920, 1080}};

    /** Every type the arrangement moves (all but the crosshair). */
    private static List<HudWidgetType> movableTypes() {
        List<HudWidgetType> out = new ArrayList<>();
        for (HudWidgetType type : HudWidgetType.values()) {
            if (type.movable()) {
                out.add(type);
            }
        }
        return out;
    }

    /** A layout with the given types at their default spots plus the crosshair. */
    private static HudLayout atDefaults(List<HudWidgetType> types) {
        HudLayout layout = HudLayout.EMPTY;
        for (HudWidgetType type : types) {
            layout = layout.with(HudWidgetState.defaults(layout.nextId(type), type));
        }
        return layout.with(HudWidgetState.defaults("crosshair", HudWidgetType.CROSSHAIR));
    }

    /** The bug of client 1.5.0: every widget on the same spot (what anchor-only hud.set moves produced). */
    private static HudLayout allOnOneSpot(List<HudWidgetType> types) {
        HudLayout layout = HudLayout.EMPTY;
        for (HudWidgetType type : types) {
            layout = layout.with(HudWidgetState.defaults(layout.nextId(type), type, HudAnchor.TOP_LEFT, 4, 4));
        }
        return layout.with(HudWidgetState.defaults("crosshair", HudWidgetType.CROSSHAIR));
    }

    /** The widget's rectangle without the clamping {@link HudLayout#resolveRect} applies. */
    private static HudRect raw(HudWidgetState widget, int screenW, int screenH, double globalScale) {
        int w = Math.max(1, (int) Math.round(widget.scaledWidth() * globalScale));
        int h = Math.max(1, (int) Math.round(widget.scaledHeight() * globalScale));
        return new HudRect(widget.anchor().resolveX(screenW, w, widget.offsetX()),
                widget.anchor().resolveY(screenH, h, widget.offsetY()), w, h);
    }

    /** Asserts no overlap and everything inside, at the given size and every larger one of {@link #SIZES}. */
    private static void assertTidy(HudLayout layout, int fromW, int fromH, double globalScale, String what) {
        for (int[] size : SIZES) {
            if (size[0] < fromW || size[1] < fromH) {
                continue;
            }
            assertTidyAt(layout, size[0], size[1], globalScale, what);
        }
        assertTidyAt(layout, fromW, fromH, globalScale, what);
    }

    private static void assertTidyAt(HudLayout layout, int w, int h, double globalScale, String what) {
        List<String> overlaps = HudArranger.overlaps(layout, w, h, globalScale);
        assertTrue(overlaps.isEmpty(), what + " at " + w + "x" + h + ": overlaps " + overlaps);
        for (HudWidgetState widget : layout.enabled()) {
            if (!widget.type().movable()) {
                continue;
            }
            HudRect r = raw(widget, w, h, globalScale);
            assertTrue(r.x() >= 0 && r.y() >= 0 && r.right() <= w && r.bottom() <= h,
                    what + " at " + w + "x" + h + ": " + widget.id() + " outside the screen: " + r);
        }
    }

    private static void assertSizesKept(HudLayout before, HudLayout after) {
        assertEquals(before.size(), after.size());
        for (HudWidgetState widget : before.widgets()) {
            HudWidgetState now = after.find(widget.id()).orElseThrow();
            assertEquals(widget.width(), now.width(), widget.id());
            assertEquals(widget.height(), now.height(), widget.id());
            assertEquals(widget.scale(), now.scale(), 1e-9, widget.id());
            assertEquals(widget.enabled(), now.enabled(), widget.id());
            assertEquals(widget.withPosition(now.anchor(), now.offsetX(), now.offsetY()), now,
                    "only the position changes: " + widget.id());
        }
    }

    @Test
    void theDefaultLayoutIsAlreadyTidy() {
        HudLayout layout = HudPresets.defaultPreset().layout();
        HudArranger.Result result = HudArranger.arrange(layout, HudArranger.Target.AUTO, 1.0);
        assertEquals(List.of(), result.moved(), "the default layout already stacks every corner");
        assertEquals(layout, result.layout());
        assertEquals(9, result.arranged().size(), "every visible widget but the crosshair");
        assertEquals(HudArranger.COMPACT_WIDTH, result.screenWidth());
        assertTidy(result.layout(), HudArranger.COMPACT_WIDTH, HudArranger.COMPACT_HEIGHT, 1.0, "default");
    }

    @Test
    void everyWidgetOnOneSpotIsStackedApart() {
        for (int n = 1; n <= movableTypes().size(); n++) {
            HudLayout layout = allOnOneSpot(movableTypes().subList(0, n));
            HudArranger.Result result = HudArranger.arrange(layout, HudArranger.Target.AUTO, 1.0);
            assertTrue(result.complete(), n + " widgets: " + result.unplaced());
            assertEquals(n, result.arranged().size());
            assertSizesKept(layout, result.layout());
            assertTidy(result.layout(), result.screenWidth(), result.screenHeight(), 1.0, n + " on one spot");
        }
    }

    @Test
    void oneToAllWidgetTypesFitWithoutOverlapForEveryTarget() {
        for (HudArranger.Target target : HudArranger.Target.values()) {
            for (int n = 1; n <= movableTypes().size(); n++) {
                HudLayout layout = atDefaults(movableTypes().subList(0, n));
                HudArranger.Result result = HudArranger.arrange(layout, target, 1.0);
                String what = target.id() + " with " + n + " widget types";
                assertTrue(result.complete(), what + ": " + result.unplaced());
                assertSizesKept(layout, result.layout());
                assertTidy(result.layout(), result.screenWidth(), result.screenHeight(), 1.0, what);
                // Packing for the compact screen also holds at the small sizes directly.
                HudArranger.Result compact = HudArranger.arrange(layout, target, 427, 240, 1.0);
                if (compact.complete()) {
                    assertTidy(compact.layout(), 427, 240, 1.0, what + " (compact)");
                }
            }
        }
    }

    @Test
    void autoKeepsEachWidgetInItsCorner() {
        HudLayout layout = HudPresets.defaultPreset().layout()
                .with(HudWidgetState.defaults("keystrokes", HudWidgetType.KEYSTROKES))
                .with(HudWidgetState.defaults("cps", HudWidgetType.CPS));
        // keystrokes and cps both start at the bottom-right corner on top of item_durability.
        assertFalse(HudArranger.overlaps(layout, 854, 480, 1.0).isEmpty());
        HudArranger.Result result = HudArranger.arrange(layout, HudArranger.Target.AUTO, 1.0);
        HudLayout arranged = result.layout();
        for (String id : List.of("item_durability", "keystrokes", "cps")) {
            assertEquals(HudAnchor.BOTTOM_RIGHT, arranged.find(id).orElseThrow().anchor(), id);
        }
        assertEquals(HudAnchor.TOP_LEFT, arranged.find("fps").orElseThrow().anchor());
        assertEquals(HudAnchor.TOP_RIGHT, arranged.find("clock").orElseThrow().anchor());
        assertEquals(HudAnchor.BOTTOM_LEFT, arranged.find("armor").orElseThrow().anchor());
        // Stacked upwards from the corner in layout order (all three were on the same spot).
        assertEquals(4, arranged.find("item_durability").orElseThrow().offsetY());
        assertEquals(4 + 26 + HudArranger.GAP, arranged.find("keystrokes").orElseThrow().offsetY());
        assertEquals(List.of("keystrokes", "cps"), result.moved());
        assertTidy(arranged, 427, 240, 1.0, "default plus keystrokes and cps");
    }

    @Test
    void aCornerTargetPutsEveryWidgetThere() {
        HudLayout layout = HudPresets.defaultPreset().layout();
        for (HudArranger.Target target : List.of(HudArranger.Target.TOP_LEFT, HudArranger.Target.TOP_RIGHT,
                HudArranger.Target.BOTTOM_LEFT, HudArranger.Target.BOTTOM_RIGHT)) {
            HudArranger.Result result = HudArranger.arrange(layout, target, 1.0);
            assertTrue(result.complete());
            for (String id : result.arranged()) {
                assertEquals(target.anchor().orElseThrow(), result.layout().find(id).orElseThrow().anchor(),
                        target.id() + ": " + id);
            }
            assertTidy(result.layout(), result.screenWidth(), result.screenHeight(), 1.0, target.id());
        }
    }

    @Test
    void aFullColumnWrapsToTheNextColumn() {
        HudLayout layout = HudLayout.EMPTY;
        for (int i = 0; i < 3; i++) {
            layout = layout.with(HudWidgetState.defaults(layout.nextId(HudWidgetType.KEYSTROKES),
                    HudWidgetType.KEYSTROKES, HudAnchor.TOP_LEFT, 4, 4));
        }
        HudArranger.Result result = HudArranger.arrange(layout, HudArranger.Target.TOP_LEFT, 427, 240, 1.0);
        HudLayout arranged = result.layout();
        // 240 px high: two 84 px tall widgets fit one column, the third starts the next one.
        assertEquals(4, arranged.find("keystrokes").orElseThrow().offsetY());
        assertEquals(4 + 84 + HudArranger.GAP, arranged.find("keystrokes-2").orElseThrow().offsetY());
        HudWidgetState third = arranged.find("keystrokes-3").orElseThrow();
        assertEquals(4 + 62 + HudArranger.GAP, third.offsetX(), "next column after the widest of the first");
        assertEquals(4, third.offsetY());
        assertTidy(arranged, 427, 240, 1.0, "three keystrokes");
    }

    @Test
    void hiddenWidgetsAndTheCrosshairNeverMove() {
        HudLayout layout = allOnOneSpot(movableTypes());
        HudWidgetState hidden = layout.find("biome").orElseThrow().withEnabled(false);
        layout = layout.with(hidden);
        HudArranger.Result result = HudArranger.arrange(layout, HudArranger.Target.AUTO, 1.0);
        assertEquals(hidden, result.layout().find("biome").orElseThrow());
        assertEquals(layout.find("crosshair"), result.layout().find("crosshair"));
        assertFalse(result.arranged().contains("biome"));
        assertFalse(result.arranged().contains("crosshair"));
    }

    @Test
    void nothingCoversTheCrosshair() {
        HudLayout layout = HudLayout.EMPTY.with(HudWidgetState.defaults("crosshair", HudWidgetType.CROSSHAIR));
        // A widget dead in the centre is moved to the corner of its quarter.
        HudWidgetState fps = HudWidgetState.defaults("fps", HudWidgetType.FPS, HudAnchor.CENTER, 0, 0);
        layout = layout.with(fps);
        HudArranger.Result result = HudArranger.arrange(layout, HudArranger.Target.AUTO, 1.0);
        HudWidgetState moved = result.layout().find("fps").orElseThrow();
        assertTrue(moved.anchor() != HudAnchor.CENTER, moved.toString());
        HudRect centre = new HudRect(854 / 2 - 8, 480 / 2 - 8, 16, 16);
        assertFalse(result.layout().resolveRect(moved, 854, 480).intersects(centre));
    }

    @Test
    void theGlobalScaleIsPackedAsDrawn() {
        HudLayout layout = allOnOneSpot(movableTypes().subList(0, 8));
        HudArranger.Result result = HudArranger.arrange(layout, HudArranger.Target.AUTO, 2.0);
        assertTrue(result.complete());
        assertTidy(result.layout(), result.screenWidth(), result.screenHeight(), 2.0, "global scale 2");
        assertFalse(HudArranger.overlaps(result.layout(), 854, 480, 2.0).size() > 0);
    }

    @Test
    void tooManyWidgetsForTheCompactScreenUseTheReferenceScreen() {
        HudLayout layout = HudLayout.EMPTY;
        for (int i = 0; i < 6; i++) {
            for (HudWidgetType type : List.of(HudWidgetType.KEYSTROKES, HudWidgetType.ARMOR,
                    HudWidgetType.FRAMETIME_GRAPH)) {
                layout = layout.with(HudWidgetState.defaults(layout.nextId(type), type));
            }
        }
        assertFalse(HudArranger.arrange(layout, HudArranger.Target.AUTO, 427, 240, 1.0).complete());
        HudArranger.Result result = HudArranger.arrange(layout, HudArranger.Target.AUTO, 1.0);
        assertEquals(HudArranger.REFERENCE_WIDTH, result.screenWidth());
        assertTrue(result.complete(), result.unplaced().toString());
        assertTidy(result.layout(), 854, 480, 1.0, "18 big widgets");
    }

    @Test
    void whatDoesNotFitIsReportedAndKeptWhereItWas() {
        HudLayout layout = allOnOneSpot(movableTypes());
        HudArranger.Result result = HudArranger.arrange(layout, HudArranger.Target.AUTO, 200, 120, 1.0);
        assertFalse(result.complete());
        for (String id : result.unplaced()) {
            assertEquals(layout.find(id), result.layout().find(id), "left where it was: " + id);
        }
        // The widgets that were placed do not overlap each other.
        HudLayout placedOnly = HudLayout.EMPTY;
        for (String id : result.arranged()) {
            placedOnly = placedOnly.with(result.layout().find(id).orElseThrow());
        }
        assertEquals(List.of(), HudArranger.overlaps(placedOnly, 200, 120, 1.0));
    }

    @Test
    void anOverlapAfterAMoveRestacksOnlyThatCorner() {
        HudLayout layout = HudPresets.defaultPreset().layout();
        // The player moved the clock onto the FPS counter (top left).
        HudWidgetState clock = layout.find("clock").orElseThrow().withPosition(HudAnchor.TOP_LEFT, 4, 4);
        layout = layout.with(clock);
        Optional<HudArranger.Result> resolved = HudArranger.resolveOverlaps(layout, List.of("clock"), 1.0);
        assertTrue(resolved.isPresent());
        HudLayout fixed = resolved.get().layout();
        assertEquals(List.of(HudAnchor.TOP_LEFT), resolved.get().groups());
        HudWidgetState clockNow = fixed.find("clock").orElseThrow();
        assertEquals(HudAnchor.TOP_LEFT, clockNow.anchor());
        assertEquals(4, clockNow.offsetY(), "the moved widget keeps the requested spot");
        assertEquals(4 + 14 + HudArranger.GAP, fixed.find("fps").orElseThrow().offsetY());
        for (String id : List.of("potion_effects", "armor", "item_durability")) {
            assertEquals(layout.find(id), fixed.find(id), "other corners stay: " + id);
        }
        assertEquals(List.of(), HudArranger.overlaps(fixed, 854, 480, 1.0));
    }

    @Test
    void anOverlapOfUntouchedWidgetsIsLeftAlone() {
        HudLayout layout = allOnOneSpot(List.of(HudWidgetType.FPS, HudWidgetType.PING));
        assertEquals(Optional.empty(), HudArranger.resolveOverlaps(layout, List.of(), 1.0));
        assertEquals(Optional.empty(), HudArranger.resolveOverlaps(HudPresets.defaultPreset().layout(),
                List.of("fps", "clock"), 1.0), "nothing overlaps");
    }

    @Test
    void anchorOnlyMovesOfEveryWidgetEndStacked() {
        // What a model did in 1.5.0 for "sort the GUIs": hud.set with an anchor and no position for every widget.
        HudLayout layout = HudPresets.defaultPreset().layout();
        List<String> changed = new ArrayList<>();
        for (HudWidgetState widget : layout.widgets()) {
            if (widget.type().movable()) {
                layout = layout.with(widget.withPosition(HudAnchor.TOP_LEFT, 4, 4));
                changed.add(widget.id());
            }
        }
        HudLayout fixed = HudArranger.resolveOverlaps(layout, changed, 1.0).orElseThrow().layout();
        assertEquals(List.of(), HudArranger.overlaps(fixed, 854, 480, 1.0));
        assertTidyAt(fixed, 854, 480, 1.0, "anchor-only moves");
        assertEquals(4, fixed.find("fps").orElseThrow().offsetY(), "layout order wins the tie");
    }

    @Test
    void aWidgetMovedToTheCentreOnlyStepsOffTheCrosshair() {
        HudLayout layout = HudPresets.defaultPreset().layout();
        HudWidgetState fps = layout.find("fps").orElseThrow().withPosition(HudAnchor.CENTER, 0, 0);
        layout = layout.with(fps);
        HudArranger.Result result = HudArranger.resolveOverlaps(layout, List.of("fps"), 1.0).orElseThrow();
        HudWidgetState moved = result.layout().find("fps").orElseThrow();
        HudRect rect = result.layout().resolveRect(moved, 854, 480);
        assertFalse(rect.intersects(new HudRect(854 / 2 - 8, 480 / 2 - 8, 16, 16)));
        assertTrue(Math.abs(rect.centerX() - 427) + Math.abs(rect.centerY() - 240) < 40,
                "it stays near the centre: " + rect);
        assertEquals(List.of("fps"), result.moved());
    }

    @Test
    void targetIds() {
        assertEquals(List.of("auto", "top_left", "top_right", "bottom_left", "bottom_right"),
                HudArranger.Target.ids());
        assertEquals(Optional.of(HudArranger.Target.TOP_LEFT), HudArranger.Target.fromId("Top Left"));
        assertEquals(Optional.of(HudArranger.Target.BOTTOM_RIGHT), HudArranger.Target.fromId("bottom-right"));
        assertEquals(Optional.of(HudArranger.Target.AUTO), HudArranger.Target.fromId("AUTO"));
        assertEquals(Optional.empty(), HudArranger.Target.fromId("middle"));
        assertEquals(Optional.empty(), HudArranger.Target.fromId(null));
        assertEquals(Optional.empty(), HudArranger.Target.AUTO.anchor());
    }
}
