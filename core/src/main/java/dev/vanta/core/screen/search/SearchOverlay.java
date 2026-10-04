package dev.vanta.core.screen.search;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.HighlightedText;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.VantaUiScreen;
import dev.vanta.core.search.ActionEntry;
import dev.vanta.core.search.GlobalSearchResult;
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
import dev.vanta.core.ui.VantaMark;
import dev.vanta.core.ui.widget.SearchField;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Global command palette (Ctrl+K): a large search field over the dimmed screen, results from
 * {@link dev.vanta.core.search.GlobalSearch} grouped into Settings / Screens / Actions / Keybinds with the matched
 * characters highlighted, and keyboard navigation (↑↓ move, Enter opens, Esc closes). With an empty query the
 * palette lists every VANTA screen so it doubles as a hub.
 * <p>
 * Enter on a setting opens the settings screen scrolled to that row (through the {@link ScreenNavigator}); a
 * screen action opens that screen; a command runs through the {@code ActionDispatcher} (destructive ones confirm
 * first) and closes the palette; a key binding opens the keybinds screen with that mapping highlighted.
 */
public final class SearchOverlay extends VantaUiScreen {
    /** Most results shown. */
    public static final int RESULT_LIMIT = 12;
    /** Palette width (clamped to the screen). */
    public static final int PALETTE_W = 420;
    /** Height of one result row. */
    public static final int ROW_H = 24;
    /** Height of a group header row. */
    public static final int HEADER_H = 16;
    /** Height of the search field. */
    public static final int FIELD_H = 26;
    private static final int PAD = Theme.SPACE_4;
    private static final int HINT_H = 14;

    /** Result group in display order. */
    public enum Group {
        SETTINGS("vanta.search.group.settings", Icons.GEAR),
        SCREENS("vanta.search.group.screens", Icons.GRID),
        ACTIONS("vanta.search.group.actions", Icons.PLAY),
        KEYBINDS("vanta.search.group.keybinds", Icons.KEYBOARD);

        private final String langKey;
        private final Icons icon;

        Group(String langKey, Icons icon) {
            this.langKey = langKey;
            this.icon = icon;
        }

        /** Translation key of the header. */
        public String langKey() {
            return langKey;
        }

        /** Icon drawn in the result tile. */
        public Icons icon() {
            return icon;
        }

        /** The group a result belongs to. */
        public static Group of(GlobalSearchResult result) {
            if (result instanceof GlobalSearchResult.SettingResult) {
                return SETTINGS;
            }
            if (result instanceof GlobalSearchResult.ActionResult a) {
                return a.action().screen().isPresent() ? SCREENS : ACTIONS;
            }
            return KEYBINDS;
        }
    }

    /** One line of the flattened list: a group header or a result. */
    public record Entry(Group group, GlobalSearchResult result) {
        /** True for header lines. */
        public boolean isHeader() {
            return result == null;
        }

        int height() {
            return isHeader() ? HEADER_H : ROW_H;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private Palette palette;
    private PaletteField field;
    private ResultList list;
    private int selected = -1;
    private String query = "";
    private GlobalSearchResult lastActivated;

    public SearchOverlay(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, ScreenId.SEARCH);
    }

    // ---------------------------------------------------------------- screen contract

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- accessors (tests)

    /** Current query. */
    public String query() {
        return query;
    }

    /** Flattened list (headers + results). */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** Results only, in display order. */
    public List<GlobalSearchResult> results() {
        List<GlobalSearchResult> out = new ArrayList<>();
        for (Entry e : entries) {
            if (!e.isHeader()) {
                out.add(e.result());
            }
        }
        return out;
    }

    /** Index into {@link #entries()} of the selected result, or -1. */
    public int selectedIndex() {
        return selected;
    }

    /** The selected result. */
    public Optional<GlobalSearchResult> selectedResult() {
        return selected >= 0 && selected < entries.size() ? Optional.ofNullable(entries.get(selected).result())
                : Optional.empty();
    }

