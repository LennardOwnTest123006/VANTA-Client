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
import dev.vanta.core.ui.layout.Scrollbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Virtualised vertical list: only the rows intersecting the viewport are painted, so thousands of items are
 * cheap. Supports single selection by click or Up/Down/Home/End/PageUp/PageDown, activation with Enter or
 * double-click, wheel and thumb scrolling.
 *
 * @param <T> item type
 */
public class ListView<T> extends UiNode {

    /** Paints one row. */
    @FunctionalInterface
    public interface RowRenderer<T> {
        /** Draws {@code item} into {@code row}. */
        void render(Canvas canvas, UiContext ctx, T item, int index, Rect row, boolean selected, boolean hovered);
    }

    /** Default row height. */
    public static final int ROW_H = 18;
    private static final long DOUBLE_CLICK_MS = 350L;
    private static final int PAD_X = 8;

    private final List<T> items = new ArrayList<>();
    private RowRenderer<T> renderer;
    private int rowHeight = ROW_H;
    private int selected = -1;
    private Consumer<T> onSelect;
    private Consumer<T> onActivate;
    private AnimatedValue scroll;
    private boolean draggingThumb;
    private int dragGrab;
    private long lastClickAt;
    private int lastClickRow = -1;
    private String emptyText = "";

    /** List with a text renderer. */
    public ListView(List<T> items, Function<T, String> label) {
        this(items, textRows(label));
    }

    /** List with a custom row renderer. */
    public ListView(List<T> items, RowRenderer<T> renderer) {
        this.items.addAll(items);
        this.renderer = renderer;
        setFocusable(true);
    }

    /** Renderer drawing the label and a selection accent bar. */
    public static <T> RowRenderer<T> textRows(Function<T, String> label) {
        return (canvas, ctx, item, index, row, selected, hovered) -> {
            Theme theme = ctx.theme();
            int lh = canvas.lineHeight(FontKind.UI);
            canvas.text(canvas.textClipped(label.apply(item), row.w() - PAD_X * 2, FontKind.UI), row.x() + PAD_X,
                    WidgetPaint.textY(row, lh), selected ? theme.textPrimary() : theme.textSecondary(), FontKind.UI, false);
        };
    }

    public ListView<T> rowHeight(int h) {
        this.rowHeight = Math.max(8, h);
        return this;
    }

    public int rowHeight() {
        return rowHeight;
    }

    public ListView<T> renderer(RowRenderer<T> r) {
        this.renderer = r;
        return this;
    }

    public ListView<T> onSelect(Consumer<T> listener) {
        this.onSelect = listener;
        return this;
    }

    public ListView<T> onActivate(Consumer<T> listener) {
        this.onActivate = listener;
        return this;
    }

    /** Text shown when there are no items (translated by the caller). */
    public ListView<T> emptyText(String text) {
        this.emptyText = text == null ? "" : text;
        return this;
    }

    public List<T> items() {
        return List.copyOf(items);
    }

    /** Replaces the items, keeping the selection by equality. */
    public ListView<T> setItems(List<T> newItems) {
        T current = selectedItem().orElse(null);
        items.clear();
        items.addAll(newItems);
        selected = current == null ? -1 : items.indexOf(current);
        return this;
    }

    public int selectedIndex() {
        return selected;
    }

    public Optional<T> selectedItem() {
        return selected >= 0 && selected < items.size() ? Optional.of(items.get(selected)) : Optional.empty();
    }

    /** Selects an index (-1 clears) and fires the listener when it changed. */
    public void select(UiContext ctx, int index) {
        int clamped = index < 0 || index >= items.size() ? -1 : index;
        if (clamped == selected) {
            return;
        }
        selected = clamped;
        if (selected >= 0) {
            if (bounds().h() > 0) {
                ensureVisible(ctx, selected);
            }
            if (onSelect != null) {
                onSelect.accept(items.get(selected));
            }
        }
    }

    /** Fires the activate listener for the selection. */
    public void activate(UiContext ctx) {
        if (selected >= 0 && onActivate != null) {
            onActivate.accept(items.get(selected));
        }
    }

    public int contentHeight() {
        return items.size() * rowHeight;
    }

    public float scrollY() {
        return scroll == null ? 0f : scroll.get();
    }

    private AnimatedValue scrollValue(UiContext ctx) {
        if (scroll == null) {
            scroll = ctx.animator().value(0f);
        }
        return scroll;
    }

    /** Sets the scroll offset (clamped). */
    public void setScrollY(UiContext ctx, float value, boolean animate) {
        float clamped = Scrollbar.clamp(value, bounds().h(), contentHeight());
        if (animate) {
            scrollValue(ctx).animateTo(clamped, Theme.MOTION_FAST);
        } else {
            scrollValue(ctx).snapTo(clamped);
        }
    }

    /** Scrolls so that row {@code index} is visible. */
    public void ensureVisible(UiContext ctx, int index) {
        int top = index * rowHeight;
        float target = scrollValue(ctx).target();
        int viewH = bounds().h();
        if (top < target) {
            setScrollY(ctx, top, true);
        } else if (top + rowHeight > target + viewH) {
            setScrollY(ctx, top + rowHeight - viewH, true);
        }
    }

    /** Index of the row under an absolute y, or -1. */
    public int rowAt(double y) {
        Rect b = bounds();
        if (y < b.y() || y >= b.bottom()) {
            return -1;
        }
        int row = (int) Math.floor((y - b.y() + scrollY()) / rowHeight);
        return row >= 0 && row < items.size() ? row : -1;
    }

