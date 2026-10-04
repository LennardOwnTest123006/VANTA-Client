package dev.vanta.core.release;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * SHA-256 helpers producing lower-case hex, as used in release manifests.
 */
public final class Sha256 {
    private static final HexFormat HEX = HexFormat.of();

    private Sha256() {
    }

    /** Digest of a byte array. */
    public static String hex(byte[] data) {
        return HEX.formatHex(digest().digest(data));
    }

    /** Digest of a stream (read to the end; the stream is not closed). */
    public static String hex(InputStream in) throws IOException {
        MessageDigest md = digest();
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            md.update(buffer, 0, read);
        }
        return HEX.formatHex(md.digest());
    }

    /** Digest of a file. */
    public static String hex(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return hex(in);
        }
    }

    /** True when the file's digest equals {@code expectedHex} (case-insensitive). */
    public static boolean matches(Path file, String expectedHex) throws IOException {
        return isValidHex(expectedHex) && hex(file).equals(expectedHex.toLowerCase(Locale.ROOT));
    }

    /** True for a 64-character hex string. */
    public static boolean isValidHex(String hex) {
        return hex != null && hex.matches("[0-9a-fA-F]{64}");
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every JVM", e);
        }
    }
}
