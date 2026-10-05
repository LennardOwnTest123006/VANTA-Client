package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.core.settings.Resolution;
import dev.vanta.launcher.ui.prefs.UiPreferences;
import dev.vanta.launcher.ui.testutil.FakeBackend;
import dev.vanta.launcher.ui.testutil.TestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsViewModelTest {

    @TempDir
    Path tmp;
    private TestContext ctx;
    private SettingsViewModel vm;

    @BeforeEach
    void setUp() {
        ctx = new TestContext(tmp);
        ctx.backend.settings = new LauncherSettings(1, 6144, "", List.of("-XX:+UseZGC"), new Resolution(1920, 1080), true, "client-id",
            "https://releases.example/vanta", false, true, false, LauncherSettings.DEFAULT_THEME);
        vm = new SettingsViewModel(ctx.backend, ctx.executors, ctx.messages, ctx.formats);
    }

    @Test
    void loadsEveryField() {
        assertEquals(6144, vm.memoryMbProperty().get());
        assertTrue(vm.javaAutoProperty().get());
        assertEquals("-XX:+UseZGC", vm.jvmArgsProperty().get());
        assertTrue(vm.resolutionEnabledProperty().get());
        assertEquals("1920", vm.resolutionWidthProperty().get());
        assertEquals("1080", vm.resolutionHeightProperty().get());
        assertTrue(vm.keepLauncherOpenProperty().get());
        assertEquals("client-id", vm.msClientIdProperty().get());
        assertEquals("https://releases.example/vanta", vm.releasesBaseUrlProperty().get());
        assertFalse(vm.autoUpdateCheckProperty().get());
        assertTrue(vm.developerModeProperty().get());
        assertFalse(vm.shareOfficialFilesProperty().get());
        assertFalse(vm.highContrastProperty().get());
        assertFalse(vm.dirtyProperty().get());
        assertTrue(vm.validProperty().get());
        assertEquals(16384, vm.maxMemoryMb());
        assertTrue(vm.memoryHelp().contains("16 GB"));
    }

    @Test
    void editingMarksDirtyAndSaveMapsBack() {
        vm.memoryMbProperty().set(8192);
        vm.highContrastProperty().set(true);
        vm.reducedMotionProperty().set(true);
        vm.jvmArgsProperty().set("-Dfoo=\"a b\" -Xss2m");
        vm.resolutionEnabledProperty().set(false);
        vm.javaAutoProperty().set(false);
        vm.javaPathProperty().set("/opt/java21");
        assertTrue(vm.dirtyProperty().get());
        final LauncherSettings mapped = vm.toSettings();
        assertEquals(8192, mapped.memoryMb());
        assertEquals(SettingsViewModel.THEME_HIGH_CONTRAST, mapped.theme());
        assertEquals(List.of("-Dfoo=a b", "-Xss2m"), mapped.jvmArgs());
        assertNull(mapped.resolution());
        assertEquals("/opt/java21", mapped.javaPath());
        assertTrue(vm.toPreferences().reducedMotion());

        final AtomicReference<LauncherSettings> saved = new AtomicReference<>();
        vm.save((s, p) -> saved.set(s), error -> { });
        assertEquals(mapped, saved.get());
        assertEquals(mapped, ctx.backend.settings);
        assertFalse(vm.dirtyProperty().get());
    }

    @Test
    void validationBlocksBadResolutionAndUrl() {
        vm.resolutionWidthProperty().set("abc");
        assertFalse(vm.validProperty().get());
        assertEquals(ctx.messages.get("settings.error.resolution"), vm.validationErrors().get(0));
        vm.resolutionWidthProperty().set("1280");
        vm.resolutionHeightProperty().set("100");
        assertFalse(vm.validProperty().get(), "below the minimum height");
        vm.resolutionHeightProperty().set("720");
        assertTrue(vm.validProperty().get());
        vm.releasesBaseUrlProperty().set("ftp://nope");
        assertFalse(vm.validProperty().get());
        assertEquals(ctx.messages.get("settings.releasesUrl.invalid"), vm.validationErrors().get(0));
        vm.releasesBaseUrlProperty().set("");
        assertTrue(vm.validProperty().get());
        final AtomicReference<Boolean> savedFlag = new AtomicReference<>(false);
        vm.releasesBaseUrlProperty().set("not a url");
        vm.save((s, p) -> savedFlag.set(true), error -> { });
        assertFalse(savedFlag.get(), "invalid settings are never saved");
        assertFalse(vm.errorTextProperty().get().isEmpty());
    }

    @Test
    void resetRestoresDefaultsAndDiscardReloads() {
        vm.resetToDefaults();
        assertEquals(LauncherSettings.defaultMemoryMb(16384), vm.memoryMbProperty().get());
        assertEquals("", vm.msClientIdProperty().get());
        assertTrue(vm.autoUpdateCheckProperty().get());
        assertTrue(vm.dirtyProperty().get(), "defaults differ from the stored settings");
        vm.discard();
        assertEquals(6144, vm.memoryMbProperty().get());
        assertFalse(vm.dirtyProperty().get());
    }

    @Test
    void javaPathValidationUsesTheBackendProbe() {
        vm.javaAutoProperty().set(false);
        vm.javaPathProperty().set("/nowhere");
        vm.validateJavaPath();
        assertEquals(SettingsViewModel.JavaCheck.INVALID, vm.javaCheckProperty().get());
        ctx.backend.probes.put(Path.of("/old"), ctx.backend.system17());
        vm.javaPathProperty().set("/old");
        vm.validateJavaPath();
        assertEquals(SettingsViewModel.JavaCheck.TOO_OLD, vm.javaCheckProperty().get());
        assertTrue(vm.javaCheckTextProperty().get().contains("Java 17"));
        ctx.backend.probes.put(Path.of("/new"), ctx.backend.temurin21());
        vm.javaPathProperty().set("/new");
        vm.validateJavaPath();
        assertEquals(SettingsViewModel.JavaCheck.VALID, vm.javaCheckProperty().get());
        vm.javaAutoProperty().set(true);
        assertEquals(SettingsViewModel.JavaCheck.NONE, vm.javaCheckProperty().get());
    }

    @Test
    void clientIdFromEnvironmentIsDetected() {
        assertFalse(vm.clientIdFromEnvironment());
        ctx.backend.putEnv(LauncherSettings.MS_CLIENT_ID_ENV, "env-id");
        vm.msClientIdProperty().set("");
        assertTrue(vm.clientIdFromEnvironment());
    }

    @Test
    void unknownMemoryFallsBackToSixteenGigabytes() {
        ctx.backend.totalMemory = OptionalLong.empty();
        assertEquals(SettingsViewModel.FALLBACK_MAX_MEMORY_MB, vm.maxMemoryMb());
        assertEquals(ctx.messages.get("settings.memory.helpUnknown"), vm.memoryHelp());
    }

    @Test
    void jvmArgsParsing() {
        assertEquals(List.of(), SettingsViewModel.parseJvmArgs(""));
        assertEquals(List.of(), SettingsViewModel.parseJvmArgs(null));
        assertEquals(List.of("-Xss2m", "-Dname=John Doe", "-ea"), SettingsViewModel.parseJvmArgs("  -Xss2m   -Dname=\"John Doe\" -ea "));
    }

    @Test
    void loadWithPrefs() {
        vm.load(LauncherSettings.defaults(8192).withTheme(SettingsViewModel.THEME_HIGH_CONTRAST), UiPreferences.defaults().withReducedMotion(true));
        assertTrue(vm.highContrastProperty().get());
        assertTrue(vm.reducedMotionProperty().get());
        assertFalse(vm.dirtyProperty().get());
        assertEquals(FakeBackend.CLOCK, ctx.backend.clock());
    }

    @Test
    void effectiveReleasesUrlFollowsTheEditorAndResets() {
        assertEquals("https://releases.example/vanta", vm.effectiveReleasesUrlProperty().get());
        assertEquals(ctx.messages.get("settings.releasesUrl.source.settings"), vm.effectiveReleasesSourceProperty().get());
        vm.resetReleasesBaseUrl();
        assertEquals("", vm.releasesBaseUrlProperty().get());
        assertEquals(LauncherSettings.DEFAULT_RELEASES_BASE_URL, vm.effectiveReleasesUrlProperty().get());
        assertEquals(ctx.messages.get("settings.releasesUrl.source.default"), vm.effectiveReleasesSourceProperty().get());
        assertEquals(LauncherSettings.DEFAULT_RELEASES_BASE_URL, vm.defaultReleasesBaseUrl());
        assertTrue(vm.dirtyProperty().get());
        assertTrue(vm.validProperty().get(), "an empty field is valid: the default applies");
        assertEquals("", vm.toSettings().releasesBaseUrl(), "the default is not copied into settings.json");

        ctx.backend.putEnv(LauncherSettings.RELEASES_BASE_URL_ENV, "https://mirror.example/vanta");
        vm.releasesBaseUrlProperty().set(" ");
        vm.releasesBaseUrlProperty().set("");
        assertEquals("https://mirror.example/vanta", vm.effectiveReleasesUrlProperty().get());
        assertEquals(ctx.messages.format("settings.releasesUrl.source.environment", LauncherSettings.RELEASES_BASE_URL_ENV),
            vm.effectiveReleasesSourceProperty().get());
    }
}
