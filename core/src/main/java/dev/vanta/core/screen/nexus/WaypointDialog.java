package dev.vanta.core.screen.nexus;

import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.layout.Align;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Justify;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.TextField;
import dev.vanta.core.waypoints.Waypoint;
import dev.vanta.core.waypoints.WaypointCategories;
import dev.vanta.core.waypoints.WaypointColors;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Modal add / edit dialog for a waypoint: name, X / Y / Z (prefilled with the player's position for a new waypoint),
 * category with suggestion chips and the colour swatches of {@link WaypointColors#PALETTE}. The confirm button stays
 * disabled while the name is blank or taken or a coordinate is not a number; Enter in a field confirms, Escape cancels.
 */
public final class WaypointDialog extends UiNode {
    /** Dialog width. */
    public static final int WIDTH = 300;
    private static final int PAD = 12;
    private static final int SWATCH = 14;

    /** What the player entered. */
    public record Values(String name, double x, double y, double z, String category, int color) {
    }

    private final Column column = new Column(Theme.SPACE_3);
    private final TextField name;
    private final TextField fieldX;
    private final TextField fieldY;
    private final TextField fieldZ;
    private final TextField category;
    private final Row swatches = new Row(Theme.SPACE_2);
    private final Label error = new Label("", Label.Variant.CAPTION);
    private final Button cancel;
    private final Button confirm;
    private final Function<String, Optional<String>> nameValidator;
    private final Consumer<Values> onConfirm;
    private final String title;
    private int color;
    private UiContext openContext;

    private WaypointDialog(String title, String initialName, Vec3d position, String initialCategory, int initialColor,
                           List<String> suggestions, Function<String, Optional<String>> nameValidator,
                           Consumer<Values> onConfirm) {
        this.title = title;
        this.nameValidator = Objects.requireNonNull(nameValidator, "nameValidator");
        this.onConfirm = Objects.requireNonNull(onConfirm, "onConfirm");
        this.color = initialColor;
        name = new TextField(initialName).maxLength(Waypoint.MAX_NAME_LENGTH)
                .placeholder(Lang.tr("vanta.waypoints.dialog.name"));
        name.setId("waypoint-dialog.name");
        name.onChange(t -> validate());
        name.onSubmit(t -> submit());
        column.add(labeled(Lang.tr("vanta.waypoints.dialog.name"), name));
        Row coords = new Row(Theme.SPACE_3);
        fieldX = coordinate(position.x(), "x");
        fieldY = coordinate(position.y(), "y");
        fieldZ = coordinate(position.z(), "z");
        coords.add(labeled(Lang.tr("vanta.hud.label.x"), fieldX));
        coords.add(labeled(Lang.tr("vanta.hud.label.y"), fieldY));
        coords.add(labeled(Lang.tr("vanta.hud.label.z"), fieldZ));
        column.add(coords);
        category = new TextField(initialCategory).maxLength(WaypointCategories.MAX_LENGTH)
                .placeholder(Lang.tr("vanta.waypoints.dialog.category"));
        category.setId("waypoint-dialog.category");
        category.onSubmit(t -> submit());
        column.add(labeled(Lang.tr("vanta.waypoints.dialog.category"), category));
        ChipFlow chips = new ChipFlow();
        for (String suggestion : suggestions) {
            Button chip = Button.ghost(suggestion, () -> category.setText(suggestion)).compact(true);
            chip.setId("waypoint-dialog.category." + suggestion.toLowerCase(Locale.ROOT).replace(' ', '_'));
            chips.add(chip);
        }
        column.add(chips);
        for (int i = 0; i < WaypointColors.PALETTE.size(); i++) {
            swatches.add(new Swatch(WaypointColors.PALETTE.get(i), i));
        }
        column.add(labeled(Lang.tr("vanta.waypoints.dialog.color"), swatches));
        error.setId("waypoint-dialog.error");
        column.add(error);
        Row buttons = new Row(Theme.SPACE_3).justify(Justify.END);
        cancel = Button.secondary(Lang.tr("vanta.common.cancel"), null);
        cancel.setId("waypoint-dialog.cancel");
        confirm = Button.primary(Lang.tr("vanta.common.save"), this::submit);
        confirm.setId("waypoint-dialog.confirm");
        buttons.add(cancel);
        buttons.add(confirm);
        column.add(buttons);
        add(column);
        validate();
    }

    /**
     * Opens the dialog.
     *
     * @param nameValidator returns the translation key of a problem with the name, or empty when fine
     */
    public static WaypointDialog open(UiContext ctx, String title, String initialName, Vec3d position,
                                      String initialCategory, int initialColor, List<String> suggestions,
                                      Function<String, Optional<String>> nameValidator, Consumer<Values> onConfirm) {
        WaypointDialog d = new WaypointDialog(title, initialName, position, initialCategory, initialColor, suggestions,
                nameValidator, onConfirm);
        d.openContext = ctx;
        d.cancel.onClick(() -> d.close(ctx));
        ctx.popups().open(ctx, d, true, null);
        d.name.selectAll();
        ctx.focus().focus(ctx, d.name);
        return d;
    }

    private TextField coordinate(double initial, String axis) {
        TextField field = new TextField(format(initial)).maxLength(12)
                .charFilter(cp -> cp == '-' || cp == '.' || (cp >= '0' && cp <= '9'))
                .validator(text -> parse(text).isPresent());
        field.setId("waypoint-dialog." + axis);
        field.onChange(t -> validate());
        field.onSubmit(t -> submit());
        return field;
    }

    static String format(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static Optional<Double> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String t = text.trim();
        if (t.isEmpty() || "-".equals(t) || ".".equals(t) || "-.".equals(t)) {
            return Optional.empty();
        }
        try {
            double v = Double.parseDouble(t);
            return Double.isFinite(v) && Math.abs(v) < 30_000_000 ? Optional.of(v) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Column labeled(String label, UiNode control) {
        Column c = new Column(Theme.SPACE_1);
        c.add(new Label(label, Label.Variant.CAPTION));
        c.add(control);
        c.flex(control instanceof TextField ? 1f : 0f);
        return c;
    }

    // ---- accessors (tests) ---------------------------------------------------------------------------------------

    public TextField nameField() {
        return name;
    }

    public TextField xField() {
        return fieldX;
    }

    public TextField yField() {
        return fieldY;
    }

    public TextField zField() {
        return fieldZ;
    }

    public TextField categoryField() {
        return category;
    }

    public Button confirmButton() {
        return confirm;
    }

    public Button cancelButton() {
        return cancel;
    }

    /** The selected colour (ARGB). */
    public int color() {
        return color;
    }

    /** Selects a palette colour. */
    public void setColor(int argb) {
        this.color = argb;
    }

    /** Current validation problem (translated) or {@code null}. */
    public String error() {
        return error.text().isEmpty() ? null : error.text();
    }

    // ---- validation and submit -----------------------------------------------------------------------------------

    private void validate() {
        String problem = nameValidator.apply(name.text()).map(Lang::tr).orElse(null);
        if (problem == null && (parse(fieldX.text()).isEmpty() || parse(fieldY.text()).isEmpty()
                || parse(fieldZ.text()).isEmpty())) {
            problem = Lang.tr("vanta.waypoints.dialog.coordinates_invalid");
        }
        error.setText(problem == null ? "" : problem);
        error.color(problem == null ? 0 : openContext != null ? openContext.theme().danger() : 0xFFFF6B6B);
        confirm.setEnabled(problem == null);
    }

    private void submit() {
        validate();
        if (!confirm.isEnabled()) {
            return;
        }
        Values values = new Values(name.text().trim(), parse(fieldX.text()).orElseThrow(),
                parse(fieldY.text()).orElseThrow(), parse(fieldZ.text()).orElseThrow(),
                WaypointCategories.sanitize(category.text()), color);
        UiContext ctx = openContext;
        if (ctx != null) {
            close(ctx);
        }
        onConfirm.accept(values);
    }

    /** Closes the dialog without confirming. */
    public void close(UiContext ctx) {
        ctx.popups().close(ctx, this);
    }

    // ---- layout and rendering ------------------------------------------------------------------------------------

    private void position(UiContext ctx) {
        int w = Math.min(WIDTH, Math.max(160, ctx.screenWidth() - Theme.SPACE_6 * 2));
        int innerW = w - PAD * 2;
        int h = PAD + ctx.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_3 + column.preferredSize(ctx, innerW).h() + PAD;
        h = Math.min(h, ctx.screenHeight());
        setBounds((ctx.screenWidth() - w) / 2, Math.max(0, (ctx.screenHeight() - h) / 2), w, h);
    }

    @Override
    protected Size measure(UiContext ctx) {
        return bounds().size();
    }

    @Override
    public void layout(UiContext ctx) {
        position(ctx);
        Rect b = bounds();
        int top = b.y() + PAD + ctx.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_3;
        column.setBounds(b.x() + PAD, top, b.w() - PAD * 2, Math.max(0, b.bottom() - PAD - top));
        column.layout(ctx);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        canvas.fillRounded(b.x() + 2, b.y() + 4, b.w(), b.h(), Theme.RADIUS_LG, Colors.withAlpha(theme.bgVoid(), 0.6f));
        canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.surface2());
        canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.borderStrong());
        canvas.text(canvas.textClipped(title, b.w() - PAD * 2, FontKind.UI_BOLD), b.x() + PAD, b.y() + PAD,
                theme.textPrimary(), FontKind.UI_BOLD, false);
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (key == Keys.ESCAPE) {
            close(ctx);
            return true;
        }
        return false;
    }

    /** One palette colour; the selected one has a light ring. */
    public final class Swatch extends UiNode {
        private final int argb;

        Swatch(int argb, int index) {
            this.argb = argb;
            setFocusable(true);
            setId("waypoint-dialog.color." + index);
            setTooltip(WaypointColors.toHex(argb));
        }

        public int argb() {
            return argb;
        }

        @Override
        protected Size measure(UiContext ctx) {
            return new Size(SWATCH, SWATCH);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Rect b = bounds();
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, argb);
            if (color == argb) {
                canvas.strokeRounded(b.x() - 1, b.y() - 1, b.w() + 2, b.h() + 2, Theme.RADIUS_MD, Colors.WHITE);
            } else if (isFocused()) {
                canvas.strokeRounded(b.x() - 1, b.y() - 1, b.w() + 2, b.h() + 2, Theme.RADIUS_MD,
                        Colors.withAlpha(ctx.theme().borderFocus(), 0.9f));
            }
        }

        @Override
        protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
            if (button != Keys.MOUSE_LEFT) {
                return false;
            }
            requestFocus(ctx);
            ctx.playClick();
            color = argb;
            return true;
        }

        @Override
        public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
            if (Keys.isActivate(key)) {
                color = argb;
                ctx.playClick();
                return true;
            }
            return false;
        }
    }

    /** Alignment helper so the coordinate row centres its labels (unused directly; kept for the Row API). */
    static Align center() {
        return Align.CENTER;
    }
}
