package dev.vanta.core.preview.previews;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.cosmetics.HudTheme;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.crosshair.editor.CrosshairEditorScreen;
import dev.vanta.core.crosshair.render.CrosshairRenderer;
import dev.vanta.core.hud.editor.HudEditorScreen;
import dev.vanta.core.hud.render.HudRenderer;
import dev.vanta.core.hud.render.SampleGameData;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.notifications.render.NotificationOverlay;
import dev.vanta.core.preview.PreviewProvider;
import dev.vanta.core.preview.ScreenPreview;
import dev.vanta.core.preview.ScreenPreviews;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Previews of the HUD editor (desktop and compact sizes, inspector open, save-preset dialog), the crosshair
 * customizer and the in-game HUD with sample data in each of the four HUD themes, including notification toasts.
 */
public final class HudPreviews implements PreviewProvider {

    /** Public no-arg constructor for {@link java.util.ServiceLoader}. */
    public HudPreviews() {
    }

    @Override
    public List<ScreenPreviews.Entry> previews() {
        List<ScreenPreviews.Entry> entries = new ArrayList<>();
        ScreenPreview.Options desktop = ScreenPreview.Options.standard().lang(Lang::tr);
        ScreenPreview.Options compact = ScreenPreview.Options.standard().size(427, 240).scale(3).lang(Lang::tr);
        entries.add(ScreenPreviews.Entry.of("hud-editor",
                () -> editor(s -> s.session().select("coordinates")), desktop));
        entries.add(ScreenPreviews.Entry.of("hud-editor-live",
                () -> liveEditor(s -> s.session().select("keystrokes")), desktop));
        entries.add(ScreenPreviews.Entry.of("hud-editor-compact", () -> editor(s -> s.session().select("fps")), compact));
        entries.add(ScreenPreviews.Entry.of("hud-editor-compact-inspector", () -> editor(s -> {
            s.session().select("fps");
            s.toggleInspector();
        }), compact));
        entries.add(ScreenPreviews.Entry.of("hud-editor-compact-widgets", () -> editor(s -> {
            s.session().select("armor");
            s.toggleWidgetList();
        }), compact));
        entries.add(ScreenPreviews.Entry.of("hud-editor-save-preset",
                () -> editor(HudEditorScreen::openSavePresetDialog), desktop));
        entries.add(ScreenPreviews.Entry.of("crosshair-editor", HudPreviews::crosshair, desktop));
        entries.add(ScreenPreviews.Entry.of("crosshair-editor-compact", HudPreviews::crosshair, compact));
        for (HudTheme theme : HudTheme.values()) {
            entries.add(ScreenPreviews.Entry.of("hud-ingame-" + theme.id(), () -> new HudMockScreen(theme), desktop));
        }
        entries.add(ScreenPreviews.Entry.of("hud-ingame-pvp-compact", () -> new HudMockScreen(HudTheme.GLASS, "pvp"),
                compact));
        return entries;
    }

    // ---- factories -------------------------------------------------------------------------------------------------

    /** Services on a throw-away config directory with the fake bridges. */
    static VantaServices newServices(boolean inWorld) {
        try {
            Path dir = Files.createTempDirectory("vanta-hud-preview-");
            FakeGameBridge game = new FakeGameBridge();
            if (!inWorld) {
                game.onTitleScreen();
            }
            VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, new FakeOptionsBridge(),
                    new FakeKeybindBridge(), new FakeResourcePackBridge(), MutableClock.standard());
            services.load();
            return services;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static HudEditorScreen editor(java.util.function.Consumer<HudEditorScreen> initial) {
        VantaServices services = newServices(false);
        return new HudEditorScreen(services).withInitialAction(initial);
    }

    private static HudEditorScreen liveEditor(java.util.function.Consumer<HudEditorScreen> initial) {
        VantaServices services = newServices(true);
        FakeGameBridge game = (FakeGameBridge) services.game();
        game.onServer("play.sample.invalid", "Sample Server", 48);
        services.hud().applyPreset(services.hud().findPreset("pvp").orElseThrow());
        return new HudEditorScreen(services).withInitialAction(initial);
    }

    private static CrosshairEditorScreen crosshair() {
        VantaServices services = newServices(false);
        services.crosshair().applyPreset(CrosshairPresets.find(CrosshairPresets.PRECISION).orElseThrow());
        return new CrosshairEditorScreen(services);
    }

    /**
     * Stand-in for the in-game HUD: a dusk gradient labelled as a preview background, every widget of a preset
     * drawn from {@link SampleGameData}, the custom crosshair and two notification toasts.
     */
    static final class HudMockScreen extends UiScreen {
        private final VantaServices services;
        private final HudRenderer renderer;
        private final CrosshairRenderer crosshair;
        private final NotificationOverlay overlay;

        HudMockScreen(HudTheme theme) {
            this(theme, "default");
        }

        HudMockScreen(HudTheme theme, String presetId) {
            this.services = newServices(true);
            services.settings().set(VantaSettings.COSMETICS_HUD_THEME, theme);
            services.hud().applyPreset(services.hud().findPreset(presetId).orElseThrow());
            services.crosshair().applyPreset(CrosshairPresets.find(CrosshairPresets.DEFAULT).orElseThrow());
            this.renderer = new HudRenderer(services, new SampleGameData());
            this.crosshair = new CrosshairRenderer(services);
            this.overlay = new NotificationOverlay(services);
            MutableClock clock = (MutableClock) services.clock();
            services.notifications().hudPresetApplied(Lang.tr("vanta.hud.preset." + presetId));
            clock.advance(1_200L);
            services.notifications().post(NotificationKind.INFO, Lang.tr("vanta.notification.render_distance_suggestion.title"),
                    Lang.tr("vanta.notification.render_distance_suggestion.body", 12, 10), 8000L);
            clock.advance(900L);
            services.tick();
            renderer.tick();
            overlay.tick();
        }

        @Override
        public String title() {
            return "HUD preview";
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean wantsBlur() {
            return false;
        }

        @Override
        protected UiNode build(UiContext ctx) {
            return new UiNode() {
            };
        }

        @Override
        protected void renderBackground(Canvas canvas, UiContext context) {
            int w = canvas.width();
            int h = canvas.height();
            int horizon = Math.round(h * 0.56f);
            canvas.fillGradientV(0, 0, w, horizon, 0xFF1A2130, 0xFF2D3550);
            canvas.fillGradientV(0, horizon, w, h - horizon, 0xFF2A2F3E, 0xFF0D0F14);
            canvas.fill(0, horizon, w, 1, Colors.withAlpha(0xFF7C5CFF, 0.35f));
            String label = Lang.tr("vanta.hud.preview.background");
            canvas.textCentered(label, w / 2, horizon + 6, Colors.withAlpha(0xFFA1A1AA, 0.9f), FontKind.UI, false);
        }

        @Override
        protected void renderOverlay(Canvas canvas, UiContext context) {
            renderer.render(canvas, width(), height(), 0f);
            crosshair.render(canvas, width(), height(), 0f);
            overlay.render(canvas, width(), height(), 0f);
        }
    }
}
