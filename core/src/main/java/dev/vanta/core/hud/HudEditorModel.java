package dev.vanta.core.hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

/**
 * Interaction model of the HUD editor, independent of rendering.
 * <p>
 * Holds the layout being edited, the selection, hover and drag state, snapping (2 px grid plus edge snapping to the
 * screen and to other widgets within 4 px), resize handles, keyboard nudging, duplicate / delete, a 50-deep undo/redo
 * stack and bounds clamping. The UI feeds it mouse and key events in GUI pixels and renders from
 * {@link #layout()}, {@link #rects()}, {@link #handleRects()} and {@link #guides()}.
 * <p>
 * The editor works with the stored widget scale only; the global HUD scale setting is applied by the renderer.
 */
public final class HudEditorModel {
    /** Grid step in GUI pixels. */
    public static final int GRID = 2;
    /** Distance within which edges snap to the screen or to other widgets. */
    public static final int EDGE_SNAP = 4;
    /** Undo stack depth. */
    public static final int UNDO_DEPTH = 50;
    /** Side length of a resize handle. */
    public static final int HANDLE_SIZE = 4;

    /** Resize handles. */
    public enum Handle {
        NW, N, NE, E, SE, S, SW, W;

        boolean movesLeft() {
            return this == NW || this == W || this == SW;
        }

        boolean movesRight() {
            return this == NE || this == E || this == SE;
        }

        boolean movesTop() {
            return this == NW || this == N || this == NE;
        }

        boolean movesBottom() {
            return this == SW || this == S || this == SE;
        }
    }

    /** What the mouse is doing. */
    public enum DragKind {
        NONE, MOVE, RESIZE
    }

    /**
     * A snap line to draw while dragging.
     *
     * @param vertical true for a vertical line at {@code x = position}, false for a horizontal line at {@code y}
     * @param position screen coordinate
     */
    public record SnapGuide(boolean vertical, int position) {
    }

    private HudLayout layout;
    private int screenW;
    private int screenH;
    private String selectedId;
    private String hoverId;
    private boolean snapEnabled = true;
    private final Deque<HudLayout> undo = new ArrayDeque<>();
    private final Deque<HudLayout> redo = new ArrayDeque<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private DragKind dragKind = DragKind.NONE;
    private Handle dragHandle;
    private int dragStartX;
    private int dragStartY;
    private HudRect dragStartRect;
    private HudLayout dragStartLayout;
    private boolean dragChanged;
    private List<SnapGuide> guides = List.of();

    public HudEditorModel(HudLayout layout, int screenW, int screenH) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.screenW = Math.max(1, screenW);
        this.screenH = Math.max(1, screenH);
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    /** The layout being edited. */
    public HudLayout layout() {
        return layout;
    }

    public int screenWidth() {
        return screenW;
    }

    public int screenHeight() {
        return screenH;
    }

    /** Selected widget, if any. */
    public Optional<HudWidgetState> selected() {
        return selectedId == null ? Optional.empty() : layout.find(selectedId);
    }

    /** Selected widget id, if any. */
    public Optional<String> selectedId() {
        return Optional.ofNullable(selectedId);
    }

    /** Hovered widget id, if any. */
    public Optional<String> hoverId() {
        return Optional.ofNullable(hoverId);
    }

    /** Current drag kind. */
    public DragKind dragKind() {
        return dragKind;
    }

    /** True while a mouse drag is in progress. */
    public boolean isDragging() {
        return dragKind != DragKind.NONE;
    }

    /** Snap guides for the current drag. */
    public List<SnapGuide> guides() {
        return guides;
    }

    public boolean snapEnabled() {
        return snapEnabled;
    }

    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** Screen rectangle of a widget. */
    public Optional<HudRect> rectOf(String id) {
        return layout.find(id).map(w -> layout.resolveRect(w, screenW, screenH));
    }

    /** Screen rectangles of every widget. */
    public Map<String, HudRect> rects() {
        return layout.resolveAll(screenW, screenH, 1.0);
    }

