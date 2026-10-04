package dev.vanta.launcher.core.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveExtractorTest {

    @TempDir
    Path tmp;

    // ------------------------------------------------------------------ zip

    private Path zip(final String name, final String... entries) throws IOException {
        final Path zip = tmp.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String e : entries) {
                out.putNextEntry(new ZipEntry(e));
                if (!e.endsWith("/")) {
                    out.write(("content of " + e).getBytes(StandardCharsets.UTF_8));
                }
                out.closeEntry();
            }
        }
        return zip;
    }

    @Test
    void extractsZip() throws IOException {
        final Path zip = zip("jre.zip", "jdk-21.0.4+7-jre/", "jdk-21.0.4+7-jre/bin/", "jdk-21.0.4+7-jre/bin/java.exe", "jdk-21.0.4+7-jre/lib/modules");
        final Path dest = tmp.resolve("out");
        ArchiveExtractor.extract(zip, dest);
        assertTrue(Files.isRegularFile(dest.resolve("jdk-21.0.4+7-jre/bin/java.exe")));
        assertEquals("content of jdk-21.0.4+7-jre/lib/modules", Files.readString(dest.resolve("jdk-21.0.4+7-jre/lib/modules")));
    }

    @Test
    void zipTraversalIsRejected() throws IOException {
        final Path zip = zip("evil.zip", "ok.txt", "../../evil.txt");
        final Path dest = tmp.resolve("out2");
        assertThrows(UnsafeArchiveException.class, () -> ArchiveExtractor.extractZip(zip, dest));
        assertFalse(Files.exists(tmp.resolve("evil.txt")));
        assertFalse(Files.exists(tmp.getParent().resolve("evil.txt")));
    }

    @Test
    void zipAbsolutePathIsRejected() throws IOException {
        final Path zip = zip("abs.zip", "/etc/passwd");
        assertThrows(UnsafeArchiveException.class, () -> ArchiveExtractor.extractZip(zip, tmp.resolve("out3")));
        final Path win = zip("win.zip", "C:\\Windows\\evil.exe");
        assertThrows(UnsafeArchiveException.class, () -> ArchiveExtractor.extractZip(win, tmp.resolve("out4")));
    }

    // ------------------------------------------------------------------ tar

    private static byte[] tarHeader(final String name, final long size, final char type, final int mode, final String link) {
        final byte[] h = new byte[512];
        final byte[] n = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(n, 0, h, 0, Math.min(100, n.length));
        putOctal(h, 100, 8, mode);
        putOctal(h, 108, 8, 0);
        putOctal(h, 116, 8, 0);
        putOctal(h, 124, 12, size);
        putOctal(h, 136, 12, 0);
        h[156] = (byte) type;
        if (link != null) {
            final byte[] l = link.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(l, 0, h, 157, Math.min(100, l.length));
        }
        System.arraycopy("ustar\0".getBytes(StandardCharsets.US_ASCII), 0, h, 257, 6);
        System.arraycopy("00".getBytes(StandardCharsets.US_ASCII), 0, h, 263, 2);
        Arrays.fill(h, 148, 156, (byte) ' ');
        int sum = 0;
        for (byte b : h) {
            sum += b & 0xFF;
        }
        putOctal(h, 148, 7, sum);
        h[155] = ' ';
        return h;
    }

    private static void putOctal(final byte[] h, final int off, final int len, final long value) {
        final String s = Long.toOctalString(value);
        final String padded = "0".repeat(Math.max(0, len - 1 - s.length())) + s;
        final byte[] b = padded.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(b, 0, h, off, Math.min(len - 1, b.length));
    }

    private static void tarFile(final OutputStream out, final String name, final byte[] content, final int mode) throws IOException {
        out.write(tarHeader(name, content.length, '0', mode, null));
        out.write(content);
        final int pad = (512 - content.length % 512) % 512;
        out.write(new byte[pad]);
    }

    private static void tarDir(final OutputStream out, final String name) throws IOException {
        out.write(tarHeader(name, 0, '5', 0755, null));
    }

    private static void tarSymlink(final OutputStream out, final String name, final String target) throws IOException {
        out.write(tarHeader(name, 0, '2', 0777, target));
    }

    private static void tarLongName(final OutputStream out, final String longName, final byte[] content) throws IOException {
        final byte[] nameBytes = (longName + "\0").getBytes(StandardCharsets.UTF_8);
        out.write(tarHeader("././@LongLink", nameBytes.length, 'L', 0644, null));
        out.write(nameBytes);
        out.write(new byte[(512 - nameBytes.length % 512) % 512]);
        tarFile(out, "truncated-name", content, 0644);
    }

    private Path tarGz(final String name, final TarWriter writer) throws IOException {
        final ByteArrayOutputStream raw = new ByteArrayOutputStream();
        writer.write(raw);
        raw.write(new byte[1024]);
        final Path file = tmp.resolve(name);
        try (GZIPOutputStream gz = new GZIPOutputStream(Files.newOutputStream(file))) {
            gz.write(raw.toByteArray());
        }
        return file;
    }

    @FunctionalInterface
    interface TarWriter {
        void write(OutputStream out) throws IOException;
    }

    @Test
    void extractsTarGzWithModesSymlinksAndLongNames() throws IOException {
        final String longName = "jdk-21.0.4+7-jre/lib/" + "very-long-directory-name-".repeat(5) + "/libawt_headless.so";
        final Path tgz = tarGz("jre.tar.gz", out -> {
            tarDir(out, "jdk-21.0.4+7-jre/");
            tarDir(out, "jdk-21.0.4+7-jre/bin/");
            tarFile(out, "jdk-21.0.4+7-jre/bin/java", "#!/bin/sh\necho java\n".getBytes(StandardCharsets.UTF_8), 0755);
            tarFile(out, "jdk-21.0.4+7-jre/release", "JAVA_VERSION=\"21.0.4\"\n".getBytes(StandardCharsets.UTF_8), 0644);
            tarSymlink(out, "jdk-21.0.4+7-jre/bin/java-link", "java");
            tarLongName(out, longName, "elf".getBytes(StandardCharsets.UTF_8));
        });
        final Path dest = tmp.resolve("tar-out");
        ArchiveExtractor.extract(tgz, dest);
        final Path java = dest.resolve("jdk-21.0.4+7-jre/bin/java");
        assertTrue(Files.isRegularFile(java));
        assertEquals("JAVA_VERSION=\"21.0.4\"\n", Files.readString(dest.resolve("jdk-21.0.4+7-jre/release")));
        assertEquals("elf", Files.readString(dest.resolve(longName)));
        if (Files.getFileStore(dest).supportsFileAttributeView("posix")) {
            assertTrue(Files.isExecutable(java));
            assertFalse(Files.isExecutable(dest.resolve("jdk-21.0.4+7-jre/release")));
            assertTrue(Files.isSymbolicLink(dest.resolve("jdk-21.0.4+7-jre/bin/java-link")));
        }
    }

    @Test
    void tarTraversalIsRejected() throws IOException {
        final Path tgz = tarGz("evil.tar.gz", out -> {
            tarFile(out, "ok/file", "x".getBytes(StandardCharsets.UTF_8), 0644);
            tarFile(out, "../../../evil", "x".getBytes(StandardCharsets.UTF_8), 0644);
        });
        assertThrows(UnsafeArchiveException.class, () -> ArchiveExtractor.extractTarGz(tgz, tmp.resolve("tar-out2")));
        assertFalse(Files.exists(tmp.resolve("evil")));
    }

    @Test
    void tarSymlinkEscapeIsRejected() throws IOException {
        final Path tgz = tarGz("evil-link.tar.gz", out -> {
            tarDir(out, "jre/");
            tarSymlink(out, "jre/escape", "../../../../etc");
        });
        assertThrows(UnsafeArchiveException.class, () -> ArchiveExtractor.extractTarGz(tgz, tmp.resolve("tar-out3")));
    }

    @Test
    void unsupportedExtension() {
        assertThrows(IOException.class, () -> ArchiveExtractor.extract(tmp.resolve("x.rar"), tmp.resolve("y")));
    }

    @Test
    void safeResolveNormalisesSeparators() throws IOException {
        final Path dest = tmp.toAbsolutePath().normalize();
        assertEquals(dest.resolve("a/b"), ArchiveExtractor.safeResolve(dest, "a\\b"));
        assertThrows(UnsafeArchiveException.class, () -> ArchiveExtractor.safeResolve(dest, ""));
        assertThrows(UnsafeArchiveException.class, () -> ArchiveExtractor.safeResolve(dest, "a/../../b"));
    }
}
