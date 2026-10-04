package dev.vanta.core.screen.keybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.keybinds.ConflictDetector;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.cosmetics.InfoBanner;
import dev.vanta.core.screen.cosmetics.ServicesFixture;
import dev.vanta.core.ui.Keys;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeybindsScreenTest {
    @TempDir
    Path dir;
    private ServicesFixture fx;
    private KeybindsScreen screen;

    @BeforeEach
    void setUp() {
        fx = new ServicesFixture(dir);
        screen = fx.open(ScreenId.KEYBINDS);
    }

    private KeybindRow row(String id) {
        return screen.row(id).orElseThrow();
    }

    @Test
    void listsVantaFirstThenVanillaCategoriesWithoutConflicts() {
        assertEquals(fx.keys.all().size(), screen.rows().size());
        assertEquals("key.vanta.open_menu", screen.rows().get(0).binding().id());
        assertEquals(InfoBanner.Tone.SUCCESS, screen.conflictBanner().tone());
        assertFalse(screen.conflictsOnlyToggle().isEnabled());
        assertFalse(screen.resetAllButton().isEnabled());
        assertFalse(row("key.forward").resetButton().isVisible());
        assertNotNull(screen.root().findById("keybinds.section.key.categories.movement"));
    }

    @Test
    void capturingAKeyWritesThroughTheBridgeAndRefreshesConflicts() {
        KeybindRow zoom = row("key.vanta.zoom");
        fx.click(screen, zoom.field());
        assertTrue(zoom.field().isCapturing());
        fx.key(screen, Keys.W);
        KeyRef bound = fx.keys.find("key.vanta.zoom").orElseThrow().boundKey();
        assertEquals(KeyRef.keyboard(Keys.W, "key.keyboard.w"), bound);
        // Rows were rebuilt from the refreshed model.
        KeybindRow again = row("key.vanta.zoom");
        assertFalse(again.field().isCapturing());
        assertEquals("W", again.field().keyName());
        assertTrue(again.resetButton().isVisible());
        assertTrue(again.conflict().isPresent());
        assertEquals(ConflictDetector.Severity.MEDIUM, again.conflict().get().severity());
        assertFalse(again.field().hasConflict(), "medium overlaps keep the field neutral");
        assertTrue(again.conflictText().contains("Walk Forwards"));
        assertEquals(InfoBanner.Tone.WARNING, screen.conflictBanner().tone());
        assertEquals(Lang.tr("vanta.keybinds.conflicts.one"), screen.conflictBanner().title());
        assertTrue(screen.conflictsOnlyToggle().isEnabled());
        assertTrue(screen.resetAllButton().isEnabled());
        assertTrue(fx.services.notifications().visible().stream()
                .anyMatch(n -> n.title().equals(Lang.tr("vanta.notification.keybind_conflict.title"))));
        assertTrue(again.field().isFocused(), "focus survives the rebuild");
    }

    @Test
    void highSeverityConflictsMarkTheFieldAndEscapeCancelsCapture() {
        KeybindRow editor = row("key.vanta.hud_editor");
        editor.field().startCapture(screen.context());
        fx.key(screen, Keys.RIGHT_SHIFT);
        KeybindRow again = row("key.vanta.hud_editor");
        assertEquals(ConflictDetector.Severity.HIGH, again.conflict().orElseThrow().severity());
        assertTrue(again.field().hasConflict());
        assertTrue(again.field().tooltipText().contains("Open VANTA Menu"));

        KeybindRow jump = row("key.jump");
        jump.field().startCapture(screen.context());
        fx.key(screen, Keys.ESCAPE);
        assertFalse(jump.field().isCapturing());
        assertFalse(screen.isClosing());
        assertEquals("Space", row("key.jump").field().keyName());
    }

    @Test
    void backspaceUnbindsAndResetRestoresTheDefault() {
        KeybindRow sprint = row("key.sprint");
        sprint.field().startCapture(screen.context());
        fx.key(screen, Keys.BACKSPACE);
        assertTrue(fx.keys.find("key.sprint").orElseThrow().isUnbound());
        assertEquals(Lang.tr("vanta.keybinds.unbound"), row("key.sprint").field().keyName());
        fx.click(screen, row("key.sprint").resetButton());
        assertTrue(fx.keys.find("key.sprint").orElseThrow().isDefault());
        assertFalse(row("key.sprint").resetButton().isVisible());
    }

    @Test
    void resetAllAsksForConfirmation() {
        fx.keys.setKey("key.sneak", KeyRef.keyboard(Keys.Z, "key.keyboard.z"));
        fx.keys.setKey("key.vanta.toggle_hud", KeyRef.keyboard(Keys.F6, "key.keyboard.f6"));
        screen.rebuildList();
        assertTrue(screen.resetAllButton().isEnabled());
        fx.click(screen, screen.resetAllButton());
        assertNotNull(fx.topDialog(screen));
        fx.confirmDialog(screen);
        assertTrue(fx.keys.all().stream().allMatch(b -> b.isDefault()));
        assertFalse(screen.resetAllButton().isEnabled());
    }

    @Test
    void searchAndConflictsOnlyFilterTheRows() {
        screen.setQuery("hud");
        assertTrue(screen.rows().size() < fx.keys.all().size());
        assertTrue(screen.rows().stream().allMatch(r -> r.binding().displayName().toLowerCase().contains("hud")));
        screen.setQuery("zzzzqqq");
        assertTrue(screen.rows().isEmpty());
        assertNotNull(screen.root().findById("keybinds.section.key.categories.vanta") == null ? "" : null);
        screen.setQuery("");
        assertEquals(fx.keys.all().size(), screen.rows().size());

        fx.keys.setKey("key.vanta.toggle_hud", KeyRef.keyboard(Keys.W, "key.keyboard.w"));
        screen.rebuildList();
        fx.frame(screen);
        screen.conflictsOnlyToggle().set(screen.context(), true);
        assertEquals(2, screen.rows().size());
        assertTrue(screen.filter().conflictsOnly());
        assertTrue(screen.revealKeybind("key.jump"));
        assertFalse(screen.filter().conflictsOnly());
        assertTrue(row("key.jump").field().isFocused());
        assertFalse(screen.revealKeybind("key.nope"));
    }

    @Test
    void vanillaControlsLinkOpensTheGameScreen() {
        fx.click(screen, screen.root().findById("keybinds.vanilla"));
        assertEquals("openVanillaScreen:CONTROLS", fx.game.actions.get(fx.game.actions.size() - 1));
        KeybindsScreen small = fx.open(ScreenId.KEYBINDS, 427, 240);
        assertEquals(fx.keys.all().size(), small.rows().size());
        assertTrue(small.rows().get(0).field().bounds().right() <= 427);
    }
}
