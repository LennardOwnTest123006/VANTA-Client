package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;

/**
 * Horizontal slider over a numeric range with a step. Click anywhere on the track to set, drag the knob, or
 * use Left/Right (one step; Shift = ten steps), Home/End. The value label on the right is formatted by a
 * caller-provided function.
 *
 * @param <T> the number type exposed to listeners ({@link #ofInt} / {@link #ofDouble})
 */
public class Slider<T extends Number> extends UiNode {

    /** Default height. */
    public static final int HEIGHT = 20;
    /** Knob width. */
    public static final int KNOB_W = 8;
    /** Knob height. */
    public static final int KNOB_H = 12;
    private static final int TRACK_H = 3;

    private final double min;
    private final double max;
    private final double step;
    private final DoubleFunction<T> convert;
    private double value;
    private Function<T, String> formatter;
    private Consumer<T> onChange;
    private int valueWidth = 32;
    private boolean dragging;

    private Slider(double min, double max, double step, double initial, DoubleFunction<T> convert,
                   Function<T, String> formatter) {
        if (max <= min) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        if (step <= 0) {
            throw new IllegalArgumentException("step must be positive");
        }
        this.min = min;
        this.max = max;
        this.step = step;
        this.convert = convert;
        this.formatter = formatter;
        this.value = snap(initial);
        setFocusable(true);
    }

    /**
     * Integer slider constructor for subclasses (anonymous state overrides); prefer {@link #ofInt}.
     *
     * @param min     range start
     * @param max     range end
     * @param step    step size
     * @param initial initial value
     */
    @SuppressWarnings("unchecked")
    protected Slider(double min, double max, double step, double initial) {
        this(min, max, step, initial, d -> (T) (Number) Integer.valueOf((int) Math.round(d)),
                v -> Integer.toString(v.intValue()));
    }

    /** Integer slider. */
    public static Slider<Integer> ofInt(int min, int max, int step, int initial) {
        return new Slider<>(min, max, step, initial, d -> (int) Math.round(d), v -> Integer.toString(v));
    }

    /** Double slider; the formatter shows two decimals by default. */
    public static Slider<Double> ofDouble(double min, double max, double step, double initial) {
        return new Slider<>(min, max, step, initial, d -> d, v -> String.format(Locale.ROOT, "%.2f", v));
    }

    public Slider<T> formatter(Function<T, String> f) {
        this.formatter = f;
        return this;
    }

    public Slider<T> onChange(Consumer<T> listener) {
        this.onChange = listener;
        return this;
    }

