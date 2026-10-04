package dev.vanta.core.hud.editor;

import dev.vanta.core.hud.HudAnchor;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

import java.util.function.Consumer;

/**
 * 3x3 grid of the nine {@link HudAnchor}s. Click a cell or move with the arrow keys while focused; the selected
 * cell glows with the accent gradient and the tooltip names the hovered anchor.
 */
public final class AnchorPicker extends UiNode {

    /** Side of one cell. */
    public static final int CELL = 10;
    /** Gap between cells. */
    public static final int GAP = 2;
    /** Total side length. */
    public static final int SIDE = CELL * 3 + GAP * 2;

    private HudAnchor value;
    private Consumer<HudAnchor> onChange;

    /** Picker starting at {@code initial}. */
    public AnchorPicker(HudAnchor initial, Consumer<HudAnchor> onChange) {
        this.value = initial == null ? HudAnchor.TOP_LEFT : initial;
        this.onChange = onChange;
        setFocusable(true);
    }

    /** Current anchor. */
    public HudAnchor value() {
        return value;
    }

    /** Sets the anchor without firing the listener. */
    public AnchorPicker setValue(HudAnchor anchor) {
        if (anchor != null) {
            this.value = anchor;
        }
        return this;
    }

    public AnchorPicker onChange(Consumer<HudAnchor> listener) {
        this.onChange = listener;
        return this;
    }

    /** Selects an anchor and fires the listener when it changed. */
    public void choose(UiContext ctx, HudAnchor anchor) {
        if (anchor == null || anchor == value || !isEffectivelyEnabled()) {
            return;
        }
        value = anchor;
        ctx.playClick();
        if (onChange != null) {
            onChange.accept(anchor);
        }
    }

    /** Rectangle of the cell for an anchor. */
    public Rect cellRect(HudAnchor anchor) {
        Rect b = bounds();
        int x = b.x() + anchor.column() * (CELL + GAP);
        int y = b.y() + anchor.row() * (CELL + GAP);
        return new Rect(x, y, CELL, CELL);
    }

    private HudAnchor anchorAt(double x, double y) {
        for (HudAnchor anchor : HudAnchor.values()) {
            if (cellRect(anchor).contains(x, y)) {
                return anchor;
            }
        }
        return null;
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(SIDE, SIDE);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        boolean enabled = isEffectivelyEnabled();
        HudAnchor hovered = isHovered() ? anchorAt(ctx.mouseX(), ctx.mouseY()) : null;
        setTooltip(hovered != null ? Lang.tr(hovered.langKey()) : null);
        for (HudAnchor anchor : HudAnchor.values()) {
            Rect c = cellRect(anchor);
            if (anchor == value) {
                canvas.fillRoundedGradientH(c.x(), c.y(), c.w(), c.h(), Theme.RADIUS_SM, theme.gradientStart(),
                        theme.gradientEnd());
                canvas.fill(c.x() + 3, c.y() + 3, c.w() - 6, c.h() - 6, Colors.withAlpha(Colors.WHITE, 0.85f));
            } else {
                int bg = anchor == hovered && enabled ? theme.surface4() : theme.surface3();
                canvas.fillRounded(c.x(), c.y(), c.w(), c.h(), Theme.RADIUS_SM, bg);
                canvas.strokeRounded(c.x(), c.y(), c.w(), c.h(), Theme.RADIUS_SM, theme.borderStrong());
                canvas.fill(c.x() + 4, c.y() + 4, c.w() - 8, c.h() - 8,
                        Colors.withAlpha(theme.textMuted(), anchor == hovered ? 1f : 0.7f));
            }
        }
        if (isFocused()) {
            Rect b = bounds();
            canvas.strokeRounded(b.x() - 2, b.y() - 2, b.w() + 4, b.h() + 4, Theme.RADIUS_MD,
                    Colors.withAlpha(theme.borderFocus(), 0.9f));
        }
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (button != Keys.MOUSE_LEFT || !isEffectivelyEnabled()) {
            return false;
        }
        requestFocus(ctx);
        HudAnchor hit = anchorAt(x, y);
        if (hit != null) {
            choose(ctx, hit);
        }
        return true;
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        int column = value.column();
        int row = value.row();
        switch (key) {
            case Keys.LEFT -> column = Math.max(0, column - 1);
            case Keys.RIGHT -> column = Math.min(2, column + 1);
            case Keys.UP -> row = Math.max(0, row - 1);
            case Keys.DOWN -> row = Math.min(2, row + 1);
            default -> {
                return false;
            }
        }
        for (HudAnchor anchor : HudAnchor.values()) {
            if (anchor.column() == column && anchor.row() == row) {
                choose(ctx, anchor);
                return true;
            }
        }
        return true;
    }
}
