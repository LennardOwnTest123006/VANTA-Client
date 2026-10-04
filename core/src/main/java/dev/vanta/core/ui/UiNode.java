package dev.vanta.core.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base class of the retained-mode UI tree. A node owns its bounds (absolute GUI coordinates), its children and
 * its interaction state. Containers override {@link #layout(UiContext)} to position children; widgets override
 * {@link #measure(UiContext)}, {@link #renderSelf(Canvas, UiContext)} and the {@code on*} input hooks.
 * <p>
 * Input methods return {@code true} when the event was consumed. Mouse events are routed top-down to the
 * deepest child under the cursor; key events are delivered to the focused node and bubble up its ancestors.
 */
public abstract class UiNode {

    private Rect bounds = Rect.EMPTY;
    private UiNode parent;
    private final List<UiNode> children = new ArrayList<>();
    private boolean visible = true;
    private boolean enabled = true;
    private boolean focusable;
    private boolean hovered;
    private boolean pressed;
    private boolean focused;
    private String tooltip;
    private String id;
    private int prefWidth = -1;
    private int prefHeight = -1;
    private float flex;

    // ---------------------------------------------------------------- tree

    /** Appends a child (re-parenting it if needed) and returns it. */
    public <T extends UiNode> T add(T child) {
        Objects.requireNonNull(child, "child");
        if (child == this) {
            throw new IllegalArgumentException("A node cannot be its own child");
        }
        UiNode node = child;
        if (node.parent != null) {
            node.parent.remove(node);
        }
        node.parent = this;
        children.add(node);
        return child;
    }

    /** Removes a direct child. */
    public void remove(UiNode child) {
        if (children.remove(child)) {
            child.parent = null;
        }
    }

    /** Removes every child. */
    public void clearChildren() {
        for (UiNode c : children) {
            c.parent = null;
        }
        children.clear();
    }

    /** Read-only view of the children in z-order (last is top-most). */
    public List<UiNode> children() {
        return Collections.unmodifiableList(children);
    }

    /** Parent node, or {@code null} for the root. */
    public UiNode parent() {
        return parent;
    }

    /** Whether {@code node} is this node or one of its descendants. */
    public boolean isAncestorOf(UiNode node) {
        for (UiNode n = node; n != null; n = n.parent) {
            if (n == this) {
                return true;
            }
        }
        return false;
    }

    /** Whether this node and all its ancestors are visible. */
    public boolean isShowing() {
        for (UiNode n = this; n != null; n = n.parent) {
            if (!n.visible) {
                return false;
            }
        }
        return true;
    }

    /** Whether this node and all its ancestors are enabled. */
    public boolean isEffectivelyEnabled() {
        for (UiNode n = this; n != null; n = n.parent) {
            if (!n.enabled) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- geometry

    /** Absolute bounds in GUI pixels. */
    public Rect bounds() {
        return bounds;
    }

    /** Sets absolute bounds; does not run layout. */
    public void setBounds(Rect newBounds) {
        this.bounds = Objects.requireNonNull(newBounds, "bounds");
    }

    /** Sets absolute bounds; does not run layout. */
    public final void setBounds(int x, int y, int w, int h) {
        setBounds(new Rect(x, y, Math.max(0, w), Math.max(0, h)));
    }

    /** Sets bounds and lays out the subtree. */
    public final void place(UiContext ctx, Rect newBounds) {
        setBounds(newBounds);
        layout(ctx);
    }

    /** Explicit preferred size (-1 keeps the measured value for that axis). */
    public UiNode size(int w, int h) {
        this.prefWidth = w;
        this.prefHeight = h;
        return this;
    }

    /** Explicit preferred width (-1 = measured). */
    public UiNode width(int w) {
        this.prefWidth = w;
        return this;
    }

    /** Explicit preferred height (-1 = measured). */
    public UiNode height(int h) {
        this.prefHeight = h;
        return this;
    }

    /** Explicit preferred width, or -1. */
    public int explicitWidth() {
        return prefWidth;
    }

    /** Explicit preferred height, or -1. */
    public int explicitHeight() {
        return prefHeight;
    }

    /** Grow weight inside {@code Column}/{@code Row}; 0 means fixed size. */
    public UiNode flex(float weight) {
        this.flex = Math.max(0f, weight);
        return this;
    }

    /** Grow weight inside {@code Column}/{@code Row}. */
    public float flex() {
        return flex;
    }

    /**
     * Preferred size: explicit values win, otherwise {@link #measure(UiContext)}.
     */
    public final Size preferredSize(UiContext ctx) {
        Size measured = (prefWidth >= 0 && prefHeight >= 0) ? Size.ZERO : measure(ctx);
        return new Size(prefWidth >= 0 ? prefWidth : measured.w(), prefHeight >= 0 ? prefHeight : measured.h());
    }

    /** Content-based size; widgets override. */
    protected Size measure(UiContext ctx) {
        return Size.ZERO;
    }

    /**
     * Positions children inside {@link #bounds()} and recurses. The default implementation only recurses;
     * containers override it.
     */
    public void layout(UiContext ctx) {
        for (UiNode child : children) {
            child.layout(ctx);
        }
    }

    // ---------------------------------------------------------------- state

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            pressed = false;
        }
    }

    public boolean isFocusable() {
        return focusable;
    }

    public void setFocusable(boolean focusable) {
        this.focusable = focusable;
    }

    /** Whether the node can take focus right now. */
    public boolean canFocus() {
        return focusable && isShowing() && isEffectivelyEnabled();
    }

    public boolean isHovered() {
        return hovered;
    }

    void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    public boolean isPressed() {
        return pressed;
    }

    protected void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    public boolean isFocused() {
        return focused;
    }

    void setFocused(boolean focused) {
        this.focused = focused;
    }

    /** Tooltip text, or {@code null}. */
    public String tooltipText() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }

    /** Fluent tooltip setter. */
    public UiNode tooltip(String text) {
        this.tooltip = text;
        return this;
    }

    /** Optional identifier for tests and game tests. */
    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /** Fluent id setter. */
    public UiNode id(String newId) {
        this.id = newId;
        return this;
    }

    /** Finds a descendant (or this) by id. */
    public UiNode findById(String wanted) {
        if (wanted == null) {
            return null;
        }
        if (wanted.equals(id)) {
            return this;
        }
        for (UiNode c : children) {
            UiNode found = c.findById(wanted);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Asks the focus manager to focus this node. */
    public void requestFocus(UiContext ctx) {
        ctx.focus().focus(ctx, this);
    }

    // ---------------------------------------------------------------- rendering

    /** Renders this node and then its visible children. */
    public void render(Canvas canvas, UiContext ctx) {
        renderSelf(canvas, ctx);
        renderChildren(canvas, ctx);
    }

    /** Draws this node's own visuals (nothing by default). */
    protected void renderSelf(Canvas canvas, UiContext ctx) {
    }

    /** Draws the visible children in order. */
    protected void renderChildren(Canvas canvas, UiContext ctx) {
        for (UiNode child : children) {
            if (child.visible) {
                child.render(canvas, ctx);
            }
        }
    }

    /** Called once per game tick (20 Hz). */
    public void onTick(UiContext ctx) {
        for (UiNode child : children) {
            child.onTick(ctx);
        }
    }

    /** Focus entered this node. */
    protected void onFocusGained(UiContext ctx) {
    }

    /** Focus left this node. */
    protected void onFocusLost(UiContext ctx) {
    }

    // ---------------------------------------------------------------- hit testing

    /** The deepest visible node under the point, or {@code null}. */
    public UiNode hitTest(double x, double y) {
        if (!visible || !bounds.contains(x, y)) {
            return null;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            UiNode hit = children.get(i).hitTest(x, y);
            if (hit != null) {
                return hit;
            }
        }
        return this;
    }

    /** Appends every focusable node of the subtree in traversal order. */
    public void collectFocusable(List<UiNode> out) {
        if (!visible || !enabled) {
            return;
        }
        if (focusable) {
            out.add(this);
        }
        for (UiNode child : children) {
            child.collectFocusable(out);
        }
    }

    // ---------------------------------------------------------------- input routing

    /** Routes a press to children (top-most first), then to {@link #onMouseDown}. */
    public boolean mouseDown(UiContext ctx, double x, double y, int button) {
        if (!visible || !enabled) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            UiNode child = children.get(i);
            if (child.visible && child.bounds.contains(x, y) && child.mouseDown(ctx, x, y, button)) {
                return true;
            }
        }
        return onMouseDown(ctx, x, y, button);
    }

    /** Routes a release; the screen sends it to the capturing node directly when one exists. */
    public boolean mouseUp(UiContext ctx, double x, double y, int button) {
        if (!visible || !enabled) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            UiNode child = children.get(i);
            if (child.visible && child.bounds.contains(x, y) && child.mouseUp(ctx, x, y, button)) {
                return true;
            }
        }
        return onMouseUp(ctx, x, y, button);
    }

    /** Routes a drag; the screen sends it to the capturing node directly when one exists. */
    public boolean mouseDrag(UiContext ctx, double x, double y, int button, double dx, double dy) {
        if (!visible || !enabled) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            UiNode child = children.get(i);
            if (child.visible && child.bounds.contains(x, y) && child.mouseDrag(ctx, x, y, button, dx, dy)) {
                return true;
            }
        }
        return onMouseDrag(ctx, x, y, button, dx, dy);
    }

    /** Routes a wheel event to the child under the cursor first. */
    public boolean mouseScroll(UiContext ctx, double x, double y, double scrollX, double scrollY) {
        if (!visible || !enabled) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            UiNode child = children.get(i);
            if (child.visible && child.bounds.contains(x, y) && child.mouseScroll(ctx, x, y, scrollX, scrollY)) {
                return true;
            }
        }
        return onMouseScroll(ctx, x, y, scrollX, scrollY);
    }

    /** Key press delivered to the focused node and its ancestors. */
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        return false;
    }

    /** Key release delivered to the focused node and its ancestors. */
    public boolean keyUp(UiContext ctx, int key, int scancode, int mods) {
        return false;
    }

    /** Character input delivered to the focused node and its ancestors. */
    public boolean charTyped(UiContext ctx, int codePoint, int mods) {
        return false;
    }

    /** Press on this node (children did not consume it). */
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        return false;
    }

    /** Release on this node. */
    protected boolean onMouseUp(UiContext ctx, double x, double y, int button) {
        return false;
    }

    /** Drag on this node. */
    protected boolean onMouseDrag(UiContext ctx, double x, double y, int button, double dx, double dy) {
        return false;
    }

    /** Wheel on this node. */
    protected boolean onMouseScroll(UiContext ctx, double x, double y, double scrollX, double scrollY) {
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + (id != null ? "#" + id : "") + bounds;
    }
}