    /** The result most recently opened by Enter or click. */
    public Optional<GlobalSearchResult> lastActivated() {
        return Optional.ofNullable(lastActivated);
    }

    public SearchField field() {
        return field;
    }

    // ---------------------------------------------------------------- build

    @Override
    protected UiNode build(UiContext ctx) {
        palette = new Palette();
        field = palette.add(new PaletteField(Lang.tr("vanta.search.placeholder")));
        field.setId("search.field");
        field.onChange(this::setQuery);
        field.onSubmit(text -> activateSelected());
        list = palette.add(new ResultList());
        list.setId("search.results");
        rebuild();
        return palette;
    }

    @Override
    protected void onScreenInit() {
        field.requestFocus(context());
    }

    // ---------------------------------------------------------------- model

    /** Updates the query and recomputes the results synchronously. */
    public void setQuery(String text) {
        String next = text == null ? "" : text.trim();
        if (next.equals(query) && !entries.isEmpty()) {
            return;
        }
        query = next;
        rebuild();
    }

    private void rebuild() {
        List<GlobalSearchResult> results = query.isEmpty() ? screenHub() : services().search().search(query, RESULT_LIMIT);
        entries.clear();
        entries.addAll(flatten(results));
        selected = firstResultIndex(0);
        if (list != null) {
            list.resetScroll();
        }
        invalidateLayout();
    }

    /** Every "open screen" action as a result (shown while the query is empty). */
    private static List<GlobalSearchResult> screenHub() {
        List<GlobalSearchResult> out = new ArrayList<>();
        for (ActionEntry action : ActionEntry.builtIns()) {
            if (action.screen().isPresent()) {
                out.add(new GlobalSearchResult.ActionResult(action, Lang.tr(action.langKey()),
                        Lang.tr(action.descriptionKey()), 0, List.of()));
            }
        }
        return out;
    }

    /** Groups results by {@link Group} (fixed order) and flattens them with header lines. */
    public static List<Entry> flatten(List<GlobalSearchResult> results) {
        Map<Group, List<GlobalSearchResult>> grouped = new EnumMap<>(Group.class);
        for (GlobalSearchResult r : results) {
            grouped.computeIfAbsent(Group.of(r), g -> new ArrayList<>()).add(r);
        }
        List<Entry> out = new ArrayList<>();
        for (Group g : Group.values()) {
            List<GlobalSearchResult> list = grouped.get(g);
            if (list == null || list.isEmpty()) {
                continue;
            }
            out.add(new Entry(g, null));
            for (GlobalSearchResult r : list) {
                out.add(new Entry(g, r));
            }
        }
        return out;
    }

    private int firstResultIndex(int from) {
        for (int i = Math.max(0, from); i < entries.size(); i++) {
            if (!entries.get(i).isHeader()) {
                return i;
            }
        }
        return -1;
    }

    private int lastResultIndex(int from) {
        for (int i = Math.min(entries.size() - 1, from); i >= 0; i--) {
            if (!entries.get(i).isHeader()) {
                return i;
            }
        }
        return -1;
    }

    /** Moves the selection by one result (skipping headers, clamped). */
    public void moveSelection(int direction) {
        if (entries.isEmpty()) {
            selected = -1;
            return;
        }
        int next = direction > 0 ? firstResultIndex(selected + 1) : lastResultIndex(selected - 1);
        if (next >= 0) {
            selected = next;
            if (list != null) {
                list.ensureVisible(selected);
            }
        }
    }

    /** Selects an entry index (headers are ignored). */
    public void select(int index) {
        if (index >= 0 && index < entries.size() && !entries.get(index).isHeader()) {
            selected = index;
        }
    }

    /** Opens the selected result. */
    public void activateSelected() {
        selectedResult().ifPresent(this::activate);
    }

