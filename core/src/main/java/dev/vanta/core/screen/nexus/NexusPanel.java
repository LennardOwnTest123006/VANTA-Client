package dev.vanta.core.screen.nexus;

import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.ScrollPanel;
import java.util.Objects;

/**
 * One section of the Vanta Nexus screen: a scrolling column of cards that fills the content area. Subclasses add
 * their nodes to {@link #content()} in their constructor and override the lifecycle hooks; the screen calls
 * {@link #onShown}, {@link #tick} and {@link #dispose} for the section that is open.
 */
public abstract class NexusPanel extends UiNode {
    private final VantaServices services;
    private final ScreenNavigator navigator;
    private final NexusSection section;
    private final Column content = new Column(Theme.SPACE_4);
    private final ScrollPanel scroll;

    protected NexusPanel(VantaServices services, ScreenNavigator navigator, NexusSection section) {
        this.services = Objects.requireNonNull(services, "services");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.section = Objects.requireNonNull(section, "section");
        this.scroll = new ScrollPanel(content);
        add(scroll);
        setId("nexus.panel." + section.id());
    }

    public final VantaServices services() {
        return services;
    }

    public final ScreenNavigator navigator() {
        return navigator;
    }

    public final NexusSection section() {
        return section;
    }

    /** The column the cards live in. */
    public final Column content() {
        return content;
    }

    /** The scroll panel around the content (tests scroll it to reach lower cards). */
    public final ScrollPanel scrollPanel() {
        return scroll;
    }

    /** The section was selected (or the screen opened on it). */
    public void onShown(UiContext ctx) {
    }

    /** Once per game tick while the section is open. */
    public void tick(UiContext ctx) {
    }

    /** The screen closed: unsubscribe listeners. */
    public void dispose() {
    }

    /** Re-applies the theme-dependent colours after a live theme change. */
    public void onThemeChanged(Theme theme) {
        scroll.edgeFade(Colors.withAlpha(theme.bgBase(), 0.92f));
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(200, 120);
    }

    @Override
    public void layout(UiContext ctx) {
        Rect b = bounds();
        scroll.edgeFade(Colors.withAlpha(ctx.theme().bgBase(), 0.92f));
        scroll.setBounds(b);
        scroll.layout(ctx);
    }
}
