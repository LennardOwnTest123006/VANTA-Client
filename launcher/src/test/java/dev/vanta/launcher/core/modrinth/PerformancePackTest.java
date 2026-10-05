package dev.vanta.launcher.core.modrinth;

import dev.vanta.launcher.core.install.InstallRequest;
import dev.vanta.launcher.core.install.InstallStep;
import dev.vanta.launcher.core.install.InstallListener;
import dev.vanta.launcher.core.install.InstallProgress;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.testutil.FakeWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The performance pack inside a full install: installed with the instance, never fatal, resolved once per few minutes.
 */
class PerformancePackTest {

    private static final OsInfo LINUX = new OsInfo("linux", "x64", "6.8");

    @TempDir
    Path tmp;
    private FakeWorld world;
    private LauncherPaths paths;

    @BeforeEach
    void start() throws Exception {
        world = new FakeWorld(false);
        paths = new LauncherPaths(tmp.resolve("data"));
    }

    @AfterEach
    void stop() {
        world.close();
    }

    @Test
    void aStandardInstallAddsThePackAsItsOwnStep() throws Exception {
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), Clock.systemUTC());
        final List<InstallStep> steps = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        final InstanceInfo info = wired.installer().install(InstallRequest.standard(), new InstallListener() {
            @Override
            public void onProgress(final InstallProgress p) {
                if (steps.isEmpty() || steps.get(steps.size() - 1) != p.step()) {
                    steps.add(p.step());
                }
                messages.add(p.message());
            }
        }, new CancellationToken());
        assertTrue(info.hasVantaClient());
        assertEquals(InstallStep.PERFORMANCE_PACK, steps.get(steps.size() - 2), steps.toString());
        assertEquals(InstallStep.FINALIZE, steps.get(steps.size() - 1));
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("Downloaded Iris Shaders 1.10.8+1.21.11-fabric")), messages.toString());
        assertEquals(6, ModrinthIndex.load(paths.modrinthIndexFile()).entries().size());
        try (var files = Files.list(paths.modsDir())) {
            assertEquals(8, files.count(), "Fabric API, VANTA Client and the six pack mods");
        }
        assertTrue(Files.isRegularFile(paths.instanceFile()), "the install finished");
    }

    @Test
    void withoutThePackOnlyFabricApiAndVantaAreInstalled() throws Exception {
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), Clock.systemUTC());
        wired.installer().install(InstallRequest.standard().withPerformancePack(false), InstallListener.NONE, new CancellationToken());
        try (var files = Files.list(paths.modsDir())) {
            assertEquals(2, files.count());
        }
        assertFalse(world.server().requests().stream().anyMatch(r -> r.contains("/modrinth/")), "Modrinth is not contacted");
    }

    @Test
    void modrinthUnreachableNeverFailsTheInstall() throws Exception {
        for (String slug : PerformancePack.SLUGS) {
            world.server().remove("modrinth/v2/project/" + slug + "/version");
        }
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), Clock.systemUTC());
        final List<String> log = new ArrayList<>();
        final InstanceInfo info = wired.installer().install(InstallRequest.standard(), new InstallListener() {
            @Override
            public void onLog(final String message) {
                log.add(message);
            }
        }, new CancellationToken());
        assertTrue(info.hasVantaClient(), "the regular install completed");
        assertTrue(log.stream().anyMatch(l -> l.startsWith("Performance pack: sodium: sodium was not found on Modrinth")), log.toString());
        assertTrue(log.stream().anyMatch(l -> l.equals("Performance pack: 0 mods in place, 6 skipped (see the warnings above)")), log.toString());
    }

    @Test
    void theResolutionIsReusedForTenMinutesUnlessItFoundNothing() throws Exception {
        final Instant[] now = {Instant.parse("2026-10-05T10:00:00Z")};
        final Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(final ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now[0];
            }
        };
        final PerformancePack pack = new PerformancePack(world.modrinth(paths, clock), clock);
        final ModrinthService.Resolution first = pack.resolve(CancellationToken.NONE);
        assertEquals(6, first.items().size());
        final int requests = world.server().totalHits();
        assertEquals(first, pack.resolve(CancellationToken.NONE));
        assertEquals(requests, world.server().totalHits(), "plan and install ask Modrinth once");
        now[0] = now[0].plus(Duration.ofMinutes(PerformancePack.CACHE_MINUTES + 1));
        pack.resolve(CancellationToken.NONE);
        assertTrue(world.server().totalHits() > requests);

        pack.invalidate();
        world.close();
        final ModrinthService.Resolution offline = pack.resolve(CancellationToken.NONE);
        assertTrue(offline.items().isEmpty());
        assertFalse(offline.warnings().isEmpty());
        final PerformancePack.Outcome outcome = pack.install(DownloadProgressListener.NONE, s -> { }, n -> { }, CancellationToken.NONE);
        assertTrue(outcome.applied().isEmpty());
        assertFalse(outcome.warnings().isEmpty());
    }

    @Test
    void cancellationStillStopsThePack() {
        final PerformancePack pack = new PerformancePack(world.modrinth(paths, Clock.systemUTC()), Clock.systemUTC());
        final CancellationToken token = new CancellationToken();
        token.cancel();
        assertThrows(CancellationException.class, () -> pack.install(DownloadProgressListener.NONE, s -> { }, n -> { }, token));
    }
}
