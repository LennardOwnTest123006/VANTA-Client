package dev.vanta.core.screen.stats;

import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.stats.SessionStats;
import dev.vanta.core.stats.StatsTracker;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Live card of the running session: play time (h:mm:ss, counting only while a world is loaded, which the heading says
 * while the player is outside one), average and current fps, distance, blocks, screenshots and where the player is.
 * The text is read from the {@link StatsTracker} and {@link GameBridge} by {@link #refresh()}, which the Statistics
 * screen calls once per shown second; a frame only draws the cached text. When statistics are turned off it says so
 * instead of showing zeros.
 */
public final class CurrentSessionCard extends UiNode {
    /** Card height. */
    public static final int HEIGHT = 58;
    private static final int PAD = Theme.SPACE_4;
    private static final int LINE = 11;

    /** One label / value pair. */
    public record Fact(String label, String value) {
    }

    private final VantaServices services;
    private boolean recording;
    private boolean inWorld;
    private String heading = "";
    private List<Fact> facts = List.of();

    public CurrentSessionCard(VantaServices services) {
        this.services = Objects.requireNonNull(services, "services");
        refresh();
    }

    /**
     * Re-reads the running session and the game (one small list of six facts). The screen calls it once per shown
     * second, never per frame.
     */
    public void refresh() {
        StatsTracker tracker = services.stats();
        recording = tracker.privacy().enabled();
        inWorld = services.game().isInWorld();
        facts = recording ? readFacts(tracker, services.game()) : List.of();
        if (!recording) {
            heading = Lang.tr("vanta.stats.current.none");
        } else {
            heading = inWorld ? Lang.tr("vanta.stats.recording") : Lang.tr("vanta.stats.recording.outside_world");
        }
    }

    /** The facts as of the last {@link #refresh()} (six, or empty when statistics are off). */
    public List<Fact> facts() {
        return facts;
    }

    /** The heading as of the last {@link #refresh()}. */
    public String heading() {
        return heading;
    }

    private static List<Fact> readFacts(StatsTracker tracker, GameBridge game) {
        Optional<SessionStats> current = tracker.current();
        List<Fact> out = new ArrayList<>(6);
        long playtime = current.map(SessionStats::playtimeMs).orElse(0L);
        double avg = current.map(SessionStats::averageFps).orElse(0.0);
        out.add(new Fact(Lang.tr("vanta.stats.playtime"), StatFormats.clock(playtime)));
        out.add(new Fact(Lang.tr("vanta.stats.avg_fps"), avg > 0 ? StatFormats.fps(avg) : StatFormats.fps(game.fps())));
        out.add(new Fact(Lang.tr("vanta.stats.distance"),
                StatFormats.distance(current.map(SessionStats::distanceBlocks).orElse(0.0))));
        out.add(new Fact(Lang.tr("vanta.stats.blocks"), Lang.tr("vanta.stats.blocks_value",
                StatFormats.count(current.map(SessionStats::blocksBroken).orElse(0)),
                StatFormats.count(current.map(SessionStats::blocksPlaced).orElse(0)))));
        out.add(new Fact(Lang.tr("vanta.stats.screenshots"),
                StatFormats.count(current.map(SessionStats::screenshots).orElse(0))));
        out.add(new Fact(Lang.tr("vanta.stats.where"), where(game)));
        return List.copyOf(out);
    }

    private static String where(GameBridge game) {
        if (!game.isInWorld()) {
            return Lang.tr("vanta.stats.not_in_world");
        }
        if (!game.isSingleplayer()) {
            return game.serverName().filter(n -> !n.isBlank())
                    .or(() -> game.serverAddress().flatMap(StatsTracker::hostname))
                    .orElse(Lang.tr("vanta.stats.multiplayer"));
        }
        return game.lastWorldName().filter(n -> !n.isBlank()).orElse(Lang.tr("vanta.stats.singleplayer"));
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(240, HEIGHT);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.panelBackground());
        canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.borderSubtle());
        boolean counting = recording && inWorld;
        int dot = counting ? theme.success() : recording ? theme.warning() : theme.textMuted();
        canvas.circle(b.x() + PAD + 2, b.y() + PAD + 3, 2, dot);
        canvas.text(canvas.textClipped(heading, b.w() - PAD * 2 - 8, FontKind.UI_BOLD), b.x() + PAD + 8,
                b.y() + PAD - 1, recording ? theme.textPrimary() : theme.textMuted(), FontKind.UI_BOLD, false);
        List<Fact> facts = this.facts;
        if (facts.isEmpty()) {
            canvas.text(canvas.textClipped(Lang.tr("vanta.stats.disabled"), b.w() - PAD * 2, FontKind.UI), b.x() + PAD,
                    b.y() + PAD + LINE + Theme.SPACE_2, theme.textMuted(), FontKind.UI, false);
            return;
        }
        int columns = b.w() >= 360 ? 3 : 2;
        int colW = (b.w() - PAD * 2) / columns;
        int top = b.y() + PAD + LINE + Theme.SPACE_2;
        for (int i = 0; i < facts.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int x = b.x() + PAD + col * colW;
            int y = top + row * LINE;
            if (y + LINE > b.bottom() - 2) {
                break;
            }
            Fact f = facts.get(i);
            int labelW = canvas.textWidth(f.label(), FontKind.UI);
            canvas.text(f.label(), x, y, theme.textMuted(), FontKind.UI, false);
            int vx = x + labelW + Theme.SPACE_2;
            canvas.text(canvas.textClipped(f.value(), Math.max(0, x + colW - Theme.SPACE_3 - vx), FontKind.UI_BOLD), vx, y,
                    Colors.withAlpha(theme.textPrimary(), 0.95f), FontKind.UI_BOLD, false);
        }
    }
}
