package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.AnimatedValue;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.layout.Scrollbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Dropdown choice. Click/Enter/Space/Down opens a popup listing the options; inside it Up/Down move the
 * highlight, Enter selects, Escape closes and typing jumps to the first option starting with the typed text.
 *
 * @param <T> option type
 */
public class Select<T> extends UiNode {

    /** Default height. */
    public static final int HEIGHT = 20;
    /** Height of one option row in the popup. */
    public static final int ROW_H = 16;
    /** Maximum rows shown before the popup scrolls. */
    public static final int MAX_VISIBLE = 8;
    private static final int PAD_X = 8;
    private static final int CHEVRON = 9;
    private static final long TYPE_AHEAD_RESET_MS = 800L;

    private final List<T> options = new ArrayList<>();
    private Function<T, String> labelOf;
    private int selected;
    private Consumer<T> onChange;
    private Popup popup;
    private AnimatedValue openT;

    /** Select over {@code options} using {@code toString()} for labels. */
    public Select(List<T> options, T initial) {
        this(options, initial, Object::toString);
    }

    /** Select with a custom label function. */
    public Select(List<T> options, T initial, Function<T, String> labelOf) {
        this.options.addAll(Objects.requireNonNull(options, "options"));
        this.labelOf = Objects.requireNonNull(labelOf, "labelOf");
        this.selected = Math.max(0, this.options.indexOf(initial));
        setFocusable(true);
    }

    public Select<T> onChange(Consumer<T> listener) {
        this.onChange = listener;
        return this;
    }

    public Select<T> labels(Function<T, String> f) {
        this.labelOf = Objects.requireNonNull(f);
        return this;
    }

    /** Current option (or {@code null} when there are none). */
    public T value() {
        return options.isEmpty() ? null : options.get(selected);
    }

    public int selectedIndex() {
        return selected;
    }

    public List<T> options() {
        return List.copyOf(options);
    }

    /** Replaces the options, keeping the selection by equality where possible. */
    public Select<T> setOptions(List<T> newOptions) {
        T current = value();
        options.clear();
        options.addAll(newOptions);
        selected = Math.max(0, options.indexOf(current));
        return this;
    }

    /** Selects by value without firing the listener. */
    public Select<T> setValue(T v) {
        int i = options.indexOf(v);
        if (i >= 0) {
            selected = i;
        }
        return this;
    }

    /** Selects by index and fires the listener when the choice changed. */
    public void select(UiContext ctx, int index) {
        if (index < 0 || index >= options.size()) {
            return;
        }
        if (index != selected) {
            selected = index;
            if (onChange != null) {
                onChange.accept(options.get(selected));
            }
        }
    }

    /** Label of an option. */
    public String labelOf(T option) {
        return labelOf.apply(option);
    }

    public boolean isOpen() {
        return popup != null;
    }

    /** The popup node while open (for tests). */
    public UiNode popupNode() {
        return popup;
    }

    /** Opens the dropdown. */
    public void open(UiContext ctx) {
        if (popup != null || options.isEmpty() || !isEffectivelyEnabled()) {
            return;
        }
        popup = new Popup();
        ctx.popups().open(ctx, popup, false, () -> popup = null);
        ctx.playClick();
    }

    /** Closes the dropdown. */
    public void close(UiContext ctx) {
        if (popup != null) {
            ctx.popups().close(ctx, popup);
        }
    }