    /** Topmost widget under the point (disabled widgets are editable too). */
    public Optional<HudWidgetState> widgetAt(int x, int y) {
        List<HudWidgetState> widgets = layout.widgets();
        for (int i = widgets.size() - 1; i >= 0; i--) {
            HudWidgetState widget = widgets.get(i);
            if (layout.resolveRect(widget, screenW, screenH).contains(x, y)) {
                return Optional.of(widget);
            }
        }
        return Optional.empty();
    }

    /** Resize handle rectangles of the selected widget (empty when nothing resizable is selected). */
    public Map<Handle, HudRect> handleRects() {
        Optional<HudWidgetState> selected = selected();
        if (selected.isEmpty() || !selected.get().type().supportsResize()) {
            return Map.of();
        }
        HudRect r = layout.resolveRect(selected.get(), screenW, screenH);
        int half = HANDLE_SIZE / 2;
        Map<Handle, HudRect> out = new EnumMap<>(Handle.class);
        out.put(Handle.NW, handle(r.x() - half, r.y() - half));
        out.put(Handle.N, handle(r.centerX() - half, r.y() - half));
        out.put(Handle.NE, handle(r.right() - half, r.y() - half));
        out.put(Handle.E, handle(r.right() - half, r.centerY() - half));
        out.put(Handle.SE, handle(r.right() - half, r.bottom() - half));
        out.put(Handle.S, handle(r.centerX() - half, r.bottom() - half));
        out.put(Handle.SW, handle(r.x() - half, r.bottom() - half));
        out.put(Handle.W, handle(r.x() - half, r.centerY() - half));
        return out;
    }

    private static HudRect handle(int x, int y) {
        return new HudRect(x, y, HANDLE_SIZE, HANDLE_SIZE);
    }

