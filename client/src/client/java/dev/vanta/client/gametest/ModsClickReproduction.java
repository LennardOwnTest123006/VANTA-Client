package dev.vanta.client.gametest;

import com.mojang.blaze3d.platform.Window;
import dev.vanta.client.screen.VantaScreen;
import dev.vanta.client.screen.VantaScreens;
import dev.vanta.core.modrinth.ModrinthSearchHit;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.mods.ModsScreen;
import dev.vanta.core.screen.mods.ModsTab;
import dev.vanta.core.screen.mods.ProjectRow;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.ScrollIntoView;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Tabs;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Reproduces the "Install only works in fullscreen" report in the real game: at several window sizes the Mods &amp;
 * Shaders screen is opened and its buttons are clicked exactly the way a player's mouse does it (cursor position and
 * button press delivered through {@code MouseHandler.onMove} / {@code MouseHandler.onButton}, which the Fabric client
 * game test input drives through its own accessors). The effect of every click is then verified through the core
 * screen's state.
 * <p>
 * A click that has no effect is collected as a finding instead of aborting the test; the caller fails the test once
 * at the very end so the frame-cost probe and the remaining sizes still run. Findings are labelled
 * {@code REPRODUCED} when the geometry explains the failure (the button lies outside the screen or outside an
 * ancestor's bounds, so {@link UiNode#mouseDown} never descends into it) and {@code UNEXPECTED} otherwise.
 * <p>
 * The Install buttons are clicked with their action swapped for a flag: the routing up to the button's own
 * {@code onClick} is identical, but no mod is downloaded into the CI run's mods folder, so the check can be repeated
 * at every size and in both the menu and the in-world situation.
 */
final class ModsClickReproduction {
    /** Window size and GUI scale of one pass. */
    record WindowSetup(int width, int height, int guiScale) {
        String tag() {
            return width + "x" + height + "_gui" + guiScale;
        }
    }

    /**
     * 854x480 at GUI scale 2 is the default windowed game (427x240 GUI px); 1920x1080 at scale 4 is a maximised
     * window on a 1080p screen with "large" GUI (480x270); 1920x1080 at scale 2 (960x540) is the capture size
     * where everything fits.
     */
    static final List<WindowSetup> SETUPS = List.of(
            new WindowSetup(854, 480, 2),
            new WindowSetup(1920, 1080, 4),
            new WindowSetup(1920, 1080, 2));

    /** Ticks the live Modrinth search may take (20 s). */
    private static final int MODRINTH_TIMEOUT_TICKS = 400;
    /** Ticks for a scroll animation and the following re-layout to settle. */
    private static final int SETTLE_TICKS = 8;
    private static final double CURSOR_TOLERANCE = 2.0;
    private static final int SHADERS_TAB = ModsTab.SHADERS.ordinal();

    private ModsClickReproduction() {
    }

    /** Host geometry as the core screen sees it. */
    private record Host(int screenWidth, int screenHeight, int guiWidth, int guiHeight, int guiScale,
                        int logicalWidth, int logicalHeight, float effectiveScale) {
        String describe() {
            return String.format(Locale.ROOT, "window %dx%d, GUI scale %d → %dx%d GUI px, core screen %dx%d "
                            + "logical px (effective scale %.2f)", screenWidth, screenHeight, guiScale, guiWidth,
                    guiHeight, logicalWidth, logicalHeight, effectiveScale);
        }
    }

    /** One node to click: where it is and whether the input routing can reach its centre (client thread). */
    private record Target(String label, boolean found, String problem, Rect bounds, Rect visible, double lx,
                          double ly, boolean enabled, boolean showing, String blockedBy, String hit, boolean hitOk,
                          float scale) {
        static Target missing(String label, String problem) {
            return new Target(label, false, problem, Rect.EMPTY, Rect.EMPTY, 0.0, 0.0, false, false, null, null,
                    false, 1f);
        }

        /** Whether the core's own hit testing says the centre reaches the node. */
        boolean geometryOk() {
            return found && !visible.isEmpty() && blockedBy == null && hitOk;
        }

        String describe() {
            if (!found) {
                return label + ": " + problem;
            }
            return String.format(Locale.ROOT, "%s bounds=%s visible=%s centre=(%.1f,%.1f) logical px, enabled=%s "
                            + "showing=%s, %s, hitTest→%s", label, fmt(bounds), fmt(visible), lx, ly, enabled, showing,
                    blockedBy == null ? "every ancestor contains the centre"
                            : "centre OUTSIDE ancestor " + blockedBy + " so mouseDown never descends",
                    hit + (hitOk ? " (the node)" : " (NOT the node)"));
        }
    }

    /** What the injected click did. */
    private record ClickResult(boolean tried, boolean effect, double hostX, double hostY, double[] window,
                               double[] cursor, String problem) {
        static ClickResult notTried(String problem) {
            return new ClickResult(false, false, 0.0, 0.0, new double[2], new double[2], problem);
        }

        String describe() {
            if (!tried) {
                return "click not sent: " + problem;
            }
            return String.format(Locale.ROOT, "clicked at host (%.1f,%.1f) GUI px = window (%.1f,%.1f) px, cursor "
                    + "read back at (%.1f,%.1f)", hostX, hostY, window[0], window[1], cursor[0], cursor[1]);
        }
    }

    /**
     * Runs every window setup once.
     *
     * @param situation "menu" (no world) or "world" (in a world), used in log lines and screenshot names
     * @param shotBase  number of the first screenshot
     * @param failures  findings are appended here
     */
    static void run(ClientGameTestContext context, VantaServices services, String situation, int shotBase,
                    List<String> failures) {
        int shot = shotBase;
        for (WindowSetup setup : SETUPS) {
            String tag = setup.tag() + "/" + situation;
            applyWindow(context, setup);
            context.setScreen(() -> VantaScreens.create(ScreenId.MODS, null));
            context.waitFor(client -> client.screen instanceof VantaScreen vanta && vanta.screenId() == ScreenId.MODS);
            boolean answered = VantaClientGameTest.pollFor(context, client -> mods(client) != null
                    && !mods(client).isSearching(), MODRINTH_TIMEOUT_TICKS);
            String state = context.computeOnClient(client -> {
                ModsScreen mods = mods(client);
                if (mods == null) {
                    return "not the Mods screen";
                }
                return mods.searchError().map(e -> "Modrinth error " + e.kind())
                        .orElse(mods.results().hits().size() + " Modrinth results, " + mods.rows().size() + " rows");
            });
            Host host = host(context);
            VantaClientGameTest.step("click reproduction [" + tag + "]: " + host.describe() + "; "
                    + (answered ? state : "Modrinth did not answer within 20 s (" + state + ")"));
            parkCursor(context, setup);
            context.waitTicks(5);
            Path before = context.takeScreenshot(shotName(shot, setup, situation, "before"));

            ModrinthSearchHit picked = checkRow(context, tag, failures);
            checkDetailInstall(context, tag, picked, failures);
            checkPackInstall(context, tag, failures);
            checkTabs(context, tag, failures);

            parkCursor(context, setup);
            context.waitTicks(5);
            Path after = context.takeScreenshot(shotName(shot + 1, setup, situation, "after"));
            VantaClientGameTest.step("click reproduction [" + tag + "] → " + before.getFileName() + ", "
                    + after.getFileName());
            shot += 2;
            context.setScreen(() -> null);
            context.waitTicks(5);
        }
    }

    /** Resizes the game window and sets the GUI scale the way the main test does for its captures. */
    static void applyWindow(ClientGameTestContext context, WindowSetup setup) {
        context.getInput().resizeWindow(setup.width(), setup.height());
        context.runOnClient(client -> {
            client.options.guiScale().set(setup.guiScale());
            client.resizeDisplay();
        });
        context.waitTicks(2);
    }

    // ---- the four checks ---------------------------------------------------------------------------------------

    /**
     * Clicks the first result row that is not installed yet (its detail shows the Install button); the selection
     * must change to that project. Returns the hit, or {@code null} when there is no such row.
     */
    private static ModrinthSearchHit checkRow(ClientGameTestContext context, String tag, List<String> failures) {
        record Pick(ModrinthSearchHit hit, String rowId) {
        }
        Pick pick = context.computeOnClient(client -> {
            ModsScreen mods = mods(client);
            if (mods == null) {
                return null;
            }
            List<ProjectRow> rows = mods.rows();
            List<ModrinthSearchHit> hits = mods.results().hits();
            for (int i = 0; i < Math.min(rows.size(), hits.size()); i++) {
                if (rows.get(i).model().badge().isEmpty() && hits.get(i).type().isPresent()) {
                    return new Pick(hits.get(i), rows.get(i).id());
                }
            }
            return null;
        });
        if (pick == null) {
            VantaClientGameTest.warn("[" + tag + "] no installable Modrinth result row (no answer, or every result "
                    + "is installed): the row and detail Install checks are skipped");
            return null;
        }
        reveal(context, pick.rowId());
        Target target = locate(context, "result row " + pick.rowId(), pick.rowId(), null);
        String projectId = pick.hit().projectId();
        ClickResult result = click(context, target, client -> mods(client) != null
                && mods(client).selectedKey().filter(projectId::equals).isPresent());
        recordOutcome(tag, target, result, "selectedKey becomes " + projectId, failures);
        if (!result.effect()) {
            context.runOnClient(client -> {
                ModsScreen mods = mods(client);
                if (mods != null) {
                    mods.selectHit(pick.hit());
                }
            });
            VantaClientGameTest.step("[" + tag + "] selected " + projectId + " programmatically so the detail check "
                    + "can run");
        }
        context.waitTicks(2);
        return pick.hit();
    }

    /** Clicks the detail panel's Install button (action swapped for a flag). */
    private static void checkDetailInstall(ClientGameTestContext context, String tag, ModrinthSearchHit picked,
                                           List<String> failures) {
        if (picked == null) {
            return;
        }
        Target target = locate(context, "detail Install button mods.action.install", "mods.action.install", null);
        if (!target.found()) {
            VantaClientGameTest.warn("[" + tag + "] " + target.describe() + " (is " + picked.slug()
                    + " already installed?): detail Install check skipped");
            return;
        }
        AtomicBoolean clicked = arm(context, "mods.action.install");
        ClickResult result = click(context, target, client -> clicked.get());
        recordOutcome(tag, target, result, "the Install action runs", failures);
    }

    /** Scrolls the Performance pack card into view and clicks its Install button (action swapped for a flag). */
    private static void checkPackInstall(ClientGameTestContext context, String tag, List<String> failures) {
        reveal(context, "mods.pack.install");
        Target target = locate(context, "Performance pack Install button mods.pack.install", "mods.pack.install",
                null);
        if (!target.found()) {
            VantaClientGameTest.warn("[" + tag + "] " + target.describe() + ": pack Install check skipped");
            return;
        }
        if (!target.enabled()) {
            VantaClientGameTest.step("[" + tag + "] the pack Install button is disabled (pack installed or an "
                    + "install is running): pack Install check skipped; " + target.describe());
            return;
        }
        AtomicBoolean clicked = arm(context, "mods.pack.install");
        ClickResult result = click(context, target, client -> clicked.get());
        recordOutcome(tag, target, result, "the pack Install action runs", failures);
    }

    /** Clicks the Shaders tab; the screen's tab must change. */
    private static void checkTabs(ClientGameTestContext context, String tag, List<String> failures) {
        Target target = locate(context, "Shaders tab of mods.tabs", "mods.tabs",
                (node, ctx) -> node instanceof Tabs tabs ? tabs.tabRect(ctx, SHADERS_TAB) : node.bounds());
        if (!target.found()) {
            VantaClientGameTest.warn("[" + tag + "] " + target.describe() + ": tabs check skipped");
            return;
        }
        ClickResult result = click(context, target, client -> mods(client) != null
                && mods(client).tab() == ModsTab.SHADERS);
        recordOutcome(tag, target, result, "tab() becomes SHADERS", failures);
    }

    // ---- node geometry and clicking ----------------------------------------------------------------------------

    private static Target locate(ClientGameTestContext context, String label, String id,
                                 BiFunction<UiNode, UiContext, Rect> rectOf) {
        return context.computeOnClient(client -> {
            VantaScreen vanta = client.screen instanceof VantaScreen v && v.ui() instanceof ModsScreen ? v : null;
            if (vanta == null) {
                return Target.missing(label, "the Mods screen is not open (" + client.screen + ")");
            }
            UiScreen ui = vanta.ui();
            UiNode node = ui.root().findById(id);
            if (node == null) {
                return Target.missing(label, "no node with id " + id);
            }
            UiContext ctx = ui.context();
            Rect bounds = rectOf == null ? node.bounds() : rectOf.apply(node, ctx);
            Rect clip = new Rect(0, 0, ui.width(), ui.height());
            for (UiNode a = node.parent(); a != null; a = a.parent()) {
                if (a instanceof ScrollPanel) {
                    clip = clip.intersect(a.bounds());
                }
            }
            Rect visible = clip.intersect(bounds);
            Rect centreOf = visible.isEmpty() ? bounds : visible;
            double lx = centreOf.x() + centreOf.w() / 2.0;
            double ly = centreOf.y() + centreOf.h() / 2.0;
            String blockedBy = null;
            for (UiNode a = node.parent(); a != null && a != ui.root(); a = a.parent()) {
                if (!a.bounds().contains(lx, ly)) {
                    // Keep walking so the outermost blocking ancestor is reported (the one the click dies at).
                    blockedBy = describe(a) + " bounds=" + fmt(a.bounds());
                }
            }
            UiNode hit = ui.root().hitTest(lx, ly);
            boolean hitOk = false;
            for (UiNode n = hit; n != null; n = n.parent()) {
                if (n == node) {
                    hitOk = true;
                    break;
                }
            }
            return new Target(label, true, null, bounds, visible, lx, ly, node.isEffectivelyEnabled(),
                    node.isShowing(), blockedBy, hit == null ? "null" : describe(hit) + " bounds=" + fmt(hit.bounds()),
                    hitOk, ui.effectiveScale());
        });
    }

    /**
     * Moves the cursor to the node's centre and presses the left button the way GLFW would, then evaluates
     * {@code effect} on the client thread.
     */
    private static ClickResult click(ClientGameTestContext context, Target target, Predicate<Minecraft> effect) {
        if (!target.found()) {
            return ClickResult.notTried(target.problem());
        }
        // Node bounds are logical pixels; the host screen works in GUI pixels; GLFW reports window pixels.
        double hostX = target.lx() * target.scale();
        double hostY = target.ly() * target.scale();
        double[] window = context.computeOnClient(client -> {
            Window w = client.getWindow();
            return new double[]{hostX * w.getScreenWidth() / w.getGuiScaledWidth(),
                    hostY * w.getScreenHeight() / w.getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(window[0], window[1]);
        double[] cursor = context.computeOnClient(client ->
                new double[]{client.mouseHandler.xpos(), client.mouseHandler.ypos()});
        if (Math.abs(cursor[0] - window[0]) > CURSOR_TOLERANCE || Math.abs(cursor[1] - window[1]) > CURSOR_TOLERANCE) {
            return new ClickResult(false, false, hostX, hostY, window, cursor, String.format(Locale.ROOT,
                    "the cursor ended at window (%.1f,%.1f) instead of (%.1f,%.1f)", cursor[0], cursor[1], window[0],
                    window[1]));
        }
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        boolean ok = context.computeOnClient(effect::test);
        return new ClickResult(true, ok, hostX, hostY, window, cursor, null);
    }

    /** Replaces a button's action with a flag so the click can be observed without its side effect. */
    private static AtomicBoolean arm(ClientGameTestContext context, String id) {
        AtomicBoolean clicked = new AtomicBoolean();
        context.runOnClient(client -> {
            ModsScreen mods = mods(client);
            if (mods != null && mods.root().findById(id) instanceof Button button) {
                button.onClick(() -> clicked.set(true));
            }
        });
        return clicked;
    }

    /** Scrolls every scroll panel around the node so it is in view, like focusing it with the keyboard does. */
    private static void reveal(ClientGameTestContext context, String id) {
        context.runOnClient(client -> {
            ModsScreen mods = mods(client);
            if (mods == null) {
                return;
            }
            UiNode node = mods.root().findById(id);
            if (node != null) {
                ScrollIntoView.reveal(mods.context(), node);
            }
        });
        context.waitTicks(SETTLE_TICKS);
    }

    private static void recordOutcome(String tag, Target target, ClickResult result, String expectation,
                                      List<String> failures) {
        String detail = target.describe() + "; " + result.describe();
        if (result.effect()) {
            VantaClientGameTest.step("[" + tag + "] OK: " + target.label() + " → " + expectation + " (" + detail + ")");
            return;
        }
        String kind = !result.tried() ? "INCONCLUSIVE" : target.geometryOk() ? "UNEXPECTED" : "REPRODUCED";
        String line = kind + " [" + tag + "] " + target.label() + ": " + expectation + " did NOT happen; " + detail;
        VantaClientGameTest.warn(line);
        failures.add(line);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private static ModsScreen mods(Minecraft client) {
        return client.screen instanceof VantaScreen vanta && vanta.ui() instanceof ModsScreen mods ? mods : null;
    }

    private static Host host(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            Window w = client.getWindow();
            ModsScreen mods = mods(client);
            return new Host(w.getScreenWidth(), w.getScreenHeight(), w.getGuiScaledWidth(), w.getGuiScaledHeight(),
                    client.options.guiScale().get(), mods == null ? 0 : mods.width(), mods == null ? 0 : mods.height(),
                    mods == null ? 1f : mods.effectiveScale());
        });
    }

    private static void parkCursor(ClientGameTestContext context, WindowSetup setup) {
        context.getInput().setCursorPos(setup.width() - 2, setup.height() - 2);
    }

    private static String shotName(int number, WindowSetup setup, String situation, String phase) {
        return String.format(Locale.ROOT, "%02d_mods_%s_%s_%s", number, setup.tag(), situation, phase);
    }

    private static String describe(UiNode node) {
        String name = node.getClass().getSimpleName();
        if (name.isEmpty()) {
            name = node.getClass().getName();
        }
        String id = node.id();
        String label = node instanceof Button b ? " \"" + b.label() + "\"" : "";
        return name + (id != null ? "#" + id : "") + label;
    }

    private static String fmt(Rect r) {
        return "[" + r.x() + "," + r.y() + " " + r.w() + "x" + r.h() + " → " + r.right() + "," + r.bottom() + "]";
    }
}