    /** Opens a result: settings deep link, screen, command or key binding. */
    public void activate(GlobalSearchResult result) {
        UiContext ctx = context();
        lastActivated = result;
        ctx.playClick();
        if (result instanceof GlobalSearchResult.SettingResult s) {
            navigator().openSetting(ctx, s.setting());
        } else if (result instanceof GlobalSearchResult.ActionResult a) {
            if (a.action().screen().isPresent()) {
                navigator().openScreen(ctx, a.action().screen().get());
            } else {
                actions().dispatch(ctx, a.action().id());
                if (!ctx.popups().isOpen()) {
                    close();
                }
            }
        } else if (result instanceof GlobalSearchResult.KeybindResult k) {
            navigator().openKeybind(ctx, k.binding().id());
        }
    }

    // ---------------------------------------------------------------- nodes

    /** Search field whose Escape closes the palette instead of clearing the text first. */
    private static final class PaletteField extends SearchField {
        PaletteField(String placeholder) {
            super(placeholder);
        }

        @Override
        public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
            if (key == Keys.ESCAPE || key == Keys.UP || key == Keys.DOWN || key == Keys.PAGE_UP
                    || key == Keys.PAGE_DOWN) {
                return false;
            }
            return super.keyDown(ctx, key, scancode, mods);
        }
    }

    /** Positions the field and the list inside the centred panel and handles ↑↓. */
    private final class Palette extends UiNode {
        private Rect panel = Rect.EMPTY;

        /** The panel rectangle (tests). */
        Rect panelRect() {
            return panel;
        }

        @Override
        public void layout(UiContext ctx) {
            Rect b = bounds();
            int w = Math.min(PALETTE_W, b.w() - Theme.SPACE_6 * 2);
            int top = Math.max(Theme.SPACE_6, b.h() / 9);
            int available = b.h() - top - Theme.SPACE_6;
            int listH = Math.max(0, Math.min(list.contentHeight(), available - PAD * 3 - FIELD_H - HINT_H));
            int h = PAD + FIELD_H + PAD + listH + PAD + HINT_H;
            panel = new Rect(b.x() + (b.w() - w) / 2, b.y() + top, w, h);
            field.setBounds(panel.x() + PAD, panel.y() + PAD, w - PAD * 2, FIELD_H);
            field.layout(ctx);
            list.setBounds(panel.x() + PAD, field.bounds().bottom() + PAD, w - PAD * 2, listH);
            list.layout(ctx);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect p = panel;
            canvas.fillRounded(p.x() + 2, p.y() + 4, p.w(), p.h(), Theme.RADIUS_LG, Colors.withAlpha(theme.bgVoid(), 0.6f));
            canvas.fillRounded(p.x(), p.y(), p.w(), p.h(), Theme.RADIUS_LG,
                    Colors.withAlpha(theme.surface1(), Math.min(1f, theme.panelAlpha() + 0.14f)));
            canvas.strokeRounded(p.x(), p.y(), p.w(), p.h(), Theme.RADIUS_LG, theme.borderStrong());
            canvas.fillGradientH(p.x() + Theme.RADIUS_LG, p.y(), p.w() - Theme.RADIUS_LG * 2, 1,
                    Colors.withAlpha(theme.gradientStart(), 0.9f), Colors.withAlpha(theme.gradientEnd(), 0.9f));
            // Hint line at the bottom.
            String hint = Lang.tr("vanta.search.hint");
            int hy = p.bottom() - PAD - HINT_H + (HINT_H - canvas.lineHeight(FontKind.UI)) / 2;
            canvas.fill(p.x() + PAD, p.bottom() - PAD - HINT_H - Theme.SPACE_2, p.w() - PAD * 2, 1,
                    Colors.withAlpha(theme.borderSubtle(), 0.9f));
            VantaMark.draw(canvas, p.x() + PAD, hy, 9, theme);
            canvas.text(canvas.textClipped(hint, p.w() - PAD * 2 - 13, FontKind.UI), p.x() + PAD + 13, hy,
                    theme.textMuted(), FontKind.UI, false);
        }

