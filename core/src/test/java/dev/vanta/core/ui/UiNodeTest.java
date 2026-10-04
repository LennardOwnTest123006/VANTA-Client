package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class UiNodeTest {

    /** Plain node that records whether its own handler ran. */
    static final class Probe extends UiNode {
        boolean consume;
        int downs;

        Probe(boolean consume) {
            this.consume = consume;
        }

        @Override
        protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
            downs++;
            return consume;
        }
    }

    private final UiTestSupport t = new UiTestSupport();

    @Test
    void addReparentsAndRemoveDetaches() {
        Probe a = new Probe(false);
        Probe b = new Probe(false);
        Probe child = new Probe(false);
        a.add(child);
        assertSame(a, child.parent());
        b.add(child);
        assertSame(b, child.parent());
        assertTrue(a.children().isEmpty());
        b.remove(child);
        assertNull(child.parent());
        assertThrows(IllegalArgumentException.class, () -> a.add(a));
        assertThrows(UnsupportedOperationException.class, () -> a.children().add(b));
    }

    @Test
    void hitTestReturnsTopMostDeepestChild() {
        Probe root = new Probe(false);
        root.setBounds(0, 0, 100, 100);
        Probe bottom = root.add(new Probe(false));
        bottom.setBounds(10, 10, 50, 50);
        Probe top = root.add(new Probe(false));
        top.setBounds(30, 30, 50, 50);
        Probe nested = top.add(new Probe(false));
        nested.setBounds(40, 40, 10, 10);
        assertSame(nested, root.hitTest(45, 45));
        assertSame(top, root.hitTest(70, 70));
        assertSame(bottom, root.hitTest(15, 15));
        assertSame(root, root.hitTest(5, 5));
        assertNull(root.hitTest(150, 150));
        top.setVisible(false);
        assertSame(bottom, root.hitTest(45, 45));
    }

    @Test
    void mouseDownRoutesToChildrenFirstAndStopsWhenConsumed() {
        Probe root = new Probe(true);
        root.setBounds(0, 0, 100, 100);
        Probe child = root.add(new Probe(true));
        child.setBounds(0, 0, 50, 50);
        assertTrue(root.mouseDown(t.ctx(), 10, 10, 0));
        assertEquals(1, child.downs);
        assertEquals(0, root.downs, "consumed by the child");
        assertTrue(root.mouseDown(t.ctx(), 80, 80, 0));
        assertEquals(1, root.downs);
        child.consume = false;
        assertTrue(root.mouseDown(t.ctx(), 10, 10, 0));
        assertEquals(2, child.downs);
        assertEquals(2, root.downs, "falls through to the parent when the child declines");
        root.setEnabled(false);
        assertFalse(root.mouseDown(t.ctx(), 10, 10, 0));
    }

    @Test
    void focusableCollectionIsDepthFirstAndSkipsHiddenOrDisabled() {
        Probe root = new Probe(false);
        Probe a = root.add(new Probe(false));
        a.setFocusable(true);
        Probe group = root.add(new Probe(false));
        Probe b = group.add(new Probe(false));
        b.setFocusable(true);
        Probe c = root.add(new Probe(false));
        c.setFocusable(true);
        Probe hidden = root.add(new Probe(false));
        hidden.setFocusable(true);
        hidden.setVisible(false);
        List<UiNode> out = new ArrayList<>();
        root.collectFocusable(out);
        assertEquals(List.of(a, b, c), out);
        group.setEnabled(false);
        out.clear();
        root.collectFocusable(out);
        assertEquals(List.of(a, c), out);
        assertFalse(b.canFocus());
        assertFalse(b.isEffectivelyEnabled());
        assertFalse(hidden.isShowing());
        assertTrue(root.isAncestorOf(b));
        assertFalse(a.isAncestorOf(b));
    }

    @Test
    void preferredSizeHonoursExplicitValues() {
        Probe n = new Probe(false);
        assertEquals(Size.ZERO, n.preferredSize(t.ctx()));
        n.size(30, -1);
        assertEquals(new Size(30, 0), n.preferredSize(t.ctx()));
        n.height(12);
        assertEquals(new Size(30, 12), n.preferredSize(t.ctx()));
        n.flex(2f);
        assertEquals(2f, n.flex(), 1e-6f);
        n.flex(-1f);
        assertEquals(0f, n.flex(), 1e-6f);
    }

    @Test
    void findByIdAndTooltip() {
        Probe root = new Probe(false);
        Probe deep = root.add(new Probe(false)).add(new Probe(false));
        deep.id("deep").tooltip("hint");
        assertSame(deep, root.findById("deep"));
        assertNull(root.findById("missing"));
        assertNull(root.findById(null));
        assertEquals("hint", deep.tooltipText());
        assertTrue(deep.toString().contains("deep"));
    }

    @Test
    void setBoundsRejectsNegativeSizesGracefully() {
        Probe n = new Probe(false);
        n.setBounds(1, 2, -5, -5);
        assertEquals(new Rect(1, 2, 0, 0), n.bounds());
        n.setEnabled(false);
        assertFalse(n.isPressed());
    }
}
