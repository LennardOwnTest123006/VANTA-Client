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
    void bundledLinksAndNotConfiguredWebsite() {
        final LauncherLinks links = LauncherLinks.load(Map.of());
        assertEquals("https://github.com/LennardOwnTest123006/VANTA-Client", links.source().toString());
        assertTrue(links.clientIdDocs().toString().endsWith("#microsoft-client-id"));
        assertEquals(Optional.of(URI.create("https://github.com/LennardOwnTest123006/VANTA-Client/issues")), links.support());
        assertTrue(links.website().isEmpty(), "no public website URL is configured yet");
        assertEquals("https://www.microsoft.com/link", links.microsoftLink().toString());
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
