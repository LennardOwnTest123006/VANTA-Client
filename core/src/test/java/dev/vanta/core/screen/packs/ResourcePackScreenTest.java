package dev.vanta.core.screen.packs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.PackInfo;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.TestCanvas;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourcePackScreenTest {
    @TempDir
    Path dir;
    private ScreenTestSupport t;

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
    }

    private static List<String> ids(List<PackInfo> packs) {
        return packs.stream().map(PackInfo::id).toList();
    }

    @Test
    void modelSplitsAndSortsPacks() {
        PackListModel model = PackListModel.from(t.packs.packs(), "");
        assertEquals(List.of("file/Faithful.zip", "high_contrast", "programmer_art"), ids(model.available()));
        assertEquals(List.of("vanilla"), ids(model.selected()));
        assertEquals(4, model.total());
        assertFalse(model.canMoveUp(model.selected().get(0)));
        assertFalse(model.canMoveDown(model.selected().get(0)));

        PackListModel filtered = PackListModel.from(t.packs.packs(), "faith");
        assertEquals(List.of("file/Faithful.zip"), ids(filtered.available()));
        assertTrue(filtered.selected().isEmpty());
        assertTrue(PackListModel.from(t.packs.packs(), "zzz").isFilteredEmpty());
        assertTrue(PackListModel.matches(t.packs.packs().get(0), "DEFAULT"), "case-insensitive title match");
    }

    @Test
    void enableDisableAndApplyGoThroughTheBridge() {
        ResourcePackScreen screen = t.show(ScreenId.RESOURCE_PACKS, 854, 480);
        assertEquals(3, screen.availableRows().size());
        assertEquals(1, screen.selectedRows().size());
        assertFalse(screen.applyButton().isEnabled());

        PackRow art = screen.rowFor("programmer_art").orElseThrow();
        ScreenTestSupport.click(screen, art.buttons().get(0));
        assertTrue(t.packs.hasPendingChanges());
        assertEquals(List.of("programmer_art", "vanilla"), ids(screen.model().selected()), "new packs go on top");
        assertTrue(screen.applyButton().isEnabled());
        assertTrue(screen.discardButton().isEnabled());
        TestCanvas canvas = t.frame(screen, -1000, -1000);
        assertTrue(canvas.hasText("Unapplied changes"));

        PackRow enabled = screen.rowFor("programmer_art").orElseThrow();
        ScreenTestSupport.click(screen, enabled.buttons().get(enabled.buttons().size() - 1));
        assertFalse(t.packs.hasPendingChanges(), "disabling again restores the applied state");

        screen.toggle(t.packs.packs().stream().filter(p -> p.id().equals("high_contrast")).findFirst().orElseThrow());
        ScreenTestSupport.click(screen, screen.applyButton());
        assertTrue(t.packs.actions.contains("apply"));
        assertFalse(t.packs.hasPendingChanges());
        assertFalse(screen.applyButton().isEnabled());
        assertTrue(t.services.notifications().visible().stream().anyMatch(n -> n.title().equals("Resource packs applied")));
    }

    @Test
    void requiredPacksCannotBeDisabled() {
        ResourcePackScreen screen = t.show(ScreenId.RESOURCE_PACKS, 854, 480);
        PackRow vanilla = screen.rowFor("vanilla").orElseThrow();
        assertEquals(2, vanilla.buttons().size(), "move buttons only");
        screen.toggle(vanilla.pack());
        assertFalse(t.packs.hasPendingChanges());
    }

    @Test
    void reorderWithButtonsKeysAndDrag() {
        t.packs.setEnabled("programmer_art", true);
        t.packs.setEnabled("high_contrast", true);
        t.packs.apply();
        ResourcePackScreen screen = t.show(ScreenId.RESOURCE_PACKS, 854, 480);
        assertEquals(List.of("high_contrast", "programmer_art", "vanilla"), ids(screen.model().selected()));

        PackRow art = screen.rowFor("programmer_art").orElseThrow();
        ScreenTestSupport.click(screen, art.buttons().get(0));
        assertEquals(List.of("programmer_art", "high_contrast", "vanilla"), ids(screen.model().selected()));
        assertEquals("pack.programmer_art", screen.context().focus().focused().id(), "focus follows the moved pack");

        ScreenTestSupport.key(screen, Keys.DOWN, Keys.MOD_SHIFT);
        assertEquals(List.of("high_contrast", "programmer_art", "vanilla"), ids(screen.model().selected()));

        PackRow top = screen.rowFor("high_contrast").orElseThrow();
        assertFalse(top.buttons().get(0).isEnabled(), "top pack cannot move up");

        PackRow drag = screen.rowFor("programmer_art").orElseThrow();
        double x = drag.bounds().centerX();
        double y = drag.bounds().centerY();
        screen.mouseDown(x, y, Keys.MOUSE_LEFT);
        assertTrue(drag.isDragging());
        screen.mouseDrag(x, y - PackRow.HEIGHT, Keys.MOUSE_LEFT, 0, -PackRow.HEIGHT);
        screen.mouseUp(x, y - PackRow.HEIGHT, Keys.MOUSE_LEFT);
        assertEquals(List.of("programmer_art", "high_contrast", "vanilla"), ids(screen.model().selected()));
        assertTrue(t.packs.hasPendingChanges());
    }

    @Test
    void leavingWithPendingChangesAsksFirst() {
        t.services.settings().set(VantaSettings.ACCESSIBILITY_REDUCED_MOTION, true);
        ResourcePackScreen screen = t.show(ScreenId.RESOURCE_PACKS, 854, 480);
        assertTrue(screen.shouldCloseOnEsc());
        screen.toggle(t.packs.packs().get(1));
        assertFalse(screen.shouldCloseOnEsc());
        assertTrue(ScreenTestSupport.key(screen, Keys.ESCAPE));
        assertNotNull(ScreenTestSupport.openDialog(screen));
        assertFalse(screen.isClosing());
        ScreenTestSupport.cancelDialog(screen);
        assertTrue(t.packs.hasPendingChanges(), "cancel keeps the staged changes");
        ScreenTestSupport.key(screen, Keys.ESCAPE);
        ScreenTestSupport.confirmDialog(screen);
        assertFalse(t.packs.hasPendingChanges(), "discarded");
        assertTrue(screen.isClosing());
    }

    @Test
    void searchFiltersBothColumnsAndFooterShortcutsWork() {
        ResourcePackScreen screen = t.show(ScreenId.RESOURCE_PACKS, 854, 480);
        screen.searchField().setText("contrast");
        assertEquals(1, screen.availableRows().size());
        assertTrue(screen.selectedRows().isEmpty());
        assertTrue(t.frame(screen, -1000, -1000).hasTextContaining("No packs match"));
        screen.searchField().setText("");
        assertEquals(3, screen.availableRows().size());
        ScreenTestSupport.click(screen, screen.root().findById("packs.openFolder"));
        assertTrue(t.packs.actions.contains("openPackFolder"));
        ScreenTestSupport.click(screen, screen.root().findById("packs.vanilla"));
        assertTrue(t.packs.actions.contains("openVanillaScreen"));
    }

    @Test
    void incompatiblePacksAreMarkedAndIconsFallBackToMonograms() {
        ResourcePackScreen screen = t.show(ScreenId.RESOURCE_PACKS, 854, 480);
        TestCanvas canvas = t.frame(screen, -1000, -1000);
        assertTrue(canvas.hasText("Incompatible"));
        assertTrue(canvas.hasText("Required"));
        assertTrue(canvas.hasText("H"), "monogram for a pack without an icon");
        assertEquals("vanta:pack_icons/file/faithful.zip", PackIcons.texture(t.packs.packs().get(3)).toString());
        assertEquals("P", PackIcons.monogram("Programmer Art"));
        assertEquals("#", PackIcons.monogram("…"));
        assertEquals("my_pack_", PackIcons.sanitize("My Pack!"));
    }
}
