package dev.vanta.core.perf;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vanta.core.bridge.OptionsBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.notifications.NotificationCenter;
import dev.vanta.core.settings.SettingsStore;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * The one-time frame-rate uncap (client 1.5.0). A new game folder starts with Minecraft's own defaults, VSync on and
 * Max Framerate 120, and VANTA's frame-rate choice (default {@link FpsLimitPreset#UNLIMITED}) only follows what the
 * game has, so a 60 Hz monitor held the game at 60 FPS even with "Unlimited" picked in Video Settings or Sodium.
 * <p>
 * When the game has started (its options exist), {@link #runOnce(String)} checks the vanilla options once: with VSync
 * on or Max Framerate below Unlimited it applies {@link FpsLimitPreset#UNLIMITED} through
 * {@link PerformanceCenter#applyFpsLimit} (the path of Reset and Boost FPS: VSync off, Max Framerate 260, the choice
 * stored, {@code options.txt} saved once) and queues one notification. Nothing else is written: no graphics preset, no
 * render distance, no other option.
 * <p>
 * The run is recorded in {@code config/vanta/frame-rate.json} ({@code uncappedFor}: the client version that ran it,
 * plus what it found). Once recorded it never runs again, on this version or any later one, so the player's own choice
 * afterwards (VSync back on, a limit) stays. A game folder without the file (a fresh install, or an update from 1.4.x
 * or older) gets the check once.
 * <p>
 * Render-thread only, like every core service.
 */
public final class FrameRateUncap {
    /** Schema of {@code frame-rate.json}. */
    public static final int SCHEMA = 1;

    /** What {@link #runOnce(String)} did. */
    public enum Outcome {
        /** VSync was on or the limit below Unlimited: both are now off / Unlimited. */
        UNCAPPED,
        /** The options were already uncapped; only the record was written. */
        ALREADY_UNCAPPED,
        /** The check ran before (the record exists); nothing was touched. */
        ALREADY_DONE,
        /** The running game does not expose Max Framerate and VSync (yet); nothing recorded, the next start retries. */
        OPTIONS_UNAVAILABLE
    }

    /**
     * The recorded check.
     *
     * @param clientVersion the client version that ran it ({@code uncappedFor})
     * @param atMillis      when it ran (epoch millis)
     * @param vsyncBefore   VSync as the check found it
     * @param limitBefore   Max Framerate as the check found it (260 = unlimited)
     * @param changed       true when the check uncapped the options
     */
    public record Record(String clientVersion, long atMillis, boolean vsyncBefore, int limitBefore, boolean changed) {
        public Record {
            Objects.requireNonNull(clientVersion, "clientVersion");
        }
    }

    private final PerformanceCenter performance;
    private final OptionsBridge options;
    private final SettingsStore settings;
    private final NotificationCenter notifications;
    private final JsonStore store;
    private final Path file;
    private final Clock clock;
    private Record record;
    private boolean noticePending;

    public FrameRateUncap(PerformanceCenter performance, OptionsBridge options, SettingsStore settings,
                          NotificationCenter notifications, JsonStore store, Path file, Clock clock) {
        this.performance = Objects.requireNonNull(performance, "performance");
        this.options = Objects.requireNonNull(options, "options");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.store = Objects.requireNonNull(store, "store");
        this.file = Objects.requireNonNull(file, "file");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Reads {@code frame-rate.json}. Writes nothing and touches no option (the game options may not exist yet). */
    public void load() {
        record = store.readObject(file).flatMap(FrameRateUncap::fromJson).orElse(null);
    }

    /** The recorded check, empty while it never ran in this game folder. */
    public Optional<Record> record() {
        return Optional.ofNullable(record);
    }

    /** True while the "Frame rate uncapped" notification waits for a screen the player can see. */
    public boolean isNoticePending() {
        return noticePending;
    }

    /**
     * The check itself; call once the game has started and its options exist. Runs only while no record exists.
     *
     * @param clientVersion the running client version, recorded as {@code uncappedFor}
     */
    public Outcome runOnce(String clientVersion) {
        Objects.requireNonNull(clientVersion, "clientVersion");
        if (record != null) {
            return Outcome.ALREADY_DONE;
        }
        if (!options.supports(VanillaOption.FRAMERATE_LIMIT) || !options.supports(VanillaOption.VSYNC)
                || options.get(VanillaOption.FRAMERATE_LIMIT).isEmpty() || options.get(VanillaOption.VSYNC).isEmpty()) {
            return Outcome.OPTIONS_UNAVAILABLE;
        }
        boolean vsync = options.getBoolean(VanillaOption.VSYNC, false);
        int limit = options.getInt(VanillaOption.FRAMERATE_LIMIT, FpsLimitPreset.VANILLA_UNLIMITED);
        boolean capped = isCapped(vsync, limit);
        if (capped) {
            performance.applyFpsLimit(FpsLimitPreset.UNLIMITED);
            noticePending = true;
        } else {
            performance.reconcileFpsLimitChoice(); // settings.json only: the choice says Unlimited like the game
        }
        settings.saveIfDirty();
        record = new Record(clientVersion, clock.millis(), vsync, limit, capped);
        save();
        if (capped) {
            CoreLog.info("Frame rate uncapped once for client {}: VSync {} → off, Max Framerate {} → Unlimited",
                    clientVersion, vsync ? "on" : "off", limit);
        } else {
            CoreLog.info("Frame rate already uncapped (VSync off, Max Framerate Unlimited); recorded for client {}",
                    clientVersion);
        }
        return capped ? Outcome.UNCAPPED : Outcome.ALREADY_UNCAPPED;
    }

    /** True when VSync or a Max Framerate below Unlimited caps the frame rate. */
    public static boolean isCapped(boolean vsync, int framerateLimit) {
        return vsync || framerateLimit < FpsLimitPreset.VANILLA_UNLIMITED;
    }

    /**
     * Posts the "Frame rate uncapped" notification when the check uncapped the options and it was not shown yet. The
     * host calls this where the player can see a toast (the VANTA main menu, a loaded world): at start-up itself the
     * loading screen still covers the game.
     *
     * @return true when the notification was posted now
     */
    public boolean postPendingNotice() {
        if (!noticePending) {
            return false;
        }
        noticePending = false;
        notifications.frameRateUncapped();
        return true;
    }

    // ---- JSON --------------------------------------------------------------------------------------------------

    private void save() {
        try {
            store.writeObject(file, toJson(record));
        } catch (UncheckedIOException e) {
            CoreLog.warn(e, "Could not write {}; the frame-rate check runs again next start", file);
        }
    }

    /** Serialises a record. */
    public static JsonObject toJson(Record record) {
        Objects.requireNonNull(record, "record");
        JsonObject json = new JsonObject();
        json.addProperty(JsonStore.SCHEMA_VERSION, SCHEMA);
        json.addProperty("uncappedFor", record.clientVersion());
        json.addProperty("atMillis", record.atMillis());
        json.addProperty("vsyncBefore", record.vsyncBefore());
        json.addProperty("limitBefore", record.limitBefore());
        json.addProperty("changed", record.changed());
        return json;
    }

    /** Parses a record; a file without a usable {@code uncappedFor} counts as no record. */
    public static Optional<Record> fromJson(JsonObject json) {
        if (json == null) {
            return Optional.empty();
        }
        JsonElement version = json.get("uncappedFor");
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isString()
                || version.getAsString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Record(version.getAsString(), number(json, "atMillis").longValue(),
                bool(json, "vsyncBefore"), number(json, "limitBefore").intValue(), bool(json, "changed")));
    }

    private static Number number(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsNumber() : 0;
    }

    private static boolean bool(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }
}
