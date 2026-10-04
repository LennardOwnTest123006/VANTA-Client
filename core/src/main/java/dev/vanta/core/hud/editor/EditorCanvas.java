package dev.vanta.core.hud.editor;

import dev.vanta.core.hud.HudEditorModel;
import dev.vanta.core.hud.HudRect;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudRenderer;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The editing surface of the HUD editor: shows the game screen (through an {@link EditorViewport}) with every
 * widget drawn live, the optional grid, hover and selection outlines, resize handles and snap guides, and feeds
 * mouse input to the {@link HudEditorModel}.
 * <p>
 * Left button: select / drag / resize. Right button: toggle the visibility of the widget under the cursor.
 */
public final class EditorCanvas extends UiNode {

    /** Padding between the canvas bounds and the drawn screen. */
    public static final int PAD = 6;
    /** Grid line spacing in host pixels. */
    public static final int GRID_STEP = 16;
    /** Alpha used for hidden widgets. */
    public static final float HIDDEN_ALPHA = 0.35f;
    private static final int HANDLE = 5;
    private static final int TAG_H = 11;

    private final HudEditorSession session;
    private EditorViewport viewport;
    private boolean dragging;

    /** Canvas for a session. */
    public EditorCanvas(HudEditorSession session) {
        this.session = Objects.requireNonNull(session, "session");
        this.viewport = EditorViewport.fit(new Rect(0, 0, 1, 1), session.screenWidth(), session.screenHeight());
        setId("hud.canvas");
    }

    /** Current mapping between the game screen and this node. */
    public EditorViewport viewport() {
        return viewport;
    }

    @Override
    public void layout(UiContext ctx) {
        viewport = EditorViewport.fit(bounds().inset(PAD), session.screenWidth(), session.screenHeight());
        super.layout(ctx);
    }

    // ---- rendering ---------------------------------------------------------------------------------------------------

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        HudEditorModel model = session.model();
        Rect vp = viewport.area();
        updateHover(ctx);

        if (!session.isLive()) {
            drawSampleBackdrop(canvas, theme, vp);
        }
        canvas.strokeRect(vp.x() - 1, vp.y() - 1, vp.w() + 2, vp.h() + 2, 1, theme.borderStrong());
        if (session.gridVisible()) {
            drawGrid(canvas, theme, vp);
        }

        HudRenderer renderer = session.renderer();
        HudData data = renderer.data();
        Map<String, HudRect> rects = model.rects();
        canvas.pushScissor(vp);
        canvas.pushTranslate(vp.x(), vp.y());
        canvas.pushScale(viewport.scale(), viewport.scale());
        try {
            for (HudWidgetState widget : model.layout().widgets()) {
                HudRect rect = rects.get(widget.id());
                if (rect == null) {
                    continue;
                }
                renderer.renderWidget(canvas, widget, rect, data, widget.enabled() ? 1f : HIDDEN_ALPHA, 1.0);
            }
        } finally {
            canvas.pop();
            canvas.pop();
            canvas.popScissor();
        }

        for (HudWidgetState widget : model.layout().widgets()) {
            if (!widget.enabled()) {
                HudRect rect = rects.get(widget.id());
                if (rect != null) {
                    dashedRect(canvas, viewport.toLogical(rect), Colors.withAlpha(theme.textSecondary(), 0.55f));
                }
            }
        }

