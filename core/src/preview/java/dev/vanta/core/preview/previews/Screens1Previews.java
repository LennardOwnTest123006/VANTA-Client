package dev.vanta.core.preview.previews;

import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.preview.PreviewProvider;
import dev.vanta.core.preview.ScreenPreview;
import dev.vanta.core.preview.ScreenPreviews;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.ThemeFactory;
import dev.vanta.core.screen.menu.MainMenuScreen;
import dev.vanta.core.screen.perf.PerformanceScreen;
import dev.vanta.core.screen.search.SearchOverlay;
import dev.vanta.core.screen.settings.SettingsScreen;
import dev.vanta.core.settings.SettingCategory;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Previews of the screens registered by {@link dev.vanta.core.screen.Screens1}: main menu (every background,
 * hover, entrance frames, compact layout), settings (categories, search, deep link, compact), search overlay,
 * Performance Center (in world, title screen, compact), accessibility (default and high contrast), resource packs
 * (clean and with pending changes) and about. Desktop frames are 854x480 at GUI scale 2, compact ones 427x240.
 */
public final class Screens1Previews implements PreviewProvider {
    private static final int DESKTOP_W = 854;
    private static final int DESKTOP_H = 480;
    private static final int SMALL_W = 427;
    private static final int SMALL_H = 240;

    private static ScreenPreview.Options desktop() {
        return ScreenPreview.Options.standard().size(DESKTOP_W, DESKTOP_H).scale(2).lang(Lang::tr);
    }

    private static ScreenPreview.Options small() {
        return ScreenPreview.Options.standard().size(SMALL_W, SMALL_H).scale(2).lang(Lang::tr);
    }

    private static UiScreen screen(PreviewServices p, ScreenId id) {
        return (UiScreen) p.services.screens().create(id).orElseThrow();
    }

    private static Supplier<UiScreen> menu(MenuBackground background, MenuParticles particles) {
        return () -> {
            PreviewServices p = PreviewServices.create().onTitleScreen();
            p.services.settings().set(VantaSettings.MENU_BACKGROUND, background);
            p.services.settings().set(VantaSettings.MENU_PARTICLES, particles);
            return screen(p, ScreenId.MAIN_MENU);
        };
    }

    private static Supplier<UiScreen> settings(Function<PreviewServices, UiScreen> factory) {
        return () -> factory.apply(PreviewServices.create().onTitleScreen());
    }

