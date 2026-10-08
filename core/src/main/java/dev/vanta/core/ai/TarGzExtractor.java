package dev.vanta.core.ai;

import dev.vanta.core.config.CoreLog;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * A small built-in reader for gzip-compressed ustar / GNU / pax tar archives (the Linux and macOS llama.cpp builds).
 * Supports regular files, directories, symbolic links (best effort), GNU long names ({@code L}) and pax extended
 * headers ({@code x}, keys {@code path} and {@code size}); other entry types are skipped. File modes with an
 * executable bit are preserved where the file system supports POSIX permissions. No external tools involved.
 */
public final class TarGzExtractor {
    private static final int BLOCK = 512;
    /** Upper bound on entries, far above what a llama.cpp archive holds. */
    public static final int MAX_ENTRIES = 10_000;

    private TarGzExtractor() {
    }

    /**
     * Extracts {@code tarGz} into {@code targetDir} (created when missing).
     *
     * @return the extracted regular files in archive order
     * @throws LocalAiException {@code INVALID_ARCHIVE} for an unsafe or unreadable archive
     */
    public static List<Path> extract(Path tarGz, Path targetDir) throws IOException {
        try (InputStream in = Files.newInputStream(tarGz)) {
            return extract(in, targetDir);
        }
    }

    /** Extracts a gzip-compressed tar stream into {@code targetDir}. */
    public static List<Path> extract(InputStream gzipped, Path targetDir) throws IOException {
        Objects.requireNonNull(gzipped, "gzipped");
        try {
            return extractTar(new GZIPInputStream(gzipped), targetDir);
        } catch (java.util.zip.ZipException e) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, "not a gzip stream: " + e.getMessage(),
                    e);
        }
    }

    /** Extracts an uncompressed tar stream into {@code targetDir}. */
    public static List<Path> extractTar(InputStream tar, Path targetDir) throws IOException {
        Path root = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(root);
        List<Path> files = new ArrayList<>();
        byte[] header = new byte[BLOCK];
        String pendingName = null;
        String pendingLink = null;
        long pendingSize = -1;
        int count = 0;
        while (true) {
            if (!readFully(tar, header)) {
                break; // EOF without the end marker: tolerated
            }
            if (isZero(header)) {
                break; // end of archive
            }
            if (++count > MAX_ENTRIES) {
                throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, "tar has too many entries");
            }
            String name = string(header, 0, 100);
            long mode = octal(header, 100, 8);
            long size = number(header, 124, 12);
            byte type = header[156];
            String linkName = string(header, 157, 100);
            String magic = string(header, 257, 6);
            if (magic.startsWith("ustar")) {
                String prefix = string(header, 345, 155);
                if (!prefix.isEmpty()) {
                    name = prefix + "/" + name;
                }
            }
            if (pendingName != null) {
                name = pendingName;
                pendingName = null;
            }
            if (pendingLink != null) {
                linkName = pendingLink;
                pendingLink = null;
            }
            if (pendingSize >= 0) {
                size = pendingSize;
                pendingSize = -1;
            }
            switch (type) {
                case 'L' -> pendingName = trimNul(new String(readData(tar, size), StandardCharsets.UTF_8));
                case 'K' -> pendingLink = trimNul(new String(readData(tar, size), StandardCharsets.UTF_8));
                case 'x' -> {
                    PaxHeader pax = PaxHeader.parse(readData(tar, size));
                    if (pax.path != null) {
                        pendingName = pax.path;
                    }
                    if (pax.linkPath != null) {
                        pendingLink = pax.linkPath;
                    }
                    if (pax.size >= 0) {
                        pendingSize = pax.size;
                    }
                }
                case 'g' -> skip(tar, size);
                case '5' -> {
                    Files.createDirectories(ArchiveSafety.resolve(root, name));
                    skip(tar, size);
                }
                case '0', 0, '7' -> {
                    Path target = ArchiveSafety.resolve(root, name);
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    copyData(tar, size, target);
                    if ((mode & 0111) != 0) {
                        setExecutable(target);
                    }
                    files.add(target);
                }
                case '2' -> {
                    Path target = ArchiveSafety.resolve(root, name);
                    createSymlink(root, target, linkName);
                    skip(tar, size);
                }
                default -> skip(tar, size);
            }
        }
        return files;
    }

    /** Marks a file executable for everyone who can read it; silently ignored on file systems without modes. */
    static void setExecutable(Path file) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            if (perms.contains(PosixFilePermission.GROUP_READ)) {
                perms.add(PosixFilePermission.GROUP_EXECUTE);
            }
            if (perms.contains(PosixFilePermission.OTHERS_READ)) {
                perms.add(PosixFilePermission.OTHERS_EXECUTE);
            }
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException | IOException e) {
            if (!file.toFile().setExecutable(true, false)) {
                CoreLog.debug("Could not mark {} executable", file);
            }
        }
    }

    private static void createSymlink(Path root, Path target, String linkName) throws IOException {
        if (linkName == null || linkName.isBlank()) {
            return;
        }
        Path link = Path.of(linkName.replace('\\', '/'));
        if (link.isAbsolute()) {
            CoreLog.debug("Skipping absolute symlink {} -> {}", target, linkName);
            return;
        }
        Path parent = target.getParent();
        Path resolved = (parent == null ? root : parent).resolve(link).normalize();
        if (!resolved.startsWith(root)) {
            CoreLog.debug("Skipping symlink leaving the archive root {} -> {}", target, linkName);
            return;
        }
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.deleteIfExists(target);
            Files.createSymbolicLink(target, link);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            CoreLog.debug("Could not create symlink {} -> {}: {}", target, linkName, e.toString());
        }
    }

    private static void copyData(InputStream tar, long size, Path target) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long remaining = size;
        try (OutputStream out = Files.newOutputStream(target)) {
            while (remaining > 0) {
                int read = tar.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, "tar ends inside " + target);
                }
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }
        skipPadding(tar, size);
    }

    private static byte[] readData(InputStream tar, long size) throws IOException {
        if (size < 0 || size > 1 << 20) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, "tar header entry too large: " + size);
        }
        byte[] data = new byte[(int) size];
        if (!readFully(tar, data)) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, "tar ends inside a header entry");
        }
        skipPadding(tar, size);
        return data;
    }

    private static void skip(InputStream tar, long size) throws IOException {
        long remaining = size;
        while (remaining > 0) {
            long skipped = tar.skip(remaining);
            if (skipped <= 0) {
                if (tar.read() < 0) {
                    throw new EOFException("tar ends inside an entry");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
        skipPadding(tar, size);
    }

    private static void skipPadding(InputStream tar, long size) throws IOException {
        long padding = (BLOCK - (size % BLOCK)) % BLOCK;
        long remaining = padding;
        while (remaining > 0) {
            long skipped = tar.skip(remaining);
            if (skipped <= 0) {
                if (tar.read() < 0) {
                    return;
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static boolean readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = in.read(buffer, offset, buffer.length - offset);
            if (read < 0) {
                if (offset == 0) {
                    return false;
                }
                throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, "truncated tar header");
            }
            offset += read;
        }
        return true;
    }

    private static boolean isZero(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String string(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static String trimNul(String s) {
        int end = s.indexOf('\0');
        return end >= 0 ? s.substring(0, end) : s;
    }

    private static long octal(byte[] header, int offset, int length) throws LocalAiException {
        String text = string(header, offset, length).trim();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(text, 8);
        } catch (NumberFormatException e) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, "bad octal field in tar header");
        }
    }

    /** Size field: octal, or base-256 when the first byte has its high bit set (GNU large files). */
    private static long number(byte[] header, int offset, int length) throws LocalAiException {
        if ((header[offset] & 0x80) != 0) {
            long value = 0;
            for (int i = 1; i < length; i++) {
                value = (value << 8) | (header[offset + i] & 0xFF);
            }
            return value;
        }
        return octal(header, offset, length);
    }

    /** Parsed pax extended header records ({@code "<len> <key>=<value>\n"}). */
    private static final class PaxHeader {
        String path;
        String linkPath;
        long size = -1;

        static PaxHeader parse(byte[] data) throws LocalAiException {
            PaxHeader pax = new PaxHeader();
            int pos = 0;
            while (pos < data.length) {
                int space = indexOf(data, (byte) ' ', pos);
                if (space < 0) {
                    break;
                }
                int recordLength;
                try {
                    recordLength = Integer.parseInt(new String(data, pos, space - pos, StandardCharsets.US_ASCII));
                } catch (NumberFormatException e) {
                    throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, "bad pax record length");
                }
                if (recordLength <= 0 || pos + recordLength > data.length) {
                    throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, "bad pax record length");
                }
                String record = new String(data, space + 1, pos + recordLength - space - 2, StandardCharsets.UTF_8);
                int eq = record.indexOf('=');
                if (eq > 0) {
                    String key = record.substring(0, eq);
                    String value = record.substring(eq + 1);
                    switch (key) {
                        case "path" -> pax.path = value;
                        case "linkpath" -> pax.linkPath = value;
                        case "size" -> {
                            try {
                                pax.size = Long.parseLong(value.trim());
                            } catch (NumberFormatException e) {
                                throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, "bad pax size");
                            }
                        }
                        default -> {
                            // other keys (mtime, uid, ...) are not needed
                        }
                    }
                }
                pos += recordLength;
            }
            return pax;
        }

        private static int indexOf(byte[] data, byte value, int from) {
            for (int i = from; i < data.length; i++) {
                if (data[i] == value) {
                    return i;
                }
            }
            return -1;
        }
    }
}
