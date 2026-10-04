package dev.vanta.core.stats;

import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.settings.SettingsStore;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Feeds {@link SessionStats} from game events and commits sessions to the {@link StatsStore}.
 * <p>
 * Distance is integrated from position deltas between ticks; a delta above {@value #TELEPORT_THRESHOLD} blocks or a
 * dimension change is treated as a teleport and ignored. Privacy flags are re-read from the settings on every call, so
 * turning tracking off takes effect immediately. Server addresses are reduced to lower-case hostnames (no port).
 */
public final class StatsTracker {
    /** Largest per-tick movement counted as walking/flying. */
    public static final double TELEPORT_THRESHOLD = 50.0;
    /** Ticks between fps samples (once per second). */
    public static final int FPS_SAMPLE_INTERVAL = 20;

    private final StatsStore store;
    private final SettingsStore settings;
    private final Clock clock;
    private SessionStats session;
    private long lastTickAt;
    private Vec3d lastPosition;
    private String lastDimension;
    private int tickCounter;

    public StatsTracker(StatsStore store, SettingsStore settings, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Current privacy flags. */
    public StatsPrivacy privacy() {
        return StatsPrivacy.fromSettings(settings);
    }

    /** Starts a session (idempotent). */
    public void startSession() {
        if (session == null) {
            session = new SessionStats(clock.millis());
            lastTickAt = 0;
            lastPosition = null;
            lastDimension = null;
            tickCounter = 0;
        }
    }

    /** The running session, if any. */
    public Optional<SessionStats> current() {
        return Optional.ofNullable(session);
    }

    /** Once per client tick while the game runs. */
    public void onTick(GameBridge game) {
        if (!privacy().enabled()) {
            return;
        }
        startSession();
        long now = clock.millis();
        if (!game.isInWorld()) {
            lastTickAt = 0;
            lastPosition = null;
            return;
        }
        if (lastTickAt > 0) {
            session.addPlaytime(Math.min(now - lastTickAt, 1000));
        }
        lastTickAt = now;
        tickCounter++;
        if (tickCounter % FPS_SAMPLE_INTERVAL == 0) {
            session.addFpsSample(game.fps());
        }
        Optional<Vec3d> position = game.playerPosition();
        String dimension = game.dimensionId().orElse("");
        if (position.isPresent()) {
            if (lastPosition != null && dimension.equals(lastDimension)) {
                double delta = position.get().distanceTo(lastPosition);
                if (delta <= TELEPORT_THRESHOLD) {
                    session.addDistance(delta);
                }
            }
            lastPosition = position.get();
            lastDimension = dimension;
        }
    }

    /** Player joined a singleplayer world. */
    public void onJoinWorld(String worldName) {
        StatsPrivacy privacy = privacy();
        if (!privacy.enabled()) {
            return;
        }
        startSession();
        lastPosition = null;
        if (privacy.trackWorlds() && worldName != null && !worldName.isBlank()) {
            session.worlds().add(truncate(worldName.trim()));
        }
    }

    /** Player joined a server. */
    public void onJoinServer(String address) {
        StatsPrivacy privacy = privacy();
        if (!privacy.enabled()) {
            return;
        }
        startSession();
        lastPosition = null;
        if (privacy.trackServers()) {
            hostname(address).ifPresent(session.servers()::add);
        }
    }

    /** Player left the world or server. */
    public void onLeave() {
        lastTickAt = 0;
        lastPosition = null;
        lastDimension = null;
    }

    public void onBlockBroken() {
        if (privacy().enabled()) {
            startSession();
            session.incrementBlocksBroken();
        }
    }

    public void onBlockPlaced() {
        if (privacy().enabled()) {
            startSession();
            session.incrementBlocksPlaced();
        }
    }

    public void onScreenshot() {
        if (privacy().enabled()) {
            startSession();
            session.incrementScreenshots();
        }
    }

    /**
     * Ends the session and records it when something happened and tracking is enabled.
     *
     * @return the record, or empty when nothing was recorded
     */
    public Optional<SessionRecord> endSession() {
        if (session == null) {
            return Optional.empty();
        }
        SessionRecord record = session.toRecord(clock.millis());
        session = null;
        onLeave();
        if (!privacy().enabled() || record.playtimeMs() == 0 && record.blocksBroken() == 0
                && record.blocksPlaced() == 0 && record.screenshots() == 0) {
            return Optional.empty();
        }
        store.record(record);
        store.save();
        return Optional.of(record);
    }

    /** Lower-case hostname without port or user info; empty for blank input. */
    public static Optional<String> hostname(String address) {
        if (address == null) {
            return Optional.empty();
        }
        String host = address.trim().toLowerCase(Locale.ROOT);
        int at = host.lastIndexOf('@');
        if (at >= 0) {
            host = host.substring(at + 1);
        }
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            host = end > 0 ? host.substring(1, end) : host.substring(1);
        } else {
            int colon = host.indexOf(':');
            if (colon >= 0) {
                host = host.substring(0, colon);
            }
        }
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        return host.isBlank() ? Optional.empty() : Optional.of(truncate(host));
    }

    private static String truncate(String value) {
        return value.length() > 64 ? value.substring(0, 64) : value;
    }
}
