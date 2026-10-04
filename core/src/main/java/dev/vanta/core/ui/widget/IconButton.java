package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.AnimatedValue;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

import java.util.Objects;

/**
 * Square button showing only an icon. Ghost by default (surface appears on hover); {@link #active(boolean)}
 * shows a persistent selected state (used by rails and toolbars). Set a tooltip for accessibility.
 */
public class IconButton extends UiNode {

    /** Default side length. */
    public static final int SIZE = 20;

    private Icons icon;
    private int side = SIZE;
    private int iconSize = 10;
    private Runnable onClick;
    private boolean active;
    private boolean outlined;
    private Integer iconColor;
    private AnimatedValue hoverT;

    /** Ghost icon button. */
    public IconButton(Icons icon, Runnable onClick) {
        this.icon = Objects.requireNonNull(icon, "icon");
        this.onClick = onClick;
        setFocusable(true);
    }

    public IconButton icon(Icons newIcon) {
        this.icon = Objects.requireNonNull(newIcon);
        return this;
    }

    public Icons icon() {
        return icon;
    }

    /** Side length and icon size. */
    public IconButton sizes(int sideLength, int glyphSize) {
        this.side = Math.max(8, sideLength);
        this.iconSize = Math.max(4, glyphSize);
        return this;
    }

    /** Persistent selected look (accent tint). */
    public IconButton active(boolean on) {
        this.active = on;
        return this;
    }

    public boolean isActive() {
        return active;
    }

    /** Always draw the surface + border (secondary look) instead of ghost. */
    public IconButton outlined(boolean on) {
        this.outlined = on;
        return this;
    }

    /** Fixed icon color (defaults to secondary → primary on hover). */
    public IconButton iconColor(Integer argb) {
        this.iconColor = argb;
        return this;
    }

    public IconButton onClick(Runnable action) {
        this.onClick = action;
        return this;
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(side, side);
    }

    /** Fires the action. */
    public void click(UiContext ctx) {
        if (!isEffectivelyEnabled()) {
            return;
        }
        ctx.playClick();
        if (onClick != null) {
            onClick.run();
        }
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        if (hoverT == null) {
            hoverT = ctx.animator().value(0f);
        }
        Theme theme = ctx.theme();
        Rect b = bounds();
        boolean enabled = isEffectivelyEnabled();
        hoverT.animateTo(enabled && isHovered(), Theme.MOTION_FAST);
        float hover = hoverT.get();
        int radius = Theme.RADIUS_MD;
        WidgetPaint.disabled(canvas, !enabled, () -> {
            int glyph;
            if (active) {
                canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), radius,
                        Colors.withAlpha(theme.accent(), 0.22f + 0.1f * hover));
                canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), radius, Colors.withAlpha(theme.accent(), 0.55f));
                glyph = Colors.lerp(theme.accentHover(), Colors.WHITE, hover * 0.6f);
            } else if (outlined) {
                int bg = Colors.lerp(theme.surface3(), theme.surface4(), hover);
                canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), radius, isPressed() ? theme.surface2() : bg);
                canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), radius, theme.borderStrong());
                glyph = Colors.lerp(theme.textSecondary(), theme.textPrimary(), hover);
            } else {
                if (hover > 0f || isPressed()) {
                    canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), radius,
                            Colors.withAlpha(theme.surface3(), isPressed() ? 1f : hover));
                }
                glyph = Colors.lerp(theme.textSecondary(), theme.textPrimary(), hover);
            }
            if (iconColor != null) {
                glyph = iconColor;
            }
            int ix = b.x() + (b.w() - iconSize) / 2;
            int iy = b.y() + (b.h() - iconSize) / 2;
            icon.draw(canvas, ix, iy, iconSize, glyph);
        });
        if (isFocused()) {
            WidgetPaint.focusRing(canvas, theme, b, radius);
        }
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (button != Keys.MOUSE_LEFT || !isEffectivelyEnabled()) {
            return false;
        }
        setPressed(true);
        requestFocus(ctx);
        ctx.captureMouse(this);
        return true;
    }

    @Override
    protected boolean onMouseUp(UiContext ctx, double x, double y, int button) {
        if (!isPressed()) {
            return false;
        }
        setPressed(false);
        ctx.releaseMouse();
        if (bounds().contains(x, y) && button == Keys.MOUSE_LEFT) {
            click(ctx);
        }
        return true;
    }

    @Override
    protected boolean onMouseDrag(UiContext ctx, double x, double y, int button, double dx, double dy) {
        return isPressed();
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (Keys.isActivate(key) && isEffectivelyEnabled()) {
            click(ctx);
            return true;
        }
        return false;
    }
}