    /** Width reserved for the value label (0 hides it). */
    public Slider<T> valueWidth(int w) {
        this.valueWidth = Math.max(0, w);
        return this;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    /** Current value in the listener's number type. */
    public T value() {
        return convert.apply(value);
    }

    /** Current value as a double. */
    public double doubleValue() {
        return value;
    }

    /** Sets the value (snapped and clamped) without firing the listener. */
    public Slider<T> setValue(double v) {
        this.value = snap(v);
        return this;
    }

    /** Sets the value and fires the listener when it changed. */
    public void change(double v) {
        double snapped = snap(v);
        if (snapped != value) {
            value = snapped;
            if (onChange != null) {
                onChange.accept(value());
            }
        }
    }

    /** Snaps to the step grid and clamps to the range. */
    public double snap(double v) {
        double clamped = Math.max(min, Math.min(max, v));
        double steps = Math.round((clamped - min) / step);
        double snapped = min + steps * step;
        // Avoid floating point drift such as 0.30000000000000004.
        snapped = Math.round(snapped * 1e9) / 1e9;
        return Math.max(min, Math.min(max, snapped));
    }

    /** Normalised position 0..1 of the value. */
    public float fraction() {
        return (float) ((value - min) / (max - min));
    }

    /** The track rectangle (knob centres travel along it). */
    public Rect trackRect() {
        Rect b = bounds();
        int left = b.x() + KNOB_W / 2;
        int right = b.right() - valueWidth - KNOB_W / 2 - (valueWidth > 0 ? Theme.SPACE_3 : 0);
        return new Rect(left, b.centerY() - TRACK_H / 2, Math.max(1, right - left), TRACK_H);
    }

    /** Knob x for a value. */
    public int valueToX(double v) {
        Rect track = trackRect();
        double f = (snap(v) - min) / (max - min);
        return track.x() + (int) Math.round(f * track.w());
    }

    /** Value for a pointer x (snapped). */
    public double xToValue(double px) {
        Rect track = trackRect();
        double f = (px - track.x()) / track.w();
        f = Math.max(0.0, Math.min(1.0, f));
        return snap(min + f * (max - min));
    }

    /** Formatted current value. */
    public String formattedValue() {
        return formatter == null ? String.valueOf(value()) : formatter.apply(value());
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(120 + valueWidth, HEIGHT);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        Rect track = trackRect();
        boolean enabled = isEffectivelyEnabled();
        int knobX = valueToX(value);
        WidgetPaint.disabled(canvas, !enabled, () -> {
            canvas.fillRounded(track.x() - KNOB_W / 2, track.y(), track.w() + KNOB_W, track.h(), 1, theme.surface3());
            int filled = knobX - (track.x() - KNOB_W / 2);
            if (filled > 0) {
                canvas.fillGradientH(track.x() - KNOB_W / 2, track.y(), filled, track.h(), theme.gradientStart(),
                        Colors.lerp(theme.gradientStart(), theme.gradientEnd(), fraction()));
            }
            int kx = knobX - KNOB_W / 2;
            int ky = b.centerY() - KNOB_H / 2;
            boolean hot = isHovered() || dragging;
            canvas.fillRounded(kx, ky + 1, KNOB_W, KNOB_H, Theme.RADIUS_MD, Colors.withAlpha(theme.bgVoid(), 0.5f));
            canvas.fillRounded(kx, ky, KNOB_W, KNOB_H, Theme.RADIUS_MD,
                    hot ? Colors.WHITE : theme.textPrimary());
            if (valueWidth > 0) {
                String text = formattedValue();
                int textY = WidgetPaint.textY(b, canvas.lineHeight(FontKind.UI));
                canvas.textRight(canvas.textClipped(text, valueWidth, FontKind.UI), b.right(), textY,
                        theme.textPrimary(), FontKind.UI, false);
            }
        });
        if (isFocused()) {
            WidgetPaint.focusRing(canvas, theme, new Rect(knobX - KNOB_W / 2, b.centerY() - KNOB_H / 2, KNOB_W, KNOB_H),
                    Theme.RADIUS_MD);
        }
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (button != Keys.MOUSE_LEFT || !isEffectivelyEnabled()) {
            return false;
        }
        Rect b = bounds();
        if (valueWidth > 0 && x > b.right() - valueWidth) {
            requestFocus(ctx);
            return true;
        }
        requestFocus(ctx);
        dragging = true;
        ctx.captureMouse(this);
        change(xToValue(x));
        return true;
    }

    @Override
    protected boolean onMouseDrag(UiContext ctx, double x, double y, int button, double dx, double dy) {
        if (!dragging) {
            return false;
        }
        change(xToValue(x));
        return true;
    }

    @Override
    protected boolean onMouseUp(UiContext ctx, double x, double y, int button) {
        if (!dragging) {
            return false;
        }
        dragging = false;
        ctx.releaseMouse();
        ctx.playClick();
        return true;
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (!isEffectivelyEnabled()) {
            return false;
        }
        double amount = Keys.hasShift(mods) ? step * 10 : step;
        switch (key) {
            case Keys.LEFT -> {
                change(value - amount);
                return true;
            }
            case Keys.RIGHT -> {
                change(value + amount);
                return true;
            }
            case Keys.HOME -> {
                change(min);
                return true;
            }
            case Keys.END -> {
                change(max);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Whether the knob is being dragged. */
    public boolean isDragging() {
        return dragging;
    }
}
