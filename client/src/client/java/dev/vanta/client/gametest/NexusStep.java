package dev.vanta.client.gametest;

import static dev.vanta.client.gametest.VantaClientGameTest.check;
import static dev.vanta.client.gametest.VantaClientGameTest.pollFor;
import static dev.vanta.client.gametest.VantaClientGameTest.step;
import static dev.vanta.client.gametest.VantaClientGameTest.warn;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vanta.client.hud.WaypointMarkerElement;
import dev.vanta.client.render.WaypointBeamRenderer;
import dev.vanta.client.render.WorldCamera;
import dev.vanta.client.screen.NexusFirstStart;
import dev.vanta.client.screen.VantaScreen;
import dev.vanta.client.screen.VantaScreens;
import dev.vanta.core.VantaVersion;
import dev.vanta.core.ai.LocalAiStatus;
import dev.vanta.core.ai.NexusActions;
import dev.vanta.core.ai.NexusHudPreset;
import dev.vanta.core.ai.NexusUndo;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.lab.LabEffects;
import dev.vanta.core.lab.LabFeature;
import dev.vanta.core.lab.LabSettings;
import dev.vanta.core.profiles.BuiltInProfiles;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.nexus.NexusSection;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.ScrollIntoView;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Tabs;
import dev.vanta.core.ui.widget.Toggle;
import dev.vanta.core.waypoints.Waypoint;
import dev.vanta.core.waypoints.WorldKeys;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;

/**
 * Vanta Nexus in the real game (runs inside the test world):
 * <ol>
 *   <li>the Nexus screen opens at the small window of the click reproduction (854x480, GUI scale 2) and at the
 *       capture size; every section the sidebar offers is visited (the VantaShell rail buttons
 *       {@code rail.<NexusSection id>}, or any button/tab whose id starts with {@code nexus.} and names a
 *       section, nav entry, tab or sidebar item) and screenshotted
 *       ({@code 50_nexus_*}); in every view each showing, enabled button and toggle must be reachable: its centre
 *       inside every ancestor's bounds and the first hit of the core's own hit test (after scrolling it into view when
 *       it sits in a scroll panel). Unreachable controls are collected as findings, judged at the end of the test;</li>
 *   <li>the "minimal" HUD preset is applied through {@code NexusActions} (the assistant's code path): exactly FPS and
 *       coordinates stay visible; Undo restores the previous layout;</li>
 *   <li>the built-in Recording profile is activated and the previous profile restored;</li>
 *   <li>a waypoint is added in front of the player through the store under the world key the bridge derives
 *       ({@code sp:<level folder>}), it is listed for that key, the marker element draws it
 *       ({@code 60_waypoint_marker}), the Waypoint beams Lab feature draws a beam ({@code 61_waypoint_beam}), and the
 *       waypoint is removed again;</li>
 *   <li>every Lab toggle flips its setting and back; the {@code LabEffects} curves behave as specified (the HUD alpha
 *       drops after 11 s without input while Dynamic HUD is on and returns on input; the crosshair spreads on attack
 *       only while Animated crosshair is on; screen transitions take 160 ms only while on);</li>
 *   <li>the first-start notice decision is checked with every relevant input.</li>
 * </ol>
 * The step ends with no screen open and the capture window restored.
 */
final class NexusStep {
    /** Name of the waypoint the step adds and removes. */
    static final String WAYPOINT_NAME = "Game test waypoint";
    /** Blocks in front of the player the waypoint is placed. */
    static final double WAYPOINT_AHEAD = 6.0;
    /** Ticks for a click, a scroll animation and the re-layout to settle. */
    private static final int SETTLE_TICKS = 6;
    /** Ticks the marker and the beam may take to appear after the store changed. */
    private static final int RENDER_TIMEOUT_TICKS = 100;
    private static final int FIRST_SHOT = 50;
    private static final List<String> NAV_HINTS = List.of("section", "nav", "tab", "sidebar");
    private static final ModsClickReproduction.WindowSetup CAPTURE =
            new ModsClickReproduction.WindowSetup(1920, 1080, 2);

    private NexusStep() {
    }

    static void run(ClientGameTestContext context, VantaServices services, List<String> findings) {
        try {
            screens(context, services, findings);
        } finally {
            ModsClickReproduction.applyWindow(context, CAPTURE);
            context.setScreen(() -> null);
            context.waitForScreen(null);
        }
        hudPreset(context, services);
        profileSwitch(context, services);
        waypoints(context, services);
        labToggles(context, services);
        firstStartDecision();
    }

