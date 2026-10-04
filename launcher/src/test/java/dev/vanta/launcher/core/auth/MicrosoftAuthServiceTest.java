package dev.vanta.launcher.core.auth;

import dev.vanta.launcher.core.log.Redactor;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.HttpRequestSpec;
import dev.vanta.launcher.core.net.HttpResult;
import dev.vanta.launcher.core.util.Sleeper;
import dev.vanta.launcher.testutil.FakeTransport;
import dev.vanta.launcher.testutil.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicrosoftAuthServiceTest {

    private static final URI BASE = URI.create("https://auth.test/");
    private static final MicrosoftAuthService.Endpoints ENDPOINTS = MicrosoftAuthService.Endpoints.relativeTo(BASE);
    private static final String CLIENT_ID = "11111111-2222-3333-4444-555555555555";

    private FakeTransport transport;
    private List<Duration> sleeps;
    private MicrosoftAuthService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        transport = new FakeTransport();
        sleeps = new ArrayList<>();
        clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC);
        service = new MicrosoftAuthService(transport, ENDPOINTS, () -> Optional.of(CLIENT_ID), clock, sleeps::add);
    }

    private void happyChain() {
        transport.onJson("/oauth/devicecode", 200, Fixtures.read("auth/devicecode.json"));
        transport.onJson("/xbox/authenticate", 200, Fixtures.read("auth/xbox-authenticate.json"));
        transport.onJson("/xsts/authorize", 200, Fixtures.read("auth/xsts-authorize.json"));
        transport.onJson("/mc/login_with_xbox", 200, Fixtures.read("auth/mc-login.json"));
        transport.onJson("/mc/entitlements", 200, Fixtures.read("auth/entitlements.json"));
        transport.onJson("/mc/profile", 200, Fixtures.read("auth/profile.json"));
    }

    @Test
    void deviceCodeRequestUsesConsumersEndpointScopeAndClientId() throws Exception {
        happyChain();
        final DeviceCode code = service.beginDeviceCode();
        assertEquals("FJ8KQ4RTX", code.userCode());
        assertEquals("https://www.microsoft.com/link", code.verificationUri());
        assertEquals(5, code.interval());
        assertTrue(code.isValid(Instant.now(clock)));
        assertTrue(code.message().contains("FJ8KQ4RTX"));
        assertFalse(code.toString().contains(code.deviceCode()), "device code never printed");
        final HttpRequestSpec req = transport.requests().get(0);
        assertEquals("POST", req.method());
        assertEquals("application/x-www-form-urlencoded", req.headers().get("Content-Type"));
        final String body = transport.bodies().get(0);
        assertTrue(body.contains("client_id=" + CLIENT_ID));
        assertTrue(body.contains("scope=XboxLive.signin+offline_access"));
    }

    @Test
    void fullHappyPathProducesAccountAndRedactsTokens() throws Exception {
        happyChain();
        transport.onJsonSequence("/oauth/token", List.of(
            Map.entry(400, Fixtures.read("auth/token-pending.json")),
            Map.entry(400, Fixtures.read("auth/token-slow-down.json")),
            Map.entry(400, Fixtures.read("auth/token-pending.json")),
            Map.entry(200, Fixtures.read("auth/token-success.json"))));
        final DeviceCode code = service.beginDeviceCode();
        final Account account = service.login(code, new CancellationToken());

        assertEquals("VantaTester", account.name());
        assertEquals("3f2a1b4c-5d6e-7f80-91a2-b3c4d5e6f708", account.uuid());
        assertEquals("3f2a1b4c5d6e7f8091a2b3c4d5e6f708", account.undashedUuid());
        assertEquals("2535412345678901", account.xuid());
        assertEquals(AccountType.MICROSOFT, account.type());
        assertEquals("msa", account.userType());
        assertTrue(account.accessToken().startsWith("eyJhbGciOiJIUzI1NiJ9.MC-TEST"));
        assertTrue(account.refreshToken().startsWith("M.C512_BAY"));
        assertEquals(Instant.now(clock).plusSeconds(86400).toEpochMilli(), account.expiresAt());
        assertFalse(account.isExpired(Instant.now(clock)));
        assertTrue(account.canRefresh());
        assertFalse(account.toString().contains("eyJ"), "toString hides tokens");

        // Polling honoured interval and slow_down (+5s)
        assertEquals(List.of(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10)), sleeps);
        assertEquals(4, transport.hits("/oauth/token"));
        final String poll = transport.bodies().get(1);
        assertTrue(poll.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"));
        assertTrue(poll.contains("device_code=" + code.deviceCode()));

        // Xbox chain payloads
        final String xbox = transport.bodies().get(transport.requests().size() - 5);
        assertTrue(xbox.contains("\"RpsTicket\":\"d=EwAIA61DBAAUm"));
        assertTrue(xbox.contains("\"RelyingParty\":\"http://auth.xboxlive.com\""));
        final String xsts = transport.bodies().get(transport.requests().size() - 4);
        assertTrue(xsts.contains("\"RelyingParty\":\"rp://api.minecraftservices.com/\""));
        assertTrue(xsts.contains("\"SandboxId\":\"RETAIL\""));
        final String mc = transport.bodies().get(transport.requests().size() - 3);
        assertTrue(mc.contains("\"identityToken\":\"XBL3.0 x=12345678901234567890;eyJlbmMiOiJB"));
        final HttpRequestSpec entitlements = transport.requests().get(transport.requests().size() - 2);
        assertEquals("Bearer " + account.accessToken(), entitlements.headers().get("Authorization"));

        // Redaction registered for every secret
        final String log = Redactor.global().apply("tokens: " + account.accessToken() + " " + account.refreshToken() + " " + code.deviceCode());
        assertFalse(log.contains(account.accessToken()));
        assertFalse(log.contains(account.refreshToken()));
        assertFalse(log.contains(code.deviceCode()));
    }

    @Test
    void notConfiguredWithoutClientId() {
        final MicrosoftAuthService unconfigured = new MicrosoftAuthService(transport, ENDPOINTS, Optional::empty, clock, Sleeper.NONE);
        assertFalse(unconfigured.isConfigured());
        final AuthNotConfiguredException e = assertThrows(AuthNotConfiguredException.class, unconfigured::beginDeviceCode);
        assertTrue(e.getMessage().contains("VANTA_MS_CLIENT_ID"));
        assertTrue(e.getMessage().contains("msClientId"));
        assertTrue(transport.requests().isEmpty(), "no network call without configuration");
    }

    @Test
    void clientIdFallsBackToEnvironment() {
        assertEquals(Optional.of("from-env"), MicrosoftAuthService.clientIdFrom(() -> "", Map.of("VANTA_MS_CLIENT_ID", "from-env")).get());
        assertEquals(Optional.of("from-settings"), MicrosoftAuthService.clientIdFrom(() -> "from-settings", Map.of("VANTA_MS_CLIENT_ID", "from-env")).get());
        assertEquals(Optional.empty(), MicrosoftAuthService.clientIdFrom(() -> " ", Map.of()).get());
    }

    @Test
    void declinedAndExpiredCodes() throws Exception {
        happyChain();
        transport.onJson("/oauth/token", 400, Fixtures.read("auth/token-declined.json"));
        final DeviceCode code = service.beginDeviceCode();
        assertTrue(assertThrows(AuthException.class, () -> service.pollForToken(code, new CancellationToken())).getMessage().contains("declined"));

        transport.onJson("/oauth/token", 400, Fixtures.read("auth/token-expired.json"));
        assertTrue(assertThrows(AuthException.class, () -> service.pollForToken(code, new CancellationToken())).getMessage().contains("expired"));

        // Local expiry check without waiting for the server
        final MicrosoftAuthService later = new MicrosoftAuthService(transport, ENDPOINTS, () -> Optional.of(CLIENT_ID),
            Clock.offset(clock, Duration.ofMinutes(16)), Sleeper.NONE);
        assertTrue(assertThrows(AuthException.class, () -> later.pollForToken(code, new CancellationToken())).getMessage().contains("expired"));
    }

    @Test
    void cancellationStopsPolling() throws Exception {
        happyChain();
        transport.onJson("/oauth/token", 400, Fixtures.read("auth/token-pending.json"));
        final DeviceCode code = service.beginDeviceCode();
        final CancellationToken token = new CancellationToken();
        token.cancel();
        assertThrows(java.util.concurrent.CancellationException.class, () -> service.pollForToken(code, token));
        assertEquals(0, transport.hits("/oauth/token"));
    }

    @Test
    void xstsErrorsAreMappedToActionableMessages() throws Exception {
        happyChain();
        transport.onJson("/oauth/token", 200, Fixtures.read("auth/token-success.json"));
        final DeviceCode code = service.beginDeviceCode();

        transport.onJson("/xsts/authorize", 401, Fixtures.read("auth/xsts-error-no-xbox.json"));
        XboxAuthException e = assertThrows(XboxAuthException.class, () -> service.login(code, new CancellationToken()));
        assertEquals(XboxAuthException.XERR_NO_XBOX_ACCOUNT, e.xerr());
        assertTrue(e.getMessage().contains("no Xbox profile"));

        transport.onJson("/xsts/authorize", 401, Fixtures.read("auth/xsts-error-child.json"));
        e = assertThrows(XboxAuthException.class, () -> service.login(code, new CancellationToken()));
        assertEquals(XboxAuthException.XERR_CHILD_ACCOUNT, e.xerr());
        assertTrue(e.getMessage().contains("child account"));

        transport.onJson("/xsts/authorize", 401, Fixtures.read("auth/xsts-error-region.json"));
        e = assertThrows(XboxAuthException.class, () -> service.login(code, new CancellationToken()));
        assertEquals(XboxAuthException.XERR_REGION_UNAVAILABLE, e.xerr());
        assertTrue(e.getMessage().contains("region"));

        transport.on("/xsts/authorize", r -> HttpResult.json(401, "{\"XErr\":1234}"));
        e = assertThrows(XboxAuthException.class, () -> service.login(code, new CancellationToken()));
        assertEquals(1234, e.xerr());
        assertTrue(e.getMessage().contains("XErr 1234"));
    }

    @Test
    void noGameOwnership() throws Exception {
        happyChain();
        transport.onJson("/oauth/token", 200, Fixtures.read("auth/token-success.json"));
        transport.onJson("/mc/entitlements", 200, Fixtures.read("auth/entitlements-empty.json"));
        transport.onJson("/mc/profile", 404, Fixtures.read("auth/profile-not-found.json"));
        final DeviceCode code = service.beginDeviceCode();
        final NoGameOwnershipException e = assertThrows(NoGameOwnershipException.class, () -> service.login(code, new CancellationToken()));
        assertTrue(e.getMessage().contains("does not own Minecraft: Java Edition"));
    }

    @Test
    void gamePassAccountWithoutEntitlementButWithProfileIsAccepted() throws Exception {
        happyChain();
        transport.onJson("/oauth/token", 200, Fixtures.read("auth/token-success.json"));
        transport.onJson("/mc/entitlements", 200, Fixtures.read("auth/entitlements-empty.json"));
        final Account account = service.login(service.beginDeviceCode(), new CancellationToken());
        assertEquals("VantaTester", account.name());
    }

    @Test
    void xboxFailureAndMalformedResponses() throws Exception {
        happyChain();
        transport.onJson("/oauth/token", 200, Fixtures.read("auth/token-success.json"));
        transport.on("/xbox/authenticate", r -> HttpResult.json(400, "{\"error\":\"bad\"}"));
        final DeviceCode code = service.beginDeviceCode();
        assertTrue(assertThrows(AuthException.class, () -> service.login(code, new CancellationToken())).getMessage().contains("Xbox Live authentication failed"));
        transport.on("/xbox/authenticate", r -> HttpResult.json(200, "not json"));
        assertTrue(assertThrows(AuthException.class, () -> service.login(code, new CancellationToken())).getMessage().contains("unreadable"));
        transport.on("/xbox/authenticate", r -> HttpResult.json(200, "{\"Token\":\"x\"}"));
        assertTrue(assertThrows(AuthException.class, () -> service.login(code, new CancellationToken())).getMessage().contains("user hash"));
    }

    @Test
    void refreshUsesRefreshTokenAndKeepsOldOneWhenNotRotated() throws Exception {
        happyChain();
        transport.onJson("/oauth/token", 200, Fixtures.read("auth/token-refreshed.json"));
        final Account old = new Account("3f2a1b4c-5d6e-7f80-91a2-b3c4d5e6f708", "VantaTester", "2535412345678901", "expired", 1L,
            "old-refresh-token", AccountType.MICROSOFT);
        final Account refreshed = service.refresh(old);
        assertTrue(refreshed.refreshToken().contains("Rotated"));
        assertTrue(transport.bodies().get(0).contains("grant_type=refresh_token"));
        assertTrue(transport.bodies().get(0).contains("refresh_token=old-refresh-token"));

        transport.onJson("/oauth/token", 200, "{\"access_token\":\"EwNEW\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
        final Account kept = service.refresh(old);
        assertEquals("old-refresh-token", kept.refreshToken(), "missing refresh token in response keeps the previous one");

        transport.onJson("/oauth/token", 400, Fixtures.read("auth/token-invalid-grant.json"));
        assertTrue(assertThrows(AuthException.class, () -> service.refresh(old)).getMessage().contains("Sign in again"));
        assertThrows(AuthException.class, () -> service.refresh(old.withType(AccountType.OFFLINE)));
    }

    @Test
    void transportFailuresPropagateAsIoException() {
        transport.failWith(new java.net.ConnectException("offline"));
        assertThrows(java.io.IOException.class, service::beginDeviceCode);
    }
}
