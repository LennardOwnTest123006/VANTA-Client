package dev.vanta.core.screen.profiles;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.screen.stats.StatFormats;
import dev.vanta.core.ui.AnimatedValue;
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
import dev.vanta.core.ui.widget.Badge;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.IconButton;
import dev.vanta.core.ui.widget.TextField;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * One profile in the grid: icon tile, name (editable inline), "Active" / "Built-in" badges, description, a 2x2
 * summary (HUD preset, performance preset, crosshair, last update) and the action row (Activate, Duplicate, Rename,
 * Export, Reset for built-ins, Delete). Every action is reported to the {@link Listener}; the card never touches
 * the stores itself.
 */
public final class ProfileCard extends UiNode {

    /** Receives the card actions. */
    public interface Listener {
        void activate(String profileId);

        void duplicate(String profileId);

        void rename(String profileId, String newName);

        void export(String profileId);

        void reset(String profileId);

        void delete(String profileId);
    }

    /** Card height (fixed so grid rows line up). */
    public static final int HEIGHT = 108;
    private static final int PAD = Theme.SPACE_4;
    private static final int TILE = 22;
    private static final int ICON = 11;
    private static final int LINE = 11;

    private final ProfileSummary summary;
    private final boolean active;
    private final boolean deletable;
    private final Listener listener;
    private final Function<String, Optional<String>> nameValidator;
    private final ZoneId zone;
    private final long now;
    private final Badge activeBadge;
    private final Badge builtInBadge;
    private final Button activateButton;
    private final IconButton duplicateButton;
    private final IconButton renameButton;
    private final IconButton exportButton;
    private final IconButton resetButton;
    private final IconButton deleteButton;
    private final TextField nameField;
    private boolean renaming;
    private String renameError;
    private AnimatedValue hoverT;
    private UiContext lastContext;

    /**
     * @param summary       the profile and its derived facts
     * @param active        whether this is the active profile
     * @param deletable     false when this is the last profile
     * @param nameValidator returns the translation key of a problem with a proposed name, or empty
     * @param zone          time zone for the "updated" column
     * @param now           current time for relative dates
     * @param listener      action sink
     */
    public ProfileCard(ProfileSummary summary, boolean active, boolean deletable,
                       Function<String, Optional<String>> nameValidator, ZoneId zone, long now, Listener listener) {
        this.summary = Objects.requireNonNull(summary, "summary");
        this.active = active;
        this.deletable = deletable;
        this.nameValidator = Objects.requireNonNull(nameValidator, "nameValidator");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.now = now;
        this.listener = Objects.requireNonNull(listener, "listener");
        String id = profile().id();
        setId("profile." + id);

        activeBadge = new Badge(Lang.tr("vanta.profiles.active"), Badge.Tone.SUCCESS);
        activeBadge.setVisible(active);
        add(activeBadge);
        builtInBadge = new Badge(Lang.tr("vanta.profiles.builtin"), Badge.Tone.NEUTRAL);
        builtInBadge.setVisible(summary.builtIn());
        add(builtInBadge);

        activateButton = Button.primary(Lang.tr("vanta.profiles.activate"), () -> listener.activate(id)).compact(true);
        activateButton.setId("profile." + id + ".activate");
        activateButton.setVisible(!active);
        add(activateButton);

        duplicateButton = iconButton(Icons.COPY, "vanta.profiles.duplicate", id + ".duplicate",
                () -> listener.duplicate(id));
        renameButton = iconButton(Icons.EDIT, "vanta.profiles.rename", id + ".rename", () -> {
            if (lastContext != null) {
                startRename(lastContext);
            }
        });
        exportButton = iconButton(Icons.DOWNLOAD, "vanta.profiles.export", id + ".export", () -> listener.export(id));
        resetButton = iconButton(Icons.RESET, "vanta.profiles.reset_builtin", id + ".reset", () -> listener.reset(id));
        resetButton.setVisible(summary.builtIn());
        deleteButton = iconButton(Icons.TRASH, "vanta.profiles.delete", id + ".delete", () -> listener.delete(id));
        if (!deletable) {
            deleteButton.setEnabled(false);
            deleteButton.setTooltip(Lang.tr("vanta.profiles.cannot_delete_last"));
        }

        nameField = new TextField(profile().name()) {
            @Override
            protected void onFocusLost(UiContext ctx) {
                super.onFocusLost(ctx);
                if (renaming) {
                    commitRename();
                }
            }
        };
        nameField.maxLength(Profile.MAX_NAME).placeholder(Lang.tr("vanta.profiles.new_name"))
                .validator(name -> nameValidator.apply(name).isEmpty())
                .onChange(name -> renameError = nameValidator.apply(name).map(Lang::tr).orElse(null))
                .onSubmit(name -> commitRename());
        nameField.setId("profile." + id + ".name");
        nameField.setVisible(false);
        add(nameField);
    }