    // ---- 1. screens --------------------------------------------------------------------------------------------

    private static void screens(ClientGameTestContext context, VantaServices services, List<String> findings) {
        boolean registered = context.computeOnClient(client -> services.screens().isRegistered(ScreenId.NEXUS));
        check(registered, "No screen factory registered for " + ScreenId.NEXUS.id());
        int shot = FIRST_SHOT;
        for (ModsClickReproduction.WindowSetup setup : List.of(ModsClickReproduction.SETUPS.get(0), CAPTURE)) {
            String tag = setup.tag();
            ModsClickReproduction.applyWindow(context, setup);
            context.setScreen(() -> VantaScreens.create(ScreenId.NEXUS, null));
            context.waitFor(client -> nexus(client) != null);
            context.getInput().setCursorPos(setup.width() - 2, setup.height() - 2);
            context.waitTicks(10);
            String geometry = context.computeOnClient(client -> {
                UiScreen ui = nexus(client);
                return ui == null ? "not open" : String.format(Locale.ROOT, "%dx%d logical px, effective scale %.2f",
                        ui.width(), ui.height(), ui.effectiveScale());
            });
            Path home = context.takeScreenshot(shotName(shot++, tag, "home"));
            step("nexus [" + tag + "]: opened (" + geometry + ") -> " + home.getFileName());
            checkReachable(context, tag + "/home", findings);

            List<String> sections = context.computeOnClient(client -> navIds(nexus(client)));
            if (sections.isEmpty()) {
                warn("nexus [" + tag + "]: no rail buttons (rail.<section>) and no buttons or tabs with an id starting "
                        + "with 'nexus.' and naming a section/nav/tab/sidebar entry were found; only the opening view "
                        + "was checked");
            } else {
                step("nexus [" + tag + "]: " + sections.size() + " section control(s): " + sections);
            }
            for (String section : sections) {
                boolean clicked = context.computeOnClient(client -> clickNav(nexus(client), section));
                check(clicked, "nexus [" + tag + "]: section control " + section + " disappeared before it was clicked");
                context.waitTicks(SETTLE_TICKS);
                boolean stillOpen = context.computeOnClient(client -> {
                    UiScreen ui = nexus(client);
                    return ui != null && ui.isInitialised() && !ui.isClosing();
                });
                check(stillOpen, "nexus [" + tag + "]: the screen closed after clicking " + section);
                int showing = context.computeOnClient(client -> countShowing(nexus(client).root()));
                check(showing > 0, "nexus [" + tag + "]: nothing is showing after clicking " + section);
                context.getInput().setCursorPos(setup.width() - 2, setup.height() - 2);
                context.waitTicks(2);
                Path file = context.takeScreenshot(shotName(shot++, tag, section));
                step("nexus [" + tag + "]: section " + section + " renders (" + showing + " nodes showing) -> "
                        + file.getFileName());
                checkReachable(context, tag + "/" + section, findings);
            }
            context.setScreen(() -> null);
            context.waitTicks(5);
        }
    }

