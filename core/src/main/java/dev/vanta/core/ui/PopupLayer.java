package dev.vanta.core.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Stack of overlay nodes drawn above the screen content: dropdowns, color palettes and modal dialogs. The top
 * popup receives input first; clicks outside a non-modal popup dismiss it, clicks outside a modal one are
 * swallowed. Focus is scoped to the top popup while it is open and restored when it closes.
 */
public final class PopupLayer {

    /**
     * An open popup.
     *
     * @param node          the popup content (positions itself through its bounds)
     * @param modal         whether input outside the popup is blocked
     * @param onClose       callback run after the popup closed (may be {@code null})
     * @param previousFocus node focused before the popup opened
     */
    public record Popup(UiNode node, boolean modal, Runnable onClose, UiNode previousFocus) {
    }

    private final List<Popup> stack = new ArrayList<>();
    private Supplier<UiNode> baseScope = () -> null;

    /** The focus scope used when no popup is open (the screen root). */
    public void setBaseScope(Supplier<UiNode> root) {
        this.baseScope = root == null ? () -> null : root;
    }

    /** Opens a popup; the node must already have bounds. */
    public Popup open(UiContext ctx, UiNode node, boolean modal, Runnable onClose) {
        Popup popup = new Popup(node, modal, onClose, ctx.focus().focused());
        stack.add(popup);
        node.layout(ctx);
        applyScope(ctx);
        ctx.focus().focusFirst(ctx);
        return popup;
    }

    /** Closes the popup showing {@code node} (no-op when it is not open). */
    public void close(UiContext ctx, UiNode node) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).node() == node) {
                closeAt(ctx, i);
                return;
            }
        }
    }

    /** Closes the top-most popup. */
    public boolean closeTop(UiContext ctx) {
        if (stack.isEmpty()) {
            return false;
        }
        closeAt(ctx, stack.size() - 1);
        return true;
    }

    /** Closes everything. */
    public void closeAll(UiContext ctx) {
        while (!stack.isEmpty()) {
            closeAt(ctx, stack.size() - 1);
        }
    }

    private void closeAt(UiContext ctx, int index) {
        Popup popup = stack.remove(index);
        ctx.releaseMouse();
        applyScope(ctx);
        UiNode previous = popup.previousFocus();
        if (previous != null && previous.canFocus() && inScope(previous)) {
            ctx.focus().focus(ctx, previous);
        } else {
            ctx.focus().validate(ctx);
        }
        if (popup.onClose() != null) {
            popup.onClose().run();
        }
    }

    private boolean inScope(UiNode node) {
        UiNode root = ctxScopeRoot();
        return root != null && root.isAncestorOf(node);
    }

    private UiNode ctxScopeRoot() {
        return stack.isEmpty() ? baseScope.get() : stack.get(stack.size() - 1).node();
    }

    private void applyScope(UiContext ctx) {
        ctx.focus().setScope(this::ctxScopeRoot);
        ctx.focus().validate(ctx);
    }

    /** Whether any popup is open. */
    public boolean isOpen() {
        return !stack.isEmpty();
    }

    /** Whether a popup showing {@code node} is open. */
    public boolean isOpen(UiNode node) {
        for (Popup p : stack) {
            if (p.node() == node) {
                return true;
            }
        }
        return false;
    }

    /** The top-most popup or {@code null}. */
    public Popup top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    /** Open popups, bottom first. */
    public List<Popup> popups() {
        return Collections.unmodifiableList(stack);
    }

    /** Re-runs layout of every popup. */
    public void layout(UiContext ctx) {
        for (Popup p : stack) {
            p.node().layout(ctx);
        }
    }

    /** Renders popups bottom-up; modal ones dim everything beneath. */
    public void render(Canvas canvas, UiContext ctx) {
        for (Popup p : stack) {
            if (p.modal()) {
                canvas.fill(0, 0, ctx.screenWidth(), ctx.screenHeight(), ctx.theme().modalDim());
            }
            p.node().render(canvas, ctx);
        }
    }

    /** Deepest popup node under the point (top popup only), or {@code null}. */
    public UiNode hitTest(double x, double y) {
        Popup top = top();
        if (top == null) {
            return null;
        }
        UiNode hit = top.node().hitTest(x, y);
        if (hit == null && top.modal()) {
            return top.node();
        }
        return hit;
    }

    /** Ticks every popup. */
    public void tick(UiContext ctx) {
        for (Popup p : new ArrayList<>(stack)) {
            p.node().onTick(ctx);
        }
    }

    /**
     * Handles a press. Inside the top popup the event is forwarded; outside, a non-modal popup is dismissed and
     * the press is consumed, a modal popup swallows the press.
     */
    public boolean mouseDown(UiContext ctx, double x, double y, int button) {
        Popup top = top();
        if (top == null) {
            return false;
        }
        if (top.node().bounds().contains(x, y)) {
            top.node().mouseDown(ctx, x, y, button);
            return true;
        }
        if (!top.modal()) {
            closeTop(ctx);
        }
        return true;
    }

    /** Forwards a release to the top popup when inside it. */
    public boolean mouseUp(UiContext ctx, double x, double y, int button) {
        Popup top = top();
        if (top == null) {
            return false;
        }
        if (top.node().bounds().contains(x, y)) {
            top.node().mouseUp(ctx, x, y, button);
        }
        return true;
    }

    /** Forwards a drag to the top popup. */
    public boolean mouseDrag(UiContext ctx, double x, double y, int button, double dx, double dy) {
        Popup top = top();
        if (top == null) {
            return false;
        }
        top.node().mouseDrag(ctx, x, y, button, dx, dy);
        return true;
    }

    /** Wheel inside the top popup is forwarded; outside a non-modal popup it dismisses it. */
    public boolean mouseScroll(UiContext ctx, double x, double y, double scrollX, double scrollY) {
        Popup top = top();
        if (top == null) {
            return false;
        }
        if (top.node().bounds().contains(x, y)) {
            top.node().mouseScroll(ctx, x, y, scrollX, scrollY);
            return true;
        }
        if (!top.modal()) {
            closeTop(ctx);
        }
        return true;
    }

    /** Escape closes the top popup. Other keys are not handled here. */
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (!isOpen()) {
            return false;
        }
        if (key == Keys.ESCAPE) {
            closeTop(ctx);
            return true;
        }
        return false;
    }
}
