package dev.vanta.core.stats;

import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Aggregates over every recorded session.
 *
 * @param playtimeMs     total time in worlds
 * @param sessions       number of sessions
 * @param distanceBlocks total distance
 * @param blocksBroken   total
 * @param blocksPlaced   total
 * @param screenshots    total
 * @param maxFps         best fps ever sampled
 * @param worlds         distinct world names
 * @param servers        distinct server hostnames
 */
public record LifetimeStats(long playtimeMs, int sessions, double distanceBlocks, long blocksBroken, long blocksPlaced,
                            int screenshots, int maxFps, Set<String> worlds, Set<String> servers) {
    /** Nothing recorded yet. */
    public static final LifetimeStats EMPTY = new LifetimeStats(0, 0, 0, 0, 0, 0, 0, Set.of(), Set.of());

    /** Most distinct names kept per list. */
    public static final int MAX_NAMES = 200;

    public LifetimeStats {
        worlds = Set.copyOf(new LinkedHashSet<>(worlds));
        servers = Set.copyOf(new LinkedHashSet<>(servers));
    }

    /** Aggregates with one more session. */
    public LifetimeStats plus(SessionRecord session) {
        Set<String> w = new LinkedHashSet<>(worlds);
        w.addAll(session.worlds());
        Set<String> s = new LinkedHashSet<>(servers);
        s.addAll(session.servers());
        return new LifetimeStats(playtimeMs + session.playtimeMs(), sessions + 1,
                distanceBlocks + session.distanceBlocks(), blocksBroken + session.blocksBroken(),
                blocksPlaced + session.blocksPlaced(), screenshots + session.screenshots(),
                Math.max(maxFps, session.maxFps()), cap(w), cap(s));
    }

    private static Set<String> cap(Set<String> names) {
        if (names.size() <= MAX_NAMES) {
            return names;
        }
        Set<String> out = new LinkedHashSet<>();
        for (String name : names) {
            if (out.size() >= MAX_NAMES) {
                break;
            }
            out.add(name);
        }
        return out;
    }

    /** JSON form. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("playtimeMs", playtimeMs);
        o.addProperty("sessions", sessions);
        o.addProperty("distanceBlocks", distanceBlocks);
        o.addProperty("blocksBroken", blocksBroken);
        o.addProperty("blocksPlaced", blocksPlaced);
        o.addProperty("screenshots", screenshots);
        o.addProperty("maxFps", maxFps);
        o.add("worlds", SessionRecord.strings(List.copyOf(worlds)));
        o.add("servers", SessionRecord.strings(List.copyOf(servers)));
        return o;
    }

    /** Tolerant JSON read. */
    public static LifetimeStats fromJson(JsonObject o) {
        if (o == null) {
            return EMPTY;
        }
        return new LifetimeStats(SessionRecord.num(o, "playtimeMs"), (int) SessionRecord.num(o, "sessions"),
                SessionRecord.dbl(o, "distanceBlocks"), SessionRecord.num(o, "blocksBroken"),
                SessionRecord.num(o, "blocksPlaced"), (int) SessionRecord.num(o, "screenshots"),
                (int) SessionRecord.num(o, "maxFps"), new LinkedHashSet<>(SessionRecord.stringList(o, "worlds")),
                new LinkedHashSet<>(SessionRecord.stringList(o, "servers")));
    }

    /** Copy without world and server names (privacy setting turned off later). */
    public LifetimeStats withoutNames(boolean dropWorlds, boolean dropServers) {
        return new LifetimeStats(playtimeMs, sessions, distanceBlocks, blocksBroken, blocksPlaced, screenshots, maxFps,
                dropWorlds ? Set.of() : worlds, dropServers ? Set.of() : servers);
    }
}
