package dev.vanta.core.hud.editor;

import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.i18n.Lang;
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
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.widget.IconButton;
import dev.vanta.core.ui.widget.SearchField;
import dev.vanta.core.ui.widget.Toggle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Left panel of the HUD editor: every widget type with a visibility switch (one row per instance) or a plus
 * button for types not in the layout, filtered by the search field. Clicking a row selects the widget.
 */
public final class WidgetListPanel extends UiNode {

    /** Panel width. */
    public static final int WIDTH = 140;
    /** Row height. */
    public static final int ROW_H = 18;
    private static final int PAD = Theme.SPACE_4;

    private final HudEditorSession session;
    private final SearchField search;
    private final Column rows = new Column(1);
    private final ScrollPanel scroll;
    private final List<WidgetRow> rowNodes = new ArrayList<>();
    private String filter = "";
    private List<String> builtKeys = List.of();
    private boolean dirty = true;

    /** Panel for a session. */
    public WidgetListPanel(HudEditorSession session) {
        this.session = Objects.requireNonNull(session, "session");
        this.search = new SearchField(Lang.tr("vanta.hud.editor.search_placeholder"));
        this.search.setId("hud.widgets.search");
        this.search.onChange(text -> {
            filter = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            dirty = true;
        });
        this.scroll = new ScrollPanel(rows);
        add(search);
        add(scroll);
        setId("hud.widgets");
    }

    /** Marks the row list stale (layout changed). */
    public void markDirty() {
        dirty = true;
    }

    /** Current filter text (lower case). */
    public String filter() {
        return filter;
    }

    /** The search field. */
    public SearchField searchField() {
        return search;
    }

    /** Rows currently shown. */
    public List<WidgetRow> rows() {
        return List.copyOf(rowNodes);
    }

