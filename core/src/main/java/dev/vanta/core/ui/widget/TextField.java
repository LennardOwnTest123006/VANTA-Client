package dev.vanta.core.ui.widget;

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

import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Single-line text input with caret, selection, clipboard shortcuts (Ctrl+A/C/V/X), word navigation
 * (Ctrl+Left/Right), max length, placeholder, optional character filter and a validation predicate that
 * switches the border to the danger color.
 * <p>
 * The editing model is public so tests and other widgets can drive it without a canvas.
 */
public class TextField extends UiNode {

    /** Default height. */
    public static final int HEIGHT = 20;
    /** Horizontal padding. */
    public static final int PADDING_X = 6;
    /** Caret blink period. */
    public static final long BLINK_MS = 1000L;
    private static final int ICON_SIZE = 9;

    private final StringBuilder text = new StringBuilder();
    private int caret;
    private int anchor;
    private String placeholder = "";
    private int maxLength = 256;
    private IntPredicate charFilter;
    private Predicate<String> validator;
    private Consumer<String> onChange;
    private Consumer<String> onSubmit;
    private Icons leadingIcon;
    private int trailingSpace;
    private int scrollX;
    private boolean selectingWithMouse;
    private long lastEditAt;

    /** Empty field. */
    public TextField() {
        this("");
    }

    /** Field with initial text. */
    public TextField(String initial) {
        setFocusable(true);
        setText(initial);
    }

    // ---------------------------------------------------------------- configuration

    public TextField placeholder(String hint) {
        this.placeholder = hint == null ? "" : hint;
        return this;
    }

    public String placeholder() {
        return placeholder;
    }

    /** Maximum number of characters (1..). */
    public TextField maxLength(int n) {
        this.maxLength = Math.max(1, n);
        if (text.length() > maxLength) {
            text.setLength(maxLength);
            caret = Math.min(caret, maxLength);
            anchor = Math.min(anchor, maxLength);
        }
        return this;
    }

    public int maxLength() {
        return maxLength;
    }

    /** Only code points accepted by the filter can be typed or pasted. */
    public TextField charFilter(IntPredicate filter) {
        this.charFilter = filter;
        return this;
    }

    /** Validation; {@code false} shows the invalid state. */
    public TextField validator(Predicate<String> v) {
        this.validator = v;
        return this;
    }

    public TextField onChange(Consumer<String> listener) {
        this.onChange = listener;
        return this;
    }

    public TextField onSubmit(Consumer<String> listener) {
        this.onSubmit = listener;
        return this;
    }

    /** Icon drawn at the left inside the field. */
    public TextField leadingIcon(Icons icon) {
        this.leadingIcon = icon;
        return this;
    }

    /** Pixels reserved at the right (for overlaid buttons such as a clear button). */
    protected void trailingSpace(int px) {
        this.trailingSpace = Math.max(0, px);
    }

    // ---------------------------------------------------------------- model

    /** Current text. */
    public String text() {
        return text.toString();
    }

    /** Replaces the text (clamped to the max length) and moves the caret to the end. Fires onChange. */
    public TextField setText(String value) {
        String v = value == null ? "" : value;
        if (v.length() > maxLength) {
            v = v.substring(0, maxLength);
        }
        text.setLength(0);
        text.append(v);
        caret = text.length();
        anchor = caret;
        fireChange();
        return this;
    }

    public int caret() {
        return caret;
    }

    /** Selection start (inclusive). */
    public int selectionStart() {
        return Math.min(caret, anchor);
    }

    /** Selection end (exclusive). */
    public int selectionEnd() {
        return Math.max(caret, anchor);
    }

    public boolean hasSelection() {
        return caret != anchor;
    }

