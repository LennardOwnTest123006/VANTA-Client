package dev.vanta.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Recording {@link Canvas} for unit tests (usable by every core test package). Every primitive is appended to
 * {@link #ops()}; fixed-advance text metrics make layout deterministic: UI/UI_BOLD 5 px per character,
 * DISPLAY 8 px, MINECRAFT 6 px.
 */
public final class TestCanvas extends AbstractCanvas {

    /** Base type of a recorded operation. */
    public sealed interface Op permits Fill, Text, Image, Scissor, TransformOp {
    }

    /** Recorded fill; coordinates are exactly what the caller passed (GUI space of the current transform). */
    public record Fill(int x, int y, int w, int h, int argb) implements Op {
        /** Right edge (exclusive). */
        public int right() {
            return x + w;
        }

        /** Bottom edge (exclusive). */
        public int bottom() {
            return y + h;
        }

        /** Whether the fill covers the pixel. */
        public boolean covers(int px, int py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    /** Recorded text. */
    public record Text(String text, int x, int y, int argb, FontKind font, boolean shadow) implements Op {
    }

    /** Recorded image. */
    public record Image(TextureRef texture, int x, int y, int w, int h) implements Op {
    }

    /** Recorded scissor change ({@code null} clip means unclipped). */
    public record Scissor(Rect deviceClip) implements Op {
    }

    /** Recorded transform change. */
    public record TransformOp(String kind, float a, float b) implements Op {
    }

    private final List<Op> ops = new ArrayList<>();

    /** Canvas of the given logical size. */
    public TestCanvas(int width, int height) {
        super(width, height);
    }

    /** 400x300 canvas. */
    public TestCanvas() {
        this(400, 300);
    }

    /** Fixed metrics matching this canvas (for building a {@link UiEnvironment} before rendering). */
    public static TextMetrics metrics() {
        return new TextMetrics() {
            @Override
            public int textWidth(String text, FontKind font) {
                return TestCanvas.width(text, font);
            }
        };
    }

    static int width(String text, FontKind font) {
        if (text == null) {
            return 0;
        }
        int per = switch (font == null ? FontKind.UI : font) {
            case DISPLAY -> 8;
            case MINECRAFT -> 6;
            default -> 5;
        };
        return text.codePointCount(0, text.length()) * per;
    }

    @Override
    public int textWidth(String text, FontKind font) {
        return width(text, font);
    }

    @Override
    protected void fillImpl(int x, int y, int w, int h, int argb) {
        ops.add(new Fill(x, y, w, h, argb));
    }

    @Override
    protected void textImpl(String text, int x, int y, int argb, FontKind font, boolean shadow) {
        ops.add(new Text(text, x, y, argb, font, shadow));
    }

    @Override
    protected void imageImpl(TextureRef texture, int x, int y, int w, int h, float u, float v, float uw, float vh,
                             int texW, int texH, float alpha) {
        ops.add(new Image(texture, x, y, w, h));
    }

    @Override
    protected void onPushTranslate(float dx, float dy) {
        ops.add(new TransformOp("translate", dx, dy));
    }

    @Override
    protected void onPushScale(float sx, float sy) {
        ops.add(new TransformOp("scale", sx, sy));
    }

    @Override
    protected void onPopTransform(Transform restored) {
        ops.add(new TransformOp("pop", restored.tx(), restored.ty()));
    }

    @Override
    protected void onScissorChanged(Rect deviceClip) {
        ops.add(new Scissor(deviceClip));
    }

    /** All recorded operations in order. */
    public List<Op> ops() {
        return List.copyOf(ops);
    }

    /** Recorded fills. */
    public List<Fill> fills() {
        return ops.stream().filter(Fill.class::isInstance).map(Fill.class::cast).collect(Collectors.toList());
    }

    /** Recorded text draws. */
    public List<Text> texts() {
        return ops.stream().filter(Text.class::isInstance).map(Text.class::cast).collect(Collectors.toList());
    }

    /** Recorded image draws. */
    public List<Image> images() {
        return ops.stream().filter(Image.class::isInstance).map(Image.class::cast).collect(Collectors.toList());
    }

    /** Recorded scissor changes. */
    public List<Scissor> scissors() {
        return ops.stream().filter(Scissor.class::isInstance).map(Scissor.class::cast).collect(Collectors.toList());
    }

    /** Whether any text draw equals the string. */
    public boolean hasText(String text) {
        return texts().stream().anyMatch(t -> t.text().equals(text));
    }

    /** Whether any text draw contains the string. */
    public boolean hasTextContaining(String fragment) {
        return texts().stream().anyMatch(t -> t.text().contains(fragment));
    }

    /** Whether any fill covers the pixel. */
    public boolean isFilled(int px, int py) {
        return fills().stream().anyMatch(f -> f.covers(px, py));
    }

    /** Whether any fill with exactly this color covers the pixel. */
    public boolean isFilled(int px, int py, int argb) {
        return fills().stream().anyMatch(f -> f.argb() == argb && f.covers(px, py));
    }

    /** Smallest rectangle containing every fill, or {@link Rect#EMPTY}. */
    public Rect fillBounds() {
        Rect r = Rect.EMPTY;
        for (Fill f : fills()) {
            Rect fr = new Rect(f.x(), f.y(), f.w(), f.h());
            r = r.isEmpty() ? fr : r.union(fr);
        }
        return r;
    }

    /** Forgets everything recorded so far. */
    public void clear() {
        ops.clear();
    }
}
