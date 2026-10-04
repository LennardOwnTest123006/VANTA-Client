package dev.vanta.launcher.core.net;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Streaming digest helpers.
 */
public final class Checksums {

    private static final HexFormat HEX = HexFormat.of();
    private static final int BUFFER = 64 * 1024;

    private Checksums() {
    }

    /**
     * @param file file
     * @return lower-case hex SHA-1
     * @throws IOException on read failure
     */
    public static String sha1Hex(final Path file) throws IOException {
        return hex(file, HashAlgorithm.SHA1);
    }

    /**
     * @param file file
     * @return lower-case hex SHA-256
     * @throws IOException on read failure
     */
    public static String sha256Hex(final Path file) throws IOException {
        return hex(file, HashAlgorithm.SHA256);
    }

    /**
     * @param file      file
     * @param algorithm algorithm
     * @return lower-case hex digest
     * @throws IOException on read failure
     */
    public static String hex(final Path file, final HashAlgorithm algorithm) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return hex(in, algorithm);
        }
    }

    /**
     * Digests a stream to its end (the stream is not closed).
     *
     * @param in        stream
     * @param algorithm algorithm
     * @return lower-case hex digest
     * @throws IOException on read failure
     */
    public static String hex(final InputStream in, final HashAlgorithm algorithm) throws IOException {
        final MessageDigest digest = algorithm.newDigest();
        final byte[] buf = new byte[BUFFER];
        int n;
        while ((n = in.read(buf)) >= 0) {
            digest.update(buf, 0, n);
        }
        return HEX.formatHex(digest.digest());
    }

    /**
     * @param bytes     data
     * @param algorithm algorithm
     * @return lower-case hex digest
     */
    public static String hex(final byte[] bytes, final HashAlgorithm algorithm) {
        return HEX.formatHex(algorithm.newDigest().digest(bytes));
    }

    /**
     * @param digest raw digest
     * @return lower-case hex
     */
    public static String toHex(final byte[] digest) {
        return HEX.formatHex(digest);
    }
}
