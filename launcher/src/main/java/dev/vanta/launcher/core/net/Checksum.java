package dev.vanta.launcher.core.net;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * An expected digest.
 *
 * @param algorithm algorithm
 * @param hex       lower-case hex digest
 */
public record Checksum(HashAlgorithm algorithm, String hex) {

    public Checksum {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(hex, "hex");
        hex = hex.trim().toLowerCase(Locale.ROOT);
        if (hex.length() != algorithm.hexLength() || !hex.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            throw new IllegalArgumentException("Invalid " + algorithm + " hex digest: '" + hex + "'");
        }
    }

    /**
     * @param hex hex SHA-1
     * @return checksum
     */
    public static Checksum sha1(final String hex) {
        return new Checksum(HashAlgorithm.SHA1, hex);
    }

    /**
     * @param hex hex SHA-256
     * @return checksum
     */
    public static Checksum sha256(final String hex) {
        return new Checksum(HashAlgorithm.SHA256, hex);
    }

    /**
     * @param hex hex SHA-512
     * @return checksum
     */
    public static Checksum sha512(final String hex) {
        return new Checksum(HashAlgorithm.SHA512, hex);
    }

    /**
     * Computes the digest of a file and compares.
     *
     * @param file file
     * @return whether the digest matches
     * @throws IOException on read failure
     */
    public boolean matches(final Path file) throws IOException {
        return hex.equals(Checksums.hex(file, algorithm));
    }

    @Override
    public String toString() {
        return algorithm.name().toLowerCase(Locale.ROOT) + ":" + hex;
    }
}
