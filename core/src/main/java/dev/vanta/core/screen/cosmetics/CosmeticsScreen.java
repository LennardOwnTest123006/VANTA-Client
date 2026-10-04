package dev.vanta.core.screen.cosmetics;

import dev.vanta.core.cosmetics.Badge;
import dev.vanta.core.cosmetics.CosmeticPack;
import dev.vanta.core.cosmetics.CosmeticsRegistry;
import dev.vanta.core.cosmetics.CosmeticsSelection;
import dev.vanta.core.cosmetics.CosmeticsStore;
import dev.vanta.core.cosmetics.HudTheme;
import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.cosmetics.UiThemeDefinition;
import dev.vanta.core.crosshair.CrosshairPreset;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.VantaShell;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.widget.Button;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Cosmetics screen: a rail with UI themes, menu backgrounds, HUD styles, crosshair presets, menu particles, badges
 * and installed packs. Every option is a {@link CosmeticCard} with a live thumbnail; selecting one writes the
 * {@link CosmeticsStore} / settings immediately, which re-themes this screen on the spot. Cosmetics never affect
 * gameplay, which the footer states on every section.
 */
public final class CosmeticsScreen extends ThemedScreen {

    /** The rail sections. */
    public enum Section {
        THEMES("vanta.cosmetics.themes", Icons.PALETTE),
        BACKGROUNDS("vanta.cosmetics.backgrounds", Icons.GRID),
        HUD("vanta.cosmetics.hud_theme", Icons.SLIDERS),
        CROSSHAIR("vanta.cosmetics.crosshair_presets", Icons.CROSSHAIR),
        PARTICLES("vanta.cosmetics.particles", Icons.STAR),
        BADGES("vanta.cosmetics.badges", Icons.PROFILE),
        PACKS("vanta.cosmetics.packs", Icons.PACKAGE);

        private final String langKey;
        private final Icons icon;

        Section(String langKey, Icons icon) {
            this.langKey = langKey;
            this.icon = icon;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Translation key of the section heading. */
        public String langKey() {
            return langKey;
        }

        /** Translation key of the short rail label. */
        public String railKey() {
            return "vanta.cosmetics.rail." + id();
        }

        public Icons icon() {
            return icon;
        }

        static Section fromId(String id) {
            for (Section s : values()) {
                if (s.id().equals(id)) {
                    return s;
                }
            }
            return THEMES;
        }
    }

    private static final long PARTICLE_SEED = 20261004L;

    private final CosmeticsStore cosmetics;
    private final CosmeticsRegistry registry;
    private Section section = Section.THEMES;
    private VantaShell shell;
    private Column body;
    private ScrollPanel scroll;
    private final List<CosmeticCard> cards = new ArrayList<>();

    public CosmeticsScreen(VantaServices services) {
        super(services, ScreenId.COSMETICS);
        this.cosmetics = services.cosmetics();
        this.registry = services.cosmeticsRegistry();
    }

    @Override
    protected UiNode build(UiContext ctx) {
        shell = newShell();
        shell.footerText(Lang.tr("vanta.cosmetics.footer"));
        List<VantaShell.RailItem> items = new ArrayList<>();
        for (Section s : Section.values()) {
            items.add(new VantaShell.RailItem(s.id(), s.icon(), Lang.tr(s.railKey())));
        }
        shell.rail(items, section.id(), id -> showSection(Section.fromId(id)));
        body = new Column(Theme.SPACE_4);
        scroll = new ScrollPanel(body).edgeFade(ctx.theme().bgBase());
        shell.content(scroll);
        rebuild();
        return shell;
    }

    /** Switches the rail section. */
    public void showSection(Section next) {
        section = next;
        if (shell == null) {
            return; // before build(): the section is picked up when the tree is created
        }
        if (!next.id().equals(shell.selectedRail())) {
            shell.selectRail(context(), next.id());
        }
        rebuild();
        scroll.setScrollY(context(), 0f, false);
    }

