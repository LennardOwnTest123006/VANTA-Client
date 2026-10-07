package dev.vanta.core.ui.layout;

import dev.vanta.core.ui.AnimatedValue;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.ScrollIntoView;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

import java.util.Objects;

/**
 * Vertical scroll container around one content node. Supports the wheel, dragging the thumb, clicking the
 * track, keyboard paging, smooth animated scrolling and nested clipping. The content is laid out at the
 * viewport width (minus the scrollbar when it is needed) and positioned at {@code y - scroll}.
 */
public class ScrollPanel extends UiNode implements ScrollIntoView {

    private final UiNode content;
    private AnimatedValue scroll;
    private float appliedScroll = Float.NaN;
    private int contentHeight;
    private boolean draggingThumb;
    private int dragGrabOffset;
    private int edgeFade = Colors.TRANSPARENT;
    private boolean alwaysReserveScrollbar;

    /** Scroll panel around {@code content}. */
    public ScrollPanel(UiNode content) {
        this.content = Objects.requireNonNull(content, "content");
        add(content);
    }

    /** The scrolled content. */
    public UiNode content() {
        return content;
    }

    /**
     * Color used to fade the top/bottom edges when there is more content in that direction (usually the panel
     * background). Transparent disables the fade.
     */
    public ScrollPanel edgeFade(int argb) {
        this.edgeFade = argb;
        return this;
    }

    /** Reserve the scrollbar column even when the content fits (stable layouts). */
    public ScrollPanel alwaysReserveScrollbar(boolean reserve) {
        this.alwaysReserveScrollbar = reserve;
        return this;
    }

    private AnimatedValue scrollValue(UiContext ctx) {
        if (scroll == null) {
            scroll = ctx.animator().value(0f);
        }
        return scroll;
    }

    /** Current (animated) offset. */
    public float scrollY() {
        return scroll == null ? 0f : scroll.get();
    }

    /** Offset the animation is heading to. */
    public float targetScrollY() {
        return scroll == null ? 0f : scroll.target();
    }

    /** Height of the laid-out content. */
    public int contentHeight() {
        return contentHeight;
    }

    /** Largest valid offset. */
    public float maxScroll() {
        return Scrollbar.maxScroll(bounds().h(), contentHeight);
    }

    /** Whether the scrollbar is shown. */
    public boolean isScrollable() {
        return Scrollbar.needed(bounds().h(), contentHeight);
    }

    /** Sets the offset (clamped), optionally animating towards it. */
    public void setScrollY(UiContext ctx, float value, boolean animate) {
        float clamped = Scrollbar.clamp(value, bounds().h(), contentHeight);
        AnimatedValue v = scrollValue(ctx);
        if (animate) {
            v.animateTo(clamped, Theme.MOTION_FAST);
        } else {
            v.snapTo(clamped);
        }
        applyScroll(ctx);
    }

    /** Scrolls by a delta (animated). */
    public void scrollBy(UiContext ctx, float delta) {
        setScrollY(ctx, targetScrollY() + delta, true);
    }

    @Override
    public void scrollIntoView(UiContext ctx, Rect target) {
        Rect view = bounds();
        float current = targetScrollY();
        // Target is expressed in absolute coordinates at the *current* offset; convert to content space.
        float topInContent = target.y() - (view.y() - appliedScrollOrZero());
        float bottomInContent = topInContent + target.h();
        float margin = Theme.SPACE_2;
        float next = current;
        if (topInContent - margin < current) {
            next = topInContent - margin;
        } else if (bottomInContent + margin > current + view.h()) {
            next = bottomInContent + margin - view.h();
        }
        if (next != current) {
            setScrollY(ctx, next, true);
        }
    }

    private float appliedScrollOrZero() {
        return Float.isNaN(appliedScroll) ? 0f : appliedScroll;
    }

    @Override
    protected Size measure(UiContext ctx) {
        return measure(ctx, -1);
    }

    @Override
    protected Size measure(UiContext ctx, int availableWidth) {
        int contentWidth = availableWidth < 0 ? -1 : Math.max(0, availableWidth - Scrollbar.reservedWidth());
        Size pref = content.preferredSize(ctx, contentWidth);
        return new Size(pref.w() + Scrollbar.reservedWidth(), pref.h());
    }

    @Override
    public void layout(UiContext ctx) {
        Rect view = bounds();
        int fullWidth = view.w();
        int narrowWidth = Math.max(0, fullWidth - Scrollbar.reservedWidth());
        // Measure at the width the content will really get: once the scrollbar column is reserved the content
        // is narrower and wrapping content grows, so its height is taken at the narrow width in the same pass.
        Size pref = content.preferredSize(ctx, alwaysReserveScrollbar ? narrowWidth : fullWidth);
        boolean reserve = alwaysReserveScrollbar || pref.h() > view.h();
        if (reserve && !alwaysReserveScrollbar) {
            pref = content.preferredSize(ctx, narrowWidth);
        }
        int contentWidth = reserve ? narrowWidth : fullWidth;
        contentHeight = Math.max(pref.h(), view.h());
        AnimatedValue v = scrollValue(ctx);
        float clamped = Scrollbar.clamp(v.target(), view.h(), contentHeight);
        if (clamped != v.target()) {
            v.snapTo(clamped);
        }
        appliedScroll = v.get();
        content.setBounds(view.x(), view.y() - Math.round(appliedScroll), contentWidth, contentHeight);
        content.layout(ctx);
    }

