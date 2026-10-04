package dev.vanta.launcher.core.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * Atomic file writes: data is written to a sibling temporary file and moved into place, so a crash never leaves
 * a half-written settings file, manifest or download behind.
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /**
     * Writes bytes atomically.
     *
     * @param target destination file
     * @param bytes  content
     * @throws IOException on I/O failure
     */
    public static void write(final Path target, final byte[] bytes) throws IOException {
        final Path dir = target.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        final Path tmp = tempSibling(target);
        try {
            Files.write(tmp, bytes);
            move(tmp, target);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Writes a UTF-8 string atomically.
     *
     * @param target destination file
     * @param text   content
     * @throws IOException on I/O failure
     */
    public static void writeString(final Path target, final String text) throws IOException {
        write(target, text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Moves {@code source} over {@code target}, atomically where the file system supports it and with a plain
     * replacing move otherwise.
     *
     * @param source temporary file (same directory recommended)
     * @param target destination
     * @throws IOException on failure
     */
    public static void move(final Path source, final Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (FileSystemException e) {
            // Windows may refuse an atomic replace while another process holds the target; retry non-atomically.
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Creates the temporary sibling path used for a target.
     *
     * @param target destination file
     * @return {@code <target>.<nanos>.tmp} in the same directory
     */
    public static Path tempSibling(final Path target) {
        final Path abs = target.toAbsolutePath();
        return abs.resolveSibling(abs.getFileName() + "." + Long.toHexString(System.nanoTime()) + ".tmp");
    }

    /**
     * Restricts a file to the owning user where the file system supports POSIX permissions. On Windows the file
     * inherits the ACL of the user profile directory, which is already private to the user.
     *
     * @param file file to restrict
     * @throws IOException on failure
     */
    public static void restrictToOwner(final Path file) throws IOException {
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            final Set<PosixFilePermission> perms = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, perms);
        }
    }
}