    /** Row for a widget id, if listed. */
    public Optional<WidgetRow> rowFor(String id) {
        for (WidgetRow row : rowNodes) {
            if (id.equals(row.widgetId)) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    /** Row offering to add a type, if listed. */
    public Optional<WidgetRow> addRowFor(HudWidgetType type) {
        for (WidgetRow row : rowNodes) {
            if (row.widgetId == null && row.type == type) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    private boolean matches(HudWidgetType type) {
        if (filter.isEmpty()) {
            return true;
        }
        String name = Lang.tr(type.langKey()).toLowerCase(Locale.ROOT);
        String description = Lang.tr(type.descriptionKey()).toLowerCase(Locale.ROOT);
        return name.contains(filter) || description.contains(filter) || type.id().contains(filter);
    }

    private void rebuildIfNeeded(UiContext ctx) {
        HudLayout layout = session.model().layout();
        List<String> keys = new ArrayList<>();
        for (HudWidgetType type : HudWidgetType.values()) {
            if (!matches(type)) {
                continue;
            }
            List<HudWidgetState> instances = layout.byType(type);
            if (instances.isEmpty()) {
                keys.add(type.id() + ":+");
            } else {
                for (HudWidgetState instance : instances) {
                    keys.add(type.id() + ":" + instance.id());
                }
            }
        }
        if (!dirty && keys.equals(builtKeys)) {
            return;
        }
        dirty = false;
        builtKeys = keys;
        rows.clearChildren();
        rowNodes.clear();
        for (HudWidgetType type : HudWidgetType.values()) {
            if (!matches(type)) {
                continue;
            }
            List<HudWidgetState> instances = layout.byType(type);
            if (instances.isEmpty()) {
                WidgetRow row = new WidgetRow(type, null, instances.size());
                rowNodes.add(row);
                rows.add(row);
            } else {
                for (int i = 0; i < instances.size(); i++) {
                    WidgetRow row = new WidgetRow(type, instances.get(i).id(), i);
                    rowNodes.add(row);
                    rows.add(row);
                }
            }
        }
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(WIDTH, 120);
    }

    @Override
    public void layout(UiContext ctx) {
        rebuildIfNeeded(ctx);
        Rect b = bounds();
        int y = b.y() + PAD + ctx.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_3;
        search.setBounds(b.x() + PAD, y, b.w() - PAD * 2, dev.vanta.core.ui.widget.TextField.HEIGHT);
        search.layout(ctx);
        y += dev.vanta.core.ui.widget.TextField.HEIGHT + Theme.SPACE_3;
        scroll.setBounds(b.x() + PAD, y, b.w() - PAD * 2, Math.max(0, b.bottom() - PAD - y));
        scroll.layout(ctx);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG,
                Colors.withAlpha(theme.surface1(), theme.reducedTransparency() ? 1f : 0.94f));
        canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.borderSubtle());
        int y = b.y() + PAD;
        canvas.fillGradientV(b.x() + PAD, y, 2, canvas.lineHeight(FontKind.UI_BOLD), theme.gradientStart(),
                theme.gradientEnd());
        canvas.text(Lang.tr("vanta.hud.editor.widgets"), b.x() + PAD + Theme.SPACE_3, y, theme.textPrimary(),
                FontKind.UI_BOLD, false);
        String count = Lang.tr("vanta.hud.editor.widgets_count", session.widgetCount());
        canvas.textRight(count, b.right() - PAD, y, theme.textMuted(), FontKind.UI, false);
        if (rowNodes.isEmpty()) {
            Rect s = scroll.bounds();
            canvas.textCentered(canvas.textClipped(Lang.tr("vanta.hud.editor.no_matches"), s.w(), FontKind.UI),
                    s.centerX(), s.y() + Theme.SPACE_4, theme.textMuted(), FontKind.UI, false);
        }
    }

    /** One list row: a widget instance with a visibility switch, or a type with an add button. */
    public final class WidgetRow extends UiNode {
        private final HudWidgetType type;
        private final String widgetId;
        private final int ordinal;
        private final Toggle toggle;
        private final IconButton addButton;

        WidgetRow(HudWidgetType type, String widgetId, int ordinal) {
            this.type = type;
            this.widgetId = widgetId;
            this.ordinal = ordinal;
            setFocusable(true);
            setTooltip(Lang.tr(type.descriptionKey()));
            if (widgetId != null) {
                setId("hud.widgets.row." + widgetId);
                toggle = new Toggle(isWidgetEnabled(), on -> session.toggleEnabled(widgetId));
                toggle.setId("hud.widgets.toggle." + widgetId);
                addButton = null;
                add(toggle);
            } else {
                setId("hud.widgets.add." + type.id());
                toggle = null;
                addButton = new IconButton(Icons.PLUS, () -> session.addWidget(type)).sizes(16, 8);
                addButton.setTooltip(Lang.tr("vanta.hud.editor.add_widget"));
                addButton.setId("hud.widgets.addbutton." + type.id());
                add(addButton);
            }
        }

        /** Widget type of the row. */
        public HudWidgetType type() {
            return type;
        }

        /** Widget id, or {@code null} for an add row. */
        public String widgetId() {
            return widgetId;
        }

        /** The visibility switch (instance rows only). */
        public Toggle toggle() {
            return toggle;
        }

        /** The add button (type rows only). */
        public IconButton addButton() {
            return addButton;
        }

        private boolean isWidgetEnabled() {
            return widgetId != null && session.model().layout().find(widgetId).map(HudWidgetState::enabled).orElse(false);
        }

        private boolean isSelected() {
            return widgetId != null && session.model().selectedId().map(widgetId::equals).orElse(false);
        }

        private String label() {
            String name = Lang.tr(type.langKey());
            return ordinal > 0 ? Lang.tr("vanta.hud.editor.instance", name, ordinal + 1) : name;
        }

        @Override
        protected Size measure(UiContext ctx) {
            return new Size(WIDTH - PAD * 2, ROW_H);
        }

        @Override
        public void layout(UiContext ctx) {
            Rect b = bounds();
            if (toggle != null) {
                toggle.setBounds(b.right() - Theme.SPACE_2 - Toggle.TRACK_W, b.y() + (b.h() - Toggle.TRACK_H) / 2,
                        Toggle.TRACK_W, Toggle.TRACK_H);
                toggle.layout(ctx);
            } else {
                addButton.setBounds(b.right() - Theme.SPACE_2 - 16, b.y() + (b.h() - 16) / 2, 16, 16);
                addButton.layout(ctx);
            }
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            boolean selected = isSelected();
            if (toggle != null) {
                toggle.setOn(isWidgetEnabled());
            }
            if (selected) {
                canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_SM, Colors.withAlpha(theme.accent(), 0.18f));
                canvas.fillGradientV(b.x(), b.y() + 3, 2, b.h() - 6, theme.gradientStart(), theme.gradientEnd());
            } else if (isHovered()) {
                canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_SM, Colors.withAlpha(theme.surface3(), 0.9f));
            }
            int controlW = (toggle != null ? Toggle.TRACK_W : 16) + Theme.SPACE_2;
            int textX = b.x() + Theme.SPACE_3;
            int maxW = Math.max(0, b.right() - controlW - Theme.SPACE_2 - textX);
            int color;
            if (widgetId == null) {
                color = theme.textMuted();
            } else if (!isWidgetEnabled()) {
                color = theme.textSecondary();
            } else {
                color = theme.textPrimary();
            }
            canvas.text(canvas.textClipped(label(), maxW, FontKind.UI), textX, b.y() + (b.h() - canvas.lineHeight(FontKind.UI)) / 2,
                    color, FontKind.UI, false);
            if (isFocused()) {
                canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_SM, Colors.withAlpha(theme.borderFocus(), 0.9f));
            }
        }

        private void activate(UiContext ctx) {
            if (widgetId != null) {
                session.select(widgetId);
            } else {
                session.addWidget(type);
            }
            ctx.playClick();
        }

        @Override
        protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
            if (button != Keys.MOUSE_LEFT) {
                return false;
            }
            requestFocus(ctx);
            activate(ctx);
            return true;
        }

        @Override
        public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
            if (Keys.isActivate(key)) {
                activate(ctx);
                return true;
            }
            return false;
        }
    }
}
