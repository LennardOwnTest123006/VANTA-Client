package dev.vanta.core.modrinth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** SHA-512 helpers producing lower-case hex, the format Modrinth publishes. */
public final class Sha512 {
    private static final HexFormat HEX = HexFormat.of();

    private Sha512() {
    }

    /** A fresh SHA-512 digest. */
    public static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 is mandatory on every JVM", e);
        }
    }

    /** Lower-case hex of a finished digest. */
    public static String hex(MessageDigest digest) {
        return HEX.formatHex(digest.digest());
    }

    /** Digest of bytes. */
    public static String hex(byte[] data) {
        return HEX.formatHex(digest().digest(data));
    }

    /** Digest of a file. */
    public static String hex(Path file) throws IOException {
        MessageDigest md = digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                md.update(buffer, 0, read);
            }
        }
        return hex(md);
    }

    /** True when the file's digest equals {@code expected} (case-insensitive) and {@code expected} is well-formed. */
    public static boolean matches(Path file, String expected) throws IOException {
        return ModrinthFile.isSha512(expected) && hex(file).equals(expected.toLowerCase(Locale.ROOT));
    }
}