    /** Re-positions the content when the animated offset moved since the last layout. */
    private void applyScroll(UiContext ctx) {
        float now = scrollValue(ctx).get();
        if (Float.isNaN(appliedScroll) || Math.round(now) != Math.round(appliedScroll)) {
            appliedScroll = now;
            Rect view = bounds();
            Rect c = content.bounds();
            content.setBounds(view.x(), view.y() - Math.round(now), c.w(), c.h());
            content.layout(ctx);
        }
    }

    @Override
    public void render(Canvas canvas, UiContext ctx) {
        applyScroll(ctx);
        Rect view = bounds();
        canvas.pushScissor(view);
        renderChildren(canvas, ctx);
        if (Colors.alpha(edgeFade) > 0 && isScrollable()) {
            int fadeH = Theme.SPACE_4;
            float s = scrollY();
            if (s > 0.5f) {
                canvas.fillGradientV(view.x(), view.y(), view.w() - Scrollbar.reservedWidth(), fadeH, edgeFade,
                        Colors.withAlpha(edgeFade, 0f));
            }
            if (s < maxScroll() - 0.5f) {
                canvas.fillGradientV(view.x(), view.bottom() - fadeH, view.w() - Scrollbar.reservedWidth(), fadeH,
                        Colors.withAlpha(edgeFade, 0f), edgeFade);
            }
        }
        canvas.popScissor();
        if (isScrollable()) {
            Rect track = Scrollbar.track(view);
            boolean active = draggingThumb || track.expand(2).contains(ctx.mouseX(), ctx.mouseY());
            Scrollbar.render(canvas, ctx.theme(), track, contentHeight, scrollY(), active);
        }
    }

    @Override
    public boolean mouseDown(UiContext ctx, double x, double y, int button) {
        if (!isVisible() || !isEnabled()) {
            return false;
        }
        if (isScrollable() && button == Keys.MOUSE_LEFT) {
            Rect track = Scrollbar.track(bounds());
            Rect hit = track.expand(2);
            if (hit.contains(x, y)) {
                Rect thumb = Scrollbar.thumb(track, contentHeight, scrollY());
                if (thumb.expand(2).contains(x, y)) {
                    draggingThumb = true;
                    dragGrabOffset = (int) y - thumb.y();
                } else {
                    // Page towards the click.
                    float delta = y < thumb.y() ? -bounds().h() : bounds().h();
                    scrollBy(ctx, delta);
                    draggingThumb = true;
                    Rect after = Scrollbar.thumb(track, contentHeight, targetScrollY());
                    dragGrabOffset = after.h() / 2;
                }
                ctx.captureMouse(this);
                return true;
            }
        }
        return super.mouseDown(ctx, x, y, button);
    }

    @Override
    public boolean mouseDrag(UiContext ctx, double x, double y, int button, double dx, double dy) {
        if (draggingThumb) {
            Rect track = Scrollbar.track(bounds());
            float target = Scrollbar.scrollForThumbTop(track, contentHeight, (int) y - dragGrabOffset);
            setScrollY(ctx, target, false);
            return true;
        }
        return super.mouseDrag(ctx, x, y, button, dx, dy);
    }

    @Override
    public boolean mouseUp(UiContext ctx, double x, double y, int button) {
        if (draggingThumb) {
            draggingThumb = false;
            ctx.releaseMouse();
            return true;
        }
        return super.mouseUp(ctx, x, y, button);
    }

    @Override
    protected boolean onMouseScroll(UiContext ctx, double x, double y, double scrollX, double scrollY) {
        if (!isScrollable()) {
            return false;
        }
        float before = targetScrollY();
        scrollBy(ctx, (float) (-scrollY * Scrollbar.WHEEL_STEP));
        return before != targetScrollY() || (scrollY != 0 && isScrollable());
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (!isScrollable()) {
            return false;
        }
        switch (key) {
            case Keys.PAGE_DOWN -> {
                scrollBy(ctx, bounds().h() - Theme.SPACE_6);
                return true;
            }
            case Keys.PAGE_UP -> {
                scrollBy(ctx, -(bounds().h() - Theme.SPACE_6));
                return true;
            }
            case Keys.HOME -> {
                if (Keys.hasControl(mods) || isFocused()) {
                    setScrollY(ctx, 0f, true);
                    return true;
                }
            }
            case Keys.END -> {
                if (Keys.hasControl(mods) || isFocused()) {
                    setScrollY(ctx, maxScroll(), true);
                    return true;
                }
            }
            case Keys.DOWN -> {
                if (isFocused()) {
                    scrollBy(ctx, Scrollbar.WHEEL_STEP);
                    return true;
                }
            }
            case Keys.UP -> {
                if (isFocused()) {
                    scrollBy(ctx, -Scrollbar.WHEEL_STEP);
                    return true;
                }
            }
            default -> {
            }
        }
        return false;
    }

    /** Whether the thumb is being dragged. */
    public boolean isDraggingThumb() {
        return draggingThumb;
    }
}
