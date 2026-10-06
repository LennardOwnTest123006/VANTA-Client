package dev.vanta.core.notifications.render;

import dev.vanta.core.accessibility.AccessibilityState;
import dev.vanta.core.cosmetics.UiThemeDefinition;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.Notification;
import dev.vanta.core.notifications.NotificationCenter;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.notifications.NotificationPosition;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.AnimatedValue;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Easing;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.TextMetrics;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiEnvironment;
import dev.vanta.core.ui.UiHost;
import dev.vanta.core.ui.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Draws the {@link NotificationCenter}'s toasts in the configured screen corner
 * ({@code general.notificationsPosition}). Toasts slide in from the near edge and fade, stack towards the screen
 * centre (at most {@value NotificationCenter#MAX_VISIBLE} plus the ones still fading out), show a remaining-time
 * line and the icon of their kind, and ease to their new position when one above them disappears.
 * <p>
 * Driving it from the client: {@link #tick()} once per client tick (after {@code VantaServices.tick()}) and
 * {@link #render(Canvas, int, int, float)} once per frame after everything else — in a world from the HUD element,
 * on menus after the screen — so toasts are visible everywhere but drawn only once. Reduced motion snaps instead
 * of animating; reduced transparency makes the cards opaque.
 */
public final class NotificationOverlay {

    /** Distance from the screen edges. */
    public static final int MARGIN = 8;
    /** Vertical gap between toasts. */
    public static final int GAP = 6;
    /** Toast width. */
    public static final int WIDTH = Toast.WIDTH;
    /** Slide-in duration. */
    public static final long IN_MS = 240L;
    /** Fade-out duration. */
    public static final long OUT_MS = 200L;
    /** Duration of the re-stacking motion. */
    public static final long MOVE_MS = 220L;

    /** One toast on screen (visible or fading out). */
    public static final class Slot {
        private Notification notification;
        private final long shownAt;
        private long dismissedAt = -1L;
        private final Toast toast;
        private AnimatedValue y;

        Slot(Notification notification, long shownAt) {
            this.notification = notification;
            this.shownAt = shownAt;
            this.toast = new Toast(toastKind(notification.kind()), notification.title(), notification.body());
        }

        /** The notification shown. */
        public Notification notification() {
            return notification;
        }

        /** Overlay clock time when the toast appeared. */
        public long shownAt() {
            return shownAt;
        }

        /** Overlay clock time when the toast was dismissed, or -1 while it is visible. */
        public long dismissedAt() {
            return dismissedAt;
        }

        /** True while the toast fades out. */
        public boolean isExiting() {
            return dismissedAt >= 0L;
        }

        /** The toast node (bounds are those of the last render). */
        public Toast toast() {
            return toast;
        }
    }

    private final VantaServices services;
    private final NotificationCenter center;
    private final MetricsProxy metrics = new MetricsProxy();
    private final UiContext ctx;
    private final NotificationCenter.Listener listener;
    private final Runnable unlistenAccessibility;
    private final Runnable unlistenCosmetics;
    private final List<Slot> slots = new ArrayList<>();
    private Theme theme;
    private boolean themeDirty;

    /** Overlay for the services' notification centre, themed from the cosmetics and accessibility settings. */
    public NotificationOverlay(VantaServices services) {
        this.services = Objects.requireNonNull(services, "services");
        this.center = services.notifications();
        this.theme = buildTheme();
        dev.vanta.core.ui.Clock uiClock = () -> services.clock().millis();
        this.ctx = new UiContext(new UiEnvironment(UiHost.NONE, theme, uiClock, metrics, Lang::tr), () -> { });
        this.listener = new NotificationCenter.Listener() {
            @Override
            public void onShown(Notification notification) {
                addSlot(notification);
            }

            @Override
            public void onDismissed(Notification notification) {
                markDismissed(notification.id());
            }
        };
        center.addListener(listener);
        for (Notification visible : center.visible()) {
            addSlot(visible);
        }
        // The theme is rebuilt on the next tick after a change instead of being rebuilt and compared every tick.
        this.unlistenAccessibility = services.accessibility().listen(state -> themeDirty = true);
        this.unlistenCosmetics = services.cosmetics().onSelectionChanged(selection -> themeDirty = true);
        this.themeDirty = false;
    }

    /** Stops listening to the notification centre and the theme sources. */
    public void dispose() {
        center.removeListener(listener);
        unlistenAccessibility.run();
        unlistenCosmetics.run();
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    private long now() {
        return services.clock().millis();
    }

    private void addSlot(Notification notification) {
        for (Slot slot : slots) {
            if (slot.notification.id() == notification.id()) {
                slot.notification = notification;
                return;
            }
        }
        slots.add(new Slot(notification, now()));
    }

    private void markDismissed(long id) {
        for (Slot slot : slots) {
            if (slot.notification.id() == id && !slot.isExiting()) {
                slot.dismissedAt = now();
            }
        }
    }

    /** Pulls updates (progress, body) from the centre and drops toasts that finished fading out. */
    private void sync() {
        long now = now();
        List<Notification> visible = center.visible();
        for (Notification n : visible) {
            addSlot(n);
        }
        for (Slot slot : slots) {
            if (!slot.isExiting()) {
                boolean stillVisible = false;
                for (Notification n : visible) {
                    if (n.id() == slot.notification.id()) {
                        stillVisible = true;
                        break;
                    }
                }
                if (!stillVisible) {
                    slot.dismissedAt = now;
                }
            }
        }
        long out = theme.reducedMotion() ? 0L : OUT_MS;
        slots.removeIf(slot -> slot.isExiting() && now - slot.dismissedAt >= out);
    }

    /** Once per client tick: picks up a changed theme and prunes finished toasts. */
    public void tick() {
        if (themeDirty) {
            themeDirty = false;
            refreshTheme();
        }
        sync();
    }

    private Theme buildTheme() {
        UiThemeDefinition definition = services.cosmetics().activeTheme();
        AccessibilityState a = services.accessibility().state();
        return Theme.DEFAULT.withOverrides(definition.tokenOverrides())
                .withIdentity(definition.id(), definition.name())
                .withReducedMotion(a.reducedMotion())
                .withReducedTransparency(a.reducedTransparency())
                .withHighContrast(a.highContrast());
    }

    private void refreshTheme() {
        Theme next = buildTheme();
        if (!next.equals(theme)) {
            theme = next;
            ctx.setTheme(next);
        }
    }

    /** Toasts on screen, oldest first (fading ones included). */
    public List<Slot> slots() {
        return Collections.unmodifiableList(new ArrayList<>(slots));
    }

    /** Corner toasts appear in. */
    public NotificationPosition position() {
        return services.settings().get(VantaSettings.GENERAL_NOTIFICATIONS_POSITION);
    }

    /** The theme the overlay currently renders with. */
    public Theme theme() {
        return theme;
    }

    // ---- rendering -----------------------------------------------------------------------------------------------

    /** Draws every toast; {@code screenWidth}/{@code screenHeight} are the size of {@code canvas} in GUI pixels. */
    public void render(Canvas canvas, int screenWidth, int screenHeight, float deltaTicks) {
        Objects.requireNonNull(canvas, "canvas");
        metrics.set(canvas);
        ctx.setScreenSize(screenWidth, screenHeight);
        ctx.setDeltaTicks(deltaTicks);
        sync();
        if (slots.isEmpty()) {
            return;
        }
        NotificationPosition position = position();
        long now = now();
        boolean right = position.isRight();
        boolean top = position.isTop();
        int x = right ? screenWidth - MARGIN - WIDTH : MARGIN;
        int cursor = top ? MARGIN : screenHeight - MARGIN;
        int shown = 0;
        for (Slot slot : slots) {
            if (!slot.isExiting() && shown >= NotificationCenter.MAX_VISIBLE) {
                break;
            }
            Notification n = slot.notification;
            Toast toast = slot.toast;
            toast.title(n.title()).body(n.body()).kind(toastKind(n.kind())).icon(iconFor(n.iconId()));
            toast.progress(progressOf(n, now));
            toast.width(WIDTH);
            toast.setBounds(x, 0, WIDTH, 0);
            int h = toast.preferredSize(ctx).h();
            int targetY = top ? cursor : cursor - h;
            if (slot.y == null) {
                slot.y = ctx.animator().value((float) targetY);
            } else if (Math.round(slot.y.target()) != targetY) {
                slot.y.animateTo(targetY, MOVE_MS, Easing.STANDARD);
            }
            int y = Math.round(slot.y.get());
            float enter = ctx.animator().progress(slot.shownAt, IN_MS, Easing.OUT_CUBIC);
            float alpha = enter;
            float slide = (1f - enter) * (WIDTH + MARGIN);
            if (slot.isExiting()) {
                float exit = ctx.animator().progress(slot.dismissedAt, OUT_MS, Easing.IN_CUBIC);
                alpha = Math.min(alpha, 1f - exit);
                slide = Math.max(slide, exit * (WIDTH + MARGIN) * 0.5f);
            }
            if (alpha > 0.005f) {
                float previous = canvas.partialAlpha();
                canvas.setPartialAlpha(previous * alpha);
                canvas.pushTranslate(right ? slide : -slide, 0f);
                try {
                    toast.setBounds(x, y, WIDTH, h);
                    toast.layout(ctx);
                    toast.render(canvas, ctx);
                } finally {
                    canvas.pop();
                    canvas.setPartialAlpha(previous);
                }
            } else {
                toast.setBounds(x, y, WIDTH, h);
            }
            cursor = top ? cursor + h + GAP : cursor - h - GAP;
            if (!slot.isExiting()) {
                shown++;
            }
        }
    }

    /** Remaining-time fraction for the progress line, or the explicit progress of a progress toast. */
    static float progressOf(Notification n, long now) {
        if (n.progress().isPresent() && !n.isComplete()) {
            return (float) n.progress().getAsDouble();
        }
        if (n.isSticky()) {
            return -1f;
        }
        return (float) (1.0 - n.elapsedFraction(now));
    }

    /** Maps the notification severity to the toast widget's kind. */
    static Toast.Kind toastKind(NotificationKind kind) {
        return switch (kind) {
            case SUCCESS -> Toast.Kind.SUCCESS;
            case WARNING -> Toast.Kind.WARNING;
            case ERROR -> Toast.Kind.ERROR;
            default -> Toast.Kind.INFO;
        };
    }

    /** Resolves a notification icon id to an icon; unknown ids fall back to the kind's icon. */
    static Icons iconFor(String iconId) {
        if (iconId == null || iconId.isBlank()) {
            return null;
        }
        try {
            return Icons.valueOf(iconId.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Text metrics that delegate to the canvas of the current frame. */
    private static final class MetricsProxy implements TextMetrics {
        private TextMetrics delegate = TextMetrics.fixed(5);

        void set(TextMetrics m) {
            this.delegate = m;
        }

        @Override
        public int textWidth(String text, FontKind font) {
            return delegate.textWidth(text, font);
        }

        @Override
        public int lineHeight(FontKind font) {
            return delegate.lineHeight(font);
        }
    }
}
