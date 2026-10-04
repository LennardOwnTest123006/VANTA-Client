package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.VantaVersion;
import dev.vanta.core.ui.layout.Panel;
import org.junit.jupiter.api.Test;

import java.util.List;

class VantaShellTest {

    private final UiTestSupport t = new UiTestSupport();

    @Test
    void regionsFollowTheChromeConstants() {
        VantaShell shell = new VantaShell("Settings", null);
        Panel content = new Panel();
        shell.content(content);
        UiScreen screen = t.screen(shell, 640, 360);
        assertEquals(new Rect(0, 0, 640, VantaShell.TOP_BAR_H), shell.topBarRect());
        assertEquals(new Rect(0, 360 - VantaShell.FOOTER_H, 640, VantaShell.FOOTER_H), shell.footerRect());
        assertTrue(shell.railRect().isEmpty());
        Rect expectedContent = new Rect(0, VantaShell.TOP_BAR_H, 640, 360 - VantaShell.TOP_BAR_H - VantaShell.FOOTER_H)
                .inset(VantaShell.CONTENT_PAD);
        assertEquals(expectedContent, shell.contentRect());
        assertEquals(expectedContent, content.bounds());
        assertFalse(shell.hasRail());
        assertEquals(screen.root(), shell);
    }

    @Test
    void railTakesSpaceAndCollapsesOnNarrowScreens() {
        VantaShell shell = new VantaShell("Settings", null);
        shell.content(new Panel());
        String[] selected = {null};
        shell.rail(List.of(new VantaShell.RailItem("general", Icons.GEAR, "General"),
                new VantaShell.RailItem("video", Icons.EYE, "Video")), "general", id -> selected[0] = id);
        UiScreen screen = t.screen(shell, 640, 360);
        assertTrue(shell.hasRail());
        assertFalse(shell.isCompactRail());
        assertEquals(VantaShell.RAIL_W, shell.railWidth());
        assertEquals(VantaShell.RAIL_W + VantaShell.CONTENT_PAD, shell.contentRect().x());
        UiNode video = shell.findById("rail.video");
        UiTestSupport.click(screen, video.bounds().centerX(), video.bounds().centerY());
        assertEquals("video", selected[0]);
        assertEquals("video", shell.selectedRail());
        assertEquals(1, t.host.clicks());
        shell.selectRail(screen.context(), "video");
        assertEquals(1, t.host.clicks(), "re-selecting is a no-op");

        screen.resize(400, 240);
        assertTrue(shell.isCompactRail());
        assertEquals(VantaShell.RAIL_COMPACT_W, shell.railWidth());
        assertEquals("Video", video.tooltipText(), "compact rail exposes labels as tooltips");
        shell.rail(List.of(), null, null);
        screen.resize(400, 240);
        assertFalse(shell.hasRail());
    }

    @Test
    void footerShowsVersionLabelAndBackButtonWorks() {
        boolean[] back = {false};
        VantaShell shell = new VantaShell("About", () -> back[0] = true);
        shell.content(new Panel());
        UiScreen screen = t.screen(shell, 640, 360);
        t.clock.advance(1000L);
        TestCanvas canvas = t.frame(screen, 0, 0);
        assertTrue(canvas.hasText("About"));
        assertTrue(shell.footerText().contains(VantaVersion.MINECRAFT));
        assertTrue(canvas.texts().stream().anyMatch(tx -> tx.text().contains("Minecraft " + VantaVersion.MINECRAFT)));
        Rect b = shell.backButton().bounds();
        UiTestSupport.click(screen, b.centerX(), b.centerY());
        assertTrue(back[0]);
        shell.footer(false);
        screen.resize(640, 360);
        assertTrue(shell.footerRect().isEmpty());
        assertEquals(360 - VantaShell.TOP_BAR_H - VantaShell.CONTENT_PAD * 2, shell.contentRect().h());
    }

    @Test
    void rightSlotIsPlacedInTheTopBar() {
        VantaShell shell = new VantaShell("Search", null);
        shell.content(new Panel());
        Panel slot = new Panel();
        slot.size(100, 20);
        shell.rightSlot(slot);
        t.screen(shell, 640, 360);
        assertEquals(640 - Theme.SPACE_5 - 100, slot.bounds().x());
        assertEquals((VantaShell.TOP_BAR_H - 20) / 2, slot.bounds().y());
        shell.rightSlot(null);
        assertFalse(shell.children().contains(slot));
    }
}
