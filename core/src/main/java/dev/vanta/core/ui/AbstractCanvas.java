package dev.vanta.core.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Base class for {@link Canvas} implementations. It owns the bookkeeping every backend needs: the transform
 * stack (translate + scale), the scissor stack (intersected in device space) and the partial-alpha multiplier.
 * Subclasses implement the raw primitives and are told whenever the clip or transform changes.
 * <p>
 * Device space is the untransformed GUI coordinate system of the surface; a canvas backing a scaled
 * bitmap applies its own base scale inside the primitives.
 * <p>
 * Note for the Minecraft backend: {@code GuiGraphics.enableScissor} transforms the rectangle by the current pose,
 * while {@link #onScissorChanged(Rect)} already receives device-space coordinates. Either apply the clip with an
 * identity pose ({@code pose().pushMatrix(); pose().identity(); enableScissor(...); pose().popMatrix();}) or override
 * {@link #pushScissor(Rect)} / {@link #popScissor()} to delegate to the native scissor stack, which also intersects
 * nested clips.
 */
public abstract class AbstractCanvas implements Canvas {

    /**
     * Affine transform without rotation: {@code device = gui * scale + translate}.
     *
     * @param tx translation x
     * @param ty translation y
     * @param sx scale x
     * @param sy scale y
     */
    public record Transform(float tx, float ty, float sx, float sy) {
        /** Identity transform. */
        public static final Transform IDENTITY = new Transform(0f, 0f, 1f, 1f);

        /** Appends a translation in the current (already transformed) units. */
        public Transform translate(float dx, float dy) {
            return new Transform(tx + dx * sx, ty + dy * sy, sx, sy);
        }

        /** Appends a scale about the current origin. */
        public Transform scale(float fx, float fy) {
            return new Transform(tx, ty, sx * fx, sy * fy);
        }

        /** Maps a GUI-space rectangle to device space (rounded to pixels). */
        public Rect apply(Rect r) {
            int x1 = Math.round(r.x() * sx + tx);
            int y1 = Math.round(r.y() * sy + ty);
            int x2 = Math.round(r.right() * sx + tx);
            int y2 = Math.round(r.bottom() * sy + ty);
            return Rect.fromCorners(x1, y1, x2, y2);
        }

        /** Maps a device point back to GUI space. */
        public double inverseX(double deviceX) {
            return (deviceX - tx) / sx;
        }

        /** Maps a device point back to GUI space. */
        public double inverseY(double deviceY) {
            return (deviceY - ty) / sy;
        }
    }

    private final int width;
    private final int height;
    private final Deque<Transform> transforms = new ArrayDeque<>();
    private final Deque<Rect> scissors = new ArrayDeque<>();
    private float partialAlpha = 1f;

    protected AbstractCanvas(int width, int height) {
        this.width = width;
        this.height = height;
        transforms.push(Transform.IDENTITY);
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    // ---------------------------------------------------------------- alpha

    @Override
    public float partialAlpha() {
        return partialAlpha;
    }

    @Override
    public void setPartialAlpha(float alpha) {
        this.partialAlpha = alpha < 0f ? 0f : Math.min(1f, alpha);
    }

    /** Applies the partial alpha multiplier to a color. */
    protected final int applyAlpha(int argb) {
        return partialAlpha >= 1f ? argb : Colors.multiplyAlpha(argb, partialAlpha);
    }

    // ---------------------------------------------------------------- primitives

    @Override
    public final void fill(int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int color = applyAlpha(argb);
        if (Colors.alpha(color) == 0) {
            return;
        }
        fillImpl(x, y, w, h, color);
    }

    @Override
    public final void text(String text, int x, int y, int argb, FontKind font, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int color = applyAlpha(argb);
        if (Colors.alpha(color) == 0) {
            return;
        }
        textImpl(text, x, y, color, font == null ? FontKind.UI : font, shadow);
    }

    @Override
    public final void image(TextureRef texture, int x, int y, int w, int h, float u, float v, float uw, float vh,
                            int texW, int texH) {
        if (w <= 0 || h <= 0) {
            return;
        }
        imageImpl(Objects.requireNonNull(texture, "texture"), x, y, w, h, u, v, uw, vh, texW, texH, partialAlpha);
    }

    /** Fills a rectangle; the color already includes partial alpha and is never fully transparent. */
    protected abstract void fillImpl(int x, int y, int w, int h, int argb);

    /** Draws text; the color already includes partial alpha. */
    protected abstract void textImpl(String text, int x, int y, int argb, FontKind font, boolean shadow);

    /** Draws a texture region; {@code alpha} is the partial alpha multiplier. */
    protected abstract void imageImpl(TextureRef texture, int x, int y, int w, int h, float u, float v, float uw,
                                      float vh, int texW, int texH, float alpha);

    // ---------------------------------------------------------------- transforms

    /** The current absolute transform. */
    public final Transform transform() {
        return transforms.peek();
    }

    @Override
    public void pushTranslate(float dx, float dy) {
        transforms.push(transform().translate(dx, dy));
        onPushTranslate(dx, dy);
    }

    @Override
    public void pushScale(float sx, float sy) {
        transforms.push(transform().scale(sx, sy));
        onPushScale(sx, sy);
    }

    @Override
    public void pop() {
        if (transforms.size() <= 1) {
            throw new IllegalStateException("Transform stack underflow");
        }
        transforms.pop();
        onPopTransform(transform());
    }

    /** Number of pushed transforms currently open. */
    public final int transformDepth() {
        return transforms.size() - 1;
    }

    /** Called after a translation was pushed. */
    protected abstract void onPushTranslate(float dx, float dy);

    /** Called after a scale was pushed. */
    protected abstract void onPushScale(float sx, float sy);

    /** Called after a transform was popped; {@code restored} is the transform now in effect. */
    protected abstract void onPopTransform(Transform restored);

    // ---------------------------------------------------------------- scissors

    @Override
    public void pushScissor(Rect clip) {
        Rect device = transform().apply(Objects.requireNonNull(clip, "clip"));
        Rect current = scissors.peek();
        Rect next = current == null ? device : current.intersect(device);
        scissors.push(next);
        onScissorChanged(next);
    }

    @Override
    public void popScissor() {
        if (scissors.isEmpty()) {
            throw new IllegalStateException("Scissor stack underflow");
        }
        scissors.pop();
        onScissorChanged(scissors.peek());
    }

    /** The active clip in device space, or {@code null} when nothing is clipped. */
    public final Rect deviceScissor() {
        return scissors.peek();
    }

    /** Number of pushed scissors currently open. */
    public final int scissorDepth() {
        return scissors.size();
    }

    /** Called whenever the active device-space clip changes; {@code null} means unclipped. */
    protected abstract void onScissorChanged(Rect deviceClip);

    /** Whether a GUI-space rectangle is at least partially visible under the active clip. */
    public final boolean isVisible(Rect guiRect) {
        Rect clip = scissors.peek();
        if (clip == null) {
            return true;
        }
        return clip.intersects(transform().apply(guiRect));
    }
}
