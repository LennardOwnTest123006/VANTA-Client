package dev.vanta.core.hud;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;
import java.util.Optional;

/**
 * The nine screen anchors a HUD widget can attach to. Offsets are measured in "anchor space": from the anchored edge
 * towards the screen centre, so a widget anchored top-right with offset (4, 4) keeps hugging the corner at any
 * resolution.
 */
public enum HudAnchor implements LangKeyed {
    TOP_LEFT(0, 0),
    TOP_CENTER(1, 0),
    TOP_RIGHT(2, 0),
    MIDDLE_LEFT(0, 1),
    CENTER(1, 1),
    MIDDLE_RIGHT(2, 1),
    BOTTOM_LEFT(0, 2),
    BOTTOM_CENTER(1, 2),
    BOTTOM_RIGHT(2, 2);

    private final int column;
    private final int row;

    HudAnchor(int column, int row) {
        this.column = column;
        this.row = row;
    }

    /** 0 = left, 1 = centre, 2 = right. */
    public int column() {
        return column;
    }

    /** 0 = top, 1 = middle, 2 = bottom. */
    public int row() {
        return row;
    }

    /** Screen x for a widget of {@code widgetW} pixels with the given anchor-space offset. */
    public int resolveX(int screenW, int widgetW, int offsetX) {
        return resolve(column, screenW, widgetW, offsetX);
    }

    /** Screen y for a widget of {@code widgetH} pixels with the given anchor-space offset. */
    public int resolveY(int screenH, int widgetH, int offsetY) {
        return resolve(row, screenH, widgetH, offsetY);
    }

    /** Inverse of {@link #resolveX}: anchor-space offset that places the widget at screen {@code x}. */
    public int offsetXFor(int screenW, int widgetW, int x) {
        return offsetFor(column, screenW, widgetW, x);
    }

    /** Inverse of {@link #resolveY}. */
    public int offsetYFor(int screenH, int widgetH, int y) {
        return offsetFor(row, screenH, widgetH, y);
    }

    private static int resolve(int index, int screen, int widget, int offset) {
        return switch (index) {
            case 0 -> offset;
            case 2 -> screen - widget - offset;
            default -> (screen - widget) / 2 + offset;
        };
    }

    private static int offsetFor(int index, int screen, int widget, int position) {
        return switch (index) {
            case 0 -> position;
            case 2 -> screen - widget - position;
            default -> position - (screen - widget) / 2;
        };
    }

    /** Anchor whose screen third contains the rectangle's centre. */
    public static HudAnchor nearest(HudRect rect, int screenW, int screenH) {
        int column = third(rect.centerX(), screenW);
        int row = third(rect.centerY(), screenH);
        for (HudAnchor anchor : values()) {
            if (anchor.column == column && anchor.row == row) {
                return anchor;
            }
        }
        return TOP_LEFT;
    }

    private static int third(int position, int extent) {
        if (extent <= 0) {
            return 0;
        }
        if (position * 3 < extent) {
            return 0;
        }
        return position * 3 < extent * 2 ? 1 : 2;
    }

    /** Lower-case id used in JSON. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.hud.anchor." + id();
    }

    /** Finds an anchor by id (case-insensitive). */
    public static Optional<HudAnchor> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (HudAnchor anchor : values()) {
            if (anchor.id().equalsIgnoreCase(id) || anchor.name().equalsIgnoreCase(id)) {
                return Optional.of(anchor);
            }
        }
        return Optional.empty();
    }
}
