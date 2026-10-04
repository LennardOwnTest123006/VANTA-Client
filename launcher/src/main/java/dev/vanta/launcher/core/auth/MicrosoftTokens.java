package dev.vanta.launcher.core.auth;

import com.google.gson.annotations.SerializedName;

/**
 * Microsoft OAuth token response.
 *
 * @param accessToken  access token for Xbox Live sign-in
 * @param refreshToken refresh token
 * @param expiresIn    access token lifetime in seconds
 * @param tokenType    token type (Bearer)
 * @param scope        granted scopes
 */
public record MicrosoftTokens(@SerializedName("access_token") String accessToken, @SerializedName("refresh_token") String refreshToken,
                              @SerializedName("expires_in") long expiresIn, @SerializedName("token_type") String tokenType,
                              String scope) {

    public MicrosoftTokens {
        accessToken = accessToken == null ? "" : accessToken;
        refreshToken = refreshToken == null ? "" : refreshToken;
    }

    /** Safe representation without tokens. */
    @Override
    public String toString() {
        return "MicrosoftTokens[expiresIn=" + expiresIn + ", hasRefresh=" + !refreshToken.isEmpty() + "]";
    }
}
