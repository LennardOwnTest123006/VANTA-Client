package dev.vanta.launcher.core.auth;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.HttpRequestSpec;
import dev.vanta.launcher.core.net.HttpResult;
import dev.vanta.launcher.core.net.HttpTransport;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.Sleeper;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Microsoft account sign-in for Minecraft: Java Edition.
 *
 * <ol>
 *   <li>OAuth 2.0 device code flow against {@code login.microsoftonline.com/consumers} with scope
 *       {@code XboxLive.signin offline_access}</li>
 *   <li>Xbox Live user authentication ({@code user.auth.xboxlive.com})</li>
 *   <li>XSTS authorization for relying party {@code rp://api.minecraftservices.com/}</li>
 *   <li>{@code login_with_xbox} at Minecraft services</li>
 *   <li>Entitlement check ({@code entitlements/mcstore}) and profile lookup ({@code minecraft/profile})</li>
 * </ol>
 *
 * <p>All HTTP goes through an injectable {@link HttpTransport}. Tokens are registered with the global
 * {@link dev.vanta.launcher.core.log.Redactor} as soon as they are received.</p>
 */
public final class MicrosoftAuthService {

    /** OAuth scope requested from Microsoft. */
    public static final String SCOPE = "XboxLive.signin offline_access";
    /** Relying party for XSTS tokens used by Minecraft services. */
    public static final String RELYING_PARTY = "rp://api.minecraftservices.com/";

    private static final Logger LOG = LauncherLog.get("Auth");
    /** Compact serializer for request payloads (the shared instance pretty prints for files). */
    private static final Gson WIRE = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    /**
     * Endpoints of the sign-in chain.
     *
     * @param deviceCode   Microsoft device authorization endpoint
     * @param token        Microsoft token endpoint
     * @param xboxAuth     Xbox Live user authentication
     * @param xsts         XSTS authorization
     * @param mcLogin      Minecraft services login_with_xbox
     * @param entitlements Minecraft services entitlements/mcstore
     * @param profile      Minecraft services minecraft/profile
     */
    public record Endpoints(URI deviceCode, URI token, URI xboxAuth, URI xsts, URI mcLogin, URI entitlements, URI profile) {

        /** Production endpoints. */
        public static final Endpoints DEFAULT = new Endpoints(
            URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"),
            URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"),
            URI.create("https://user.auth.xboxlive.com/user/authenticate"),
            URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"),
            URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"),
            URI.create("https://api.minecraftservices.com/entitlements/mcstore"),
            URI.create("https://api.minecraftservices.com/minecraft/profile"));

        /**
         * Endpoints relative to one base (test servers).
         *
         * @param base base URL ending with a slash
         * @return endpoints
         */
        public static Endpoints relativeTo(final URI base) {
            return new Endpoints(base.resolve("oauth/devicecode"), base.resolve("oauth/token"), base.resolve("xbox/authenticate"),
                base.resolve("xsts/authorize"), base.resolve("mc/login_with_xbox"), base.resolve("mc/entitlements"),
                base.resolve("mc/profile"));
        }
    }

    private final HttpTransport transport;
    private final Endpoints endpoints;
    private final Supplier<Optional<String>> clientId;
    private final Clock clock;
    private final Sleeper sleeper;

    /**
     * @param transport HTTP transport
     * @param endpoints endpoints
     * @param clientId  supplier of the configured client id (settings, then environment)
     * @param clock     clock
     * @param sleeper   sleeper used while polling
     */
    public MicrosoftAuthService(final HttpTransport transport, final Endpoints endpoints, final Supplier<Optional<String>> clientId,
                                final Clock clock, final Sleeper sleeper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.endpoints = Objects.requireNonNull(endpoints, "endpoints");
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /**
     * Resolves the client id from settings then environment.
     *
     * @param settingsValue value from settings (may be empty)
     * @param env           environment variables
     * @return supplier
     */
    public static Supplier<Optional<String>> clientIdFrom(final Supplier<String> settingsValue, final Map<String, String> env) {
        return () -> {
            final String fromSettings = settingsValue.get();
            if (fromSettings != null && !fromSettings.isBlank()) {
                return Optional.of(fromSettings.trim());
            }
            final String fromEnv = env.get(dev.vanta.launcher.core.settings.LauncherSettings.MS_CLIENT_ID_ENV);
            return fromEnv == null || fromEnv.isBlank() ? Optional.empty() : Optional.of(fromEnv.trim());
        };
    }

    /** @return whether a client id is configured */
    public boolean isConfigured() {
        return clientId.get().isPresent();
    }

    /**
     * Starts the device code flow.
     *
     * @return device code to show to the user
     * @throws AuthNotConfiguredException when no client id is configured
     * @throws AuthException              when Microsoft refuses
     * @throws IOException                on transport failure
     * @throws InterruptedException       when interrupted
     */
    public DeviceCode beginDeviceCode() throws AuthException, IOException, InterruptedException {
        final String id = clientId.get().orElseThrow(AuthNotConfiguredException::new);
        final Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", id);
        form.put("scope", SCOPE);
        final JsonObject body = postForm(endpoints.deviceCode(), form, "Microsoft device authorization");
        if (body.has("error")) {
            throw new AuthException("Microsoft refused to start sign-in: " + describeOAuthError(body));
        }
        final DeviceCode code = Json.GSON.fromJson(body, DeviceCode.class).issuedAt(Instant.now(clock));
        LauncherLog.redact(code.deviceCode());
        LOG.log(Level.INFO, "Device code issued; user code {0}", code.userCode());
        return code;
    }

    /**
     * Polls the token endpoint until the user completes sign-in, the code expires, or cancellation.
     *
     * @param code  device code
     * @param token cancellation token
     * @return Microsoft tokens
     * @throws AuthException        when the user declined, the code expired, or Microsoft refused
     * @throws IOException          on transport failure
     * @throws InterruptedException when interrupted
     */
    public MicrosoftTokens pollForToken(final DeviceCode code, final CancellationToken token) throws AuthException, IOException, InterruptedException {
        final String id = clientId.get().orElseThrow(AuthNotConfiguredException::new);
        int interval = code.interval();
        while (true) {
            token.throwIfCancelled();
            if (!code.isValid(Instant.now(clock))) {
                throw new AuthException("The sign-in code expired before it was used. Start the sign-in again.");
            }
            sleeper.sleep(Duration.ofSeconds(interval));
            token.throwIfCancelled();
            final Map<String, String> form = new LinkedHashMap<>();
            form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            form.put("client_id", id);
            form.put("device_code", code.deviceCode());
            final JsonObject body = postForm(endpoints.token(), form, "Microsoft token");
            if (!body.has("error")) {
                return tokens(body);
            }
            final String error = body.get("error").getAsString();
            switch (error) {
                case "authorization_pending" -> {
                    // keep polling
                }
                case "slow_down" -> interval += 5;
                case "authorization_declined" -> throw new AuthException("Sign-in was declined in the browser.");
                case "expired_token" -> throw new AuthException("The sign-in code expired before it was used. Start the sign-in again.");
                case "bad_verification_code" -> throw new AuthException("Microsoft did not recognise the sign-in code. Start the sign-in again.");
                default -> throw new AuthException("Microsoft sign-in failed: " + describeOAuthError(body));
            }
        }
    }

    /**
     * Exchanges Microsoft tokens for a Minecraft account (Xbox Live → XSTS → Minecraft → entitlements → profile).
     *
     * @param ms Microsoft tokens
     * @return account
     * @throws AuthException        on refusal ({@link XboxAuthException}, {@link NoGameOwnershipException}, ...)
     * @throws IOException          on transport failure
     * @throws InterruptedException when interrupted
     */
    public Account completeLogin(final MicrosoftTokens ms) throws AuthException, IOException, InterruptedException {
        final XblToken xbl = authenticateXbox(ms.accessToken());
        final XblToken xsts = authorizeXsts(xbl.token());
        final McToken mc = loginMinecraft(xsts.userHash(), xsts.token());
        final boolean owns = checkEntitlements(mc.accessToken());
        final Optional<Profile> profile = fetchProfile(mc.accessToken());
        if (profile.isEmpty()) {
            // Game Pass accounts can have an empty entitlement list but still a profile; the reverse (entitlement
            // without profile) means the account never created a Java profile.
            throw new NoGameOwnershipException(owns ? "This account has a Minecraft entitlement but no Java Edition profile. "
                + "Sign in at minecraft.net once to create the profile; it" : "This Microsoft account");
        }
        final Profile p = profile.get();
        final long expiresAt = Instant.now(clock).plusSeconds(mc.expiresIn()).toEpochMilli();
        LauncherLog.redact(mc.accessToken());
        LauncherLog.redact(ms.refreshToken());
        LOG.log(Level.INFO, "Signed in as {0}", p.name());
        return new Account(Account.dashUuid(p.id()), p.name(), xsts.xuid(), mc.accessToken(), expiresAt, ms.refreshToken(),
            AccountType.MICROSOFT);
    }

    /**
     * Full interactive login: poll + complete.
     *
     * @param code  device code from {@link #beginDeviceCode()}
     * @param token cancellation
     * @return account
     * @throws AuthException        on refusal
     * @throws IOException          on transport failure
     * @throws InterruptedException when interrupted
     */
    public Account login(final DeviceCode code, final CancellationToken token) throws AuthException, IOException, InterruptedException {
        return completeLogin(pollForToken(code, token));
    }

    /**
     * Refreshes an account with its refresh token and re-runs the Xbox/Minecraft chain.
     *
     * @param account account with a refresh token
     * @return refreshed account
     * @throws AuthException        when the refresh token is invalid or the chain fails
     * @throws IOException          on transport failure
     * @throws InterruptedException when interrupted
     */
    public Account refresh(final Account account) throws AuthException, IOException, InterruptedException {
        if (!account.canRefresh()) {
            throw new AuthException("This account cannot be refreshed; sign in again.");
        }
        final String id = clientId.get().orElseThrow(AuthNotConfiguredException::new);
        final Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("client_id", id);
        form.put("refresh_token", account.refreshToken());
        form.put("scope", SCOPE);
        final JsonObject body = postForm(endpoints.token(), form, "Microsoft token refresh");
        if (body.has("error")) {
            throw new AuthException("Your Microsoft session has expired. Sign in again. (" + describeOAuthError(body) + ")");
        }
        final MicrosoftTokens tokens = tokens(body);
        final MicrosoftTokens withRefresh = tokens.refreshToken().isEmpty()
            ? new MicrosoftTokens(tokens.accessToken(), account.refreshToken(), tokens.expiresIn(), tokens.tokenType(), tokens.scope())
            : tokens;
        return completeLogin(withRefresh);
    }

    // ------------------------------------------------------------------------------------------------------
    // Chain steps (package-private for tests)
    // ------------------------------------------------------------------------------------------------------

    /**
     * Xbox Live / XSTS token.
     *
     * @param token    token
     * @param userHash {@code uhs} display claim
     * @param xuid     {@code xid} display claim (empty when absent)
     */
    record XblToken(String token, String userHash, String xuid) {
    }

    /**
     * Minecraft services token.
     *
     * @param accessToken token
     * @param expiresIn   lifetime in seconds
     */
    record McToken(String accessToken, long expiresIn) {
    }

    /**
     * Minecraft profile.
     *
     * @param id   undashed UUID
     * @param name player name
     */
    record Profile(String id, String name) {
    }

    XblToken authenticateXbox(final String msAccessToken) throws AuthException, IOException, InterruptedException {
        final JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", "d=" + msAccessToken);
        final JsonObject req = new JsonObject();
        req.add("Properties", props);
        req.addProperty("RelyingParty", "http://auth.xboxlive.com");
        req.addProperty("TokenType", "JWT");
        final JsonObject body = postJson(endpoints.xboxAuth(), req, "Xbox Live authentication", true);
        final XblToken t = parseXbl(body, "Xbox Live");
        LauncherLog.redact(t.token());
        return t;
    }

    XblToken authorizeXsts(final String xblToken) throws AuthException, IOException, InterruptedException {
        final JsonObject props = new JsonObject();
        props.addProperty("SandboxId", "RETAIL");
        final JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        props.add("UserTokens", userTokens);
        final JsonObject req = new JsonObject();
        req.add("Properties", props);
        req.addProperty("RelyingParty", RELYING_PARTY);
        req.addProperty("TokenType", "JWT");
        final HttpRequestSpec spec = HttpRequestSpec.postJson(endpoints.xsts(), WIRE.toJson(req))
            .withHeader("x-xbl-contract-version", "1");
        try (HttpResult result = transport.execute(spec)) {
            final String text = result.bodyAsString();
            if (result.status() == 401) {
                long xerr = 0;
                try {
                    final JsonElement el = Json.tree(text);
                    if (el.isJsonObject() && el.getAsJsonObject().has("XErr")) {
                        xerr = el.getAsJsonObject().get("XErr").getAsLong();
                    }
                } catch (JsonParseException | IllegalStateException ignored) {
                    // raw text is passed through
                }
                throw new XboxAuthException(xerr, xerr == 0 ? text : null);
            }
            if (!result.isSuccess()) {
                throw new AuthException("XSTS authorization failed with HTTP " + result.status());
            }
            final XblToken t = parseXbl(parseObject(text, "XSTS"), "XSTS");
            LauncherLog.redact(t.token());
            return t;
        }
    }

    McToken loginMinecraft(final String userHash, final String xstsToken) throws AuthException, IOException, InterruptedException {
        final JsonObject req = new JsonObject();
        req.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        final JsonObject body = postJson(endpoints.mcLogin(), req, "Minecraft services login", false);
        if (!body.has("access_token")) {
            throw new AuthException("Minecraft services did not return an access token.");
        }
        final long expiresIn = body.has("expires_in") ? body.get("expires_in").getAsLong() : 86400L;
        final String token = body.get("access_token").getAsString();
        LauncherLog.redact(token);
        return new McToken(token, expiresIn);
    }

    boolean checkEntitlements(final String mcToken) throws AuthException, IOException, InterruptedException {
        final HttpRequestSpec spec = HttpRequestSpec.get(endpoints.entitlements()).withHeader("Authorization", "Bearer " + mcToken)
            .withHeader("Accept", "application/json");
        try (HttpResult result = transport.execute(spec)) {
            final String text = result.bodyAsString();
            if (!result.isSuccess()) {
                throw new AuthException("Minecraft services could not verify game ownership (HTTP " + result.status() + ").");
            }
            final JsonObject body = parseObject(text, "entitlements");
            if (!body.has("items") || !body.get("items").isJsonArray()) {
                return false;
            }
            for (JsonElement item : body.getAsJsonArray("items")) {
                if (item.isJsonObject() && item.getAsJsonObject().has("name")) {
                    final String name = item.getAsJsonObject().get("name").getAsString();
                    if ("product_minecraft".equals(name) || "game_minecraft".equals(name)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    Optional<Profile> fetchProfile(final String mcToken) throws AuthException, IOException, InterruptedException {
        final HttpRequestSpec spec = HttpRequestSpec.get(endpoints.profile()).withHeader("Authorization", "Bearer " + mcToken)
            .withHeader("Accept", "application/json");
        try (HttpResult result = transport.execute(spec)) {
            final String text = result.bodyAsString();
            if (result.status() == 404) {
                return Optional.empty();
            }
            if (!result.isSuccess()) {
                throw new AuthException("Minecraft services could not load the player profile (HTTP " + result.status() + ").");
            }
            final JsonObject body = parseObject(text, "profile");
            if (!body.has("id") || !body.has("name")) {
                throw new AuthException("Minecraft services returned an incomplete profile.");
            }
            return Optional.of(new Profile(body.get("id").getAsString(), body.get("name").getAsString()));
        }
    }

    // ------------------------------------------------------------------------------------------------------

    private MicrosoftTokens tokens(final JsonObject body) throws AuthException {
        final MicrosoftTokens t = Json.GSON.fromJson(body, MicrosoftTokens.class);
        if (t.accessToken().isEmpty()) {
            throw new AuthException("Microsoft did not return an access token.");
        }
        LauncherLog.redact(t.accessToken());
        LauncherLog.redact(t.refreshToken());
        return t;
    }

    private static XblToken parseXbl(final JsonObject body, final String what) throws AuthException {
        if (!body.has("Token")) {
            throw new AuthException(what + " did not return a token.");
        }
        String uhs = "";
        String xid = "";
        if (body.has("DisplayClaims") && body.getAsJsonObject("DisplayClaims").has("xui")) {
            final JsonArray xui = body.getAsJsonObject("DisplayClaims").getAsJsonArray("xui");
            if (!xui.isEmpty() && xui.get(0).isJsonObject()) {
                final JsonObject first = xui.get(0).getAsJsonObject();
                uhs = first.has("uhs") ? first.get("uhs").getAsString() : "";
                xid = first.has("xid") ? first.get("xid").getAsString() : "";
            }
        }
        if (uhs.isEmpty()) {
            throw new AuthException(what + " did not return a user hash.");
        }
        return new XblToken(body.get("Token").getAsString(), uhs, xid);
    }

    private JsonObject postForm(final URI uri, final Map<String, String> form, final String what) throws AuthException, IOException, InterruptedException {
        try (HttpResult result = transport.execute(HttpRequestSpec.postForm(uri, form))) {
            final String text = result.bodyAsString();
            final JsonObject body = parseObject(text, what);
            if (!result.isSuccess() && !body.has("error")) {
                throw new AuthException(what + " failed with HTTP " + result.status());
            }
            return body;
        }
    }

    private JsonObject postJson(final URI uri, final JsonObject payload, final String what, final boolean contractVersion)
        throws AuthException, IOException, InterruptedException {
        HttpRequestSpec spec = HttpRequestSpec.postJson(uri, WIRE.toJson(payload));
        if (contractVersion) {
            spec = spec.withHeader("x-xbl-contract-version", "1");
        }
        try (HttpResult result = transport.execute(spec)) {
            final String text = result.bodyAsString();
            if (!result.isSuccess()) {
                throw new AuthException(what + " failed with HTTP " + result.status() + (text.isBlank() ? "" : ": " + summarize(text)));
            }
            return parseObject(text, what);
        }
    }

    private static JsonObject parseObject(final String text, final String what) throws AuthException {
        try {
            final JsonElement el = Json.tree(text);
            if (!el.isJsonObject()) {
                throw new AuthException(what + " returned an unexpected response.");
            }
            return el.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new AuthException(what + " returned an unreadable response.", e);
        }
    }

    private static String describeOAuthError(final JsonObject body) {
        final String error = body.has("error") ? body.get("error").getAsString() : "unknown_error";
        final String description = body.has("error_description") ? body.get("error_description").getAsString() : "";
        return description.isEmpty() ? error : error + " - " + description.split("\\R")[0];
    }

    private static String summarize(final String text) {
        final String t = text.strip();
        return t.length() > 160 ? t.substring(0, 160) + "…" : t;
    }
}