        @Override
        public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
            switch (key) {
                case Keys.DOWN -> {
                    moveSelection(1);
                    return true;
                }
                case Keys.UP -> {
                    moveSelection(-1);
                    return true;
                }
                case Keys.PAGE_DOWN -> {
                    for (int i = 0; i < 5; i++) {
                        moveSelection(1);
                    }
                    return true;
                }
                case Keys.PAGE_UP -> {
                    for (int i = 0; i < 5; i++) {
                        moveSelection(-1);
                    }
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }
    }

    /** Draws the grouped entries with highlights, hover and selection; scrolls when they do not fit. */
    private final class ResultList extends UiNode {
        private static final int TILE = 16;
        private static final int ICON = 9;
        private float scroll;

        int contentHeight() {
            int h = 0;
            for (Entry e : entries) {
                h += e.height();
            }
            if (entries.isEmpty()) {
                h = ROW_H * 2;
            }
            return h;
        }

        void resetScroll() {
            scroll = 0f;
        }

        private int topOf(int index) {
            int y = 0;
            for (int i = 0; i < index && i < entries.size(); i++) {
                y += entries.get(i).height();
            }
            return y;
        }

        void ensureVisible(int index) {
            if (index < 0 || index >= entries.size() || bounds().h() <= 0) {
                return;
            }
            int top = topOf(index);
            int bottom = top + entries.get(index).height();
            if (top < scroll) {
                scroll = top;
            } else if (bottom > scroll + bounds().h()) {
                scroll = bottom - bounds().h();
            }
            clampScroll();
        }

        private void clampScroll() {
            float max = Math.max(0f, contentHeight() - bounds().h());
            scroll = Math.max(0f, Math.min(max, scroll));
        }

        /** Entry under an absolute y, or -1. */
        int entryAt(double y) {
            Rect b = bounds();
            if (y < b.y() || y >= b.bottom()) {
                return -1;
            }
            double rel = y - b.y() + scroll;
            int acc = 0;
            for (int i = 0; i < entries.size(); i++) {
                int h = entries.get(i).height();
                if (rel >= acc && rel < acc + h) {
                    return i;
                }
                acc += h;
            }
            return -1;
        }

        @Override
        protected Size measure(UiContext ctx) {
            return new Size(PALETTE_W - PAD * 2, contentHeight());
        }

        @Override
        public void layout(UiContext ctx) {
            clampScroll();
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            if (b.h() <= 0) {
                return;
            }
            canvas.pushScissor(b);
            if (entries.isEmpty()) {
                int lh = canvas.lineHeight(FontKind.UI);
                canvas.textCentered(Lang.tr("vanta.search.no_results", query), b.centerX(), b.y() + ROW_H - lh,
                        theme.textSecondary(), FontKind.UI_BOLD, false);
                canvas.textCentered(Lang.tr("vanta.search.no_results_hint"), b.centerX(), b.y() + ROW_H + 2,
                        theme.textMuted(), FontKind.UI, false);
                canvas.popScissor();
                return;
            }
            int hovered = isHovered() ? entryAt(ctx.mouseY()) : -1;
            int y = b.y() - Math.round(scroll);
            int scrollbarW = contentHeight() > b.h() ? 4 : 0;
            int rowW = b.w() - scrollbarW;
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                int h = e.height();
                if (y + h >= b.y() && y < b.bottom()) {
                    if (e.isHeader()) {
                        renderHeader(canvas, theme, e, b.x(), y, rowW, h);
                    } else {
                        renderRow(canvas, theme, e, b.x(), y, rowW, h, i == selected, i == hovered);
                    }
                }
                y += h;
            }
            canvas.popScissor();
            if (scrollbarW > 0) {
                int trackH = b.h();
                int thumbH = Math.max(10, Math.round((float) trackH * trackH / contentHeight()));
                float max = contentHeight() - trackH;
                int thumbY = b.y() + Math.round((trackH - thumbH) * (max <= 0 ? 0 : scroll / max));
                canvas.fillRounded(b.right() - 3, b.y(), 3, trackH, 1, theme.surface3());
                canvas.fillRounded(b.right() - 3, thumbY, 3, thumbH, 1, theme.scrollThumb());
            }
        }

