package dev.vanta.core.modrinth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstallPlannerTest {
    private FakeModrinthServer server;
    private ModrinthFixtures fx;
    private InstallPlanner planner;

    @BeforeEach
    void start() throws Exception {
        server = new FakeModrinthServer();
        fx = new ModrinthFixtures(server);
        planner = new InstallPlanner(server.client(new ArrayList<Duration>()));
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private static List<String> ids(InstallPlan plan) {
        return plan.installs().stream().map(PlannedInstall::projectId).toList();
    }

    @Test
    void performancePackResolvesEveryMemberAndSkipsWhatHasNoVersion() {
        fx.performancePack();
        InstallPlan plan = planner.plan(PerformancePack.requests(PerformancePack.slugs()), InstallState.none());
        assertEquals(List.of("AANobbMI", "LITHIUM1", "IMMFAST1", "ENTCULL1", "YL57xq9U"), ids(plan));
        PlannedInstall sodium = plan.find("AANobbMI").orElseThrow();
        assertEquals("rkdTcxoT", sodium.version().id(), "newest release, not the newer beta");
        assertEquals(List.of("YL57xq9U"), sodium.requiredBy(), "Iris needs Sodium");
        assertTrue(sodium.root());
        assertEquals(List.of("AANobbMI"), plan.find("YL57xq9U").orElseThrow().dependsOn());
        assertEquals(1, plan.notes(PlanNote.Kind.NO_COMPATIBLE_VERSION).size());
        assertEquals("FerriteCore", plan.notes(PlanNote.Kind.NO_COMPATIBLE_VERSION).get(0).subject());
        assertEquals(1, plan.notes(PlanNote.Kind.FABRIC_API_PRESENT).size(), "EntityCulling's Fabric API is not downloaded");
        assertTrue(server.requests("/v2/project/P7dR8mSH").isEmpty(), "Fabric API is never even looked up");
        assertEquals(ModrinthProjectType.MOD, plan.find("ENTCULL1").orElseThrow().type());
        assertTrue(plan.totalBytes() > 0);
    }

    @Test
    void requiredDependencyIsInstalledInTheVersionItPins() {
        fx.performancePack();
        InstallPlan plan = planner.plan(List.of(InstallRequest.of("iris")), InstallState.none());
        assertEquals(List.of("AANobbMI", "YL57xq9U"), ids(plan), "dependency first");
        PlannedInstall sodium = plan.installs().get(0);
        assertFalse(sodium.root());
        assertEquals("rkdTcxoT", sodium.version().id());
        assertEquals("mods/sodium-fabric-0.8.14+mc1.21.11.jar", sodium.relativeFile());
    }

    @Test
    void pinnedDependencyOverridesTheAutomaticChoice() {
        fx.performancePack();
        // Iris pins Sodium's beta here: the pin wins over the newest release chosen for the Sodium request.
        String iris = fx.load("versions-iris.json").replace("\"version_id\":\"rkdTcxoT\"", "\"version_id\":\"RB7CDjTS\"");
        server.json("/v2/project/YL57xq9U/version", iris);
        InstallPlan plan = planner.plan(List.of(InstallRequest.of("sodium"), InstallRequest.of("iris")),
                InstallState.none());
        assertEquals("RB7CDjTS", plan.find("AANobbMI").orElseThrow().version().id());
        assertTrue(plan.find("AANobbMI").orElseThrow().root());
    }

    @Test
    void installedProjectsAreSkippedAndDisabledDependenciesEnabled() {
        fx.performancePack();
        InstallState state = new InstallState(Map.of("AANobbMI", false), Set.of(), true);
        InstallPlan plan = planner.plan(List.of(InstallRequest.of("iris")), state);
        assertEquals(List.of("YL57xq9U"), ids(plan));
        assertEquals(List.of("AANobbMI"), plan.enableExisting());
        assertEquals(Set.of("YL57xq9U"), plan.requiredByExisting().get("AANobbMI"));

        InstallPlan again = planner.plan(List.of(InstallRequest.of("sodium")), new InstallState(Map.of("AANobbMI", true),
                Set.of(), true));
        assertTrue(again.isEmpty());
        assertEquals(1, again.notes(PlanNote.Kind.ALREADY_INSTALLED).size());
    }

    @Test
    void loadedPackMembersAreNeverPlannedNotEvenAsDependencies() {
        fx.performancePack();
        // Sodium is loaded from a jar Modrinth does not know (CurseForge build): no index entry, no hash hit.
        InstallState state = new InstallState(Map.of(), Set.of(), Set.of("sodium"), true);
        InstallPlan plan = planner.plan(List.of(InstallRequest.of("sodium"), InstallRequest.of("iris")), state);
        assertEquals(List.of("YL57xq9U"), ids(plan), "Iris alone; its loaded dependency is not downloaded again");
        assertEquals(1, plan.notes(PlanNote.Kind.ALREADY_INSTALLED).size());
        assertTrue(plan.enableExisting().isEmpty());
        assertTrue(plan.requiredByExisting().isEmpty(), "a jar VANTA does not manage gets no index link");
    }

    @Test
    void manuallyInstalledProjectsCountAsPresent() {
        fx.performancePack();
        InstallPlan plan = planner.plan(List.of(InstallRequest.of("iris")), new InstallState(Map.of(),
                Set.of("AANobbMI"), true));
        assertEquals(List.of("YL57xq9U"), ids(plan));
        assertTrue(plan.requiredByExisting().isEmpty(), "files VANTA does not manage get no index links");
    }

    @Test
    void fabricApiIsInstalledWhenItIsMissing() {
        fx.performancePack();
        fx.simpleMod(ModrinthConstants.FABRIC_API_PROJECT_ID, "fabric-api", "Fabric API", "FAPIV001",
                "0.141.6+1.21.11", "fabric-api-0.141.6+1.21.11.jar");
        InstallPlan plan = planner.plan(List.of(InstallRequest.of("entityculling")),
                new InstallState(Map.of(), Set.of(), false));
        assertEquals(List.of(ModrinthConstants.FABRIC_API_PROJECT_ID, "ENTCULL1"), ids(plan));
    }

    @Test
    void incompatibleInstalledProjectRefusesTheRequestOnly() {
        fx.performancePack();
        fx.simpleMod("OPTIF001", "optifabric", "OptiFabric", "OPTV0001", "1.0", "optifabric-1.0.jar",
                ModrinthFixtures.dependency(null, "AANobbMI", "incompatible"));
        InstallPlan plan = planner.plan(List.of(InstallRequest.of("optifabric"), InstallRequest.of("lithium")),
                new InstallState(Map.of("AANobbMI", true), Set.of(), true));
        assertEquals(List.of("LITHIUM1"), ids(plan), "the other request still goes ahead");
        PlanNote note = plan.notes(PlanNote.Kind.INCOMPATIBLE).get(0);
        assertEquals("OptiFabric", note.subject());
        assertEquals("Sodium", note.detail());
        assertTrue(note.message().contains("OptiFabric") && note.message().contains("Sodium"));
    }

    @Test
    void incompatibleWithSomethingInTheSamePlanIsRefused() {
        fx.performancePack();
        fx.simpleMod("OPTIF001", "optifabric", "OptiFabric", "OPTV0001", "1.0", "optifabric-1.0.jar",
                ModrinthFixtures.dependency(null, "AANobbMI", "incompatible"));
        InstallPlan plan = planner.plan(List.of(InstallRequest.of("sodium"), InstallRequest.of("optifabric")),
                InstallState.none());
        assertEquals(List.of("AANobbMI"), ids(plan));
        assertEquals(1, plan.notes(PlanNote.Kind.INCOMPATIBLE).size());
    }

    @Test
    void missingDependencyRefusesTheRequest() {
        fx.performancePack();
        fx.simpleMod("ADDON001", "addon", "Some Addon", "ADDV0001", "2.0", "addon-2.0.jar",
                ModrinthFixtures.dependency(null, "FERRITE1", "required"));
        InstallPlan plan = planner.plan(List.of(InstallRequest.of("addon")), InstallState.none());
        assertTrue(plan.installs().isEmpty());
        assertEquals("FerriteCore", plan.notes(PlanNote.Kind.MISSING_DEPENDENCY).get(0).detail());
    }

    @Test
    void optionalAndEmbeddedDependenciesAreIgnored() {
        fx.performancePack();
        fx.simpleMod("ADDON002", "addon2", "Addon Two", "ADDV0002", "1.0", "addon2-1.0.jar",
                ModrinthFixtures.dependency(null, "LITHIUM1", "optional"),
                ModrinthFixtures.dependency(null, "IMMFAST1", "embedded"));
        assertEquals(List.of("ADDON002"), ids(planner.plan(List.of(InstallRequest.of("addon2")), InstallState.none())));
    }

    @Test
    void unknownProjectAndModpacksAreReported() {
        server.json("/v2/project/a-modpack", ModrinthFixtures.project("PACK0001", "a-modpack", "A Modpack", "modpack"));
        InstallPlan plan = planner.plan(List.of(InstallRequest.of("nope"), InstallRequest.of("a-modpack")),
                InstallState.none());
        assertTrue(plan.isEmpty());
        assertEquals("nope", plan.notes(PlanNote.Kind.NOT_FOUND).get(0).subject());
        assertEquals("modpack", plan.notes(PlanNote.Kind.UNSUPPORTED_TYPE).get(0).detail());
    }

    @Test
    void shaderPacksUseTheIrisLoader() {
        String project = ModrinthFixtures.project("HVnmMxH1", "complementary-reimagined",
                "Complementary Shaders - Reimagined", "shader");
        server.json("/v2/project/complementary-reimagined", project).json("/v2/project/HVnmMxH1", project);
        server.json("/v2/project/HVnmMxH1/version", ModrinthFixtures.array(fx.version("Bqen1mJX", "HVnmMxH1", "r5.9.3",
                "release", "2026-09-15T06:35:41Z", List.of("iris", "optifine"), "ComplementaryReimagined_r5.9.3.zip",
                "complementary")));
        InstallPlan plan = planner.plan(List.of(InstallRequest.of("complementary-reimagined")), InstallState.none());
        assertEquals("shaderpacks/ComplementaryReimagined_r5.9.3.zip", plan.installs().get(0).relativeFile());
        assertTrue(server.requests("/v2/project/HVnmMxH1/version").get(0).query().contains("iris"));
    }
}
