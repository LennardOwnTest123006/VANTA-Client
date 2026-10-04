package dev.vanta.launcher.core.model;

/**
 * A downloadable file reference from a Mojang version JSON ({@code downloads.client}, {@code libraries[].downloads.artifact}).
 *
 * @param path relative path below the libraries directory (artifacts only, may be {@code null})
 * @param url  absolute download URL
 * @param sha1 hex SHA-1 (may be {@code null} when Mojang does not publish one)
 * @param size size in bytes, {@code 0} when unknown
 */
public record Download(String path, String url, String sha1, long size) {

    /** @return whether a SHA-1 is available */
    public boolean hasSha1() {
        return sha1 != null && !sha1.isBlank();
    }
}
