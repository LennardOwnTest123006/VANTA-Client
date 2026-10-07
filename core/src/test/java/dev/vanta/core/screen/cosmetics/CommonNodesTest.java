package dev.vanta.core.screen.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.cosmetics.Badge;
import dev.vanta.core.cosmetics.HudTheme;
import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiTestSupport;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.widget.Button;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommonNodesTest {
    private final UiTestSupport ui = new UiTestSupport();

    private static final class Box extends UiNode {
        Box(int w, int h) {
            size(w, h);
        }
    }

    @Test
    void cardGridFlowsByAvailableWidthAndRequestsLayoutWhenColumnsChange() {
        CardGrid grid = new CardGrid(100, 3, 10);
        for (int i = 0; i < 5; i++) {
            grid.add(new Box(50, 40));
        }
        ui.place(grid, 0, 0, 320, 300);
        assertEquals(3, grid.columns());
        assertEquals(new Rect(0, 0, 100, 40), grid.children().get(0).bounds());
        assertEquals(new Rect(110, 0, 100, 40), grid.children().get(1).bounds());
        assertEquals(new Rect(0, 50, 100, 40), grid.children().get(3).bounds());
        Size measured = grid.preferredSize(ui.ctx());
        assertEquals(90, measured.h());
        ui.place(grid, 0, 0, 150, 300);
        assertEquals(1, grid.columns());
        assertEquals(new Rect(0, 40 * 4 + 40, 150, 40), grid.children().get(4).bounds());
        grid.rowHeight(30);
        ui.place(grid, 0, 0, 320, 300);
        assertEquals(30, grid.children().get(0).bounds().h());
    }

    @Test
    void emptyStateMeasuresWithHintAndAction() {
        EmptyState empty = new EmptyState(Icons.INFO, "Nothing", "A longer hint that wraps onto several lines when narrow");
        ui.place(empty, 0, 0, 200, 150);
        int withoutAction = empty.preferredSize(ui.ctx()).h();
        Button action = Button.primary("Do it", () -> { });
        empty.action(action);
        ui.place(empty, 0, 0, 200, 150);
        assertTrue(empty.preferredSize(ui.ctx()).h() > withoutAction);
        assertTrue(action.bounds().w() > 0);
        assertEquals("Nothing", empty.title());
        TestCanvas canvas = ui.render(empty);
        assertTrue(canvas.ops().stream().anyMatch(op -> op instanceof TestCanvas.Text t && t.text().equals("Nothing")));
        empty.action(null);
        assertEquals(1, empty.children().size() == 0 ? 1 : 0);
    }

    @Test
    void sectionHeaderDrawsTextHairlineAndTrailing() {
        SectionHeader header = new SectionHeader("Themes").trailing("5 themes");
        ui.place(header, 0, 0, 200, SectionHeader.HEIGHT);
        TestCanvas canvas = ui.render(header);
        List<TestCanvas.Text> texts = canvas.ops().stream().filter(op -> op instanceof TestCanvas.Text)
                .map(op -> (TestCanvas.Text) op).toList();
        assertEquals(2, texts.size());
        assertEquals("Themes", texts.get(0).text());
        assertEquals("5 themes", texts.get(1).text());
        assertTrue(canvas.ops().stream().anyMatch(op -> op instanceof TestCanvas.Fill f && f.h() == 1 && f.w() > 20));
    }

    @Test
    void infoBannerPlacesActionsRightOnWideRowsAndBelowOnNarrowOnes() {
        InfoBanner banner = new InfoBanner(InfoBanner.Tone.WARNING, "Title", "Body text that is long enough to wrap.");
        Button action = Button.primary("Act", () -> { });
        banner.action(action);
        banner.setBounds(0, 0, 400, 0);
        ui.place(banner, 0, 0, 400, banner.preferredSize(ui.ctx()).h());
        assertTrue(action.bounds().right() <= 400 && action.bounds().x() > 200);
        int wideH = banner.bounds().h();
        banner.setBounds(0, 0, 200, 0);
        ui.place(banner, 0, 0, 200, banner.preferredSize(ui.ctx()).h());
        assertTrue(banner.bounds().h() > wideH);
        assertTrue(action.bounds().y() > banner.bounds().y() + 10);
        assertEquals(ui.theme.warning(), banner.accent(ui.theme));
        assertEquals(ui.theme.textSecondary(), banner.tone(InfoBanner.Tone.NEUTRAL).accent(ui.theme));
    }

    /**
     * A fresh banner in a column measures at the width the column offers, so in the first pass it is already as
     * tall as its wrapped body plus the stacked action, also below the 240 px it used to assume as a minimum.
     */
    @Test
    void infoBannerTakesItsRealHeightInTheFirstPassOfANarrowColumn() {
        Column col = new Column(4);
        InfoBanner banner = col.add(new InfoBanner(InfoBanner.Tone.INFO, "Title", "word ".repeat(40).trim()));
        Button action = Button.primary("Act", () -> { });
        banner.action(action);
        Button next = col.add(new Button("Next", () -> { }));
        assertEquals(240, banner.preferredSize(ui.ctx()).w(), "nothing known yet: the old 240 px assumption");
        ui.place(col, 0, 0, 140, 400);
        // 106 px of text (four 20 px words per line, ten lines), the title, the stacked action and the padding.
        int textBottom = banner.bounds().y() + Theme.SPACE_4 + 9 + Theme.SPACE_1 + 10 * 9;
        assertEquals(new Rect(0, 0, 140, 16 + 101 + Theme.SPACE_3 + 20), banner.bounds());
        assertTrue(action.bounds().y() >= textBottom, "the stacked action sits below the body text");
        assertEquals(banner.bounds().bottom() + 4, next.bounds().y());
        ui.place(col, 0, 0, 140, 400);
        assertEquals(new Rect(0, 0, 140, 143), banner.bounds(), "a second pass changes nothing");
        assertTrue(banner.preferredSize(ui.ctx(), 400).h() < 143, "a wider offer wraps to fewer lines");
    }

    @Test
    void cosmeticCardSelectsOnClickAndKeyboardButNotWhenAlreadySelected() {
        int[] selected = {0};
        CosmeticCard card = new CosmeticCard(new CosmeticCard.Painted((c, ctx, r) -> c.fill(r.x(), r.y(), r.w(), r.h(), 0xFF112233)),
                "Clean", "caption", "Selected", false, () -> selected[0]++);
        ui.place(card, 0, 0, 140, CosmeticCard.HEIGHT);
        ui.click(card);
        assertEquals(1, selected[0]);
        assertTrue(card.isFocused());
        ui.key(dev.vanta.core.ui.Keys.ENTER);
        assertEquals(2, selected[0]);
        CosmeticCard already = new CosmeticCard(new UiNode() { }, "Glass", "", "Selected", true, () -> selected[0]++);
        ui.place(already, 0, 0, 140, CosmeticCard.HEIGHT);
        ui.click(already);
        assertEquals(2, selected[0]);
        assertTrue(already.isSelected());
        TestCanvas canvas = ui.render(already);
        assertTrue(canvas.ops().stream().anyMatch(op -> op instanceof TestCanvas.Text t && t.text().equals("Selected")));
    }

    @Test
    void thumbnailsDrawInsideTheirRectangles() {
        Rect r = new Rect(10, 10, 100, 46);
        for (MenuBackground background : MenuBackground.values()) {
            TestCanvas canvas = new TestCanvas(200, 100);
            Thumbnails.background(canvas, r, background, Theme.DEFAULT);
            assertTrue(canvas.ops().size() > 10, background.name());
        }
        for (HudTheme theme : HudTheme.values()) {
            TestCanvas canvas = new TestCanvas(200, 100);
            Thumbnails.hudWidget(canvas, r, theme, Theme.DEFAULT);
            assertTrue(canvas.ops().stream().anyMatch(op -> op instanceof TestCanvas.Text t && t.text().equals("120")), theme.name());
        }
        for (var preset : CrosshairPresets.builtIns()) {
            TestCanvas canvas = new TestCanvas(200, 100);
            Thumbnails.crosshair(canvas, r, preset.style());
            assertTrue(canvas.ops().stream().filter(op -> op instanceof TestCanvas.Fill).count() > 5, preset.id());
        }
        for (Badge badge : Badge.values()) {
            TestCanvas canvas = new TestCanvas(200, 100);
            Thumbnails.badge(canvas, r, badge, Theme.DEFAULT);
            assertFalse(canvas.ops().isEmpty(), badge.name());
        }
        TestCanvas palette = new TestCanvas(200, 100);
        Thumbnails.palette(palette, r, Theme.DEFAULT);
        assertTrue(palette.ops().size() > 10);
    }

    @Test
    void particlePreviewWarmsUpAndAdvancesPerTick() {
        ParticlePreview preview = new ParticlePreview(MenuParticles.EMBERS, 1L);
        assertEquals(0, preview.system().count());
        ui.place(preview, 0, 0, 120, 46);
        int warmed = preview.system().count();
        assertTrue(warmed > 0, "warm-up should spawn particles");
        preview.onTick(ui.ctx());
        assertTrue(preview.system().count() > 0);
        TestCanvas canvas = ui.render(preview);
        assertTrue(canvas.ops().size() > warmed);
        ParticlePreview none = new ParticlePreview(MenuParticles.NONE, 1L);
        ui.place(none, 0, 0, 120, 46);
        assertEquals(0, none.system().count());
    }
}
