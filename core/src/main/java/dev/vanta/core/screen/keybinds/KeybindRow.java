package dev.vanta.core.screen.keybinds;

import dev.vanta.core.bridge.KeyBinding;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.keybinds.ConflictDetector;
import dev.vanta.core.keybinds.KeybindFormatter;
import dev.vanta.core.keybinds.VantaKeys;
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
import dev.vanta.core.ui.widget.IconButton;
import dev.vanta.core.ui.widget.KeyCaptureField;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * One key mapping: name (with the VANTA description as tooltip for VANTA's own keys), a reset button when the
 * binding differs from its default, a conflict icon whose tooltip lists the other mappings on the same key, and the
 * {@link KeyCaptureField}. Pressing Backspace or Delete while capturing unbinds the key; Escape cancels.
 */
public final class KeybindRow extends UiNode {
    /** Row height. */
    public static final int HEIGHT = 22;
    /** Width of the key field. */
    public static final int FIELD_W = 84;
    private static final int ICON = 9;
    private static final int ICON_BOX = 14;

    private final KeyBinding binding;
    private final Optional<ConflictDetector.Conflict> conflict;
    private final KeyCaptureField field;
    private final IconButton reset;
    private final Rect[] conflictIconRect = {Rect.EMPTY};
    private final ConflictIcon conflictIcon;

    /**
     * @param binding  the mapping
     * @param conflict the conflict it is part of, if any
     * @param onKey    receives the mapping id and the new key ({@link KeyRef#UNBOUND} to unbind)
     * @param onReset  receives the mapping id when the reset button is pressed
     */
    public KeybindRow(KeyBinding binding, Optional<ConflictDetector.Conflict> conflict,
                      BiConsumer<String, KeyRef> onKey, Consumer<String> onReset) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.conflict = Objects.requireNonNull(conflict, "conflict");
        setId("keybind." + binding.id());
        if (VantaKeys.isVantaKey(binding.id()) && Lang.has(binding.id() + ".description")) {
            setTooltip(Lang.tr(binding.id() + ".description"));
        }
        field = new KeyCaptureField(binding.boundKeyCode(), KeybindFormatter.format(binding.boundKey()), code -> {
            if (code == Keys.BACKSPACE || code == Keys.DELETE) {
                onKey.accept(binding.id(), KeyRef.UNBOUND);
            } else {
                onKey.accept(binding.id(), KeyRef.keyboard(code, KeybindFormatter.glfwKeyName(code)));
            }
        });
        field.capturePrompt(Lang.tr("vanta.keybinds.press_key"));
        field.conflict(conflict.map(c -> c.severity() == ConflictDetector.Severity.HIGH).orElse(false));
        field.setTooltip(Lang.tr("vanta.keybinds.esc_to_cancel"));
        field.setId("keybind." + binding.id() + ".field");
        add(field);
        reset = new IconButton(Icons.RESET, () -> onReset.accept(binding.id())).sizes(ICON_BOX + 2, ICON);
        reset.setTooltip(Lang.tr("vanta.keybinds.default_key", defaultLabel()));
        reset.setVisible(!binding.isDefault());
        reset.setId("keybind." + binding.id() + ".reset");
        add(reset);
        conflictIcon = new ConflictIcon();
        conflictIcon.setVisible(conflict.isPresent());
        add(conflictIcon);
    }

    private String defaultLabel() {
        // The bridge only reports whether the binding is default; the default key itself is known for VANTA's keys.
        return VantaKeys.find(binding.id()).map(d -> KeybindFormatter.format(d.defaultKey()))
                .orElse(Lang.tr("vanta.common.default"));
    }

    /** The mapping shown. */
    public KeyBinding binding() {
        return binding;
    }

    public KeyCaptureField field() {
        return field;
    }

    public IconButton resetButton() {
        return reset;
    }

    /** Conflict of this mapping, if any. */
    public Optional<ConflictDetector.Conflict> conflict() {
        return conflict;
    }

    /** Translated tooltip of the conflict icon: the other mappings bound to the same key. */
    public String conflictText() {
        if (conflict.isEmpty()) {
            return "";
        }
        List<String> others = new ArrayList<>();
        for (KeyBinding other : conflict.get().bindings()) {
            if (!other.id().equals(binding.id())) {
                others.add(other.displayName());
            }
        }
        String severity = Lang.tr(conflict.get().severity() == ConflictDetector.Severity.HIGH
                ? "vanta.keybinds.severity.high" : "vanta.keybinds.severity.medium");
        return severity + ": " + Lang.tr("vanta.keybinds.conflict_with", String.join(", ", others));
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(240, HEIGHT);
    }

    @Override
    public void layout(UiContext ctx) {
        Rect b = bounds();
        int x = b.right();
        x -= FIELD_W;
        field.setBounds(x, b.y() + (b.h() - KeyCaptureField.HEIGHT) / 2, FIELD_W, KeyCaptureField.HEIGHT);
        field.layout(ctx);
        x -= Theme.SPACE_2;
        if (conflictIcon.isVisible()) {
            x -= ICON_BOX;
            conflictIcon.setBounds(x, b.y() + (b.h() - ICON_BOX) / 2, ICON_BOX, ICON_BOX);
            conflictIconRect[0] = conflictIcon.bounds();
            x -= Theme.SPACE_1;
        }
        if (reset.isVisible()) {
            x -= ICON_BOX + 2;
            reset.setBounds(x, b.y() + (b.h() - ICON_BOX - 2) / 2, ICON_BOX + 2, ICON_BOX + 2);
            reset.layout(ctx);
        }
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        boolean hot = isHovered();
        if (hot) {
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_SM, Colors.withAlpha(theme.surface3(), 0.7f));
        }
        int rightMost = field.bounds().x();
        if (conflictIcon.isVisible()) {
            rightMost = conflictIcon.bounds().x();
        }
        if (reset.isVisible()) {
            rightMost = reset.bounds().x();
        }
        int textX = b.x() + Theme.SPACE_3;
        int maxW = Math.max(0, rightMost - Theme.SPACE_3 - textX);
        int lh = canvas.lineHeight(FontKind.UI);
        int color = binding.isDefault() ? theme.textPrimary() : theme.accentHover();
        canvas.text(canvas.textClipped(binding.displayName(), maxW, FontKind.UI), textX, b.y() + (b.h() - lh) / 2,
                color, FontKind.UI, false);
    }

    /** The conflict indicator with its own tooltip. */
    private final class ConflictIcon extends UiNode {
        ConflictIcon() {
            setTooltip(conflictText());
        }

        @Override
        protected Size measure(UiContext ctx) {
            return new Size(ICON_BOX, ICON_BOX);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect r = bounds();
            boolean high = conflict.map(c -> c.severity() == ConflictDetector.Severity.HIGH).orElse(false);
            int color = high ? theme.danger() : theme.warning();
            if (isHovered()) {
                canvas.fillRounded(r.x(), r.y(), r.w(), r.h(), Theme.RADIUS_SM, Colors.withAlpha(color, 0.18f));
            }
            Icons.WARNING.draw(canvas, r.x() + (r.w() - ICON) / 2, r.y() + (r.h() - ICON) / 2, ICON, color);
        }
    }
}
