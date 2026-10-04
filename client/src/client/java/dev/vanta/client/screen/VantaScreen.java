package dev.vanta.client.screen;

import dev.vanta.client.VantaRuntime;
import dev.vanta.client.render.GuiGraphicsCanvas;
import dev.vanta.client.render.MinecraftTextMetrics;
import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiEnvironment;
import dev.vanta.core.ui.UiScreen;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Hosts a core {@link UiScreen} inside a vanilla {@link Screen}.
 * <p>
 * The vanilla screen is a thin shell: it forwards size, rendering, ticks and input to the core screen, which owns all
 * widgets, focus handling, animations and its own background (dim + optional blur). The host only paints what the core
 * cannot: outside a world it draws the vanilla panorama or an opaque base colour so screens never float over black.
 * <p>
 * Lifecycle: {@code init} (also after resizes) → {@code ui.init}; {@code removed} → {@code ui.onClose} exactly once;
 * {@link #closeNow()} (called by the core after its close transition, or by vanilla's {@code onClose}) returns to the
 * parent screen.
 */
public final class VantaScreen extends Screen {
    private final ScreenId id;
    private final UiScreen ui;
    private final Screen parent;
    private final VantaRuntime runtime;
    private boolean closed;

    /**
     * @param id      which VANTA screen this is
     * @param ui      the core screen (fresh instance from the registry)
     * @param parent  screen to return to when closed, {@code null} for the game / vanilla title flow
     * @param runtime the client runtime (theme, overlays)
     */
    public VantaScreen(ScreenId id, UiScreen ui, Screen parent, VantaRuntime runtime) {
        super(Component.literal(ui.title()));
        this.id = Objects.requireNonNull(id, "id");
        this.ui = ui;
        this.parent = parent;
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        ui.attach(new UiEnvironment(new MinecraftUiHost(this, runtime), runtime.theme(), runtime.uiClock(),
                MinecraftTextMetrics.INSTANCE, Lang::tr));
    }

    /** Which VANTA screen is hosted. */
    public ScreenId screenId() {
        return id;
    }

    /** The hosted core screen. */
    public UiScreen ui() {
        return ui;
    }

    /** The screen shown when this one closes (may be {@code null}). */
    public Screen parent() {
        return parent;
    }

    // ---- lifecycle ---------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        syncTheme();
        ui.init(width, height);
    }

    @Override
    public void tick() {
        syncTheme();
        ui.tick();
    }

    private void syncTheme() {
        Theme current = runtime.theme();
        if (ui.context() != null && !current.equals(ui.theme())) {
            ui.setTheme(current);
        }
    }

    /** Re-runs the core layout before the next frame. */
    void requestLayout() {
        ui.invalidateLayout();
    }

    /** Leaves this screen for its parent immediately (the core has already played its close transition). */
    void closeNow() {
        minecraft.setScreen(parent);
    }

    /** Vanilla asked the screen to close (never from Escape: see {@link #shouldCloseOnEsc()}). */
    @Override
    public void onClose() {
        closeNow();
    }

    @Override
    public void removed() {
        if (!closed) {
            closed = true;
            ui.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return ui.isPauseScreen();
    }

    /** Escape is handled by the core screen (close transition), never by the vanilla shortcut. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ---- rendering ---------------------------------------------------------------------------------------------

    /**
     * Base layer. In a world the core screen dims and blurs the game itself; outside a world this paints the vanilla
     * panorama when the menu background style asks for it, otherwise the theme's void colour.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float deltaTicks) {
        if (minecraft.level != null) {
            return;
        }
        MenuBackground style = runtime.services().settings().get(VantaSettings.MENU_BACKGROUND);
        if (style == MenuBackground.VANILLA_PANORAMA) {
            renderPanorama(graphics, deltaTicks);
        } else {
            graphics.fill(0, 0, width, height, runtime.theme().bgVoid());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float deltaTicks) {
        runtime.frames().onScreenFrame(minecraft);
        GuiGraphicsCanvas canvas = new GuiGraphicsCanvas(graphics, font, width, height, true);
        try {
            ui.render(canvas, mouseX, mouseY, deltaTicks);
            runtime.notifications().render(canvas, width, height, deltaTicks);
        } finally {
            canvas.finish();
        }
    }

    // ---- input -------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return ui.mouseDown(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return ui.mouseUp(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return ui.mouseDrag(event.x(), event.y(), event.button(), dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return ui.mouseScroll(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ui.keyDown(event.key(), event.scancode(), event.modifiers())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (ui.keyUp(event.key(), event.scancode(), event.modifiers())) {
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (ui.charTyped(event.codepoint(), event.modifiers())) {
            return true;
        }
        return super.charTyped(event);
    }
}
