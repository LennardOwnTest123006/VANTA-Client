package dev.vanta.launcher.ui;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherLinksTest {

    @Test
    void bundledLinksIncludingTheWebsite() {
        final LauncherLinks links = LauncherLinks.load(Map.of());
        assertEquals("https://github.com/LennardOwnTest123006/VANTA-Client", links.source().toString());
        assertTrue(links.clientIdDocs().toString().endsWith("#microsoft-client-id"));
        assertEquals(Optional.of(URI.create("https://github.com/LennardOwnTest123006/VANTA-Client/issues")), links.support());
        assertEquals(Optional.of(URI.create("https://vanta-client.netlify.app")), links.website(),
            "the deployed website: the sidebar and About 'Website' entries are active");
        assertEquals(Optional.of(URI.create("https://github.com/LennardOwnTest123006/VANTA-Client/releases")), links.releases());
        assertEquals("https://www.microsoft.com/link", links.microsoftLink().toString());
    }

    @Test
    void emptyWebsiteMeansNotConfigured() {
        final Properties props = new Properties();
        props.setProperty("website.url", "");
        assertTrue(new LauncherLinks(props, Map.of()).website().isEmpty());
        props.setProperty("website.url", "ftp://vanta.example");
        assertTrue(new LauncherLinks(props, Map.of()).website().isEmpty(), "only http(s) links are opened");
    }

    @Test
    void environmentOverridesAndValidation() {
        final LauncherLinks links = LauncherLinks.load(Map.of(LauncherLinks.WEBSITE_ENV, "https://vanta.example", LauncherLinks.SUPPORT_ENV, "not a url"));
        assertEquals(Optional.of(URI.create("https://vanta.example")), links.website());
        assertTrue(links.support().isEmpty(), "an invalid override disables the link instead of producing a broken one");
    }

    @Test
    void changelogResolution() {
        final Properties props = new Properties();
        props.setProperty("changelog.raw.base", "https://raw.example/main");
        props.setProperty("changelog.browse.base", "https://github.example/blob/main/");
        final LauncherLinks links = new LauncherLinks(props, Map.of());
        assertEquals(Optional.of(URI.create("https://raw.example/main/website/content/changelog/client-1.0.0.md")),
            links.changelogRaw("website/content/changelog/client-1.0.0.md"));
        assertEquals(Optional.of(URI.create("https://github.example/blob/main/website/content/changelog/client-1.0.0.md")),
            links.changelogPage("/website/content/changelog/client-1.0.0.md"));
        assertEquals(Optional.of(URI.create("https://other.example/notes.md")), links.changelogRaw("https://other.example/notes.md"));
        assertTrue(links.changelogRaw("").isEmpty());
        assertTrue(links.changelogRaw("Inline notes\nwith lines").isEmpty());
        assertTrue(links.changelogRaw("notes.txt").isEmpty());
    }
}
