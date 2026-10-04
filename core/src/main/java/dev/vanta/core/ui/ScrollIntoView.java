package dev.vanta.core.ui;

/**
 * Nodes that can scroll their content implement this so the focus manager can reveal a newly focused child.
 */
public interface ScrollIntoView {

    /** Scrolls so that {@code target} (absolute bounds) becomes visible. */
    void scrollIntoView(UiContext ctx, Rect target);

    /** Asks every scrolling ancestor of {@code node} to reveal it. */
    static void reveal(UiContext ctx, UiNode node) {
        Rect target = node.bounds();
        for (UiNode n = node.parent(); n != null; n = n.parent()) {
            if (n instanceof ScrollIntoView scroller) {
                scroller.scrollIntoView(ctx, target);
            }
        }
    }
}