        Optional<String> hover = model.hoverId();
        Optional<String> selected = model.selectedId();
        if (hover.isPresent() && !hover.equals(selected) && !model.isDragging()) {
            HudRect rect = rects.get(hover.get());
            if (rect != null) {
                Rect r = viewport.toLogical(rect).expand(1);
                canvas.strokeRect(r.x(), r.y(), r.w(), r.h(), 1, Colors.withAlpha(theme.textPrimary(), 0.45f));
            }
        }
        for (HudEditorModel.SnapGuide guide : model.guides()) {
            int c = Colors.withAlpha(theme.accentBlue(), 0.85f);
            if (guide.vertical()) {
                int x = Math.min(vp.right() - 1, viewport.toLogicalX(guide.position()));
                canvas.fill(x, vp.y(), 1, vp.h(), c);
            } else {
                int y = Math.min(vp.bottom() - 1, viewport.toLogicalY(guide.position()));
                canvas.fill(vp.x(), y, vp.w(), 1, c);
            }
        }
        if (selected.isPresent()) {
            HudRect rect = rects.get(selected.get());
            if (rect != null) {
                drawSelection(canvas, theme, model, rect);
            }
        }
    }

    private void updateHover(UiContext ctx) {
        if (isHovered() && viewport.contains(ctx.mouseX(), ctx.mouseY()) && !dragging) {
            session.model().updateHover(viewport.toHostX(ctx.mouseX()), viewport.toHostY(ctx.mouseY()));
        } else if (!dragging) {
            session.model().updateHover(-1, -1);
        }
    }

    private static void drawSampleBackdrop(Canvas canvas, Theme theme, Rect vp) {
        // A dusk-like gradient stands in for the world so widgets are judged against something darker than the
        // menu dim; the label makes clear it is not a real world.
        int top = 0xFF1A2130;
        int horizon = 0xFF2A2F44;
        int ground = 0xFF0E1016;
        int h1 = Math.round(vp.h() * 0.58f);
        canvas.fillGradientV(vp.x(), vp.y(), vp.w(), h1, top, horizon);
        canvas.fillGradientV(vp.x(), vp.y() + h1, vp.w(), vp.h() - h1, Colors.lerp(horizon, ground, 0.3f), ground);
        canvas.fill(vp.x(), vp.y() + h1, vp.w(), 1, Colors.withAlpha(theme.accent(), 0.25f));
        String label = Lang.tr("vanta.hud.editor.sample_background");
        int lw = canvas.textWidth(label, FontKind.UI);
        if (lw + 8 < vp.w()) {
            canvas.text(label, vp.centerX() - lw / 2, vp.y() + h1 + 4, Colors.withAlpha(theme.textMuted(), 0.9f),
                    FontKind.UI, false);
        }
    }

    private void drawGrid(Canvas canvas, Theme theme, Rect vp) {
        int step = GRID_STEP;
        while (step * viewport.scale() < 6f) {
            step *= 2;
        }
        int faint = Colors.withAlpha(theme.textPrimary(), 0.05f);
        int thirds = Colors.withAlpha(theme.accent(), 0.22f);
        int centre = Colors.withAlpha(theme.accent(), 0.38f);
        int hw = viewport.hostWidth();
        int hh = viewport.hostHeight();
        for (int gx = step; gx < hw; gx += step) {
            int x = viewport.toLogicalX(gx);
            if (x < vp.right()) {
                canvas.fill(x, vp.y(), 1, vp.h(), faint);
            }
        }
        for (int gy = step; gy < hh; gy += step) {
            int y = viewport.toLogicalY(gy);
            if (y < vp.bottom()) {
                canvas.fill(vp.x(), y, vp.w(), 1, faint);
            }
        }
        for (int i = 1; i <= 2; i++) {
            canvas.fill(viewport.toLogicalX(hw * i / 3), vp.y(), 1, vp.h(), thirds);
            canvas.fill(vp.x(), viewport.toLogicalY(hh * i / 3), vp.w(), 1, thirds);
        }
        canvas.fill(viewport.toLogicalX(hw / 2), vp.y(), 1, vp.h(), centre);
        canvas.fill(vp.x(), viewport.toLogicalY(hh / 2), vp.w(), 1, centre);
    }

    private void drawSelection(Canvas canvas, Theme theme, HudEditorModel model, HudRect rect) {
        Rect r = viewport.toLogical(rect).expand(1);
        canvas.strokeRect(r.x(), r.y(), r.w(), r.h(), 1, theme.accentHover());
        HudWidgetState widget = model.selected().orElse(null);
        if (widget != null) {
            String tag = Lang.tr(widget.type().langKey()) + "  " + rect.x() + ", " + rect.y();
            int tw = canvas.textWidth(tag, FontKind.UI) + 6;
            Rect vp = viewport.area();
            int tx = Math.max(vp.x(), Math.min(r.x(), vp.right() - tw));
            int ty = r.y() - TAG_H - 1 >= vp.y() ? r.y() - TAG_H - 1 : r.bottom() + 1;
            canvas.fillRounded(tx, ty, tw, TAG_H, Theme.RADIUS_SM, Colors.withAlpha(theme.accent(), 0.92f));
            canvas.text(tag, tx + 3, ty + 1, Colors.WHITE, FontKind.UI, false);
        }
        for (Map.Entry<HudEditorModel.Handle, HudRect> entry : model.handleRects().entrySet()) {
            HudRect h = entry.getValue();
            int cx = viewport.toLogicalX(h.centerX());
            int cy = viewport.toLogicalY(h.centerY());
            canvas.fill(cx - HANDLE / 2, cy - HANDLE / 2, HANDLE, HANDLE, Colors.WHITE);
            canvas.strokeRect(cx - HANDLE / 2, cy - HANDLE / 2, HANDLE, HANDLE, 1, theme.accentPressed());
        }
    }

    private static void dashedRect(Canvas canvas, Rect r, int argb) {
        int dash = 3;
        int period = dash * 2;
        for (int x = r.x(); x < r.right(); x += period) {
            int w = Math.min(dash, r.right() - x);
            canvas.fill(x, r.y(), w, 1, argb);
            canvas.fill(x, r.bottom() - 1, w, 1, argb);
        }
        for (int y = r.y(); y < r.bottom(); y += period) {
            int h = Math.min(dash, r.bottom() - y);
            canvas.fill(r.x(), y, 1, h, argb);
            canvas.fill(r.right() - 1, y, 1, h, argb);
        }
    }

    // ---- input -------------------------------------------------------------------------------------------------------

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        HudEditorModel model = session.model();
        ctx.focus().clear(ctx);
        if (!viewport.contains(x, y)) {
            if (button == Keys.MOUSE_LEFT) {
                model.clearSelection();
            }
            return true;
        }
        int hx = viewport.toHostX(x);
        int hy = viewport.toHostY(y);
        if (button == Keys.MOUSE_RIGHT) {
            Optional<HudWidgetState> hit = model.widgetAt(hx, hy);
            if (hit.isPresent()) {
                session.select(hit.get().id());
                session.setSelectedEnabled(!hit.get().enabled());
                ctx.playClick();
            }
            return true;
        }
        if (button != Keys.MOUSE_LEFT) {
            return true;
        }
        model.mouseDown(hx, hy);
        dragging = true;
        ctx.captureMouse(this);
        return true;
    }

    @Override
    protected boolean onMouseDrag(UiContext ctx, double x, double y, int button, double dx, double dy) {
        if (!dragging) {
            return false;
        }
        if (session.model().isDragging()) {
            session.model().mouseDrag(viewport.toHostX(x), viewport.toHostY(y));
        }
        return true;
    }

    @Override
    protected boolean onMouseUp(UiContext ctx, double x, double y, int button) {
        if (!dragging) {
            return false;
        }
        dragging = false;
        session.model().mouseUp(viewport.toHostX(x), viewport.toHostY(y));
        ctx.releaseMouse();
        return true;
    }

    /** Whether a left-button drag is in progress. */
    public boolean isDragging() {
        return dragging;
    }

    /** Snap guides currently drawn (for tests). */
    public List<HudEditorModel.SnapGuide> guides() {
        return session.model().guides();
    }
}
