package dev.vanta.launcher.core.auth;

import com.google.gson.annotations.SerializedName;

import java.time.Duration;
import java.time.Instant;

/**
 * Response of the Microsoft device authorization endpoint.
 *
 * @param deviceCode      opaque code polled by the launcher (secret)
 * @param userCode        short code the user types at {@link #verificationUri()}
 * @param verificationUri page where the user signs in
 * @param expiresIn       seconds until the codes expire
 * @param interval        minimum polling interval in seconds
 * @param message         Microsoft's ready-made instruction text
 * @param issuedAt        epoch millis when the launcher received the response
 */
public record DeviceCode(@SerializedName("device_code") String deviceCode, @SerializedName("user_code") String userCode,
                         @SerializedName("verification_uri") String verificationUri, @SerializedName("expires_in") int expiresIn,
                         int interval, String message, long issuedAt) {

    public DeviceCode {
        interval = interval <= 0 ? 5 : interval;
        expiresIn = expiresIn <= 0 ? 900 : expiresIn;
        message = message == null ? "" : message;
        verificationUri = verificationUri == null ? "https://www.microsoft.com/link" : verificationUri;
    }

    /**
     * Copy with the issue time set.
     *
     * @param now current time
     * @return copy
     */
    public DeviceCode issuedAt(final Instant now) {
        return new DeviceCode(deviceCode, userCode, verificationUri, expiresIn, interval, message, now.toEpochMilli());
    }

    /** @return when the code expires */
    public Instant expiresAt() {
        return Instant.ofEpochMilli(issuedAt).plus(Duration.ofSeconds(expiresIn));
    }

    /**
     * @param now current time
     * @return whether the code is still valid
     */
    public boolean isValid(final Instant now) {
        return now.isBefore(expiresAt());
    }

    /** Safe representation without the device code. */
    @Override
    public String toString() {
        return "DeviceCode[userCode=" + userCode + ", verificationUri=" + verificationUri + "]";
    }
}
