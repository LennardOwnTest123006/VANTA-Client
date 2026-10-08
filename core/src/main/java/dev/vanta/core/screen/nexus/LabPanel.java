package dev.vanta.core.screen.nexus;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.lab.LabFeature;
import dev.vanta.core.lab.LabSettings;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.CanvasText;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.widget.Card;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.Toggle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Vanta Lab section: one row per {@link LabFeature} with its title, the honest one-line description and a
 * toggle bound to {@link LabSettings}. Nothing else.
 */
public final class LabPanel extends NexusPanel {
    private final LabSettings lab;
    private final Map<LabFeature, LabRow> rows = new EnumMap<>(LabFeature.class);
    private final Runnable unsubscribe;

    public LabPanel(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, NexusSection.LAB);
        this.lab = services.lab();
        content().add(new Label(Lang.tr("vanta.nexus.lab.intro"), Label.Variant.MUTED).wrap(true));
        Card card = new Card(Lang.tr("vanta.nexus.section.lab"));
        card.setId("nexus.lab.card");
        card.body().gap(Theme.SPACE_1);
        for (LabFeature feature : LabFeature.values()) {
            LabRow row = new LabRow(feature);
            rows.put(feature, row);
            card.add(row);
        }
        content().add(card);
        unsubscribe = lab.onAnyChange((feature, enabled) -> {
            LabRow row = rows.get(feature);
            if (row != null) {
                row.toggle.setOn(enabled);
            }
        });
    }

    /** The toggle of a feature. */
    public Toggle toggle(LabFeature feature) {
        return rows.get(feature).toggle;
    }

    /** The rows in enum order. */
    public List<LabRow> rows() {
        return new ArrayList<>(rows.values());
    }

    @Override
    public void dispose() {
        unsubscribe.run();
    }

    /** Title, wrapped description and the toggle at the right. */
    public final class LabRow extends UiNode {
        private static final int PAD_Y = Theme.SPACE_3;
        private final LabFeature feature;
        private final Toggle toggle;
        private List<String> lines = List.of();

        LabRow(LabFeature feature) {
            this.feature = feature;
            this.toggle = new Toggle(lab.isEnabled(feature), on -> lab.set(feature, on));
            toggle.setId("nexus.lab.toggle." + feature.id());
            toggle.setTooltip(Lang.tr(feature.descriptionKey()));
            add(toggle);
            setId("nexus.lab.row." + feature.id());
        }

        public LabFeature feature() {
            return feature;
        }

        public Toggle toggle() {
            return toggle;
        }

        private int textWidth(int width) {
            return Math.max(40, width - Toggle.TRACK_W - Theme.SPACE_4 * 2);
        }

        private int heightFor(UiContext ctx, int width) {
            List<String> wrapped = CanvasText.wrap(Lang.tr(feature.descriptionKey()), textWidth(width), FontKind.UI,
                    ctx.metrics());
            return PAD_Y * 2 + ctx.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_1 + wrapped.size() * ctx.lineHeight(FontKind.UI);
        }

        @Override
        protected Size measure(UiContext ctx) {
            return measure(ctx, -1);
        }

        @Override
        protected Size measure(UiContext ctx, int availableWidth) {
            int w = explicitWidth() > 0 ? explicitWidth() : availableWidth > 0 ? availableWidth
                    : Math.max(bounds().w(), 240);
            return new Size(w, Math.max(heightFor(ctx, w), Toggle.TRACK_H + PAD_Y * 2));
        }

        @Override
        public void layout(UiContext ctx) {
            Rect b = bounds();
            lines = CanvasText.wrap(Lang.tr(feature.descriptionKey()), textWidth(b.w()), FontKind.UI, ctx.metrics());
            toggle.setBounds(b.right() - Theme.SPACE_2 - Toggle.TRACK_W, b.y() + PAD_Y, Toggle.TRACK_W,
                    Math.max(Toggle.TRACK_H, ctx.lineHeight(FontKind.UI_BOLD)));
            toggle.layout(ctx);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            int x = b.x() + Theme.SPACE_2;
            int y = b.y() + PAD_Y;
            canvas.text(canvas.textClipped(Lang.tr(feature.langKey()), textWidth(b.w()), FontKind.UI_BOLD), x, y,
                    theme.textPrimary(), FontKind.UI_BOLD, false);
            y += canvas.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_1;
            for (String line : lines) {
                canvas.text(line, x, y, theme.textMuted(), FontKind.UI, false);
                y += canvas.lineHeight(FontKind.UI);
            }
            canvas.fill(b.x(), b.bottom() - 1, b.w(), 1, dev.vanta.core.ui.Colors.withAlpha(theme.borderSubtle(), 0.7f));
        }
    }
}