    public String selectedText() {
        return text.substring(selectionStart(), selectionEnd());
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    /** Whether the validator accepts the current text (always true without a validator). */
    public boolean isValid() {
        return validator == null || validator.test(text.toString());
    }

    /** Moves the caret; with {@code select} the anchor stays put. */
    public void setCaret(int position, boolean select) {
        caret = Math.max(0, Math.min(text.length(), position));
        if (!select) {
            anchor = caret;
        }
    }

    public void selectAll() {
        anchor = 0;
        caret = text.length();
    }

    /** Inserts at the caret replacing any selection; respects filter and max length. */
    public void insert(String s) {
        if (s == null || s.isEmpty()) {
            return;
        }
        StringBuilder accepted = new StringBuilder();
        s.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\r' || cp == '\t') {
                return;
            }
            if (Keys.isPrintable(cp) && (charFilter == null || charFilter.test(cp))) {
                accepted.appendCodePoint(cp);
            }
        });
        if (accepted.isEmpty()) {
            return;
        }
        deleteSelectionInternal();
        int room = maxLength - text.length();
        if (room <= 0) {
            return;
        }
        String ins = accepted.length() > room ? accepted.substring(0, room) : accepted.toString();
        text.insert(caret, ins);
        caret += ins.length();
        anchor = caret;
        fireChange();
    }

    /** Deletes the selection, or the character before the caret. */
    public void deleteBackward() {
        if (hasSelection()) {
            deleteSelectionInternal();
            fireChange();
            return;
        }
        if (caret > 0) {
            int from = text.offsetByCodePoints(caret, -1);
            text.delete(from, caret);
            caret = from;
            anchor = caret;
            fireChange();
        }
    }

    /** Deletes the selection, or the character after the caret. */
    public void deleteForward() {
        if (hasSelection()) {
            deleteSelectionInternal();
            fireChange();
            return;
        }
        if (caret < text.length()) {
            int to = text.offsetByCodePoints(caret, 1);
            text.delete(caret, to);
            anchor = caret;
            fireChange();
        }
    }

    /** Deletes from the caret to the previous word boundary (or the selection). */
    public void deleteWordBackward() {
        if (hasSelection()) {
            deleteBackward();
            return;
        }
        int from = previousWordBoundary(caret);
        if (from < caret) {
            text.delete(from, caret);
            caret = from;
            anchor = caret;
            fireChange();
        }
    }

    /** Deletes from the caret to the next word boundary (or the selection). */
    public void deleteWordForward() {
        if (hasSelection()) {
            deleteForward();
            return;
        }
        int to = nextWordBoundary(caret);
        if (to > caret) {
            text.delete(caret, to);
            anchor = caret;
            fireChange();
        }
    }

    /** Removes the selected text (no listener call when nothing is selected). */
    public void deleteSelection() {
        if (hasSelection()) {
            deleteSelectionInternal();
            fireChange();
        }
    }

    private void deleteSelectionInternal() {
        if (!hasSelection()) {
            return;
        }
        int start = selectionStart();
        int end = selectionEnd();
        text.delete(start, end);
        caret = start;
        anchor = start;
    }

    /** Moves the caret by one code point. */
    public void moveCaret(int direction, boolean select) {
        if (!select && hasSelection()) {
            caret = direction < 0 ? selectionStart() : selectionEnd();
            anchor = caret;
            return;
        }
        int next = caret;
        if (direction < 0 && caret > 0) {
            next = text.offsetByCodePoints(caret, -1);
        } else if (direction > 0 && caret < text.length()) {
            next = text.offsetByCodePoints(caret, 1);
        }
        setCaret(next, select);
    }

    /** Moves the caret by one word. */
    public void moveWord(int direction, boolean select) {
        int next = direction < 0 ? previousWordBoundary(caret) : nextWordBoundary(caret);
        setCaret(next, select);
    }

    /** Index of the start of the word before {@code pos}. */
    public int previousWordBoundary(int pos) {
        int i = Math.max(0, Math.min(pos, text.length()));
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    /** Index of the end of the word after {@code pos}. */
    public int nextWordBoundary(int pos) {
        int i = Math.max(0, Math.min(pos, text.length()));
        int n = text.length();
        while (i < n && !Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        while (i < n && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /** Copies the selection to the host clipboard. */
    public void copy(UiContext ctx) {
        if (hasSelection()) {
            ctx.host().setClipboard(selectedText());
        }
    }

    /** Cuts the selection to the host clipboard. */
    public void cut(UiContext ctx) {
        if (hasSelection()) {
            ctx.host().setClipboard(selectedText());
            deleteSelection();
        }
    }

    /** Pastes from the host clipboard. */
    public void paste(UiContext ctx) {
        String clip = ctx.host().getClipboard();
        if (clip != null && !clip.isEmpty()) {
            insert(clip);
        }
    }

    private void fireChange() {
        lastEditAt = Long.MIN_VALUE;
        if (onChange != null) {
            onChange.accept(text.toString());
        }
    }

    /** Called with the text when Enter is pressed. */
    protected void submit(UiContext ctx) {
        if (onSubmit != null) {
            onSubmit.accept(text.toString());
        }
    }

    // ---------------------------------------------------------------- geometry

    /** Inner text rectangle. */
    public Rect textRect() {
        Rect b = bounds();
        int left = PADDING_X + (leadingIcon != null ? ICON_SIZE + Theme.SPACE_2 : 0);
        return b.inset(left, 0, PADDING_X + trailingSpace, 0);
    }

    /** Caret index for a pointer x (absolute). */
    public int indexAt(UiContext ctx, double px) {
        Rect inner = textRect();
        double rel = px - inner.x() + scrollX;
        if (rel <= 0) {
            return 0;
        }
        String s = text.toString();
        int best = s.length();
        for (int i = 0; i <= s.length(); i = i < s.length() ? s.offsetByCodePoints(i, 1) : i + 1) {
            int w = ctx.textWidth(s.substring(0, i), FontKind.UI);
            int next = i < s.length() ? ctx.textWidth(s.substring(0, s.offsetByCodePoints(i, 1)), FontKind.UI) : w;
            if (rel < (w + next) / 2.0) {
                best = i;
                break;
            }
        }
        return best;
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(120, HEIGHT);
    }

    private void ensureCaretVisible(UiContext ctx, Rect inner) {
        int caretX = ctx.textWidth(text.substring(0, caret), FontKind.UI);
        if (caretX - scrollX > inner.w() - 1) {
            scrollX = caretX - inner.w() + 1;
        } else if (caretX - scrollX < 0) {
            scrollX = caretX;
        }
        int total = ctx.textWidth(text.toString(), FontKind.UI);
        scrollX = Math.max(0, Math.min(scrollX, Math.max(0, total - inner.w() + 1)));
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        boolean enabled = isEffectivelyEnabled();
        boolean focused = isFocused();
        boolean invalid = !isValid();
        int radius = Theme.RADIUS_MD;
        WidgetPaint.disabled(canvas, !enabled, () -> {
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), radius, theme.surface1());
            int border;
            if (invalid) {
                border = theme.danger();
            } else if (focused) {
                border = theme.borderFocus();
            } else if (isHovered()) {
                border = Colors.lighten(theme.borderStrong(), 0.2f);
            } else {
                border = theme.borderSubtle();
            }
            canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), radius, border);
            Rect inner = textRect();
            if (leadingIcon != null) {
                leadingIcon.draw(canvas, b.x() + PADDING_X, b.y() + (b.h() - ICON_SIZE) / 2, ICON_SIZE,
                        focused ? theme.textSecondary() : theme.textMuted());
            }
            ensureCaretVisible(ctx, inner);
            int lh = canvas.lineHeight(FontKind.UI);
            int textY = WidgetPaint.textY(b, lh);
            canvas.pushScissor(inner);
            int baseX = inner.x() - scrollX;
            if (text.isEmpty()) {
                if (!placeholder.isEmpty()) {
                    canvas.text(placeholder, inner.x(), textY, theme.textMuted(), FontKind.UI, false);
                }
            } else {
                if (hasSelection() && focused) {
                    int sx = baseX + canvas.textWidth(text.substring(0, selectionStart()), FontKind.UI);
                    int ex = baseX + canvas.textWidth(text.substring(0, selectionEnd()), FontKind.UI);
                    canvas.fill(sx, b.y() + 3, Math.max(1, ex - sx), b.h() - 6, Colors.withAlpha(theme.accent(), 0.4f));
                }
                canvas.text(text.toString(), baseX, textY, theme.textPrimary(), FontKind.UI, false);
            }
            if (focused && enabled) {
                boolean visible = ctx.animator().phase(BLINK_MS) < 0.5f || ctx.theme().reducedMotion();
                if (visible) {
                    int cx = baseX + canvas.textWidth(text.substring(0, caret), FontKind.UI);
                    canvas.fill(cx, b.y() + 4, 1, b.h() - 8, invalid ? theme.danger() : theme.accentHover());
                }
            }
            canvas.popScissor();
        });
    }

    // ---------------------------------------------------------------- input

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (!isEffectivelyEnabled()) {
            return false;
        }
        if (button != Keys.MOUSE_LEFT) {
            requestFocus(ctx);
            return true;
        }
        requestFocus(ctx);
        int idx = indexAt(ctx, x);
        setCaret(idx, false);
        selectingWithMouse = true;
        ctx.captureMouse(this);
        return true;
    }

    @Override
    protected boolean onMouseDrag(UiContext ctx, double x, double y, int button, double dx, double dy) {
        if (!selectingWithMouse) {
            return false;
        }
        setCaret(indexAt(ctx, x), true);
        return true;
    }

    @Override
    protected boolean onMouseUp(UiContext ctx, double x, double y, int button) {
        if (!selectingWithMouse) {
            return false;
        }
        selectingWithMouse = false;
        ctx.releaseMouse();
        return true;
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (!isEffectivelyEnabled()) {
            return false;
        }
        boolean shift = Keys.hasShift(mods);
        boolean ctrl = Keys.hasControl(mods);
        switch (key) {
            case Keys.LEFT -> {
                if (ctrl) {
                    moveWord(-1, shift);
                } else {
                    moveCaret(-1, shift);
                }
                return true;
            }
            case Keys.RIGHT -> {
                if (ctrl) {
                    moveWord(1, shift);
                } else {
                    moveCaret(1, shift);
                }
                return true;
            }
            case Keys.HOME -> {
                setCaret(0, shift);
                return true;
            }
            case Keys.END -> {
                setCaret(text.length(), shift);
                return true;
            }
            case Keys.BACKSPACE -> {
                if (ctrl) {
                    deleteWordBackward();
                } else {
                    deleteBackward();
                }
                return true;
            }
            case Keys.DELETE -> {
                if (ctrl) {
                    deleteWordForward();
                } else {
                    deleteForward();
                }
                return true;
            }
            case Keys.ENTER, Keys.KP_ENTER -> {
                submit(ctx);
                return true;
            }
            case Keys.A -> {
                if (ctrl) {
                    selectAll();
                    return true;
                }
            }
            case Keys.C -> {
                if (ctrl) {
                    copy(ctx);
                    return true;
                }
            }
            case Keys.X -> {
                if (ctrl) {
                    cut(ctx);
                    return true;
                }
            }
            case Keys.V -> {
                if (ctrl) {
                    paste(ctx);
                    return true;
                }
            }
            default -> {
            }
        }
        // Swallow plain printable keys so the screen does not treat them as shortcuts; charTyped inserts them.
        return !ctrl && !Keys.isModifier(key) && key != Keys.TAB && key != Keys.ESCAPE && key != Keys.UP
                && key != Keys.DOWN && key >= Keys.SPACE && key <= Keys.GRAVE_ACCENT;
    }

    @Override
    public boolean charTyped(UiContext ctx, int codePoint, int mods) {
        if (!isEffectivelyEnabled() || Keys.hasControl(mods)) {
            return false;
        }
        if (!Keys.isPrintable(codePoint)) {
            return false;
        }
        insert(new String(Character.toChars(codePoint)));
        return true;
    }

    @Override
    protected void onFocusLost(UiContext ctx) {
        anchor = caret;
        selectingWithMouse = false;
    }

    /** Time of the last edit (for callers debouncing searches). */
    public long lastEditAt() {
        return lastEditAt;
    }
}
