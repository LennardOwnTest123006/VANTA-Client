package dev.vanta.core.modrinth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.VantaVersion;
import dev.vanta.core.config.JsonStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Live check against the real Modrinth API (network required). Excluded from {@code ./gradlew test}; run with
 * {@code ./gradlew liveTest}. CI runs it in the {@code client-gametest-performance} job: it resolves the Performance
 * pack for Minecraft 1.21.11 + Fabric exactly like the game does, downloads every planned file and checks that the
 * SHA-512 of each file on disk equals the value Modrinth published (the installer verifies it twice as well). It also
 * asks the installer for a real mod that stops Minecraft 1.21.11 while it starts, and checks that it is refused.
 */
@Tag("live")
class ModrinthLiveTest {
    @TempDir
    Path gameDir;

    @Test
    void performancePackResolvesDownloadsAndVerifiesAgainstTheRealApi() throws Exception {
        ModrinthClient client = new ModrinthClient(ModrinthClient.Config.defaults(VantaVersion.CLIENT));

        ModrinthSearchResult search = client.search(SearchRequest.of("sodium", ModrinthProjectType.MOD));
        assertTrue(search.hits().stream().anyMatch(h -> h.slug().equals("sodium")), "search finds Sodium");

        InstallPlan plan = new InstallPlanner(client).plan(PerformancePack.requests(PerformancePack.slugs()),
                new InstallState(java.util.Map.of(), Set.of(), true));
        List<String> report = new ArrayList<>();
        for (PlannedInstall item : plan.installs()) {
            report.add(item.slug() + " " + item.version().versionNumber() + " (" + item.version().id() + ") "
                    + item.file().filename() + " sha512=" + item.file().sha512());
        }
        for (PlanNote note : plan.notes()) {
            report.add("note: " + note.kind() + " " + note.subject() + " " + note.detail());
        }
        report.forEach(line -> System.out.println("[modrinth-live] " + line));

        for (String slug : PerformancePack.slugs()) {
            ModrinthProject project = client.project(slug);
            boolean planned = plan.find(project.id()).isPresent();
            boolean skipped = plan.notes(PlanNote.Kind.NO_COMPATIBLE_VERSION).stream()
                    .anyMatch(n -> n.subject().equals(project.title()));
            assertTrue(planned || skipped, slug + " is neither planned nor reported as skipped: " + report);
        }
        assertTrue(plan.find(ModrinthConstants.FABRIC_API_PROJECT_ID).isEmpty(), "Fabric API is never downloaded");
        assertTrue(plan.installs().stream().anyMatch(i -> i.slug().equals("sodium")), "Sodium exists for 1.21.11");

        ModrinthLibrary library = new ModrinthLibrary(gameDir, new JsonStore(Clock.systemUTC()));
        InstallResult result = new ModrinthInstaller(client, library, Clock.systemUTC()).execute(plan, null);
        assertTrue(result.failed().isEmpty(), "every download verified: " + result.notes());
        assertEquals(plan.installs().size(), result.installed().size());
        for (PlannedInstall item : result.installed()) {
            Path file = gameDir.resolve(item.relativeFile());
            assertTrue(Files.isRegularFile(file), item.relativeFile());
            String actual = Sha512.hex(file);
            assertEquals(item.file().sha512(), actual, "SHA-512 of " + item.relativeFile());
            assertEquals(actual, library.index().find(item.projectId()).orElseThrow().sha512());
            System.out.println("[modrinth-live] verified " + item.relativeFile() + " (" + Files.size(file) + " bytes)");
        }
    }

    /**
     * Smart FPS Booster ({@code smart-fps-booster}) lists Minecraft 1.21.11 on Modrinth, but both of its Fabric versions
     * for it (hE70j3c1, built for 1.21.4, and XhY98l33, built for 1.21.8) create key bindings with the constructor
     * Minecraft removed in 1.21.9, so the game stops while starting (NoSuchMethodError in its client entrypoint). The
     * in-game installer must refuse it as {@link ModrinthException.Kind#INCOMPATIBLE} and leave no jar in mods/.
     */
    @Test
    void modBuiltForAnOlderMinecraftIsRefusedByTheRealInstaller() throws Exception {
        ModrinthClient client = new ModrinthClient(ModrinthClient.Config.defaults(VantaVersion.CLIENT));
        InstallPlan plan = new InstallPlanner(client, "1.21.11")
                .plan(List.of(InstallRequest.of("smart-fps-booster")), InstallState.none());
        PlannedInstall item = plan.installs().stream().filter(i -> i.slug().equals("smart-fps-booster")).findFirst()
                .orElseThrow(() -> new AssertionError("smart-fps-booster is not planned for 1.21.11: " + plan.notes()));
        System.out.println("[modrinth-live] smart-fps-booster " + item.version().versionNumber() + " ("
                + item.version().id() + ") " + item.file().filename());
        assertTrue(Set.of("hE70j3c1", "XhY98l33").contains(item.version().id()), "Smart FPS Booster has a version "
                + "this case does not know (" + item.version().id() + " " + item.file().filename() + "); check "
                + "whether it still crashes Minecraft 1.21.11 and update this test");

        ModrinthLibrary library = new ModrinthLibrary(gameDir, new JsonStore(Clock.systemUTC()));
        InstallResult result = new ModrinthInstaller(client, library, Clock.systemUTC()).execute(plan, null);
        result.notes().forEach(n -> System.out.println("[modrinth-live] note: " + n.kind() + " " + n.subject() + " "
                + n.detail()));

        assertTrue(result.installed().stream().noneMatch(i -> i.slug().equals("smart-fps-booster")),
                "Smart FPS Booster must not be installed");
        assertTrue(result.failed().stream().anyMatch(i -> i.slug().equals("smart-fps-booster")),
                "Smart FPS Booster is reported as not installed");
        String incompatible = ModrinthInstaller.errorText(
                new ModrinthException(ModrinthException.Kind.INCOMPATIBLE, item.file().filename()));
        assertTrue(result.notes().stream().anyMatch(n -> n.subject().equals(item.title())
                && n.detail().equals(incompatible)), "refused as INCOMPATIBLE: " + result.notes());
        assertTrue(library.index().find(item.projectId()).isEmpty(), "not recorded in modrinth.json");
        Path mods = library.directory(ModrinthProjectType.MOD);
        if (Files.isDirectory(mods)) {
            try (Stream<Path> files = Files.list(mods)) {
                List<String> left = files.map(f -> f.getFileName().toString())
                        .filter(n -> n.startsWith("smart-fps-booster")).toList();
                assertTrue(left.isEmpty(), "no Smart FPS Booster file in mods/: " + left);
            }
        }
    }
}
