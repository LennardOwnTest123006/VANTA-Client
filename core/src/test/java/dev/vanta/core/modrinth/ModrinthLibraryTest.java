package dev.vanta.core.modrinth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.config.JsonStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModrinthLibraryTest {
    @TempDir
    Path gameDir;

    private ModrinthLibrary library;

    @BeforeEach
    void setUp() throws Exception {
        library = new ModrinthLibrary(gameDir, new JsonStore(Clock.systemUTC()));
        Files.createDirectories(gameDir.resolve("mods"));
        Files.createDirectories(gameDir.resolve("shaderpacks"));
        Files.createDirectories(gameDir.resolve("resourcepacks"));
    }

    private void track(String projectId, String title, String type, String file, boolean enabled, String... requiredBy)
            throws Exception {
        Path path = gameDir.resolve(enabled ? file : file + ".disabled");
        Files.writeString(path, title);
        library.update(index -> {
            index.put(new InstalledEntry(projectId, title.toLowerCase(), title, "V" + projectId, "1.0", type, file,
                    "", enabled, List.of(requiredBy), "2026-10-05T12:00:00Z", null));
            return null;
        });
    }

    @Test
    void indexLivesInConfigVanta() {
        assertEquals(gameDir.resolve("config/vanta/modrinth.json").toAbsolutePath().normalize(), library.indexFile());
    }

    @Test
    void disableAndEnableRenameTheJar() throws Exception {
        track("AANobbMI", "Sodium", "mod", "mods/sodium.jar", true);
        InstalledEntry off = library.setEnabled("AANobbMI", false);
        assertFalse(off.enabled());
        assertFalse(Files.exists(gameDir.resolve("mods/sodium.jar")));
        assertTrue(Files.exists(gameDir.resolve("mods/sodium.jar.disabled")));
        InstalledEntry stored = library.index().find("AANobbMI").orElseThrow();
        assertFalse(stored.enabled());
        assertEquals("mods/sodium.jar", stored.file(), "the index keeps the enabled file name");
        LocalItem item = library.items().get(0);
        assertFalse(item.enabled());
        assertEquals("mods/sodium.jar.disabled", item.relativeFile());

        library.setEnabled("AANobbMI", true);
        assertTrue(Files.exists(gameDir.resolve("mods/sodium.jar")));
        assertFalse(Files.exists(gameDir.resolve("mods/sodium.jar.disabled")));
        assertTrue(library.index().find("AANobbMI").orElseThrow().enabled());
    }

    @Test
    void onlyModsCanBeDisabled() throws Exception {
        track("HVnmMxH1", "Complementary", "shader", "shaderpacks/complementary.zip", true);
        assertEquals(ModrinthException.Kind.UNSAFE_FILE,
                assertThrows(ModrinthException.class, () -> library.setEnabled("HVnmMxH1", false)).kind());
    }

    @Test
    void removeDeletesOnlyTheTrackedFileAndCleansRequiredBy() throws Exception {
        track("AANobbMI", "Sodium", "mod", "mods/sodium.jar", true, "YL57xq9U");
        track("YL57xq9U", "Iris", "mod", "mods/iris.jar", true);
        Files.writeString(gameDir.resolve("mods/somebody-elses.jar"), "keep me");
        InstalledEntry removed = library.remove("YL57xq9U");
        assertEquals("Iris", removed.title());
        assertFalse(Files.exists(gameDir.resolve("mods/iris.jar")));
        assertTrue(Files.exists(gameDir.resolve("mods/sodium.jar")));
        assertTrue(Files.exists(gameDir.resolve("mods/somebody-elses.jar")));
        assertFalse(library.index().contains("YL57xq9U"));
        assertTrue(library.index().find("AANobbMI").orElseThrow().requiredBy().isEmpty());
        assertEquals(ModrinthException.Kind.NOT_FOUND,
                assertThrows(ModrinthException.class, () -> library.remove("YL57xq9U")).kind());
    }

    @Test
    void removeAlsoDeletesADisabledFile() throws Exception {
        track("AANobbMI", "Sodium", "mod", "mods/sodium.jar", false);
        library.remove("AANobbMI");
        assertFalse(Files.exists(gameDir.resolve("mods/sodium.jar.disabled")));
    }

    @Test
    void entriesPointingOutsideTheContentFoldersAreNeverDeleted() throws Exception {
        Path outside = gameDir.resolve("options.txt");
        Files.writeString(outside, "do not touch");
        library.update(index -> {
            index.put(new InstalledEntry("EVIL0001", "evil", "Evil", "", "", "mod", "mods/../options.txt", "", true,
                    List.of(), "", null));
            return null;
        });
        assertEquals(ModrinthException.Kind.UNSAFE_FILE,
                assertThrows(ModrinthException.class, () -> library.remove("EVIL0001")).kind());
        assertTrue(Files.exists(outside));
    }

    @Test
    void listsManagedItemsThenFilesInstalledByOtherMeansByName() throws Exception {
        track("AANobbMI", "Sodium", "mod", "mods/sodium.jar", true);
        track("GONE0001", "Gone", "mod", "mods/gone.jar", true);
        Files.delete(gameDir.resolve("mods/gone.jar"));
        Files.writeString(gameDir.resolve("mods/fabric-api-0.141.6+1.21.11.jar"), "fapi");
        Files.writeString(gameDir.resolve("mods/old.jar.disabled"), "old");
        Files.writeString(gameDir.resolve("mods/notes.txt"), "not content");
        Files.writeString(gameDir.resolve("mods/x.jar" + ModrinthLibrary.STAGING_SUFFIX), "staging");
        Files.writeString(gameDir.resolve("shaderpacks/BSL.zip"), "zip");
        Files.createDirectories(gameDir.resolve("resourcepacks/MyPack"));

        List<LocalItem> items = library.items();
        assertEquals(List.of("Gone", "Sodium", "fabric-api-0.141.6+1.21.11.jar", "old.jar.disabled", "BSL.zip",
                "MyPack"), items.stream().map(LocalItem::name).toList());
        assertEquals(LocalItem.State.MISSING, items.get(0).state());
        assertEquals(LocalItem.State.INSTALLED, items.get(1).state());
        LocalItem manual = items.get(2);
        assertEquals(LocalItem.State.MANUAL, manual.state());
        assertFalse(manual.managed());
        assertFalse(items.get(3).enabled());
        assertEquals(ModrinthProjectType.SHADER, items.get(4).type());
        assertEquals(ModrinthProjectType.RESOURCE_PACK, items.get(5).type());
        assertTrue(library.hasFabricApiJar());
        assertEquals(2, library.unknownModJars().size());
    }

    @Test
    void forgetDropsAnEntryWithoutTouchingFiles() throws Exception {
        track("GONE0001", "Gone", "mod", "mods/gone.jar", true);
        library.forget("GONE0001");
        assertFalse(library.index().contains("GONE0001"));
        assertTrue(Files.exists(gameDir.resolve("mods/gone.jar")));
    }
}
