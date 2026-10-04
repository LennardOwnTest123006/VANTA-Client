package dev.vanta.core.hud.editor;

import dev.vanta.core.hud.HudEditorModel;
import dev.vanta.core.hud.HudProp;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.widget.Badge;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.ColorField;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.Select;
import dev.vanta.core.ui.widget.Slider;
import dev.vanta.core.ui.widget.TextField;
import dev.vanta.core.ui.widget.Toggle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Right panel of the HUD editor: edits the selected widget. Position (anchor picker + offsets), size, scale,
 * opacity, colours, the widget-specific properties from {@link HudWidgetType#settingsSchema()} and the actions
 * (duplicate, bring to front, reset, remove). Rebuilt when the selection changes; values are kept in sync with the
 * model otherwise so sliders stay draggable.
 * <p>
 * Rapid edits of the same field within {@value #COALESCE_MS} ms (a slider drag, typing a number) collapse into a
 * single undo step.
 */
public final class InspectorPanel extends UiNode {

    /** Panel width. */
    public static final int WIDTH = 176;
    /** Edits of one field closer together than this merge into one undo step. */
    public static final long COALESCE_MS = 600L;
    private static final int PAD = Theme.SPACE_4;
    private static final int FIELD_W = 44;

    private final HudEditorSession session;
    private final Column content = new Column(Theme.SPACE_3);
    private final ScrollPanel scroll;
    private final Map<String, UiNode> propControls = new LinkedHashMap<>();
    private String builtFor;
    private boolean dirty = true;
    private boolean syncing;
    private boolean applying;
    private String lastField;
    private long lastEditAt;

    private Toggle visible;
    private Badge hiddenBadge;
    private AnchorPicker anchor;
    private TextField offsetX;
    private TextField offsetY;
    private TextField widthField;
    private TextField heightField;
    private Slider<Double> scale;
    private Slider<Double> opacity;
    private Toggle background;
    private ColorField backgroundColor;
    private ColorField textColor;
    private ColorField accentColor;

    /** Inspector for a session. */
    public InspectorPanel(HudEditorSession session) {
        this.session = Objects.requireNonNull(session, "session");
        this.scroll = new ScrollPanel(content);
        add(scroll);
        setId("hud.inspector");
        session.model().onChange(() -> {
            if (!applying) {
                lastField = null;
            }
            dirty = true;
        });
    }

    /** Marks the panel stale (selection or layout changed). */
    public void markDirty() {
        dirty = true;
    }

    // ---- accessors for tests -------------------------------------------------------------------------------------

    public Slider<Double> scaleSlider() {
        return scale;
    }

    public Slider<Double> opacitySlider() {
        return opacity;
    }

    public AnchorPicker anchorPicker() {
        return anchor;
    }

    public TextField offsetXField() {
        return offsetX;
    }

    public TextField offsetYField() {
        return offsetY;
    }

    public TextField widthFieldNode() {
        return widthField;
    }

    public TextField heightFieldNode() {
        return heightField;
    }

    public Toggle visibleToggle() {
        return visible;
    }

    public Toggle backgroundToggle() {
        return background;
    }

    public ColorField textColorField() {
        return textColor;
    }

    /** Control built for a widget property key, if the selected widget has it. */
    public Optional<UiNode> propControl(String key) {
        return Optional.ofNullable(propControls.get(key));
    }

    // ---- building ----------------------------------------------------------------------------------------------------

    private static String keyOf(Optional<HudWidgetState> widget) {
        return widget.map(w -> w.id() + "|" + w.type().id()).orElse("");
    }

    private void rebuildIfNeeded(UiContext ctx) {
        Optional<HudWidgetState> selected = session.selected();
        String key = keyOf(selected);
        if (key.equals(builtFor) && !dirty) {
            return;
        }
        boolean structural = !key.equals(builtFor);
        dirty = false;
        if (!structural) {
            syncValues(selected);
            return;
        }
        builtFor = key;
        content.clearChildren();
        propControls.clear();
        visible = null;
        hiddenBadge = null;
        anchor = null;
        offsetX = null;
        offsetY = null;
        widthField = null;
        heightField = null;
        scale = null;
        opacity = null;
        background = null;
        backgroundColor = null;
        textColor = null;
        accentColor = null;
        if (selected.isEmpty()) {
            buildEmpty();
        } else {
            build(ctx, selected.get());
        }
        scroll.setScrollY(ctx, 0f, false);
    }

    private void buildEmpty() {
        content.add(new Label(Lang.tr("vanta.hud.editor.no_selection"), Label.Variant.CAPTION).wrap(true));
        content.add(new Label(Lang.tr("vanta.hud.editor.no_selection.hint"), Label.Variant.MUTED).wrap(true));
    }

    private void build(UiContext ctx, HudWidgetState w) {
        HudWidgetType type = w.type();
        Row header = new Row(Theme.SPACE_3);
        Label title = new Label(Lang.tr(type.langKey()), Label.Variant.TITLE);
        title.flex(1f);
        header.add(title);
        hiddenBadge = new Badge(Lang.tr("vanta.hud.editor.hidden"), Badge.Tone.WARNING);
        hiddenBadge.setVisible(!w.enabled());
        header.add(hiddenBadge);
        content.add(header);

        visible = new Toggle(Lang.tr("vanta.hud.editor.enabled"), w.enabled(),
                on -> edit(ctx, "enabled", s -> s.withEnabled(on)));
        visible.setId("hud.inspector.visible");
        content.add(visible);

        if (type.alwaysCentered()) {
            content.add(new Label(Lang.tr("vanta.hud.editor.crosshair_hint"), Label.Variant.CAPTION).wrap(true));
            Button open = Button.secondary(Lang.tr("vanta.hud.editor.open_crosshair"),
                    () -> ctx.host().openScreen(ScreenId.CROSSHAIR)).icon(Icons.CROSSHAIR);
            open.setId("hud.inspector.openCrosshair");
            content.add(open);
        } else {
            content.add(section(Lang.tr("vanta.hud.editor.position")));
            Row positionRow = new Row(Theme.SPACE_4).align(dev.vanta.core.ui.layout.Align.START);
            anchor = new AnchorPicker(w.anchor(), a -> {
                if (!syncing) {
                    applying = true;
                    try {
                        session.setSelectedAnchor(a);
                    } finally {
                        applying = false;
                    }
                    lastField = null;
                }
            });
            anchor.setId("hud.inspector.anchor");
            anchor.setTooltip(Lang.tr("vanta.hud.editor.anchor"));
            positionRow.add(anchor);
            Column offsets = new Column(Theme.SPACE_2);
            offsets.flex(1f);
            offsetX = intField(w.offsetX(), v -> edit(ctx, "offsetX", s -> s.withPosition(s.anchor(), v, s.offsetY())));
            offsetX.setId("hud.inspector.offsetX");
            offsetY = intField(w.offsetY(), v -> edit(ctx, "offsetY", s -> s.withPosition(s.anchor(), s.offsetX(), v)));
            offsetY.setId("hud.inspector.offsetY");
            offsets.add(labeled(Lang.tr("vanta.hud.label.x"), offsetX));
            offsets.add(labeled(Lang.tr("vanta.hud.label.y"), offsetY));
            positionRow.add(offsets);
            content.add(positionRow);
        }

        if (type.supportsResize()) {
            content.add(section(Lang.tr("vanta.hud.editor.size")));
            Row sizeRow = new Row(Theme.SPACE_3);
            widthField = intField(w.width(), v -> edit(ctx, "width",
                    s -> s.withSize(Math.max(HudWidgetState.MIN_SIZE, v), s.height())));
            widthField.setId("hud.inspector.width");
            heightField = intField(w.height(), v -> edit(ctx, "height",
                    s -> s.withSize(s.width(), Math.max(HudWidgetState.MIN_SIZE, v))));
            heightField.setId("hud.inspector.height");
            sizeRow.add(labeled(Lang.tr("vanta.hud.editor.width.short"), widthField));
            sizeRow.add(labeled(Lang.tr("vanta.hud.editor.height.short"), heightField));
            content.add(sizeRow);
            Button fit = Button.ghost(Lang.tr("vanta.hud.editor.fit_content"), () -> {
                applying = true;
                try {
                    session.fitToContent(ctx.metrics());
                } finally {
                    applying = false;
                }
                lastField = null;
            }).compact(true);
            fit.setId("hud.inspector.fit");
            content.add(fit);
        }

        if (type.supportsScale() || type.supportsColor()) {
            content.add(section(Lang.tr("vanta.hud.editor.appearance")));
        }
        if (type.supportsScale()) {
            scale = Slider.ofDouble(HudWidgetState.MIN_SCALE, HudWidgetState.MAX_SCALE, 0.05, w.scale())
                    .formatter(v -> String.format(Locale.ROOT, "%.2fx", v))
                    .valueWidth(30)
                    .onChange(v -> edit(ctx, "scale", s -> s.withScale(v)));
            scale.setId("hud.inspector.scale");
            content.add(stacked(Lang.tr("vanta.hud.editor.scale"), scale));
        }
        if (type.supportsColor()) {
            opacity = Slider.ofDouble(0.1, 1.0, 0.05, w.opacity())
                    .formatter(v -> Math.round(v * 100) + "%")
                    .valueWidth(30)
                    .onChange(v -> edit(ctx, "opacity", s -> s.withOpacity(v)));
            opacity.setId("hud.inspector.opacity");
            content.add(stacked(Lang.tr("vanta.hud.editor.opacity"), opacity));
            background = new Toggle(Lang.tr("vanta.hud.editor.background"), w.backgroundEnabled(),
                    on -> edit(ctx, "background", s -> s.withBackground(on, s.backgroundColor())));
            background.setId("hud.inspector.background");
            content.add(background);
            backgroundColor = new ColorField(w.backgroundColor(), true,
                    c -> edit(ctx, "backgroundColor", s -> s.withBackground(s.backgroundEnabled(), c)));
            backgroundColor.setId("hud.inspector.backgroundColor");
            content.add(stacked(Lang.tr("vanta.hud.editor.background_color"), backgroundColor));
            textColor = new ColorField(w.textColor(), true, c -> edit(ctx, "textColor", s -> s.withTextColor(c)));
            textColor.setId("hud.inspector.textColor");
            content.add(stacked(Lang.tr("vanta.hud.editor.text_color"), textColor));
            accentColor = new ColorField(w.accentColor(), true, c -> edit(ctx, "accentColor", s -> s.withAccentColor(c)));
            accentColor.setId("hud.inspector.accentColor");
            content.add(stacked(Lang.tr("vanta.hud.editor.accent_color"), accentColor));
        }

        List<HudProp> schema = type.settingsSchema();
        if (!schema.isEmpty()) {
            content.add(section(Lang.tr("vanta.hud.editor.widget_settings")));
            for (HudProp prop : schema) {
                UiNode control = propControl(ctx, w, prop);
                propControls.put(prop.key(), control);
            }
        }

        content.add(section(Lang.tr("vanta.hud.editor.actions")));
        List<Button> actions = new ArrayList<>();
        if (!type.alwaysCentered()) {
            Button duplicate = Button.secondary(Lang.tr("vanta.hud.editor.duplicate"), () -> run(session::duplicate))
                    .icon(Icons.COPY).compact(true);
            duplicate.setId("hud.inspector.duplicate");
            actions.add(duplicate);
            Button front = Button.secondary(Lang.tr("vanta.hud.editor.bring_to_front"),
                    () -> run(session::bringToFront)).icon(Icons.ARROW_UP).compact(true);
            front.setId("hud.inspector.front");
            actions.add(front);
        }
        Button reset = Button.secondary(Lang.tr("vanta.hud.editor.reset_widget"), () -> run(session::resetSelected))
                .icon(Icons.RESET).compact(true);
        reset.setId("hud.inspector.reset");
        actions.add(reset);
        if (!type.alwaysCentered()) {
            Button remove = Button.danger(Lang.tr("vanta.hud.editor.remove"), () -> run(session::removeSelected))
                    .icon(Icons.TRASH).compact(true);
            remove.setId("hud.inspector.remove");
            actions.add(remove);
        }
        for (Button action : actions) {
            content.add(action);
        }
    }

    private void run(Runnable action) {
        applying = true;
        try {
            action.run();
        } finally {
            applying = false;
        }
        lastField = null;
    }

    private UiNode propControl(UiContext ctx, HudWidgetState w, HudProp prop) {
        String label = Lang.tr(prop.langKey());
        String key = prop.key();
        switch (prop.kind()) {
            case BOOL -> {
                Toggle toggle = new Toggle(label, w.propBool(key),
                        on -> edit(ctx, "prop." + key, s -> s.withProp(key, Boolean.toString(on))));
                toggle.setId("hud.inspector.prop." + key);
                content.add(toggle);
                return toggle;
            }
            case INT -> {
                Slider<Integer> slider = Slider.ofInt(prop.min(), prop.max(), 1, w.propInt(key))
                        .valueWidth(22)
                        .onChange(v -> edit(ctx, "prop." + key, s -> s.withProp(key, Integer.toString(v))));
                slider.setId("hud.inspector.prop." + key);
                content.add(stacked(label, slider));
                return slider;
            }
            default -> {
                Select<String> select = new Select<>(prop.options(), w.prop(key), o -> Lang.tr(prop.optionLangKey(o)))
                        .onChange(v -> edit(ctx, "prop." + key, s -> s.withProp(key, v)));
                select.setId("hud.inspector.prop." + key);
                content.add(stacked(label, select));
                return select;
            }
        }
    }

    private TextField intField(int initial, java.util.function.IntConsumer onValue) {
        TextField field = new TextField(Integer.toString(initial))
                .maxLength(5)
                .charFilter(cp -> cp == '-' || (cp >= '0' && cp <= '9'))
                .validator(text -> parseInt(text).isPresent());
        field.width(FIELD_W);
        field.onChange(text -> {
            if (syncing) {
                return;
            }
            parseInt(text).ifPresent(onValue::accept);
        });
        return field;
    }

    private static Optional<Integer> parseInt(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String t = text.trim();
        if (t.isEmpty() || "-".equals(t)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(t));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Label section(String text) {
        return new Label(text.toUpperCase(Locale.ROOT), Label.Variant.MUTED).font(FontKind.UI_BOLD);
    }

    private static Row labeled(String label, UiNode control) {
        Row row = new Row(Theme.SPACE_3);
        Label l = new Label(label, Label.Variant.CAPTION);
        l.flex(1f);
        row.add(l);
        row.add(control);
        return row;
    }

    private static Column stacked(String label, UiNode control) {
        Column column = new Column(Theme.SPACE_1);
        column.add(new Label(label, Label.Variant.CAPTION));
        column.add(control);
        return column;
    }

    // ---- editing -------------------------------------------------------------------------------------------------------

    /** Applies a change to the selected widget, merging rapid edits of the same field into one undo step. */
    void edit(UiContext ctx, String field, UnaryOperator<HudWidgetState> change) {
        if (syncing) {
            return;
        }
        HudEditorModel model = session.model();
        long now = ctx.now();
        applying = true;
        try {
            if (field.equals(lastField) && now - lastEditAt <= COALESCE_MS && model.canUndo()) {
                model.undo();
            }
            model.update(change);
        } finally {
            applying = false;
        }
        lastField = field;
        lastEditAt = now;
    }

    private void syncValues(Optional<HudWidgetState> selected) {
        if (selected.isEmpty()) {
            return;
        }
        HudWidgetState w = selected.get();
        syncing = true;
        try {
            if (visible != null) {
                visible.setOn(w.enabled());
            }
            if (hiddenBadge != null) {
                hiddenBadge.setVisible(!w.enabled());
            }
            if (anchor != null) {
                anchor.setValue(w.anchor());
            }
            syncField(offsetX, w.offsetX());
            syncField(offsetY, w.offsetY());
            syncField(widthField, w.width());
            syncField(heightField, w.height());
            if (scale != null) {
                scale.setValue(w.scale());
            }
            if (opacity != null) {
                opacity.setValue(w.opacity());
            }
            if (background != null) {
                background.setOn(w.backgroundEnabled());
            }
            if (backgroundColor != null && backgroundColor.color() != w.backgroundColor()) {
                backgroundColor.setColor(w.backgroundColor());
            }
            if (textColor != null && textColor.color() != w.textColor()) {
                textColor.setColor(w.textColor());
            }
            if (accentColor != null && accentColor.color() != w.accentColor()) {
                accentColor.setColor(w.accentColor());
            }
            for (HudProp prop : w.type().settingsSchema()) {
                UiNode control = propControls.get(prop.key());
                if (control instanceof Toggle toggle) {
                    toggle.setOn(w.propBool(prop.key()));
                } else if (control instanceof Slider<?> slider) {
                    slider.setValue(w.propInt(prop.key()));
                } else if (control instanceof Select<?> select) {
                    @SuppressWarnings("unchecked")
                    Select<String> typed = (Select<String>) select;
                    typed.setValue(w.prop(prop.key()));
                }
            }
        } finally {
            syncing = false;
        }
    }

    private static void syncField(TextField field, int value) {
        if (field == null) {
            return;
        }
        Optional<Integer> current = parseInt(field.text());
        if (current.isEmpty() || current.get() != value) {
            field.setText(Integer.toString(value));
        }
    }

    // ---- node ----------------------------------------------------------------------------------------------------------

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(WIDTH, 120);
    }

    @Override
    public void layout(UiContext ctx) {
        rebuildIfNeeded(ctx);
        Rect b = bounds();
        int y = b.y() + PAD + ctx.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_3;
        scroll.setBounds(b.x() + PAD, y, b.w() - PAD * 2, Math.max(0, b.bottom() - PAD - y));
        // Two passes so wrapped labels measure against the real column width.
        scroll.layout(ctx);
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
        canvas.text(Lang.tr("vanta.hud.editor.inspector"), b.x() + PAD + Theme.SPACE_3, y, theme.textPrimary(),
                FontKind.UI_BOLD, false);
        session.selected().ifPresent(w -> canvas.textRight(canvas.textClipped(w.id(), b.w() / 2, FontKind.UI),
                b.right() - PAD, y, theme.textMuted(), FontKind.UI, false));
    }
}
