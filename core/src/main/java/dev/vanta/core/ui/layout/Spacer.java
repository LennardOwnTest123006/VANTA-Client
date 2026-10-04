package dev.vanta.core.ui.layout;

import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

/** Invisible node that takes space: fixed ({@link #fixed}) or flexible ({@link #grow}). */
public final class Spacer extends UiNode {

    private final int w;
    private final int h;

    private Spacer(int w, int h) {
        this.w = w;
        this.h = h;
    }

    /** Fixed-size spacer. */
    public static Spacer fixed(int w, int h) {
        return new Spacer(Math.max(0, w), Math.max(0, h));
    }

    /** Spacer that grows to fill a {@link Column} or {@link Row}. */
    public static Spacer grow() {
        Spacer s = new Spacer(0, 0);
        s.flex(1f);
        return s;
    }

    /** Spacer with a custom grow weight. */
    public static Spacer grow(float weight) {
        Spacer s = new Spacer(0, 0);
        s.flex(weight);
        return s;
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(w, h);
    }
}