    private IconButton iconButton(Icons icon, String tooltipKey, String idSuffix, Runnable action) {
        IconButton button = new IconButton(icon, action).sizes(20, 10);
        button.setTooltip(Lang.tr(tooltipKey));
        button.setId("profile." + idSuffix);
        add(button);
        return button;
    }

    /** The profile shown. */
    public Profile profile() {
        return summary.profile();
    }

    public ProfileSummary summary() {
        return summary;
    }

    public boolean isActiveProfile() {
        return active;
    }

    public boolean isRenaming() {
        return renaming;
    }

    /** The inline name field (visible while renaming). */
    public TextField nameField() {
        return nameField;
    }

    public Button activateButton() {
        return activateButton;
    }

    public IconButton renameButton() {
        return renameButton;
    }

    public IconButton deleteButton() {
        return deleteButton;
    }

    public IconButton resetButton() {
        return resetButton;
    }

    /** Shows the inline name field with the current name selected. */
    public void startRename(UiContext ctx) {
        renaming = true;
        renameError = null;
        nameField.setText(profile().name());
        nameField.setVisible(true);
        nameField.selectAll();
        layout(ctx);
        nameField.requestFocus(ctx);
    }

    /** Leaves rename mode without changing anything. */
    public void cancelRename() {
        renaming = false;
        renameError = null;
        nameField.setVisible(false);
    }

    /** Applies the typed name when it is valid and changed; otherwise just leaves rename mode. */
    public void commitRename() {
        if (!renaming) {
            return;
        }
        String typed = nameField.text().trim();
        boolean valid = nameValidator.apply(typed).isEmpty();
        cancelRename();
        if (valid && !typed.equals(profile().name())) {
            listener.rename(profile().id(), typed);
        }
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(200, HEIGHT);
    }

    private Rect headerRect() {
        Rect b = bounds();
        return new Rect(b.x() + PAD, b.y() + PAD, b.w() - PAD * 2, TILE);
    }

    private int summaryTop() {
        return bounds().y() + PAD + TILE + Theme.SPACE_2 + LINE + Theme.SPACE_3;
    }

    private Rect actionsRect() {
        Rect b = bounds();
        return new Rect(b.x() + PAD, b.bottom() - PAD - Button.HEIGHT, b.w() - PAD * 2, Button.HEIGHT);
    }

