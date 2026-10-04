package dev.vanta.core.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable ordered list of widgets plus the anchor math that places them on a screen of any size.
 * Later widgets draw on top of earlier ones.
 *
 * @param widgets widgets in draw order; ids are unique
 */
public record HudLayout(List<HudWidgetState> widgets) {
    /** Layout without widgets. */
    public static final HudLayout EMPTY = new HudLayout(List.of());

    public HudLayout {
        Objects.requireNonNull(widgets, "widgets");
        List<HudWidgetState> copy = new ArrayList<>(widgets.size());
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (HudWidgetState widget : widgets) {
            if (seen.putIfAbsent(widget.id(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Duplicate widget id in layout: " + widget.id());
            }
            copy.add(widget);
        }
        widgets = List.copyOf(copy);
    }

    /** Finds a widget by id. */
    public Optional<HudWidgetState> find(String id) {
        for (HudWidgetState widget : widgets) {
            if (widget.id().equals(id)) {
                return Optional.of(widget);
            }
        }
        return Optional.empty();
    }

    /** All widgets of a type. */
    public List<HudWidgetState> byType(HudWidgetType type) {
        List<HudWidgetState> out = new ArrayList<>();
        for (HudWidgetState widget : widgets) {
            if (widget.type() == type) {
                out.add(widget);
            }
        }
        return out;
    }

    /** Enabled widgets in draw order. */
    public List<HudWidgetState> enabled() {
        List<HudWidgetState> out = new ArrayList<>();
        for (HudWidgetState widget : widgets) {
            if (widget.enabled()) {
                out.add(widget);
            }
        }
        return out;
    }

    /** Index of a widget id, or -1. */
    public int indexOf(String id) {
        for (int i = 0; i < widgets.size(); i++) {
            if (widgets.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** Copy with the widget replaced (same id) or appended (new id). */
    public HudLayout with(HudWidgetState widget) {
        List<HudWidgetState> next = new ArrayList<>(widgets);
        int index = indexOf(widget.id());
        if (index >= 0) {
            next.set(index, widget);
        } else {
            next.add(widget);
        }
        return new HudLayout(next);
    }

    /** Copy without the widget. */
    public HudLayout without(String id) {
        List<HudWidgetState> next = new ArrayList<>(widgets);
        next.removeIf(w -> w.id().equals(id));
        return new HudLayout(next);
    }

    /** Copy with the widget moved to the end (drawn on top). */
    public HudLayout bringToFront(String id) {
        int index = indexOf(id);
        if (index < 0 || index == widgets.size() - 1) {
            return this;
        }
        List<HudWidgetState> next = new ArrayList<>(widgets);
        HudWidgetState widget = next.remove(index);
        next.add(widget);
        return new HudLayout(next);
    }

    /** A free id for a new widget of {@code type}: {@code fps}, then {@code fps-2}, {@code fps-3}, … */
    public String nextId(HudWidgetType type) {
        String base = type.id();
        if (find(base).isEmpty()) {
            return base;
        }
        int n = 2;
        while (find(base + "-" + n).isPresent()) {
            n++;
        }
        return base + "-" + n;
    }

    /** Screen rectangle of a widget without a global scale. */
    public HudRect resolveRect(HudWidgetState widget, int screenW, int screenH) {
        return resolveRect(widget, screenW, screenH, 1.0);
    }

    /**
     * Screen rectangle of a widget: scaled size, anchor math, then clamped into the screen. Always-centred widgets
     * ignore anchor and offsets.
     */
    public HudRect resolveRect(HudWidgetState widget, int screenW, int screenH, double globalScale) {
        double scale = Math.max(0.1, globalScale);
        int w = Math.max(1, (int) Math.round(widget.scaledWidth() * scale));
        int h = Math.max(1, (int) Math.round(widget.scaledHeight() * scale));
        int x;
        int y;
        if (widget.type().alwaysCentered()) {
            x = (screenW - w) / 2;
            y = (screenH - h) / 2;
        } else {
            x = widget.anchor().resolveX(screenW, w, widget.offsetX());
            y = widget.anchor().resolveY(screenH, h, widget.offsetY());
        }
        return new HudRect(x, y, w, h).clampTo(screenW, screenH);
    }

    /** Rectangles of every widget keyed by id. */
    public Map<String, HudRect> resolveAll(int screenW, int screenH, double globalScale) {
        Map<String, HudRect> out = new LinkedHashMap<>();
        for (HudWidgetState widget : widgets) {
            out.put(widget.id(), resolveRect(widget, screenW, screenH, globalScale));
        }
        return out;
    }

    /**
     * Copy of {@code widget} positioned so that its rectangle is at {@code (x, y)} on the given screen, keeping the
     * current anchor.
     */
    public static HudWidgetState placed(HudWidgetState widget, int x, int y, int screenW, int screenH) {
        if (widget.type().alwaysCentered()) {
            return widget;
        }
        int w = widget.scaledWidth();
        int h = widget.scaledHeight();
        return widget.withPosition(widget.anchor(), widget.anchor().offsetXFor(screenW, w, x),
                widget.anchor().offsetYFor(screenH, h, y));
    }

    /**
     * Copy of {@code widget} moved along its anchor's stacking direction (downwards from top and middle anchors,
     * upwards from bottom anchors) until it no longer overlaps an enabled widget of this layout. Used when a widget
     * is added so it never lands on top of another one. When the screen offers no free slot the widget is returned
     * unchanged.
     */
    public HudWidgetState placedClear(HudWidgetState widget, int screenW, int screenH) {
        Objects.requireNonNull(widget, "widget");
        if (widget.type().alwaysCentered()) {
            return widget;
        }
        boolean upwards = widget.anchor().row() == 2;
        HudWidgetState candidate = widget;
        for (int attempt = 0; attempt < 64; attempt++) {
            HudRect rect = resolveRect(candidate, screenW, screenH);
            HudRect blocker = null;
            for (HudWidgetState other : widgets) {
                if (other.id().equals(candidate.id()) || !other.enabled() || other.type().alwaysCentered()) {
                    continue;
                }
                HudRect otherRect = resolveRect(other, screenW, screenH);
                if (otherRect.intersects(rect) && (blocker == null
                        || (upwards ? otherRect.y() < blocker.y() : otherRect.bottom() > blocker.bottom()))) {
                    blocker = otherRect;
                }
            }
            if (blocker == null) {
                return candidate;
            }
            int gap = 2;
            int y = upwards ? blocker.y() - gap - rect.height() : blocker.bottom() + gap;
            if (y < 0 || y + rect.height() > screenH) {
                return widget;
            }
            candidate = placed(candidate, rect.x(), y, screenW, screenH);
        }
        return widget;
    }

    /** Copy of {@code widget} re-anchored to the screen third its rectangle is in, keeping the same position. */
    public static HudWidgetState reanchored(HudWidgetState widget, int screenW, int screenH) {
        if (widget.type().alwaysCentered()) {
            return widget;
        }
        int w = widget.scaledWidth();
        int h = widget.scaledHeight();
        int x = widget.anchor().resolveX(screenW, w, widget.offsetX());
        int y = widget.anchor().resolveY(screenH, h, widget.offsetY());
        HudAnchor anchor = HudAnchor.nearest(new HudRect(x, y, w, h), screenW, screenH);
        return widget.withPosition(anchor, anchor.offsetXFor(screenW, w, x), anchor.offsetYFor(screenH, h, y));
    }

    /** Number of widgets. */
    public int size() {
        return widgets.size();
    }

    /** True when no widgets exist. */
    public boolean isEmpty() {
        return widgets.isEmpty();
    }
}