        private void renderHeader(Canvas canvas, Theme theme, Entry e, int x, int y, int w, int h) {
            String label = Lang.tr(e.group().langKey());
            int lh = canvas.lineHeight(FontKind.UI_BOLD);
            int ty = y + (h - lh) / 2 + 1;
            canvas.text(label, x + Theme.SPACE_2, ty, theme.textMuted(), FontKind.UI_BOLD, false);
            int lineX = x + Theme.SPACE_2 + canvas.textWidth(label, FontKind.UI_BOLD) + Theme.SPACE_3;
            if (w - lineX + x > 0) {
                canvas.fill(lineX, y + h / 2, x + w - lineX, 1, Colors.withAlpha(theme.borderSubtle(), 0.9f));
            }
        }

        private void renderRow(Canvas canvas, Theme theme, Entry e, int x, int y, int w, int h, boolean sel,
                               boolean hover) {
            GlobalSearchResult r = e.result();
            if (sel) {
                canvas.fillRounded(x, y + 1, w, h - 2, Theme.RADIUS_MD, Colors.withAlpha(theme.accent(), 0.18f));
                canvas.fillGradientV(x, y + 4, 2, h - 8, theme.gradientStart(), theme.gradientEnd());
            } else if (hover) {
                canvas.fillRounded(x, y + 1, w, h - 2, Theme.RADIUS_MD, Colors.withAlpha(theme.surface3(), 0.9f));
            }
            int tileX = x + Theme.SPACE_3;
            int tileY = y + (h - TILE) / 2;
            canvas.fillRounded(tileX, tileY, TILE, TILE, Theme.RADIUS_SM, sel ? Colors.withAlpha(theme.accent(), 0.25f)
                    : theme.surface3());
            e.group().icon().draw(canvas, tileX + (TILE - ICON) / 2, tileY + (TILE - ICON) / 2, ICON,
                    sel ? theme.accentHover() : theme.textSecondary());
            int textX = tileX + TILE + Theme.SPACE_3;
            int subtitleW = Math.min(canvas.textWidth(r.subtitle(), FontKind.UI), (w - textX + x) / 2);
            int titleW = Math.max(0, x + w - Theme.SPACE_3 - subtitleW - Theme.SPACE_3 - textX);
            int ty = y + (h - canvas.lineHeight(FontKind.UI_BOLD)) / 2;
            HighlightedText.draw(canvas, r.title(), r.highlights(), textX, ty, titleW, FontKind.UI_BOLD,
                    theme.textPrimary(), theme.accentHover());
            canvas.textRight(canvas.textClipped(r.subtitle(), subtitleW, FontKind.UI), x + w - Theme.SPACE_3,
                    y + (h - canvas.lineHeight(FontKind.UI)) / 2, theme.textMuted(), FontKind.UI, false);
        }

        @Override
        protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
            if (button != Keys.MOUSE_LEFT) {
                return false;
            }
            int index = entryAt(y);
            if (index >= 0 && !entries.get(index).isHeader()) {
                selected = index;
                activateSelected();
            }
            return true;
        }

        @Override
        protected boolean onMouseScroll(UiContext ctx, double x, double y, double scrollX, double scrollY) {
            if (contentHeight() <= bounds().h()) {
                return false;
            }
            scroll += (float) (-scrollY * ROW_H);
            clampScroll();
            return true;
        }
    }

    /** The panel rectangle (tests). */
    public Rect panelRect() {
        return palette == null ? Rect.EMPTY : palette.panelRect();
    }
}
