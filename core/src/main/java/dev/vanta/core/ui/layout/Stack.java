package dev.vanta.core.ui.layout;

import dev.vanta.core.ui.Insets;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

/**
 * Children fill the same area and draw in order (the last child is on top). Used for overlays such as a
 * background layer underneath content.
 */
public class Stack extends UiNode {

    private Insets padding = Insets.ZERO;

    public Stack padding(Insets insets) {
        this.padding = insets == null ? Insets.ZERO : insets;
        return this;
    }

    @Override
    protected Size measure(UiContext ctx) {
        Size max = Size.ZERO;
        for (UiNode child : children()) {
            if (child.isVisible()) {
                max = max.max(child.preferredSize(ctx));
            }
        }
        return max.plus(padding);
    }

    @Override
    public void layout(UiContext ctx) {
        Rect inner = bounds().inset(padding);
        for (UiNode child : children()) {
            child.setBounds(inner);
            child.layout(ctx);
        }
    }
}
