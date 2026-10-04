package dev.vanta.core.screen.about;

import dev.vanta.core.VantaVersion;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.perf.SystemInfo;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaLinks;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.Brand;
import dev.vanta.core.screen.common.InfoBanner;
import dev.vanta.core.screen.common.LabelValueRow;
import dev.vanta.core.screen.common.LinkRow;
import dev.vanta.core.screen.common.ResponsiveGrid;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.VantaUiScreen;
import dev.vanta.core.search.ActionEntry;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.CanvasText;
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
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Card;
import dev.vanta.core.ui.widget.Label;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * About VANTA: brand block, every version that matters (client, Minecraft, Fabric Loader, Fabric API, Java, OS),
 * links opened through {@code GameBridge.openUrl} (website only when the host configured one), the licenses and
 * the fair-play statement. "Copy version info" puts the version block on the clipboard for support requests.
 */
public final class AboutScreen extends VantaUiScreen {
    private final SystemInfo system = SystemInfo.current();
    private final List<LinkRow> links = new ArrayList<>();
    private Button copyButton;

    public AboutScreen(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, ScreenId.ABOUT);
    }

    /** Link rows shown (tests). */
    public List<LinkRow> links() {
        return List.copyOf(links);
    }

    public Button copyButton() {
        return copyButton;
    }

    /** The plain-text version block copied to the clipboard. */
    public String versionText() {
        StringBuilder sb = new StringBuilder();
        sb.append(VantaVersion.BRAND).append(' ').append(services().game().clientVersion()).append('\n');
        sb.append("Minecraft ").append(services().game().minecraftVersion()).append('\n');
        sb.append("Fabric Loader ").append(services().game().fabricLoaderVersion()).append('\n');
        sb.append("Fabric API ").append(VantaVersion.FABRIC_API).append('\n');
        sb.append(system.javaLabel()).append('\n');
        sb.append(system.machineLabel());
        return sb.toString();
    }

    @Override
    protected UiNode build(UiContext ctx) {
        VantaShell shell = new VantaShell(Lang.tr("vanta.about.title"), this::close);
        shell.backTooltip(Lang.tr("vanta.common.back"));
        Column content = new Column(Theme.SPACE_5);
        content.add(new Hero());

        ResponsiveGrid grid = new ResponsiveGrid(230, 2, Theme.SPACE_4);
        grid.add(versionsCard());
        grid.add(linksCard());
        content.add(grid);
        content.add(licensesCard());
        content.add(new InfoBanner(InfoBanner.Tone.SUCCESS, Lang.tr("vanta.about.fair_play.title"),
                Lang.tr("vanta.about.fair_play")).icon(Icons.SUCCESS));

        ScrollPanel scroll = new ScrollPanel(content).edgeFade(Colors.withAlpha(ctx.theme().bgBase(), 0.92f));
        shell.content(scroll);
        return shell;
    }

    private Card versionsCard() {
        Card card = new Card(Lang.tr("vanta.about.versions"));
        card.setId("about.versions");
        card.body().gap(Theme.SPACE_2);
        card.add(new LabelValueRow(VantaVersion.BRAND, services().game().clientVersion()));
        card.add(new LabelValueRow(Lang.tr("vanta.about.minecraft"), services().game().minecraftVersion()));
        card.add(new LabelValueRow(Lang.tr("vanta.about.fabric_loader"), services().game().fabricLoaderVersion()));
        card.add(new LabelValueRow(Lang.tr("vanta.about.fabric_api"), VantaVersion.FABRIC_API));
        card.add(new LabelValueRow(Lang.tr("vanta.perf.java"), system.javaVersion() + " · " + system.javaVendor()));
        card.add(new LabelValueRow(Lang.tr("vanta.perf.os"), system.osName() + " " + system.osVersion() + " · "
                + system.arch()));
        copyButton = Button.secondary(Lang.tr("vanta.about.copy_versions"), this::copyVersions).compact(true);
        copyButton.icon(Icons.COPY);
        copyButton.setId("about.copy");
        card.add(copyButton);
        return card;
    }

    private Card linksCard() {
        Card card = new Card(Lang.tr("vanta.about.links"));
        card.setId("about.links");
        card.body().gap(0);
        Optional<String> website = services().websiteUrl();
        website.ifPresent(url -> link(card, Icons.EXTERNAL_LINK, "vanta.about.website", url, url));
        link(card, Icons.LIST, "vanta.about.documentation", VantaLinks.DOCUMENTATION, VantaLinks.DOCUMENTATION);
        link(card, Icons.GRID, "vanta.about.repository", VantaLinks.REPOSITORY, VantaLinks.REPOSITORY);
        link(card, Icons.WARNING, "vanta.about.issues", VantaLinks.ISSUES, VantaLinks.ISSUES);
        LinkRow config = new LinkRow(Icons.FOLDER, Lang.tr("vanta.action.open_config_folder"),
                services().paths().root().toString(), () -> services().runAction(ActionEntry.OPEN_CONFIG_FOLDER));
        config.setId("about.link.config");
        card.add(config);
        links.add(config);
        return card;
    }

    private void link(Card card, Icons icon, String key, String url, String description) {
        LinkRow row = new LinkRow(icon, Lang.tr(key), description, () -> services().game().openUrl(url)).external(true);
        row.setId("about.link." + key.substring(key.lastIndexOf('.') + 1));
        card.add(row);
        links.add(row);
    }

    private Card licensesCard() {
        Card card = new Card(Lang.tr("vanta.about.licenses"));
        card.setId("about.licenses");
        card.body().gap(Theme.SPACE_3);
        card.add(new Label(Lang.tr("vanta.about.license"), Label.Variant.CAPTION).wrap(true));
        card.add(new Label(Lang.tr("vanta.about.fonts"), Label.Variant.CAPTION).wrap(true));
        card.add(new Label(Lang.tr("vanta.about.not_affiliated"), Label.Variant.MUTED).wrap(true));
        return card;
    }

    private void copyVersions() {
        String text = versionText();
        context().host().setClipboard(text);
        services().clipboard().ifPresent(c -> c.set(text));
        services().notifications().post(NotificationKind.SUCCESS, Lang.tr("vanta.common.copied"),
                Lang.tr("vanta.about.version_line", services().game().clientVersion(),
                        services().game().minecraftVersion(), services().game().fabricLoaderVersion()));
    }

    /** Logo, wordmark, version line and the one-paragraph description. */
    private final class Hero extends UiNode {
        private static final int LOGO = 44;
        private static final int WORD_W = 96;
        private List<String> lines = List.of();

        private int textLeft() {
            return LOGO + Theme.SPACE_6;
        }

        @Override
        protected Size measure(UiContext ctx) {
            int w = explicitWidth() > 0 ? explicitWidth() : Math.max(bounds().w(), 240);
            lines = CanvasText.wrap(Lang.tr("vanta.about.description"), Math.max(40, w - textLeft()), FontKind.UI,
                    ctx.metrics());
            int textH = Brand.wordmarkTextHeight(Math.min(w - textLeft(), WORD_W)) + Theme.SPACE_3
                    + ctx.lineHeight(FontKind.UI) + Theme.SPACE_3 + lines.size() * ctx.lineHeight(FontKind.UI);
            return new Size(w, Math.max(LOGO, textH) + Theme.SPACE_2);
        }

        @Override
        public void layout(UiContext ctx) {
            lines = CanvasText.wrap(Lang.tr("vanta.about.description"), Math.max(40, bounds().w() - textLeft()),
                    FontKind.UI, ctx.metrics());
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            Brand.drawLogo(canvas, theme, b.x(), b.y(), LOGO);
            int x = b.x() + textLeft();
            int wordmarkW = Math.min(b.w() - textLeft(), WORD_W);
            int wordmarkH = Brand.drawWordmarkText(canvas, x, b.y() + 1, wordmarkW);
            int y = b.y() + wordmarkH + Theme.SPACE_3;
            canvas.text(canvas.textClipped(services().versionLine(), b.right() - x, FontKind.UI), x, y,
                    theme.textSecondary(), FontKind.UI, false);
            y += canvas.lineHeight(FontKind.UI) + Theme.SPACE_3;
            for (String line : lines) {
                canvas.text(line, x, y, theme.textMuted(), FontKind.UI, false);
                y += canvas.lineHeight(FontKind.UI);
            }
        }
    }
}
