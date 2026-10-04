package dev.vanta.core.crosshair.editor;

import dev.vanta.core.crosshair.CrosshairPreset;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.crosshair.CrosshairShape;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.VantaShell;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Card;
import dev.vanta.core.ui.widget.ColorField;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.Select;
import dev.vanta.core.ui.widget.Slider;
import dev.vanta.core.ui.widget.Toggle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The crosshair customizer: live preview tiles, the preset gallery and a control for every
 * {@link CrosshairStyle} field. Changes apply to the {@link dev.vanta.core.crosshair.CrosshairStore} immediately (so
 * the HUD follows) and are written to disk when the screen closes or Save is pressed. "Use vanilla crosshair" flips
 * the crosshair widget of the HUD layout, which is what {@code CrosshairRenderer.isCustomEnabled()} reads.
 * <p>
 * Wide screens show preview and presets on the left and the controls on the right; below
 * {@value #WIDE_BREAKPOINT} logical pixels everything stacks into one scrollable column.
 */
public final class CrosshairEditorScreen extends UiScreen {

    /** Logical width from which the two-column arrangement is used. */
    public static final int WIDE_BREAKPOINT = 540;
    /** Width of the left column in the wide arrangement. */
    public static final int LEFT_W = 244;
    /** Id of the crosshair widget created when the layout has none and the custom crosshair is turned on. */
    public static final String CROSSHAIR_WIDGET_ID = HudWidgetType.CROSSHAIR.id();

    private final VantaServices services;
    private VantaShell shell;
    private CrosshairPreviewStrip preview;
    private PresetGallery gallery;
    private Card previewCard;
    private Card presetsCard;
    private Card styleCard;
    private Select<CrosshairShape> shape;
    private Slider<Integer> size;
    private Slider<Integer> thickness;
    private Slider<Integer> gap;
    private Toggle outline;
    private Slider<Integer> outlineThickness;
    private Slider<Integer> opacity;
    private ColorField color;
    private ColorField outlineColor;
    private Toggle dynamic;
    private Toggle hideThirdPerson;
    private Toggle useVanilla;
    private Button saveButton;
    private boolean syncing;
    private Runnable unsubscribe;
    private boolean saved;

    /** Opens the customizer on the active style. */
    public CrosshairEditorScreen(VantaServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /** The style being edited (always the store's live style). */
    public CrosshairStyle style() {
        return services.crosshair().style();
    }

    // ---- accessors for tests -------------------------------------------------------------------------------------

    public Select<CrosshairShape> shapeSelect() {
        return shape;
    }

    public Slider<Integer> sizeSlider() {
        return size;
    }

    public Slider<Integer> thicknessSlider() {
        return thickness;
    }

    public Slider<Integer> gapSlider() {
        return gap;
    }

    public Toggle outlineToggle() {
        return outline;
    }

    public Slider<Integer> opacitySlider() {
        return opacity;
    }

    public ColorField colorField() {
        return color;
    }

    public Toggle dynamicToggle() {
        return dynamic;
    }

    public Toggle useVanillaToggle() {
        return useVanilla;
    }

    public PresetGallery gallery() {
        return gallery;
    }

    public CrosshairPreviewStrip preview() {
        return preview;
    }

    public Button saveButton() {
        return saveButton;
    }

    /** Whether the two-column arrangement is active. */
    public boolean isWide() {
        return width() >= WIDE_BREAKPOINT;
    }

    // ---- lifecycle -----------------------------------------------------------------------------------------------

    @Override
    public String title() {
        return Lang.tr("vanta.crosshair.title");
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected UiNode build(UiContext ctx) {
        Theme theme = ctx.theme();
        shell = new VantaShell(Lang.tr("vanta.crosshair.title"), this::done);
        shell.backTooltip(Lang.tr("vanta.common.back"));
        Button done = Button.primary(Lang.tr("vanta.common.done"), this::done);
        done.setId("crosshair.done");
        shell.rightSlot(done);

        preview = new CrosshairPreviewStrip(this::style);
        previewCard = new Card(Lang.tr("vanta.crosshair.preview"))
                .caption(Lang.tr("vanta.crosshair.preview.description"));
        previewCard.add(preview);

        gallery = new PresetGallery(this::style, this::applyPreset);
        presetsCard = new Card(Lang.tr("vanta.crosshair.presets"))
                .caption(Lang.tr("vanta.crosshair.presets.description"));
        presetsCard.add(gallery);

        styleCard = new Card(Lang.tr("vanta.crosshair.style"));
        buildControls(ctx, styleCard.body());

        Arrangement root = new Arrangement(theme);
        shell.content(root);
        unsubscribe = services.crosshair().onChange(s -> syncControls());
        return shell;
    }

    private void buildControls(UiContext ctx, Column body) {
        CrosshairStyle s = style();
        shape = new Select<>(List.of(CrosshairShape.values()), s.shape(), v -> Lang.tr(v.langKey()))
                .onChange(v -> apply(style().withShape(v)));
        shape.setId("crosshair.shape");
        body.add(stacked(Lang.tr("vanta.crosshair.shape"), shape));

        size = Slider.ofInt(1, CrosshairStyle.MAX_SIZE, 1, s.size()).valueWidth(22)
                .onChange(v -> apply(style().withSize(v)));
        size.setId("crosshair.size");
        body.add(stacked(Lang.tr("vanta.crosshair.size"), size));

        thickness = Slider.ofInt(1, CrosshairStyle.MAX_THICKNESS, 1, s.thickness()).valueWidth(22)
                .onChange(v -> apply(style().withThickness(v)));
        thickness.setId("crosshair.thickness");
        body.add(stacked(Lang.tr("vanta.crosshair.thickness"), thickness));

        gap = Slider.ofInt(0, CrosshairStyle.MAX_GAP, 1, s.gap()).valueWidth(22)
                .onChange(v -> apply(style().withGap(v)));
        gap.setId("crosshair.gap");
        body.add(stacked(Lang.tr("vanta.crosshair.gap"), gap));

        opacity = Slider.ofInt(0, 100, 5, (int) Math.round(s.opacity() * 100)).formatter(v -> v + "%").valueWidth(28)
                .onChange(v -> apply(style().withOpacity(v / 100.0)));
        opacity.setId("crosshair.opacity");
        body.add(stacked(Lang.tr("vanta.crosshair.opacity"), opacity));

        color = new ColorField(s.color(), false, c -> apply(style().withColor(Colors.opaque(c))));
        color.setId("crosshair.color");
        body.add(stacked(Lang.tr("vanta.crosshair.color"), color));

        outline = new Toggle(Lang.tr("vanta.crosshair.outline"), s.outline(),
                on -> apply(style().withOutline(on, style().outlineThickness())));
        outline.setId("crosshair.outline");
        body.add(outline);

        outlineThickness = Slider.ofInt(1, CrosshairStyle.MAX_OUTLINE, 1, s.outlineThickness()).valueWidth(22)
                .onChange(v -> apply(style().withOutline(style().outline(), v)));
        outlineThickness.setId("crosshair.outlineThickness");
        body.add(stacked(Lang.tr("vanta.crosshair.outline_thickness"), outlineThickness));

        outlineColor = new ColorField(s.outlineColor(), false, c -> apply(style().withOutlineColor(Colors.opaque(c))));
        outlineColor.setId("crosshair.outlineColor");
        body.add(stacked(Lang.tr("vanta.crosshair.outline_color"), outlineColor));

        dynamic = new Toggle(Lang.tr("vanta.crosshair.dynamic"), s.dynamic(), on -> apply(style().withDynamic(on)));
        dynamic.setId("crosshair.dynamic");
        body.add(described(dynamic, Lang.tr("vanta.crosshair.dynamic.description")));

        hideThirdPerson = new Toggle(Lang.tr("vanta.crosshair.hide_third_person"), s.hideOnThirdPerson(),
                on -> apply(style().withHideOnThirdPerson(on)));
        hideThirdPerson.setId("crosshair.hideThirdPerson");
        body.add(described(hideThirdPerson, Lang.tr("vanta.crosshair.hide_third_person.description")));

        useVanilla = new Toggle(Lang.tr("vanta.crosshair.use_vanilla"), isVanillaCrosshair(), this::setVanillaCrosshair);
        useVanilla.setId("crosshair.useVanilla");
        body.add(described(useVanilla, Lang.tr("vanta.crosshair.use_vanilla.description")));

        Row actions = new Row(Theme.SPACE_3).justify(dev.vanta.core.ui.layout.Justify.END);
        Button reset = Button.secondary(Lang.tr("vanta.crosshair.reset"), () -> apply(CrosshairStyle.DEFAULT))
                .icon(Icons.RESET);
        reset.setId("crosshair.reset");
        saveButton = Button.primary(Lang.tr("vanta.common.save"), this::save).icon(Icons.DOWNLOAD);
        saveButton.setId("crosshair.save");
        actions.add(reset);
        actions.add(saveButton);
        body.add(actions);
    }

    private static Column stacked(String label, UiNode control) {
        Column column = new Column(Theme.SPACE_1);
        column.add(new Label(label, Label.Variant.CAPTION));
        column.add(control);
        return column;
    }

    private static Column described(UiNode control, String description) {
        Column column = new Column(Theme.SPACE_1);
        column.add(control);
        column.add(new Label(description, Label.Variant.MUTED).wrap(true));
        return column;
    }

    @Override
    public void onClose() {
        if (unsubscribe != null) {
            unsubscribe.run();
            unsubscribe = null;
        }
        save();
    }

    // ---- model -----------------------------------------------------------------------------------------------------

    /** Applies a style to the store and refreshes the controls. */
    public void apply(CrosshairStyle next) {
        Objects.requireNonNull(next, "next");
        if (syncing || next.equals(style())) {
            return;
        }
        services.crosshair().setStyle(next);
        Optional<CrosshairPreset> preset = CrosshairPresets.matching(next);
        services.settings().set(VantaSettings.COSMETICS_CROSSHAIR_PRESET, preset.map(CrosshairPreset::id).orElse("custom"));
        syncControls();
    }

    /** Applies a built-in preset. */
    public void applyPreset(CrosshairPreset preset) {
        apply(preset.style());
    }

    /** True when the HUD layout has no enabled crosshair widget (vanilla draws the crosshair). */
    public boolean isVanillaCrosshair() {
        for (HudWidgetState widget : services.hud().layout().widgets()) {
            if (widget.type() == HudWidgetType.CROSSHAIR) {
                return !widget.enabled();
            }
        }
        return true;
    }

    /** Switches between the vanilla crosshair and the custom one by toggling the crosshair widget. */
    public void setVanillaCrosshair(boolean vanilla) {
        HudLayout layout = services.hud().layout();
        HudWidgetState crosshair = null;
        for (HudWidgetState widget : layout.widgets()) {
            if (widget.type() == HudWidgetType.CROSSHAIR) {
                crosshair = widget;
                break;
            }
        }
        if (crosshair == null) {
            if (vanilla) {
                return;
            }
            crosshair = HudWidgetState.defaults(layout.nextId(HudWidgetType.CROSSHAIR), HudWidgetType.CROSSHAIR);
        }
        services.hud().setLayout(layout.with(crosshair.withEnabled(!vanilla)));
        syncControls();
    }

    private void syncControls() {
        if (shape == null) {
            return;
        }
        CrosshairStyle s = style();
        syncing = true;
        try {
            shape.setValue(s.shape());
            size.setValue(s.size());
            thickness.setValue(s.thickness());
            gap.setValue(s.gap());
            opacity.setValue(Math.round(s.opacity() * 100));
            if (color.color() != Colors.opaque(s.color())) {
                color.setColor(s.color());
            }
            outline.setOn(s.outline());
            outlineThickness.setValue(s.outlineThickness());
            outlineThickness.setEnabled(s.outline());
            if (outlineColor.color() != Colors.opaque(s.outlineColor())) {
                outlineColor.setColor(s.outlineColor());
            }
            outlineColor.setEnabled(s.outline());
            dynamic.setOn(s.dynamic());
            hideThirdPerson.setOn(s.hideOnThirdPerson());
            useVanilla.setOn(isVanillaCrosshair());
        } finally {
            syncing = false;
        }
    }

    /**
     * Writes the style (and the HUD layout when the vanilla switch changed it) when dirty.
     *
     * @return true when the crosshair file was written
     */
    public boolean save() {
        boolean wrote = false;
        if (services.crosshair().isDirty()) {
            services.crosshair().save();
            services.notifications().post(NotificationKind.SUCCESS, Lang.tr("vanta.crosshair.saved"),
                    Lang.tr("vanta.crosshair.saved.body"));
            wrote = true;
        }
        services.hud().saveIfDirty();
        saved = true;
        return wrote;
    }

    /** Saves and closes. */
    public void done() {
        save();
        close();
    }

    /** Whether {@link #save()} ran (for tests). */
    public boolean wasSaved() {
        return saved;
    }

    @Override
    protected boolean onKeyDown(int key, int scancode, int mods) {
        if (key == Keys.ESCAPE) {
            done();
            return true;
        }
        if (key == Keys.S && Keys.hasControl(mods)) {
            save();
            return true;
        }
        return false;
    }

    // ---- arrangement -------------------------------------------------------------------------------------------------

    /** Switches between two columns and one scrollable column depending on the width. */
    private final class Arrangement extends UiNode {
        private final Column leftColumn = new Column(Theme.SPACE_4);
        private final Column rightColumn = new Column(Theme.SPACE_4);
        private final Column singleColumn = new Column(Theme.SPACE_4);
        private final ScrollPanel leftScroll;
        private final ScrollPanel rightScroll;
        private final ScrollPanel singleScroll;
        private Boolean wide;

        Arrangement(Theme theme) {
            int fade = Colors.withAlpha(theme.bgBase(), 0.9f);
            leftScroll = new ScrollPanel(leftColumn).edgeFade(fade);
            rightScroll = new ScrollPanel(rightColumn).edgeFade(fade);
            singleScroll = new ScrollPanel(singleColumn).edgeFade(fade);
            add(leftScroll);
            add(rightScroll);
            add(singleScroll);
        }

        private void arrange(boolean twoColumns) {
            if (wide != null && wide == twoColumns) {
                return;
            }
            wide = twoColumns;
            leftColumn.clearChildren();
            rightColumn.clearChildren();
            singleColumn.clearChildren();
            if (twoColumns) {
                leftColumn.add(previewCard);
                leftColumn.add(presetsCard);
                rightColumn.add(styleCard);
            } else {
                singleColumn.add(previewCard);
                singleColumn.add(presetsCard);
                singleColumn.add(styleCard);
            }
            leftScroll.setVisible(twoColumns);
            rightScroll.setVisible(twoColumns);
            singleScroll.setVisible(!twoColumns);
        }

        @Override
        public void layout(UiContext ctx) {
            Rect b = bounds();
            boolean twoColumns = b.w() >= WIDE_BREAKPOINT - VantaShell.CONTENT_PAD * 2;
            arrange(twoColumns);
            // Two passes: wrapped labels and the preview strip measure against the width of the previous pass.
            for (int pass = 0; pass < 2; pass++) {
                if (twoColumns) {
                    leftScroll.setBounds(b.x(), b.y(), LEFT_W, b.h());
                    leftScroll.layout(ctx);
                    int rx = b.x() + LEFT_W + Theme.SPACE_5;
                    rightScroll.setBounds(rx, b.y(), Math.max(0, b.right() - rx), b.h());
                    rightScroll.layout(ctx);
                } else {
                    singleScroll.setBounds(b);
                    singleScroll.layout(ctx);
                }
            }
        }
    }
}
