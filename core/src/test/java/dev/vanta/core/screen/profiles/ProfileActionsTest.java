package dev.vanta.core.screen.profiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.profiles.ProfileImportException;
import dev.vanta.core.profiles.ProfileManager;
import dev.vanta.core.screen.cosmetics.ServicesFixture;
import dev.vanta.core.settings.VantaSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileActionsTest {
    @TempDir
    Path dir;
    private ServicesFixture fx;
    private ProfileActions actions;

    @BeforeEach
    void setUp() {
        fx = new ServicesFixture(dir);
        actions = new ProfileActions(fx.services);
    }

    @Test
    void exportWritesTheFileAndReturnsItsJson() throws Exception {
        Optional<ProfileActions.Export> export = actions.export("pvp");
        assertTrue(export.isPresent());
        Path file = export.get().file();
        assertEquals(fx.services.paths().root().resolve("exports").resolve("vanta-profile-pvp.json"), file);
        assertTrue(Files.isRegularFile(file));
        String onDisk = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals(JsonParser.parseString(onDisk), JsonParser.parseString(export.get().json()));
        assertEquals("pvp", JsonParser.parseString(export.get().json()).getAsJsonObject().get("id").getAsString());
        assertEquals("config/vanta/exports/vanta-profile-pvp.json", actions.displayPath(file));
        assertTrue(actions.export("nope").isEmpty());
    }

    @Test
    void importFromTextGoesThroughTheProfileManagerRules() throws Exception {
        String json = actions.export("building").orElseThrow().json();
        Profile imported = actions.importFromText(json);
        assertEquals("Building (2)", imported.name());
        assertEquals(6, fx.services.profiles().size());
        assertTrue(Files.exists(fx.services.paths().profilesDir().resolve(imported.id() + ".json")));

        ProfileImportException empty = assertThrows(ProfileImportException.class, () -> actions.importFromText("  "));
        assertEquals(ProfileImportException.Reason.NOT_JSON, empty.reason());
        ProfileImportException notJson = assertThrows(ProfileImportException.class,
                () -> actions.importFromText("{oops"));
        assertEquals(ProfileImportException.Reason.NOT_JSON, notJson.reason());
        ProfileImportException schema = assertThrows(ProfileImportException.class,
                () -> actions.importFromText("{\"schemaVersion\": 99, \"name\": \"x\"}"));
        assertEquals(ProfileImportException.Reason.UNSUPPORTED_SCHEMA, schema.reason());
        ProfileImportException invalid = assertThrows(ProfileImportException.class,
                () -> actions.importFromText("{\"schemaVersion\": 1}"));
        assertEquals(ProfileImportException.Reason.INVALID_FIELD, invalid.reason());
        String huge = "{\"name\":\"" + "x".repeat((int) ProfileManager.MAX_IMPORT_BYTES) + "\"}";
        ProfileImportException tooLarge = assertThrows(ProfileImportException.class, () -> actions.importFromText(huge));
        assertEquals(ProfileImportException.Reason.TOO_LARGE, tooLarge.reason());
        assertEquals(6, fx.services.profiles().size(), "failed imports add nothing");
    }

    @Test
    void importsFolderListsJsonFilesAndImportsThem() throws Exception {
        assertTrue(actions.listImportFiles().isEmpty());
        Path folder = actions.ensureImportsDir();
        assertTrue(Files.isDirectory(folder));
        assertEquals(fx.services.paths().root().resolve("imports"), folder);
        fx.services.profiles().exportTo("recording", folder.resolve("B-stream.json"));
        fx.services.profiles().exportTo("pvp", folder.resolve("a-pvp.JSON"));
        Files.writeString(folder.resolve("notes.txt"), "ignored");
        List<Path> files = actions.listImportFiles();
        assertEquals(List.of("a-pvp.JSON", "B-stream.json"), files.stream().map(p -> p.getFileName().toString()).toList());
        Profile imported = actions.importFile(files.get(1));
        assertEquals("Recording (2)", imported.name());
        assertThrows(ProfileImportException.class, () -> actions.importFile(folder.resolve("missing.json")));
    }

    @Test
    void resetBuiltInRestoresShippedContentButKeepsNameAndAppliesWhenActive() {
        ProfileManager profiles = fx.services.profiles();
        profiles.rename("pvp", "My PvP");
        fx.services.crosshair().applyPreset(CrosshairPresets.find(CrosshairPresets.CIRCLE).orElseThrow());
        fx.services.settings().set(VantaSettings.HUD_GLOBAL_SCALE, 1.5);
        profiles.activate("pvp");
        profiles.updateActiveFromCurrent();
        Profile drifted = profiles.find("pvp").orElseThrow();
        assertEquals(CrosshairPresets.find(CrosshairPresets.CIRCLE).orElseThrow().style(), drifted.crosshair());

        assertTrue(actions.resetBuiltIn("pvp"));
        Profile reset = profiles.find("pvp").orElseThrow();
        assertEquals("My PvP", reset.name());
        assertEquals(CrosshairPresets.find(CrosshairPresets.BOLD).orElseThrow().style(), reset.crosshair());
        assertNotEquals(drifted.settings(), reset.settings());
        // Active profile: the restored content was applied to the live stores.
        assertEquals(reset.crosshair(), fx.services.crosshair().style());
        assertEquals(1.0, fx.services.settings().get(VantaSettings.HUD_GLOBAL_SCALE));
        assertEquals("pvp", profiles.activeId().orElseThrow());

        assertFalse(actions.resetBuiltIn("not-a-profile"));
        Profile custom = profiles.createFromCurrent("Custom", "profile");
        assertFalse(actions.resetBuiltIn(custom.id()));
        assertFalse(ProfileActions.isBuiltIn(custom.id()));
        assertTrue(ProfileActions.isBuiltIn("default"));
    }

    @Test
    void validatesNamesAndSuggestsCopyNames() {
        assertEquals(Optional.of("vanta.profiles.empty_name"), actions.validateName("   ", null));
        assertEquals(Optional.of("vanta.profiles.name_too_long"), actions.validateName("x".repeat(65), null));
        assertEquals(Optional.of("vanta.profiles.name_taken"), actions.validateName("pvp", null));
        assertEquals(Optional.empty(), actions.validateName("PvP", "pvp"), "own name allowed while renaming");
        assertEquals(Optional.empty(), actions.validateName("Fresh", null));
        assertEquals("PvP (copy)", actions.copyName("PvP"));
        fx.services.profiles().duplicate("pvp", "PvP (copy)");
        assertEquals("PvP (copy 2)", actions.copyName("PvP"));
        assertTrue(actions.copyName("y".repeat(70)).length() <= Profile.MAX_NAME);
    }
}