    /** Handle under the point, if the selected widget is resizable. */
    public Optional<Handle> handleAt(int x, int y) {
        for (Map.Entry<Handle, HudRect> entry : handleRects().entrySet()) {
            if (entry.getValue().grow(1).contains(x, y)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    // ---- layout management -----------------------------------------------------------------------------------------

    /** Replaces the layout as an undoable step (presets, external edits). */
    public void setLayout(HudLayout next) {
        Objects.requireNonNull(next, "next");
        if (next.equals(layout)) {
            return;
        }
        pushUndo();
        layout = next;
        if (selectedId != null && layout.find(selectedId).isEmpty()) {
            selectedId = null;
        }
        fire();
    }

    /** Replaces the layout and clears history (used when the editor opens). */
    public void reset(HudLayout next) {
        layout = Objects.requireNonNull(next, "next");
        undo.clear();
        redo.clear();
        selectedId = null;
        hoverId = null;
        cancelDrag();
        fire();
    }

    /** Updates the screen size; widget offsets are anchor-relative so nothing else changes. */
    public void resize(int width, int height) {
        screenW = Math.max(1, width);
        screenH = Math.max(1, height);
        fire();
    }

    // ---- selection -----------------------------------------------------------------------------------------------

    /** Selects a widget by id (unknown ids clear the selection). */
    public void select(String id) {
        selectedId = id != null && layout.find(id).isPresent() ? id : null;
        fire();
    }

    public void clearSelection() {
        selectedId = null;
        fire();
    }

    /** Updates hover from the mouse position. */
    public void updateHover(int x, int y) {
        String next = widgetAt(x, y).map(HudWidgetState::id).orElse(null);
        if (!Objects.equals(next, hoverId)) {
            hoverId = next;
            fire();
        }
    }

    // ---- mouse ---------------------------------------------------------------------------------------------------

    /**
     * Left mouse pressed.
     *
     * @return true when the editor consumed the press (a widget or handle was hit)
     */
    public boolean mouseDown(int x, int y) {
        Optional<Handle> handle = handleAt(x, y);
        if (handle.isPresent() && selected().isPresent()) {
            beginDrag(DragKind.RESIZE, handle.get(), x, y);
            return true;
        }
        Optional<HudWidgetState> hit = widgetAt(x, y);
        if (hit.isEmpty()) {
            selectedId = null;
            fire();
            return false;
        }
        selectedId = hit.get().id();
        if (hit.get().type().movable()) {
            beginDrag(DragKind.MOVE, null, x, y);
        }
        fire();
        return true;
    }

    private void beginDrag(DragKind kind, Handle handle, int x, int y) {
        dragKind = kind;
        dragHandle = handle;
        dragStartX = x;
        dragStartY = y;
        dragStartRect = layout.resolveRect(selected().orElseThrow(), screenW, screenH);
        dragStartLayout = layout;
        dragChanged = false;
    }

    /** Mouse moved while the left button is held. */
    public void mouseDrag(int x, int y) {
        if (dragKind == DragKind.NONE || selectedId == null) {
            return;
        }
        Optional<HudWidgetState> selected = layout.find(selectedId);
        if (selected.isEmpty()) {
            cancelDrag();
            return;
        }
        int dx = x - dragStartX;
        int dy = y - dragStartY;
        HudWidgetState widget = selected.get();
        HudWidgetState next;
        List<SnapGuide> newGuides = new ArrayList<>();
        if (dragKind == DragKind.MOVE) {
            HudRect target = dragStartRect.translate(dx, dy);
            target = snapRect(target, widget.id(), newGuides);
            target = target.clampTo(screenW, screenH);
            next = HudLayout.placed(widget, target.x(), target.y(), screenW, screenH);
        } else {
            HudRect r = resizeRect(dragStartRect, dragHandle, dx, dy, widget.scale());
            r = snapEdges(r, dragHandle, newGuides).clampTo(screenW, screenH);
            int unscaledW = Math.max(HudWidgetState.MIN_SIZE, (int) Math.round(r.width() / widget.scale()));
            int unscaledH = Math.max(HudWidgetState.MIN_SIZE, (int) Math.round(r.height() / widget.scale()));
            next = HudLayout.placed(widget.withSize(unscaledW, unscaledH), r.x(), r.y(), screenW, screenH);
        }
        guides = Collections.unmodifiableList(newGuides);
        if (!next.equals(widget)) {
            layout = layout.with(next);
            dragChanged = true;
        }
        fire();
    }

    /** Left mouse released: commits the drag as one undo step and re-anchors the widget to its screen third. */
    public void mouseUp(int x, int y) {
        if (dragKind == DragKind.NONE) {
            return;
        }
        if (dragChanged && selectedId != null) {
            HudLayout before = dragStartLayout;
            layout.find(selectedId).ifPresent(w -> layout = layout.with(HudLayout.reanchored(w, screenW, screenH)));
            recordUndo(before);
        }
        cancelDrag();
        fire();
    }

    private void cancelDrag() {
        dragKind = DragKind.NONE;
        dragHandle = null;
        dragStartRect = null;
        dragStartLayout = null;
        dragChanged = false;
        guides = List.of();
    }

    private HudRect resizeRect(HudRect start, Handle handle, int dx, int dy, double scale) {
        int minSize = Math.max(1, (int) Math.round(HudWidgetState.MIN_SIZE * scale));
        int left = start.x();
        int top = start.y();
        int right = start.right();
        int bottom = start.bottom();
        if (handle.movesLeft()) {
            left = Math.min(left + dx, right - minSize);
        }
        if (handle.movesRight()) {
            right = Math.max(right + dx, left + minSize);
        }
        if (handle.movesTop()) {
            top = Math.min(top + dy, bottom - minSize);
        }
        if (handle.movesBottom()) {
            bottom = Math.max(bottom + dy, top + minSize);
        }
        return new HudRect(left, top, right - left, bottom - top);
    }

    /** Snaps a moving rectangle: grid first, then edges/centres against the screen and other widgets. */
    private HudRect snapRect(HudRect rect, String ignoreId, List<SnapGuide> out) {
        if (!snapEnabled) {
            return rect;
        }
        int x = snapGrid(rect.x());
        int y = snapGrid(rect.y());
        HudRect gridded = rect.at(x, y);
        int bestDx = Integer.MAX_VALUE;
        int bestDy = Integer.MAX_VALUE;
        SnapGuide guideX = null;
        SnapGuide guideY = null;
        for (int target : xTargets(ignoreId)) {
            for (int own : new int[] {gridded.x(), gridded.right(), gridded.centerX()}) {
                int d = target - own;
                if (Math.abs(d) <= EDGE_SNAP && Math.abs(d) < Math.abs(bestDx)) {
                    bestDx = d;
                    guideX = new SnapGuide(true, target);
                }
            }
        }
        for (int target : yTargets(ignoreId)) {
            for (int own : new int[] {gridded.y(), gridded.bottom(), gridded.centerY()}) {
                int d = target - own;
                if (Math.abs(d) <= EDGE_SNAP && Math.abs(d) < Math.abs(bestDy)) {
                    bestDy = d;
                    guideY = new SnapGuide(false, target);
                }
            }
        }
        if (bestDx != Integer.MAX_VALUE) {
            x = gridded.x() + bestDx;
            out.add(guideX);
        }
        if (bestDy != Integer.MAX_VALUE) {
            y = gridded.y() + bestDy;
            out.add(guideY);
        }
        return gridded.at(x, y);
    }

    /** Snaps the edges moved by a resize handle to the grid and to nearby edges. */
    private HudRect snapEdges(HudRect rect, Handle handle, List<SnapGuide> out) {
        if (!snapEnabled) {
            return rect;
        }
        int left = rect.x();
        int top = rect.y();
        int right = rect.right();
        int bottom = rect.bottom();
        String ignore = selectedId;
        if (handle.movesLeft()) {
            left = snapEdge(snapGrid(left), xTargets(ignore), true, out);
        }
        if (handle.movesRight()) {
            right = snapEdge(snapGrid(right), xTargets(ignore), true, out);
        }
        if (handle.movesTop()) {
            top = snapEdge(snapGrid(top), yTargets(ignore), false, out);
        }
        if (handle.movesBottom()) {
            bottom = snapEdge(snapGrid(bottom), yTargets(ignore), false, out);
        }
        if (right - left < 1) {
            right = left + 1;
        }
        if (bottom - top < 1) {
            bottom = top + 1;
        }
        return new HudRect(left, top, right - left, bottom - top);
    }

    private static int snapEdge(int value, List<Integer> targets, boolean vertical, List<SnapGuide> out) {
        int best = value;
        int bestDistance = EDGE_SNAP + 1;
        for (int target : targets) {
            int d = Math.abs(target - value);
            if (d < bestDistance) {
                bestDistance = d;
                best = target;
            }
        }
        if (best != value || bestDistance == 0) {
            out.add(new SnapGuide(vertical, best));
        }
        return best;
    }

    private List<Integer> xTargets(String ignoreId) {
        List<Integer> targets = new ArrayList<>();
        targets.add(0);
        targets.add(screenW);
        targets.add(screenW / 2);
        for (HudWidgetState other : layout.widgets()) {
            if (other.id().equals(ignoreId)) {
                continue;
            }
            HudRect r = layout.resolveRect(other, screenW, screenH);
            targets.add(r.x());
            targets.add(r.right());
        }
        return targets;
    }

    private List<Integer> yTargets(String ignoreId) {
        List<Integer> targets = new ArrayList<>();
        targets.add(0);
        targets.add(screenH);
        targets.add(screenH / 2);
        for (HudWidgetState other : layout.widgets()) {
            if (other.id().equals(ignoreId)) {
                continue;
            }
            HudRect r = layout.resolveRect(other, screenW, screenH);
            targets.add(r.y());
            targets.add(r.bottom());
        }
        return targets;
    }

    static int snapGrid(int value) {
        return (int) Math.round(value / (double) GRID) * GRID;
    }

    // ---- commands ------------------------------------------------------------------------------------------------

    /** Moves the selected widget by a pixel delta (arrow keys). */
    public boolean nudge(int dx, int dy) {
        Optional<HudWidgetState> selected = selected();
        if (selected.isEmpty() || !selected.get().type().movable()) {
            return false;
        }
        HudRect r = layout.resolveRect(selected.get(), screenW, screenH).translate(dx, dy).clampTo(screenW, screenH);
        HudWidgetState moved = HudLayout.reanchored(
                HudLayout.placed(selected.get(), r.x(), r.y(), screenW, screenH), screenW, screenH);
        return commit(layout.with(moved));
    }

    /** Duplicates the selected widget 8 px right/down and selects the copy. */
    public Optional<HudWidgetState> duplicate() {
        Optional<HudWidgetState> selected = selected();
        if (selected.isEmpty() || selected.get().type().alwaysCentered()) {
            return Optional.empty();
        }
        HudWidgetState source = selected.get();
        String id = layout.nextId(source.type());
        HudRect r = layout.resolveRect(source, screenW, screenH).translate(8, 8).clampTo(screenW, screenH);
        HudWidgetState copy = HudLayout.reanchored(HudLayout.placed(source.withId(id), r.x(), r.y(), screenW, screenH),
                screenW, screenH);
        commit(layout.with(copy));
        selectedId = id;
        fire();
        return Optional.of(copy);
    }

    /** Removes the selected widget (the crosshair can only be disabled, not removed). */
    public boolean delete() {
        Optional<HudWidgetState> selected = selected();
        if (selected.isEmpty()) {
            return false;
        }
        if (selected.get().type().alwaysCentered()) {
            return toggleEnabled();
        }
        boolean changed = commit(layout.without(selected.get().id()));
        selectedId = null;
        fire();
        return changed;
    }

    /** Toggles the enabled flag of the selected widget. */
    public boolean toggleEnabled() {
        return update(w -> w.withEnabled(!w.enabled()));
    }

    /** Resets scale, opacity, size and colours of the selected widget to the type defaults. */
    public boolean resetWidget() {
        return update(HudWidgetState::resetVisuals);
    }

    /** Applies a transformation to the selected widget as one undo step. */
    public boolean update(UnaryOperator<HudWidgetState> change) {
        Optional<HudWidgetState> selected = selected();
        if (selected.isEmpty()) {
            return false;
        }
        HudWidgetState next = change.apply(selected.get());
        if (next == null || !next.id().equals(selected.get().id())) {
            throw new IllegalArgumentException("update must keep the widget id");
        }
        return commit(layout.with(next));
    }

    /** Adds a widget of the type at its default anchor, moved clear of existing widgets, and selects it. */
    public HudWidgetState addWidget(HudWidgetType type) {
        String id = layout.nextId(type);
        HudWidgetState widget = layout.placedClear(HudWidgetState.defaults(id, type), screenW, screenH);
        commit(layout.with(widget));
        selectedId = id;
        fire();
        return widget;
    }

    /** Undoes the last step. */
    public boolean undo() {
        if (undo.isEmpty()) {
            return false;
        }
        cancelDrag();
        redo.push(layout);
        layout = undo.pop();
        if (selectedId != null && layout.find(selectedId).isEmpty()) {
            selectedId = null;
        }
        fire();
        return true;
    }

    /** Redoes the last undone step. */
    public boolean redo() {
        if (redo.isEmpty()) {
            return false;
        }
        cancelDrag();
        undo.push(layout);
        layout = redo.pop();
        if (selectedId != null && layout.find(selectedId).isEmpty()) {
            selectedId = null;
        }
        fire();
        return true;
    }

    private boolean commit(HudLayout next) {
        if (next.equals(layout)) {
            return false;
        }
        pushUndo();
        layout = next;
        fire();
        return true;
    }

    private void pushUndo() {
        recordUndo(layout);
    }

    private void recordUndo(HudLayout before) {
        undo.push(before);
        while (undo.size() > UNDO_DEPTH) {
            undo.removeLast();
        }
        redo.clear();
    }

    // ---- listeners -----------------------------------------------------------------------------------------------

    /** Called after every change of layout, selection, hover or drag state. */
    public Runnable onChange(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    private void fire() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
