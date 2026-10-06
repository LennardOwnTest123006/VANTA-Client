package dev.vanta.core.screen;

import dev.vanta.core.hud.editor.WidgetListPanel;
import dev.vanta.core.screen.mods.PackItemToggle;
import dev.vanta.core.screen.mods.ProjectRow;
import dev.vanta.core.screen.packs.PackRow;
import dev.vanta.core.ui.PopupLayer;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.ColorField;
import dev.vanta.core.ui.widget.IconButton;
import dev.vanta.core.ui.widget.KeyCaptureField;
import dev.vanta.core.ui.widget.ListView;
import dev.vanta.core.ui.widget.Select;
import dev.vanta.core.ui.widget.Slider;
import dev.vanta.core.ui.widget.Tabs;
import dev.vanta.core.ui.widget.TextField;
import dev.vanta.core.ui.widget.Toggle;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks a laid-out screen tree and checks that every interactive node can actually be clicked: its bounds must
 * intersect the screen (and the viewport of every enclosing {@link ScrollPanel}), every ancestor's bounds must
 * contain the click point (that is what {@link UiNode#mouseDown} requires before it descends into a child) and
 * {@link UiNode#hitTest} from the root must land on the node or one of its descendants.
 * <p>
 * Nodes inside a scroll panel that are scrolled completely out of the viewport are skipped: the user can reach
 * them with the wheel. Nodes that overflow a plain container are the bug this walker hunts.
 * <p>
 * Two interactive nodes that are not nested must not share a visible pixel either: the one drawn later would
 * swallow clicks meant for the other (the toolbar overlap of the HUD editor at 320x240).
 */
public final class ReachabilityWalker {

    /** One unreachable interactive node. */
    public record Failure(String screen, String size, String node, Rect bounds, String reason) {
        @Override
        public String toString() {
            return screen + " @ " + size + ": " + node + " bounds=" + fmt(bounds) + " -> " + reason;
        }
    }

    /** Result of one walk. */
    public record Report(int interactive, int skippedScrolledOut, List<Failure> failures) {
    }

    private ReachabilityWalker() {
    }

    /** Whether the node is one the user is meant to click, type into or drag. */
    public static boolean isInteractive(UiNode node) {
        return node.isFocusable()
                || node instanceof Button
                || node instanceof Toggle
                || node instanceof Tabs
                || node instanceof IconButton
                || node instanceof Select<?>
                || node instanceof Slider<?>
                || node instanceof TextField
                || node instanceof ColorField
                || node instanceof KeyCaptureField
                || node instanceof ListView<?>
                || node instanceof ProjectRow
                || node instanceof PackItemToggle
                || node instanceof PackRow
                || node instanceof WidgetListPanel.WidgetRow;
    }

    /** An interactive node and the part of it that is visible (clipped to the screen and its scroll viewports). */
    private record Placed(UiNode node, Rect visible) {
    }

    /** Walks the screen's root tree (or the top popup when a modal popup is open). */
    public static Report walk(UiScreen screen, String screenName, String sizeName) {
        List<Failure> failures = new ArrayList<>();
        List<Placed> placed = new ArrayList<>();
        int[] counters = new int[2];
        Rect screenRect = new Rect(0, 0, screen.width(), screen.height());
        PopupLayer popups = screen.context().popups();
        if (popups.isOpen() && popups.top().modal()) {
            UiNode popup = popups.top().node();
            visit(popup, popup, new ArrayList<>(), screenRect, screenRect, false, screenName, sizeName, counters,
                    failures, placed);
        } else {
            visit(screen.root(), screen.root(), new ArrayList<>(), screenRect, screenRect, false, screenName,
                    sizeName, counters, failures, placed);
        }
        checkOverlaps(placed, screenName, sizeName, failures);
        return new Report(counters[0], counters[1], List.copyOf(failures));
    }

    /** Every pair of visible interactive nodes that are not nested in each other must be disjoint. */
    private static void checkOverlaps(List<Placed> placed, String screenName, String sizeName,
                                      List<Failure> failures) {
        for (int i = 0; i < placed.size(); i++) {
            Placed a = placed.get(i);
            for (int j = i + 1; j < placed.size(); j++) {
                Placed b = placed.get(j);
                if (isSelfOrDescendant(a.node(), b.node()) || isSelfOrDescendant(b.node(), a.node())) {
                    continue;
                }
                if (a.visible().intersects(b.visible())) {
                    failures.add(new Failure(screenName, sizeName, describe(a.node()), a.node().bounds(),
                            "OVERLAP: shares pixels " + fmt(a.visible().intersect(b.visible())) + " with "
                                    + describe(b.node()) + " bounds=" + fmt(b.node().bounds())));
                }
            }
        }
    }

    /** Checks one specific node of a laid-out screen. */
    public static List<Failure> check(UiScreen screen, UiNode node, String screenName, String sizeName) {
        List<UiNode> ancestors = new ArrayList<>();
        Rect clip = new Rect(0, 0, screen.width(), screen.height());
        boolean inScroll = false;
        for (UiNode a = node.parent(); a != null; a = a.parent()) {
            ancestors.add(0, a);
        }
        for (UiNode a : ancestors) {
            if (a instanceof ScrollPanel) {
                clip = clip.intersect(a.bounds());
                inScroll = true;
            }
        }
        List<Failure> failures = new ArrayList<>();
        // An explicit check asks about THIS node: being scrolled out of view is a finding, not a skip.
        checkNode(screen.root(), node, ancestors, clip, false, screenName, sizeName, new int[2], failures);
        if (inScroll) {
            for (int i = 0; i < failures.size(); i++) {
                Failure f = failures.get(i);
                if (f.reason().startsWith("OFF_SCREEN")) {
                    failures.set(i, new Failure(f.screen(), f.size(), f.node(), f.bounds(),
                            "SCROLLED_OUT: no pixel inside the scroll viewport " + fmt(clip)));
                }
            }
        }
        return failures;
    }

    /** The visible part of the node: its bounds intersected with the screen and every enclosing scroll viewport. */
    public static Rect visibleRect(UiScreen screen, UiNode node) {
        Rect clip = new Rect(0, 0, screen.width(), screen.height());
        for (UiNode a = node.parent(); a != null; a = a.parent()) {
            if (a instanceof ScrollPanel) {
                clip = clip.intersect(a.bounds());
            }
        }
        return clip.intersect(node.bounds());
    }

    /** The nearest enclosing scroll panel, or {@code null}. */
    public static ScrollPanel enclosingScroll(UiNode node) {
        for (UiNode a = node.parent(); a != null; a = a.parent()) {
            if (a instanceof ScrollPanel s) {
                return s;
            }
        }
        return null;
    }

    private static void visit(UiNode root, UiNode node, List<UiNode> ancestors, Rect screenRect, Rect clip,
                              boolean inScroll, String screenName, String sizeName, int[] counters,
                              List<Failure> failures, List<Placed> placed) {
        if (!node.isVisible()) {
            return; // not rendered, nothing to reach
        }
        boolean enabled = node.isEnabled() && ancestors.stream().allMatch(UiNode::isEnabled);
        if (node != root && enabled && isInteractive(node)) {
            checkNode(root, node, ancestors, clip, inScroll, screenName, sizeName, counters, failures);
            Rect visible = clip.intersect(node.bounds());
            if (!visible.isEmpty()) {
                placed.add(new Placed(node, visible));
            }
        }
        Rect childClip = clip;
        boolean childInScroll = inScroll;
        if (node instanceof ScrollPanel) {
            childClip = clip.intersect(node.bounds());
            childInScroll = true;
        }
        ancestors.add(node);
        for (UiNode child : node.children()) {
            visit(root, child, ancestors, screenRect, childClip, childInScroll, screenName, sizeName, counters,
                    failures, placed);
        }
        ancestors.remove(ancestors.size() - 1);
    }

    private static void checkNode(UiNode root, UiNode node, List<UiNode> ancestors, Rect clip, boolean inScroll,
                                  String screenName, String sizeName, int[] counters, List<Failure> failures) {
        Rect b = node.bounds();
        String name = describe(node);
        if (b.isEmpty()) {
            counters[0]++;
            failures.add(new Failure(screenName, sizeName, name, b, "EMPTY: laid out with zero size"));
            return;
        }
        Rect visible = clip.intersect(b);
        if (visible.isEmpty()) {
            if (inScroll) {
                // Inside a scroll panel but not in its viewport: reachable with the wheel, not a bug.
                counters[1]++;
                return;
            }
            counters[0]++;
            failures.add(new Failure(screenName, sizeName, name, b,
                    "OFF_SCREEN: no pixel inside " + fmt(clip)));
            return;
        }
        counters[0]++;
        double px = visible.x() + visible.w() / 2.0;
        double py = visible.y() + visible.h() / 2.0;
        boolean ok = true;
        for (UiNode a : ancestors) {
            if (a == root) {
                continue; // the screen routes to the root without a bounds check
            }
            if (!a.bounds().contains(px, py)) {
                failures.add(new Failure(screenName, sizeName, name, b,
                        "ANCESTOR_CLIPS: click at (" + px + "," + py + ") is outside " + describe(a) + " bounds="
                                + fmt(a.bounds()) + " so mouseDown never descends"));
                ok = false;
                break;
            }
        }
        if (!ok) {
            return;
        }
        UiNode hit = root.hitTest(px, py);
        if (hit == null || !isSelfOrDescendant(node, hit)) {
            failures.add(new Failure(screenName, sizeName, name, b,
                    "HIT_MISS: hitTest(" + px + "," + py + ") returned " + (hit == null ? "null" : describe(hit)
                            + " bounds=" + fmt(hit.bounds()))));
        }
    }

    private static boolean isSelfOrDescendant(UiNode node, UiNode candidate) {
        for (UiNode n = candidate; n != null; n = n.parent()) {
            if (n == node) {
                return true;
            }
        }
        return false;
    }

    /** Class name plus id (when set). */
    public static String describe(UiNode node) {
        String cls = node.getClass().getSimpleName();
        if (cls.isEmpty()) {
            cls = node.getClass().getName();
        }
        String id = node.id();
        String label = "";
        if (node instanceof Button b) {
            label = " \"" + b.label() + "\"";
        }
        return cls + (id != null ? "#" + id : "") + label;
    }

    private static String fmt(Rect r) {
        return "[" + r.x() + "," + r.y() + " " + r.w() + "x" + r.h() + " -> " + r.right() + "," + r.bottom() + "]";
    }

    /**
     * The scale a {@link UiScreen} uses for a theme scale at a host size: the theme scale, lowered (never below 1)
     * so the logical area keeps at least {@link UiScreen#MIN_LOGICAL_WIDTH}x{@link UiScreen#MIN_LOGICAL_HEIGHT}
     * pixels (see {@link UiScreen#effectiveScale()}). 427x240 with large text (1.15) renders at 1, 480x270 at
     * 1.125 and 640x360 at the full 1.15.
     */
    public static float expectedScale(float themeScale, int hostWidth, int hostHeight) {
        float allowed = Math.min(hostWidth / (float) UiScreen.MIN_LOGICAL_WIDTH,
                hostHeight / (float) UiScreen.MIN_LOGICAL_HEIGHT);
        return Math.min(themeScale, Math.max(1f, allowed));
    }
}
