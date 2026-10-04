package dev.vanta.core.screen.profiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.Notification;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.cosmetics.ServicesFixture;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.widget.Button;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfilesScreenTest {
    @TempDir
    Path dir;
    private ServicesFixture fx;
    private ProfilesScreen screen;

    @BeforeEach
    void setUp() {
        fx = new ServicesFixture(dir);
        screen = fx.open(ScreenId.PROFILES);
    }

    private ProfileCard card(String id) {
        return screen.card(id).orElseThrow();
    }

    private Notification lastToast() {
        List<Notification> visible = fx.services.notifications().visible();
        return visible.get(visible.size() - 1);
    }

    @Test
    void showsEveryProfileWithTheActiveOneMarked() {
        assertEquals(5, screen.cards().size());
        assertTrue(card("default").isActiveProfile());
        assertFalse(card("default").activateButton().isVisible());
        assertTrue(card("pvp").activateButton().isVisible());
        assertTrue(card("pvp").resetButton().isVisible(), "built-ins are resettable");
        assertTrue(card("pvp").deleteButton().isEnabled());
        assertFalse(screen.unsavedBanner().isVisible());
        assertTrue(screen.cards().stream().allMatch(c -> c.bounds().w() > 200 && c.bounds().h() == ProfileCard.HEIGHT));
    }

    @Test
    void activateAppliesTheProfileAndShowsTheToast() {
        fx.clock.advance(2000);
        fx.click(screen, card("pvp").activateButton());
        assertEquals("pvp", fx.services.profiles().activeId().orElseThrow());
        assertTrue(card("pvp").isActiveProfile());
        assertFalse(card("default").isActiveProfile());
        assertEquals(Lang.tr("vanta.notification.profile_loaded.title"), lastToast().title());
        assertFalse(screen.unsavedBanner().isVisible());
    }

    @Test
    void unsavedBannerAppearsAndSavesIntoTheActiveProfile() {
        fx.services.settings().set(VantaSettings.HUD_GLOBAL_SCALE, 1.5);
        screen.refresh();
        fx.frame(screen);
        assertTrue(screen.unsavedBanner().isVisible());
        assertTrue(screen.unsavedBanner().body().contains(Lang.tr("vanta.profiles.area.settings")));
        Button save = (Button) screen.root().findById("profiles.save-active");
        fx.clock.advance(2000);
        fx.click(screen, save);
        assertFalse(screen.unsavedBanner().isVisible());
        Profile active = fx.services.profiles().active().orElseThrow();
        assertEquals(VantaSettings.HUD_GLOBAL_SCALE.encode(1.5), active.settings().get(VantaSettings.HUD_GLOBAL_SCALE.id()));
        assertEquals(Lang.tr("vanta.profiles.notification.saved.title"), lastToast().title());
    }

    @Test
    void inlineRenameValidatesAndCommitsOnEnterAndCancelsOnEscape() {
        ProfileCard building = card("building");
        fx.click(screen, building.renameButton());
        assertTrue(building.isRenaming());
        assertTrue(building.nameField().isVisible());
        assertTrue(building.nameField().isFocused());
        building.nameField().setText("PvP");
        assertFalse(building.nameField().isValid(), "duplicate names are rejected");
        building.nameField().setText("");
        fx.type(screen, "Mega builds");
        assertTrue(building.nameField().isValid());
        fx.key(screen, Keys.ENTER);
        assertEquals("Mega builds", fx.services.profiles().find("building").orElseThrow().name());
        assertFalse(card("building").isRenaming());

        ProfileCard pvp = card("pvp");
        fx.click(screen, pvp.renameButton());
        pvp.nameField().setText("Nope");
        fx.key(screen, Keys.ESCAPE);
        assertFalse(pvp.isRenaming());
        assertEquals("PvP", fx.services.profiles().find("pvp").orElseThrow().name());
        assertFalse(screen.isClosing(), "Escape while renaming must not close the screen");
    }

    @Test
    void duplicateOpensNamePromptAndCreatesTheCopy() {
        fx.click(screen, screen.root().findById("profile.pvp.duplicate"));
        var field = screen.root() == null ? null : screen.context().popups().top();
        assertNotNull(field);
        NameDialog dialog = (NameDialog) field.node();
        assertEquals("PvP (copy)", dialog.field().text());
        assertNull(dialog.error());
        dialog.field().setText("");
        assertNotNull(dialog.error());
        assertFalse(dialog.confirmButton().isEnabled());
        dialog.field().setText("Arena");
        dialog.confirmButton().click(screen.context());
        fx.frame(screen);
        assertEquals(6, fx.services.profiles().size());
        assertTrue(fx.services.profiles().list().stream().anyMatch(p -> p.name().equals("Arena")));
        assertEquals(6, screen.cards().size());
        assertFalse(screen.context().popups().isOpen());
    }

    @Test
    void deleteAsksForConfirmationAndRefusesTheLastProfile() {
        fx.click(screen, card("recording").deleteButton());
        assertNotNull(fx.topDialog(screen));
        fx.cancelDialog(screen);
        assertEquals(5, fx.services.profiles().size());
        fx.click(screen, card("recording").deleteButton());
        fx.clock.advance(2000);
        fx.confirmDialog(screen);
        assertEquals(4, fx.services.profiles().size());
        assertTrue(screen.card("recording").isEmpty());
        assertEquals(Lang.tr("vanta.profiles.notification.deleted.title"), lastToast().title());

        // Deleting the active profile hands activation to another one.
        fx.click(screen, card("default").deleteButton());
        assertTrue(fx.topDialog(screen).message().contains("PvP"));
        fx.confirmDialog(screen);
        assertEquals("pvp", fx.services.profiles().activeId().orElseThrow());

        for (String id : List.of("pvp", "building")) {
            fx.click(screen, card(id).deleteButton());
            fx.confirmDialog(screen);
        }
        assertEquals(1, fx.services.profiles().size());
        assertFalse(card("performance").deleteButton().isEnabled());
    }

    @Test
    void exportWritesFileCopiesJsonAndNotifies() throws Exception {
        fx.clock.advance(2000);
        fx.click(screen, screen.root().findById("profile.building.export"));
        Path file = fx.services.paths().root().resolve("exports").resolve("vanta-profile-building.json");
        assertTrue(Files.isRegularFile(file));
        assertTrue(fx.clipboard.get().contains("\"id\": \"building\""));
        assertEquals(Lang.tr("vanta.notification.profile_exported.title"), lastToast().title());
        assertTrue(lastToast().body().contains("config/vanta/exports/vanta-profile-building.json"));
    }

    @Test
    void importDialogImportsFromClipboardAndFolder() throws Exception {
        fx.clipboard.set(screen.actions().export("pvp").orElseThrow().json());
        ImportDialog dialog = screen.openImportDialog();
        fx.frame(screen);
        assertTrue(Files.isDirectory(screen.actions().importsDir()));
        dialog.clipboardButton().click(screen.context());
        assertFalse(dialog.isStatusError(), dialog.status());
        assertTrue(dialog.status().contains("PvP (2)"));
        assertEquals(6, fx.services.profiles().size());
        assertEquals(6, screen.cards().size());

        fx.clipboard.set("not json");
        dialog.importClipboard();
        assertTrue(dialog.isStatusError());
        assertEquals(Lang.tr("vanta.profiles.import_error.not_json"), dialog.status());
        fx.clipboard.set("");
        dialog.importClipboard();
        assertEquals(Lang.tr("vanta.profiles.import_error.empty_clipboard"), dialog.status());

        fx.services.profiles().exportTo("recording", screen.actions().importsDir().resolve("stream.json"));
        dialog.tabs().select(screen.context(), 1);
        dialog.refreshFiles();
        fx.frame(screen);
        assertEquals(1, dialog.fileList().items().size());
        dialog.fileList().select(screen.context(), 0);
        dialog.importFile(dialog.fileList().items().get(0));
        assertFalse(dialog.isStatusError());
        assertEquals(7, fx.services.profiles().size());
        dialog.close(screen.context());
        assertFalse(screen.context().popups().isOpen());
    }

    @Test
    void createFromCurrentAndResetBuiltIn() {
        NameDialog create = screen.openCreateDialog();
        fx.type(screen, "Weekend");
        fx.key(screen, Keys.ENTER);
        fx.frame(screen);
        assertEquals(6, fx.services.profiles().size());
        assertTrue(screen.cards().stream().anyMatch(c -> c.profile().name().equals("Weekend")));
        assertFalse(create.isShowing() && screen.context().popups().isOpen(create));

        fx.services.profiles().activate("pvp");
        fx.services.settings().set(VantaSettings.HUD_GLOBAL_SCALE, 1.5);
        fx.services.profiles().updateActiveFromCurrent();
        screen.refresh();
        fx.frame(screen);
        fx.click(screen, card("pvp").resetButton());
        fx.clock.advance(2000);
        fx.confirmDialog(screen);
        assertEquals(1.0, fx.services.settings().get(VantaSettings.HUD_GLOBAL_SCALE));
        assertEquals(Lang.tr("vanta.profiles.notification.reset.title"), lastToast().title());
    }

    @Test
    void compactLayoutUsesASingleColumn() {
        ProfilesScreen small = fx.open(ScreenId.PROFILES, 427, 240);
        assertEquals(5, small.cards().size());
        int x = small.cards().get(0).bounds().x();
        assertTrue(small.cards().stream().allMatch(c -> c.bounds().x() == x));
    }
}