    @Override
    public void layout(UiContext ctx) {
        lastContext = ctx;
        Rect header = headerRect();
        int x = header.right();
        for (Badge badge : new Badge[] {builtInBadge, activeBadge}) {
            if (!badge.isVisible()) {
                continue;
            }
            Size s = badge.preferredSize(ctx);
            x -= s.w();
            badge.setBounds(x, header.y() + (TILE - s.h()) / 2, s.w(), s.h());
            badge.layout(ctx);
            x -= Theme.SPACE_2;
        }
        int nameLeft = header.x() + TILE + Theme.SPACE_3;
        nameField.setBounds(nameLeft - Theme.SPACE_2, header.y() + 1, Math.max(40, x - nameLeft + Theme.SPACE_2),
                TILE - 2);
        nameField.layout(ctx);

        Rect actions = actionsRect();
        int ax = actions.x();
        if (activateButton.isVisible()) {
            Size s = activateButton.preferredSize(ctx);
            activateButton.setBounds(ax, actions.y(), s.w(), s.h());
            activateButton.layout(ctx);
            ax += s.w() + Theme.SPACE_3;
        }
        IconButton[] icons = {duplicateButton, renameButton, exportButton, resetButton, deleteButton};
        int rx = actions.right();
        for (int i = icons.length - 1; i >= 0; i--) {
            IconButton b = icons[i];
            if (!b.isVisible()) {
                continue;
            }
            rx -= IconButton.SIZE;
            b.setBounds(rx, actions.y(), IconButton.SIZE, IconButton.SIZE);
            b.layout(ctx);
            rx -= Theme.SPACE_1;
        }
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        if (hoverT == null) {
            hoverT = ctx.animator().value(0f);
        }
        hoverT.animateTo(isHovered(), Theme.MOTION_FAST);
        float hover = hoverT.get();
        Theme theme = ctx.theme();
        Rect b = bounds();
        canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG,
                Colors.lerp(theme.panelBackground(), Colors.withAlpha(theme.surface2(), theme.panelAlpha()), hover));
        int border = active ? Colors.withAlpha(theme.accent(), 0.75f)
                : Colors.lerp(theme.borderSubtle(), theme.borderStrong(), hover);
        canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, border);
        if (active) {
            canvas.fillGradientH(b.x() + Theme.RADIUS_LG, b.y(), b.w() - Theme.RADIUS_LG * 2, 1, theme.gradientStart(),
                    theme.gradientEnd());
        }

        Rect header = headerRect();
        int tileBg = active ? Colors.withAlpha(theme.accent(), 0.22f) : theme.surface3();
        canvas.fillRounded(header.x(), header.y(), TILE, TILE, Theme.RADIUS_MD, tileBg);
        canvas.strokeRounded(header.x(), header.y(), TILE, TILE, Theme.RADIUS_MD,
                active ? Colors.withAlpha(theme.accent(), 0.5f) : theme.borderStrong());
        summary.icon().draw(canvas, header.x() + (TILE - ICON) / 2, header.y() + (TILE - ICON) / 2, ICON,
                active ? theme.accentHover() : theme.textSecondary());

        int textX = header.x() + TILE + Theme.SPACE_3;
        int badgesLeft = header.right();
        for (Badge badge : new Badge[] {builtInBadge, activeBadge}) {
            if (badge.isVisible()) {
                badgesLeft = Math.min(badgesLeft, badge.bounds().x());
            }
        }
        int nameW = Math.max(0, badgesLeft - Theme.SPACE_3 - textX);
        if (!renaming) {
            int nameY = header.y() + (TILE - canvas.lineHeight(FontKind.UI_BOLD)) / 2;
            canvas.text(canvas.textClipped(profile().name(), nameW, FontKind.UI_BOLD), textX, nameY, theme.textPrimary(),
                    FontKind.UI_BOLD, false);
        }
        int descY = header.bottom() + Theme.SPACE_2;
        if (renaming && renameError != null) {
            canvas.text(canvas.textClipped(renameError, header.w(), FontKind.UI), header.x(), descY, theme.danger(),
                    FontKind.UI, false);
        } else {
            canvas.text(canvas.textClipped(summary.description(), header.w(), FontKind.UI), header.x(), descY,
                    theme.textMuted(), FontKind.UI, false);
        }

        int top = summaryTop();
        int colW = header.w() / 2;
        drawFact(canvas, theme, header.x(), top, colW - Theme.SPACE_3, Lang.tr("vanta.profiles.summary.hud"),
                summary.hudPreset());
        drawFact(canvas, theme, header.x() + colW, top, colW, Lang.tr("vanta.profiles.summary.performance"),
                summary.perfPreset());
        drawFact(canvas, theme, header.x(), top + LINE, colW - Theme.SPACE_3,
                Lang.tr("vanta.profiles.summary.crosshair"), summary.crosshair());
        drawFact(canvas, theme, header.x() + colW, top + LINE, colW, Lang.tr("vanta.profiles.summary.updated"),
                StatFormats.relative(profile().updatedAt(), now, zone));
        if (active && !activateButton.isVisible()) {
            Rect actions = actionsRect();
            String label = Lang.tr("vanta.profiles.active_hint");
            int maxW = Math.max(0, deleteButton.bounds().x() - IconButton.SIZE * 4 - Theme.SPACE_4 - actions.x());
            canvas.text(canvas.textClipped(label, maxW, FontKind.UI), actions.x(),
                    actions.y() + (actions.h() - canvas.lineHeight(FontKind.UI)) / 2, theme.textMuted(), FontKind.UI,
                    false);
        }
    }

    private static void drawFact(Canvas canvas, Theme theme, int x, int y, int w, String label, String value) {
        int labelW = canvas.textWidth(label, FontKind.UI);
        canvas.text(label, x, y, theme.textMuted(), FontKind.UI, false);
        int vx = x + labelW + Theme.SPACE_2;
        canvas.text(canvas.textClipped(value, Math.max(0, x + w - vx), FontKind.UI), vx, y, theme.textSecondary(),
                FontKind.UI, false);
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (renaming && key == Keys.ESCAPE) {
            cancelRename();
            renameButton.requestFocus(ctx);
            return true;
        }
        return false;
    }
}
