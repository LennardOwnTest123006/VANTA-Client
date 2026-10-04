package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.AnimatedValue;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.CanvasText;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

/**
 * Push button. {@link Variant#PRIMARY} uses the accent gradient, {@link Variant#SECONDARY} a graphite surface,
 * {@link Variant#GHOST} is text-only until hovered and {@link Variant#DANGER} is tinted red. Activates on
 * left click release inside the bounds, Enter or Space.
 */
public class Button extends UiNode {

    /** Visual style. */
    public enum Variant { PRIMARY, SECONDARY, GHOST, DANGER }

    /** Default height in GUI pixels. */
    public static final int HEIGHT = 20;
    /** Horizontal padding. */
    public static final int PADDING_X = 12;
    /** Leading icon size. */
    public static final int ICON_SIZE = 9;
    private static final int ICON_GAP = 5;

    private String label;
    private Variant variant;
    private Icons icon;
    private Runnable onClick;
    private AnimatedValue hoverT;
    private AnimatedValue pressT;
    private boolean compact;

    /** Secondary button. */
    public Button(String label, Runnable onClick) {
        this(label, Variant.SECONDARY, onClick);
    }

    /** Button with a variant. */
    public Button(String label, Variant variant, Runnable onClick) {
        this.label = label == null ? "" : label;
        this.variant = variant == null ? Variant.SECONDARY : variant;
        this.onClick = onClick;
        setFocusable(true);
    }

    /** Primary (gradient) button. */
    public static Button primary(String label, Runnable onClick) {
        return new Button(label, Variant.PRIMARY, onClick);
    }

    /** Secondary (surface) button. */
    public static Button secondary(String label, Runnable onClick) {
        return new Button(label, Variant.SECONDARY, onClick);
    }

    /** Ghost (text) button. */
    public static Button ghost(String label, Runnable onClick) {
        return new Button(label, Variant.GHOST, onClick);
    }

    /** Danger button. */
    public static Button danger(String label, Runnable onClick) {
        return new Button(label, Variant.DANGER, onClick);
    }

    public String label() {
        return label;
    }

    public Button setLabel(String newLabel) {
        this.label = newLabel == null ? "" : newLabel;
        return this;
    }

    public Variant variant() {
        return variant;
    }

    public Button variant(Variant v) {
        this.variant = v == null ? Variant.SECONDARY : v;
        return this;
    }

    /** Leading icon (or {@code null}). */
    public Button icon(Icons leading) {
        this.icon = leading;
        return this;
    }

    public Icons icon() {
        return icon;
    }

    /** Smaller horizontal padding. */
    public Button compact(boolean on) {
        this.compact = on;
        return this;
    }

    public Button onClick(Runnable action) {
        this.onClick = action;
        return this;
    }

    /** Font used for the label. */
    public FontKind font() {
        return variant == Variant.GHOST ? FontKind.UI : FontKind.UI_BOLD;
    }

    private int paddingX() {
        return compact ? Theme.SPACE_3 : PADDING_X;
    }

    @Override
    protected Size measure(UiContext ctx) {
        int w = ctx.textWidth(label, font()) + paddingX() * 2;
        if (icon != null) {
            w += ICON_SIZE + ICON_GAP;
        }
        return new Size(Math.max(w, HEIGHT), HEIGHT);
    }

    private void ensureAnimations(UiContext ctx) {
        if (hoverT == null) {
            hoverT = ctx.animator().value(0f);
            pressT = ctx.animator().value(0f);
        }
    }

    /** Fires the click action (plays the click sound). */
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
        ensureAnimations(ctx);
        Theme theme = ctx.theme();
        Rect b = bounds();
        boolean enabled = isEffectivelyEnabled();
        hoverT.animateTo(enabled && isHovered() && !isPressed(), Theme.MOTION_FAST);
        pressT.animateTo(isPressed(), 60L);
        float hover = hoverT.get();
        float press = pressT.get();
        int radius = Theme.RADIUS_MD;

        WidgetPaint.disabled(canvas, !enabled, () -> {
            int textColor;
            switch (variant) {
                case PRIMARY -> {
                    int start = Colors.lighten(theme.gradientStart(), 0.10f * hover);
                    int end = Colors.lighten(theme.gradientEnd(), 0.10f * hover);
                    if (press > 0f) {
                        start = Colors.darken(start, 0.18f * press);
                        end = Colors.darken(end, 0.18f * press);
                    }
                    canvas.fillRoundedGradientH(b.x(), b.y(), b.w(), b.h(), radius, start, end);
                    // Top highlight for depth.
                    canvas.fill(b.x() + radius, b.y() + 1, b.w() - 2 * radius, 1,
                            Colors.withAlpha(Colors.WHITE, 0.14f + 0.08f * hover));
                    textColor = Colors.WHITE;
                }
                case SECONDARY -> {
                    int bg = Colors.lerp(theme.surface3(), theme.surface4(), hover);
                    if (press > 0f) {
                        bg = Colors.lerp(bg, theme.surface2(), press);
                    }
                    canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), radius, bg);
                    int border = Colors.lerp(theme.borderStrong(), Colors.lighten(theme.borderStrong(), 0.25f), hover);
                    canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), radius, border);
                    textColor = theme.textPrimary();
                }
                case GHOST -> {
                    if (hover > 0f || press > 0f) {
                        int bg = Colors.withAlpha(theme.surface3(), Math.min(1f, hover * 0.9f + press * 0.4f));
                        canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), radius, bg);
                    }
                    textColor = Colors.lerp(theme.textSecondary(), theme.textPrimary(), hover);
                }
                default -> {
                    int bg = Colors.withAlpha(theme.danger(), 0.14f + 0.18f * hover + 0.1f * press);
                    canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), radius, bg);
                    canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), radius,
                            Colors.withAlpha(theme.danger(), 0.45f + 0.4f * hover));
                    textColor = Colors.lerp(theme.danger(), Colors.lighten(theme.danger(), 0.35f), hover);
                }
            }
            drawContent(canvas, ctx, b, textColor);
        });
        if (isFocused()) {
            WidgetPaint.focusRing(canvas, theme, b, radius);
        }
    }

    private void drawContent(Canvas canvas, UiContext ctx, Rect b, int textColor) {
        FontKind f = font();
        int inner = b.w() - paddingX() * 2;
        int iconSpace = icon != null ? ICON_SIZE + ICON_GAP : 0;
        String shown = CanvasText.ellipsize(label, Math.max(0, inner - iconSpace), f, canvas);
        int textW = canvas.textWidth(shown, f);
        int total = textW + iconSpace;
        int x = b.x() + (b.w() - total) / 2;
        int textY = WidgetPaint.textY(b, canvas.lineHeight(f));
        if (icon != null) {
            int iy = b.y() + (b.h() - ICON_SIZE) / 2;
            icon.draw(canvas, x, iy, ICON_SIZE, textColor);
            x += ICON_SIZE + ICON_GAP;
        }
        canvas.text(shown, x, textY, textColor, f, false);
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
