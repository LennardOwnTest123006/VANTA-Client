package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Color input: a swatch, a hex text field and (on click of the swatch) a popup with twelve preset swatches and,
 * when alpha is allowed, an opacity slider. Accepts {@code #RRGGBB} and {@code #AARRGGBB}.
 */
public class ColorField extends UiNode {

    /** Default height. */
    public static final int HEIGHT = 20;
    /** Swatch side. */
    public static final int SWATCH = 16;
    private static final int SWATCH_SMALL = 14;

    /** The twelve preset swatches offered in the popup. */
    public static final List<Integer> PALETTE = List.of(
            0xFFF5F5F7, 0xFFA1A1AA, 0xFF3A3A48, 0xFF0B0B10,
            0xFF7C5CFF, 0xFF4F8DFF, 0xFF3DD6F5, 0xFF3DDC97,
            0xFFB6F55C, 0xFFF5B942, 0xFFFF5C7A, 0xFFFF7AD9);

    private int color;
    private final boolean allowAlpha;
    private Consumer<Integer> onChange;
    private final TextField hex;
    private Popup popup;

    /** Field for {@code initial}; {@code allowAlpha} enables the opacity slider and 8-digit hex. */
    public ColorField(int initial, boolean allowAlpha, Consumer<Integer> onChange) {
        this.allowAlpha = allowAlpha;
        this.color = allowAlpha ? initial : Colors.opaque(initial);
        this.onChange = onChange;
        this.hex = new TextField(Colors.toHex(this.color))
                .maxLength(9)
                .charFilter(cp -> cp == '#' || Character.digit(cp, 16) >= 0)
                .validator(s -> isAcceptable(s))
                .onChange(this::hexEdited)
                .onSubmit(s -> {
                    if (isAcceptable(s)) {
                        apply(Colors.fromHex(s), true);
                    }
                });
        add(hex);
        setFocusable(true);
    }

    private boolean isAcceptable(String s) {
        if (!Colors.isValidHex(s)) {
            return false;
        }
        String digits = s.startsWith("#") ? s.substring(1) : s;
        return digits.length() == 6 || (allowAlpha && digits.length() == 8);
    }

    private void hexEdited(String s) {
        if (isAcceptable(s)) {
            int parsed = Colors.fromHex(s);
            if (parsed != color) {
                color = parsed;
                if (onChange != null) {
                    onChange.accept(color);
                }
            }
        }
    }

    public int color() {
        return color;
    }

    public boolean allowsAlpha() {
        return allowAlpha;
    }

    public ColorField onChange(Consumer<Integer> listener) {
        this.onChange = listener;
        return this;
    }

    /** The hex text field. */
    public TextField hexField() {
        return hex;
    }

    /** Sets the color without firing the listener. */
    public ColorField setColor(int argb) {
        this.color = allowAlpha ? argb : Colors.opaque(argb);
        syncHex();
        return this;
    }

    private void syncHex() {
        String wanted = Colors.toHex(color).toUpperCase(Locale.ROOT);
        if (!hex.text().equalsIgnoreCase(wanted)) {
            Consumer<Integer> saved = onChange;
            onChange = null;
            hex.setText(wanted);
            onChange = saved;
        }
    }

    /** Applies a new color, updating the hex field and firing the listener when changed. */
    public void apply(int argb, boolean fire) {
        int next = allowAlpha ? argb : Colors.opaque(argb);
        boolean changed = next != color;
        color = next;
        syncHex();
        if (changed && fire && onChange != null) {
            onChange.accept(color);
        }
    }

    /** Swatch rectangle. */
    public Rect swatchRect() {
        Rect b = bounds();
        return new Rect(b.x(), b.y() + (b.h() - SWATCH) / 2, SWATCH, SWATCH);
    }

    public boolean isPopupOpen() {
        return popup != null;
    }

    /** Opens the palette popup. */
    public void openPopup(UiContext ctx) {
        if (popup != null || !isEffectivelyEnabled()) {
            return;
        }
        popup = new Popup(ctx);
        ctx.popups().open(ctx, popup, false, () -> popup = null);
        ctx.playClick();
    }

    /** Closes the palette popup. */
    public void closePopup(UiContext ctx) {
        if (popup != null) {
            ctx.popups().close(ctx, popup);
        }
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(SWATCH + Theme.SPACE_3 + 84, HEIGHT);
    }

    @Override
    public void layout(UiContext ctx) {
        Rect b = bounds();
        int x = b.x() + SWATCH + Theme.SPACE_3;
        hex.setBounds(x, b.y(), Math.max(0, b.right() - x), b.h());
        hex.layout(ctx);
    }

    /** Draws a checkerboard-backed swatch with a border. */
    static void drawSwatch(Canvas canvas, Theme theme, Rect r, int argb, boolean selected) {
        if (Colors.alpha(argb) < 255) {
            int cell = Math.max(2, r.w() / 4);
            for (int y = 0; y < r.h(); y += cell) {
                for (int x = 0; x < r.w(); x += cell) {
                    boolean dark = ((x / cell) + (y / cell)) % 2 == 0;
                    canvas.fill(r.x() + x, r.y() + y, Math.min(cell, r.w() - x), Math.min(cell, r.h() - y),
                            dark ? 0xFF2A2A36 : 0xFF3C3C4A);
                }
            }
        }
        canvas.fillRounded(r.x(), r.y(), r.w(), r.h(), Theme.RADIUS_SM, argb);
        canvas.strokeRounded(r.x(), r.y(), r.w(), r.h(), Theme.RADIUS_SM,
                selected ? theme.textPrimary() : Colors.withAlpha(theme.borderStrong(), 0.9f));
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        drawSwatch(canvas, theme, swatchRect(), color, isPopupOpen() || (isHovered() && !hex.isHovered()));
        if (isFocused()) {
            WidgetPaint.focusRing(canvas, theme, swatchRect(), Theme.RADIUS_SM);
        }
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (!isEffectivelyEnabled()) {
            return false;
        }
        if (button == Keys.MOUSE_LEFT && swatchRect().expand(1).contains(x, y)) {
            requestFocus(ctx);
            if (isPopupOpen()) {
                closePopup(ctx);
            } else {
                openPopup(ctx);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (Keys.isActivate(key) && isFocused()) {
            openPopup(ctx);
            return true;
        }
        return false;
    }

    /** Palette popup. */
    private final class Popup extends UiNode {
        private static final int COLS = 6;
        private static final int GAP = 4;
        private static final int PAD = 6;
        private final Slider<Integer> alpha;
        private int highlight;

        Popup(UiContext ctx) {
            setFocusable(true);
            highlight = Math.max(0, PALETTE.indexOf(Colors.opaque(color)));
            if (allowAlpha) {
                alpha = Slider.ofInt(0, 100, 1, Math.round(Colors.alphaF(color) * 100f))
                        .formatter(v -> v + "%")
                        .onChange(v -> apply(Colors.withAlpha(color, v / 100f), true));
                add(alpha);
            } else {
                alpha = null;
            }
            position(ctx);
        }

        /**
         * Places the palette below the field (above it when only that side has room) and keeps it on the screen;
         * runs on every layout pass so it follows the field after a resize or theme change.
         */
        private void position(UiContext ctx) {
            int rows = (PALETTE.size() + COLS - 1) / COLS;
            int w = PAD * 2 + COLS * SWATCH_SMALL + (COLS - 1) * GAP;
            int h = PAD * 2 + rows * SWATCH_SMALL + (rows - 1) * GAP + (allowAlpha ? Slider.HEIGHT + GAP : 0);
            Rect anchor = ColorField.this.bounds();
            int y = anchor.bottom() + 2;
            if (y + h > ctx.screenHeight() && anchor.y() - 2 - h >= 0) {
                y = anchor.y() - 2 - h;
            }
            y = Math.max(0, Math.min(y, ctx.screenHeight() - h));
            int x = Math.min(anchor.x(), Math.max(0, ctx.screenWidth() - w));
            setBounds(x, y, w, h);
        }

        private Rect cell(int i) {
            Rect b = bounds();
            int col = i % COLS;
            int row = i / COLS;
            return new Rect(b.x() + PAD + col * (SWATCH_SMALL + GAP), b.y() + PAD + row * (SWATCH_SMALL + GAP),
                    SWATCH_SMALL, SWATCH_SMALL);
        }

        private int cellAt(double x, double y) {
            for (int i = 0; i < PALETTE.size(); i++) {
                if (cell(i).contains(x, y)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public void layout(UiContext ctx) {
            position(ctx);
            if (alpha != null) {
                Rect b = bounds();
                alpha.setBounds(b.x() + PAD, b.bottom() - PAD - Slider.HEIGHT, b.w() - PAD * 2, Slider.HEIGHT);
                alpha.layout(ctx);
            }
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            canvas.fillRounded(b.x() + 1, b.y() + 2, b.w(), b.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.bgVoid(), 0.55f));
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, theme.surface2());
            canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, theme.borderStrong());
            int opaque = Colors.opaque(color);
            for (int i = 0; i < PALETTE.size(); i++) {
                Rect c = cell(i);
                boolean sel = PALETTE.get(i) == opaque;
                drawSwatch(canvas, theme, c, PALETTE.get(i), sel);
                if (i == highlight && isFocused()) {
                    WidgetPaint.focusRing(canvas, theme, c, Theme.RADIUS_SM);
                }
            }
        }

        private void pick(UiContext ctx, int index) {
            int rgb = PALETTE.get(index);
            apply(allowAlpha ? Colors.withAlpha255(rgb, Colors.alpha(color)) : rgb, true);
            ctx.playClick();
            if (!allowAlpha) {
                closePopup(ctx);
            }
        }

        @Override
        protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
            int i = cellAt(x, y);
            if (i >= 0 && button == Keys.MOUSE_LEFT) {
                highlight = i;
                pick(ctx, i);
            }
            return true;
        }

        @Override
        public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
            switch (key) {
                case Keys.RIGHT -> highlight = Math.min(PALETTE.size() - 1, highlight + 1);
                case Keys.LEFT -> highlight = Math.max(0, highlight - 1);
                case Keys.DOWN -> highlight = Math.min(PALETTE.size() - 1, highlight + COLS);
                case Keys.UP -> highlight = Math.max(0, highlight - COLS);
                case Keys.ENTER, Keys.KP_ENTER, Keys.SPACE -> pick(ctx, highlight);
                default -> {
                    return false;
                }
            }
            return true;
        }
    }
}
