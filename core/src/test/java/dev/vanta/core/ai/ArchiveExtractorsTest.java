package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveExtractorsTest {
    @TempDir
    Path dir;

    @Test
    void extractsAZipWithDirectoriesAndFiles() throws IOException {
        byte[] zip = FakeArchives.zip(List.of("bin/", "bin/llama-server.exe", "README.md"),
                List.of(new byte[0], "exe".getBytes(StandardCharsets.UTF_8), "readme".getBytes(StandardCharsets.UTF_8)));
        Path target = dir.resolve("out");
        List<Path> files = ZipExtractor.extract(new ByteArrayInputStream(zip), target);
        assertEquals(2, files.size());
        assertEquals("exe", Files.readString(target.resolve("bin").resolve("llama-server.exe")));
        assertEquals("readme", Files.readString(target.resolve("README.md")));
    }

    @Test
    void zipSlipIsRefused() {
        byte[] zip = FakeArchives.zip(List.of("../escape.txt"), List.of("x".getBytes(StandardCharsets.UTF_8)));
        LocalAiException e = assertThrows(LocalAiException.class,
                () -> ZipExtractor.extract(new ByteArrayInputStream(zip), dir.resolve("out")));
        assertEquals(LocalAiException.Kind.INVALID_ARCHIVE, e.kind());
        assertFalse(Files.exists(dir.resolve("escape.txt")));
        byte[] absolute = FakeArchives.zip(List.of("/tmp/abs.txt"), List.of("x".getBytes(StandardCharsets.UTF_8)));
        assertThrows(LocalAiException.class,
                () -> ZipExtractor.extract(new ByteArrayInputStream(absolute), dir.resolve("out2")));
    }

    @Test
    void garbageIsNotAZip() throws IOException {
        Path junk = dir.resolve("junk.zip");
        Files.write(junk, FakeArchives.randomBytes(300, 3));
        // A ZipInputStream over garbage yields no entries; the installer then misses the server executable.
        assertTrue(ZipExtractor.extract(junk, dir.resolve("out")).isEmpty());
    }

    @Test
    void extractsATarGzWithModesLongNamesAndSymlinks() throws IOException {
        byte[] data = FakeArchives.randomBytes(1500, 5);
        String longName = "build/" + "very-long-directory-name-".repeat(5) + "/llama-server";
        byte[] tgz = FakeArchives.tarGz(FakeArchives.list(
                FakeArchives.TarEntry.dir("build"),
                FakeArchives.TarEntry.dir("build/bin"),
                FakeArchives.TarEntry.file("build/bin/llama-server", data, 0755),
                FakeArchives.TarEntry.file("build/bin/libggml.so", "lib", 0644),
                FakeArchives.TarEntry.file(longName, "long", 0755),
                FakeArchives.TarEntry.symlink("build/bin/llama", "llama-server")));
        Path target = dir.resolve("out");
        List<Path> files = TarGzExtractor.extract(new ByteArrayInputStream(tgz), target);
        assertEquals(3, files.size());
        Path server = target.resolve("build").resolve("bin").resolve("llama-server");
        assertArrayEquals(data, Files.readAllBytes(server));
        assertEquals("lib", Files.readString(target.resolve("build").resolve("bin").resolve("libggml.so")));
        assertEquals("long", Files.readString(target.resolve(longName)));
        if (Files.getFileStore(target).supportsFileAttributeView("posix")) {
            assertTrue(Files.isExecutable(server), "mode 0755 is preserved");
            assertFalse(Files.isExecutable(target.resolve("build").resolve("bin").resolve("libggml.so")));
            Path link = target.resolve("build").resolve("bin").resolve("llama");
            assertTrue(Files.isSymbolicLink(link));
            assertEquals(Path.of("llama-server"), Files.readSymbolicLink(link));
        }
    }

    @Test
    void paxHeadersGiveTheRealPath() throws IOException {
        byte[] tar = FakeArchives.tarWithPaxPath("short", "deep/nested/real-name.txt", "pax".getBytes(
                StandardCharsets.UTF_8));
        Path target = dir.resolve("out");
        List<Path> files = TarGzExtractor.extractTar(new ByteArrayInputStream(tar), target);
        assertEquals(1, files.size());
        assertEquals("pax", Files.readString(target.resolve("deep").resolve("nested").resolve("real-name.txt")));
        assertFalse(Files.exists(target.resolve("short")));
    }

    @Test
    void tarSlipAndAbsoluteSymlinksAreRefusedOrSkipped() throws IOException {
        byte[] escaping = FakeArchives.tarGz(FakeArchives.list(
                FakeArchives.TarEntry.file("../escape", "x", 0644)));
        LocalAiException e = assertThrows(LocalAiException.class,
                () -> TarGzExtractor.extract(new ByteArrayInputStream(escaping), dir.resolve("out")));
        assertEquals(LocalAiException.Kind.INVALID_ARCHIVE, e.kind());
        assertFalse(Files.exists(dir.resolve("escape")));

        byte[] badLink = FakeArchives.tarGz(FakeArchives.list(
                FakeArchives.TarEntry.symlink("bin/evil", "../../../../etc/passwd"),
                FakeArchives.TarEntry.symlink("bin/abs", "/etc/passwd"),
                FakeArchives.TarEntry.file("bin/ok", "ok", 0644)));
        Path target = dir.resolve("links");
        TarGzExtractor.extract(new ByteArrayInputStream(badLink), target);
        assertFalse(Files.exists(target.resolve("bin").resolve("evil"), java.nio.file.LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(target.resolve("bin").resolve("abs"), java.nio.file.LinkOption.NOFOLLOW_LINKS));
        assertEquals("ok", Files.readString(target.resolve("bin").resolve("ok")));
    }

    @Test
    void notGzipIsAnInvalidArchive() throws IOException {
        Path junk = dir.resolve("junk.tar.gz");
        Files.write(junk, "definitely not gzip".getBytes(StandardCharsets.UTF_8));
        LocalAiException e = assertThrows(LocalAiException.class,
                () -> TarGzExtractor.extract(junk, dir.resolve("out")));
        assertEquals(LocalAiException.Kind.INVALID_ARCHIVE, e.kind());
    }

    @Test
    void truncatedTarIsAnInvalidArchive() {
        byte[] tar = FakeArchives.tar(FakeArchives.list(FakeArchives.TarEntry.file("a.txt",
                FakeArchives.randomBytes(2000, 9), 0644)));
        byte[] cut = java.util.Arrays.copyOf(tar, 900);
        LocalAiException e = assertThrows(LocalAiException.class,
                () -> TarGzExtractor.extractTar(new ByteArrayInputStream(cut), dir.resolve("out")));
        assertEquals(LocalAiException.Kind.INVALID_ARCHIVE, e.kind());
    }
}
