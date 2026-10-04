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
 * Live card of the running session: duration, average and current fps, distance, blocks, screenshots and where the
 * player is. It reads the {@link StatsTracker} and {@link GameBridge} on every frame, so it needs no rebuild. When
 * statistics are turned off it says so instead of showing zeros.
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

    public CurrentSessionCard(VantaServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /** The facts shown right now (six, or empty when statistics are off). */
    public List<Fact> facts() {
        StatsTracker tracker = services.stats();
        if (!tracker.privacy().enabled()) {
            return List.of();
        }
        Optional<SessionStats> current = tracker.current();
        GameBridge game = services.game();
        List<Fact> out = new ArrayList<>();
        long playtime = current.map(SessionStats::playtimeMs).orElse(0L);
        double avg = current.map(SessionStats::averageFps).orElse(0.0);
        out.add(new Fact(Lang.tr("vanta.stats.duration_label"), StatFormats.duration(playtime)));
        out.add(new Fact(Lang.tr("vanta.stats.avg_fps"), avg > 0 ? StatFormats.fps(avg) : StatFormats.fps(game.fps())));
        out.add(new Fact(Lang.tr("vanta.stats.distance"),
                StatFormats.distance(current.map(SessionStats::distanceBlocks).orElse(0.0))));
        out.add(new Fact(Lang.tr("vanta.stats.blocks"), Lang.tr("vanta.stats.blocks_value",
                StatFormats.count(current.map(SessionStats::blocksBroken).orElse(0)),
                StatFormats.count(current.map(SessionStats::blocksPlaced).orElse(0)))));
        out.add(new Fact(Lang.tr("vanta.stats.screenshots"),
                StatFormats.count(current.map(SessionStats::screenshots).orElse(0))));
        out.add(new Fact(Lang.tr("vanta.stats.where"), where(game)));
        return out;
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
        boolean recording = services.stats().privacy().enabled();
        int dot = recording ? theme.success() : theme.textMuted();
        canvas.circle(b.x() + PAD + 2, b.y() + PAD + 3, 2, dot);
        String heading = recording ? Lang.tr("vanta.stats.recording") : Lang.tr("vanta.stats.current.none");
        canvas.text(canvas.textClipped(heading, b.w() - PAD * 2 - 8, FontKind.UI_BOLD), b.x() + PAD + 8, b.y() + PAD - 1,
                recording ? theme.textPrimary() : theme.textMuted(), FontKind.UI_BOLD, false);
        List<Fact> facts = facts();
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
