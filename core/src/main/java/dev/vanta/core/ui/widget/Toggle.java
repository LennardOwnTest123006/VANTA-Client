package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.AnimatedValue;
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
 * On/off switch with an animated knob. Optionally shows a label to the left; the switch itself sits at the
 * right edge of the bounds. Toggles on click, Enter and Space; Left/Right set off/on.
 */
public class Toggle extends UiNode {

    /** Track width. */
    public static final int TRACK_W = 22;
    /** Track height. */
    public static final int TRACK_H = 12;
    private static final int KNOB = 8;
    private static final int KNOB_INSET = 2;

    private boolean on;
    private String label;
    private Consumer<Boolean> onChange;
    private AnimatedValue knobT;

    /** Switch without a label. */
    public Toggle(boolean initial, Consumer<Boolean> onChange) {
        this(null, initial, onChange);
    }

    /** Switch with a label on the left. */
    public Toggle(String label, boolean initial, Consumer<Boolean> onChange) {
        this.label = label;
        this.on = initial;
        this.onChange = onChange;
        setFocusable(true);
    }

    public boolean isOn() {
        return on;
    }

    /** Sets the state without firing the listener. */
    public Toggle setOn(boolean value) {
        this.on = value;
        return this;
    }

    public Toggle onChange(Consumer<Boolean> listener) {
        this.onChange = listener;
        return this;
    }

    public String label() {
        return label;
    }

    /** Flips the state, fires the listener and plays the click sound. */
    public void toggle(UiContext ctx) {
        set(ctx, !on);
    }

    /** Sets the state and fires the listener when it changed. */
    public void set(UiContext ctx, boolean value) {
        if (!isEffectivelyEnabled() || value == on) {
            return;
        }
        on = value;
        ctx.playClick();
        if (onChange != null) {
            onChange.accept(on);
        }
    }

    /** The switch rectangle inside the bounds (right aligned). */
    public Rect trackRect() {
        Rect b = bounds();
        return new Rect(b.right() - TRACK_W, b.y() + (b.h() - TRACK_H) / 2, TRACK_W, TRACK_H);
    }

    @Override
    protected Size measure(UiContext ctx) {
        int w = TRACK_W;
        if (label != null && !label.isEmpty()) {
            w += ctx.textWidth(label, dev.vanta.core.ui.FontKind.UI) + Theme.SPACE_4;
        }
        return new Size(w, Math.max(TRACK_H, Button.HEIGHT));
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        if (knobT == null) {
            knobT = ctx.animator().value(on);
        }
        knobT.animateTo(on, Theme.MOTION_FAST);
        Theme theme = ctx.theme();
        boolean enabled = isEffectivelyEnabled();
        Rect track = trackRect();
        float t = knobT.get();
        WidgetPaint.disabled(canvas, !enabled, () -> {
            if (label != null && !label.isEmpty()) {
                Rect b = bounds();
                int textY = WidgetPaint.textY(b, canvas.lineHeight(dev.vanta.core.ui.FontKind.UI));
                canvas.text(label, b.x(), textY, theme.textPrimary(), dev.vanta.core.ui.FontKind.UI, false);
            }
            int offBg = isHovered() ? theme.surface4() : theme.surface3();
            if (t <= 0f) {
                canvas.fillRounded(track.x(), track.y(), track.w(), track.h(), Theme.RADIUS_LG, offBg);
                canvas.strokeRounded(track.x(), track.y(), track.w(), track.h(), Theme.RADIUS_LG, theme.borderStrong());
            } else {
                int start = Colors.lerp(offBg, theme.gradientStart(), t);
                int end = Colors.lerp(offBg, theme.gradientEnd(), t);
                if (isHovered()) {
                    start = Colors.lighten(start, 0.08f);
                    end = Colors.lighten(end, 0.08f);
                }
                canvas.fillRoundedGradientH(track.x(), track.y(), track.w(), track.h(), Theme.RADIUS_LG, start, end);
                if (t < 1f) {
                    canvas.strokeRounded(track.x(), track.y(), track.w(), track.h(), Theme.RADIUS_LG,
                            Colors.withAlpha(theme.borderStrong(), 1f - t));
                }
            }
            int knobOff = track.x() + KNOB_INSET;
            int knobOn = track.right() - KNOB_INSET - KNOB;
            int kx = Math.round(knobOff + (knobOn - knobOff) * t);
            int ky = track.y() + (track.h() - KNOB) / 2;
            int knobColor = Colors.lerp(theme.textSecondary(), Colors.WHITE, t);
            canvas.fillRounded(kx, ky, KNOB, KNOB, Theme.RADIUS_LG, knobColor);
        });
        if (isFocused()) {
            WidgetPaint.focusRing(canvas, theme, track, Theme.RADIUS_LG);
        }
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (button != Keys.MOUSE_LEFT || !isEffectivelyEnabled()) {
            return false;
        }
        requestFocus(ctx);
        toggle(ctx);
        return true;
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (Keys.isActivate(key)) {
            toggle(ctx);
            return true;
        }
        if (key == Keys.LEFT) {
            set(ctx, false);
            return true;
        }
        if (key == Keys.RIGHT) {
            set(ctx, true);
            return true;
        }
        return false;
    }
}
