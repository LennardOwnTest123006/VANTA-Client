package dev.vanta.launcher.core.modrinth;

import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.NetworkErrors;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The VANTA performance pack: Sodium (rendering), Lithium (game logic), FerriteCore (memory), ImmediatelyFast
 * (immediate-mode rendering), Entity Culling (hidden entities are not drawn) and Iris (shaders), plus their required
 * dependencies except Fabric API. Every version is resolved from Modrinth when the pack is installed
 * ({@link ModrinthService#resolve}): the newest version for Minecraft 1.21.11 and Fabric, downloaded only from the
 * URL Modrinth returns and verified with its SHA-512.
 *
 * <p>The pack never makes an install fail: a project without a compatible version, a failed download or Modrinth
 * being unreachable becomes a warning, and the regular install (or "Use with Minecraft Launcher") continues.
 * Cancellation still stops everything. A resolution is reused for {@value #CACHE_MINUTES} minutes, so the
 * confirmation dialog of "Use with Minecraft Launcher" and the install that follows ask Modrinth only once.</p>
 */
public final class PerformancePack {

    /** Modrinth slugs of the pack, in install order. */
    public static final List<String> SLUGS = List.of("sodium", "lithium", "ferrite-core", "immediatelyfast", "entityculling", "iris");
    /** How long a resolution is reused. */
    public static final int CACHE_MINUTES = 10;

    private static final Logger LOG = LauncherLog.get("PerformancePack");

    private final ModrinthService modrinth;
    private final Clock clock;
    private ModrinthService.Resolution cached;
    private Instant cachedAt = Instant.EPOCH;

    /**
     * @param modrinth Modrinth service of the VANTA instance
     * @param clock    clock (cache expiry)
     */
    public PerformancePack(final ModrinthService modrinth, final Clock clock) {
        this.modrinth = Objects.requireNonNull(modrinth, "modrinth");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** @return the Modrinth service */
    public ModrinthService modrinth() {
        return modrinth;
    }

    /**
     * Outcome of an install.
     *
     * @param applied  installed (or already present and verified) projects
     * @param warnings what was skipped and why
     */
    public record Outcome(List<ModrinthService.Applied> applied, List<String> warnings) {

        public Outcome {
            applied = List.copyOf(applied);
            warnings = List.copyOf(warnings);
        }

        /** @return the files of the pack in the instance */
        public List<Path> files() {
            return applied.stream().map(ModrinthService.Applied::path).toList();
        }
    }

    /**
     * Resolves the pack, never failing: problems become warnings of the resolution.
     *
     * @param token cancellation
     * @return resolution (possibly empty with warnings)
     * @throws InterruptedException when interrupted
     */
    public synchronized ModrinthService.Resolution resolve(final CancellationToken token) throws InterruptedException {
        final Instant now = clock.instant();
        if (cached != null && Duration.between(cachedAt, now).compareTo(Duration.ofMinutes(CACHE_MINUTES)) < 0) {
            return cached;
        }
        final List<ModrinthService.Root> roots = new ArrayList<>();
        for (String slug : SLUGS) {
            roots.add(new ModrinthService.Root(slug, ContentType.MOD));
        }
        ModrinthService.Resolution resolution;
        try {
            resolution = modrinth.resolve(roots, true, token);
        } catch (CancellationException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            resolution = new ModrinthService.Resolution(List.of(), List.of("Modrinth could not be reached: " + NetworkErrors.describe(e)));
        }
        if (resolution.items().isEmpty() && !resolution.warnings().isEmpty()) {
            // Nothing found at all (offline?): do not keep that for the next attempt.
            return resolution;
        }
        cached = resolution;
        cachedAt = now;
        return resolution;
    }

    /** Forgets the cached resolution. */
    public synchronized void invalidate() {
        cached = null;
    }

    /**
     * Installs the pack into the instance. Never throws for Modrinth or download problems (see the class description).
     *
     * @param downloads  byte progress
     * @param log        log lines
     * @param onResolved receives the number of projects that will be installed, before the first download
     * @param token      cancellation
     * @return outcome
     * @throws CancellationException when cancelled
     * @throws InterruptedException  when interrupted
     */
    public Outcome install(final DownloadProgressListener downloads, final Consumer<String> log, final java.util.function.IntConsumer onResolved,
                           final CancellationToken token) throws InterruptedException {
        final Consumer<String> out = log == null ? s -> { } : log;
        final ModrinthService.Resolution resolution = resolve(token);
        if (onResolved != null) {
            onResolved.accept(resolution.items().size());
        }
        ModrinthService.ApplyResult result;
        try {
            result = modrinth.apply(resolution, downloads, out, true, token);
        } catch (CancellationException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // A tolerant apply reports a failed modrinth.json write as a warning and keeps the applied list, so this is
            // the last resort (an index that cannot even be read, an unexpected runtime failure): nothing is known to be
            // in place, and the cause is named rather than hidden behind a bare path.
            LOG.log(Level.WARNING, "The performance pack could not be installed", e);
            result = new ModrinthService.ApplyResult(List.of(), List.of("the pack could not be installed: " + NetworkErrors.describe(e)));
        }
        for (String w : result.warnings()) {
            LOG.log(Level.WARNING, "Performance pack: {0}", w);
            out.accept("Performance pack: " + w);
        }
        return new Outcome(result.applied(), result.warnings());
    }
}
