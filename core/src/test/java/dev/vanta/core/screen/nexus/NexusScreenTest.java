package dev.vanta.core.screen.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.vanta.core.ai.ChatBackend;
import dev.vanta.core.ai.LocalAiClient;
import dev.vanta.core.ai.LocalAiException;
import dev.vanta.core.ai.LocalAiStatus;
import dev.vanta.core.ai.NexusHudPreset;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudPreset;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.lab.LabFeature;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.screen.ReachabilityWalker;
import dev.vanta.core.screen.ScreenBootstrap;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.screen.menu.MainMenuScreen;
import dev.vanta.core.screen.profiles.NameDialog;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.waypoints.Waypoint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Vanta Nexus: every section renders and every interactive node is clickable in the small windows (427x240 and
 * large text), the assistant applies a HUD preset from a canned reply and shows the receipt with Undo, the HUD
 * Designer, waypoints, lab and performance sections work on the real stores, and the main menu opens the screen.
 */
class NexusScreenTest {
    @TempDir
    Path dir;
    private ScreenTestSupport t;
    private final CannedBackend backend = new CannedBackend();

    /** Answers synchronously with the next canned content. */
    static final class CannedBackend implements ChatBackend {
        String next = "{\"message\":\"ok\",\"actions\":[]}";
        Consumer<LocalAiClient.ChatResponse> pending;
        int requests;

