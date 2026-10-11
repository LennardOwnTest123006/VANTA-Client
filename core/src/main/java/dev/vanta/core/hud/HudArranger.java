package dev.vanta.core.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic, overlap-free placement of HUD widgets: the Vanta Nexus action {@code hud.arrange} ("sort the HUD")
 * and the repair after an assistant turn left widgets on top of each other (from client 1.6.0 on).
 * <p>
 * Every visible movable widget is put into a <em>group</em>: one of the four corners or one of the four edge middles.
 * {@link Target#AUTO} keeps each widget in the group it is nearest to (a widget in the screen centre goes to the
 * corner of its quarter, so nothing ever covers the crosshair); a corner target puts all of them into that corner.
 * Each group is stacked from its corner or edge in a column with a {@value #GAP} px gap, wrapping to a next column when
 * the column is full; edge groups are centred along their edge. A widget that does not fit its group's columns (other
 * groups are in the way) goes to the free spot nearest to where its column would have put it. Sizes, scale and every
 * other attribute are kept; only anchor and offsets change, and hidden widgets and the crosshair are never touched.
 * <p>
 * {@link #arrange(HudLayout, Target, double)} first packs for the smallest screen VANTA supports
 * ({@value #COMPACT_WIDTH} x {@value #COMPACT_HEIGHT} GUI pixels): every widget then hangs on its corner or edge, so a
 * larger window only moves the groups apart and the HUD stays tidy at any size. When the widgets do not all fit there,
 * it packs for the reference screen ({@value #REFERENCE_WIDTH} x {@value #REFERENCE_HEIGHT}) instead.
 * <p>
 * Pure functions on immutable layouts; safe on any thread.
 */
public final class HudArranger {
    /** Space between stacked widgets and between columns. */
    public static final int GAP = 2;
    /** Distance kept from the screen edges. */
    public static final int MARGIN = 4;
    /** Reference screen (GUI pixels) the assistant places widgets on. */
    public static final int REFERENCE_WIDTH = 854;
    /** Reference screen height. */
    public static final int REFERENCE_HEIGHT = 480;
    /** Smallest screen VANTA lays out for (the screen reachability minimum). */
    public static final int COMPACT_WIDTH = 427;
    /** Smallest screen height. */
    public static final int COMPACT_HEIGHT = 240;

    /** Where {@code hud.arrange} puts the widgets. */
    public enum Target {
        /** Each widget stays in the corner or edge it is nearest to. */
        AUTO(null),
        TOP_LEFT(HudAnchor.TOP_LEFT),
        TOP_RIGHT(HudAnchor.TOP_RIGHT),
        BOTTOM_LEFT(HudAnchor.BOTTOM_LEFT),
        BOTTOM_RIGHT(HudAnchor.BOTTOM_RIGHT);

        private final HudAnchor anchor;

        Target(HudAnchor anchor) {
            this.anchor = anchor;
        }

        /** Lower-case id used by the action ({@code auto}, {@code top_left}, ...). */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The corner, empty for {@link #AUTO}. */
        public Optional<HudAnchor> anchor() {
            return Optional.ofNullable(anchor);
        }

        /** Finds a target by id (case-insensitive; spaces and dashes count as underscores). */
        public static Optional<Target> fromId(String id) {
            if (id == null) {
                return Optional.empty();
            }
            String wanted = id.trim().replace(' ', '_').replace('-', '_');
            for (Target target : values()) {
                if (target.id().equalsIgnoreCase(wanted)) {
                    return Optional.of(target);
                }
            }
            return Optional.empty();
        }

        /** Every id in declaration order. */
        public static List<String> ids() {
            List<String> out = new ArrayList<>();
            for (Target target : values()) {
                out.add(target.id());
            }
            return out;
        }
    }

    /**
     * What an arrangement did.
     *
     * @param layout       the new layout (equal to the input when nothing moved)
     * @param arranged     ids of the widgets that were placed, in placement order
     * @param moved        ids whose anchor or offsets changed
     * @param unplaced     ids that found no free spot and were left where they were
     * @param groups       the groups (their anchors) that were stacked
     * @param screenWidth  screen width the arrangement was computed for
     * @param screenHeight screen height the arrangement was computed for
     */
    public record Result(HudLayout layout, List<String> arranged, List<String> moved, List<String> unplaced,
                         List<HudAnchor> groups, int screenWidth, int screenHeight) {
        public Result {
            Objects.requireNonNull(layout, "layout");
            arranged = List.copyOf(arranged);
            moved = List.copyOf(moved);
            unplaced = List.copyOf(unplaced);
            groups = List.copyOf(groups);
        }

        /** True when every arranged widget found a spot. */
        public boolean complete() {
            return unplaced.isEmpty();
        }
    }

    /**
     * Group processing order: corners first, then the edge middles, then the centre (only the overlap repair uses it:
     * its widgets move to the nearest free spot one by one).
     */
    private static final List<HudAnchor> GROUP_ORDER = List.of(HudAnchor.TOP_LEFT, HudAnchor.TOP_RIGHT,
            HudAnchor.BOTTOM_LEFT, HudAnchor.BOTTOM_RIGHT, HudAnchor.TOP_CENTER, HudAnchor.BOTTOM_CENTER,
            HudAnchor.MIDDLE_LEFT, HudAnchor.MIDDLE_RIGHT, HudAnchor.CENTER);

    private HudArranger() {
    }

    // ---- public entry points ---------------------------------------------------------------------------------

    /**
     * Arranges every visible movable widget: packed for the compact screen when everything fits there, otherwise for
     * the reference screen.
     */
    public static Result arrange(HudLayout layout, Target target, double globalScale) {
        Result compact = arrange(layout, target, COMPACT_WIDTH, COMPACT_HEIGHT, globalScale);
        if (compact.complete()) {
            return compact;
        }
        return arrange(layout, target, REFERENCE_WIDTH, REFERENCE_HEIGHT, globalScale);
    }

    /**
     * Arranges every visible movable widget for one screen size. Groups are chosen from the widgets' positions on the
     * reference screen (where the player and the assistant place them), so the grouping does not depend on the size.
     */
    public static Result arrange(HudLayout layout, Target target, int screenW, int screenH, double globalScale) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(target, "target");
        List<HudWidgetState> widgets = packable(layout);
        Map<HudAnchor, List<HudWidgetState>> groups = new EnumMap<>(HudAnchor.class);
        for (HudWidgetState widget : widgets) {
            HudAnchor group = target.anchor().orElseGet(() -> cornerOrEdge(layout, widget, REFERENCE_WIDTH,
                    REFERENCE_HEIGHT, globalScale));
            groups.computeIfAbsent(group, g -> new ArrayList<>()).add(widget);
        }
        List<HudRect> obstacles = new ArrayList<>();
        obstacles.add(centreReserve(layout, screenW, screenH));
        return pack(layout, groups, obstacles, Set.of(), screenW, screenH, globalScale);
    }

    /**
     * Repairs overlaps the given widgets are involved in, on the reference screen: every corner or edge group
     * containing a changed widget that overlaps another visible widget is stacked again with the same packing as
     * {@link #arrange}; every other widget stays exactly where it is and only acts as an obstacle. Within a group the
     * changed widgets win ties, so a widget moved onto another one keeps the requested spot. A changed widget in the
     * middle of the screen that overlaps something (or the crosshair) only moves to the nearest free spot.
     *
     * @return empty when nothing overlaps
     */
    public static Optional<Result> resolveOverlaps(HudLayout layout, Collection<String> changedIds,
                                                   double globalScale) {
        return resolveOverlaps(layout, changedIds, REFERENCE_WIDTH, REFERENCE_HEIGHT, globalScale);
    }

    /** {@link #resolveOverlaps(HudLayout, Collection, double)} for an explicit screen size. */
    public static Optional<Result> resolveOverlaps(HudLayout layout, Collection<String> changedIds, int screenW,
                                                   int screenH, double globalScale) {
        Objects.requireNonNull(layout, "layout");
        Set<String> changed = new HashSet<>(changedIds);
        List<HudWidgetState> widgets = packable(layout);
        Map<String, HudRect> rects = new LinkedHashMap<>();
        Map<String, HudAnchor> groupOf = new LinkedHashMap<>();
        for (HudWidgetState widget : widgets) {
            rects.put(widget.id(), layout.resolveRect(widget, screenW, screenH, globalScale));
            groupOf.put(widget.id(), HudAnchor.nearest(rects.get(widget.id()), screenW, screenH));
        }
        HudRect centre = centreReserve(layout, screenW, screenH);
        Set<HudAnchor> dirty = new LinkedHashSet<>();
        for (HudWidgetState widget : widgets) {
            if (!changed.contains(widget.id())) {
                continue;
            }
            HudRect rect = rects.get(widget.id());
            boolean conflict = groupOf.get(widget.id()) == HudAnchor.CENTER && rect.intersects(centre);
            for (HudWidgetState other : widgets) {
                if (!other.id().equals(widget.id()) && rects.get(other.id()).intersects(rect)) {
                    conflict = true;
                    dirty.add(groupOf.get(other.id()));
                }
            }
            if (conflict) {
                dirty.add(groupOf.get(widget.id()));
            }
        }
        if (dirty.isEmpty()) {
            return Optional.empty();
        }
        Map<HudAnchor, List<HudWidgetState>> groups = new EnumMap<>(HudAnchor.class);
        List<HudRect> obstacles = new ArrayList<>();
        obstacles.add(centre);
        for (HudWidgetState widget : widgets) {
            HudAnchor group = groupOf.get(widget.id());
            if (dirty.contains(group)) {
                groups.computeIfAbsent(group, g -> new ArrayList<>()).add(widget);
            } else {
                obstacles.add(rects.get(widget.id()));
            }
        }
        return Optional.of(pack(layout, groups, obstacles, changed, screenW, screenH, globalScale));
    }

    /**
     * Pairs of visible movable widgets whose rectangles overlap on the given screen, as {@code "a+b"} in layout order.
     * Used by the tests and the receipts.
     */
    public static List<String> overlaps(HudLayout layout, int screenW, int screenH, double globalScale) {
        List<HudWidgetState> widgets = packable(layout);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < widgets.size(); i++) {
            HudRect a = layout.resolveRect(widgets.get(i), screenW, screenH, globalScale);
            for (int j = i + 1; j < widgets.size(); j++) {
                HudRect b = layout.resolveRect(widgets.get(j), screenW, screenH, globalScale);
                if (a.intersects(b)) {
                    out.add(widgets.get(i).id() + "+" + widgets.get(j).id());
                }
            }
        }
        return out;
    }

    // ---- grouping ----------------------------------------------------------------------------------------------

    /** Visible widgets that can move (the crosshair and hidden widgets are never arranged). */
    static List<HudWidgetState> packable(HudLayout layout) {
        List<HudWidgetState> out = new ArrayList<>();
        for (HudWidgetState widget : layout.widgets()) {
            if (widget.enabled() && widget.type().movable()) {
                out.add(widget);
            }
        }
        return out;
    }

    /** The corner or edge a widget is nearest to; the centre maps to the corner of the widget's quarter. */
    private static HudAnchor cornerOrEdge(HudLayout layout, HudWidgetState widget, int screenW, int screenH,
                                          double globalScale) {
        HudRect rect = layout.resolveRect(widget, screenW, screenH, globalScale);
        HudAnchor nearest = HudAnchor.nearest(rect, screenW, screenH);
        if (nearest != HudAnchor.CENTER) {
            return nearest;
        }
        boolean left = rect.centerX() * 2 < screenW;
        boolean top = rect.centerY() * 2 < screenH;
        if (top) {
            return left ? HudAnchor.TOP_LEFT : HudAnchor.TOP_RIGHT;
        }
        return left ? HudAnchor.BOTTOM_LEFT : HudAnchor.BOTTOM_RIGHT;
    }

    /** The crosshair's square (vanilla or VANTA, both sit in the centre), grown by the gap. */
    private static HudRect centreReserve(HudLayout layout, int screenW, int screenH) {
        int size = HudWidgetType.CROSSHAIR.defaultHeight();
        for (HudWidgetState widget : layout.byType(HudWidgetType.CROSSHAIR)) {
            size = Math.max(size, Math.max(widget.scaledWidth(), widget.scaledHeight()));
        }
        return new HudRect((screenW - size) / 2, (screenH - size) / 2, size, size).grow(GAP);
    }

    // ---- packing -----------------------------------------------------------------------------------------------

    /** A widget being placed, with its drawn size on the target screen. */
    private record Item(HudWidgetState widget, int index, int w, int h, HudRect before, boolean changed) {
    }

    private static Result pack(HudLayout layout, Map<HudAnchor, List<HudWidgetState>> groups,
                               List<HudRect> obstacles, Set<String> changed, int screenW, int screenH,
                               double globalScale) {
        List<HudRect> placed = new ArrayList<>(obstacles);
        HudLayout result = layout;
        List<String> arranged = new ArrayList<>();
        List<String> moved = new ArrayList<>();
        List<String> unplaced = new ArrayList<>();
        List<HudAnchor> stacked = new ArrayList<>();
        for (HudAnchor group : GROUP_ORDER) {
            List<HudWidgetState> members = groups.get(group);
            if (members == null || members.isEmpty()) {
                continue;
            }
            stacked.add(group);
            List<Item> items = new ArrayList<>();
            for (HudWidgetState widget : members) {
                // Order and the fallback start from where the widget is now, measured on the reference screen.
                HudRect now = layout.resolveRect(widget, REFERENCE_WIDTH, REFERENCE_HEIGHT, globalScale);
                int w = Math.max(1, (int) Math.round(widget.scaledWidth() * Math.max(0.1, globalScale)));
                int h = Math.max(1, (int) Math.round(widget.scaledHeight() * Math.max(0.1, globalScale)));
                items.add(new Item(widget, layout.indexOf(widget.id()), w, h, now, changed.contains(widget.id())));
            }
            items.sort(order(group));
            List<HudRect> preferred = preferredRects(group, items, placed, screenW, screenH);
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                HudRect want = preferred.get(i);
                HudRect start = want != null ? want : scaledTo(item.before(), screenW, screenH);
                HudRect spot = want != null && fits(want, placed, screenW, screenH) ? want
                        : nearestFree(start, placed, screenW, screenH).orElse(null);
                if (spot == null) {
                    unplaced.add(item.widget().id());
                    continue;
                }
                placed.add(spot);
                HudAnchor anchor = spot.equals(want) ? group : HudAnchor.nearest(spot, screenW, screenH);
                HudWidgetState next = item.widget().withPosition(anchor,
                        anchor.offsetXFor(screenW, item.w(), spot.x()), anchor.offsetYFor(screenH, item.h(), spot.y()));
                arranged.add(next.id());
                if (!next.equals(item.widget())) {
                    moved.add(next.id());
                    result = result.with(next);
                }
            }
        }
        return new Result(result, arranged, moved, unplaced, stacked, screenW, screenH);
    }

    /**
     * Stacking order inside a group: the widgets' top-to-bottom order on the screen (bottom-to-top for a bottom group,
     * which stacks upwards), then changed widgets first, then layout order. The screen order is read from the anchors,
     * not from pixel positions, so widgets of different sizes on one spot keep their layout order: first the widgets
     * anchored at the group's own edge by their distance from it, then the middle row, then the far edge.
     */
    private static Comparator<Item> order(HudAnchor group) {
        boolean bottom = group.row() == 2;
        Comparator<Item> band = Comparator.comparingInt(item -> bottom ? 2 - item.widget().anchor().row()
                : item.widget().anchor().row());
        Comparator<Item> along = Comparator.comparingInt(item -> {
            int offset = item.widget().offsetY();
            int downwards = item.widget().anchor().row() == 2 ? -offset : offset;
            return bottom ? -downwards : downwards;
        });
        return band.thenComparing(along).thenComparing(item -> !item.changed()).thenComparingInt(Item::index);
    }

    /**
     * The column positions of a group's items in screen coordinates ({@code null} where the group has no room left).
     * Corner groups skip over obstacles inside their columns; edge groups are laid out as a centred block and checked
     * afterwards.
     */
    private static List<HudRect> preferredRects(HudAnchor group, List<Item> items, List<HudRect> obstacles,
                                                int screenW, int screenH) {
        if (group == HudAnchor.CENTER) {
            List<HudRect> none = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                none.add(null);
            }
            return none;
        }
        boolean corner = group.column() != 1 && group.row() != 1;
        if (corner) {
            return cornerColumns(group, items, obstacles, screenW, screenH);
        }
        return edgeBlock(group, items, screenW, screenH);
    }

    /** Corner stacking in "corner space" (the corner at the origin, columns growing away from it). */
    private static List<HudRect> cornerColumns(HudAnchor group, List<Item> items, List<HudRect> obstacles,
                                               int screenW, int screenH) {
        boolean flipX = group.column() == 2;
        boolean flipY = group.row() == 2;
        List<HudRect> local = new ArrayList<>();
        for (HudRect o : obstacles) {
            local.add(mirror(o, flipX, flipY, screenW, screenH));
        }
        List<HudRect> out = new ArrayList<>();
        int colX = MARGIN;
        int colWidth = 0;
        int cursorY = MARGIN;
        for (Item item : items) {
            HudRect spot = null;
            while (colX + item.w() <= screenW - MARGIN) {
                OptionalSpot y = firstFreeY(colX, cursorY, item.w(), item.h(), local, screenH);
                if (y.found) {
                    spot = new HudRect(colX, y.value, item.w(), item.h());
                    break;
                }
                // The column is full: the next one starts after the widest widget of this column, or after whatever
                // blocks this column when nothing of the group is in it yet.
                int next = colWidth > 0 ? colX + colWidth + GAP : skipPast(colX, item.w(), local);
                if (next <= colX) {
                    break;
                }
                colX = next;
                colWidth = 0;
                cursorY = MARGIN;
            }
            if (spot == null) {
                out.add(null);
                continue;
            }
            local.add(spot);
            colWidth = Math.max(colWidth, item.w());
            cursorY = spot.bottom() + GAP;
            out.add(mirror(spot, flipX, flipY, screenW, screenH));
        }
        return out;
    }

    /** First column start right of the obstacles that cover the strip {@code [x, x + w)}; {@code x} when none. */
    private static int skipPast(int x, int w, List<HudRect> obstacles) {
        int best = Integer.MAX_VALUE;
        for (HudRect o : obstacles) {
            if (o.x() < x + w && x < o.right() && o.right() + GAP > x) {
                best = Math.min(best, o.right() + GAP);
            }
        }
        return best == Integer.MAX_VALUE ? x : best;
    }

    /** Result of a vertical scan. */
    private record OptionalSpot(boolean found, int value) {
    }

    /** Smallest y at or below {@code fromY} where a {@code w x h} rectangle at {@code x} is free and on screen. */
    private static OptionalSpot firstFreeY(int x, int fromY, int w, int h, List<HudRect> obstacles, int screenH) {
        List<Integer> candidates = new ArrayList<>();
        candidates.add(fromY);
        for (HudRect o : obstacles) {
            int below = o.bottom() + GAP;
            if (below > fromY) {
                candidates.add(below);
            }
        }
        candidates.sort(Integer::compare);
        for (int y : candidates) {
            if (y + h > screenH - MARGIN) {
                break;
            }
            HudRect rect = new HudRect(x, y, w, h);
            if (free(rect, obstacles)) {
                return new OptionalSpot(true, y);
            }
        }
        return new OptionalSpot(false, 0);
    }

    /**
     * Edge groups: columns of at most the available length, side by side, the whole block centred along the edge
     * (top and bottom middles) or each column centred on the side (left and right middles).
     */
    private static List<HudRect> edgeBlock(HudAnchor group, List<Item> items, int screenW, int screenH) {
        boolean horizontalEdge = group.column() == 1; // top_center, bottom_center
        // A top or bottom middle column stops short of the crosshair; a side column may use the whole height.
        int available = horizontalEdge ? screenH / 2 - MARGIN - HudWidgetType.CROSSHAIR.defaultHeight()
                : screenH - 2 * MARGIN;
        List<List<Item>> columns = new ArrayList<>();
        List<Item> current = new ArrayList<>();
        int used = 0;
        for (Item item : items) {
            int need = current.isEmpty() ? item.h() : used + GAP + item.h();
            if (!current.isEmpty() && need > available) {
                columns.add(current);
                current = new ArrayList<>();
                need = item.h();
            }
            current.add(item);
            used = need;
        }
        if (!current.isEmpty()) {
            columns.add(current);
        }
        int[] widths = new int[columns.size()];
        int total = 0;
        for (int c = 0; c < columns.size(); c++) {
            for (Item item : columns.get(c)) {
                widths[c] = Math.max(widths[c], item.w());
            }
            total += widths[c] + (c > 0 ? GAP : 0);
        }
        Map<Item, HudRect> spots = new LinkedHashMap<>();
        int x;
        if (horizontalEdge) {
            x = (screenW - total) / 2;
        } else {
            x = MARGIN;
        }
        for (int c = 0; c < columns.size(); c++) {
            List<Item> column = columns.get(c);
            int height = 0;
            for (Item item : column) {
                height += item.h() + (height > 0 ? GAP : 0);
            }
            int y = group.row() == 1 ? (screenH - height) / 2 : MARGIN;
            for (Item item : column) {
                int left = horizontalEdge ? x + (widths[c] - item.w()) / 2 : x;
                HudRect local = new HudRect(left, y, item.w(), item.h());
                spots.put(item, mirror(local, group.column() == 2, group.row() == 2, screenW, screenH));
                y += item.h() + GAP;
            }
            x += widths[c] + GAP;
        }
        List<HudRect> out = new ArrayList<>();
        for (Item item : items) {
            out.add(spots.get(item));
        }
        return out;
    }

    /** Mirrors a rectangle into (or out of) corner space. */
    private static HudRect mirror(HudRect r, boolean flipX, boolean flipY, int screenW, int screenH) {
        int x = flipX ? screenW - r.right() : r.x();
        int y = flipY ? screenH - r.bottom() : r.y();
        return new HudRect(x, y, r.width(), r.height());
    }

    // ---- free-spot search --------------------------------------------------------------------------------------

    /** True when the rectangle lies inside the screen and keeps the gap to every placed rectangle. */
    private static boolean fits(HudRect rect, List<HudRect> placed, int screenW, int screenH) {
        return rect.x() >= 0 && rect.y() >= 0 && rect.right() <= screenW && rect.bottom() <= screenH
                && free(rect, placed);
    }

    private static boolean free(HudRect rect, List<HudRect> placed) {
        for (HudRect other : placed) {
            if (other.grow(GAP).intersects(rect)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The free spot nearest to {@code want} (Manhattan distance; ties go to the higher, then the more left spot).
     * Candidates are the screen margins and the positions just beside every placed rectangle, which is where the
     * nearest free spot of an axis-aligned packing always lies.
     */
    private static Optional<HudRect> nearestFree(HudRect want, List<HudRect> placed, int screenW, int screenH) {
        int w = want.width();
        int h = want.height();
        if (w > screenW || h > screenH) {
            return Optional.empty();
        }
        Set<Integer> xs = new LinkedHashSet<>();
        Set<Integer> ys = new LinkedHashSet<>();
        xs.add(clampTo(want.x(), w, screenW));
        ys.add(clampTo(want.y(), h, screenH));
        xs.add(Math.min(MARGIN, screenW - w));
        xs.add(Math.max(0, screenW - w - MARGIN));
        ys.add(Math.min(MARGIN, screenH - h));
        ys.add(Math.max(0, screenH - h - MARGIN));
        for (HudRect o : placed) {
            xs.add(o.right() + GAP);
            xs.add(o.x() - GAP - w);
            ys.add(o.bottom() + GAP);
            ys.add(o.y() - GAP - h);
        }
        HudRect best = null;
        long bestScore = Long.MAX_VALUE;
        for (int y : ys) {
            for (int x : xs) {
                HudRect rect = new HudRect(x, y, w, h);
                if (!fits(rect, placed, screenW, screenH)) {
                    continue;
                }
                long score = (long) Math.abs(x - want.x()) + Math.abs(y - want.y());
                if (best == null || score < bestScore || (score == bestScore
                        && (y < best.y() || (y == best.y() && x < best.x())))) {
                    best = rect;
                    bestScore = score;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** A reference-screen rectangle moved proportionally onto the target screen (start of a free-spot search). */
    private static HudRect scaledTo(HudRect reference, int screenW, int screenH) {
        if (screenW == REFERENCE_WIDTH && screenH == REFERENCE_HEIGHT) {
            return reference;
        }
        int x = (int) Math.round(reference.x() * (double) screenW / REFERENCE_WIDTH);
        int y = (int) Math.round(reference.y() * (double) screenH / REFERENCE_HEIGHT);
        return new HudRect(x, y, reference.width(), reference.height());
    }

    private static int clampTo(int position, int size, int screen) {
        return Math.max(0, Math.min(position, screen - size));
    }
}
