package dev.vanta.core.hud.editor;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The HUD editor. The game screen is shown live (sample data outside a world) with every widget drawn by the real
 * renderers; widgets are dragged, resized through their handles, toggled in the widget list and styled in the
 * inspector. Presets, undo / redo, grid and snapping sit in the toolbar.
 * <p>
 * Keyboard: arrows nudge one pixel (Shift: ten), Delete hides the selection (Shift+Delete removes it), Ctrl+D
 * duplicates, Ctrl+Z / Ctrl+Y undo and redo, Ctrl+S saves, Escape saves and closes.
 * <p>
 * Below {@value #COMPACT_WIDTH}x{@value #COMPACT_HEIGHT} logical pixels (427x240 at GUI scale 3) the side panels
 * collapse into toolbar toggles and float over the preview when opened; the preview itself is always shown whole,
 * scaled down when panels take space.
 */
public final class HudEditorScreen extends UiScreen {

    /** Logical width below which the panels collapse. */
    public static final int COMPACT_WIDTH = 560;
    /** Logical height below which the panels collapse. */
    public static final int COMPACT_HEIGHT = 300;
    /** Footer height. */
    public static final int FOOTER_H = 14;
    private static final int MARGIN = Theme.SPACE_3;

    private final VantaServices services;
    private final HudEditorSession session;
    private EditorRoot root;
    private EditorCanvas canvasArea;
    private WidgetListPanel widgetList;
    private InspectorPanel inspector;
    private EditorToolbar toolbar;
    private boolean widgetListOpen = true;
    private boolean inspectorOpen = true;
    private Boolean lastCompact;
    private boolean saved;
    private Consumer<HudEditorScreen> initialAction;

    /** Opens the editor on the live layout. */
    public HudEditorScreen(VantaServices services) {
        this.services = Objects.requireNonNull(services, "services");
        this.session = new HudEditorSession(services, 1, 1);
    }

    /**
     * Runs {@code action} once the screen is built and laid out (previews and game tests use it to select a widget
     * or open a panel before the first frame).
     */
    public HudEditorScreen withInitialAction(Consumer<HudEditorScreen> action) {
        this.initialAction = action;
        return this;
    }

    /** The editing session (model, renderer, store glue). */
    public HudEditorSession session() {
        return session;
    }

    /** The editing surface. */
    public EditorCanvas canvasArea() {
        return canvasArea;
    }

    public WidgetListPanel widgetList() {
        return widgetList;
    }

    public InspectorPanel inspector() {
        return inspector;
    }

    public EditorToolbar toolbar() {
        return toolbar;
    }

    /** Game screen width in GUI pixels (the layout's coordinate space). */
    public int hostWidth() {
        return Math.max(1, Math.round(width() * effectiveScale()));
    }

    /** Game screen height in GUI pixels. */
    public int hostHeight() {
        return Math.max(1, Math.round(height() * effectiveScale()));
    }

    /** Whether the compact arrangement is in use. */
    public boolean isCompact() {
        return width() < COMPACT_WIDTH || height() < COMPACT_HEIGHT;
    }

    public boolean isWidgetListOpen() {
        return widgetListOpen;
    }

    public boolean isInspectorOpen() {
        return inspectorOpen;
    }

    /** Shows or hides the widget list. */
    public void toggleWidgetList() {
        widgetListOpen = !widgetListOpen;
        toolbar.refresh();
        invalidateLayout();
    }

    /** Shows or hides the inspector. */
    public void toggleInspector() {
        inspectorOpen = !inspectorOpen;
        toolbar.refresh();
        invalidateLayout();
    }

    // ---- lifecycle -----------------------------------------------------------------------------------------------

    @Override
    public String title() {
        return Lang.tr("vanta.hud.editor.title");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean wantsBlur() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected UiNode build(UiContext ctx) {
        session.resize(hostWidth(), hostHeight());
        canvasArea = new EditorCanvas(session);
        widgetList = new WidgetListPanel(session);
        inspector = new InspectorPanel(session);
        toolbar = new EditorToolbar(this, session);
        root = new EditorRoot();
        root.add(canvasArea);
        root.add(widgetList);
        root.add(inspector);
        root.add(toolbar);
        session.model().onChange(() -> {
            widgetList.markDirty();
            inspector.markDirty();
            toolbar.refresh();
            invalidateLayout();
        });
        return root;
    }

    @Override
    protected void onInit() {
        services.labEffects().onScreenOpened(context().now());
        session.tick();
        if (initialAction != null) {
            Consumer<HudEditorScreen> action = initialAction;
            initialAction = null;
            action.accept(this);
        }
    }

    @Override
    protected void onResize() {
        session.resize(hostWidth(), hostHeight());
    }

    @Override
    protected void onCloseStarted(long now) {
        services.labEffects().onScreenClosed(now);
    }

    @Override
    protected float slideProgress(long now) {
        return services.labEffects().transitionProgress(now);
    }

    @Override
    protected void onTick() {
        session.tick();
        toolbar.refresh();
    }

    @Override
    public void onClose() {
        save();
    }

    /** Saves the layout (when changed) and shows the toast. */
    public boolean save() {
        boolean written = session.save();
        saved = true;
        return written;
    }

    /** Saves and closes. */
    public void done() {
        save();
        close();
    }

    /** Opens the "Save as preset" dialog. */
    public void openSavePresetDialog() {
        UiContext ctx = context();
        PresetNameDialog.open(ctx, name -> {
            session.savePreset(name);
            toolbar.refresh();
        });
    }

    /** Whether {@link #save()} ran (for tests). */
    public boolean wasSaved() {
        return saved;
    }

    // ---- rendering -------------------------------------------------------------------------------------------------

    @Override
    protected void renderBackground(Canvas canvas, UiContext context) {
        Theme theme = context.theme();
        float alpha = theme.reducedTransparency() ? 1f : (session.isLive() ? 0.45f : 0.72f);
        canvas.fill(0, 0, canvas.width(), canvas.height(), Colors.withAlpha(theme.bgVoid(), alpha));
    }

    // ---- input -----------------------------------------------------------------------------------------------------

    @Override
    protected boolean onKeyDown(int key, int scancode, int mods) {
        boolean ctrl = Keys.hasControl(mods);
        boolean shift = Keys.hasShift(mods);
        switch (key) {
            case Keys.ESCAPE -> {
                done();
                return true;
            }
            case Keys.Z -> {
                if (ctrl) {
                    if (shift) {
                        session.redo();
                    } else {
                        session.undo();
                    }
                    return true;
                }
            }
            case Keys.Y -> {
                if (ctrl) {
                    session.redo();
                    return true;
                }
            }
            case Keys.D -> {
                if (ctrl) {
                    session.duplicate();
                    return true;
                }
            }
            case Keys.S -> {
                if (ctrl) {
                    save();
                    return true;
                }
            }
            case Keys.LEFT -> {
                return session.nudge(-1, 0, shift);
            }
            case Keys.RIGHT -> {
                return session.nudge(1, 0, shift);
            }
            case Keys.UP -> {
                return session.nudge(0, -1, shift);
            }
            case Keys.DOWN -> {
                return session.nudge(0, 1, shift);
            }
            case Keys.DELETE, Keys.BACKSPACE -> {
                if (shift) {
                    return session.removeSelected();
                }
                return session.setSelectedEnabled(false);
            }
            default -> {
            }
        }
        return false;
    }

    // ---- root layout -----------------------------------------------------------------------------------------------

    /** Positions toolbar, panels and the canvas; draws the footer. */
    private final class EditorRoot extends UiNode {

        @Override
        public void layout(UiContext ctx) {
            Rect b = bounds();
            boolean compact = isCompact();
            if (lastCompact == null || lastCompact != compact) {
                widgetListOpen = !compact;
                inspectorOpen = !compact;
                lastCompact = compact;
                toolbar.refresh();
            }
            toolbar.setCompact(compact);
            int barH = toolbar.barHeightFor(ctx, b.w());
            toolbar.setBounds(b.x(), b.y(), b.w(), barH);
            toolbar.layout(ctx);
            int top = b.y() + barH;
            int bottom = b.bottom() - FOOTER_H;
            int left = b.x();
            int right = b.right();
            widgetList.setVisible(widgetListOpen);
            if (widgetListOpen) {
                widgetList.setBounds(left + MARGIN, top + MARGIN, WidgetListPanel.WIDTH,
                        Math.max(0, bottom - top - MARGIN * 2));
                widgetList.layout(ctx);
                left += WidgetListPanel.WIDTH + MARGIN;
            }
            inspector.setVisible(inspectorOpen);
            if (inspectorOpen) {
                inspector.setBounds(right - MARGIN - InspectorPanel.WIDTH, top + MARGIN, InspectorPanel.WIDTH,
                        Math.max(0, bottom - top - MARGIN * 2));
                inspector.layout(ctx);
                right -= InspectorPanel.WIDTH + MARGIN;
            }
            canvasArea.setBounds(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
            canvasArea.layout(ctx);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            Rect footer = new Rect(b.x(), b.bottom() - FOOTER_H, b.w(), FOOTER_H);
            canvas.fill(footer.x(), footer.y(), footer.w(), footer.h(), Colors.withAlpha(theme.surface1(), theme.panelAlpha()));
            canvas.fill(footer.x(), footer.y(), footer.w(), 1, theme.borderSubtle());
            int textY = footer.y() + (FOOTER_H - canvas.lineHeight(FontKind.UI)) / 2 + 1;
            String info = Lang.tr("vanta.hud.editor.screen_size", session.screenWidth(), session.screenHeight())
                    + "  ·  " + Lang.tr("vanta.hud.editor.widgets_count", session.widgetCount());
            String source = Lang.tr(session.isLive() ? "vanta.hud.editor.live_data" : "vanta.hud.editor.sample_data");
            int sourceW = canvas.textWidth(source, FontKind.UI_BOLD);
            int infoW = canvas.textWidth(info, FontKind.UI);
            int rightX = footer.right() - Theme.SPACE_4;
            int sourceColor = session.isLive() ? theme.success() : theme.warning();
            canvas.text(source, rightX - sourceW, textY, sourceColor, FontKind.UI_BOLD, false);
            canvas.fillRounded(rightX - sourceW - Theme.SPACE_3 - 4, footer.y() + (FOOTER_H - 4) / 2, 4, 4, 2, sourceColor);
            int infoRight = rightX - sourceW - Theme.SPACE_3 - 4 - Theme.SPACE_4;
            canvas.text(info, infoRight - infoW, textY, theme.textMuted(), FontKind.UI, false);
            String hint = Lang.tr(isCompact() ? "vanta.hud.editor.hint_compact" : "vanta.hud.editor.hint");
            int hintW = Math.max(0, infoRight - infoW - Theme.SPACE_5 - (footer.x() + Theme.SPACE_4));
            canvas.text(canvas.textClipped(hint, hintW, FontKind.UI), footer.x() + Theme.SPACE_4, textY,
                    theme.textSecondary(), FontKind.UI, false);
        }
    }
}
