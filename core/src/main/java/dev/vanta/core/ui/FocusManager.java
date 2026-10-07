package dev.vanta.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Keyboard focus for one screen. Traversal order is depth-first order of the focusable nodes under the current
 * scope root (the screen root, or the top-most popup while one is open).
 */
public final class FocusManager {

    private UiNode focused;
    private Supplier<UiNode> scope = () -> null;
    private long changedAt;

    /** The focused node, or {@code null}. */
    public UiNode focused() {
        return focused;
    }

    /** Whether the node has focus. */
    public boolean isFocused(UiNode node) {
        return focused != null && focused == node;
    }

    /** Sets the subtree focus traversal is limited to. */
    public void setScope(Supplier<UiNode> scopeRoot) {
        this.scope = scopeRoot == null ? () -> null : scopeRoot;
    }

    /** The current traversal root (may be {@code null} before init). */
    public UiNode scopeRoot() {
        return scope.get();
    }

    /** Time (clock ms) of the last focus change; used to scroll the focused node into view once. */
    public long changedAt() {
        return changedAt;
    }

    /**
     * Moves focus to {@code node}. Ignored when the node cannot take focus or lies outside the current scope (a
     * node behind an open popup): {@link #validate} would drop such a focus on the next frame anyway, and the
     * node focused inside the popup must keep it.
     */
    public void focus(UiContext ctx, UiNode node) {
        if (node == focused) {
            return;
        }
        if (node != null && !node.canFocus()) {
            return;
        }
        if (node != null && scope.get() != null && !inScope(node)) {
            return;
        }
        UiNode old = focused;
        focused = node;
        changedAt = ctx.now();
        if (old != null) {
            old.setFocused(false);
            old.onFocusLost(ctx);
        }
        if (node != null) {
            node.setFocused(true);
            node.onFocusGained(ctx);
        }
    }

    /** Removes focus from any node. */
    public void clear(UiContext ctx) {
        focus(ctx, null);
    }

    /** Focusable nodes in traversal order under the scope root. */
    public List<UiNode> traversal() {
        List<UiNode> out = new ArrayList<>();
        UiNode root = scope.get();
        if (root != null) {
            root.collectFocusable(out);
        }
        out.removeIf(n -> !n.canFocus());
        return out;
    }

    /** Focuses the next node in order (wrapping). Returns whether something is focused afterwards. */
    public boolean focusNext(UiContext ctx) {
        return step(ctx, 1);
    }

    /** Focuses the previous node in order (wrapping). */
    public boolean focusPrev(UiContext ctx) {
        return step(ctx, -1);
    }

    /** Focuses the first focusable node under the scope. */
    public boolean focusFirst(UiContext ctx) {
        List<UiNode> order = traversal();
        if (order.isEmpty()) {
            clear(ctx);
            return false;
        }
        focus(ctx, order.get(0));
        return true;
    }

    /** Drops focus when the focused node left the tree, became hidden or disabled. */
    public void validate(UiContext ctx) {
        if (focused != null && (!focused.canFocus() || !inScope(focused))) {
            clear(ctx);
        }
    }

    private boolean inScope(UiNode node) {
        UiNode root = scope.get();
        return root != null && root.isAncestorOf(node);
    }

    private boolean step(UiContext ctx, int direction) {
        List<UiNode> order = traversal();
        if (order.isEmpty()) {
            clear(ctx);
            return false;
        }
        int index = focused == null ? -1 : order.indexOf(focused);
        int next;
        if (index < 0) {
            next = direction > 0 ? 0 : order.size() - 1;
        } else {
            next = Math.floorMod(index + direction, order.size());
        }
        focus(ctx, order.get(next));
        return true;
    }
}
