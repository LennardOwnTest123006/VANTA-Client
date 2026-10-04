package dev.vanta.core.preview.previews;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.preview.PreviewProvider;
import dev.vanta.core.preview.ScreenPreview;
import dev.vanta.core.preview.ScreenPreviews;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.cosmetics.CosmeticsScreen;
import dev.vanta.core.screen.cosmetics.ThemeResolver;
import dev.vanta.core.screen.keybinds.KeybindsScreen;
import dev.vanta.core.screen.profiles.ProfilesScreen;
import dev.vanta.core.screen.stats.StatisticsScreen;
import dev.vanta.core.ui.UiScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Previews of the screens registered by {@link dev.vanta.core.screen.Screens2}: profiles (clean, unsaved changes,
 * inline rename, import dialog, create dialog, compact), keybinds (default, conflicts filter, search, capturing,
 * compact), cosmetics (every section, a re-themed variant, installed packs, compact) and statistics (populated,
 * fresh install, disabled, compact). Desktop frames are 854x480 at GUI scale 2, compact ones 427x240.
 */
public final class Screens2Previews implements PreviewProvider {
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

    private static UiScreen screen(Screens2PreviewServices p, ScreenId id) {
        return (UiScreen) p.services.screens().create(id).orElseThrow();
    }

    private static Supplier<UiScreen> with(Function<Screens2PreviewServices, UiScreen> factory) {
        return () -> factory.apply(Screens2PreviewServices.create());
    }

    @Override
    public List<ScreenPreviews.Entry> previews() {
        List<ScreenPreviews.Entry> out = new ArrayList<>();

        // ---- profiles
        out.add(ScreenPreviews.Entry.of("profiles", with(p -> screen(p, ScreenId.PROFILES)),
                desktop().mouseOver("profile.pvp.activate")));
        out.add(ScreenPreviews.Entry.of("profiles-unsaved", with(p -> {
            p.withUnsavedChanges();
            return screen(p, ScreenId.PROFILES);
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("profiles-rename", with(p -> {
            ProfilesScreen s = (ProfilesScreen) screen(p, ScreenId.PROFILES);
            s.onceInitialised(() -> {
                s.card("building").orElseThrow().startRename(s.context());
                s.card("building").orElseThrow().nameField().setText("Mega builds");
            });
            return s;
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("profiles-import", with(p -> {
            p.withImportFiles();
            ProfilesScreen s = (ProfilesScreen) screen(p, ScreenId.PROFILES);
            s.onceInitialised(() -> s.openImportDialog().tabs().select(s.context(), 1));
            return s;
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("profiles-create", with(p -> {
            ProfilesScreen s = (ProfilesScreen) screen(p, ScreenId.PROFILES);
            s.onceInitialised(() -> s.openCreateDialog().field().setText("Weekend survival"));
            return s;
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("profiles-small", with(p -> screen(p, ScreenId.PROFILES)), small()));

        // ---- keybinds
        out.add(ScreenPreviews.Entry.of("keybinds", with(p -> {
            p.withKeyConflicts();
            return screen(p, ScreenId.KEYBINDS);
        }), desktop().mouseOver("keybind.key.vanta.hud_editor.field")));
        out.add(ScreenPreviews.Entry.of("keybinds-clean", with(p -> screen(p, ScreenId.KEYBINDS)), desktop()));
        out.add(ScreenPreviews.Entry.of("keybinds-conflicts", with(p -> {
            p.withKeyConflicts();
            KeybindsScreen s = (KeybindsScreen) screen(p, ScreenId.KEYBINDS);
            s.onceInitialised(() -> s.conflictsOnlyToggle().set(s.context(), true));
            return s;
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("keybinds-search", with(p -> {
            p.withKeyConflicts();
            KeybindsScreen s = (KeybindsScreen) screen(p, ScreenId.KEYBINDS);
            s.onceInitialised(() -> s.setQuery("hud"));
            return s;
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("keybinds-capture", with(p -> {
            KeybindsScreen s = (KeybindsScreen) screen(p, ScreenId.KEYBINDS);
            s.onceInitialised(() -> s.row("key.vanta.zoom").orElseThrow().field().startCapture(s.context()));
            return s;
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("keybinds-small", with(p -> {
            p.withKeyConflicts();
            return screen(p, ScreenId.KEYBINDS);
        }), small()));

        // ---- cosmetics
        for (CosmeticsScreen.Section section : CosmeticsScreen.Section.values()) {
            out.add(ScreenPreviews.Entry.of("cosmetics-" + section.id(), with(p -> {
                if (section == CosmeticsScreen.Section.PACKS) {
                    p.withCosmeticPack();
                }
                CosmeticsScreen s = (CosmeticsScreen) screen(p, ScreenId.COSMETICS);
                s.showSection(section);
                return s;
            }), desktop()));
        }
        out.add(ScreenPreviews.Entry.of("cosmetics-packs-empty", with(p -> {
            CosmeticsScreen s = (CosmeticsScreen) screen(p, ScreenId.COSMETICS);
            s.showSection(CosmeticsScreen.Section.PACKS);
            return s;
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("cosmetics-themes-midnight", () -> {
            Screens2PreviewServices p = Screens2PreviewServices.create();
            p.services.cosmetics().selectTheme("midnight");
            return screen(p, ScreenId.COSMETICS);
        }, desktop().theme(ThemeResolver.themeFor(themedServices("midnight")))));
        out.add(ScreenPreviews.Entry.of("cosmetics-themes-aurora", () -> {
            Screens2PreviewServices p = Screens2PreviewServices.create();
            p.services.cosmetics().selectTheme("aurora");
            return screen(p, ScreenId.COSMETICS);
        }, desktop().theme(ThemeResolver.themeFor(themedServices("aurora"))).mouseOver("cosmetic.themes.ember")));
        out.add(ScreenPreviews.Entry.of("cosmetics-small", with(p -> {
            CosmeticsScreen s = (CosmeticsScreen) screen(p, ScreenId.COSMETICS);
            s.showSection(CosmeticsScreen.Section.BACKGROUNDS);
            return s;
        }), small()));

        // ---- statistics
        out.add(ScreenPreviews.Entry.of("statistics", with(p -> {
            p.withSampleStats().withLiveSession();
            return screen(p, ScreenId.STATISTICS);
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("statistics-tall", with(p -> {
            p.withSampleStats().withLiveSession();
            return screen(p, ScreenId.STATISTICS);
        }), desktop().size(DESKTOP_W, 900)));
        out.add(ScreenPreviews.Entry.of("statistics-empty", with(p -> {
            p.onTitleScreen();
            return screen(p, ScreenId.STATISTICS);
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("statistics-disabled", with(p -> {
            p.withSampleStats().withStatsDisabled();
            return screen(p, ScreenId.STATISTICS);
        }), desktop()));
        out.add(ScreenPreviews.Entry.of("statistics-small", with(p -> {
            p.withSampleStats().withLiveSession();
            return screen(p, ScreenId.STATISTICS);
        }), small()));
        return out;
    }

    private static dev.vanta.core.screen.VantaServices themedServices(String themeId) {
        Screens2PreviewServices p = Screens2PreviewServices.create();
        p.services.cosmetics().selectTheme(themeId);
        return p.services;
    }
}