    @Override
    public List<ScreenPreviews.Entry> previews() {
        List<ScreenPreviews.Entry> out = new ArrayList<>();

        // ---- main menu
        out.add(ScreenPreviews.Entry.of("main-menu", menu(MenuBackground.VIOLET_HORIZON, MenuParticles.EMBERS),
                desktop().mouseOver("menu.play")).withFrames(0L, 60L, 120L, 180L, 240L));
        out.add(ScreenPreviews.Entry.of("main-menu-gradient", menu(MenuBackground.GRADIENT_DARK, MenuParticles.DUST),
                desktop()));
        out.add(ScreenPreviews.Entry.of("main-menu-grid", menu(MenuBackground.GRAPHITE_GRID, MenuParticles.NONE),
                desktop()));
        out.add(ScreenPreviews.Entry.of("main-menu-panorama", menu(MenuBackground.VANILLA_PANORAMA, MenuParticles.NONE),
                desktop().backdrop(0xFF3A5A8C)));
        out.add(ScreenPreviews.Entry.of("main-menu-solid", menu(MenuBackground.SOLID, MenuParticles.NONE), desktop()));
        out.add(ScreenPreviews.Entry.of("main-menu-small", menu(MenuBackground.VIOLET_HORIZON, MenuParticles.EMBERS),
                small()));
        out.add(ScreenPreviews.Entry.of("main-menu-no-world", () -> {
            PreviewServices p = PreviewServices.create().onTitleScreen();
            p.game.lastWorldName = java.util.Optional.empty();
            return screen(p, ScreenId.MAIN_MENU);
        }, desktop()));

        // ---- settings
        out.add(ScreenPreviews.Entry.of("settings", settings(p -> screen(p, ScreenId.SETTINGS)), desktop()));
        out.add(ScreenPreviews.Entry.of("settings-video", settings(p -> {
            ScreenNavigator.forServices(p.services).stageCategory(SettingCategory.VIDEO);
            return screen(p, ScreenId.SETTINGS);
        }), desktop().mouseOver("setting.video.renderDistance")));
        out.add(ScreenPreviews.Entry.of("settings-search", settings(p -> {
            SettingsScreen s = (SettingsScreen) screen(p, ScreenId.SETTINGS);
            s.setQuery("fps");
            return s;
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("settings-reveal", settings(p -> {
            p.services.settings().set(VantaSettings.HUD_GLOBAL_SCALE, 1.25);
            ScreenNavigator.forServices(p.services).stageSetting(VantaSettings.HUD_GLOBAL_SCALE);
            return screen(p, ScreenId.SETTINGS);
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("settings-small", settings(p -> screen(p, ScreenId.SETTINGS)), small()));
        out.add(ScreenPreviews.Entry.of("settings-dialog", settings(p -> {
            SettingsScreen s = (SettingsScreen) screen(p, ScreenId.SETTINGS);
            s.onceInitialised(() -> s.resetAllButton().click(s.context()));
            return s;
        }), desktop()));

        // ---- search overlay
        out.add(ScreenPreviews.Entry.of("search-overlay", settings(p -> screen(p, ScreenId.SEARCH)), desktop()));
        out.add(ScreenPreviews.Entry.of("search-overlay-query", settings(p -> {
            SearchOverlay s = (SearchOverlay) screen(p, ScreenId.SEARCH);
            s.onceInitialised(() -> s.field().setText("render"));
            return s;
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("search-overlay-small", settings(p -> {
            SearchOverlay s = (SearchOverlay) screen(p, ScreenId.SEARCH);
            s.onceInitialised(() -> s.field().setText("hud"));
            return s;
        }), small()));

        // ---- performance center
        out.add(ScreenPreviews.Entry.of("performance", () -> {
            PreviewServices p = PreviewServices.create().withFrameHistory();
            return screen(p, ScreenId.PERFORMANCE);
        }, desktop()));
        out.add(ScreenPreviews.Entry.of("performance-preset", () -> {
            PreviewServices p = PreviewServices.create().withFrameHistory();
            PerformanceScreen s = (PerformanceScreen) screen(p, ScreenId.PERFORMANCE);
            s.selectPreset(PerformancePreset.LOW);
            return s;
        }, desktop()));
        out.add(ScreenPreviews.Entry.of("performance-no-world", () -> {
            PreviewServices p = PreviewServices.create().onTitleScreen();
            return screen(p, ScreenId.PERFORMANCE);
        }, desktop()));
        out.add(ScreenPreviews.Entry.of("performance-small", () -> {
            PreviewServices p = PreviewServices.create().withFrameHistory();
            return screen(p, ScreenId.PERFORMANCE);
        }, small()));

        // ---- accessibility
        out.add(ScreenPreviews.Entry.of("accessibility", settings(p -> screen(p, ScreenId.ACCESSIBILITY)), desktop()));
        out.add(ScreenPreviews.Entry.of("accessibility-high-contrast", () -> {
            PreviewServices p = PreviewServices.create().onTitleScreen();
            p.services.settings().set(VantaSettings.ACCESSIBILITY_HIGH_CONTRAST, true);
            p.services.settings().set(VantaSettings.ACCESSIBILITY_COLOR_BLIND_PALETTE,
                    dev.vanta.core.accessibility.ColorBlindPalette.DEUTERANOPIA);
            return screen(p, ScreenId.ACCESSIBILITY);
        }, desktop().theme(highContrastTheme())));
        out.add(ScreenPreviews.Entry.of("accessibility-small", settings(p -> screen(p, ScreenId.ACCESSIBILITY)), small()));

        // ---- resource packs
        out.add(ScreenPreviews.Entry.of("resource-packs", () -> {
            PreviewServices p = PreviewServices.createWithPacks(new PreviewResourcePackBridge()).onTitleScreen();
            return screen(p, ScreenId.RESOURCE_PACKS);
        }, desktop()));
        out.add(ScreenPreviews.Entry.of("resource-packs-pending", () -> {
            PreviewResourcePackBridge packs = new PreviewResourcePackBridge();
            packs.setEnabled("file/Faithful 32x.zip", true);
            packs.setEnabled("high_contrast", true);
            packs.move("high_contrast", 1);
            PreviewServices p = PreviewServices.createWithPacks(packs).onTitleScreen();
            return screen(p, ScreenId.RESOURCE_PACKS);
        }, desktop().mouseOver("pack.high_contrast")));
        out.add(ScreenPreviews.Entry.of("resource-packs-small", () -> {
            PreviewServices p = PreviewServices.createWithPacks(new PreviewResourcePackBridge()).onTitleScreen();
            return screen(p, ScreenId.RESOURCE_PACKS);
        }, small()));

        // ---- about
        out.add(ScreenPreviews.Entry.of("about", settings(p -> {
            p.services.setWebsiteUrl("https://vanta.example.invalid");
            return screen(p, ScreenId.ABOUT);
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("about-small", settings(p -> screen(p, ScreenId.ABOUT)), small()));
        return out;
    }

    private static Theme highContrastTheme() {
        PreviewServices p = PreviewServices.create();
        p.services.settings().set(VantaSettings.ACCESSIBILITY_HIGH_CONTRAST, true);
        p.services.settings().set(VantaSettings.ACCESSIBILITY_COLOR_BLIND_PALETTE,
                dev.vanta.core.accessibility.ColorBlindPalette.DEUTERANOPIA);
        return ThemeFactory.themeFor(p.services);
    }
}
