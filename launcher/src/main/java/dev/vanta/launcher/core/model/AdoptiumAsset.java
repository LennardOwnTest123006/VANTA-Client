package dev.vanta.launcher.core.model;

import com.google.gson.annotations.SerializedName;

/**
 * One entry of the Adoptium API {@code /v3/assets/latest/21/hotspot} response.
 *
 * @param binary      binary description
 * @param releaseName release name (e.g. {@code jdk-21.0.4+7})
 * @param vendor      vendor (eclipse)
 * @param version     version details
 */
public record AdoptiumAsset(Binary binary, @SerializedName("release_name") String releaseName, String vendor,
                            Version version) {

    /**
     * Binary description.
     *
     * @param os           {@code windows}, {@code linux}, {@code mac}
     * @param architecture {@code x64}, {@code aarch64}, {@code x86}
     * @param imageType    {@code jre} or {@code jdk}
     * @param jvmImpl      {@code hotspot}
     * @param pkg          package (archive) details
     */
    public record Binary(String os, String architecture, @SerializedName("image_type") String imageType,
                         @SerializedName("jvm_impl") String jvmImpl, @SerializedName("package") Package pkg) {
    }

    /**
     * Archive package.
     *
     * @param name         file name (zip or tar.gz)
     * @param link         download URL
     * @param checksum     hex SHA-256
     * @param checksumLink URL of the checksum file
     * @param size         size in bytes
     */
    public record Package(String name, String link, String checksum, @SerializedName("checksum_link") String checksumLink,
                          long size) {
    }

    /**
     * Version details.
     *
     * @param major    major version
     * @param minor    minor version
     * @param security security version
     * @param build    build number
     * @param semver   full semver
     */
    public record Version(int major, int minor, int security, int build, String semver) {
    }
}
