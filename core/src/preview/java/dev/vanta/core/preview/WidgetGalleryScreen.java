package dev.vanta.core.preview;

import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.VantaShell;
import dev.vanta.core.ui.layout.Align;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Grid;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.layout.Spacer;
import dev.vanta.core.ui.widget.Badge;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Card;
import dev.vanta.core.ui.widget.ColorField;
import dev.vanta.core.ui.widget.Dialog;
import dev.vanta.core.ui.widget.Divider;
import dev.vanta.core.ui.widget.IconButton;
import dev.vanta.core.ui.widget.KeyCaptureField;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.ListView;
import dev.vanta.core.ui.widget.ProgressBar;
import dev.vanta.core.ui.widget.SearchField;
import dev.vanta.core.ui.widget.Select;
import dev.vanta.core.ui.widget.Slider;
import dev.vanta.core.ui.widget.Tabs;
import dev.vanta.core.ui.widget.TextField;
import dev.vanta.core.ui.widget.Toast;
import dev.vanta.core.ui.widget.Toggle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows every widget of the UI kit in its normal, hover, pressed, focused and disabled states inside the
 * standard {@link VantaShell}. Rendered first by {@code previewScreens}; also a handy manual test surface.
 * <p>
 * Hover/pressed/focused samples override the state getters so all states appear in one frame; the live cursor
 * of the preview hovers {@link #TOOLTIP_TARGET_ID} to show a real tooltip.
 */
public final class WidgetGalleryScreen extends UiScreen {

    /** Id of the node the preview cursor hovers. */
    public static final String TOOLTIP_TARGET_ID = "gallery.reset";

    private static final String[] SECTION_IDS = {"buttons", "controls", "inputs", "lists", "feedback", "icons"};
    private static final String[] SECTION_LABELS = {"Buttons", "Controls", "Inputs", "Lists", "Feedback", "Icons"};
    private static final Icons[] SECTION_ICONS = {Icons.PLAY, Icons.SLIDERS, Icons.EDIT, Icons.LIST, Icons.INFO,
            Icons.GRID};

    private final boolean openDialog;
    private final boolean openSelect;
    private final String initialSection;
    private VantaShell shell;
    private ScrollPanel scroll;
    private Column content;
    private final List<Card> cards = new ArrayList<>();
    private Select<String> sampleSelect;

    /** Plain gallery. */
    public WidgetGalleryScreen() {
        this(false, false);
    }

    private WidgetGalleryScreen(boolean openDialog, boolean openSelect) {
        this(openDialog, openSelect, null);
    }

    private WidgetGalleryScreen(boolean openDialog, boolean openSelect, String initialSection) {
        this.openDialog = openDialog;
        this.openSelect = openSelect;
        this.initialSection = initialSection;
    }

    /** Gallery scrolled to the given section id ({@code lists}, {@code feedback}, {@code icons}, ...). */
    public static WidgetGalleryScreen scrolledTo(String sectionId) {
        return new WidgetGalleryScreen(false, false, sectionId);
    }

    /** Gallery with a confirmation dialog open. */
    public static WidgetGalleryScreen withDialog() {
        return new WidgetGalleryScreen(true, false);
    }

    /** Gallery with the sample dropdown open. */
    public static WidgetGalleryScreen withSelectOpen() {
        return new WidgetGalleryScreen(false, true);
    }

    @Override
    public String title() {
        return "Widget gallery";
    }

    @Override
    protected UiNode build(UiContext ctx) {
        Theme theme = ctx.theme();
        shell = new VantaShell("Widget gallery", this::close);
        List<VantaShell.RailItem> rail = new ArrayList<>();
        for (int i = 0; i < SECTION_IDS.length; i++) {
            rail.add(new VantaShell.RailItem(SECTION_IDS[i], SECTION_ICONS[i], SECTION_LABELS[i]));
        }
        shell.rail(rail, SECTION_IDS[0], this::scrollToSection);
        SearchField search = new SearchField("Filter sections…");
        search.width(130);
        search.onChange(this::filter);
        shell.rightSlot(search);

        content = new Column(Theme.SPACE_5);
        cards.clear();
        cards.add(section("buttons", buttonsCard(ctx)));
        cards.add(section("controls", controlsCard(ctx)));
        cards.add(section("inputs", inputsCard(ctx)));
        cards.add(section("lists", listsCard(ctx)));
        cards.add(section("feedback", feedbackCard(ctx)));
        cards.add(section("icons", iconsCard(ctx)));
        for (Card c : cards) {
            content.add(c);
        }
        scroll = new ScrollPanel(content).edgeFade(Colors.withAlpha(theme.bgBase(), 0.9f));
        shell.content(scroll);
        return shell;
    }

    @Override
    protected void onInit() {
        UiContext ctx = context();
        if (initialSection != null) {
            shell.selectRail(ctx, initialSection);
            UiNode card = content.findById("section." + initialSection);
            if (card != null) {
                scroll.setScrollY(ctx, card.bounds().y() - content.bounds().y(), false);
            }
        }
        if (openDialog) {
            Dialog.confirm(ctx, "Delete profile?", "This removes “Competitive” together with its HUD layout "
                    + "and keybind overrides. This cannot be undone.", "Delete", "Cancel", true, () -> { });
        }
        if (openSelect && sampleSelect != null) {
            sampleSelect.open(ctx);
        }
    }

    private Card section(String id, Card card) {
        card.setId("section." + id);
        return card;
    }

    private void scrollToSection(String id) {
        UiNode card = content.findById("section." + id);
        if (card != null && scroll != null) {
            float target = card.bounds().y() - content.bounds().y();
            scroll.setScrollY(context(), target, true);
        }
    }

    private void filter(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (Card c : cards) {
            boolean match = q.isEmpty() || c.title().toLowerCase(Locale.ROOT).contains(q);
            c.setVisible(match);
        }
        invalidateLayout();
    }

    // ---------------------------------------------------------------- sections

    private static Label header(String text) {
        return new Label(text, Label.Variant.MUTED);
    }

    private static Label rowLabel(String text) {
        return new Label(text, Label.Variant.CAPTION);
    }

    private Card buttonsCard(UiContext ctx) {
        Card card = new Card("Buttons").caption("Primary, secondary, ghost and danger in every state");
        Grid grid = new Grid(6).gap(Theme.SPACE_3, Theme.SPACE_3);
        grid.add(header(""));
        for (String s : new String[] {"Normal", "Hover", "Pressed", "Focused", "Disabled"}) {
            grid.add(header(s));
        }
        boolean narrow = ctx.screenWidth() < 560;
        for (Button.Variant v : Button.Variant.values()) {
            String name = v.name().charAt(0) + v.name().substring(1).toLowerCase(Locale.ROOT);
            grid.add(rowLabel(name));
            grid.add(new Button("Button", v, null).compact(narrow));
            grid.add(hover(new Button("Button", v, null)).compact(narrow));
            grid.add(pressed(new Button("Button", v, null)).compact(narrow));
            grid.add(focused(new Button("Button", v, null)).compact(narrow));
            Button disabled = new Button("Button", v, null).compact(narrow);
            disabled.setEnabled(false);
            grid.add(disabled);
        }
        card.add(grid);
        Row icons = new Row(Theme.SPACE_3);
        icons.add(Button.primary("Create profile", null).icon(Icons.PLUS));
        icons.add(Button.secondary("Import", null).icon(Icons.DOWNLOAD));
        icons.add(Button.ghost("Open folder", null).icon(Icons.FOLDER));
        icons.add(Spacer.fixed(Theme.SPACE_3, 0));
        IconButton reset = new IconButton(Icons.RESET, null);
        reset.setTooltip("Reset to default");
        reset.setId(TOOLTIP_TARGET_ID);
        icons.add(reset);
        icons.add(new IconButton(Icons.EDIT, null).outlined(true));
        icons.add(new IconButton(Icons.STAR, null).active(true));
        icons.add(focused(new IconButton(Icons.COPY, null)));
        IconButton disabledIcon = new IconButton(Icons.TRASH, null).outlined(true);
        disabledIcon.setEnabled(false);
        icons.add(disabledIcon);
        icons.add(Spacer.grow());
        icons.add(new Badge("Coming soon", Badge.Tone.NEUTRAL));
        card.add(icons);
        return card;
    }

    private Card controlsCard(UiContext ctx) {
        Card card = new Card("Controls").caption("Toggles, sliders, progress and tabs");
        Row toggles = new Row(Theme.SPACE_6);
        toggles.add(labelled("Off", new Toggle(false, null)));
        toggles.add(labelled("On", new Toggle(true, null)));
        toggles.add(labelled("Hover", hover(new Toggle(true, null))));
        toggles.add(labelled("Focused", focused(new Toggle(false, null))));
        Toggle disabledToggle = new Toggle(true, null);
        disabledToggle.setEnabled(false);
        toggles.add(labelled("Disabled", disabledToggle));
        toggles.add(Spacer.grow());
        toggles.add(new Toggle("Show FPS", true, null));
        card.add(toggles);

        Grid sliders = new Grid(2).gap(Theme.SPACE_6, Theme.SPACE_3);
        sliders.add(labelled("Render distance", Slider.ofInt(2, 32, 1, 12).formatter(v -> v + " chunks").valueWidth(52)));
        sliders.add(labelled("HUD opacity", Slider.ofDouble(0, 1, 0.05, 0.6)
                .formatter(v -> Math.round(v * 100) + "%").valueWidth(32)));
        sliders.add(labelled("Focused", focused(Slider.ofInt(0, 100, 5, 35).formatter(v -> v + "%").valueWidth(32))));
        Slider<Integer> disabledSlider = Slider.ofInt(0, 100, 1, 70).formatter(v -> v + "%").valueWidth(32);
        disabledSlider.setEnabled(false);
        sliders.add(labelled("Disabled", disabledSlider));
        card.add(sliders);

        Grid progress = new Grid(3).gap(Theme.SPACE_6, Theme.SPACE_3);
        progress.add(labelled("Downloading 35%", new ProgressBar(0.35f)));
        progress.add(labelled("Verifying 72%", new ProgressBar(0.72f).color(ctx.theme().success())));
        progress.add(labelled("Indeterminate", ProgressBar.indeterminate()));
        card.add(progress);

        Row tabs = new Row(Theme.SPACE_6);
        tabs.add(new Tabs(List.of("General", "Video", "HUD", "Performance"), 1));
        tabs.add(focused(new Tabs(List.of("Layout", "Widgets"), 0)));
        card.add(tabs);
        return card;
    }

    private Card inputsCard(UiContext ctx) {
        Card card = new Card("Inputs").caption("Text, search, dropdowns, key capture and colors");
        Grid fields = new Grid(3).gap(Theme.SPACE_5, Theme.SPACE_3);
        fields.add(labelled("Empty", new TextField().placeholder("Profile name")));
        fields.add(labelled("Filled", new TextField("Competitive")));
        TextField selection = focused(new TextField("Competitive PvP"));
        selection.setCaret(11, false);
        selection.setCaret(15, true);
        fields.add(labelled("Focused with selection", selection));
        fields.add(labelled("Invalid", new TextField("#12G").validator(Colors::isValidHex)));
        TextField disabledField = new TextField("Locked value");
        disabledField.setEnabled(false);
        fields.add(labelled("Disabled", disabledField));
        SearchField search = new SearchField("Search settings…");
        search.setText("render distance");
        fields.add(labelled("Search", search));
        card.add(fields);

        Grid choices = new Grid(3).gap(Theme.SPACE_5, Theme.SPACE_3);
        sampleSelect = new Select<>(List.of("Low", "Balanced", "High", "Ultra"), "Balanced");
        choices.add(labelled("Select", sampleSelect));
        choices.add(labelled("Select focused", focused(new Select<>(List.of("Cross", "Dot", "Circle"), "Dot"))));
        Select<String> disabledSelect = new Select<>(List.of("Fancy"), "Fancy");
        disabledSelect.setEnabled(false);
        choices.add(labelled("Select disabled", disabledSelect));
        choices.add(labelled("Key binding", new KeyCaptureField(Keys.RIGHT_SHIFT, null, null)));
        choices.add(labelled("Capturing", capturing(new KeyCaptureField(Keys.C, null, null))));
        choices.add(labelled("Conflict", new KeyCaptureField(Keys.C, null, null).conflict(true)));
        choices.add(labelled("Color", new ColorField(0xFF7C5CFF, false, null)));
        choices.add(labelled("Color with alpha", new ColorField(0x993DDC97, true, null)));
        choices.add(labelled("Color focused", focused(new ColorField(0xFF4F8DFF, false, null))));
        card.add(choices);
        return card;
    }

    private Card listsCard(UiContext ctx) {
        Card card = new Card("Lists").caption("Virtualised list view, badges and dividers");
        Row row = new Row(Theme.SPACE_6).align(Align.START);
        List<String> worlds = new ArrayList<>();
        for (int i = 1; i <= 24; i++) {
            worlds.add("Survival world " + i);
        }
        ListView<String> list = new ListView<>(worlds, s -> s);
        list.height(ListView.ROW_H * 6);
        list.flex(1f);
        list.select(ctx, 2);
        row.add(list);
        Column side = new Column(Theme.SPACE_3).align(Align.START);
        side.width(200);
        side.add(header("Badges"));
        Row badges = new Row(Theme.SPACE_2);
        badges.add(new Badge("Default", Badge.Tone.NEUTRAL));
        badges.add(new Badge("Active", Badge.Tone.ACCENT));
        badges.add(new Badge("Saved", Badge.Tone.SUCCESS));
        badges.add(new Badge("3 conflicts", Badge.Tone.WARNING));
        side.add(badges);
        Row badges2 = new Row(Theme.SPACE_2);
        badges2.add(new Badge("Error", Badge.Tone.DANGER));
        badges2.add(new Badge("Beta", Badge.Tone.INFO));
        side.add(badges2);
        side.add(Spacer.fixed(0, Theme.SPACE_1));
        side.add(header("Divider"));
        Divider divider = new Divider();
        divider.width(200);
        side.add(divider);
        side.add(new Label("Body text in the primary color.", Label.Variant.BODY));
        side.add(new Label("Caption text in the secondary color.", Label.Variant.CAPTION));
        side.add(new Label("Muted helper text.", Label.Variant.MUTED));
        side.add(new Label("Title text", Label.Variant.TITLE));
        side.add(new Label("Display", Label.Variant.DISPLAY));
        row.add(side);
        card.add(row);
        return card;
    }

    private Card feedbackCard(UiContext ctx) {
        Card card = new Card("Feedback").caption("Toasts, nested cards and tooltips");
        Grid toasts = new Grid(2).gap(Theme.SPACE_3, Theme.SPACE_3);
        toasts.add(new Toast(Toast.Kind.SUCCESS, "Profile saved", "“Competitive” was written to config/vanta/profiles.")
                .progress(0.7f));
        toasts.add(new Toast(Toast.Kind.INFO, "Performance preset applied", "Balanced: render distance 10, simulation 8."));
        toasts.add(new Toast(Toast.Kind.WARNING, "3 keybind conflicts", "Zoom, Sprint and Open menu share the same key."));
        toasts.add(new Toast(Toast.Kind.ERROR, "Resource pack failed", "The pack targets an older pack format and was skipped."));
        card.add(toasts);
        Card nested = new Card("Nested card").caption("With a header slot").headerSlot(new Toggle(true, null));
        nested.add(new Label("Cards hold settings rows; the header slot takes a toggle or badge.", Label.Variant.CAPTION)
                .wrap(true));
        card.add(nested);
        return card;
    }

    private Card iconsCard(UiContext ctx) {
        Card card = new Card("Icons").caption("All " + Icons.values().length + " icons, drawn from primitives at 10 px");
        Grid grid = new Grid(8).gap(Theme.SPACE_2, Theme.SPACE_3);
        for (Icons icon : Icons.values()) {
            grid.add(new IconTile(icon));
        }
        card.add(grid);
        return card;
    }

    // ---------------------------------------------------------------- helpers

    private static Column labelled(String caption, UiNode node) {
        Column c = new Column(Theme.SPACE_1);
        c.add(header(caption));
        c.add(node);
        return c;
    }

    private static Button hover(Button b) {
        return new Button(b.label(), b.variant(), null) {
            @Override
            public boolean isHovered() {
                return true;
            }
        };
    }

    private static Button pressed(Button b) {
        return new Button(b.label(), b.variant(), null) {
            @Override
            public boolean isPressed() {
                return true;
            }
        };
    }

    private static Button focused(Button b) {
        return new Button(b.label(), b.variant(), null) {
            @Override
            public boolean isFocused() {
                return true;
            }
        };
    }

    private static IconButton focused(IconButton b) {
        return new IconButton(b.icon(), null) {
            @Override
            public boolean isFocused() {
                return true;
            }
        };
    }

    private static Toggle hover(Toggle t) {
        return new Toggle(t.isOn(), null) {
            @Override
            public boolean isHovered() {
                return true;
            }
        };
    }

    private static Toggle focused(Toggle t) {
        return new Toggle(t.isOn(), null) {
            @Override
            public boolean isFocused() {
                return true;
            }
        };
    }

    private static Slider<Integer> focused(Slider<Integer> s) {
        Slider<Integer> copy = new Slider<>(s.min(), s.max(), s.step(), s.doubleValue()) {
            @Override
            public boolean isFocused() {
                return true;
            }
        };
        return copy;
    }

    private static TextField focused(TextField f) {
        return new TextField(f.text()) {
            @Override
            public boolean isFocused() {
                return true;
            }
        };
    }

    private static Select<String> focused(Select<String> s) {
        return new Select<>(s.options(), s.value()) {
            @Override
            public boolean isFocused() {
                return true;
            }
        };
    }

    private static Tabs focused(Tabs t) {
        return new Tabs(t.labels(), t.selected()) {
            @Override
            public boolean isFocused() {
                return true;
            }
        };
    }

    private static KeyCaptureField capturing(KeyCaptureField f) {
        return new KeyCaptureField(f.keyCode(), f.keyName(), null) {
            @Override
            public boolean isCapturing() {
                return true;
            }
        };
    }

    private static ColorField focused(ColorField f) {
        return new ColorField(f.color(), f.allowsAlpha(), null) {
            @Override
            public boolean isFocused() {
                return true;
            }
        };
    }

    /** Icon with its name underneath. */
    private static final class IconTile extends Column {
        IconTile(Icons icon) {
            super(Theme.SPACE_1);
            align(Align.CENTER);
            add(new IconGlyph(icon));
            add(new Label(icon.name().toLowerCase(Locale.ROOT).replace('_', ' '), Label.Variant.MUTED));
        }
    }

    private static final class IconGlyph extends UiNode {
        private static final int SIZE = 10;
        private final Icons icon;

        IconGlyph(Icons icon) {
            this.icon = icon;
        }

        @Override
        protected dev.vanta.core.ui.Size measure(UiContext ctx) {
            return new dev.vanta.core.ui.Size(SIZE + 8, SIZE + 8);
        }

        @Override
        protected void renderSelf(dev.vanta.core.ui.Canvas canvas, UiContext ctx) {
            dev.vanta.core.ui.Rect b = bounds();
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, ctx.theme().surface2());
            canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, ctx.theme().borderSubtle());
            icon.draw(canvas, b.x() + (b.w() - SIZE) / 2, b.y() + (b.h() - SIZE) / 2, SIZE, ctx.theme().textPrimary());
        }
    }
}
