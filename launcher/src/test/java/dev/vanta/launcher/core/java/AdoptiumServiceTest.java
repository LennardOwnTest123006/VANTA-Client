package dev.vanta.launcher.core.java;

import dev.vanta.launcher.core.model.AdoptiumAsset;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.Checksums;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.net.HashAlgorithm;
import dev.vanta.launcher.core.net.IntegrityException;
import dev.vanta.launcher.core.net.JdkHttpTransport;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.core.util.Sleeper;
import dev.vanta.launcher.testutil.FakeHttpServer;
import dev.vanta.launcher.testutil.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdoptiumServiceTest {

    @TempDir
    Path tmp;
    private FakeHttpServer server;
    private Downloader downloader;

    @BeforeEach
    void start() throws IOException {
        server = new FakeHttpServer();
        downloader = new Downloader(new JdkHttpTransport("test"), Sleeper.NONE, 2, 2);
    }

    @AfterEach
    void stop() {
        downloader.close();
        server.close();
    }

    private static byte[] fakeJreZip() throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("jdk-21.0.4+7-jre/bin/java.exe"));
            zip.write("MZ fake".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("jdk-21.0.4+7-jre/release"));
            zip.write("JAVA_VERSION=\"21.0.4\"".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private String assetsJson(final byte[] archive, final String checksum) {
        // Point the windows/x64 fixture entry at the fake server and give it the right checksum.
        final com.google.gson.JsonArray arr = Fixtures.json("adoptium-latest.json").getAsJsonArray();
        for (com.google.gson.JsonElement el : arr) {
            final com.google.gson.JsonObject pkg = el.getAsJsonObject().getAsJsonObject("binary").getAsJsonObject("package");
            if (pkg.get("name").getAsString().endsWith(".zip")) {
                pkg.addProperty("link", server.url("dl/" + pkg.get("name").getAsString()).toString());
                pkg.addProperty("checksum", checksum);
                pkg.addProperty("size", archive.length);
                server.add("dl/" + pkg.get("name").getAsString(), archive);
            }
        }
        return arr.toString();
    }

    @Test
    void findsAssetForPlatform() throws Exception {
        server.addJson("v3/assets/latest/21/hotspot", Fixtures.read("adoptium-latest.json"));
        final AdoptiumService linux = new AdoptiumService(downloader, new LauncherPaths(tmp), new OsInfo("linux", "x64", ""), e -> Optional.empty(), server.url("v3/"));
        assertTrue(linux.latestAssetsUrl().getQuery().contains("os=linux"));
        assertTrue(linux.latestAssetsUrl().getQuery().contains("architecture=x64"));
        assertTrue(linux.latestAssetsUrl().getQuery().contains("image_type=jre"));
        final AdoptiumAsset asset = linux.findLatest();
        assertEquals("linux", asset.binary().os());
        assertEquals("jdk-21.0.4+7", asset.releaseName());
        assertTrue(asset.binary().pkg().name().endsWith(".tar.gz"));
        assertEquals(tmp.resolve("runtimes/temurin-21-jdk-21.0.4+7"), linux.runtimeDir(asset));

        final AdoptiumService mac = new AdoptiumService(downloader, new LauncherPaths(tmp), new OsInfo("osx", "arm64", ""), e -> Optional.empty(), server.url("v3/"));
        assertEquals("aarch64", mac.findLatest().binary().architecture());
        assertEquals("mac", mac.findLatest().binary().os());
    }

    @Test
    void installsVerifiesExtractsAndProbes() throws Exception {
        final byte[] archive = fakeJreZip();
        server.addJson("v3/assets/latest/21/hotspot", assetsJson(archive, Checksums.hex(archive, HashAlgorithm.SHA256)));
        final OsInfo windows = new OsInfo("windows", "x64", "10.0");
        final JavaProbe probe = exe -> Optional.of(new JavaInstall(JavaVersionParser.homeOf(exe), exe, "21.0.4", 21, "Eclipse Adoptium", "amd64", true));
        final LauncherPaths paths = new LauncherPaths(tmp);
        final AdoptiumService service = new AdoptiumService(downloader, paths, windows, probe, server.url("v3/"));
        final JavaInstall install = service.install(DownloadProgressListener.NONE, CancellationToken.NONE);
        assertEquals(21, install.major());
        assertEquals(paths.runtimesDir().resolve("temurin-21-jdk-21.0.4+7/jdk-21.0.4+7-jre/bin/java.exe"), install.executable());
        assertTrue(Files.isRegularFile(install.executable()));
        assertFalse(Files.exists(paths.cacheDir().resolve("OpenJDK21U-jre_x64_windows_hotspot_21.0.4_7.zip")), "archive removed after extraction");
        assertFalse(Files.exists(paths.runtimesDir().resolve("temurin-21-jdk-21.0.4+7.extracting")));

        // Second install reuses the extracted runtime without downloading again.
        final int hitsBefore = server.totalHits();
        service.install(DownloadProgressListener.NONE, CancellationToken.NONE);
        assertEquals(hitsBefore + 1, server.totalHits(), "only the API query, no download");
    }

    @Test
    void checksumMismatchRefusesInstall() throws Exception {
        final byte[] archive = fakeJreZip();
        server.addJson("v3/assets/latest/21/hotspot", assetsJson(archive, "0".repeat(64)));
        final AdoptiumService service = new AdoptiumService(downloader, new LauncherPaths(tmp), new OsInfo("windows", "x64", ""),
            exe -> Optional.empty(), server.url("v3/"));
        assertThrows(IntegrityException.class, () -> service.install(DownloadProgressListener.NONE, CancellationToken.NONE));
        assertFalse(Files.exists(tmp.resolve("runtimes/temurin-21-jdk-21.0.4+7")));
    }

    @Test
    void missingChecksumRefusesInstall() {
        final AdoptiumAsset asset = new AdoptiumAsset(new AdoptiumAsset.Binary("windows", "x64", "jre", "hotspot",
            new AdoptiumAsset.Package("x.zip", server.url("dl/x.zip").toString(), "", "", 1)), "jdk-21.0.4+7", "eclipse", null);
        final AdoptiumService service = new AdoptiumService(downloader, new LauncherPaths(tmp), new OsInfo("windows", "x64", ""),
            exe -> Optional.empty(), server.url("v3/"));
        assertThrows(IntegrityException.class, () -> service.install(asset, DownloadProgressListener.NONE, CancellationToken.NONE));
    }

    @Test
    void noMatchingAssetIsAnError() {
        server.addJson("v3/assets/latest/21/hotspot", "[]");
        final AdoptiumService service = new AdoptiumService(downloader, new LauncherPaths(tmp), new OsInfo("linux", "x64", ""),
            exe -> Optional.empty(), server.url("v3/"));
        final IOException e = assertThrows(IOException.class, service::findLatest);
        assertTrue(e.getMessage().contains("no Temurin 21 JRE"));
    }
}
