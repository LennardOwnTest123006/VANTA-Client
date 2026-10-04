package dev.vanta.launcher.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code arguments} block of a version JSON.
 *
 * @param game game (main class) arguments
 * @param jvm  JVM arguments
 */
public record Arguments(List<Argument> game, List<Argument> jvm) {

    /** Empty arguments. */
    public static final Arguments EMPTY = new Arguments(List.of(), List.of());

    public Arguments {
        game = game == null ? List.of() : List.copyOf(game);
        jvm = jvm == null ? List.of() : List.copyOf(jvm);
    }

    /**
     * Appends another block (parent first, then child — the Fabric profile convention).
     *
     * @param child arguments appended after this block
     * @return combined arguments
     */
    public Arguments concat(final Arguments child) {
        if (child == null) {
            return this;
        }
        final List<Argument> g = new ArrayList<>(game);
        g.addAll(child.game());
        final List<Argument> j = new ArrayList<>(jvm);
        j.addAll(child.jvm());
        return new Arguments(g, j);
    }
}
