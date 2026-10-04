package dev.vanta.core.screen.menu;

import dev.vanta.core.ui.AnimatedValue;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Easing;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.widget.Button;

/**
 * Main-menu button: a {@link Button} that grows by 2 % and gains a soft glow while hovered, and slides up / fades in
 * when the menu opens ({@link #enterAt}). Under reduced motion both effects snap.
 */
public class MenuButton extends Button {
    /** Hover scale factor. */
    public static final float HOVER_SCALE = 1.02f;
    private static final float ENTER_OFFSET = 10f;

    private AnimatedValue hoverT;
    private long enterStart = -1L;
    private long enterDuration;

    public MenuButton(String label, Variant variant, Runnable onClick) {
        super(label, variant, onClick);
    }

    /** Schedules the entrance: invisible before {@code startMs}, fully shown {@code durationMs} later. */
    public MenuButton enterAt(long startMs, long durationMs) {
        this.enterStart = startMs;
        this.enterDuration = Math.max(1L, durationMs);
        return this;
    }

    /** Entrance progress 0..1 (1 when no entrance was scheduled). */
    public float enterProgress(UiContext ctx) {
        return enterStart < 0L ? 1f : ctx.animator().progress(enterStart, enterDuration, Easing.OUT_CUBIC);
    }

    @Override
    public void render(Canvas canvas, UiContext ctx) {
        if (hoverT == null) {
            hoverT = ctx.animator().value(0f);
        }
        float enter = enterProgress(ctx);
        if (enter <= 0f) {
            return;
        }
        hoverT.animateTo(isHovered() && isEffectivelyEnabled() && !isPressed(), Theme.MOTION_FAST);
        float hover = hoverT.get();
        Rect b = bounds();
        float cx = b.x() + b.w() / 2f;
        float cy = b.y() + b.h() / 2f;
        float scale = 1f + (HOVER_SCALE - 1f) * hover;
        float dy = (1f - enter) * ENTER_OFFSET;
        float previousAlpha = canvas.partialAlpha();
        canvas.setPartialAlpha(previousAlpha * enter);
        boolean transformed = scale != 1f || dy != 0f;
        if (transformed) {
            canvas.pushTranslate(cx, cy + dy);
            canvas.pushScale(scale, scale);
            canvas.pushTranslate(-cx, -cy);
        }
        if (hover > 0f) {
            Theme theme = ctx.theme();
            boolean primary = variant() == Variant.PRIMARY;
            int glow = primary ? Colors.withAlpha(theme.accent(), 0.38f * hover) : Colors.withAlpha(Colors.WHITE, 0.07f * hover);
            canvas.fillRounded(b.x() - 2, b.y() - 2, b.w() + 4, b.h() + 4, Theme.RADIUS_LG, glow);
            if (primary) {
                canvas.fillRounded(b.x() - 4, b.y() - 4, b.w() + 8, b.h() + 8, Theme.RADIUS_LG,
                        Colors.withAlpha(theme.accent(), 0.14f * hover));
            }
        }
        super.render(canvas, ctx);
        if (transformed) {
            canvas.pop();
            canvas.pop();
            canvas.pop();
        }
        canvas.setPartialAlpha(previousAlpha);
    }
}
