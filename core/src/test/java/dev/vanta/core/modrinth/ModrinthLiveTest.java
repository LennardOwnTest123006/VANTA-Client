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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Live check against the real Modrinth API (network required). Excluded from {@code ./gradlew test}; run with
 * {@code ./gradlew liveTest}. CI runs it in the {@code client-gametest-performance} job: it resolves the Performance
 * pack for Minecraft 1.21.11 + Fabric exactly like the game does, downloads every planned file and checks that the
 * SHA-512 of each file on disk equals the value Modrinth published (the installer verifies it twice as well).
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
}
