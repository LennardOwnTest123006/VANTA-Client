package dev.vanta.core.hud.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.hud.HudAnchor;
import dev.vanta.core.hud.HudEditorModel;
import dev.vanta.core.hud.HudPreset;
import dev.vanta.core.hud.HudPresets;
import dev.vanta.core.hud.HudRect;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.ManualClock;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.TestHost;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiEnvironment;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.widget.Toggle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HudEditorScreenTest {
    @TempDir
    Path dir;
    private VantaServices services;
    private VantaPaths paths;
    private FakeGameBridge game;
    private MutableClock clock;
    private final ManualClock uiClock = new ManualClock(5_000L);
    private final TestHost host = new TestHost();
    private HudEditorScreen screen;

    @BeforeEach
    void setUp() {
        clock = MutableClock.standard();
        paths = VantaPaths.inGameDirectory(dir);
        game = new FakeGameBridge().onTitleScreen();
        services = VantaServices.create(paths, game, new FakeOptionsBridge(), new FakeKeybindBridge(),
                new FakeResourcePackBridge(), clock);
        services.load();
    }

    private HudEditorScreen open(int width, int height) {
        screen = new HudEditorScreen(services);
        screen.attach(new UiEnvironment(host, Theme.DEFAULT, uiClock, TestCanvas.metrics(), Function.identity()));
        screen.init(width, height);
        frame();
        // Let the open transition finish so frames are drawn at full alpha.
        uiClock.advance(UiScreen.TRANSITION_MS);
        frame();
        return screen;
    }

    /**
     * The toolbar keeps one line while its controls fit side by side (427x240, the common windowed size) and wraps
     * onto two lines at the 320x240 minimum, where the grid / snap icons would otherwise overlap the panel toggles;
     * the screen gives the bar exactly the height the toolbar asks for.
     */
    @Test
    void toolbarWrapsOntoTwoLinesOnlyWhenControlsWouldOverlap() {
        HudEditorScreen oneLine = open(427, 240);
        assertEquals(1, oneLine.toolbar().rows());
        assertEquals(EditorToolbar.COMPACT_HEIGHT, oneLine.toolbar().bounds().h());
        HudEditorScreen twoLines = open(320, 240);
        assertEquals(2, twoLines.toolbar().rows());
        assertEquals(2 * EditorToolbar.COMPACT_HEIGHT, twoLines.toolbar().bounds().h());
        assertEquals(twoLines.toolbar().barHeight(), twoLines.toolbar().barHeightFor(twoLines.context(), 320));
        Rect snap = twoLines.root().findById("hud.toolbar.snapIcon").bounds();
        Rect widgets = twoLines.toolbar().widgetsToggleButton().bounds();
        assertFalse(snap.intersects(widgets), "snap " + snap + " and widgets " + widgets + " share no pixels");
    }

    private TestCanvas frame() {
        TestCanvas canvas = new TestCanvas(screen.width(), screen.height());
        screen.render(canvas, -100, -100, 0f);
        return canvas;
    }

    private HudEditorModel model() {
        return screen.session().model();
    }

    private HudRect hostRect(String id) {
        return model().rectOf(id).orElseThrow();
    }

    private Rect logicalRect(String id) {
        return screen.canvasArea().viewport().toLogical(hostRect(id));
    }

    private void click(double x, double y) {
        screen.mouseDown(x, y, Keys.MOUSE_LEFT);
        screen.mouseUp(x, y, Keys.MOUSE_LEFT);
    }

    @Test
    void desktopLayoutShowsBothPanelsAroundAScaledPreview() {
        open(640, 360);
        assertFalse(screen.isCompact());
        assertTrue(screen.widgetList().isVisible());
        assertTrue(screen.inspector().isVisible());
        EditorViewport vp = screen.canvasArea().viewport();
        assertEquals(640, vp.hostWidth());
        assertEquals(360, vp.hostHeight());
        assertTrue(vp.scale() < 1f, "panels leave less room than the game screen");
        assertTrue(screen.canvasArea().bounds().contains(vp.area()));
        assertTrue(vp.area().x() >= WidgetListPanel.WIDTH);
        TestCanvas canvas = frame();
        assertTrue(canvas.hasText("HUD Editor"));
        assertTrue(canvas.hasText("Widgets"));
        assertTrue(canvas.hasText("Inspector"));
        assertTrue(canvas.hasText("Sample data"), "no world loaded");
        assertTrue(canvas.hasText("144"), "sample FPS drawn by the real renderer");
        assertFalse(screen.session().isLive());
    }

    @Test
    void compactLayoutCollapsesPanelsIntoToggles() {
        open(427, 240);
        assertTrue(screen.isCompact());
        assertTrue(screen.toolbar().isCompact());
        assertFalse(screen.widgetList().isVisible());
        assertFalse(screen.inspector().isVisible());
        EditorViewport full = screen.canvasArea().viewport();
        assertTrue(full.scale() > 0.75f, "only toolbar and footer take room: " + full.scale());
        assertEquals(427, full.hostWidth());
        screen.toggleInspector();
        frame();
        assertTrue(screen.inspector().isVisible());
        assertTrue(screen.toolbar().inspectorToggleButton().isActive());
        Rect bounds = screen.inspector().bounds();
        assertTrue(bounds.right() <= 427 && bounds.y() >= EditorToolbar.COMPACT_HEIGHT);
        assertTrue(screen.canvasArea().viewport().scale() < full.scale(), "the preview shrinks to stay whole");
        screen.toggleInspector();
        screen.toggleWidgetList();
        frame();
        assertFalse(screen.inspector().isVisible());
        assertTrue(screen.widgetList().isVisible());
        screen.resize(640, 360);
        frame();
        assertTrue(screen.inspector().isVisible(), "desktop defaults both panels open again");
    }

    @Test
    void clickSelectsAndDragMovesWithGridSnapAndUndoRedo() {
        open(640, 360);
        Rect fps = logicalRect("fps");
        HudRect before = hostRect("fps");
        click(fps.centerX(), fps.centerY());
        assertEquals(Optional.of("fps"), model().selectedId());
        assertTrue(host.events().isEmpty());

        double cx = fps.centerX();
        double cy = fps.centerY();
        screen.mouseDown(cx, cy, Keys.MOUSE_LEFT);
        screen.mouseDrag(cx + 20, cy + 9, Keys.MOUSE_LEFT, 20, 9);
        assertTrue(screen.canvasArea().isDragging());
        assertTrue(model().isDragging());
        screen.mouseUp(cx + 20, cy + 9, Keys.MOUSE_LEFT);
        HudRect after = hostRect("fps");
        assertTrue(after.x() > before.x() && after.y() > before.y());
        assertEquals(0, after.x() % HudEditorModel.GRID, "snapped to the grid");
        assertEquals(0, after.y() % HudEditorModel.GRID);
        assertEquals(after, services.hud().layout().resolveRect(services.hud().layout().find("fps").orElseThrow(),
                640, 360), "the store follows the editor live");

        assertTrue(screen.keyDown(Keys.Z, 0, Keys.MOD_CONTROL));
        assertEquals(before, hostRect("fps"));
        assertTrue(screen.keyDown(Keys.Y, 0, Keys.MOD_CONTROL));
        assertEquals(after, hostRect("fps"));
        assertTrue(screen.keyDown(Keys.Z, 0, Keys.MOD_CONTROL | Keys.MOD_SHIFT), "Ctrl+Shift+Z redoes too");
        assertTrue(screen.toolbar().undoButton().isEnabled());
    }

    @Test
    void clickingEmptySpaceClearsAndRightClickTogglesVisibility() {
        open(640, 360);
        screen.session().select("fps");
        Rect vp = screen.canvasArea().viewport().area();
        click(vp.right() - 2, vp.bottom() - 2);
        assertTrue(model().selectedId().isEmpty());
        Rect fps = logicalRect("fps");
        screen.mouseDown(fps.centerX(), fps.centerY(), Keys.MOUSE_RIGHT);
        screen.mouseUp(fps.centerX(), fps.centerY(), Keys.MOUSE_RIGHT);
        assertFalse(model().layout().find("fps").orElseThrow().enabled());
        assertEquals(Optional.of("fps"), model().selectedId());
        TestCanvas canvas = frame();
        assertTrue(canvas.hasText("Hidden"), "inspector badge for hidden widgets");
    }

    @Test
    void resizeHandleGrowsTheWidget() {
        open(640, 360);
        screen.session().select("coordinates");
        frame();
        HudRect before = hostRect("coordinates");
        HudRect handle = model().handleRects().get(HudEditorModel.Handle.SE);
        EditorViewport vp = screen.canvasArea().viewport();
        double hx = vp.toLogicalX(handle.centerX());
        double hy = vp.toLogicalY(handle.centerY());
        screen.mouseDown(hx, hy, Keys.MOUSE_LEFT);
        assertEquals(HudEditorModel.DragKind.RESIZE, model().dragKind());
        screen.mouseDrag(hx + 12, hy + 6, Keys.MOUSE_LEFT, 12, 6);
        screen.mouseUp(hx + 12, hy + 6, Keys.MOUSE_LEFT);
        HudRect after = hostRect("coordinates");
        assertTrue(after.width() > before.width());
        assertTrue(after.height() > before.height());
        assertEquals(before.x(), after.x());
    }

    @Test
    void keyboardNudgesHidesAndDuplicates() {
        open(640, 360);
        screen.session().select("fps");
        HudRect start = hostRect("fps");
        assertTrue(screen.keyDown(Keys.RIGHT, 0, 0));
        assertEquals(start.x() + 1, hostRect("fps").x());
        assertTrue(screen.keyDown(Keys.DOWN, 0, Keys.MOD_SHIFT));
        assertEquals(start.y() + 10, hostRect("fps").y());
        assertTrue(screen.keyDown(Keys.LEFT, 0, 0));
        assertTrue(screen.keyDown(Keys.UP, 0, 0));
        int count = screen.session().widgetCount();
        assertTrue(screen.keyDown(Keys.D, 0, Keys.MOD_CONTROL));
        assertEquals(count + 1, screen.session().widgetCount());
        assertEquals(Optional.of("fps-2"), model().selectedId());
        assertTrue(screen.keyDown(Keys.DELETE, 0, 0));
        assertFalse(model().layout().find("fps-2").orElseThrow().enabled());
        assertTrue(screen.keyDown(Keys.DELETE, 0, Keys.MOD_SHIFT));
        assertTrue(model().layout().find("fps-2").isEmpty(), "Shift+Delete removes");
        screen.session().select("crosshair");
        assertTrue(screen.keyDown(Keys.DELETE, 0, Keys.MOD_SHIFT));
        assertTrue(model().layout().find("crosshair").isPresent(), "the crosshair can only be hidden");
    }

    @Test
    void escapeSavesTheLayoutAndClosesAfterTheTransition() {
        open(640, 360);
        assertFalse(Files.exists(paths.hudLayoutFile()));
        screen.session().select("fps");
        screen.keyDown(Keys.RIGHT, 0, 0);
        assertTrue(screen.keyDown(Keys.ESCAPE, 0, 0));
        assertTrue(screen.wasSaved());
        assertTrue(Files.exists(paths.hudLayoutFile()));
        assertFalse(services.hud().isDirty());
        assertTrue(services.notifications().visible().stream().anyMatch(n -> n.title().equals("Layout saved")));
        assertTrue(screen.isClosing());
        assertFalse(host.closed());
        uiClock.advance(UiScreen.TRANSITION_MS + 10);
        frame();
        assertTrue(host.closed());
        clock.advance(1_100);
        screen.onClose();
        assertEquals(1, services.notifications().visible().stream().filter(n -> n.title().equals("Layout saved")).count(),
                "saving twice without changes shows no second toast");
    }

    @Test
    void doneButtonAndCtrlSSave() {
        open(640, 360);
        screen.session().select("ping");
        screen.keyDown(Keys.RIGHT, 0, 0);
        assertTrue(screen.keyDown(Keys.S, 0, Keys.MOD_CONTROL));
        assertTrue(Files.exists(paths.hudLayoutFile()));
        assertFalse(screen.isClosing());
        screen.toolbar().doneButton().click(screen.context());
        assertTrue(screen.isClosing());
    }

    @Test
    void widgetListTogglesVisibilityAndAddsMissingTypes() {
        open(640, 360);
        WidgetListPanel list = screen.widgetList();
        WidgetListPanel.WidgetRow fpsRow = list.rowFor("fps").orElseThrow();
        Toggle toggle = fpsRow.toggle();
        assertNotNull(toggle);
        assertTrue(toggle.isOn());
        click(toggle.bounds().centerX(), toggle.bounds().centerY());
        assertFalse(model().layout().find("fps").orElseThrow().enabled());
        frame();
        assertFalse(fpsRow.toggle().isOn());
        click(fpsRow.bounds().x() + 10, fpsRow.bounds().centerY());
        assertEquals(Optional.of("fps"), model().selectedId());

        HudWidgetType missing = null;
        for (HudWidgetType type : HudWidgetType.values()) {
            if (model().layout().byType(type).isEmpty()) {
                missing = type;
                break;
            }
        }
        assertNotNull(missing, "the default preset leaves at least one type unused");
        WidgetListPanel.WidgetRow addRow = list.addRowFor(missing).orElseThrow();
        addRow.addButton().click(screen.context());
        assertFalse(model().layout().byType(missing).isEmpty());
        frame();
        assertTrue(list.rowFor(missing.id()).isPresent(), "the row now shows the instance");

        list.searchField().setText("fps");
        frame();
        assertTrue(list.rowFor("fps").isPresent());
        assertTrue(list.rowFor("ping").isEmpty());
        assertEquals("fps", list.filter());
    }

    @Test
    void inspectorEditsCoalesceIntoOneUndoStepAndKeepTheAnchorPosition() {
        open(640, 360);
        screen.session().select("fps");
        frame();
        InspectorPanel inspector = screen.inspector();
        assertNotNull(inspector.scaleSlider());
        inspector.scaleSlider().change(1.5);
        inspector.scaleSlider().change(1.6);
        assertEquals(1.6, model().layout().find("fps").orElseThrow().scale(), 1e-9);
        assertTrue(model().undo());
        assertEquals(1.0, model().layout().find("fps").orElseThrow().scale(), 1e-9, "both drag steps were one undo entry");
        assertFalse(model().canUndo());
        uiClock.advance(InspectorPanel.COALESCE_MS + 1);
        inspector.scaleSlider().change(1.2);
        uiClock.advance(InspectorPanel.COALESCE_MS + 1);
        inspector.opacitySlider().change(0.5);
        assertEquals(0.5, model().layout().find("fps").orElseThrow().opacity(), 1e-9);
        assertTrue(model().undo());
        assertEquals(1.2, model().layout().find("fps").orElseThrow().scale(), 1e-9);
        assertEquals(1.0, model().layout().find("fps").orElseThrow().opacity(), 1e-9);
        frame();
        assertEquals(1.2, inspector.scaleSlider().doubleValue(), 1e-9, "controls follow the model");

        HudRect rect = hostRect("fps");
        inspector.anchorPicker().choose(screen.context(), HudAnchor.TOP_RIGHT);
        HudWidgetState moved = model().layout().find("fps").orElseThrow();
        assertEquals(HudAnchor.TOP_RIGHT, moved.anchor());
        assertEquals(rect, hostRect("fps"), "re-anchoring keeps the screen position");

        inspector.offsetXField().setText("30");
        assertEquals(30, model().layout().find("fps").orElseThrow().offsetX());
        inspector.widthFieldNode().setText("80");
        assertEquals(80, model().layout().find("fps").orElseThrow().width());
        inspector.visibleToggle().toggle(screen.context());
        assertFalse(model().layout().find("fps").orElseThrow().enabled());
        inspector.propControl("showLabel").map(Toggle.class::cast).orElseThrow().toggle(screen.context());
        assertFalse(model().layout().find("fps").orElseThrow().propBool("showLabel"));
        inspector.textColorField().apply(0xFF112233, true);
        assertEquals(0xFF112233, model().layout().find("fps").orElseThrow().textColor());
    }

    @Test
    void inspectorShowsEmptyStateAndCrosshairHint() {
        open(640, 360);
        TestCanvas canvas = frame();
        assertTrue(canvas.hasText("Select a widget to edit it"));
        screen.session().select("crosshair");
        canvas = frame();
        assertTrue(canvas.hasText("Open crosshair screen"));
        assertTrue(screen.inspector().anchorPicker() == null, "the crosshair has no position controls");
        screen.inspector().propControl("x");
    }

    @Test
    void presetSelectAppliesPresetsAndShowsCustomForEditedLayouts() {
        open(640, 360);
        EditorToolbar toolbar = screen.toolbar();
        assertEquals(HudPresets.DEFAULT, toolbar.presetSelect().value());
        List<String> options = toolbar.presetSelect().options();
        int minimal = options.indexOf(HudPresets.MINIMAL);
        assertTrue(minimal >= 0);
        toolbar.presetSelect().select(screen.context(), minimal);
        HudPreset preset = HudPresets.find(HudPresets.MINIMAL).orElseThrow();
        assertEquals(preset.layout(), model().layout());
        assertTrue(services.notifications().visible().stream().anyMatch(n -> n.body().contains("Minimal")));
        assertTrue(model().canUndo());
        screen.session().select("fps");
        screen.keyDown(Keys.RIGHT, 0, 0);
        assertEquals(EditorToolbar.CUSTOM, toolbar.presetSelect().value());
        assertTrue(toolbar.presetSelect().options().contains(EditorToolbar.CUSTOM));
    }

    @Test
    void savePresetDialogCreatesAUserPreset() {
        open(640, 360);
        screen.openSavePresetDialog();
        assertTrue(screen.context().popups().isOpen());
        for (char c : "Mine".toCharArray()) {
            screen.charTyped(c, 0);
        }
        assertTrue(screen.keyDown(Keys.ENTER, 0, 0));
        assertFalse(screen.context().popups().isOpen());
        assertEquals(1, services.hud().userPresets().size());
        assertEquals("Mine", services.hud().userPresets().get(0).name());
        assertTrue(screen.toolbar().presetSelect().options().contains(services.hud().userPresets().get(0).id()));
        assertTrue(services.notifications().visible().stream().anyMatch(n -> n.title().contains("Mine")));
        screen.openSavePresetDialog();
        assertTrue(screen.keyDown(Keys.ESCAPE, 0, 0), "Escape cancels the dialog instead of closing the screen");
        assertFalse(screen.context().popups().isOpen());
        assertFalse(screen.isClosing());
    }

    @Test
    void sessionHelpersFitResetAndBringToFront() {
        open(640, 360);
        HudEditorSession session = screen.session();
        session.select("fps");
        session.model().update(w -> w.withSize(120, 40));
        assertTrue(session.fitToContent(TestCanvas.metrics()));
        HudWidgetState fitted = model().layout().find("fps").orElseThrow();
        assertTrue(fitted.width() < 120 && fitted.height() < 40);
        assertTrue(session.bringToFront());
        assertEquals("fps", model().layout().widgets().get(model().layout().size() - 1).id());
        assertFalse(session.bringToFront(), "already in front");
        assertTrue(session.resetSelected());
        assertEquals(HudWidgetType.FPS.defaultWidth(), model().layout().find("fps").orElseThrow().width());
        assertTrue(session.toggleEnabled("ping"));
        assertFalse(model().layout().find("ping").orElseThrow().enabled());
        assertEquals(Optional.of("fps"), model().selectedId(), "toggling another widget keeps the selection");
        assertFalse(session.toggleEnabled("nope"));
        session.setGridVisible(false);
        assertFalse(session.gridVisible());
        session.setSnapEnabled(false);
        assertFalse(session.model().snapEnabled());
        assertNotEquals(model().layout(), HudPresets.defaultPreset().layout());
        session.resetLayout();
        assertEquals(HudPresets.defaultPreset().layout(), model().layout());
    }

    @Test
    void liveGameDataIsUsedInsideAWorld() {
        game.inWorld = true;
        game.onServer("mc.example.invalid", "Example", 40);
        open(640, 360);
        assertTrue(screen.session().isLive());
        TestCanvas canvas = frame();
        assertTrue(canvas.hasText("Live data"));
        assertTrue(canvas.hasText("120"), "fps from the game bridge");
    }
}
