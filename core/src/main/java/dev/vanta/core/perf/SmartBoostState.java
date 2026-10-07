package dev.vanta.core.perf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.config.JsonStore;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What Smart Boost remembers between games, stored in {@code config/vanta/smart-boost.json}:
 * <ul>
 *   <li>{@code lastRunClientVersion} / {@code lastRunAtMillis}: the automatic run happens once per installed client
 *       version (first install and every update);</li>
 *   <li>{@code result}: the preset the last completed run picked and what it measured;</li>
 *   <li>{@code owned}: every vanilla option Smart Boost wrote, with the value it wrote. An option whose live value no
 *       longer matches was changed by the player (Video Settings, Sodium's screen, a profile, a VANTA preset) and moves
 *       to {@code released} for good;</li>
 *   <li>{@code released}: options automatic runs never touch again (only the explicit Re-tune clears this);</li>
 *   <li>{@code undo}: the value each owned option had before Smart Boost first wrote it;</li>
 *   <li>{@code graphicsAtWrite}: the graphics preset the game reported after Smart Boost's last write ({@code ""} for
 *       the game's "custom"). A different Fast / Fancy / Fabulous later means the player picked a graphics preset;</li>
 *   <li>{@code adaptiveCeiling}: the render distance the run picked; the continuous mode never goes above it.</li>
 * </ul>
 * Reading is tolerant (a broken file is moved aside by {@link JsonStore} and Smart Boost starts fresh).
 */
public final class SmartBoostState {
    /** Schema of the file. */
    public static final int SCHEMA = 1;

    /**
     * Result of a completed run.
     *
     * @param preset          preset picked
     * @param targetFps       frame rate it aimed for
     * @param p50Fps          median frame rate measured with that preset
     * @param onePercentLowFps 1 % low measured with that preset
     */
    public record Result(PerformancePreset preset, int targetFps, double p50Fps, double onePercentLowFps) {
        public Result {
            Objects.requireNonNull(preset, "preset");
        }
    }

    private String lastRunClientVersion;
    private long lastRunAtMillis;
    private Result result;
    private final Map<VanillaOption, Object> owned = new EnumMap<>(VanillaOption.class);
    private final Set<VanillaOption> released = EnumSet.noneOf(VanillaOption.class);
    private final Map<VanillaOption, Object> undo = new EnumMap<>(VanillaOption.class);
    private String graphicsAtWrite;
    private boolean graphicsPresetChosen;
    private int adaptiveCeiling;

    // ---- accessors ---------------------------------------------------------------------------------------------

    public Optional<String> lastRunClientVersion() {
        return Optional.ofNullable(lastRunClientVersion);
    }

    public long lastRunAtMillis() {
        return lastRunAtMillis;
    }

    public Optional<Result> result() {
        return Optional.ofNullable(result);
    }

    /** Options Smart Boost wrote, with the value it wrote (read-only view). */
    public Map<VanillaOption, Object> owned() {
        return Collections.unmodifiableMap(owned);
    }

    /** Options the player took over (read-only view). */
    public Set<VanillaOption> released() {
        return Collections.unmodifiableSet(released);
    }

    /** Values before Smart Boost's first write (read-only view). */
    public Map<VanillaOption, Object> undo() {
        return Collections.unmodifiableMap(undo);
    }

    /** Graphics preset reported after the last write; empty when never recorded, {@code ""} for "custom". */
    public Optional<String> graphicsAtWrite() {
        return Optional.ofNullable(graphicsAtWrite);
    }

    /** True after the player picked a graphics preset that released every option. */
    public boolean graphicsPresetChosen() {
        return graphicsPresetChosen;
    }

    /** Render distance ceiling of the continuous mode, 0 when none. */
    public int adaptiveCeiling() {
        return adaptiveCeiling;
    }

    public boolean isOwned(VanillaOption option) {
        return owned.containsKey(option);
    }

    public boolean isReleased(VanillaOption option) {
        return released.contains(option);
    }

    // ---- mutation (package-private: only the tuner changes the state) ------------------------------------------

    void completeRun(String clientVersion, long atMillis, Result runResult) {
        lastRunClientVersion = clientVersion;
        lastRunAtMillis = atMillis;
        if (runResult != null) {
            result = runResult;
        }
    }

    /** Records a write; the first write of an option also remembers {@code before} for Undo. */
    void own(VanillaOption option, Object written, Object before) {
        if (!owned.containsKey(option) && !undo.containsKey(option) && before != null) {
            undo.put(option, before);
        }
        owned.put(option, written);
        released.remove(option);
    }

    /** The player changed the option: Smart Boost lets go of it for good. */
    void release(VanillaOption option) {
        owned.remove(option);
        undo.remove(option);
        released.add(option);
    }

    /** Drops an option from {@code owned} and {@code undo} without marking it released (Undo, revert). */
    void forget(VanillaOption option) {
        owned.remove(option);
        undo.remove(option);
    }

    /** Re-tune: the player asked Smart Boost to take over again. */
    void clearReleased() {
        released.clear();
        graphicsPresetChosen = false;
    }

    void setGraphicsAtWrite(String graphics) {
        graphicsAtWrite = graphics;
    }

    void setGraphicsPresetChosen(boolean chosen) {
        graphicsPresetChosen = chosen;
    }

    void setAdaptiveCeiling(int ceiling) {
        adaptiveCeiling = Math.max(0, ceiling);
    }

    // ---- JSON --------------------------------------------------------------------------------------------------

    /** Serialises the state. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(JsonStore.SCHEMA_VERSION, SCHEMA);
        if (lastRunClientVersion != null) {
            json.addProperty("lastRunClientVersion", lastRunClientVersion);
        }
        json.addProperty("lastRunAtMillis", lastRunAtMillis);
        if (result != null) {
            JsonObject r = new JsonObject();
            r.addProperty("preset", result.preset().id());
            r.addProperty("target", result.targetFps());
            r.addProperty("p50", Math.round(result.p50Fps() * 10) / 10.0);
            r.addProperty("onePercentLow", Math.round(result.onePercentLowFps() * 10) / 10.0);
            json.add("result", r);
        }
        json.add("owned", values(owned));
        JsonArray releasedJson = new JsonArray();
        for (VanillaOption option : released) {
            releasedJson.add(option.id());
        }
        json.add("released", releasedJson);
        json.add("undo", values(undo));
        if (graphicsAtWrite != null) {
            json.addProperty("graphicsAtWrite", graphicsAtWrite);
        }
        json.addProperty("graphicsPresetChosen", graphicsPresetChosen);
        json.addProperty("adaptiveCeiling", adaptiveCeiling);
        return json;
    }

    /** Parses a state; unknown options and malformed values are skipped. */
    public static SmartBoostState fromJson(JsonObject json) {
        SmartBoostState state = new SmartBoostState();
        if (json == null) {
            return state;
        }
        state.lastRunClientVersion = string(json, "lastRunClientVersion");
        state.lastRunAtMillis = number(json, "lastRunAtMillis").map(Number::longValue).orElse(0L);
        JsonElement r = json.get("result");
        if (r != null && r.isJsonObject()) {
            JsonObject o = r.getAsJsonObject();
            Optional<PerformancePreset> preset = Optional.ofNullable(string(o, "preset"))
                    .flatMap(PerformancePreset::fromId);
            preset.ifPresent(p -> state.result = new Result(p,
                    number(o, "target").map(Number::intValue).orElse(PerformanceCenter.DEFAULT_TARGET_FPS),
                    number(o, "p50").map(Number::doubleValue).orElse(0.0),
                    number(o, "onePercentLow").map(Number::doubleValue).orElse(0.0)));
        }
        readValues(json.get("owned"), state.owned);
        readValues(json.get("undo"), state.undo);
        JsonElement rel = json.get("released");
        if (rel != null && rel.isJsonArray()) {
            for (JsonElement e : rel.getAsJsonArray()) {
                if (e.isJsonPrimitive()) {
                    VanillaOption.fromId(e.getAsString()).ifPresent(state.released::add);
                }
            }
        }
        for (VanillaOption option : state.released) {
            state.owned.remove(option);
            state.undo.remove(option);
        }
        state.graphicsAtWrite = string(json, "graphicsAtWrite");
        JsonElement chosen = json.get("graphicsPresetChosen");
        state.graphicsPresetChosen = chosen != null && chosen.isJsonPrimitive()
                && chosen.getAsJsonPrimitive().isBoolean() && chosen.getAsBoolean();
        state.adaptiveCeiling = number(json, "adaptiveCeiling").map(Number::intValue).orElse(0);
        return state;
    }

    /** Loads the state from {@code file}; missing or broken files give a fresh state. */
    public static SmartBoostState load(JsonStore store, Path file) {
        return store.readObject(file).map(SmartBoostState::fromJson).orElseGet(SmartBoostState::new);
    }

    /** Writes the state atomically; a failure is logged, never thrown into the game loop. */
    public void save(JsonStore store, Path file) {
        try {
            store.writeObject(file, toJson());
        } catch (UncheckedIOException e) {
            CoreLog.warn(e, "Could not write {}", file);
        }
    }

    private static JsonObject values(Map<VanillaOption, Object> map) {
        JsonObject json = new JsonObject();
        for (Map.Entry<VanillaOption, Object> entry : map.entrySet()) {
            Object v = entry.getValue();
            if (v instanceof Boolean b) {
                json.add(entry.getKey().id(), new JsonPrimitive(b));
            } else if (v instanceof Number n) {
                json.add(entry.getKey().id(), new JsonPrimitive(n));
            } else if (v != null) {
                json.add(entry.getKey().id(), new JsonPrimitive(v.toString()));
            }
        }
        return json;
    }

    private static void readValues(JsonElement element, Map<VanillaOption, Object> into) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            Optional<VanillaOption> option = VanillaOption.fromId(entry.getKey());
            if (option.isEmpty() || !entry.getValue().isJsonPrimitive()) {
                continue;
            }
            JsonPrimitive p = entry.getValue().getAsJsonPrimitive();
            Object raw = p.isBoolean() ? (Object) p.getAsBoolean()
                    : p.isNumber() ? (Object) p.getAsDouble() : p.getAsString();
            option.get().normalize(raw).ifPresent(v -> into.put(option.get(), v));
        }
    }

    private static String string(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    private static Optional<Number> number(JsonObject json, String key) {
        JsonElement e = json.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return Optional.empty();
        }
        return Optional.of(e.getAsDouble());
    }
}