    /** Current section. */
    public Section section() {
        return section;
    }

    /** Cards of the current section. */
    public List<CosmeticCard> cards() {
        return List.copyOf(cards);
    }

    /** The card with the given title in the current section. */
    public Optional<CosmeticCard> card(String title) {
        return cards.stream().filter(c -> c.title().equals(title)).findFirst();
    }

    /** Rebuilds the current section from the stores. */
    public void rebuild() {
        body.clearChildren();
        cards.clear();
        switch (section) {
            case THEMES -> buildThemes();
            case BACKGROUNDS -> buildBackgrounds();
            case HUD -> buildHud();
            case CROSSHAIR -> buildCrosshair();
            case PARTICLES -> buildParticles();
            case BADGES -> buildBadges();
            case PACKS -> buildPacks();
        }
        if (context() != null) {
            context().requestLayout();
        }
    }

    private CardGrid grid(int minCell, int maxColumns) {
        return new CardGrid(minCell, maxColumns, Theme.SPACE_4).rowHeight(CosmeticCard.HEIGHT);
    }

    private CosmeticCard card(CardGrid grid, UiNode thumb, String title, String caption, boolean selected,
                              Runnable onSelect) {
        CosmeticCard card = new CosmeticCard(thumb, title, caption, Lang.tr("vanta.cosmetics.selected"), selected,
                onSelect);
        card.setId("cosmetic." + section.id() + "." + title.toLowerCase(Locale.ROOT).replace(' ', '-'));
        cards.add(card);
        grid.add(card);
        return card;
    }

    // ---- sections ----------------------------------------------------------------------------------------------------

    private void buildThemes() {
        CosmeticsSelection selection = cosmetics.selection();
        List<UiThemeDefinition> themes = registry.themes();
        body.add(new SectionHeader(Lang.tr("vanta.cosmetics.themes"))
                .trailing(Plurals.count(themes.size(), "vanta.cosmetics.theme_count")));
        CardGrid grid = grid(128, 4);
        Theme current = theme();
        for (UiThemeDefinition definition : themes) {
            Theme preview = ThemeResolver.preview(definition, current);
            String caption = definition.builtIn() ? Lang.tr(definition.descriptionKey()) : packCaption(definition);
            card(grid, new CosmeticCard.Painted((c, ctx, r) -> Thumbnails.palette(c, r, preview)),
                    Lang.tr(definition.langKey()), caption, definition.id().equals(selection.themeId()),
                    () -> {
                        cosmetics.selectTheme(definition.id());
                        rebuild();
                    });
        }
        body.add(grid);
        body.add(note("vanta.cosmetics.free"));
    }

    private String packCaption(UiThemeDefinition definition) {
        for (CosmeticPack pack : registry.packs()) {
            if (pack.themes().contains(definition)) {
                return Lang.tr("vanta.cosmetics.from_pack", pack.name());
            }
        }
        return "";
    }

    private void buildBackgrounds() {
        MenuBackground selected = cosmetics.selection().menuBackground();
        List<MenuBackground> backgrounds = registry.backgrounds();
        body.add(new SectionHeader(Lang.tr("vanta.cosmetics.backgrounds"))
                .trailing(Plurals.count(backgrounds.size(), "vanta.cosmetics.count")));
        CardGrid grid = grid(128, 4);
        for (MenuBackground background : backgrounds) {
            card(grid, new CosmeticCard.Painted((c, ctx, r) -> Thumbnails.background(c, r, background, ctx.theme())),
                    Lang.tr(background.langKey()), Lang.tr(background.langKey() + ".description"),
                    background == selected, () -> {
                        cosmetics.selectBackground(background);
                        rebuild();
                    });
        }
        body.add(grid);
        body.add(note("vanta.cosmetics.backgrounds_note"));
    }

