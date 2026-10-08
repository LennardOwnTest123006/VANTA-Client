package dev.vanta.core.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts a zip archive (the Windows llama.cpp builds) into a directory with the usual safety checks: an entry may
 * not escape the target directory and the archive may not contain more than {@value #MAX_ENTRIES} entries.
 */
public final class ZipExtractor {
    /** Upper bound on entries, far above what a llama.cpp archive holds. */
    public static final int MAX_ENTRIES = 10_000;

    private ZipExtractor() {
    }

    /**
     * Extracts {@code zip} into {@code targetDir} (created when missing).
     *
     * @return the extracted regular files in archive order
     * @throws LocalAiException {@code INVALID_ARCHIVE} for an unsafe or unreadable archive
     */
    public static List<Path> extract(Path zip, Path targetDir) throws IOException {
        try (InputStream in = Files.newInputStream(zip)) {
            return extract(in, targetDir);
        }
    }

    /** Extracts a zip stream into {@code targetDir}. */
    public static List<Path> extract(InputStream stream, Path targetDir) throws IOException {
        Objects.requireNonNull(stream, "stream");
        Path root = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(root);
        List<Path> files = new ArrayList<>();
        byte[] buffer = new byte[64 * 1024];
        int count = 0;
        try (ZipInputStream zin = new ZipInputStream(stream)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) {
                    throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, "zip has too many entries");
                }
                Path target = ArchiveSafety.resolve(root, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (OutputStream out = Files.newOutputStream(target)) {
                    int read;
                    while ((read = zin.read(buffer)) >= 0) {
                        out.write(buffer, 0, read);
                    }
                }
                files.add(target);
                zin.closeEntry();
            }
        } catch (IllegalArgumentException | java.util.zip.ZipException e) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, "zip is not readable: " + e.getMessage(),
                    e);
        }
        return files;
    }
}
