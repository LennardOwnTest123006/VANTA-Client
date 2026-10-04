package dev.vanta.core.crosshair.editor;

import dev.vanta.core.crosshair.CrosshairPreset;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.crosshair.render.CrosshairRenderer;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Gallery of the built-in crosshair presets: one tile per preset with a thumbnail drawn by the real renderer and
 * the preset name. The tile whose style equals the active style is highlighted; clicking (or Enter) applies a
 * preset. Tiles flow into as many columns as fit.
 */
public final class PresetGallery extends UiNode {

    /** Tile width. */
    public static final int TILE_W = 64;
    /** Tile height (thumbnail + name). */
    public static final int TILE_H = 50;
    /** Gap between tiles. */
    public static final int GAP = Theme.SPACE_3;
    private static final int THUMB_H = 34;

    private final Supplier<CrosshairStyle> active;
    private final Consumer<CrosshairPreset> onApply;
    private final List<PresetTile> tiles = new ArrayList<>();

    /** Gallery of every built-in preset. */
    public PresetGallery(Supplier<CrosshairStyle> active, Consumer<CrosshairPreset> onApply) {
        this.active = active;
        this.onApply = onApply;
        for (CrosshairPreset preset : CrosshairPresets.builtIns()) {
            PresetTile tile = new PresetTile(preset);
            tiles.add(tile);
            add(tile);
        }
        setId("crosshair.presets");
    }

    /** The tiles in preset order. */
    public List<PresetTile> tiles() {
        return List.copyOf(tiles);
    }

    /** Tile for a preset id. */
    public PresetTile tile(String presetId) {
        for (PresetTile tile : tiles) {
            if (tile.preset.id().equals(presetId)) {
                return tile;
            }
        }
        throw new IllegalArgumentException("Unknown preset " + presetId);
    }

    private int columnsFor(int width) {
        return Math.max(1, Math.min(tiles.size(), (width + GAP) / (TILE_W + GAP)));
    }

    @Override
    protected Size measure(UiContext ctx) {
        int width = bounds().w() > 0 ? bounds().w() : 3 * (TILE_W + GAP) - GAP;
        int columns = columnsFor(width);
        int rows = (tiles.size() + columns - 1) / columns;
        return new Size(Math.min(width, tiles.size() * (TILE_W + GAP) - GAP), rows * (TILE_H + GAP) - GAP);
    }

    @Override
    public void layout(UiContext ctx) {
        Rect b = bounds();
        int columns = columnsFor(b.w());
        int tileW = Math.max(TILE_W, (b.w() - GAP * (columns - 1)) / columns);
        for (int i = 0; i < tiles.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            tiles.get(i).setBounds(b.x() + column * (tileW + GAP), b.y() + row * (TILE_H + GAP), tileW, TILE_H);
            tiles.get(i).layout(ctx);
        }
    }

    /** One preset tile. */
    public final class PresetTile extends UiNode {
        private final CrosshairPreset preset;

        PresetTile(CrosshairPreset preset) {
            this.preset = preset;
            setFocusable(true);
            setId("crosshair.preset." + preset.id());
            setTooltip(Lang.tr(preset.descriptionKey()));
        }

        /** The preset shown by this tile. */
        public CrosshairPreset preset() {
            return preset;
        }

        /** Whether the active style equals this preset. */
        public boolean isActive() {
            return preset.style().equals(active.get());
        }

        @Override
        protected Size measure(UiContext ctx) {
            return new Size(TILE_W, TILE_H);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            boolean selected = isActive();
            int bg = selected ? Colors.withAlpha(theme.accent(), 0.16f) : isHovered() ? theme.surface4() : theme.surface3();
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, bg);
            Rect thumb = new Rect(b.x() + 2, b.y() + 2, b.w() - 4, THUMB_H);
            canvas.fillRounded(thumb.x(), thumb.y(), thumb.w(), thumb.h(), Theme.RADIUS_SM, 0xFF1C2230);
            canvas.pushScissor(thumb);
            CrosshairRenderer.draw(canvas, preset.style(), thumb.centerX(), thumb.centerY(), 0);
            canvas.popScissor();
            canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD,
                    selected ? theme.accentHover() : theme.borderStrong());
            String name = Lang.tr(preset.langKey());
            int textY = thumb.bottom() + (b.bottom() - thumb.bottom() - canvas.lineHeight(FontKind.UI)) / 2;
            canvas.textCentered(canvas.textClipped(name, b.w() - 6, FontKind.UI), b.centerX(), textY,
                    selected ? theme.textPrimary() : theme.textSecondary(), FontKind.UI, false);
            if (isFocused()) {
                canvas.strokeRounded(b.x() - 2, b.y() - 2, b.w() + 4, b.h() + 4, Theme.RADIUS_LG,
                        Colors.withAlpha(theme.borderFocus(), 0.9f));
            }
        }

        private void apply(UiContext ctx) {
            ctx.playClick();
            if (onApply != null) {
                onApply.accept(preset);
            }
        }

        @Override
        protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
            if (button != Keys.MOUSE_LEFT) {
                return false;
            }
            requestFocus(ctx);
            apply(ctx);
            return true;
        }

        @Override
        public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
            if (Keys.isActivate(key)) {
                apply(ctx);
                return true;
            }
            return false;
        }
    }
}
