package dev.vanta.core.screen.about;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.VantaVersion;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaLinks;
import dev.vanta.core.screen.common.LinkRow;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.TextureRef;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AboutScreenTest {
    @TempDir
    Path dir;
    private ScreenTestSupport t;

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
    }

    @Test
    void showsVersionsLicensesAndTheFairPlayStatement() {
        AboutScreen screen = t.show(ScreenId.ABOUT, 854, 480);
        TestCanvas canvas = t.frame(screen, -1000, -1000);
        assertTrue(canvas.hasText("1.21.11"));
        assertTrue(canvas.hasText("0.19.5"));
        assertTrue(canvas.hasText(VantaVersion.FABRIC_API));
        assertTrue(canvas.hasTextContaining("MIT License"));
        assertTrue(canvas.hasTextContaining("SIL Open Font License"));
        assertTrue(canvas.images().stream().anyMatch(i -> i.texture().equals(TextureRef.VANTA_LOGO)));
        assertTrue(screen.versionText().contains("Minecraft 1.21.11"));
        assertTrue(screen.versionText().contains("Fabric API " + VantaVersion.FABRIC_API));
    }

    @Test
    void linksOpenThroughTheGameBridgeAndTheWebsiteOnlyWhenConfigured() {
        AboutScreen screen = t.show(ScreenId.ABOUT, 854, 480);
        assertFalse(screen.links().stream().anyMatch(l -> "about.link.website".equals(l.id())),
                "no made-up website address");
        LinkRow repo = screen.links().stream().filter(l -> "about.link.repository".equals(l.id())).findFirst()
                .orElseThrow();
        repo.activate(screen.context());
        assertEquals("openUrl:" + VantaLinks.REPOSITORY, t.game.actions.get(t.game.actions.size() - 1));
        LinkRow issues = screen.links().stream().filter(l -> "about.link.issues".equals(l.id())).findFirst()
                .orElseThrow();
        ScreenTestSupport.click(screen, issues);
        assertEquals("openUrl:" + VantaLinks.ISSUES, t.game.actions.get(t.game.actions.size() - 1));

        t.services.setWebsiteUrl("https://example.invalid");
        AboutScreen configured = t.show(ScreenId.ABOUT, 854, 480);
        LinkRow website = configured.links().stream().filter(l -> "about.link.website".equals(l.id())).findFirst()
                .orElseThrow();
        website.activate(configured.context());
        assertEquals("openUrl:https://example.invalid", t.game.actions.get(t.game.actions.size() - 1));
    }

    @Test
    void copyVersionInfoFillsTheClipboardAndConfirms() {
        AboutScreen screen = t.show(ScreenId.ABOUT, 854, 480);
        ScreenTestSupport.click(screen, screen.copyButton());
        assertTrue(t.host.getClipboard().contains("VANTA Client 1.0.0"));
        assertTrue(t.host.getClipboard().contains("Minecraft 1.21.11"));
        assertTrue(t.services.notifications().visible().stream().anyMatch(n -> n.title().equals("Copied to clipboard")));
    }
}
