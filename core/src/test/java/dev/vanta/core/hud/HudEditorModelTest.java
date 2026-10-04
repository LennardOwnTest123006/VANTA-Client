package dev.vanta.core.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HudEditorModelTest {
    private static final int W = 400;
    private static final int H = 240;
    private HudEditorModel model;

    @BeforeEach
    void setUp() {
        HudLayout layout = new HudLayout(List.of(
                HudWidgetState.defaults("fps", HudWidgetType.FPS, HudAnchor.TOP_LEFT, 10, 10),
                HudWidgetState.defaults("ping", HudWidgetType.PING, HudAnchor.TOP_LEFT, 100, 100),
                HudWidgetState.defaults("crosshair", HudWidgetType.CROSSHAIR)));
        model = new HudEditorModel(layout, W, H);
    }

    private HudRect rect(String id) {
        return model.rectOf(id).orElseThrow();
    }

    @Test
    void clickSelectsAndEmptyClickClears() {
        assertTrue(model.mouseDown(12, 12));
        assertEquals(Optional.of("fps"), model.selectedId());
        model.mouseUp(12, 12);
        assertFalse(model.mouseDown(300, 200));
        assertTrue(model.selectedId().isEmpty());
        model.updateHover(101, 101);
        assertEquals(Optional.of("ping"), model.hoverId());
    }

    @Test
    void dragMovesWithGridSnapAndRecordsOneUndoStep() {
        model.mouseDown(12, 12);
        model.mouseDrag(12 + 33, 12 + 21);
        assertTrue(model.isDragging());
        assertEquals(HudEditorModel.DragKind.MOVE, model.dragKind());
        HudRect during = rect("fps");
        assertEquals(0, during.x() % HudEditorModel.GRID);
        assertEquals(0, during.y() % HudEditorModel.GRID);
        model.mouseUp(45, 33);
        assertFalse(model.isDragging());
        HudRect after = rect("fps");
        assertEquals(during, after, "re-anchoring keeps the screen position");
        assertTrue(model.canUndo());
        assertFalse(model.canRedo());
        assertTrue(model.undo());
        assertEquals(new HudRect(10, 10, 44, 14), rect("fps"));
        assertTrue(model.redo());
        assertEquals(after, rect("fps"));
    }

    @Test
    void dragWithoutMovementDoesNotCreateUndo() {
        model.mouseDown(12, 12);
        model.mouseDrag(12, 12);
        model.mouseUp(12, 12);
        assertFalse(model.canUndo());
    }

    @Test
    void edgeSnapToOtherWidgetAndScreen() {
        model.mouseDown(12, 12);
        HudRect ping = rect("ping");
        int targetX = ping.x() + 2;
        model.mouseDrag(12 + (targetX - 10), 12);
        assertEquals(ping.x(), rect("fps").x(), "snapped to the ping widget's left edge");
        assertTrue(model.guides().stream().anyMatch(g -> g.vertical() && g.position() == ping.x()));
        model.mouseUp(0, 0);

        model.mouseDown(rect("fps").x() + 2, rect("fps").y() + 2);
        model.mouseDrag(rect("fps").x() + 2 - 3, rect("fps").y() + 2 - 500);
        assertEquals(0, rect("fps").y(), "clamped and snapped to the top edge");
        model.mouseUp(0, 0);
    }

    @Test
    void snapCanBeDisabled() {
        model.setSnapEnabled(false);
        model.mouseDown(12, 12);
        model.mouseDrag(12 + 33, 12 + 21);
        assertEquals(new HudRect(43, 31, 44, 14), rect("fps"));
        model.mouseUp(0, 0);
    }

    @Test
    void resizeHandlesChangeSize() {
        model.select("fps");
        Map<HudEditorModel.Handle, HudRect> handles = model.handleRects();
        assertEquals(8, handles.size());
        HudRect se = handles.get(HudEditorModel.Handle.SE);
        assertEquals(Optional.of(HudEditorModel.Handle.SE), model.handleAt(se.centerX(), se.centerY()));
        assertTrue(model.mouseDown(se.centerX(), se.centerY()));
        assertEquals(HudEditorModel.DragKind.RESIZE, model.dragKind());
        model.mouseDrag(se.centerX() + 20, se.centerY() + 10);
        model.mouseUp(0, 0);
        HudWidgetState fps = model.selected().orElseThrow();
        assertEquals(64, fps.width());
        assertEquals(24, fps.height());
        assertEquals(10, rect("fps").x(), "left edge did not move");
        assertTrue(model.canUndo());

        HudRect nw = model.handleRects().get(HudEditorModel.Handle.NW);
        model.mouseDown(nw.centerX(), nw.centerY());
        model.mouseDrag(nw.centerX() + 500, nw.centerY() + 500);
        model.mouseUp(0, 0);
        assertEquals(HudWidgetState.MIN_SIZE, model.selected().orElseThrow().width(), "minimum size enforced");
    }

    @Test
    void crosshairCannotBeMovedResizedOrDuplicated() {
        HudRect crosshair = rect("crosshair");
        assertTrue(model.mouseDown(crosshair.centerX(), crosshair.centerY()));
        assertEquals(Optional.of("crosshair"), model.selectedId());
        assertFalse(model.isDragging());
        assertTrue(model.handleRects().isEmpty());
        assertTrue(model.duplicate().isEmpty());
        assertFalse(model.nudge(5, 5));
        assertTrue(model.delete(), "delete toggles the crosshair instead of removing it");
        assertTrue(model.layout().find("crosshair").isPresent());
        assertFalse(model.layout().find("crosshair").orElseThrow().enabled());
    }

    @Test
    void nudgeDuplicateDeleteAndReset() {
        model.select("fps");
        assertTrue(model.nudge(3, 0));
        assertEquals(13, rect("fps").x());
        assertTrue(model.nudge(-100, -100));
        assertEquals(new HudRect(0, 0, 44, 14), rect("fps"), "clamped");

        Optional<HudWidgetState> copy = model.duplicate();
        assertTrue(copy.isPresent());
        assertEquals("fps-2", copy.get().id());
        assertEquals(Optional.of("fps-2"), model.selectedId());
        assertEquals(new HudRect(8, 8, 44, 14), rect("fps-2"));

        assertTrue(model.update(w -> w.withScale(1.5).withOpacity(0.3)));
        assertEquals(1.5, model.selected().orElseThrow().scale());
        assertTrue(model.resetWidget());
        assertEquals(1.0, model.selected().orElseThrow().scale());

        assertTrue(model.delete());
        assertTrue(model.layout().find("fps-2").isEmpty());
        assertTrue(model.selectedId().isEmpty());
        assertTrue(model.undo());
        assertTrue(model.layout().find("fps-2").isPresent());
    }

    @Test
    void undoStackIsBounded() {
        model.select("fps");
        for (int i = 0; i < HudEditorModel.UNDO_DEPTH + 20; i++) {
            model.nudge(1, 0);
        }
        int undone = 0;
        while (model.undo()) {
            undone++;
        }
        assertEquals(HudEditorModel.UNDO_DEPTH, undone);
    }

    @Test
    void addWidgetAndPresetsAreUndoable() {
        HudWidgetState memory = model.addWidget(HudWidgetType.MEMORY);
        assertEquals("memory", memory.id());
        assertEquals(Optional.of("memory"), model.selectedId());
        HudWidgetState second = model.addWidget(HudWidgetType.MEMORY);
        assertEquals("memory-2", second.id());
        HudRect first = model.rectOf("memory").orElseThrow();
        HudRect next = model.rectOf("memory-2").orElseThrow();
        assertTrue(!first.intersects(next), "an added widget never lands on an existing one");
        for (var entry : model.rects().entrySet()) {
            assertTrue(entry.getKey().equals("memory-2") || !entry.getValue().intersects(next),
                    "clear of every widget, not just its own type: " + entry.getKey());
        }
        model.setLayout(HudPresets.find("minimal").orElseThrow().layout());
        assertEquals(3, model.layout().size());
        assertTrue(model.selectedId().isEmpty(), "selection cleared when the widget disappears");
        assertTrue(model.undo());
        assertEquals(5, model.layout().size());
    }

    @Test
    void listenersAndResize() {
        AtomicInteger events = new AtomicInteger();
        Runnable unsubscribe = model.onChange(events::incrementAndGet);
        model.select("fps");
        model.resize(800, 600);
        assertEquals(800, model.screenWidth());
        assertTrue(events.get() >= 2);
        unsubscribe.run();
        int before = events.get();
        model.clearSelection();
        assertEquals(before, events.get());
        model.reset(HudLayout.EMPTY);
        assertFalse(model.canUndo());
    }

    @Test
    void gridSnapRounding() {
        assertEquals(0, HudEditorModel.snapGrid(0));
        assertEquals(2, HudEditorModel.snapGrid(1));
        assertEquals(2, HudEditorModel.snapGrid(2));
        assertEquals(4, HudEditorModel.snapGrid(3));
        assertEquals(-2, HudEditorModel.snapGrid(-3));
    }
}