    /** First row index that is at least partially visible. */
    public int firstVisibleRow() {
        return Math.max(0, (int) Math.floor(scrollY() / rowHeight));
    }

    /** Last row index that is at least partially visible. */
    public int lastVisibleRow() {
        int last = (int) Math.floor((scrollY() + bounds().h() - 1) / rowHeight);
        return Math.min(items.size() - 1, Math.max(-1, last));
    }

    private boolean scrollable() {
        return Scrollbar.needed(bounds().h(), contentHeight());
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(160, Math.min(contentHeight(), rowHeight * 6));
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        if (items.isEmpty()) {
            if (!emptyText.isEmpty()) {
                canvas.textCentered(emptyText, b.centerX(), WidgetPaint.textY(b, canvas.lineHeight(FontKind.UI)),
                        theme.textMuted(), FontKind.UI, false);
            }
            return;
        }
        float off = scrollY();
        int rowW = b.w() - (scrollable() ? Scrollbar.reservedWidth() : 0);
        canvas.pushScissor(b);
        int first = firstVisibleRow();
        int last = lastVisibleRow();
        for (int i = first; i <= last; i++) {
            int y = b.y() + i * rowHeight - Math.round(off);
            Rect row = new Rect(b.x(), y, rowW, rowHeight);
            boolean sel = i == selected;
            boolean hot = isHovered() && row.contains(ctx.mouseX(), ctx.mouseY());
            if (sel) {
                canvas.fillRounded(row.x(), row.y(), row.w(), row.h(), Theme.RADIUS_SM,
                        Colors.withAlpha(theme.accent(), 0.18f));
                canvas.fillGradientV(row.x(), row.y() + 2, 2, row.h() - 4, theme.gradientStart(), theme.gradientEnd());
            } else if (hot) {
                canvas.fillRounded(row.x(), row.y(), row.w(), row.h(), Theme.RADIUS_SM,
                        Colors.withAlpha(theme.surface3(), 0.8f));
            }
            renderer.render(canvas, ctx, items.get(i), i, row, sel, hot);
        }
        canvas.popScissor();
        if (scrollable()) {
            Rect track = Scrollbar.track(b);
            boolean active = draggingThumb || track.expand(2).contains(ctx.mouseX(), ctx.mouseY());
            Scrollbar.render(canvas, theme, track, contentHeight(), off, active);
        }
        if (isFocused() && selected >= 0 && selected >= first && selected <= last) {
            int y = b.y() + selected * rowHeight - Math.round(off);
            Rect row = new Rect(b.x(), y, rowW, rowHeight).intersect(b);
            if (!row.isEmpty()) {
                canvas.strokeRounded(row.x(), row.y(), row.w(), row.h(), Theme.RADIUS_SM,
                        Colors.withAlpha(theme.borderFocus(), 0.9f));
            }
        }
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (!isEffectivelyEnabled()) {
            return false;
        }
        requestFocus(ctx);
        if (scrollable() && button == Keys.MOUSE_LEFT) {
            Rect track = Scrollbar.track(bounds());
            if (track.expand(2).contains(x, y)) {
                Rect thumb = Scrollbar.thumb(track, contentHeight(), scrollY());
                draggingThumb = true;
                dragGrab = thumb.contains(x, y) ? (int) y - thumb.y() : thumb.h() / 2;
                ctx.captureMouse(this);
                return true;
            }
        }
        int row = rowAt(y);
        if (row >= 0 && button == Keys.MOUSE_LEFT) {
            long now = ctx.now();
            boolean doubleClick = row == lastClickRow && now - lastClickAt <= DOUBLE_CLICK_MS;
            lastClickAt = now;
            lastClickRow = row;
            select(ctx, row);
            ctx.playClick();
            if (doubleClick) {
                activate(ctx);
            }
        }
        return true;
    }

    @Override
    protected boolean onMouseDrag(UiContext ctx, double x, double y, int button, double dx, double dy) {
        if (!draggingThumb) {
            return false;
        }
        Rect track = Scrollbar.track(bounds());
        setScrollY(ctx, Scrollbar.scrollForThumbTop(track, contentHeight(), (int) y - dragGrab), false);
        return true;
    }

    @Override
    protected boolean onMouseUp(UiContext ctx, double x, double y, int button) {
        if (!draggingThumb) {
            return false;
        }
        draggingThumb = false;
        ctx.releaseMouse();
        return true;
    }

    @Override
    protected boolean onMouseScroll(UiContext ctx, double x, double y, double scrollX, double scrollY) {
        if (!scrollable()) {
            return false;
        }
        setScrollY(ctx, scrollValue(ctx).target() + (float) (-scrollY * Scrollbar.WHEEL_STEP), true);
        return true;
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (items.isEmpty() || !isEffectivelyEnabled()) {
            return false;
        }
        int visible = Math.max(1, bounds().h() / rowHeight);
        switch (key) {
            case Keys.DOWN -> select(ctx, Math.min(items.size() - 1, selected + 1));
            case Keys.UP -> select(ctx, Math.max(0, selected - 1));
            case Keys.HOME -> select(ctx, 0);
            case Keys.END -> select(ctx, items.size() - 1);
            case Keys.PAGE_DOWN -> select(ctx, Math.min(items.size() - 1, Math.max(0, selected) + visible));
            case Keys.PAGE_UP -> select(ctx, Math.max(0, selected - visible));
            case Keys.ENTER, Keys.KP_ENTER -> activate(ctx);
            default -> {
                return false;
            }
        }
        return true;
    }
}