    /** Ids of the sidebar controls (buttons and tab strips) in tree order. */
    private static List<String> navIds(UiScreen ui) {
        List<String> ids = new ArrayList<>();
        if (ui == null) {
            return ids;
        }
        for (NexusSection section : NexusSection.values()) {
            String railId = "rail." + section.id();
            UiNode rail = ui.root().findById(railId);
            if (rail != null && rail.isShowing()) {
                ids.add(railId);
            }
        }
        if (!ids.isEmpty()) {
            return ids;
        }
        List<UiNode> nodes = new ArrayList<>();
        collect(ui.root(), nodes);
        for (UiNode node : nodes) {
            String id = node.id();
            if (id == null || !id.startsWith("nexus.") || !(node instanceof Button || node instanceof Tabs)) {
                continue;
            }
            String lower = id.toLowerCase(Locale.ROOT);
            boolean nav = false;
            for (String hint : NAV_HINTS) {
                if (lower.contains(hint)) {
                    nav = true;
                }
            }
            if (!nav) {
                continue;
            }
            if (node instanceof Tabs tabs) {
                for (int i = 0; i < tabs.labels().size(); i++) {
                    ids.add(id + "#" + i);
                }
            } else {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Activates a section control through the core widget (the same code path as a mouse click). */
    private static boolean clickNav(UiScreen ui, String section) {
        if (ui == null) {
            return false;
        }
        int hash = section.lastIndexOf('#');
        String id = hash < 0 ? section : section.substring(0, hash);
        UiNode node = ui.root().findById(id);
        UiContext ctx = ui.context();
        if (node instanceof Button button) {
            ScrollIntoView.reveal(ctx, button);
            button.click(ctx);
            return true;
        }
        if (node instanceof Tabs tabs && hash >= 0) {
            tabs.select(ctx, Integer.parseInt(section.substring(hash + 1)));
            return true;
        }
        if (node != null && id.startsWith("rail.")) {
            // VantaShell rail entries are plain nodes: a full left click through the screen's input routing, at the
            // node's centre in window coordinates (bounds are in logical pixels, the screen scales them).
            ScrollIntoView.reveal(ctx, node);
            Rect b = node.bounds();
            float s = ui.effectiveScale();
            double x = b.centerX() * s;
            double y = b.centerY() * s;
            ui.mouseDown(x, y, Keys.MOUSE_LEFT);
            ui.mouseUp(x, y, Keys.MOUSE_LEFT);
            return true;
        }
        return false;
    }

    /**
     * Every showing, enabled button and toggle of the current Nexus view must be reachable by a mouse click: its
     * centre lies inside every ancestor's bounds (so {@code mouseDown} descends into it) and the core's hit test at
     * that point returns the control or a descendant. A control scrolled out of a scroll panel is scrolled into view
     * first, like keyboard focus does. Failures are findings, not aborts.
     */
    private static void checkReachable(ClientGameTestContext context, String tag, List<String> findings) {
        List<UiNode> controls = context.computeOnClient(client -> {
            List<UiNode> out = new ArrayList<>();
            UiScreen ui = nexus(client);
            if (ui == null) {
                return out;
            }
            List<UiNode> nodes = new ArrayList<>();
            collect(ui.root(), nodes);
            for (UiNode node : nodes) {
                if ((node instanceof Button || node instanceof Toggle) && node.isShowing()
                        && node.isEffectivelyEnabled()) {
                    out.add(node);
                }
            }
            return out;
        });
        int ok = 0;
        for (UiNode control : controls) {
            String problem = context.computeOnClient(client -> reachability(nexus(client), control));
            if (problem != null && problem.startsWith("scrolled out")) {
                context.runOnClient(client -> {
                    UiScreen ui = nexus(client);
                    if (ui != null && ui.root().isAncestorOf(control)) {
                        ScrollIntoView.reveal(ui.context(), control);
                    }
                });
                context.waitTicks(SETTLE_TICKS);
                problem = context.computeOnClient(client -> reachability(nexus(client), control));
            }
            if (problem == null) {
                ok++;
            } else {
                String line = "UNREACHABLE [nexus " + tag + "] " + describe(control) + ": " + problem;
                warn(line);
                findings.add(line);
            }
        }
        step("nexus [" + tag + "]: " + ok + " of " + controls.size() + " showing controls reachable");
    }

    /** Null when the control is reachable, otherwise the reason. Client thread. */
    private static String reachability(UiScreen ui, UiNode node) {
        if (ui == null) {
            return "the Nexus screen is no longer open";
        }
        if (!ui.root().isAncestorOf(node)) {
            return "the control left the tree (the view was rebuilt)";
        }
        if (!node.isShowing()) {
            return null; // hidden meanwhile (a dialog closed, a view switched): nothing to reach
        }
        Rect bounds = node.bounds();
        Rect clip = new Rect(0, 0, ui.width(), ui.height());
        boolean scrollable = false;
        for (UiNode a = node.parent(); a != null; a = a.parent()) {
            if (a instanceof ScrollPanel) {
                clip = clip.intersect(a.bounds());
                scrollable = true;
            }
        }
        Rect visible = clip.intersect(bounds);
        if (visible.isEmpty()) {
            return (scrollable ? "scrolled out of view: " : "outside the screen or its panel: ") + "bounds="
                    + fmt(bounds) + " clip=" + fmt(clip);
        }
        double cx = visible.x() + visible.w() / 2.0;
        double cy = visible.y() + visible.h() / 2.0;
        for (UiNode a = node.parent(); a != null && a != ui.root(); a = a.parent()) {
            if (!a.bounds().contains(cx, cy)) {
                return String.format(Locale.ROOT, "centre (%.1f,%.1f) outside ancestor %s bounds=%s so mouseDown never "
                        + "descends", cx, cy, describe(a), fmt(a.bounds()));
            }
        }
        UiNode hit = ui.root().hitTest(cx, cy);
        for (UiNode n = hit; n != null; n = n.parent()) {
            if (n == node) {
                return null;
            }
        }
        return String.format(Locale.ROOT, "hit test at (%.1f,%.1f) returns %s instead of the control", cx, cy,
                hit == null ? "null" : describe(hit) + " bounds=" + fmt(hit.bounds()));
    }

    // ---- 2. HUD preset through the assistant's path -----------------------------------------------------------

    private static void hudPreset(ClientGameTestContext context, VantaServices services) {
        Set<HudWidgetType> before = context.computeOnClient(client -> visibleTypes(services.hud().layout()));
        NexusActions.Outcome outcome = context.computeOnClient(client -> {
            JsonObject action = new JsonObject();
            action.addProperty("type", "hud.preset");
            action.addProperty("preset", NexusHudPreset.MINIMAL.id());
            JsonArray actions = new JsonArray();
            actions.add(action);
            NexusUndo.Turn turn = services.nexusUndo().begin();
            NexusActions.Outcome result = services.nexusActions().execute(actions, turn);
            services.nexusUndo().commit(turn, "game test: hud.preset minimal");
            return result;
        });
        check(outcome.rejected().isEmpty() && outcome.applied().size() == 1, "hud.preset minimal through "
                + "NexusActions: applied " + outcome.applied() + ", rejected " + outcome.rejected());
        Set<HudWidgetType> after = context.computeOnClient(client -> visibleTypes(services.hud().layout()));
        check(after.equals(NexusHudPreset.MINIMAL.widgets()), "after hud.preset minimal the visible widgets are "
                + after + " instead of " + NexusHudPreset.MINIMAL.widgets());
        step("nexus: hud.preset minimal applied through NexusActions (" + outcome.applied().get(0).summary()
                + "); visible widgets " + after + " (before: " + before + ")");
        boolean undone = context.computeOnClient(client -> services.nexusUndo().undoLast());
        check(undone, "NexusUndo.undoLast returned false after the preset turn");
        Set<HudWidgetType> restored = context.computeOnClient(client -> visibleTypes(services.hud().layout()));
        check(restored.equals(before), "Undo restored the visible widgets to " + restored + " instead of " + before);
        step("nexus: Undo restored the layout (" + restored + ")");
    }

    /** Enabled widget types of a layout without the crosshair. */
    static Set<HudWidgetType> visibleTypes(HudLayout layout) {
        Set<HudWidgetType> out = EnumSet.noneOf(HudWidgetType.class);
        for (HudWidgetState widget : layout.enabled()) {
            if (widget.type() != HudWidgetType.CROSSHAIR) {
                out.add(widget.type());
            }
        }
        return out;
    }

    // ---- 3. profiles -------------------------------------------------------------------------------------------

    private static void profileSwitch(ClientGameTestContext context, VantaServices services) {
        Optional<String> before = context.computeOnClient(client -> services.profiles().activeId());
        Optional<Profile> recording = context.computeOnClient(client ->
                services.profiles().find(BuiltInProfiles.RECORDING));
        check(recording.isPresent(), "the built-in profile '" + BuiltInProfiles.RECORDING + "' is missing: "
                + context.computeOnClient(client -> services.profiles().list().stream().map(Profile::id).toList()));
        boolean activated = context.computeOnClient(client -> services.activateProfile(BuiltInProfiles.RECORDING));
        check(activated, "activateProfile(" + BuiltInProfiles.RECORDING + ") returned false");
        Optional<String> active = context.computeOnClient(client -> services.profiles().activeId());
        check(active.filter(BuiltInProfiles.RECORDING::equals).isPresent(), "active profile is " + active
                + " after switching to " + BuiltInProfiles.RECORDING);
        Set<HudWidgetType> expected = visibleTypes(recording.get().hud());
        Set<HudWidgetType> actual = context.computeOnClient(client -> visibleTypes(services.hud().layout()));
        check(actual.equals(expected), "the Recording profile should show " + expected + " but the HUD shows "
                + actual);
        step("nexus: profile '" + recording.get().name() + "' activated (HUD " + actual + ")");
        String back = before.orElse(BuiltInProfiles.DEFAULT);
        boolean restored = context.computeOnClient(client -> services.activateProfile(back));
        check(restored, "could not switch back to profile " + back);
        Optional<String> now = context.computeOnClient(client -> services.profiles().activeId());
        check(now.filter(back::equals).isPresent(), "active profile is " + now + " after switching back to " + back);
        step("nexus: switched back to profile " + back);
    }

    // ---- 4. waypoints and markers ------------------------------------------------------------------------------

    private record WorldInfo(String worldKey, String dimension, Vec3d position, float yaw) {
    }

    private static void waypoints(ClientGameTestContext context, VantaServices services) {
        context.setScreen(() -> null);
        context.waitForScreen(null);
        WorldInfo info = context.computeOnClient(client -> new WorldInfo(services.game().worldKey(),
                services.game().dimensionId().orElse(Waypoint.UNKNOWN_DIMENSION),
                services.game().playerPosition().orElse(Vec3d.ZERO), services.game().yaw()));
        check(WorldKeys.isSingleplayer(info.worldKey()), "the world key of the singleplayer test world is '"
                + info.worldKey() + "' (singleplayerLevelName bridge); level name "
                + context.computeOnClient(client -> services.game().singleplayerLevelName()));
        double radians = Math.toRadians(info.yaw());
        Vec3d target = new Vec3d(info.position().x() - Math.sin(radians) * WAYPOINT_AHEAD, info.position().y(),
                info.position().z() + Math.cos(radians) * WAYPOINT_AHEAD);
        Optional<Waypoint> added = context.computeOnClient(client ->
                services.waypoints().add(info.worldKey(), info.dimension(), WAYPOINT_NAME, target));
        check(added.isPresent(), "WaypointStore.add refused the game test waypoint for " + info.worldKey());
        String id = added.get().id();
        boolean listed = context.computeOnClient(client -> services.waypoints().forWorld(info.worldKey()).stream()
                .anyMatch(w -> w.id().equals(id)));
        check(listed, "the waypoint is not listed for " + info.worldKey());
        step("nexus: waypoint '" + WAYPOINT_NAME + "' added at " + fmt(target) + " in " + info.worldKey() + " / "
                + info.dimension() + " (player at " + fmt(info.position()) + ", yaw " + Math.round(info.yaw()) + ")");

        boolean drawn = pollFor(context, client -> WaypointMarkerElement.drawnLastFrame() >= 1, RENDER_TIMEOUT_TICKS);
        check(drawn, "the marker element did not draw the waypoint: " + markerState(context, services));
        Path markerShot = context.takeScreenshot("60_waypoint_marker");
        step("nexus: marker drawn (" + WaypointMarkerElement.drawnLastFrame() + " in the last frame, "
                + WorldCamera.frames() + " world frames captured) -> " + markerShot.getFileName());

        context.runOnClient(client -> services.lab().set(LabFeature.WAYPOINT_BEAMS, true));
        try {
            boolean beam = pollFor(context, client -> WaypointBeamRenderer.beamsLastFrame() >= 1,
                    RENDER_TIMEOUT_TICKS);
            check(beam, "no waypoint beam was drawn with the Lab feature on: " + markerState(context, services));
            Path beamShot = context.takeScreenshot("61_waypoint_beam");
            step("nexus: waypoint beam drawn -> " + beamShot.getFileName());
        } finally {
            context.runOnClient(client -> services.lab().set(LabFeature.WAYPOINT_BEAMS, false));
        }

        boolean removed = context.computeOnClient(client -> services.waypoints().remove(id));
        check(removed, "WaypointStore.remove(" + id + ") returned false");
        boolean gone = context.computeOnClient(client -> services.waypoints().forWorld(info.worldKey()).stream()
                .noneMatch(w -> w.id().equals(id)));
        check(gone, "the waypoint is still listed after removal");
        boolean cleared = pollFor(context, client -> WaypointMarkerElement.drawnLastFrame() == 0, RENDER_TIMEOUT_TICKS);
        check(cleared, "markers are still drawn after the waypoint was removed");
        step("nexus: waypoint removed, no marker drawn");
    }

    private static String markerState(ClientGameTestContext context, VantaServices services) {
        return context.computeOnClient(client -> String.format(Locale.ROOT, "worldFrames=%d cameraFresh=%s "
                        + "drawnTotal=%d beamsTotal=%d hideGui=%s debugOverlay=%s screen=%s waypointsEnabled=%s "
                        + "maxDistance=%d enabledInWorld=%d", WorldCamera.frames(), WorldCamera.isFresh(),
                WaypointMarkerElement.drawnTotal(), WaypointBeamRenderer.beamsTotal(), client.options.hideGui,
                client.debugEntries.isOverlayVisible(), client.screen,
                services.settings().get(VantaSettings.WAYPOINTS_ENABLED),
                services.settings().get(VantaSettings.WAYPOINTS_MAX_MARKER_DISTANCE),
                services.waypoints().enabledIn(services.game().worldKey(),
                        services.game().dimensionId().orElse(Waypoint.UNKNOWN_DIMENSION)).size()));
    }

    // ---- 5. lab toggles and effects ----------------------------------------------------------------------------

    /** Sampled LabEffects values (client thread), judged on the test thread. */
    private record Curves(float soon, float idle, float again, float off, float spread, float settled,
                          float spreadOff, float start, float done, float transitionsOff) {
    }

    private static void labToggles(ClientGameTestContext context, VantaServices services) {
        LabSettings lab = services.lab();
        for (LabFeature feature : LabFeature.values()) {
            boolean before = context.computeOnClient(client -> lab.isEnabled(feature));
            boolean flipped = context.computeOnClient(client -> {
                lab.set(feature, !before);
                return lab.isEnabled(feature);
            });
            check(flipped == !before, "LabSettings.set(" + feature.id() + ") did not flip the setting");
            boolean back = context.computeOnClient(client -> {
                lab.set(feature, before);
                return lab.isEnabled(feature);
            });
            check(back == before, "LabSettings.set(" + feature.id() + ") did not restore the setting");
        }
        step("nexus: every Lab toggle flips its setting and back (" + LabFeature.values().length + " features)");

        Curves c = context.computeOnClient(client -> {
            LabEffects effects = services.labEffects();
            long t0 = services.clock().millis();
            // Dynamic HUD: full alpha within 10 s of input, faded 11 s later, back on input, always 1 when off.
            lab.set(LabFeature.DYNAMIC_HUD, true);
            effects.onInput(t0);
            float soon = effects.hudAlpha(t0 + 1_000L);
            float idle = effects.hudAlpha(t0 + 11_000L);
            effects.onInput(t0 + 12_000L);
            float again = effects.hudAlpha(t0 + 12_000L);
            lab.set(LabFeature.DYNAMIC_HUD, false);
            float off = effects.hudAlpha(t0 + 40_000L);
            // Animated crosshair: an attack spreads the gap while on; 0 when off.
            lab.set(LabFeature.ANIMATED_CROSSHAIR, true);
            effects.onAttack(t0);
            float spread = effects.crosshairSpread(t0 + 50L);
            float settled = effects.crosshairSpread(t0 + 10_000L);
            lab.set(LabFeature.ANIMATED_CROSSHAIR, false);
            float spreadOff = effects.crosshairSpread(t0 + 50L);
            // Screen transitions: a 160 ms curve while on, instantly 1 when off.
            lab.set(LabFeature.SCREEN_TRANSITIONS, true);
            effects.onScreenOpened(t0);
            float start = effects.transitionProgress(t0);
            float done = effects.transitionProgress(t0 + 160L);
            lab.set(LabFeature.SCREEN_TRANSITIONS, false);
            float transitionsOff = effects.transitionProgress(t0);
            effects.onInput(services.clock().millis());
            return new Curves(soon, idle, again, off, spread, settled, spreadOff, start, done, transitionsOff);
        });
        check(c.soon() == 1f, "hudAlpha 1 s after input is " + c.soon() + " (expected 1.0)");
        check(c.idle() < 1f, "hudAlpha 11 s after the last input is still " + c.idle() + " with Dynamic HUD on");
        check(c.again() == 1f, "hudAlpha right after new input is " + c.again() + " (expected 1.0)");
        check(c.off() == 1f, "hudAlpha with Dynamic HUD off is " + c.off() + " (expected 1.0)");
        check(c.spread() > 0f && c.spread() <= 1f, "crosshairSpread 50 ms after an attack is " + c.spread()
                + " (expected within (0, 1])");
        check(c.settled() < c.spread(), "crosshairSpread 10 s after the attack (" + c.settled()
                + ") did not decay below " + c.spread());
        check(c.spreadOff() == 0f, "crosshairSpread with Animated crosshair off is " + c.spreadOff()
                + " (expected 0)");
        check(c.start() >= 0f && c.start() < 1f, "transitionProgress at the open is " + c.start()
                + " (expected below 1)");
        check(c.done() == 1f, "transitionProgress 160 ms after the open is " + c.done() + " (expected 1.0)");
        check(c.transitionsOff() == 1f, "transitionProgress with Screen transitions off is " + c.transitionsOff());
        String curves = String.format(Locale.ROOT, "hudAlpha: 1s=%.2f 11s=%.2f input=%.2f off=%.2f; crosshairSpread: "
                        + "attack=%.2f 10s=%.2f off=%.2f; transition: open=%.2f 160ms=%.2f off=%.2f", c.soon(),
                c.idle(), c.again(), c.off(), c.spread(), c.settled(), c.spreadOff(), c.start(), c.done(),
                c.transitionsOff());
        step("nexus: LabEffects curves (" + curves + ")");
    }

    // ---- 6. first-start notice decision ------------------------------------------------------------------------

    private static void firstStartDecision() {
        String v = VantaVersion.CLIENT;
        check(NexusFirstStart.decide(true, LocalAiStatus.NOT_INSTALLED, false, Optional.empty(), v),
                "first-start notice: not installed, enabled, never dismissed should show");
        check(!NexusFirstStart.decide(false, LocalAiStatus.NOT_INSTALLED, false, Optional.empty(), v),
                "first-start notice must not show when nexus.enabled is off");
        check(!NexusFirstStart.decide(true, LocalAiStatus.NOT_INSTALLED, true, Optional.empty(), v),
                "first-start notice must not show for a launcher-managed install");
        check(!NexusFirstStart.decide(true, LocalAiStatus.INSTALLED, false, Optional.empty(), v),
                "first-start notice must not show when the Local AI is installed");
        check(!NexusFirstStart.decide(true, LocalAiStatus.FAILED, false, Optional.empty(), v),
                "first-start notice must not show when the manifest is unusable");
        check(!NexusFirstStart.decide(true, LocalAiStatus.NOT_INSTALLED, false, Optional.of(v), v),
                "first-start notice must not show again after it was dismissed for this version");
        check(NexusFirstStart.decide(true, LocalAiStatus.NOT_INSTALLED, false, Optional.of("0.0.0"), v),
                "first-start notice should show again after a client update");
        step("nexus: first-start notice decision checked (shown once per client version, never when installed, "
                + "launcher-managed or disabled)");
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private static UiScreen nexus(Minecraft client) {
        return client.screen instanceof VantaScreen vanta && vanta.screenId() == ScreenId.NEXUS ? vanta.ui() : null;
    }

    private static void collect(UiNode node, List<UiNode> out) {
        out.add(node);
        for (UiNode child : node.children()) {
            collect(child, out);
        }
    }

    private static int countShowing(UiNode root) {
        List<UiNode> nodes = new ArrayList<>();
        collect(root, nodes);
        int showing = 0;
        for (UiNode node : nodes) {
            if (node.isShowing() && !node.bounds().isEmpty()) {
                showing++;
            }
        }
        return showing;
    }

    private static String describe(UiNode node) {
        String name = node.getClass().getSimpleName();
        if (name.isEmpty()) {
            name = node.getClass().getName();
        }
        String id = node.id();
        String label = node instanceof Button b ? " \"" + b.label() + "\"" : node instanceof Toggle t
                ? " \"" + t.label() + "\"" : "";
        return name + (id != null ? "#" + id : "") + label;
    }

    private static String fmt(Rect r) {
        return "[" + r.x() + "," + r.y() + " " + r.w() + "x" + r.h() + " -> " + r.right() + "," + r.bottom() + "]";
    }

    private static String fmt(Vec3d v) {
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", v.x(), v.y(), v.z());
    }

    private static String shotName(int number, String tag, String view) {
        return String.format(Locale.ROOT, "%02d_nexus_%s_%s", number, tag, view.replace('.', '_').replace('#', '_'));
    }
}