    @Override
    protected Size measure(UiContext ctx) {
        int widest = 0;
        for (T o : options) {
            widest = Math.max(widest, ctx.textWidth(labelOf(o), FontKind.UI));
        }
        return new Size(widest + PAD_X * 2 + CHEVRON + Theme.SPACE_2, HEIGHT);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        if (openT == null) {
            openT = ctx.animator().value(0f);
        }
        openT.animateTo(isOpen(), Theme.MOTION_FAST);
        Theme theme = ctx.theme();
        Rect b = bounds();
        boolean enabled = isEffectivelyEnabled();
        boolean hot = isHovered() || isOpen();
        WidgetPaint.disabled(canvas, !enabled, () -> {
            int bg = hot ? theme.surface4() : theme.surface3();
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, bg);
            int border = isOpen() ? theme.borderFocus() : theme.borderStrong();
            canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, border);
            String label = value() == null ? "" : labelOf(value());
            int maxW = b.w() - PAD_X * 2 - CHEVRON - Theme.SPACE_2;
            int textY = WidgetPaint.textY(b, canvas.lineHeight(FontKind.UI));
            canvas.text(canvas.textClipped(label, maxW, FontKind.UI), b.x() + PAD_X, textY, theme.textPrimary(),
                    FontKind.UI, false);
            Icons chevron = openT.get() > 0.5f ? Icons.CHEVRON_UP : Icons.CHEVRON_DOWN;
            chevron.draw(canvas, b.right() - PAD_X - CHEVRON + 1, b.y() + (b.h() - CHEVRON) / 2, CHEVRON,
                    hot ? theme.textPrimary() : theme.textSecondary());
        });
        if (isFocused() && !isOpen()) {
            WidgetPaint.focusRing(canvas, theme, b, Theme.RADIUS_MD);
        }
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (button != Keys.MOUSE_LEFT || !isEffectivelyEnabled()) {
            return false;
        }
        requestFocus(ctx);
        if (isOpen()) {
            close(ctx);
        } else {
            open(ctx);
        }
        return true;
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (!isEffectivelyEnabled() || isOpen()) {
            return false;
        }
        if (Keys.isActivate(key) || key == Keys.DOWN) {
            open(ctx);
            return true;
        }
        return false;
    }

    /** The dropdown list; one instance per open. */
    private final class Popup extends UiNode {
        private int highlight = selected;
        private int rows = Math.min(MAX_VISIBLE, options.size());
        private float scroll;
        private final StringBuilder typeAhead = new StringBuilder();
        private long typeAheadAt;
        private boolean draggingThumb;
        private int dragGrab;

        Popup() {
            setFocusable(true);
        }

        /** Rows the list shows: up to {@link #MAX_VISIBLE}, fewer when the screen leaves less room. */
        private int visibleRows() {
            return rows;
        }

        private int contentHeight() {
            return options.size() * ROW_H;
        }

        private Rect listRect() {
            return bounds().inset(Theme.SPACE_1);
        }

        /**
         * Places the list below the field, above it when only that side has room for every row, and otherwise on
         * the taller side with as many rows as fit (the rest scroll); the list never leaves the screen. Runs on
         * every layout pass so the popup follows the field after a resize or theme change.
         */
        private void position(UiContext ctx) {
            Rect anchor = Select.this.bounds();
            int wanted = Math.min(MAX_VISIBLE, options.size());
            int fullH = wanted * ROW_H + Theme.SPACE_1 * 2;
            int below = ctx.screenHeight() - (anchor.bottom() + 2);
            int above = anchor.y() - 2;
            boolean flip = fullH > below && (fullH <= above || above > below);
            int room = flip ? above : below;
            rows = Math.max(1, Math.min(wanted, (room - Theme.SPACE_1 * 2) / ROW_H));
            int h = rows * ROW_H + Theme.SPACE_1 * 2;
            int y = flip ? anchor.y() - 2 - h : anchor.bottom() + 2;
            y = Math.max(0, Math.min(y, ctx.screenHeight() - h));
            setBounds(anchor.x(), y, anchor.w(), h);
            ensureVisible();
        }

        @Override
        public void layout(UiContext ctx) {
            position(ctx);
        }

        private void ensureVisible() {
            int viewH = visibleRows() * ROW_H;
            float max = Scrollbar.maxScroll(viewH, contentHeight());
            int top = highlight * ROW_H;
            if (top < scroll) {
                scroll = top;
            } else if (top + ROW_H > scroll + viewH) {
                scroll = top + ROW_H - viewH;
            }
            scroll = Math.max(0f, Math.min(max, scroll));
        }

        private int rowAt(double y) {
            Rect list = listRect();
            if (y < list.y() || y >= list.bottom()) {
                return -1;
            }
            int row = (int) Math.floor((y - list.y() + scroll) / ROW_H);
            return row >= 0 && row < options.size() ? row : -1;
        }

        private boolean scrollable() {
            return contentHeight() > listRect().h();
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            canvas.fillRounded(b.x() + 1, b.y() + 2, b.w(), b.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.bgVoid(), 0.55f));
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, theme.surface2());
            canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, theme.borderStrong());
            Rect list = listRect();
            int textW = list.w() - PAD_X * 2 - (scrollable() ? Scrollbar.reservedWidth() : 0);
            canvas.pushScissor(list);
            int first = (int) Math.floor(scroll / ROW_H);
            int last = Math.min(options.size() - 1, (int) Math.ceil((scroll + list.h()) / ROW_H));
            for (int i = Math.max(0, first); i <= last; i++) {
                int y = list.y() + i * ROW_H - Math.round(scroll);
                Rect row = new Rect(list.x(), y, list.w() - (scrollable() ? Scrollbar.reservedWidth() : 0), ROW_H);
                boolean hovered = row.contains(ctx.mouseX(), ctx.mouseY());
                if (i == highlight || hovered) {
                    canvas.fillRounded(row.x() + 1, row.y(), row.w() - 2, row.h(), Theme.RADIUS_SM,
                            i == highlight ? theme.surface4() : theme.surface3());
                }
                boolean isSelected = i == selected;
                int color = isSelected ? theme.accentHover() : theme.textPrimary();
                int textY = WidgetPaint.textY(row, canvas.lineHeight(FontKind.UI));
                canvas.text(canvas.textClipped(labelOf(options.get(i)), textW - (isSelected ? CHEVRON : 0), FontKind.UI),
                        row.x() + PAD_X, textY, color, FontKind.UI, false);
                if (isSelected) {
                    Icons.CHECK.draw(canvas, row.right() - PAD_X - CHEVRON + 1, row.y() + (ROW_H - CHEVRON) / 2, CHEVRON,
                            theme.accentHover());
                }
            }
            canvas.popScissor();
            if (scrollable()) {
                Rect track = Scrollbar.track(list.inset(0, 1, 1, 1));
                Scrollbar.render(canvas, theme, track, contentHeight(), scroll, draggingThumb);
            }
        }

        private void choose(UiContext ctx, int index) {
            select(ctx, index);
            ctx.playClick();
            close(ctx);
        }

        @Override
        protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
            if (scrollable()) {
                Rect track = Scrollbar.track(listRect());
                if (track.expand(2).contains(x, y)) {
                    Rect thumb = Scrollbar.thumb(track, contentHeight(), scroll);
                    draggingThumb = true;
                    dragGrab = thumb.contains(x, y) ? (int) y - thumb.y() : thumb.h() / 2;
                    ctx.captureMouse(this);
                    return true;
                }
            }
            int row = rowAt(y);
            if (row >= 0 && button == Keys.MOUSE_LEFT) {
                choose(ctx, row);
            }
            return true;
        }

        @Override
        protected boolean onMouseDrag(UiContext ctx, double x, double y, int button, double dx, double dy) {
            if (draggingThumb) {
                Rect track = Scrollbar.track(listRect());
                scroll = Scrollbar.scrollForThumbTop(track, contentHeight(), (int) y - dragGrab);
                return true;
            }
            return false;
        }

        @Override
        protected boolean onMouseUp(UiContext ctx, double x, double y, int button) {
            if (draggingThumb) {
                draggingThumb = false;
                ctx.releaseMouse();
                return true;
            }
            return false;
        }

        @Override
        protected boolean onMouseScroll(UiContext ctx, double x, double y, double scrollX, double scrollY) {
            if (!scrollable()) {
                return true;
            }
            float max = Scrollbar.maxScroll(listRect().h(), contentHeight());
            scroll = Math.max(0f, Math.min(max, scroll + (float) (-scrollY * ROW_H * 2)));
            return true;
        }

        @Override
        public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
            switch (key) {
                case Keys.DOWN -> {
                    highlight = Math.min(options.size() - 1, highlight + 1);
                    ensureVisible();
                    return true;
                }
                case Keys.UP -> {
                    highlight = Math.max(0, highlight - 1);
                    ensureVisible();
                    return true;
                }
                case Keys.HOME -> {
                    highlight = 0;
                    ensureVisible();
                    return true;
                }
                case Keys.END -> {
                    highlight = options.size() - 1;
                    ensureVisible();
                    return true;
                }
                case Keys.PAGE_DOWN -> {
                    highlight = Math.min(options.size() - 1, highlight + visibleRows());
                    ensureVisible();
                    return true;
                }
                case Keys.PAGE_UP -> {
                    highlight = Math.max(0, highlight - visibleRows());
                    ensureVisible();
                    return true;
                }
                case Keys.ENTER, Keys.KP_ENTER, Keys.SPACE -> {
                    boolean typing = !typeAhead.isEmpty() && ctx.now() - typeAheadAt <= TYPE_AHEAD_RESET_MS;
                    if (key == Keys.SPACE && typing) {
                        return typeAheadAppend(ctx, ' ');
                    }
                    choose(ctx, highlight);
                    return true;
                }
                case Keys.TAB -> {
                    close(ctx);
                    return false;
                }
                default -> {
                    return false;
                }
            }
        }

        @Override
        public boolean charTyped(UiContext ctx, int codePoint, int mods) {
            if (!Keys.isPrintable(codePoint)) {
                return false;
            }
            return typeAheadAppend(ctx, codePoint);
        }

        private boolean typeAheadAppend(UiContext ctx, int codePoint) {
            long now = ctx.now();
            if (now - typeAheadAt > TYPE_AHEAD_RESET_MS) {
                typeAhead.setLength(0);
            }
            typeAheadAt = now;
            typeAhead.appendCodePoint(codePoint);
            String prefix = typeAhead.toString().toLowerCase(Locale.ROOT);
            for (int i = 0; i < options.size(); i++) {
                if (labelOf(options.get(i)).toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    highlight = i;
                    ensureVisible();
                    return true;
                }
            }
            return true;
        }

        int highlight() {
            return highlight;
        }
    }

    /** Highlighted row of the open popup, or -1. */
    public int highlightedIndex() {
        return popup == null ? -1 : popup.highlight();
    }
}
