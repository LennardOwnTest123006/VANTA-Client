package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;

/** {@link TextField} with a search icon and a clear button that appears when there is text. */
public class SearchField extends TextField {

    private static final int CLEAR_SIZE = 14;
    private static final int CLEAR_ICON = 7;

    /** Search field with the given placeholder (e.g. the translated "Search settings…"). */
    public SearchField(String placeholder) {
        super("");
        leadingIcon(Icons.SEARCH);
        placeholder(placeholder);
        trailingSpace(CLEAR_SIZE + Theme.SPACE_1);
    }

    /** The clear button rectangle. */
    public Rect clearRect() {
        Rect b = bounds();
        return new Rect(b.right() - PADDING_X - CLEAR_SIZE + 2, b.y() + (b.h() - CLEAR_SIZE) / 2, CLEAR_SIZE, CLEAR_SIZE);
    }

    /** Clears the text. */
    public void clear(UiContext ctx) {
        if (!isEmpty()) {
            setText("");
            ctx.playClick();
        }
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        super.renderSelf(canvas, ctx);
        if (isEmpty()) {
            return;
        }
        Theme theme = ctx.theme();
        Rect c = clearRect();
        boolean hot = c.contains(ctx.mouseX(), ctx.mouseY());
        if (hot) {
            canvas.fillRounded(c.x(), c.y(), c.w(), c.h(), Theme.RADIUS_LG, theme.surface3());
        }
        Icons.CLOSE.draw(canvas, c.x() + (c.w() - CLEAR_ICON) / 2, c.y() + (c.h() - CLEAR_ICON) / 2, CLEAR_ICON,
                hot ? theme.textPrimary() : Colors.withAlpha(theme.textSecondary(), 0.9f));
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (button == Keys.MOUSE_LEFT && !isEmpty() && clearRect().contains(x, y)) {
            clear(ctx);
            requestFocus(ctx);
            return true;
        }
        return super.onMouseDown(ctx, x, y, button);
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (key == Keys.ESCAPE && !isEmpty()) {
            clear(ctx);
            return true;
        }
        return super.keyDown(ctx, key, scancode, mods);
    }
}
