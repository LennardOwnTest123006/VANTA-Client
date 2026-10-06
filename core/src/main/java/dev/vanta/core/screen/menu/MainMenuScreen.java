package dev.vanta.core.screen.menu;

import dev.vanta.core.VantaVersion;
import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.bridge.VanillaScreen;
import dev.vanta.core.cosmetics.Badge;
import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.cosmetics.ParticleSystem;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.Brand;
import dev.vanta.core.screen.common.ConfirmDialogs;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.VantaUiScreen;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.AnimatedValue;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.IconButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The VANTA title screen: brand block (logo + wordmark + tagline), the vanilla entry points as a centred button
 * stack (two columns on windows where the single column would not fit), a quick-access icon row for the VANTA
 * screens, the version label in the bottom-left corner and the active profile in the bottom-right corner. The
 * background follows the {@code menu.background} setting and the particle layer the {@code menu.particles} setting.
 * <p>
 * PLAY continues the last singleplayer world when the game knows one and opens the world list otherwise.
 */
public final class MainMenuScreen extends VantaUiScreen {
    /** Total duration of the staggered button entrance. */
    public static final long ENTER_TOTAL_MS = 240L;
    /** Duration of one button's entrance. */
    public static final long ENTER_ITEM_MS = 120L;
    /** Seed of the particle simulation (deterministic frames in previews and tests). */
    public static final long PARTICLE_SEED = 0x5EEDL;

    private static final int BUTTON_W = 200;
    private static final int COMPACT_BUTTON_W = 118;
    private static final int BUTTON_GAP = Theme.SPACE_3;
    private static final int CORNER_PAD = Theme.SPACE_4;
    private static final int QUICK_H = 22;
    /** Space kept above and below the stack when deciding whether an arrangement fits. */
    private static final int FIT_MARGIN = Theme.SPACE_5;
    /** Smallest margin the stack is pushed up to when nothing fits. */
    private static final int MIN_MARGIN = Theme.SPACE_2;
    /** Number of buttons in the middle of the stack (between PLAY and Quit). */
    private static final int STACK_SIZE = 7;

    /**
     * One way of arranging the stack: the wide single column, the compact two-column layout, and two tighter
     * fallbacks for very short windows (large text at 427x240 leaves 209 logical pixels).
     */
    record Arrangement(boolean compact, int columns, int buttonW, int buttonGap, int gapBrand, int gapQuick) {
        static final Arrangement WIDE = new Arrangement(false, 1, BUTTON_W, BUTTON_GAP, Theme.SPACE_7, Theme.SPACE_6);
        static final Arrangement COMPACT = new Arrangement(true, 2, COMPACT_BUTTON_W, BUTTON_GAP, Theme.SPACE_5,
                Theme.SPACE_4);
        static final Arrangement TIGHT = new Arrangement(true, 2, COMPACT_BUTTON_W, Theme.SPACE_2, Theme.SPACE_2,
                Theme.SPACE_2);
        static final Arrangement TIGHT_3 = new Arrangement(true, 3, COMPACT_BUTTON_W, Theme.SPACE_2, Theme.SPACE_2,
                Theme.SPACE_2);
        /** Narrowest button of the three-column arrangement when it is squeezed into a narrow window. */
        static final int TIGHT_3_MIN_BUTTON_W = 96;

        int rows() {
            return (STACK_SIZE + columns - 1) / columns;
        }

        int stackWidth() {
            return columns * buttonW + (columns - 1) * buttonGap;
        }

        /** PLAY, the rows of the stack and Quit. */
        int stackHeight() {
            return Button.HEIGHT + buttonGap + rows() * (Button.HEIGHT + buttonGap) + Button.HEIGHT;
        }

        /** Brand block, stack and quick row with their gaps. */
        int totalHeight() {
            return BrandBlock.sizeFor(compact).h() + gapBrand + stackHeight() + gapQuick + QUICK_H;
        }

        boolean fits(int width, int height) {
            return stackWidth() + 2 * FIT_MARGIN <= width && totalHeight() + 2 * FIT_MARGIN <= height;
        }

        /**
         * The three-column arrangement with its buttons narrowed to the window when the full compact width would
         * not fit (a window that is both short and narrow, such as 400x225 with large text); the unnarrowed one
         * when even {@link #TIGHT_3_MIN_BUTTON_W} does not fit, which the width check then rejects.
         */
        static Arrangement tight3For(int width) {
            int buttonW = Math.min(COMPACT_BUTTON_W, (width - 2 * FIT_MARGIN - 2 * Theme.SPACE_2) / 3);
            return buttonW >= TIGHT_3_MIN_BUTTON_W
                    ? new Arrangement(true, 3, buttonW, Theme.SPACE_2, Theme.SPACE_2, Theme.SPACE_2) : TIGHT_3;
        }

