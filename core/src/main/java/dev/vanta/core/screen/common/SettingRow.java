package dev.vanta.core.screen.common;

import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.keybinds.KeybindFormatter;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingKind;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.ui.AnimatedValue;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Easing;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.ColorField;
import dev.vanta.core.ui.widget.IconButton;
import dev.vanta.core.ui.widget.KeyCaptureField;
import dev.vanta.core.ui.widget.Select;
import dev.vanta.core.ui.widget.Slider;
import dev.vanta.core.ui.widget.TextField;
import dev.vanta.core.ui.widget.Toggle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * One setting as a row: title, one-line description (the full text is the tooltip), inline badges ("Vanilla",
 * "Restart required"), the editor widget chosen by {@link SettingKind} at the right, a reset-to-default icon button
 * that appears when the value differs from the default and a 2 px accent bar marking modified rows.
 * <p>
 * Values are read from and written to the {@link SettingsStore}, so vanilla-bound settings go straight through the
 * options bridge. {@link #refresh()} re-reads the value after an external change; {@link #flash(UiContext)} highlights
 * the row (used when the search overlay deep-links to it).
 */
public class SettingRow extends UiNode {
    /** Fixed row height. */
    public static final int HEIGHT = 32;
    /** Duration of the highlight flash. */
    public static final long FLASH_MS = 1600L;
    private static final int PAD_X = Theme.SPACE_4;
    private static final int INDICATOR_INSET = 6;
    private static final int RESET_SIZE = 16;
    private static final int GAP = Theme.SPACE_3;
    private static final int SLIDER_W = 150;
    private static final int BADGE_H = 10;
    private static final int BADGE_PAD = 3;

    private final Setting<?> setting;
    private final SettingsStore store;
    private final Consumer<String> actionHandler;
    private final UiNode editor;
    private final IconButton reset;
    private Runnable onChanged;
    private boolean last;
    private boolean refreshing;
    private long flashStart = -1L;
    private AnimatedValue hoverT;

    /**
     * @param setting       the setting to edit
     * @param store         value store
     * @param actionHandler receives the action id when an ACTION row's button is pressed
     */
    public SettingRow(Setting<?> setting, SettingsStore store, Consumer<String> actionHandler) {
        this.setting = Objects.requireNonNull(setting, "setting");
        this.store = Objects.requireNonNull(store, "store");
        this.actionHandler = actionHandler == null ? id -> { } : actionHandler;
        this.editor = createEditor();
        this.editor.setId("setting." + setting.id());
        add(editor);
        this.reset = new IconButton(Icons.RESET, this::resetToDefault).sizes(RESET_SIZE, 9);
        this.reset.setTooltip(Lang.tr("vanta.settings.reset_to_default"));
        this.reset.setId("setting." + setting.id() + ".reset");
        add(reset);
        setTooltip(Lang.tr(setting.descriptionKey()));
        setId("row." + setting.id());
    }

    /** The setting shown. */
    public Setting<?> setting() {
        return setting;
    }

    /** The editor widget (Toggle, Slider, Select, …). */
    public UiNode editor() {
        return editor;
    }

    /** The reset button. */
    public IconButton resetButton() {
        return reset;
    }

    /** Called after the user changed the value through this row. */
    public SettingRow onChanged(Runnable listener) {
        this.onChanged = listener;
        return this;
    }

    /** Suppresses the separator line (last row of a group). */
    public SettingRow last(boolean isLast) {
        this.last = isLast;
        return this;
    }

    /** Translated title. */
    public String title() {
        return Lang.tr(setting.titleKey());
    }

    /** Whether the current value equals the default. */
    public boolean isDefault() {
        return store.isDefault(setting);
    }

    /** Restores the default and refreshes the editor. */
    public void resetToDefault() {
        if (setting.kind() == SettingKind.ACTION) {
            return;
        }
        store.reset(setting);
        refresh();
        fireChanged();
    }

    /** Starts the highlight flash. */
    public void flash(UiContext ctx) {
        flashStart = ctx.now();
    }

    /** Whether the flash is still visible. */
    public boolean isFlashing(UiContext ctx) {
        return flashStart >= 0L && ctx.now() - flashStart < FLASH_MS;
    }

    // ---------------------------------------------------------------- editors

    @SuppressWarnings("unchecked")
    private UiNode createEditor() {
        return switch (setting.kind()) {
            case BOOL -> {
                Setting<Boolean> s = (Setting<Boolean>) setting;
                Toggle toggle = new Toggle(store.get(s), null);
                toggle.onChange(v -> write(s, v));
                yield toggle;
            }
            case INT_RANGE -> {
                Setting<Integer> s = (Setting<Integer>) setting;
                Slider<Integer> slider = Slider.ofInt((int) Math.round(s.min()), (int) Math.round(s.max()),
                        Math.max(1, (int) Math.round(s.step())), store.get(s));
                slider.formatter(v -> SettingFormats.format(s, v)).valueWidth(SettingFormats.valueLabelWidth(s));
                slider.onChange(v -> write(s, v));
                slider.width(SLIDER_W + SettingFormats.valueLabelWidth(s));
                yield slider;
            }
            case DOUBLE_RANGE -> {
                Setting<Double> s = (Setting<Double>) setting;
                Slider<Double> slider = Slider.ofDouble(s.min(), s.max(), s.step(), store.get(s));
                slider.formatter(v -> SettingFormats.format(s, v)).valueWidth(SettingFormats.valueLabelWidth(s));
                slider.onChange(v -> write(s, v));
                slider.width(SLIDER_W + SettingFormats.valueLabelWidth(s));
                yield slider;
            }
            case ENUM -> {
                Setting<Object> s = (Setting<Object>) setting;
                List<Object> options = new ArrayList<>(s.options());
                Select<Object> select = new Select<>(options, store.get(s), o -> SettingFormats.optionLabel(s, o));
                select.onChange(v -> write(s, v));
                yield select;
            }
            case COLOR -> {
                Setting<Integer> s = (Setting<Integer>) setting;
                ColorField field = new ColorField(store.get(s), true, null);
                field.onChange(v -> write(s, v));
                yield field;
            }
            case STRING -> {
                Setting<String> s = (Setting<String>) setting;
                TextField field = new TextField(store.get(s)).maxLength(Setting.MAX_STRING_LENGTH);
                field.onChange(v -> {
                    if (!refreshing) {
                        write(s, v);
                    }
                });
                field.width(140);
                yield field;
            }
            case KEY -> {
                Setting<String> s = (Setting<String>) setting;
                String name = store.get(s);
                KeyCaptureField field = new KeyCaptureField(-1, KeybindFormatter.humanize(name), null);
                field.capturePrompt(Lang.tr("vanta.keybinds.press_key"));
                field.onKey(code -> captureKey(s, field, code));
                field.width(96);
                yield field;
            }
            case ACTION -> {
                String actionId = setting.actionId().orElse("");
                Button button = Button.secondary(Lang.tr("vanta.action." + actionId), () -> actionHandler.accept(actionId));
                button.compact(true);
                yield button;
            }
        };
    }

    private <T> void write(Setting<T> s, T value) {
        if (refreshing) {
            return;
        }
        store.set(s, value);
        fireChanged();
    }

    private void captureKey(Setting<String> s, KeyCaptureField field, int code) {
        String name = code == Keys.BACKSPACE || code == Keys.DELETE ? KeyRef.UNKNOWN_NAME
                : KeybindFormatter.glfwKeyName(code);
        write(s, name);
        field.setKey(code, KeybindFormatter.humanize(store.get(s)));
    }

    private void fireChanged() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    /** Re-reads the value from the store and updates the editor without firing listeners. */
    @SuppressWarnings("unchecked")
    public void refresh() {
        refreshing = true;
        try {
            switch (setting.kind()) {
                case BOOL -> ((Toggle) editor).setOn(store.get((Setting<Boolean>) setting));
                case INT_RANGE -> ((Slider<Integer>) editor).setValue(store.get((Setting<Integer>) setting));
                case DOUBLE_RANGE -> ((Slider<Double>) editor).setValue(store.get((Setting<Double>) setting));
                case ENUM -> ((Select<Object>) editor).setValue(store.get((Setting<Object>) setting));
                case COLOR -> ((ColorField) editor).setColor(store.get((Setting<Integer>) setting));
                case STRING -> {
                    TextField field = (TextField) editor;
                    String value = store.get((Setting<String>) setting);
                    if (!field.text().equals(value)) {
                        field.setText(value);
                    }
                }
                case KEY -> {
                    String name = store.get((Setting<String>) setting);
                    ((KeyCaptureField) editor).setKey(-1, KeybindFormatter.humanize(name));
                }
                case ACTION -> {
                }
            }
        } finally {
            refreshing = false;
        }
    }

    // ---------------------------------------------------------------- layout & paint

    private boolean showsReset() {
        return setting.kind() != SettingKind.ACTION;
    }

    private List<String> badges() {
        List<String> out = new ArrayList<>(2);
        if (setting.isVanilla()) {
            out.add(Lang.tr("vanta.settings.vanilla_badge"));
        }
        if (setting.needsRestart()) {
            out.add(Lang.tr("vanta.settings.requires_restart"));
        }
        return out;
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(explicitWidth() > 0 ? explicitWidth() : 320, HEIGHT);
    }

    @Override
    public void layout(UiContext ctx) {
        Rect b = bounds();
        int right = b.right() - PAD_X;
        if (showsReset()) {
            reset.setBounds(right - RESET_SIZE, b.centerY() - RESET_SIZE / 2, RESET_SIZE, RESET_SIZE);
            right -= RESET_SIZE + GAP;
        }
        reset.setVisible(showsReset() && !isDefault());
        reset.layout(ctx);
        Size pref = editor.preferredSize(ctx);
        int maxW = Math.max(40, b.w() / 2);
        int w = Math.min(pref.w(), maxW);
        int h = Math.min(pref.h(), b.h() - 4);
        editor.setBounds(right - w, b.y() + (b.h() - h) / 2, w, h);
        editor.layout(ctx);
    }

    @Override
    public void render(Canvas canvas, UiContext ctx) {
        reset.setVisible(showsReset() && !isDefault());
        super.render(canvas, ctx);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        if (hoverT == null) {
            hoverT = ctx.animator().value(0f);
        }
        Theme theme = ctx.theme();
        Rect b = bounds();
        hoverT.animateTo(isHovered(), Theme.MOTION_FAST);
        float hover = hoverT.get();
        if (hover > 0f) {
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.surface3(), 0.55f * hover));
        }
        if (flashStart >= 0L) {
            float p = ctx.animator().progress(flashStart, FLASH_MS, Easing.OUT_CUBIC);
            if (p < 1f) {
                canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD,
                        Colors.withAlpha(theme.accent(), 0.30f * (1f - p)));
                canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD,
                        Colors.withAlpha(theme.accentHover(), 0.8f * (1f - p)));
            } else {
                flashStart = -1L;
            }
        }
        boolean modified = showsReset() && !isDefault();
        if (modified) {
            canvas.fillGradientV(b.x() + 2, b.y() + INDICATOR_INSET, 2, b.h() - INDICATOR_INSET * 2,
                    theme.gradientStart(), theme.gradientEnd());
        }
        boolean enabled = isEffectivelyEnabled();
        int textX = b.x() + PAD_X + 4;
        int textRight = editor.bounds().x() - GAP;
        int textW = Math.max(0, textRight - textX);
        int lhBold = canvas.lineHeight(FontKind.UI_BOLD);
        int lh = canvas.lineHeight(FontKind.UI);
        int titleY = b.y() + (b.h() - lhBold - lh - 1) / 2;
        String title = title();
        List<String> badges = badges();
        int badgesW = 0;
        for (String badge : badges) {
            badgesW += canvas.textWidth(badge, FontKind.UI) + BADGE_PAD * 2 + Theme.SPACE_2;
        }
        String shownTitle = canvas.textClipped(title, Math.max(0, textW - badgesW), FontKind.UI_BOLD);
        canvas.text(shownTitle, textX, titleY, enabled ? theme.textPrimary() : theme.textMuted(), FontKind.UI_BOLD,
                false);
        int bx = textX + canvas.textWidth(shownTitle, FontKind.UI_BOLD) + Theme.SPACE_2;
        for (String badge : badges) {
            int bw = canvas.textWidth(badge, FontKind.UI) + BADGE_PAD * 2;
            if (bx + bw > textRight) {
                break;
            }
            int by = titleY + (lhBold - BADGE_H) / 2;
            boolean restart = badge.equals(Lang.tr("vanta.settings.requires_restart"));
            int fg = restart ? theme.warning() : theme.textMuted();
            canvas.fillRounded(bx, by, bw, BADGE_H, Theme.RADIUS_SM, restart ? Colors.withAlpha(theme.warning(), 0.16f)
                    : theme.surface3());
            canvas.strokeRounded(bx, by, bw, BADGE_H, Theme.RADIUS_SM, restart ? Colors.withAlpha(theme.warning(), 0.5f)
                    : theme.borderStrong());
            canvas.text(badge, bx + BADGE_PAD, by + (BADGE_H - lh) / 2, fg, FontKind.UI, false);
            bx += bw + Theme.SPACE_2;
        }
        String description = Lang.tr(setting.descriptionKey());
        canvas.text(canvas.textClipped(description, textW, FontKind.UI), textX, titleY + lhBold + 1, theme.textMuted(),
                FontKind.UI, false);
        if (!last) {
            canvas.fill(b.x() + PAD_X, b.bottom() - 1, b.w() - PAD_X * 2, 1, Colors.withAlpha(theme.borderSubtle(), 0.7f));
        }
    }
}
