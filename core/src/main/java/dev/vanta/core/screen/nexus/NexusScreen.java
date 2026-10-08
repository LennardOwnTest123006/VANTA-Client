package dev.vanta.core.screen.nexus;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.VantaUiScreen;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.VantaShell;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Vanta Nexus: a left rail with the seven sections (collapsing to icons below {@link VantaShell#COMPACT_BREAKPOINT}
 * like every other VANTA screen) and the section's panel as content. Panels are created when first shown and kept
 * for the life of the screen; the open panel gets the ticks. Deep links arrive through
 * {@link ScreenNavigator#openNexus} / {@link ScreenNavigator#stageNexusSection}.
 */
public final class NexusScreen extends VantaUiScreen {
    private final Map<NexusSection, NexusPanel> panels = new EnumMap<>(NexusSection.class);
    private VantaShell shell;
    private NexusSection section;

    public NexusScreen(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, ScreenId.NEXUS);
        this.section = navigator.takePendingNexusSection().flatMap(NexusSection::fromId).orElse(NexusSection.ASSISTANT);
    }

    /** The open section. */
    public NexusSection section() {
        return section;
    }

    /** The panel of a section (created on demand). */
    @SuppressWarnings("unchecked")
    public <T extends NexusPanel> T panel(NexusSection wanted) {
        return (T) panels.computeIfAbsent(wanted, this::createPanel);
    }

    /** The shell (rail and content). */
    public VantaShell shell() {
        return shell;
    }

    private NexusPanel createPanel(NexusSection wanted) {
        return switch (wanted) {
            case ASSISTANT -> new AssistantPanel(services(), navigator());
            case HUD_DESIGNER -> new HudDesignerPanel(services(), navigator());
            case PROFILES -> new NexusProfilesPanel(services(), navigator());
            case PERFORMANCE -> new NexusPerformancePanel(services(), navigator());
            case WAYPOINTS -> new WaypointsPanel(services(), navigator());
            case LAB -> new LabPanel(services(), navigator());
            case SETTINGS -> new NexusSettingsPanel(services(), navigator());
        };
    }

    @Override
    protected UiNode build(UiContext ctx) {
        shell = new VantaShell(Lang.tr("vanta.screen.nexus"), this::close);
        shell.backTooltip(Lang.tr("vanta.common.back"));
        List<VantaShell.RailItem> items = new ArrayList<>();
        for (NexusSection s : NexusSection.values()) {
            items.add(new VantaShell.RailItem(s.id(), s.icon(), Lang.tr(s.langKey())));
        }
        shell.rail(items, section.id(), id -> NexusSection.fromId(id).ifPresent(this::select));
        shell.content(panel(section));
        return shell;
    }

    /** Shows a section. */
    public void select(NexusSection next) {
        if (next == null) {
            return;
        }
        section = next;
        if (shell == null) {
            return;
        }
        shell.selectRail(context(), next.id());
        NexusPanel panel = panel(next);
        shell.content(panel);
        panel.onShown(context());
        invalidateLayout();
    }

    @Override
    protected void onScreenInit() {
        panel(section).onShown(context());
    }

    @Override
    protected void onScreenTick() {
        Optional.ofNullable(panels.get(section)).ifPresent(p -> p.tick(context()));
    }

    @Override
    protected void onThemeChanged(Theme theme) {
        for (NexusPanel panel : panels.values()) {
            panel.onThemeChanged(theme);
        }
    }

    @Override
    protected void onScreenClose() {
        for (NexusPanel panel : panels.values()) {
            panel.dispose();
        }
    }
}