        /**
         * The first arrangement whose measured height and width fit the window; when none fits vertically, the
         * shortest one that fits horizontally.
         */
        static Arrangement forSize(int width, int height) {
            Arrangement fallback = null;
            for (Arrangement a : List.of(WIDE, COMPACT, TIGHT, tight3For(width))) {
                if (a.stackWidth() + 2 * FIT_MARGIN > width) {
                    continue;
                }
                if (a.fits(width, height)) {
                    return a;
                }
                if (fallback == null || a.totalHeight() < fallback.totalHeight()) {
                    fallback = a;
                }
            }
            return fallback == null ? TIGHT : fallback;
        }
    }

    private final GameBridge game;
    private final List<MenuButton> stack = new ArrayList<>();
    private final List<IconButton> quick = new ArrayList<>();
    private MenuButton play;
    private MenuButton quit;
    private BrandBlock brand;
    private ProfileChip profileChip;
    private MenuLayout layout;
    private ParticleSystem particles;
    private long lastParticleFrame = -1L;
    private long openedAt;

    public MainMenuScreen(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, ScreenId.MAIN_MENU);
        this.game = services.game();
    }

    // ---------------------------------------------------------------- screen contract

    @Override
    public boolean wantsVanillaPanorama() {
        return background() == MenuBackground.VANILLA_PANORAMA;
    }

    @Override
    public boolean wantsBlur() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The configured background. */
    public MenuBackground background() {
        return services().settings().get(VantaSettings.MENU_BACKGROUND);
    }

    /** The configured particle layer (NONE under reduced motion). */
    public MenuParticles particleKind() {
        return theme().reducedMotion() ? MenuParticles.NONE : services().settings().get(VantaSettings.MENU_PARTICLES);
    }

    /** Label of the PLAY button for the game state. */
    public static String playLabel(GameBridge game) {
        Optional<String> world = game.lastWorldName();
        return world.map(name -> Lang.tr("vanta.menu.continue_world", name)).orElse(Lang.tr("vanta.menu.play"));
    }

    /** The PLAY button. */
    public MenuButton playButton() {
        return play;
    }

    /** Whether the compact (two- or three-column) layout is active: the wide stack would not fit the window. */
    public boolean isCompact() {
        return Arrangement.forSize(width(), height()).compact();
    }

    /** Lines of the bottom-left version label. */
    public static List<String> versionLines() {
        List<String> lines = new ArrayList<>(List.of(VantaVersion.menuLabel()));
        lines.add("Fabric API " + VantaVersion.FABRIC_API);
        return lines;
    }

    // ---------------------------------------------------------------- build

    @Override
    protected UiNode build(UiContext ctx) {
        layout = new MenuLayout();
        brand = layout.add(new BrandBlock());

        play = layout.add(new MenuButton(playLabel(game), Button.Variant.PRIMARY, this::play));
        play.icon(Icons.PLAY);
        play.setId("menu.play");
        play.setTooltip(game.lastWorldName().isPresent() ? playLabel(game) : Lang.tr("vanta.menu.no_last_world"));

        stack.add(menuButton("menu.singleplayer", "vanta.menu.singleplayer",
                () -> navigator().openVanilla(VanillaScreen.SINGLEPLAYER)));
        stack.add(menuButton("menu.multiplayer", "vanta.menu.multiplayer",
                () -> navigator().openVanilla(VanillaScreen.MULTIPLAYER)));
        stack.add(menuButton("menu.options", "vanta.menu.options", () -> open(ScreenId.SETTINGS)));
        stack.add(menuButton("menu.language", "vanta.menu.language",
                () -> navigator().openVanilla(VanillaScreen.LANGUAGE)));
        stack.add(menuButton("menu.resourcepacks", "vanta.menu.resourcepacks", () -> open(ScreenId.RESOURCE_PACKS)));
        stack.add(menuButton("menu.mods", "vanta.menu.mods", () -> open(ScreenId.MODS)));
        stack.add(menuButton("menu.accessibility", "vanta.menu.accessibility", () -> open(ScreenId.ACCESSIBILITY)));
        quit = layout.add(new MenuButton(Lang.tr("vanta.menu.quit"), Button.Variant.SECONDARY,
                () -> ConfirmDialogs.quitGame(context(), game::quitGame)));
        quit.setId("menu.quit");

        quick.add(quickButton(Icons.PROFILE, "vanta.menu.profiles", ScreenId.PROFILES));
        quick.add(quickButton(Icons.GRID, "vanta.menu.hud_editor", ScreenId.HUD_EDITOR));
        quick.add(quickButton(Icons.CHART, "vanta.menu.statistics", ScreenId.STATISTICS));
        quick.add(quickButton(Icons.PALETTE, "vanta.menu.cosmetics", ScreenId.COSMETICS));
        quick.add(quickButton(Icons.SEARCH, "vanta.menu.search", ScreenId.SEARCH));
        quick.add(quickButton(Icons.INFO, "vanta.menu.about", ScreenId.ABOUT));

        profileChip = layout.add(new ProfileChip());
        particles = new ParticleSystem(particleKind(), PARTICLE_SEED);
        return layout;
    }

    private MenuButton menuButton(String id, String langKey, Runnable action) {
        MenuButton button = layout.add(new MenuButton(Lang.tr(langKey), Button.Variant.SECONDARY, action));
        button.setId(id);
        return button;
    }

    private IconButton quickButton(Icons icon, String langKey, ScreenId target) {
        IconButton button = layout.add(new IconButton(icon, () -> open(target)).sizes(22, 11).outlined(true));
        button.setTooltip(Lang.tr(langKey));
        button.setId("menu.quick." + target.id());
        return button;
    }

    private void open(ScreenId id) {
        navigator().openScreen(context(), id);
    }

    private void play() {
        if (game.lastWorldName().isPresent() && game.continueLastWorld()) {
            return;
        }
        navigator().openVanilla(VanillaScreen.SINGLEPLAYER);
    }

    @Override
    protected void onScreenInit() {
        openedAt = context().now();
        scheduleEntrance();
        MainMenuHooks.onMainMenuShown(this);
    }

    private void scheduleEntrance() {
        List<MenuButton> order = new ArrayList<>();
        order.add(play);
        order.addAll(stack);
        order.add(quit);
        long stagger = order.size() > 1 ? (ENTER_TOTAL_MS - ENTER_ITEM_MS) / (order.size() - 1) : 0L;
        for (int i = 0; i < order.size(); i++) {
            order.get(i).enterAt(openedAt + i * stagger, ENTER_ITEM_MS);
        }
    }

    // ---------------------------------------------------------------- rendering

    @Override
    protected void renderBackground(Canvas canvas, UiContext ctx) {
        int w = canvas.width();
        int h = canvas.height();
        MenuBackgrounds.paint(canvas, ctx.theme(), background(), w, h);
        MenuParticles kind = particleKind();
        particles.setKind(kind);
        if (kind != MenuParticles.NONE) {
            long now = ctx.now();
            double dt = lastParticleFrame < 0L ? 16.0 : now - lastParticleFrame;
            lastParticleFrame = now;
            particles.update(dt, w, h);
            MenuBackgrounds.paintParticles(canvas, particles.particles());
        }
    }

    /** Live particle count (tests). */
    public int particleCount() {
        return particles.count();
    }

    // ---------------------------------------------------------------- layout node

    /** Positions the brand block, the button stack, the quick row and the corner widgets. */
    private final class MenuLayout extends UiNode {
        @Override
        public void layout(UiContext ctx) {
            Rect b = bounds();
            Arrangement a = Arrangement.forSize(b.w(), b.h());
            boolean compact = a.compact();
            int buttonH = Button.HEIGHT;
            int columns = a.columns();
            int buttonW = a.buttonW();
            int buttonGap = a.buttonGap();
            int stackW = a.stackWidth();
            int rows = a.rows();
            Size brandSize = BrandBlock.sizeFor(compact);
            int total = a.totalHeight();
            int top = Math.max(b.y() + FIT_MARGIN, b.y() + (b.h() - total) / 2 - b.h() / 24);
            // Never let the quick row run off the bottom: move the whole stack up to the minimum margin first.
            int lowestTop = b.bottom() - FIT_MARGIN - total;
            top = Math.min(top, Math.max(b.y() + MIN_MARGIN, lowestTop));
            int cx = b.centerX();

            brand.compact = compact;
            brand.setBounds(cx - brandSize.w() / 2, top, brandSize.w(), brandSize.h());
            brand.layout(ctx);
            int y = top + brandSize.h() + a.gapBrand();
            int left = cx - stackW / 2;
            play.setBounds(left, y, stackW, buttonH);
            play.layout(ctx);
            y += buttonH + buttonGap;
            for (int i = 0; i < stack.size(); i++) {
                int col = i % columns;
                int row = i / columns;
                MenuButton button = stack.get(i);
                button.setBounds(left + col * (buttonW + buttonGap), y + row * (buttonH + buttonGap), buttonW, buttonH);
                button.layout(ctx);
            }
            y += rows * (buttonH + buttonGap);
            quit.setBounds(left, y, stackW, buttonH);
            quit.layout(ctx);
            y += buttonH + a.gapQuick();
            int quickW = quick.size() * QUICK_H + (quick.size() - 1) * BUTTON_GAP;
            int qx = cx - quickW / 2;
            for (IconButton button : quick) {
                button.setBounds(qx, y, QUICK_H, QUICK_H);
                button.layout(ctx);
                qx += QUICK_H + BUTTON_GAP;
            }
            Size chip = profileChip.preferredSize(ctx);
            int chipY = b.bottom() - CORNER_PAD - chip.h();
            int chipW = chip.w();
            // The stack or the quick row reaches down into the corner on short windows: the chip gives way to
            // whatever shares its line (its name is clipped) so the two never share pixels, and disappears when
            // not even its icon fits (the quick row keeps a Profiles button).
            int obstacle = Integer.MIN_VALUE;
            if (chipY < quit.bounds().bottom()) {
                obstacle = left + stackW;
            }
            if (chipY < y + QUICK_H) {
                obstacle = Math.max(obstacle, qx - BUTTON_GAP);
            }
            if (obstacle != Integer.MIN_VALUE) {
                chipW = Math.min(chipW, Math.max(0, b.right() - CORNER_PAD - (obstacle + Theme.SPACE_3)));
            }
            profileChip.setVisible(chipW >= ProfileChip.MIN_W);
            profileChip.setBounds(b.right() - CORNER_PAD - chipW, chipY, chipW, chip.h());
            profileChip.layout(ctx);
        }

        /** Whether the version label with these lines would run into the quick row. */
        private boolean collidesWithQuickRow(Canvas canvas, Rect b, List<String> lines, int lh) {
            int widest = 0;
            for (int i = 0; i < lines.size(); i++) {
                widest = Math.max(widest, canvas.textWidth(lines.get(i), i == 0 ? FontKind.UI_BOLD : FontKind.UI));
            }
            Rect first = quick.get(0).bounds();
            return b.x() + CORNER_PAD + widest + Theme.SPACE_3 > first.x()
                    && b.bottom() - CORNER_PAD - lines.size() * lh < first.bottom();
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            if (!services().settings().get(VantaSettings.MENU_SHOW_VERSION_LABEL)) {
                return;
            }
            Theme theme = ctx.theme();
            Rect b = bounds();
            List<String> lines = versionLines();
            int lh = canvas.lineHeight(FontKind.UI);
            // On short windows the quick row comes down into the corner: drop to the version line alone first,
            // and leave the corner empty when even that would overlap (About lists the full set).
            if (collidesWithQuickRow(canvas, b, lines, lh)) {
                lines = lines.subList(0, 1);
                if (collidesWithQuickRow(canvas, b, lines, lh)) {
                    return;
                }
            }
            int y = b.bottom() - CORNER_PAD - lines.size() * lh;
            for (int i = 0; i < lines.size(); i++) {
                int color = i == 0 ? Colors.withAlpha(theme.textSecondary(), 0.95f) : theme.textMuted();
                canvas.text(lines.get(i), b.x() + CORNER_PAD, y, color, i == 0 ? FontKind.UI_BOLD : FontKind.UI, true);
                y += lh;
            }
        }
    }

    /** Logo tile, the "VANTA" word of the wordmark (the tile already shows the mark) and the tagline. */
    private final class BrandBlock extends UiNode {
        private static final int LOGO_WIDE = 56;
        private static final int LOGO_COMPACT = 28;
        private static final int WORD_WIDE = 150;

        /** Set by {@link MenuLayout} to the arrangement in use. */
        boolean compact;

        static Size sizeFor(boolean compact) {
            if (compact) {
                int wordW = LOGO_COMPACT * 3;
                return new Size(LOGO_COMPACT + Theme.SPACE_4 + wordW, LOGO_COMPACT);
            }
            return new Size(WORD_WIDE, LOGO_WIDE + Theme.SPACE_4 + Brand.wordmarkTextHeight(WORD_WIDE) + Theme.SPACE_2
                    + FontKind.UI.lineHeight());
        }

        @Override
        protected Size measure(UiContext ctx) {
            return sizeFor(compact);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            if (compact) {
                int logo = LOGO_COMPACT;
                Brand.drawLogo(canvas, theme, b.x(), b.y(), logo);
                int wordW = b.w() - logo - Theme.SPACE_4;
                int wordH = Brand.wordmarkTextHeight(wordW);
                Brand.drawWordmarkText(canvas, b.x() + logo + Theme.SPACE_4, b.y() + (logo - wordH) / 2, wordW);
                return;
            }
            int logo = LOGO_WIDE;
            int cx = b.centerX();
            Brand.drawLogo(canvas, theme, cx - logo / 2, b.y(), logo);
            int wy = b.y() + logo + Theme.SPACE_4;
            int wordH = Brand.drawWordmarkText(canvas, b.x(), wy, b.w());
            canvas.textCentered(Lang.tr("vanta.menu.tagline"), cx, wy + wordH + Theme.SPACE_2,
                    Colors.withAlpha(theme.textSecondary(), 0.9f), FontKind.UI, true);
        }
    }

    /** Bottom-right pill: cosmetic badge glyph + active profile name; opens the profiles screen. */
    private final class ProfileChip extends UiNode {
        private static final int H = 18;
        private static final int ICON = 9;
        /** Narrowest the chip is shown at: the icon with its padding. */
        private static final int MIN_W = Theme.SPACE_4 + ICON + Theme.SPACE_4;
        private AnimatedValue hoverT;

        ProfileChip() {
            setFocusable(true);
            setId("menu.profile");
            setTooltip(Lang.tr("vanta.menu.profiles"));
        }

        private String profileName() {
            return services().profiles().active().map(Profile::name).orElse(Lang.tr("vanta.common.default"));
        }

        private Badge badge() {
            return services().settings().get(VantaSettings.COSMETICS_BADGE);
        }

        @Override
        protected Size measure(UiContext ctx) {
            int w = Theme.SPACE_4 + ICON + Theme.SPACE_3 + ctx.textWidth(profileName(), FontKind.UI_BOLD) + Theme.SPACE_4;
            if (badge() != Badge.NONE) {
                w += ctx.textWidth(badge().glyph(), FontKind.MINECRAFT) + Theme.SPACE_3;
            }
            return new Size(Math.min(w, 160), H);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            if (hoverT == null) {
                hoverT = ctx.animator().value(0f);
            }
            hoverT.animateTo(isHovered(), Theme.MOTION_FAST);
            float hover = hoverT.get();
            Theme theme = ctx.theme();
            Rect b = bounds();
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG,
                    Colors.withAlpha(Colors.lerp(theme.surface2(), theme.surface3(), hover), 0.9f));
            canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG,
                    Colors.lerp(theme.borderSubtle(), theme.borderStrong(), hover));
            int x = b.x() + Theme.SPACE_4;
            Icons.PROFILE.draw(canvas, x, b.y() + (b.h() - ICON) / 2, ICON, theme.accentHover());
            x += ICON + Theme.SPACE_3;
            Badge badge = badge();
            if (badge != Badge.NONE) {
                int gw = canvas.textWidth(badge.glyph(), FontKind.MINECRAFT);
                canvas.text(badge.glyph(), x, b.y() + (b.h() - canvas.lineHeight(FontKind.MINECRAFT)) / 2 + 1,
                        badge.color(), FontKind.MINECRAFT, false);
                x += gw + Theme.SPACE_3;
            }
            int textW = Math.max(0, b.right() - Theme.SPACE_4 - x);
            canvas.text(canvas.textClipped(profileName(), textW, FontKind.UI_BOLD), x,
                    b.y() + (b.h() - canvas.lineHeight(FontKind.UI_BOLD)) / 2, theme.textPrimary(), FontKind.UI_BOLD,
                    false);
            if (isFocused()) {
                canvas.strokeRounded(b.x() - 2, b.y() - 2, b.w() + 4, b.h() + 4, Theme.RADIUS_LG,
                        Colors.withAlpha(theme.borderFocus(), 0.9f));
            }
        }

        @Override
        protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
            if (button != Keys.MOUSE_LEFT) {
                return false;
            }
            requestFocus(ctx);
            ctx.playClick();
            open(ScreenId.PROFILES);
            return true;
        }

        @Override
        public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
            if (Keys.isActivate(key)) {
                ctx.playClick();
                open(ScreenId.PROFILES);
                return true;
            }
            return false;
        }
    }
}
