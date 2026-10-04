package dev.vanta.core.config;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Ordered chain of JSON schema migrations for one store file.
 * <p>
 * Every persisted file carries a {@code schemaVersion}. A {@code Migrations} instance knows the version the running
 * code writes ({@link #currentVersion()}) and the steps that upgrade older files to it. Files written by a newer
 * version are left untouched and reported as {@link Status#NEWER_THAN_SUPPORTED} so the caller can decide whether to
 * read them tolerantly (the default) or refuse.
 */
public final class Migrations {

    /** Outcome of {@link #migrate(JsonObject)}. */
    public enum Status {
        /** The file already had the current version. */
        UP_TO_DATE,
        /** One or more steps were applied. */
        MIGRATED,
        /** The file was written by a newer version; it is returned unchanged. */
        NEWER_THAN_SUPPORTED
    }

    /** One upgrade step. */
    public record Step(int from, int to, UnaryOperator<JsonObject> transform) {
        public Step {
            if (to != from + 1) {
                throw new IllegalArgumentException("Migration steps must increase the version by exactly 1: "
                        + from + " -> " + to);
            }
            Objects.requireNonNull(transform, "transform");
        }
    }

    /** Result of a migration run. */
    public record Result(JsonObject json, int fromVersion, int toVersion, Status status) {
        /** True when the caller should persist the migrated JSON. */
        public boolean changed() {
            return status == Status.MIGRATED;
        }
    }

    private final int currentVersion;
    private final List<Step> steps;

    /**
     * @param currentVersion the version the running code writes (≥ 1)
     * @param steps          contiguous steps {@code 1→2, 2→3, …, current-1→current}
     */
    public Migrations(int currentVersion, List<Step> steps) {
        if (currentVersion < 1) {
            throw new IllegalArgumentException("currentVersion must be >= 1");
        }
        List<Step> sorted = new ArrayList<>(steps);
        sorted.sort((a, b) -> Integer.compare(a.from(), b.from()));
        int expected = 1;
        for (Step step : sorted) {
            if (step.from() != expected) {
                throw new IllegalArgumentException("Migration chain has a gap: expected a step from version "
                        + expected + " but found " + step.from());
            }
            expected = step.to();
        }
        if (!sorted.isEmpty() && expected != currentVersion) {
            throw new IllegalArgumentException("Migration chain ends at version " + expected
                    + " but the current version is " + currentVersion);
        }
        this.currentVersion = currentVersion;
        this.steps = Collections.unmodifiableList(sorted);
    }

    /** A chain with no steps (first schema version). */
    public static Migrations none(int currentVersion) {
        return new Migrations(currentVersion, List.of());
    }

    /** Convenience factory for a step. */
    public static Step step(int from, int to, UnaryOperator<JsonObject> transform) {
        return new Step(from, to, transform);
    }

    /** The version the running code writes. */
    public int currentVersion() {
        return currentVersion;
    }

    /** Registered steps in ascending order. */
    public List<Step> steps() {
        return steps;
    }

    /**
     * Upgrades {@code json} in place-copy semantics: the input object is not modified, a deep copy is returned.
     * A missing or malformed {@code schemaVersion} is treated as version 1.
     */
    public Result migrate(JsonObject json) {
        Objects.requireNonNull(json, "json");
        int from = JsonStore.schemaVersion(json, 1);
        JsonObject work = json.deepCopy();
        if (from > currentVersion) {
            return new Result(work, from, from, Status.NEWER_THAN_SUPPORTED);
        }
        if (from == currentVersion) {
            work.addProperty(JsonStore.SCHEMA_VERSION, currentVersion);
            return new Result(work, from, currentVersion, Status.UP_TO_DATE);
        }
        int version = from;
        for (Step step : steps) {
            if (step.from() == version) {
                work = Objects.requireNonNull(step.transform().apply(work), "migration returned null");
                version = step.to();
            }
        }
        if (version != currentVersion) {
            throw new IllegalStateException("No migration path from version " + from + " to " + currentVersion);
        }
        work.addProperty(JsonStore.SCHEMA_VERSION, currentVersion);
        return new Result(work, from, currentVersion, Status.MIGRATED);
    }
}
