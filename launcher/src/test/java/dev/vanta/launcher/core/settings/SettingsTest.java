package dev.vanta.launcher.core.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    @TempDir
    Path tmp;

    @Test
    void defaultMemoryIsHalfOfRamClamped() {
        assertEquals(4096, LauncherSettings.defaultMemoryMb(0));
        assertEquals(4096, LauncherSettings.defaultMemoryMb(8192));
        assertEquals(8192, LauncherSettings.defaultMemoryMb(32768));
        assertEquals(8192, LauncherSettings.defaultMemoryMb(65536));
        assertEquals(2048, LauncherSettings.defaultMemoryMb(4096));
        assertEquals(6000, LauncherSettings.defaultMemoryMb(12000));
    }

    @Test
    void defaultMemoryNeverExceedsHalfOfRam() {
        assertEquals(1536, LauncherSettings.defaultMemoryMb(3072), "3 GiB PC: half, not the 2 GiB floor");
        assertEquals(1024, LauncherSettings.defaultMemoryMb(2048));
        assertEquals(LauncherSettings.MIN_MEMORY_MB, LauncherSettings.defaultMemoryMb(1024), "never below the UI minimum");
        for (long ram = 1024; ram <= 131072; ram += 512) {
            final int heap = LauncherSettings.defaultMemoryMb(ram);
            assertTrue(heap <= Math.max(LauncherSettings.MIN_MEMORY_MB, ram / 2), ram + " MiB RAM -> " + heap);
            assertTrue(heap <= LauncherSettings.MAX_DEFAULT_MEMORY_MB, ram + " MiB RAM -> " + heap);
            if (ram >= 4096) {
                assertTrue(heap >= LauncherSettings.MIN_DEFAULT_MEMORY_MB, ram + " MiB RAM -> " + heap);
            }
        }
    }

    @Test
    void missingOrTooSmallStoredMemoryFollowsThePcMemory() throws IOException {
        final SettingsStore store = new SettingsStore(tmp.resolve("ram-settings.json"), 6144);
        Files.writeString(store.file(), "{\"schemaVersion\":1,\"memoryMb\":12}");
        assertEquals(3072, store.load().memoryMb(), "half of 6 GiB, not the flat 4096");
        Files.writeString(store.file(), "{\"schemaVersion\":1}");
        assertEquals(3072, store.load().memoryMb(), "missing memory -> default for this PC");
        Files.writeString(store.file(), "{\"schemaVersion\":1,\"memoryMb\":5120}");
        assertEquals(5120, store.load().memoryMb(), "a value the player chose is kept, even above the default");
    }

    @Test
    void defaultsAreSane() {
        final LauncherSettings s = LauncherSettings.defaults(16384);
        assertEquals(8192, s.memoryMb());
        assertTrue(s.javaPathOverride().isEmpty());
        assertTrue(s.resolutionOverride().isEmpty());
        assertFalse(s.hasReleasesBaseUrl());
        assertTrue(s.autoUpdateCheck());
        assertFalse(s.developerMode());
        assertTrue(s.shareOfficialMinecraftFiles());
        assertEquals(LauncherSettings.DEFAULT_THEME, s.theme());
        assertEquals(1, s.schemaVersion());
        assertTrue(s.localAiAutoInstall(), "the Local AI is offered by default");
        assertFalse(s.localAiConsent(), "but never downloaded before the player agreed");
        assertFalse(s.localAiWithInstalls());
    }

    @Test
    void localAiFieldsAreNullTolerantAndRoundTrip() throws IOException {
        // A settings.json written before 1.4.0 has neither key: offered, not yet accepted.
        final LauncherSettings old = dev.vanta.launcher.core.util.Json.parse(
            "{\"schemaVersion\":1,\"memoryMb\":4096,\"installPerformancePack\":false}", LauncherSettings.class);
        assertTrue(old.localAiAutoInstall());
        assertFalse(old.localAiConsent());
        assertFalse(old.performancePack());
        // The 12- and 13-argument constructors keep working.
        final LauncherSettings twelve = new LauncherSettings(1, 4096, "", List.of(), null, false, "", "", true, false, true, "t");
        assertTrue(twelve.localAiAutoInstall());
        assertFalse(twelve.localAiConsent());
        final LauncherSettings thirteen = new LauncherSettings(1, 4096, "", List.of(), null, false, "", "", true, false, true, "t", Boolean.FALSE);
        assertFalse(thirteen.performancePack());
        assertTrue(thirteen.localAiAutoInstall());

        final LauncherSettings accepted = LauncherSettings.defaults(0).withLocalAiAccepted(true);
        assertTrue(accepted.localAiWithInstalls(), "on and accepted: installs include the Local AI step");
        assertFalse(accepted.withInstallLocalAi(false).localAiWithInstalls(), "switched off: never, even with consent");
        assertTrue(accepted.withInstallLocalAi(false).localAiConsent(), "the consent itself is kept");
        assertEquals(accepted.withMemoryMb(2048).localAiConsent(), true, "every wither keeps both fields");

        final SettingsStore store = new SettingsStore(tmp.resolve("local-ai-settings.json"), 16384);
        store.save(accepted.withInstallLocalAi(false));
        final LauncherSettings loaded = store.load();
        assertFalse(loaded.localAiAutoInstall());
        assertTrue(loaded.localAiConsent());
        final String json = Files.readString(store.file());
        assertTrue(json.contains("\"installLocalAi\": false"), json);
        assertTrue(json.contains("\"localAiAccepted\": true"), json);
    }

    @Test
    void withersProduceCopies() {
        final LauncherSettings s = LauncherSettings.defaults(0)
            .withMemoryMb(6144).withJavaPath("/opt/jdk").withJvmArgs(List.of("-XX:+UseZGC")).withResolution(new Resolution(1920, 1080))
            .withKeepLauncherOpen(true).withMsClientId("abc").withReleasesBaseUrl("https://r/").withAutoUpdateCheck(false)
            .withDeveloperMode(true).withShareOfficialMinecraftFiles(false).withTheme("x");
        assertEquals(6144, s.memoryMb());
        assertEquals("/opt/jdk", s.javaPath());
        assertEquals(List.of("-XX:+UseZGC"), s.jvmArgs());
        assertEquals("1920x1080", s.resolution().toString());
        assertTrue(s.keepLauncherOpen());
        assertEquals("abc", s.msClientId());
        assertEquals("https://r/", s.releasesBaseUrl());
        assertFalse(s.autoUpdateCheck());
        assertTrue(s.developerMode());
        assertFalse(s.shareOfficialMinecraftFiles());
        assertEquals("x", s.theme());
    }

    @Test
    void storeRoundTripAndTolerantLoad() throws IOException {
        final SettingsStore store = new SettingsStore(tmp.resolve("settings.json"), 16384);
        assertEquals(8192, store.load().memoryMb(), "missing file yields defaults");
        final LauncherSettings saved = store.defaults().withMemoryMb(3072).withMsClientId("client-id").withResolution(new Resolution(1280, 720));
        store.save(saved);
        assertEquals(saved, store.load());

        Files.writeString(store.file(), "{ this is not json");
        assertEquals(8192, store.load().memoryMb(), "corrupt file yields defaults");
        assertTrue(Files.exists(tmp.resolve("settings.json.corrupt")), "corrupt file is preserved");

        Files.writeString(store.file(), "{\"schemaVersion\":1,\"memoryMb\":12}");
        assertEquals(8192, store.load().memoryMb(), "too small memory is replaced by the default for this PC");
    }

    @Test
    void resolutionParsing() {
        assertEquals(new Resolution(1920, 1080), Resolution.parse("1920x1080"));
        assertEquals(new Resolution(1280, 720), Resolution.parse(" 1280 X 720 "));
        assertThrows(IllegalArgumentException.class, () -> Resolution.parse("1920"));
        assertThrows(IllegalArgumentException.class, () -> Resolution.parse("ax b"));
        assertThrows(IllegalArgumentException.class, () -> new Resolution(10, 10));
    }

    @Test
    void releasesBaseUrlPrecedence() {
        final java.util.Map<String, String> noEnv = java.util.Map.of();
        final java.util.Map<String, String> env = java.util.Map.of(LauncherSettings.RELEASES_BASE_URL_ENV, " https://mirror.example/vanta ");
        final LauncherSettings defaults = LauncherSettings.defaults(16384);

        assertEquals(new ReleasesBaseUrl(LauncherSettings.DEFAULT_RELEASES_BASE_URL, ReleasesBaseUrl.Source.DEFAULT),
            defaults.effectiveReleasesBaseUrl(noEnv), "an empty setting uses the built-in default");
        assertEquals("https://raw.githubusercontent.com/LennardOwnTest123006/VANTA-Client/HEAD/shared/releases/latest",
            LauncherSettings.DEFAULT_RELEASES_BASE_URL);
        assertTrue(defaults.effectiveReleasesBaseUrl(noEnv).isValid());
        assertTrue(defaults.effectiveReleasesBaseUrl(noEnv).isDefault());
        assertEquals(new ReleasesBaseUrl("https://mirror.example/vanta", ReleasesBaseUrl.Source.ENVIRONMENT), defaults.effectiveReleasesBaseUrl(env));
        assertEquals(new ReleasesBaseUrl("https://own.example/r", ReleasesBaseUrl.Source.SETTINGS),
            defaults.withReleasesBaseUrl(" https://own.example/r ").effectiveReleasesBaseUrl(env), "a non-empty setting wins");
        assertEquals(ReleasesBaseUrl.Source.DEFAULT, defaults.withReleasesBaseUrl("   ").effectiveReleasesBaseUrl(noEnv).source());
        assertEquals(ReleasesBaseUrl.Source.DEFAULT,
            ReleasesBaseUrl.resolve("", java.util.Map.of(LauncherSettings.RELEASES_BASE_URL_ENV, "  ")).source(), "a blank variable is ignored");
        assertEquals("https://fake/releases/", ReleasesBaseUrl.resolve(null, noEnv, "https://fake/releases/").url());

        final ReleasesBaseUrl invalid = defaults.withReleasesBaseUrl("ftp://releases.example").effectiveReleasesBaseUrl(noEnv);
        assertEquals(ReleasesBaseUrl.Source.SETTINGS, invalid.source(), "an explicitly configured invalid URL is not replaced silently");
        assertFalse(invalid.isValid());
        for (String bad : java.util.List.of("", "releases.example", "http://", "https:// spaced .example", "file:///tmp/x")) {
            assertFalse(ReleasesBaseUrl.isHttpUrl(bad), bad);
        }
        assertTrue(ReleasesBaseUrl.isHttpUrl("http://127.0.0.1:8080/releases/"));
    }

    @Test
    void existingSettingsFilesWithAnEmptyUrlUseTheDefault() throws IOException {
        final SettingsStore store = new SettingsStore(tmp.resolve("old-settings.json"), 16384);
        Files.writeString(store.file(), "{\"schemaVersion\":1,\"memoryMb\":4096,\"releasesBaseUrl\":\"\"}");
        final LauncherSettings loaded = store.load();
        assertFalse(loaded.hasReleasesBaseUrl());
        assertEquals(LauncherSettings.DEFAULT_RELEASES_BASE_URL, loaded.effectiveReleasesBaseUrl(java.util.Map.of()).url());
    }

    @Test
    void thePerformancePackIsOnUnlessSwitchedOff() throws IOException {
        assertTrue(LauncherSettings.defaults(0).performancePack());
        final SettingsStore store = new SettingsStore(tmp.resolve("pack-settings.json"), 16384);
        // A settings.json written by launcher 1.0.x has no installPerformancePack key.
        Files.writeString(store.file(), "{\"schemaVersion\":1,\"memoryMb\":4096,\"keepLauncherOpen\":true}");
        assertTrue(store.load().performancePack());
        store.save(store.load().withInstallPerformancePack(false));
        assertTrue(Files.readString(store.file()).contains("\"installPerformancePack\": false"), Files.readString(store.file()));
        assertFalse(store.load().performancePack());
        assertTrue(store.load().keepLauncherOpen(), "other values survive");
        assertFalse(store.load().withMemoryMb(2048).performancePack(), "withers keep it");
    }
}
