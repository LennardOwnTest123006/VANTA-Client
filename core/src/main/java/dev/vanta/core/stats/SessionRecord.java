package dev.vanta.core.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable summary of one play session, as persisted in {@code stats.json}.
 *
 * @param startedAt      epoch millis
 * @param endedAt        epoch millis
 * @param playtimeMs     time spent inside worlds
 * @param averageFps     mean of the fps samples (0 when none)
 * @param maxFps         highest fps sample
 * @param worlds         world names (empty when not tracked)
 * @param servers        server hostnames (empty when not tracked)
 * @param distanceBlocks blocks travelled
 * @param blocksBroken   blocks broken
 * @param blocksPlaced   blocks placed
 * @param screenshots    screenshots taken
 */
public record SessionRecord(long startedAt, long endedAt, long playtimeMs, double averageFps, int maxFps,
                            List<String> worlds, List<String> servers, double distanceBlocks, int blocksBroken,
                            int blocksPlaced, int screenshots) {
    public SessionRecord {
        worlds = List.copyOf(worlds);
        servers = List.copyOf(servers);
        playtimeMs = Math.max(0, playtimeMs);
        distanceBlocks = Math.max(0, distanceBlocks);
    }

    /** JSON form. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("startedAt", startedAt);
        o.addProperty("endedAt", endedAt);
        o.addProperty("playtimeMs", playtimeMs);
        o.addProperty("averageFps", averageFps);
        o.addProperty("maxFps", maxFps);
        o.add("worlds", strings(worlds));
        o.add("servers", strings(servers));
        o.addProperty("distanceBlocks", distanceBlocks);
        o.addProperty("blocksBroken", blocksBroken);
        o.addProperty("blocksPlaced", blocksPlaced);
        o.addProperty("screenshots", screenshots);
        return o;
    }

    /** Tolerant JSON read. */
    public static SessionRecord fromJson(JsonObject o) {
        return new SessionRecord(num(o, "startedAt"), num(o, "endedAt"), num(o, "playtimeMs"),
                dbl(o, "averageFps"), (int) num(o, "maxFps"), stringList(o, "worlds"), stringList(o, "servers"),
                dbl(o, "distanceBlocks"), (int) num(o, "blocksBroken"), (int) num(o, "blocksPlaced"),
                (int) num(o, "screenshots"));
    }

    static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    static List<String> stringList(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(key);
        if (e != null && e.isJsonArray()) {
            for (JsonElement item : e.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    out.add(item.getAsString());
                }
            }
        }
        return out;
    }

    static long num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : 0L;
    }

    static double dbl(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            double v = e.getAsDouble();
            return Double.isFinite(v) ? v : 0;
        }
        return 0;
    }
}