    private void buildHud() {
        HudTheme selected = cosmetics.selection().hudTheme();
        List<HudTheme> themes = registry.hudThemes();
        body.add(new SectionHeader(Lang.tr("vanta.cosmetics.hud_theme"))
                .trailing(Plurals.count(themes.size(), "vanta.cosmetics.count")));
        CardGrid grid = grid(128, 4);
        for (HudTheme hudTheme : themes) {
            card(grid, new CosmeticCard.Painted((c, ctx, r) -> Thumbnails.hudWidget(c, r, hudTheme, ctx.theme())),
                    Lang.tr(hudTheme.langKey()), Lang.tr(hudTheme.langKey() + ".description"), hudTheme == selected,
                    () -> {
                        cosmetics.selectHudTheme(hudTheme);
                        rebuild();
                    });
        }
        body.add(grid);
        body.add(note("vanta.cosmetics.hud_note"));
    }

    private void buildCrosshair() {
        List<CrosshairPreset> presets = CrosshairPresets.builtIns();
        Optional<CrosshairPreset> active = services().crosshair().activePreset();
        SectionHeader header = new SectionHeader(Lang.tr("vanta.cosmetics.crosshair_presets"))
                .trailing(active.isPresent() ? Plurals.count(presets.size(), "vanta.cosmetics.count")
                        : Lang.tr("vanta.cosmetics.crosshair_custom"));
        body.add(header);
        CardGrid grid = grid(110, 6);
        for (CrosshairPreset preset : presets) {
            card(grid, new CosmeticCard.Painted((c, ctx, r) -> Thumbnails.crosshair(c, r, preset.style())),
                    Lang.tr(preset.langKey()), Lang.tr(preset.descriptionKey()),
                    active.map(a -> a.id().equals(preset.id())).orElse(false), () -> applyCrosshair(preset));
        }
        body.add(grid);
        boolean editorAvailable = services().screens().isRegistered(ScreenId.CROSSHAIR);
        Button editor = Button.secondary(Lang.tr("vanta.cosmetics.open_crosshair_editor"),
                () -> openScreen(ScreenId.CROSSHAIR)).icon(Icons.CROSSHAIR).compact(true);
        editor.setId("cosmetics.open-crosshair");
        editor.setEnabled(editorAvailable);
        if (!editorAvailable) {
            editor.setTooltip(Lang.tr("vanta.cosmetics.crosshair_editor_unavailable"));
        }
        InfoBanner banner = new InfoBanner(InfoBanner.Tone.NEUTRAL, Lang.tr("vanta.cosmetics.crosshair_more"),
                Lang.tr("vanta.cosmetics.crosshair_more.body")).icon(Icons.CROSSHAIR).action(editor);
        body.add(banner);
    }

    /** Applies a crosshair preset to the live crosshair and records it in the cosmetic selection. */
    public void applyCrosshair(CrosshairPreset preset) {
        services().crosshair().applyPreset(preset);
        services().settings().set(VantaSettings.COSMETICS_CROSSHAIR_PRESET, preset.id());
        rebuild();
    }

    private void buildParticles() {
        MenuParticles selected = cosmetics.selection().menuParticles();
        List<MenuParticles> kinds = registry.particles();
        body.add(new SectionHeader(Lang.tr("vanta.cosmetics.particles"))
                .trailing(Plurals.count(kinds.size(), "vanta.cosmetics.count")));
        CardGrid grid = grid(128, 3);
        for (MenuParticles kind : kinds) {
            card(grid, new ParticlePreview(kind, PARTICLE_SEED + kind.ordinal()), Lang.tr(kind.langKey()),
                    Lang.tr(kind.langKey() + ".description"), kind == selected, () -> {
                        cosmetics.selectParticles(kind);
                        rebuild();
                    });
        }
        body.add(grid);
        body.add(note("vanta.cosmetics.particles_note"));
    }

