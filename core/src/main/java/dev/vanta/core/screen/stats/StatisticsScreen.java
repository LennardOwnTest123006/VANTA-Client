package dev.vanta.core.screen.stats;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.cosmetics.CardGrid;
import dev.vanta.core.screen.cosmetics.EmptyState;
import dev.vanta.core.screen.cosmetics.InfoBanner;
import dev.vanta.core.screen.cosmetics.Plurals;
import dev.vanta.core.screen.cosmetics.SectionHeader;
import dev.vanta.core.screen.cosmetics.ThemedScreen;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.stats.LifetimeStats;
import dev.vanta.core.stats.LiveStats;
import dev.vanta.core.stats.SessionRecord;
import dev.vanta.core.stats.StatsPrivacy;
import dev.vanta.core.stats.StatsStore;
import dev.vanta.core.stats.StatsTracker;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.VantaShell;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.layout.Spacer;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Card;
import dev.vanta.core.ui.widget.Dialog;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.Toggle;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Statistics dashboard: lifetime summary tiles with per-session mini charts, the live current-session card, the
 * recent sessions table and the privacy panel (the three privacy settings, Export JSON, Clear all and the
 * "everything stays on this computer" note). Fresh installs get honest empty states.
 * <p>
 * The totals are live: the tiles add the running session to the stored lifetime values ({@link StatsTracker#live()})
 * and show play time with seconds, and the current-session card follows the session. Both are refreshed from the
 * screen tick: while play time counts, exactly when its shown second changes (so the clock steps by one second, never
 * skips or repeats one); while it does not count (outside a world, or statistics off), once per second for the other
 * values. Only the tile values whose text changed are replaced, nothing is laid out again and nothing is written to
 * disk; the session still reaches {@code stats.json} once, when it ends.
 */
public final class StatisticsScreen extends ThemedScreen {
    /** Folder below the config root that receives exports. */
    public static final String EXPORTS_DIR = "exports";
    /** Gap between two live refreshes while play time does not count (outside a world). */
    public static final long LIVE_REFRESH_MS = 1000L;

    private final StatsStore store;
    private VantaShell shell;
    private Column body;
    private ScrollPanel scroll;
    private InfoBanner disabledBanner;
    private final List<StatTile> tiles = new ArrayList<>();
    private SessionTable table;
    private CurrentSessionCard currentCard;
    private Toggle enabledToggle;
    private Toggle serversToggle;
    private Toggle worldsToggle;
    private Button exportButton;
    private Button clearButton;
    private long liveSecond = -1L;
    private long seenPlaytimeMs = -1L;
    private long liveRefreshedAt;
    private int liveRefreshes;

    public StatisticsScreen(VantaServices services) {
        super(services, ScreenId.STATISTICS);
        this.store = services.statsStore();
    }

    @Override
    protected UiNode build(UiContext ctx) {
        shell = newShell();
        body = new Column(Theme.SPACE_4);
        scroll = new ScrollPanel(body).edgeFade(ctx.theme().bgBase());
        shell.content(scroll);
        rebuild();
        return shell;
    }

    /** Rebuilds every section from the store and the settings. */
    public void rebuild() {
        body.clearChildren();
        tiles.clear();
        StatsSummary summary = StatsSummary.of(store);
        StatsPrivacy privacy = services().stats().privacy();
        LiveStats live = services().stats().live();
        ZoneId zone = services().clock().getZone();

        disabledBanner = new InfoBanner(InfoBanner.Tone.WARNING, Lang.tr("vanta.stats.disabled"),
                Lang.tr("vanta.stats.disabled.body"));
        Button turnOn = Button.primary(Lang.tr("vanta.stats.turn_on"),
                () -> setPrivacy(VantaSettings.PRIVACY_STATS_ENABLED, true)).compact(true);
        turnOn.setId("stats.turn-on");
        disabledBanner.action(turnOn);
        disabledBanner.setVisible(!privacy.enabled());
        body.add(disabledBanner);

        // ---- lifetime tiles
        LifetimeStats life = summary.lifetime();
        body.add(new SectionHeader(Lang.tr("vanta.stats.lifetime"))
                .trailing(Plurals.count(life.sessions(), "vanta.stats.sessions_recorded")));
        CardGrid grid = new CardGrid(150, 3, Theme.SPACE_4).rowHeight(StatTile.HEIGHT);
        Optional<SessionRecord> last = summary.lastSession();
        tile(grid, "stats.playtime", Lang.tr("vanta.stats.playtime"), playtimeText(live),
                last.map(s -> Lang.tr("vanta.stats.tile.last_session", StatFormats.duration(s.playtimeMs())))
                        .orElse(Lang.tr("vanta.stats.tile.none")), summary.playtimeSeries(), false)
                .setTooltip(Lang.tr("vanta.stats.playtime.live_hint"));
        tile(grid, "stats.sessions", Lang.tr("vanta.stats.sessions"), StatFormats.count(life.sessions()),
                life.sessions() == 0 ? Lang.tr("vanta.stats.tile.none")
                        : Lang.tr("vanta.stats.tile.per_session", StatFormats.duration(summary.averageSessionMs())),
                summary.playtimeSeries(), true);
        tile(grid, "stats.avg-fps", Lang.tr("vanta.stats.avg_fps"), StatFormats.fps(summary.averageFps()),
                last.filter(s -> s.averageFps() > 0)
                        .map(s -> Lang.tr("vanta.stats.tile.last_session", StatFormats.fps(s.averageFps())))
                        .orElse(Lang.tr("vanta.stats.tile.none")), summary.averageFpsSeries(), false);
        tile(grid, "stats.max-fps", Lang.tr("vanta.stats.max_fps"), maxFpsText(live),
                last.filter(s -> s.maxFps() > 0)
                        .map(s -> Lang.tr("vanta.stats.tile.last_session", StatFormats.count(s.maxFps())))
                        .orElse(Lang.tr("vanta.stats.tile.none")), summary.maxFpsSeries(), true);
        tile(grid, "stats.worlds", Lang.tr("vanta.stats.worlds"), StatFormats.count(live.worlds()),
                privacy.trackWorlds() ? Lang.tr("vanta.stats.tile.distinct") : Lang.tr("vanta.stats.names_hidden"),
                summary.worldsSeries(), false);
        tile(grid, "stats.servers", Lang.tr("vanta.stats.servers"), StatFormats.count(live.servers()),
                privacy.trackServers() ? Lang.tr("vanta.stats.tile.distinct") : Lang.tr("vanta.stats.names_hidden"),
                summary.serversSeries(), true);
        tile(grid, "stats.distance", Lang.tr("vanta.stats.distance"), StatFormats.distance(live.distanceBlocks()),
                last.map(s -> Lang.tr("vanta.stats.tile.last_session", StatFormats.distance(s.distanceBlocks())))
                        .orElse(Lang.tr("vanta.stats.tile.none")), summary.distanceSeries(), false);
        tile(grid, "stats.broken", Lang.tr("vanta.stats.blocks_broken"), StatFormats.count(live.blocksBroken()),
                last.map(s -> Lang.tr("vanta.stats.tile.last_session", StatFormats.count(s.blocksBroken())))
                        .orElse(Lang.tr("vanta.stats.tile.none")), summary.blocksBrokenSeries(), true);
        tile(grid, "stats.placed", Lang.tr("vanta.stats.blocks_placed"), StatFormats.count(live.blocksPlaced()),
                last.map(s -> Lang.tr("vanta.stats.tile.last_session", StatFormats.count(s.blocksPlaced())))
                        .orElse(Lang.tr("vanta.stats.tile.none")), summary.blocksPlacedSeries(), false);
        body.add(grid);

        // ---- current session
        body.add(new SectionHeader(Lang.tr("vanta.stats.current_session")));
        currentCard = new CurrentSessionCard(services());
        currentCard.setId("stats.current");
        body.add(currentCard);

        // ---- recent sessions
        List<SessionRecord> recent = store.recentSessions();
        body.add(new SectionHeader(Lang.tr("vanta.stats.recent"))
                .trailing(recent.isEmpty() ? "" : Lang.tr("vanta.stats.last_sessions", recent.size())));
        if (recent.isEmpty()) {
            EmptyState empty = new EmptyState(Icons.CHART, Lang.tr("vanta.stats.no_sessions"),
                    Lang.tr("vanta.stats.no_sessions.hint"));
            empty.setId("stats.empty");
            body.add(empty);
            table = null;
        } else {
            table = new SessionTable(recent, zone, privacy.trackWorlds() || privacy.trackServers());
            table.setId("stats.table");
            body.add(table);
        }

        // ---- privacy
        body.add(new SectionHeader(Lang.tr("vanta.stats.privacy")));
        Card privacyCard = new Card(Lang.tr("vanta.stats.local_only")).caption(Lang.tr("vanta.stats.privacy_note"))
                .accentBar(true);
        enabledToggle = privacyToggle(VantaSettings.PRIVACY_STATS_ENABLED, "stats.privacy.enabled");
        serversToggle = privacyToggle(VantaSettings.PRIVACY_STATS_TRACK_SERVERS, "stats.privacy.servers");
        worldsToggle = privacyToggle(VantaSettings.PRIVACY_STATS_TRACK_WORLDS, "stats.privacy.worlds");
        privacyCard.add(enabledToggle);
        privacyCard.add(serversToggle);
        privacyCard.add(worldsToggle);
        Row actions = new Row(Theme.SPACE_3);
        actions.add(new Label(Lang.tr("vanta.stats.file_hint"), Label.Variant.MUTED));
        actions.add(Spacer.grow());
        exportButton = Button.secondary(Lang.tr("vanta.stats.export"), this::exportJson).icon(Icons.DOWNLOAD)
                .compact(true);
        exportButton.setId("stats.export");
        clearButton = Button.danger(Lang.tr("vanta.stats.clear"), this::confirmClear).icon(Icons.TRASH).compact(true);
        clearButton.setId("stats.clear");
        clearButton.setEnabled(!summary.isEmpty());
        actions.add(exportButton);
        actions.add(clearButton);
        privacyCard.add(actions);
        body.add(privacyCard);
        body.add(Spacer.fixed(0, Theme.SPACE_2));
        markLiveRefreshed();

        if (context() != null) {
            context().requestLayout();
        }
    }

    private StatTile tile(CardGrid grid, String id, String title, String value, String caption, float[] series,
                          boolean blue) {
        StatTile tile = new StatTile(title, value, caption, series).blue(blue);
        tile.setId(id);
        tiles.add(tile);
        grid.add(tile);
        return tile;
    }

    private static String playtimeText(LiveStats live) {
        return StatFormats.clock(live.playtimeMs());
    }

    private static String maxFpsText(LiveStats live) {
        return live.maxFps() > 0 ? StatFormats.count(live.maxFps()) : "—";
    }

    /**
     * Once per game tick (also while the game is paused): refreshes the live values when the shown second of the
     * running session's play time changed, or, while play time stands still, {@value #LIVE_REFRESH_MS} ms after the
     * last refresh. The check reads two numbers and allocates nothing.
     */
    @Override
    protected void onTick() {
        if (body == null) {
            return;
        }
        long playtime = services().stats().sessionPlaytimeMs();
        boolean counting = playtime != seenPlaytimeMs;
        seenPlaytimeMs = playtime;
        if (playtime / 1000L != liveSecond) {
            refreshLive();
            return;
        }
        long now = services().clock().millis();
        if (!counting && (now - liveRefreshedAt >= LIVE_REFRESH_MS || now < liveRefreshedAt)) {
            refreshLive();
        }
    }

    /**
     * Updates the tiles that depend on the running session (play time, best FPS, worlds, servers, distance, blocks)
     * and the current-session card. Only a value whose text changed is replaced; tile sizes are fixed, so nothing is
     * laid out again.
     */
    public void refreshLive() {
        LiveStats live = services().stats().live();
        updateTile("stats.playtime", playtimeText(live));
        updateTile("stats.max-fps", maxFpsText(live));
        updateTile("stats.worlds", StatFormats.count(live.worlds()));
        updateTile("stats.servers", StatFormats.count(live.servers()));
        updateTile("stats.distance", StatFormats.distance(live.distanceBlocks()));
        updateTile("stats.broken", StatFormats.count(live.blocksBroken()));
        updateTile("stats.placed", StatFormats.count(live.blocksPlaced()));
        if (currentCard != null) {
            currentCard.refresh();
        }
        liveRefreshes++;
        markLiveRefreshed();
    }

    private void updateTile(String id, String value) {
        for (StatTile tile : tiles) {
            if (id.equals(tile.id())) {
                if (!value.equals(tile.value())) {
                    tile.setValue(value);
                }
                return;
            }
        }
    }

    private void markLiveRefreshed() {
        liveSecond = services().stats().sessionPlaytimeMs() / 1000L;
        liveRefreshedAt = services().clock().millis();
    }

    /** How often {@link #refreshLive()} ran since the screen opened (tests: the cadence). */
    public int liveRefreshes() {
        return liveRefreshes;
    }

    private Toggle privacyToggle(Setting<Boolean> setting, String id) {
        Toggle toggle = new Toggle(Lang.tr(setting.titleKey()), services().settings().get(setting),
                on -> setPrivacy(setting, on));
        toggle.setTooltip(Lang.tr(setting.descriptionKey()));
        toggle.setId(id);
        return toggle;
    }

    /** Writes a privacy setting; turning a "remember names" switch off forgets the stored names. */
    public void setPrivacy(Setting<Boolean> setting, boolean on) {
        services().settings().set(setting, on);
        if (!on && setting == VantaSettings.PRIVACY_STATS_TRACK_WORLDS) {
            store.forgetNames(true, false);
        } else if (!on && setting == VantaSettings.PRIVACY_STATS_TRACK_SERVERS) {
            store.forgetNames(false, true);
        }
        services().settings().saveIfDirty();
        store.saveIfDirty();
        rebuild();
    }

    /** Writes {@code config/vanta/exports/vanta-statistics-<timestamp>.json} and copies the JSON to the clipboard. */
    public Optional<Path> exportJson() {
        long now = services().clock().millis();
        String stamp = StatFormats.dateTime(now, services().clock().getZone()).replaceAll("[^0-9A-Za-z]+", "-");
        Path target = services().paths().root().resolve(EXPORTS_DIR).resolve("vanta-statistics-" + stamp + ".json");
        store.exportTo(target);
        String json = services().jsonStore().gson().toJson(store.toJson());
        services().clipboard().ifPresentOrElse(c -> c.set(json), () -> context().host().setClipboard(json));
        services().notifications().post(NotificationKind.SUCCESS, Lang.tr("vanta.stats.export"),
                Lang.tr("vanta.stats.exported", displayPath(services(), target)) + " · "
                        + Lang.tr("vanta.stats.copied"));
        return Optional.of(target);
    }

    /** Asks for confirmation, then deletes every statistic. */
    public void confirmClear() {
        Dialog.confirm(context(), Lang.tr("vanta.stats.confirm_clear.title"), Lang.tr("vanta.stats.confirm_clear.body"),
                Lang.tr("vanta.stats.clear"), Lang.tr("vanta.common.cancel"), true, () -> {
                    services().stats().clearAll();
                    services().notifications().statisticsCleared();
                    rebuild();
                });
    }

    /** Summary tiles in display order. */
    public List<StatTile> tiles() {
        return List.copyOf(tiles);
    }

    /** The tile with the given id ({@code stats.playtime}, {@code stats.avg-fps}, …). */
    public Optional<StatTile> tile(String id) {
        return tiles.stream().filter(t -> id.equals(t.id())).findFirst();
    }

    /** The sessions table, or empty when no session was recorded. */
    public Optional<SessionTable> table() {
        return Optional.ofNullable(table);
    }

    public CurrentSessionCard currentCard() {
        return currentCard;
    }

    public InfoBanner disabledBanner() {
        return disabledBanner;
    }

    public Toggle enabledToggle() {
        return enabledToggle;
    }

    public Toggle serversToggle() {
        return serversToggle;
    }

    public Toggle worldsToggle() {
        return worldsToggle;
    }

    public Button exportButton() {
        return exportButton;
    }

    public Button clearButton() {
        return clearButton;
    }

    @Override
    protected void onThemeChanged(Theme theme) {
        scroll.edgeFade(theme.bgBase());
    }
}
