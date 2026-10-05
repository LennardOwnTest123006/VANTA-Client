package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.core.modrinth.ContentType;
import dev.vanta.launcher.core.modrinth.ModrinthService;
import dev.vanta.launcher.ui.testutil.FakeBackend;
import dev.vanta.launcher.ui.testutil.TestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Mods page state with a scripted backend.
 */
class ModsViewModelTest {

    @TempDir
    Path tmp;
    private TestContext ctx;
    private FakeBackend backend;
    private ModsViewModel vm;

    @BeforeEach
    void setUp() {
        ctx = new TestContext(tmp);
        backend = ctx.backend;
        backend.modrinthHits.put(ContentType.MOD, List.of(
            FakeBackend.hit("AANobbMI", "sodium", "Sodium", "jellysquid3", ContentType.MOD, 236_787_190L, "Rendering engine"),
            FakeBackend.hit("PtjYWJkn", "sodium-extra", "Sodium Extra", "FlashyReese", ContentType.MOD, 99_675_777L, "Sodium addon"),
            FakeBackend.hit("gvQqBUqZ", "lithium", "Lithium", "", ContentType.MOD, 132_517_592L, "Game logic")));
        backend.modrinthHits.put(ContentType.SHADER, List.of(
            FakeBackend.hit("HVnmMxH1", "complementary-reimagined", "Complementary Shaders - Reimagined", "EminGT", ContentType.SHADER, 68_409_474L, "")));
        vm = new ModsViewModel(backend, ctx.executors, ctx.messages, ctx.formats, ctx.toasts, ctx.launcherLog);
    }

    @Test
    void searchPagesAndSwitchesTabs() {
        vm.search();
        assertEquals(2, vm.results().size());
        assertEquals(3, vm.totalHitsProperty().get());
        assertTrue(vm.hasMore());
        vm.loadMore();
        assertEquals(3, vm.results().size());
        assertFalse(vm.hasMore());
        assertEquals("by jellysquid3 · 236.8M downloads", vm.byline(vm.results().get(0)));
        assertEquals("132.5M downloads", vm.byline(vm.results().get(2)));

        vm.tabProperty().set(ContentType.SHADER);
        assertEquals(List.of("Complementary Shaders - Reimagined"), vm.results().stream().map(h -> h.title()).toList());
        assertTrue(backend.calls.contains("searchModrinth:shader::0"));

        vm.queryProperty().set("nothing like this");
        vm.search();
        assertTrue(vm.results().isEmpty());
        assertTrue(vm.searchedProperty().get());
    }

    @Test
    void aFailedSearchIsShownInsteadOfAnEmptyList() {
        backend.searchFailure = new java.net.ConnectException("Connection refused");
        vm.search();
        assertTrue(vm.results().isEmpty());
        assertEquals(ctx.messages.get("error.network.offline"), vm.searchErrorProperty().get());
        backend.searchFailure = null;
        vm.search();
        assertEquals("", vm.searchErrorProperty().get());
    }

    @Test
    void installAddsTheProjectAndSaysWhenItLoads() {
        vm.search();
        vm.install(vm.results().get(0));
        assertTrue(backend.calls.contains("installFromModrinth:AANobbMI"));
        assertTrue(vm.isInstalled(vm.results().get(0)));
        assertFalse(vm.isInstalled(vm.results().get(1)));
        assertTrue(vm.installing().isEmpty());
        assertTrue(vm.changedSinceStartProperty().get());
        assertEquals("Sodium installed", ctx.toasts.toasts().get(0).title());
        assertEquals(ctx.messages.get("mods.toast.installed.message"), ctx.toasts.toasts().get(0).message());
        assertEquals(1, vm.installedOf(ContentType.MOD).size());
        assertTrue(vm.installedOf(ContentType.SHADER).isEmpty());

        backend.modrinthInstallFailure = new IOException("sodium-extra has no version for Minecraft 1.21.11");
        vm.install(vm.results().get(1));
        assertEquals(ToastModel.Kind.ERROR, ctx.toasts.toasts().get(1).kind());
        assertTrue(ctx.toasts.toasts().get(1).message().contains("has no version for Minecraft 1.21.11"));
    }

    @Test
    void enableDisableAndRemoveExplainRefusals() {
        final Path mods = backend.paths().modsDir();
        final ModrinthService.InstalledContent sodium = new ModrinthService.InstalledContent(ContentType.MOD, "Sodium", "mc1.21.11-0.8.14-fabric",
            mods.resolve("sodium-fabric-0.8.14+mc1.21.11.jar"), true, true, false, "AANobbMI", List.of("Iris Shaders"), false);
        final ModrinthService.InstalledContent api = new ModrinthService.InstalledContent(ContentType.MOD, "fabric-api-0.141.6+1.21.11.jar", "",
            mods.resolve("fabric-api-0.141.6+1.21.11.jar"), true, false, true, "", List.of(), false);
        final ModrinthService.InstalledContent shader = new ModrinthService.InstalledContent(ContentType.SHADER, "MyShader.zip", "",
            backend.paths().shaderpacksDir().resolve("MyShader.zip"), true, false, false, "", List.of(), false);
        backend.content.addAll(List.of(sodium, api, shader));
        vm.refreshInstalled();
        assertEquals(3, vm.installed().size());

        vm.remove(sodium);
        assertEquals("Sodium is needed by Iris Shaders. Disable or remove that first.", ctx.toasts.toasts().get(0).message());
        vm.setEnabled(api, false);
        assertEquals(ctx.messages.format("mods.refused.managed", "fabric-api-0.141.6+1.21.11.jar"), ctx.toasts.toasts().get(1).message());

        vm.setEnabled(shader, false);
        assertFalse(vm.installedOf(ContentType.SHADER).get(0).enabled());
        assertTrue(vm.installedOf(ContentType.SHADER).get(0).path().toString().endsWith("MyShader.zip.disabled"));
        vm.remove(vm.installedOf(ContentType.SHADER).get(0));
        assertTrue(vm.installedOf(ContentType.SHADER).isEmpty());
        assertTrue(vm.changedSinceStartProperty().get());
    }

    @Test
    void updateAllReportsWhatChanged() {
        vm.updateAll();
        assertTrue(backend.calls.contains("updateAllContent"));
        assertFalse(vm.updatingProperty().get());
        assertEquals(ctx.messages.get("mods.toast.updated.none"), ctx.toasts.toasts().get(0).message());
        assertEquals("", vm.statusTextProperty().get());
    }

    @Test
    void fabricApiIsShownAsIncluded() {
        assertTrue(vm.isIncluded(FakeBackend.hit("P7dR8mSH", "fabric-api", "Fabric API", "modmuss50", ContentType.MOD, 268_199_555L, "")));
        assertFalse(vm.isIncluded(FakeBackend.hit("AANobbMI", "sodium", "Sodium", "jellysquid3", ContentType.MOD, 1L, "")));
    }

    @Test
    void compactNumbers() {
        assertEquals("950", ModsViewModel.compact(950));
        assertEquals("12.4k", ModsViewModel.compact(12_400));
        assertEquals("1.0M", ModsViewModel.compact(1_000_000));
    }
}
