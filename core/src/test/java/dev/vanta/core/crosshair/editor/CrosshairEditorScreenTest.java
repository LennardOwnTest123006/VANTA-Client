package dev.vanta.core.crosshair.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.crosshair.CrosshairPreset;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.crosshair.CrosshairShape;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.ManualClock;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.TestHost;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiEnvironment;
import dev.vanta.core.ui.UiScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrosshairEditorScreenTest {
    @TempDir
    Path dir;
    private VantaServices services;
    private VantaPaths paths;
    private final ManualClock uiClock = new ManualClock(2_000L);
    private final TestHost host = new TestHost();
    private CrosshairEditorScreen screen;

    @BeforeEach
    void setUp() {
        paths = VantaPaths.inGameDirectory(dir);
        services = VantaServices.create(paths, new FakeGameBridge(), new FakeOptionsBridge(), new FakeKeybindBridge(),
                new FakeResourcePackBridge(), MutableClock.standard());
        services.load();
    }

    private CrosshairEditorScreen open(int w, int h) {
        screen = new CrosshairEditorScreen(services);
        screen.attach(new UiEnvironment(host, Theme.DEFAULT, uiClock, TestCanvas.metrics(), Function.identity()));
        screen.init(w, h);
        frame();
        return screen;
    }

    private TestCanvas frame() {
        TestCanvas canvas = new TestCanvas(screen.width(), screen.height());
        screen.render(canvas, -100, -100, 0f);
        return canvas;
    }

    @Test
    void controlsEditTheStoreLiveAndMarkTheStyleCustom() {
        open(640, 360);
        assertTrue(screen.isWide());
        assertEquals(CrosshairStyle.DEFAULT, screen.style());
        screen.sizeSlider().change(9);
        assertEquals(9, screen.style().size());
        assertEquals("custom", services.settings().get(VantaSettings.COSMETICS_CROSSHAIR_PRESET));
        screen.thicknessSlider().change(2);
        screen.gapSlider().change(3);
        assertEquals(2, screen.style().thickness());
        assertEquals(3, screen.style().gap());
        int dot = screen.shapeSelect().options().indexOf(CrosshairShape.DOT);
        screen.shapeSelect().select(screen.context(), dot);
        assertEquals(CrosshairShape.DOT, screen.style().shape());
        screen.outlineToggle().toggle(screen.context());
        assertFalse(screen.style().outline());
        screen.opacitySlider().change(50);
        assertEquals(0.5, screen.style().opacity(), 1e-9);
        screen.colorField().apply(0xFF00FF00, true);
        assertEquals(0xFF00FF00, screen.style().color());
        screen.dynamicToggle().toggle(screen.context());
        assertTrue(screen.style().dynamic());
        assertTrue(services.crosshair().isDirty());
        TestCanvas canvas = frame();
        assertTrue(canvas.hasText("Crosshair"));
        assertTrue(canvas.hasText("Dark"));
        assertTrue(canvas.hasText("3× zoom"));
        assertTrue(screen.preview().pulseExpansion(screen.context()) >= 0);
    }

    @Test
    void presetTilesApplyPresetsAndHighlightTheActiveOne() {
        open(640, 360);
        PresetGallery.PresetTile circle = screen.gallery().tile(CrosshairPresets.CIRCLE);
        assertFalse(circle.isActive());
        assertTrue(screen.gallery().tile(CrosshairPresets.DEFAULT).isActive());
        Rect r = circle.bounds();
        assertTrue(r.w() > 0, "tiles are laid out");
        screen.mouseDown(r.centerX(), r.centerY(), Keys.MOUSE_LEFT);
        screen.mouseUp(r.centerX(), r.centerY(), Keys.MOUSE_LEFT);
        CrosshairPreset preset = CrosshairPresets.find(CrosshairPresets.CIRCLE).orElseThrow();
        assertEquals(preset.style(), screen.style());
        assertTrue(circle.isActive());
        assertEquals(CrosshairPresets.CIRCLE, services.settings().get(VantaSettings.COSMETICS_CROSSHAIR_PRESET));
        assertEquals(CrosshairShape.CIRCLE, screen.shapeSelect().value(), "controls follow the applied preset");
        screen.applyPreset(CrosshairPresets.find(CrosshairPresets.PRECISION).orElseThrow());
        assertTrue(screen.dynamicToggle().isOn());
    }

    @Test
    void useVanillaTogglesTheCrosshairWidget() {
        open(640, 360);
        assertFalse(screen.isVanillaCrosshair());
        screen.useVanillaToggle().toggle(screen.context());
        assertTrue(screen.isVanillaCrosshair());
        assertFalse(services.hud().layout().byType(HudWidgetType.CROSSHAIR).get(0).enabled());
        screen.useVanillaToggle().toggle(screen.context());
        assertFalse(screen.isVanillaCrosshair());
        services.hud().setLayout(services.hud().layout().without("crosshair"));
        assertTrue(screen.isVanillaCrosshair(), "no widget means vanilla");
        screen.setVanillaCrosshair(false);
        assertEquals(1, services.hud().layout().byType(HudWidgetType.CROSSHAIR).size(), "widget re-created");
        assertFalse(screen.isVanillaCrosshair());
    }

    @Test
    void resetEscapeAndDoneSaveToDisk() {
        open(640, 360);
        screen.sizeSlider().change(12);
        screen.apply(CrosshairStyle.DEFAULT);
        assertEquals(CrosshairStyle.DEFAULT, screen.style());
        assertEquals(CrosshairPresets.DEFAULT, services.settings().get(VantaSettings.COSMETICS_CROSSHAIR_PRESET));
        screen.sizeSlider().change(7);
        assertFalse(Files.exists(paths.crosshairFile()));
        assertTrue(screen.keyDown(Keys.ESCAPE, 0, 0));
        assertTrue(screen.wasSaved());
        assertTrue(Files.exists(paths.crosshairFile()));
        assertFalse(services.crosshair().isDirty());
        assertTrue(services.notifications().visible().stream().anyMatch(n -> n.title().equals("Crosshair saved")));
        assertTrue(screen.isClosing());
        uiClock.advance(UiScreen.TRANSITION_MS + 10);
        frame();
        assertTrue(host.closed());
        screen.onClose();
        assertEquals(1, services.notifications().visible().stream().filter(n -> n.title().equals("Crosshair saved")).count());
    }

    @Test
    void ctrlSAndSaveButtonWriteWithoutClosing() {
        open(640, 360);
        screen.gapSlider().change(4);
        assertTrue(screen.keyDown(Keys.S, 0, Keys.MOD_CONTROL));
        assertTrue(Files.exists(paths.crosshairFile()));
        assertFalse(screen.isClosing());
        screen.gapSlider().change(5);
        screen.saveButton().click(screen.context());
        assertFalse(services.crosshair().isDirty());
        assertFalse(screen.isClosing());
    }

    @Test
    void narrowScreensStackEverythingIntoOneColumn() {
        open(427, 240);
        assertFalse(screen.isWide());
        TestCanvas canvas = frame();
        assertTrue(canvas.hasText("Crosshair"));
        Rect preview = screen.preview().bounds();
        Rect gallery = screen.gallery().bounds();
        assertTrue(preview.w() > 0 && gallery.w() > 0);
        assertTrue(gallery.y() > preview.y(), "presets below the preview");
        screen.resize(800, 400);
        frame();
        assertTrue(screen.isWide());
        assertTrue(screen.gallery().tile(CrosshairPresets.DEFAULT).bounds().w() > 0);
    }
}
