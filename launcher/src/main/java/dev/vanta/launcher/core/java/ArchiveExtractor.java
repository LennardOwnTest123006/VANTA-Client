package dev.vanta.launcher.core.java;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts {@code .zip} and {@code .tar.gz} archives with path-traversal protection.
 *
 * <p>Every entry path is normalised and must stay below the destination directory; absolute paths, {@code ..}
 * segments and symbolic links pointing outside are rejected with {@link UnsafeArchiveException}. Executable
 * bits from tar headers are preserved on POSIX file systems. No extracted file is ever executed by this class.</p>
 */
public final class ArchiveExtractor {

    private static final int TAR_BLOCK = 512;

    private ArchiveExtractor() {
    }

    /**
     * Extracts an archive chosen by file extension.
     *
     * @param archive     {@code .zip}, {@code .tar.gz} or {@code .tgz}
     * @param destination destination directory (created)
     * @throws IOException on failure or unsafe content
     */
    public static void extract(final Path archive, final Path destination) throws IOException {
        final String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            extractZip(archive, destination);
        } else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            extractTarGz(archive, destination);
        } else {
            throw new IOException("Unsupported archive type: " + archive.getFileName());
        }
    }

    /**
     * Extracts a zip archive.
     *
     * @param archive     zip file
     * @param destination destination directory
     * @throws IOException on failure or unsafe content
     */
    public static void extractZip(final Path archive, final Path destination) throws IOException {
        final Path dest = prepare(destination);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                final Path target = safeResolve(dest, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    if (isExecutableCandidate(entry.getName())) {
                        setExecutable(target);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    /**
     * Extracts a gzip-compressed tar archive (ustar / GNU long names / pax path records).
     *
     * @param archive     tar.gz file
     * @param destination destination directory
     * @throws IOException on failure or unsafe content
     */
    public static void extractTarGz(final Path archive, final Path destination) throws IOException {
        final Path dest = prepare(destination);
        try (InputStream in = new GZIPInputStream(Files.newInputStream(archive), 64 * 1024)) {
            extractTar(in, dest);
        }
    }

    /**
     * Extracts an uncompressed tar stream.
     *
     * @param in          tar stream
     * @param destination destination directory
     * @throws IOException on failure or unsafe content
     */
    public static void extractTar(final InputStream in, final Path destination) throws IOException {
        final Path dest = prepare(destination);
        final byte[] header = new byte[TAR_BLOCK];
        String pendingLongName = null;
        String pendingLongLink = null;
        while (readFully(in, header)) {
            if (isZeroBlock(header)) {
                continue; // end-of-archive blocks
            }
            final TarHeader h = TarHeader.parse(header);
            final String name = pendingLongName != null ? pendingLongName : h.name();
            final String link = pendingLongLink != null ? pendingLongLink : h.linkName();
            pendingLongName = null;
            pendingLongLink = null;
            switch (h.type()) {
                case 'L' -> pendingLongName = readString(in, h.size());
                case 'K' -> pendingLongLink = readString(in, h.size());
                case 'x' -> {
                    final String pax = readString(in, h.size());
                    final String paxPath = paxRecord(pax, "path");
                    if (paxPath != null) {
                        pendingLongName = paxPath;
                    }
                    final String paxLink = paxRecord(pax, "linkpath");
                    if (paxLink != null) {
                        pendingLongLink = paxLink;
                    }
                }
                case 'g' -> skip(in, h.size());
                case '5' -> {
                    Files.createDirectories(safeResolve(dest, name));
                    skip(in, h.size());
                }
                case '0', '\0', '7' -> {
                    final Path target = safeResolve(dest, name);
                    Files.createDirectories(target.getParent());
                    copyExactly(in, target, h.size());
                    if ((h.mode() & 0111) != 0) {
                        setExecutable(target);
                    }
                }
                case '2' -> {
                    final Path target = safeResolve(dest, name);
                    final Path linkTarget = target.getParent().resolve(link).normalize();
                    if (!linkTarget.startsWith(dest)) {
                        throw new UnsafeArchiveException("Symbolic link escapes destination: " + name + " -> " + link);
                    }
                    Files.createDirectories(target.getParent());
                    Files.deleteIfExists(target);
                    try {
                        Files.createSymbolicLink(target, target.getParent().relativize(linkTarget));
                    } catch (UnsupportedOperationException | IOException e) {
                        // File system without symlink support (or Windows without privilege): copy later if target exists.
                        if (Files.isRegularFile(linkTarget)) {
                            Files.copy(linkTarget, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    skip(in, h.size());
                }
                case '1' -> {
                    final Path target = safeResolve(dest, name);
                    final Path linkTarget = safeResolve(dest, link);
                    Files.createDirectories(target.getParent());
                    if (Files.isRegularFile(linkTarget)) {
                        Files.copy(linkTarget, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    skip(in, h.size());
                }
                default -> skip(in, h.size()); // character/block devices, fifos: ignored
            }
        }
    }

    /**
     * Resolves an entry name below the destination, rejecting traversal.
     *
     * @param dest  destination (absolute, normalised)
     * @param entry entry name
     * @return safe absolute path
     * @throws UnsafeArchiveException when the entry escapes
     */
    static Path safeResolve(final Path dest, final String entry) throws UnsafeArchiveException {
        if (entry == null || entry.isEmpty()) {
            throw new UnsafeArchiveException("Archive entry has an empty name");
        }
        final String normalisedName = entry.replace('\\', '/');
        if (normalisedName.startsWith("/") || normalisedName.matches("^[A-Za-z]:.*")) {
            throw new UnsafeArchiveException("Archive entry has an absolute path: " + entry);
        }
        for (String segment : normalisedName.split("/")) {
            if ("..".equals(segment)) {
                throw new UnsafeArchiveException("Archive entry escapes destination: " + entry);
            }
        }
        final Path resolved = dest.resolve(normalisedName).normalize();
        if (!resolved.startsWith(dest)) {
            throw new UnsafeArchiveException("Archive entry escapes destination: " + entry);
        }
        return resolved;
    }

    private static Path prepare(final Path destination) throws IOException {
        Files.createDirectories(destination);
        return destination.toAbsolutePath().normalize();
    }

    private static boolean isExecutableCandidate(final String entryName) {
        final String n = entryName.replace('\\', '/');
        return n.contains("/bin/") || n.contains("/lib/jspawnhelper") || n.endsWith("/lib/jexec");
    }

    private static void setExecutable(final Path file) throws IOException {
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            final Set<PosixFilePermission> perms = EnumSet.copyOf(Files.getPosixFilePermissions(file));
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        }
    }

    private static boolean readFully(final InputStream in, final byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            final int n = in.read(buf, off, buf.length - off);
            if (n < 0) {
                if (off == 0) {
                    return false;
                }
                throw new IOException("Truncated tar header");
            }
            off += n;
        }
        return true;
    }

    private static boolean isZeroBlock(final byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String readString(final InputStream in, final long size) throws IOException {
        if (size > 1024L * 1024L) {
            throw new IOException("Tar meta entry too large: " + size);
        }
        final byte[] data = new byte[(int) size];
        if (!readFully(in, data)) {
            throw new IOException("Truncated tar entry");
        }
        skipPadding(in, size);
        int end = data.length;
        while (end > 0 && data[end - 1] == 0) {
            end--;
        }
        return new String(data, 0, end, StandardCharsets.UTF_8);
    }

    private static String paxRecord(final String pax, final String key) {
        // Records are "<length> <key>=<value>\n"
        for (String line : pax.split("\n")) {
            final int sp = line.indexOf(' ');
            if (sp < 0) {
                continue;
            }
            final String kv = line.substring(sp + 1);
            final int eq = kv.indexOf('=');
            if (eq > 0 && kv.substring(0, eq).equals(key)) {
                return kv.substring(eq + 1);
            }
        }
        return null;
    }

    private static void copyExactly(final InputStream in, final Path target, final long size) throws IOException {
        try (java.io.OutputStream out = Files.newOutputStream(target)) {
            final byte[] buf = new byte[64 * 1024];
            long remaining = size;
            while (remaining > 0) {
                final int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (n < 0) {
                    throw new IOException("Truncated tar entry " + target.getFileName());
                }
                out.write(buf, 0, n);
                remaining -= n;
            }
        }
        skipPadding(in, size);
    }

    private static void skip(final InputStream in, final long size) throws IOException {
        long remaining = size;
        final byte[] buf = new byte[8192];
        while (remaining > 0) {
            final int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (n < 0) {
                throw new IOException("Truncated tar entry");
            }
            remaining -= n;
        }
        skipPadding(in, size);
    }

    private static void skipPadding(final InputStream in, final long size) throws IOException {
        final long pad = (TAR_BLOCK - (size % TAR_BLOCK)) % TAR_BLOCK;
        if (pad > 0) {
            final byte[] buf = new byte[(int) pad];
            if (!readFully(in, buf)) {
                throw new IOException("Truncated tar padding");
            }
        }
    }

    /**
     * Parsed ustar header.
     *
     * @param name     entry name (prefix joined)
     * @param mode     permission bits
     * @param size     entry size
     * @param type     type flag
     * @param linkName link target
     */
    record TarHeader(String name, int mode, long size, char type, String linkName) {

        static TarHeader parse(final byte[] h) throws IOException {
            final String name = str(h, 0, 100);
            final int mode = (int) octal(h, 100, 8);
            final long size = octal(h, 124, 12);
            final char type = (char) h[156];
            final String link = str(h, 157, 100);
            final String magic = str(h, 257, 6);
            final String prefix = magic.startsWith("ustar") ? str(h, 345, 155) : "";
            final String full = prefix.isEmpty() ? name : prefix + "/" + name;
            return new TarHeader(full, mode, size, type, link);
        }

        private static String str(final byte[] h, final int off, final int len) {
            int end = off;
            while (end < off + len && h[end] != 0) {
                end++;
            }
            return new String(h, off, end - off, StandardCharsets.UTF_8);
        }

        private static long octal(final byte[] h, final int off, final int len) throws IOException {
            // GNU base-256 encoding for large sizes
            if ((h[off] & 0x80) != 0) {
                long value = 0;
                for (int i = 1; i < len; i++) {
                    value = (value << 8) | (h[off + i] & 0xFF);
                }
                return value;
            }
            final String s = str(h, off, len).trim();
            if (s.isEmpty()) {
                return 0;
            }
            try {
                return Long.parseLong(s, 8);
            } catch (NumberFormatException e) {
                throw new IOException("Malformed tar header field: '" + s + "'", e);
            }
        }
    }
}
