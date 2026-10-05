package dev.vanta.launcher.core;

import dev.vanta.launcher.core.modrinth.ModrinthApi;
import dev.vanta.launcher.core.net.HttpRequestSpec;
import dev.vanta.launcher.testutil.FakeTransport;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Downloads from the Modrinth CDN carry the same descriptive User-Agent as the API calls.
 */
class UserAgentTransportTest {

    @Test
    void addsTheUserAgentUnlessTheRequestHasOne() throws Exception {
        final FakeTransport fake = new FakeTransport();
        final String agent = ModrinthApi.userAgent("1.1.0");
        try (LauncherServices.UserAgentTransport transport = new LauncherServices.UserAgentTransport(fake, agent)) {
            transport.execute(HttpRequestSpec.get(URI.create("https://cdn.modrinth.test/data/x.jar"))).close();
            transport.execute(HttpRequestSpec.get(URI.create("https://cdn.modrinth.test/data/y.jar")).withHeader("User-Agent", "other")).close();
        }
        assertEquals(agent, fake.requests().get(0).headers().get("User-Agent"));
        assertEquals("other", fake.requests().get(1).headers().get("User-Agent"));
    }
}