    private void buildBadges() {
        Badge selected = cosmetics.selection().badge();
        List<Badge> badges = registry.badges();
        body.add(new SectionHeader(Lang.tr("vanta.cosmetics.badges"))
                .trailing(Plurals.count(badges.size(), "vanta.cosmetics.count")));
        body.add(new InfoBanner(InfoBanner.Tone.INFO, Lang.tr("vanta.cosmetics.visual_only_title"),
                Lang.tr("vanta.cosmetics.badges_note")));
        CardGrid grid = grid(110, 4);
        for (Badge badge : badges) {
            CosmeticCard card = new CosmeticCard(
                    new CosmeticCard.Painted((c, ctx, r) -> Thumbnails.badge(c, r, badge, ctx.theme())),
                    Lang.tr(badge.langKey()), Lang.tr(badge.langKey() + ".description"),
                    Lang.tr("vanta.cosmetics.equipped"), badge == selected, () -> {
                        cosmetics.selectBadge(badge);
                        rebuild();
                    });
            card.setId("cosmetic.badges." + badge.id());
            cards.add(card);
            grid.add(card);
        }
        body.add(grid);
    }

    private void buildPacks() {
        List<CosmeticPack> packs = registry.packs();
        body.add(new SectionHeader(Lang.tr("vanta.cosmetics.packs"))
                .trailing(Plurals.count(packs.size(), "vanta.cosmetics.pack_count")));
        String folder = displayPath(services(), services().paths().cosmeticPacksDir());
        Button refresh = Button.secondary(Lang.tr("vanta.cosmetics.refresh"), this::reloadPacks).icon(Icons.RESET)
                .compact(true);
        refresh.setId("cosmetics.refresh-packs");
        Button open = Button.secondary(Lang.tr("vanta.cosmetics.open_folder"),
                () -> services().game().openUrl(services().paths().cosmeticPacksDir().toUri().toString()))
                .icon(Icons.FOLDER).compact(true);
        body.add(new InfoBanner(InfoBanner.Tone.NEUTRAL, Lang.tr("vanta.cosmetics.packs_title"),
                Lang.tr("vanta.cosmetics.packs_hint", folder)).icon(Icons.PACKAGE).action(open).action(refresh));
        List<String> errors = cosmetics.loadErrors();
        if (!errors.isEmpty()) {
            body.add(new InfoBanner(InfoBanner.Tone.DANGER, Lang.tr("vanta.cosmetics.pack_errors", errors.size()),
                    String.join("  ·  ", errors)));
        }
        if (packs.isEmpty()) {
            body.add(new EmptyState(Icons.PACKAGE, Lang.tr("vanta.cosmetics.no_packs_title"),
                    Lang.tr("vanta.cosmetics.no_packs")));
            return;
        }
        CardGrid grid = grid(150, 3);
        for (CosmeticPack pack : packs) {
            String themeCount = Plurals.count(pack.themes().size(), "vanta.cosmetics.pack_themes");
            String caption = pack.author().isEmpty() ? themeCount
                    : Lang.tr("vanta.cosmetics.by_author", pack.author()) + " · " + themeCount;
            UiThemeDefinition first = pack.themes().get(0);
            Theme preview = ThemeResolver.preview(first, theme());
            CosmeticCard card = new CosmeticCard(new CosmeticCard.Painted((c, ctx, r) -> Thumbnails.palette(c, r, preview)),
                    pack.name(), caption, Lang.tr("vanta.cosmetics.selected"), false, () -> {
                        section = Section.THEMES;
                        showSection(Section.THEMES);
                    });
            card.setTooltip(Lang.tr("vanta.cosmetics.pack_tooltip"));
            card.setId("cosmetic.packs." + pack.id());
            cards.add(card);
            grid.add(card);
        }
        body.add(grid);
    }

    /** Re-reads the cosmetic packs folder. */
    public void reloadPacks() {
        cosmetics.load();
        rebuild();
    }

    private UiNode note(String key) {
        return new InfoBanner(InfoBanner.Tone.NEUTRAL, "", Lang.tr(key)).icon(Icons.INFO);
    }

    @Override
    protected void onThemeChanged(Theme theme) {
        scroll.edgeFade(theme.bgBase());
        rebuild();
    }
}
