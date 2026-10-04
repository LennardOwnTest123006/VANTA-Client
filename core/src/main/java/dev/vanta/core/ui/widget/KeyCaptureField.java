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

import java.util.function.IntConsumer;

/**
 * Shows the key bound to an action and captures a new one: click (or Enter/Space) enters capture mode, the next
 * key press is emitted through {@link #onKey}, Escape (or clicking elsewhere) cancels. The owner updates the
 * displayed name with {@link #setKey(int, String)} once the binding was applied.
 */
public class KeyCaptureField extends UiNode {

    /** Default height. */
    public static final int HEIGHT = 20;
    private static final int PAD_X = 8;
    private static final int ICON = 9;

    private int keyCode;
    private String keyName;
    private boolean capturing;
    private boolean conflict;
    private IntConsumer onKey;
    private Runnable onCancel;
    private String capturePrompt = "Press a key…";
    private long captureStartedAt;

    /** Field showing {@code keyName} for {@code keyCode}. */
    public KeyCaptureField(int keyCode, String keyName, IntConsumer onKey) {
        this.keyCode = keyCode;
        this.keyName = keyName == null ? Keys.displayName(keyCode) : keyName;
        this.onKey = onKey;
        setFocusable(true);
    }

    /** Updates the displayed binding without emitting. */
    public KeyCaptureField setKey(int code, String name) {
        this.keyCode = code;
        this.keyName = name == null ? Keys.displayName(code) : name;
        return this;
    }

    public int keyCode() {
        return keyCode;
    }

    public String keyName() {
        return keyName;
    }

    /** Marks the binding as conflicting with another one (danger styling). */
    public KeyCaptureField conflict(boolean on) {
        this.conflict = on;
        return this;
    }

    public boolean hasConflict() {
        return conflict;
    }

    /** Text shown while capturing (translated by the caller). */
    public KeyCaptureField capturePrompt(String prompt) {
        this.capturePrompt = prompt == null ? "" : prompt;
        return this;
    }

    public KeyCaptureField onKey(IntConsumer listener) {
        this.onKey = listener;
        return this;
    }

    public KeyCaptureField onCancel(Runnable listener) {
        this.onCancel = listener;
        return this;
    }

    public boolean isCapturing() {
        return capturing;
    }

    /** Enters capture mode. */
    public void startCapture(UiContext ctx) {
        if (!isEffectivelyEnabled()) {
            return;
        }
        requestFocus(ctx);
        capturing = true;
        ctx.playClick();
    }

    /** Leaves capture mode without emitting. */
    public void cancelCapture() {
        if (capturing) {
            capturing = false;
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }

    private void emit(int key) {
        capturing = false;
        if (onKey != null) {
            onKey.accept(key);
        }
    }

    @Override
    protected Size measure(UiContext ctx) {
        int w = Math.max(ctx.textWidth(keyName, FontKind.UI), ctx.textWidth(capturePrompt, FontKind.UI)) + PAD_X * 2;
        return new Size(Math.max(w, 72), HEIGHT);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        boolean enabled = isEffectivelyEnabled();
        WidgetPaint.disabled(canvas, !enabled, () -> {
            int bg;
            int border;
            int text;
            boolean capturingNow = isCapturing();
            if (capturingNow) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(ctx.animator().phase(1200L) * Math.PI * 2);
                if (theme.reducedMotion()) {
                    pulse = 1f;
                }
                bg = Colors.withAlpha(theme.accent(), 0.16f + 0.12f * pulse);
                border = Colors.lerp(theme.accent(), theme.accentHover(), pulse);
                text = theme.textPrimary();
            } else if (conflict) {
                bg = Colors.withAlpha(theme.danger(), isHovered() ? 0.22f : 0.14f);
                border = Colors.withAlpha(theme.danger(), 0.7f);
                text = theme.danger();
            } else {
                bg = isHovered() ? theme.surface4() : theme.surface3();
                border = theme.borderStrong();
                text = theme.textPrimary();
            }
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, bg);
            canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, border);
            String shown = capturingNow ? capturePrompt : keyName;
            int lh = canvas.lineHeight(FontKind.UI);
            int iconSpace = conflict && !capturingNow ? ICON + Theme.SPACE_2 : 0;
            canvas.text(canvas.textClipped(shown, b.w() - PAD_X * 2 - iconSpace, FontKind.UI), b.x() + PAD_X,
                    WidgetPaint.textY(b, lh), text, FontKind.UI, false);
            if (iconSpace > 0) {
                Icons.WARNING.draw(canvas, b.right() - PAD_X - ICON, b.y() + (b.h() - ICON) / 2, ICON, theme.danger());
            }
        });
        if (isFocused() && !isCapturing()) {
            WidgetPaint.focusRing(canvas, theme, b, Theme.RADIUS_MD);
        }
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (!isEffectivelyEnabled()) {
            return false;
        }
        if (capturing) {
            // Clicking the field again while capturing cancels.
            cancelCapture();
            return true;
        }
        if (button == Keys.MOUSE_LEFT) {
            startCapture(ctx);
        }
        return true;
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (!isEffectivelyEnabled()) {
            return false;
        }
        if (capturing) {
            if (key == Keys.ESCAPE) {
                cancelCapture();
            } else {
                emit(key);
            }
            return true;
        }
        if (Keys.isActivate(key)) {
            startCapture(ctx);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(UiContext ctx, int codePoint, int mods) {
        // While capturing, swallow the character generated by the captured key so it does not reach other widgets.
        return capturing;
    }

    @Override
    protected void onFocusLost(UiContext ctx) {
        cancelCapture();
    }
}
