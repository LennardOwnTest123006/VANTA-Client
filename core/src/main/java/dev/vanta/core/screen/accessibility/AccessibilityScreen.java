package dev.vanta.core.screen.accessibility;

import dev.vanta.core.bridge.VanillaScreen;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.LinkRow;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.SettingRow;
import dev.vanta.core.screen.common.ShortcutRow;
import dev.vanta.core.screen.common.VantaUiScreen;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingsListener;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.VantaShell;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.widget.Card;
import java.util.ArrayList;
import java.util.List;

/**
 * Accessibility preferences: interface scale, larger text, high contrast, reduced transparency, reduced motion,
 * the colour-blind palette (with a live preview of the status colours) and the keyboard navigation reference,
 * plus the vanilla accessibility options VANTA proxies and a link to Minecraft's own accessibility screen.
 * <p>
 * Every change re-themes this very screen immediately: {@link VantaUiScreen} listens to the
 * {@code AccessibilityService} and rebuilds the theme when scale, motion, contrast or palette change.
 */
public final class AccessibilityScreen extends VantaUiScreen {
    private final List<SettingRow> rows = new ArrayList<>();
    private final SettingsListener externalChange = change -> refreshRows();
    private VantaShell shell;
    private PalettePreview preview;

    public AccessibilityScreen(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, ScreenId.ACCESSIBILITY);
    }

    /** Rows shown (tests). */
    public List<SettingRow> rows() {
        return List.copyOf(rows);
    }

    /** The palette preview node (tests). */
    public PalettePreview palettePreview() {
        return preview;
    }

    @Override
    protected UiNode build(UiContext ctx) {
        shell = new VantaShell(Lang.tr("vanta.accessibility.title"), this::close);
        shell.backTooltip(Lang.tr("vanta.common.back"));
        Column content = new Column(Theme.SPACE_5);

        Card display = card("vanta.accessibility.section.display", "vanta.accessibility.live_hint");
        addRows(display, VantaSettings.GENERAL_UI_SCALE, VantaSettings.ACCESSIBILITY_LARGE_TEXT,
                VantaSettings.ACCESSIBILITY_HIGH_CONTRAST, VantaSettings.ACCESSIBILITY_REDUCED_TRANSPARENCY);
        content.add(display);

        Card motion = card("vanta.accessibility.section.motion", null);
        addRows(motion, VantaSettings.ACCESSIBILITY_REDUCED_MOTION);
        content.add(motion);

        Card colours = card("vanta.accessibility.section.colours", null);
        addRows(colours, VantaSettings.ACCESSIBILITY_COLOR_BLIND_PALETTE);
        preview = colours.add(new PalettePreview());
        preview.setId("accessibility.palettePreview");
        content.add(colours);

        Card keyboard = card("vanta.accessibility.section.keyboard", "vanta.accessibility.section.keyboard.description");
        keyboard.body().gap(Theme.SPACE_1);
        keyboard.add(new ShortcutRow(List.of("Tab", "Shift Tab"), Lang.tr("vanta.accessibility.keys.focus")));
        keyboard.add(new ShortcutRow(List.of("Enter", "Space"), Lang.tr("vanta.accessibility.keys.activate")));
        keyboard.add(new ShortcutRow(List.of("←", "→"), Lang.tr("vanta.accessibility.keys.adjust")));
        keyboard.add(new ShortcutRow(List.of("↑", "↓"), Lang.tr("vanta.accessibility.keys.move")));
        keyboard.add(new ShortcutRow(List.of("Esc"), Lang.tr("vanta.accessibility.keys.back")));
        keyboard.add(new ShortcutRow(List.of("Ctrl", "K"), Lang.tr("vanta.accessibility.keys.search")));
        keyboard.add(new ShortcutRow(List.of("Ctrl", "F"), Lang.tr("vanta.accessibility.keys.filter")));
        content.add(keyboard);

        Card vanilla = card("vanta.accessibility.section.vanilla", "vanta.accessibility.vanilla_hint");
        addRows(vanilla, VantaSettings.ACCESSIBILITY_VANILLA_HIGH_CONTRAST,
                VantaSettings.ACCESSIBILITY_TEXT_BACKGROUND_OPACITY, VantaSettings.ACCESSIBILITY_CHAT_OPACITY);
        vanilla.add(new LinkRow(Icons.ACCESSIBILITY, Lang.tr("vanta.accessibility.open_vanilla"),
                Lang.tr("vanta.setting.accessibility.openVanilla.description"),
                () -> navigator().openVanilla(VanillaScreen.ACCESSIBILITY)).external(true).id("accessibility.openVanilla"));
        content.add(vanilla);

        ScrollPanel scroll = new ScrollPanel(content).edgeFade(Colors.withAlpha(ctx.theme().bgBase(), 0.92f));
        shell.content(scroll);
        return shell;
    }

    private static Card card(String titleKey, String captionKey) {
        Card card = new Card(Lang.tr(titleKey));
        if (captionKey != null) {
            card.caption(Lang.tr(captionKey));
        }
        card.body().gap(0);
        return card;
    }

    private void addRows(Card card, Setting<?>... settings) {
        for (int i = 0; i < settings.length; i++) {
            SettingRow row = new SettingRow(settings[i], services().settings(),
                    id -> actions().dispatch(context(), id));
            row.last(i == settings.length - 1);
            rows.add(row);
            card.add(row);
        }
    }

    @Override
    protected void onScreenInit() {
        services().settings().addListener(externalChange);
    }

    @Override
    protected void onScreenClose() {
        services().settings().removeListener(externalChange);
    }

    /** Re-reads every row after an external change (reset, profile switch). */
    public void refreshRows() {
        for (SettingRow row : rows) {
            row.refresh();
        }
    }

    /** Three labelled swatches showing the status colours of the active theme (palette applied). */
    public static final class PalettePreview extends UiNode {
        private static final int H = 22;
        private static final int SWATCH = 10;

        @Override
        protected Size measure(UiContext ctx) {
            return new Size(220, H);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            int lh = canvas.lineHeight(FontKind.UI);
            int x = b.x() + Theme.SPACE_3;
            int y = b.y() + (b.h() - SWATCH) / 2;
            canvas.text(Lang.tr("vanta.accessibility.preview"), x, b.y() + (b.h() - lh) / 2, theme.textMuted(),
                    FontKind.UI, false);
            x += canvas.textWidth(Lang.tr("vanta.accessibility.preview"), FontKind.UI) + Theme.SPACE_5;
            x = swatch(canvas, theme, x, y, theme.success(), Lang.tr("vanta.accessibility.preview.success"));
            x = swatch(canvas, theme, x, y, theme.warning(), Lang.tr("vanta.accessibility.preview.warning"));
            swatch(canvas, theme, x, y, theme.danger(), Lang.tr("vanta.accessibility.preview.danger"));
        }

        private int swatch(Canvas canvas, Theme theme, int x, int y, int color, String label) {
            int lh = canvas.lineHeight(FontKind.UI);
            int w = SWATCH + Theme.SPACE_2 + canvas.textWidth(label, FontKind.UI_BOLD) + Theme.SPACE_4 * 2;
            canvas.fillRounded(x, y - 5, w, SWATCH + 10, Theme.RADIUS_LG, Colors.withAlpha(color, 0.14f));
            canvas.strokeRounded(x, y - 5, w, SWATCH + 10, Theme.RADIUS_LG, Colors.withAlpha(color, 0.45f));
            canvas.fillRounded(x + Theme.SPACE_4, y, SWATCH, SWATCH, Theme.RADIUS_LG, color);
            canvas.text(label, x + Theme.SPACE_4 + SWATCH + Theme.SPACE_2, y + (SWATCH - lh) / 2, color,
                    FontKind.UI_BOLD, false);
            return x + w + Theme.SPACE_3;
        }
    }
}