        @Override
        public void chat(LocalAiClient.ChatRequest request, Consumer<LocalAiClient.ChatResponse> onReply,
                         Consumer<LocalAiException> onError) {
            requests++;
            if (next == null) {
                pending = onReply;
                return;
            }
            onReply.accept(new LocalAiClient.ChatResponse(next, Optional.empty(), "stop", 1, 1));
        }
    }

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
        ScreenBootstrap.registerAll(t.services);
        t.services.profiles().activate("default");
        t.services.setNexusBackend(backend);
    }

    private NexusScreen open(int w, int h) {
        NexusScreen screen = t.show(ScreenId.NEXUS, w, h);
        settle(screen);
        return screen;
    }

    private static void settle(UiScreen screen) {
        for (int i = 0; i < 3; i++) {
            screen.tick();
        }
        screen.render(new TestCanvas(screen.width(), screen.height()), -1000, -1000, 0f);
        screen.render(new TestCanvas(screen.width(), screen.height()), -1000, -1000, 0f);
    }

    private static List<ReachabilityWalker.Failure> walk(UiScreen screen, String name) {
        return ReachabilityWalker.walk(screen, "NEXUS", name).failures();
    }

    private static void click(UiScreen screen, UiNode node) {
        assertNotNull(node, "node to click");
        ScreenTestSupport.click(screen, node);
        settle(screen);
    }

    @Test
    void everySectionRendersAndIsClickableInSmallWindows() {
        int[][] sizes = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}};
        List<ReachabilityWalker.Failure> failures = new ArrayList<>();
        for (boolean largeText : new boolean[] {false, true}) {
            t.services.settings().set(VantaSettings.ACCESSIBILITY_LARGE_TEXT, largeText);
            for (int[] size : sizes) {
                NexusScreen screen = open(size[0], size[1]);
                for (NexusSection section : NexusSection.values()) {
                    screen.select(section);
                    settle(screen);
                    assertEquals(section, screen.section());
                    assertNotNull(screen.root().findById("nexus.panel." + section.id()), section + " panel is in the tree");
                    TestCanvas canvas = new TestCanvas(screen.width(), screen.height());
                    screen.render(canvas, -1000, -1000, 0f);
                    assertTrue(canvas.hasText(dev.vanta.core.i18n.Lang.tr("vanta.screen.nexus")), "title drawn");
                    failures.addAll(walk(screen, size[0] + "x" + size[1] + (largeText ? " large text" : "")
                            + " [" + section.id() + "]"));
                }
                // The rail collapses to icons below the breakpoint and the back button stays reachable.
                assertEquals(screen.width() < dev.vanta.core.ui.VantaShell.COMPACT_BREAKPOINT,
                        screen.shell().isCompactRail());
            }
        }
        t.services.settings().set(VantaSettings.ACCESSIBILITY_LARGE_TEXT, false);
        if (!failures.isEmpty()) {
            fail(failures.size() + " unreachable node(s):\n" + String.join("\n",
                    failures.stream().map(Object::toString).toList()));
        }
    }

    @Test
    void sendingAPromptAppliesThePresetShowsTheReceiptAndUndoRestores() {
        NexusScreen screen = open(854, 480);
        AssistantPanel panel = screen.panel(NexusSection.ASSISTANT);
        HudLayout before = t.services.hud().layout();
        assertTrue(before.find("armor").orElseThrow().enabled(), "the default layout shows armor");
        assertTrue(panel.bubbles().isEmpty());
        assertNotNull(screen.root().findById("nexus.assistant.chip.0"), "example chips while the chat is empty");

        backend.next = "{\"message\":\"Minimal HUD: FPS and coordinates only.\","
                + "\"actions\":[{\"type\":\"hud.preset\",\"preset\":\"minimal\"}]}";
        panel.inputField().setText("Make my HUD minimal");
        click(screen, panel.sendButton());
        assertEquals(1, backend.requests);
        assertEquals(NexusHudPreset.MINIMAL.apply(before), t.services.hud().layout(), "the preset was applied");
        assertFalse(t.services.hud().layout().find("armor").orElseThrow().enabled());
        List<AssistantPanel.Bubble> bubbles = panel.bubbles();
        assertEquals(2, bubbles.size());
        assertEquals(AssistantPanel.Bubble.Kind.USER, bubbles.get(0).kind());
        assertEquals("Make my HUD minimal", bubbles.get(0).text());
        assertEquals(AssistantPanel.Bubble.Kind.ASSISTANT, bubbles.get(1).kind());
        assertEquals("Minimal HUD: FPS and coordinates only.", bubbles.get(1).text());
        assertEquals(1, bubbles.get(1).applied().size());
        TestCanvas canvas = t.frame(screen, -1000, -1000);
        assertTrue(canvas.hasTextContaining("Applied:"), "the receipt line is drawn");
        assertTrue(panel.undoButton().isPresent(), "Undo offered for the last applied turn");
        assertEquals("", panel.inputField().text(), "the field is cleared");
        assertFalse(panel.isPending());

        click(screen, panel.undoButton().orElseThrow());
        assertEquals(before, t.services.hud().layout(), "undo restores the layout");
        assertTrue(panel.undoButton().isEmpty(), "nothing left to undo");
        assertEquals(2, panel.bubbles().size(), "the transcript keeps the history");

        // A failure is explained in the chat rather than silently dropped.
        backend.next = "this is not json";
        panel.send("hello");
        List<AssistantPanel.Bubble> after = panel.bubbles();
        assertEquals(AssistantPanel.Bubble.Kind.ERROR, after.get(after.size() - 1).kind());
        assertTrue(after.get(after.size() - 1).text().contains("could not read"), after.get(after.size() - 1).text());

        // While a question is in flight the input is disabled and a pending bubble shows; the UI never blocks.
        backend.next = null;
        panel.send("second");
        assertTrue(panel.isPending());
        assertFalse(panel.inputField().isEnabled());
        assertEquals(AssistantPanel.Bubble.Kind.PENDING, panel.bubbles().get(panel.bubbles().size() - 1).kind());
        backend.pending.accept(new LocalAiClient.ChatResponse("{\"message\":\"done\",\"actions\":[]}", Optional.empty(),
                "stop", 1, 1));
        assertFalse(panel.isPending());
        assertTrue(panel.inputField().isEnabled());
    }

    @Test
    void enterSendsAndTheChipsSendTheirPrompt() {
        NexusScreen screen = open(854, 480);
        AssistantPanel panel = screen.panel(NexusSection.ASSISTANT);
        backend.next = "{\"message\":\"Moved.\",\"actions\":[{\"type\":\"hud.set\",\"element\":\"fps\",\"x\":1,\"y\":1}]}";
        panel.inputField().requestFocus(screen.context());
        ScreenTestSupport.type(screen, "Move the FPS counter to the bottom right");
        ScreenTestSupport.key(screen, Keys.ENTER);
        settle(screen);
        assertEquals(1, backend.requests);
        assertEquals(dev.vanta.core.hud.HudAnchor.BOTTOM_RIGHT, t.services.hud().layout().find("fps").orElseThrow().anchor());
        t.services.nexus().clearTranscript();
        settle(screen);
        UiNode chip = screen.root().findById("nexus.assistant.chip.1");
        assertNotNull(chip);
        backend.next = "{\"message\":\"ok\",\"actions\":[]}";
        click(screen, chip);
        assertEquals(2, backend.requests);
        assertEquals("Only show FPS and coordinates", panel.bubbles().get(0).text());
    }

    @Test
    void statusPillIsHonestAboutTheUnresolvedManifest() {
        NexusScreen screen = open(854, 480);
        AssistantPanel panel = screen.panel(NexusSection.ASSISTANT);
        assertEquals(LocalAiStatus.FAILED, t.services.localAi().status(), "the test build carries the template manifest");
        assertTrue(panel.statusPill().text().contains("Failed"), panel.statusPill().text());
        assertTrue(panel.statusReasonLabel().text().toLowerCase().contains("resolved"), panel.statusReasonLabel().text());
        assertTrue(panel.installButton().isEmpty(), "nothing to install from an unresolved manifest");
        assertNotNull(screen.root().findById("nexus.assistant.localai.problem"), "the reason is shown in the card");
        NexusSettingsPanel settings = screen.panel(NexusSection.SETTINGS);
        screen.select(NexusSection.SETTINGS);
        settle(screen);
        assertFalse(settings.installButton().isVisible());
        assertFalse(settings.removeButton().isVisible());
        assertEquals(6, settings.settingRows().size(), "the six nexus.* settings");
    }

    @Test
    void disablingNexusShowsTheBannerAndBlocksTheInput() {
        t.services.settings().set(VantaSettings.NEXUS_ENABLED, false);
        NexusScreen screen = open(854, 480);
        AssistantPanel panel = screen.panel(NexusSection.ASSISTANT);
        assertFalse(panel.inputField().isEnabled());
        UiNode enable = screen.root().findById("nexus.assistant.enable");
        assertNotNull(enable);
        click(screen, enable);
        assertTrue(t.services.settings().get(VantaSettings.NEXUS_ENABLED));
        assertTrue(panel.inputField().isEnabled());
    }

    @Test
    void hudDesignerPresetsLayoutsAndEditorLink() {
        NexusScreen screen = open(854, 480);
        screen.select(NexusSection.HUD_DESIGNER);
        settle(screen);
        HudDesignerPanel panel = screen.panel(NexusSection.HUD_DESIGNER);
        HudLayout before = t.services.hud().layout();
        click(screen, screen.root().findById("nexus.hud.preset.pvp"));
        assertEquals(NexusHudPreset.PVP.apply(before), t.services.hud().layout());
        assertTrue(t.services.hud().layout().byType(HudWidgetType.KEYSTROKES).get(0).enabled());
        assertTrue(t.services.nexusUndo().canUndo(), "preset buttons are undoable like the assistant's actions");
        assertTrue(panel.summaryText().contains("Custom") || panel.summaryText().contains("PvP"), panel.summaryText());

        click(screen, panel.saveCurrentButton());
        NameDialog dialog = (NameDialog) screen.context().popups().top().node();
        dialog.field().setText("Arena");
        dialog.confirmButton().click(screen.context());
        settle(screen);
        List<HudPreset> user = t.services.hud().userPresets();
        assertEquals(1, user.size());
        assertEquals("Arena", user.get(0).name());
        assertEquals(t.services.hud().layout(), user.get(0).layout());
        assertTrue(panel.loadButton(user.get(0).id()).isPresent());
        assertTrue(panel.deleteButton(user.get(0).id()).isPresent());
        assertTrue(panel.deleteButton("default").isEmpty(), "built-ins cannot be deleted");

        click(screen, panel.loadButton("minimal").orElseThrow());
        assertEquals(dev.vanta.core.hud.HudPresets.find("minimal").orElseThrow().layout(), t.services.hud().layout());
        assertEquals(List.of(), walk(screen, "854x480 [hud_designer]"));

        click(screen, panel.deleteButton(user.get(0).id()).orElseThrow());
        ScreenTestSupport.confirmDialog(screen);
        settle(screen);
        assertTrue(t.services.hud().userPresets().isEmpty());

        click(screen, panel.openEditorButton());
        assertTrue(t.host.events().contains("openScreen:HUD_EDITOR"));
    }

    @Test
    void waypointsSectionAddsEditsTogglesAndDeletes() {
        t.game.position = new Vec3d(10, 64, 20);
        NexusScreen screen = open(427, 240);
        screen.select(NexusSection.WAYPOINTS);
        settle(screen);
        WaypointsPanel panel = screen.panel(NexusSection.WAYPOINTS);
        assertNotNull(screen.root().findById("nexus.waypoints.empty"), "honest empty state");
        assertTrue(panel.addButton().isEnabled());

        click(screen, panel.addButton());
        WaypointDialog dialog = (WaypointDialog) screen.context().popups().top().node();
        assertEquals("10", dialog.xField().text(), "prefilled with the player's position");
        assertEquals("64", dialog.yField().text());
        assertEquals("20", dialog.zField().text());
        assertFalse(dialog.confirmButton().isEnabled(), "a name is required");
        settle(screen);
        assertEquals(List.of(), walk(screen, "427x240 [waypoint dialog]"), "the dialog is clickable at 427x240");
        dialog.nameField().setText("Home");
        dialog.categoryField().setText("Base");
        dialog.zField().setText("abc");
        assertFalse(dialog.confirmButton().isEnabled());
        dialog.zField().setText("25.5");
        assertTrue(dialog.confirmButton().isEnabled());
        dialog.confirmButton().click(screen.context());
        settle(screen);
        Waypoint home = t.services.waypoints().find(t.game.worldKey(), "Home").orElseThrow();
        assertEquals(25.5, home.z(), 1e-9);
        assertEquals("Base", home.category());
        assertEquals(1, panel.rows().size());
        WaypointsPanel.WaypointRow row = panel.row(home.id()).orElseThrow();
        assertEquals("6 m", row.distanceText(), "distance from (10,64,20) to (10,64,25.5)");
        assertEquals(List.of(), walk(screen, "427x240 [waypoints]"));

        click(screen, row.enabledToggle());
        assertFalse(t.services.waypoints().find(home.id()).orElseThrow().enabled());

        click(screen, panel.row(home.id()).orElseThrow().editButton());
        WaypointDialog edit = (WaypointDialog) screen.context().popups().top().node();
        assertEquals("Home", edit.nameField().text());
        edit.nameField().setText("Base camp");
        edit.confirmButton().click(screen.context());
        settle(screen);
        assertEquals("Base camp", t.services.waypoints().find(home.id()).orElseThrow().name());

        panel.searchField().setText("nothing");
        settle(screen);
        assertTrue(panel.rows().isEmpty());
        panel.searchField().setText("");
        settle(screen);
        assertEquals(1, panel.rows().size());

        click(screen, panel.row(home.id()).orElseThrow().deleteButton());
        ScreenTestSupport.confirmDialog(screen);
        settle(screen);
        assertEquals(0, t.services.waypoints().size());

        t.game.onTitleScreen();
        panel.refresh();
        settle(screen);
        assertFalse(panel.addButton().isEnabled(), "nothing can be added outside a world");
        assertNotNull(screen.root().findById("nexus.waypoints.empty"));
        t.services.waypoints().add("mp:play.example.net", "minecraft:overworld", "Spawn", new Vec3d(0, 70, 0));
        panel.allWorldsToggle().set(screen.context(), true);
        settle(screen);
        assertEquals(1, panel.rows().size(), "the all-worlds view lists other worlds");
        assertEquals("n/a", panel.rows().get(0).distanceText());
    }

    @Test
    void labTogglesFlipTheSettingsBothWays() {
        NexusScreen screen = open(854, 480);
        screen.select(NexusSection.LAB);
        settle(screen);
        LabPanel panel = screen.panel(NexusSection.LAB);
        assertEquals(LabFeature.values().length, panel.rows().size());
        click(screen, panel.toggle(LabFeature.DYNAMIC_HUD));
        assertTrue(t.services.lab().isEnabled(LabFeature.DYNAMIC_HUD));
        assertTrue(t.services.settings().get(VantaSettings.LAB_DYNAMIC_HUD));
        t.services.lab().set(LabFeature.DYNAMIC_HUD, false);
        assertFalse(panel.toggle(LabFeature.DYNAMIC_HUD).isOn(), "external changes update the toggle");
    }

    @Test
    void performanceSectionShowsLiveValuesAndAppliesPresets() {
        for (int i = 0; i < 100; i++) {
            t.services.performance().onFrame(i == 10 || i == 11 ? 60.0 : 8.0);
        }
        NexusScreen screen = open(854, 480);
        screen.select(NexusSection.PERFORMANCE);
        settle(screen);
        NexusPerformancePanel panel = screen.panel(NexusSection.PERFORMANCE);
        assertEquals("120 FPS", panel.fpsText());
        assertEquals("8.0 ms", panel.p50Text());
        assertEquals("60.0 ms", panel.p99Text(), "nearest rank: the 99th of 100 sorted frames");
        assertEquals("2 of 100 frames", panel.hitchesText());
        assertEquals("Singleplayer", panel.pingText());
        assertEquals("n/a", panel.gpuText(), "no GPU string from the fake bridge");
        t.game.onServer("play.example.net", "Example", 42);
        t.game.gpuRenderer = Optional.of("Test GPU 9000");
        screen.tick();
        assertEquals("42 ms", panel.pingText());
        assertEquals("Test GPU 9000", panel.gpuText());
        t.game.onTitleScreen();
        screen.tick();
        assertEquals("n/a", panel.fpsText());

        click(screen, screen.root().findById("nexus.perf.preset." + PerformancePreset.LOW.id()));
        assertEquals(Optional.of(PerformancePreset.LOW), t.services.performance().detectPreset());
        click(screen, panel.boostButton());
        assertEquals(Optional.of(PerformancePreset.BOOST), t.services.performance().detectPreset());
        click(screen, panel.openCenterButton());
        assertTrue(t.host.events().contains("openScreen:PERFORMANCE"));
    }

    @Test
    void profilesSectionActivatesAndCreates() {
        NexusScreen screen = open(854, 480);
        screen.select(NexusSection.PROFILES);
        settle(screen);
        NexusProfilesPanel panel = screen.panel(NexusSection.PROFILES);
        assertEquals(7, panel.cards().size());
        click(screen, panel.card("recording").orElseThrow().activateButton());
        assertEquals("recording", t.services.profiles().activeId().orElseThrow());
        assertTrue(panel.card("recording").orElseThrow().isActiveProfile());
        click(screen, panel.card("default").orElseThrow().activateButton());
        assertEquals("default", t.services.profiles().activeId().orElseThrow());
        click(screen, panel.createButton());
        NameDialog dialog = (NameDialog) screen.context().popups().top().node();
        dialog.field().setText("Weekend");
        dialog.confirmButton().click(screen.context());
        settle(screen);
        assertEquals(8, t.services.profiles().size());
        assertEquals(8, panel.cards().size());
        click(screen, panel.openScreenButton());
        assertTrue(t.host.events().contains("openScreen:PROFILES"));
    }

    @Test
    void deepLinksOpenTheRequestedSection() {
        t.navigator.stageNexusSection(NexusSection.WAYPOINTS.id());
        NexusScreen screen = open(854, 480);
        assertEquals(NexusSection.WAYPOINTS, screen.section());
        assertEquals("waypoints", screen.shell().selectedRail());
        NexusScreen plain = open(854, 480);
        assertEquals(NexusSection.ASSISTANT, plain.section(), "the deep link is consumed once");
        click(plain, plain.root().findById("rail.lab"));
        assertEquals(NexusSection.LAB, plain.section());
    }

    @Test
    void mainMenuOpensNexusAndTheKeybindAndActionExist() {
        t.game.onTitleScreen();
        MainMenuScreen menu = t.show(ScreenId.MAIN_MENU, 854, 480);
        UiNode entry = menu.root().findById("menu.nexus");
        assertNotNull(entry);
        ScreenTestSupport.click(menu, entry);
        assertTrue(t.host.events().contains("openScreen:NEXUS"));
        MainMenuScreen small = t.show(ScreenId.MAIN_MENU, 427, 240);
        assertTrue(small.root().findById("menu.quit").bounds().bottom() < 240, "the taller stack still fits");
        assertTrue(dev.vanta.core.search.ActionEntry.builtIns().stream()
                .anyMatch(a -> a.id().equals(dev.vanta.core.search.ActionEntry.OPEN_NEXUS)
                        && a.screen().equals(Optional.of(ScreenId.NEXUS))));
        assertTrue(t.services.search().search("nexus", 5).stream().anyMatch(h -> h.toString().contains("open_nexus")),
                "the search finds Vanta Nexus");
    }
}
