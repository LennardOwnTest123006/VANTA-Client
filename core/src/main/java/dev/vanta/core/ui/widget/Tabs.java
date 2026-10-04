package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.AnimatedValue;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Horizontal tab strip with an animated gradient indicator. Click a tab or use Left/Right while focused.
 */
public class Tabs extends UiNode {

    /** Strip height. */
    public static final int HEIGHT = 22;
    private static final int PAD_X = 10;
    private static final int GAP = 2;

    private final List<String> labels = new ArrayList<>();
    private int selected;
    private IntConsumer onSelect;
    private AnimatedValue indicatorX;
    private AnimatedValue indicatorW;

    /** Tabs with the given labels. */
    public Tabs(List<String> labels, int initial) {
        this.labels.addAll(labels);
        this.selected = Math.max(0, Math.min(labels.size() - 1, initial));
        setFocusable(true);
    }

    public Tabs onSelect(IntConsumer listener) {
        this.onSelect = listener;
        return this;
    }

    public int selected() {
        return selected;
    }

    public List<String> labels() {
        return List.copyOf(labels);
    }

    /** Selects a tab, firing the listener when it changed. */
    public void select(UiContext ctx, int index) {
        if (index < 0 || index >= labels.size() || index == selected) {
            return;
        }
        selected = index;
        ctx.playClick();
        if (onSelect != null) {
            onSelect.accept(index);
        }
    }

    /** Bounds of tab {@code i} (absolute). */
    public Rect tabRect(UiContext ctx, int i) {
        Rect b = bounds();
        int x = b.x();
        for (int k = 0; k < labels.size(); k++) {
            int w = ctx.textWidth(labels.get(k), FontKind.UI_BOLD) + PAD_X * 2;
            if (k == i) {
                return new Rect(x, b.y(), w, b.h() - 1);
            }
            x += w + GAP;
        }
        return Rect.EMPTY;
    }

    @Override
    protected Size measure(UiContext ctx) {
        int w = 0;
        for (String l : labels) {
            w += ctx.textWidth(l, FontKind.UI_BOLD) + PAD_X * 2;
        }
        w += GAP * Math.max(0, labels.size() - 1);
        return new Size(w, HEIGHT);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        canvas.fill(b.x(), b.bottom() - 1, b.w(), 1, theme.borderSubtle());
        Rect active = tabRect(ctx, selected);
        if (indicatorX == null) {
            indicatorX = ctx.animator().value(active.x());
            indicatorW = ctx.animator().value(active.w());
        }
        indicatorX.animateTo(active.x(), Theme.MOTION_BASE);
        indicatorW.animateTo(active.w(), Theme.MOTION_BASE);
        int lh = canvas.lineHeight(FontKind.UI_BOLD);
        for (int i = 0; i < labels.size(); i++) {
            Rect r = tabRect(ctx, i);
            boolean hot = r.contains(ctx.mouseX(), ctx.mouseY());
            int color;
            if (i == selected) {
                color = theme.textPrimary();
            } else if (hot) {
                color = Colors.lerp(theme.textSecondary(), theme.textPrimary(), 0.7f);
                canvas.fillRounded(r.x(), r.y() + 2, r.w(), r.h() - 4, Theme.RADIUS_SM,
                        Colors.withAlpha(theme.surface3(), 0.8f));
            } else {
                color = theme.textSecondary();
            }
            canvas.text(labels.get(i), r.x() + PAD_X, WidgetPaint.textY(r, lh), color, FontKind.UI_BOLD, false);
        }
        int ix = Math.round(indicatorX.get()) + 2;
        int iw = Math.max(4, Math.round(indicatorW.get()) - 4);
        canvas.fillGradientH(ix, b.bottom() - 2, iw, 2, theme.gradientStart(), theme.gradientEnd());
        if (isFocused()) {
            WidgetPaint.focusRing(canvas, theme, active.inset(0, 2, 0, 1), Theme.RADIUS_SM);
        }
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (button != Keys.MOUSE_LEFT || !isEffectivelyEnabled()) {
            return false;
        }
        requestFocus(ctx);
        for (int i = 0; i < labels.size(); i++) {
            if (tabRect(ctx, i).contains(x, y)) {
                select(ctx, i);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (key == Keys.LEFT) {
            select(ctx, Math.max(0, selected - 1));
            return true;
        }
        if (key == Keys.RIGHT) {
            select(ctx, Math.min(labels.size() - 1, selected + 1));
            return true;
        }
        if (key == Keys.HOME) {
            select(ctx, 0);
            return true;
        }
        if (key == Keys.END) {
            select(ctx, labels.size() - 1);
            return true;
        }
        return false;
    }
}
