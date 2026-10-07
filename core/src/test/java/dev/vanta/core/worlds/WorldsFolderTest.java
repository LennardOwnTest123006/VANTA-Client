package dev.vanta.core.worlds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldsFolderTest {
    private static final String LINUX = "Linux";

    @TempDir
    Path tmp;
    private Path home;
    private Path gameDir;
    private Path official;

    @BeforeEach
    void setUp() throws IOException {
        home = tmp.resolve("home");
        // The VANTA Launcher's Linux layout: ~/.local/share/vanta-launcher/instances/<id>.
        gameDir = home.resolve(".local").resolve("share").resolve("vanta-launcher").resolve("instances")
                .resolve("vanta-1.21.11");
        Files.createDirectories(gameDir.resolve("saves"));
        official = home.resolve(".minecraft");
    }

    private WorldsFolder resolve(WorldsFolderMode mode, Optional<Path> hinted, String override) {
        return WorldsFolder.resolve(gameDir, mode, hinted, Map.of(), LINUX, home, override);
    }

    /** Every file and directory below {@code root}, to prove a resolve changes nothing on disk. */
    private static List<Path> tree(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.sorted().toList();
        }
    }

    @Test
    void theOfficialSavesAreUsedByDefaultWhenTheyExist() throws IOException {
        Files.createDirectories(official.resolve("saves").resolve("My Old World"));
        Files.writeString(official.resolve("saves").resolve("My Old World").resolve("level.dat"), "nbt");
        List<Path> before = tree(tmp);

        WorldsFolder folder = resolve(WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(), null);

        assertTrue(folder.redirected(), folder.reason());
        assertEquals(official.toAbsolutePath().normalize().resolve("saves"), folder.savesDir());
        assertEquals(official.toAbsolutePath().normalize().resolve("backups"), folder.backupsDir());
        assertEquals(Optional.of(official.toAbsolutePath().normalize()), folder.minecraftDir());
        assertTrue(folder.reason().contains("platform default"), folder.reason());
        assertEquals(before, tree(tmp), "resolving moves, copies, creates and deletes nothing");
        assertFalse(Files.exists(official.resolve("backups")), "no backups folder is created in .minecraft");
    }

    private static void world(Path saves, String name, String dataFile) throws IOException {
        Files.createDirectories(saves.resolve(name));
        Files.writeString(saves.resolve(name).resolve(dataFile), "nbt");
    }

    @Test
    void anEmptyMinecraftSavesFolderDoesNotHideTheWorldsAlreadyInTheVantaFolder() throws IOException {
        // Vanilla creates .minecraft/saves on its first start: a VANTA-only player has an empty one.
        Files.createDirectories(official.resolve("saves").resolve("not a world"));
        world(gameDir.resolve("saves"), "Made in VANTA", "level.dat");
        List<Path> before = tree(tmp);

        WorldsFolder folder = resolve(WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(), null);

        assertFalse(folder.redirected(), folder.reason());
        assertEquals(gameDir.resolve("saves"), folder.savesDir());
        assertTrue(folder.reason().contains("no worlds yet"), folder.reason());
        assertEquals(before, tree(tmp), "resolving moves, copies, creates and deletes nothing");
    }

    @Test
    void worldsInTheMinecraftFolderWinOverWorldsInTheVantaFolder() throws IOException {
        world(official.resolve("saves"), "Old World", "level.dat_old");
        world(gameDir.resolve("saves"), "Made in VANTA", "level.dat");
        WorldsFolder folder = resolve(WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(), null);
        assertTrue(folder.redirected(), folder.reason());
        assertEquals(official.toAbsolutePath().normalize().resolve("saves"), folder.savesDir());
    }

    @Test
    void bothFoldersEmptyUsesTheMinecraftFolderLikeANormalInstallation() throws IOException {
        Files.createDirectories(official.resolve("saves"));
        assertTrue(resolve(WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(), null).redirected());
        Files.delete(gameDir.resolve("saves"));
        assertTrue(resolve(WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(), null).redirected(),
                "a VANTA folder without saves/ (first start) also uses the Minecraft folder");
    }

    @Test
    void anotherLaunchersInstanceKeepsItsOwnWorldsUnlessTheLauncherNoteNamesAFolder() throws IOException {
        world(official.resolve("saves"), "Old World", "level.dat");
        Path prism = home.resolve(".local").resolve("share").resolve("PrismLauncher").resolve("instances")
                .resolve("VANTA").resolve(".minecraft");
        Files.createDirectories(prism);
        WorldsFolder folder = WorldsFolder.resolve(prism, WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(),
                Map.of(), LINUX, home, null);
        assertFalse(folder.redirected(), folder.reason());
        assertEquals(prism.resolve("saves"), folder.savesDir());
        assertTrue(folder.reason().contains("not a VANTA Launcher instance"), folder.reason());

        Path modrinthApp = home.resolve("ModrinthApp").resolve("profiles").resolve("VANTA");
        Files.createDirectories(modrinthApp);
        assertFalse(WorldsFolder.resolve(modrinthApp, WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(), Map.of(),
                LINUX, home, null).redirected());

        assertTrue(WorldsFolder.resolve(prism, WorldsFolderMode.MINECRAFT_FOLDER, Optional.of(official), Map.of(),
                LINUX, home, null).redirected(), "an explicit launcher note is followed wherever the game runs");
    }

    @Test
    void vantaLauncherInstancesAreRecognisedOnEveryPlatformAndWithAMovedDataDirectory() throws IOException {
        assertTrue(WorldsFolder.isVantaLauncherInstance(tmp.resolve("AppData").resolve("Roaming")
                .resolve("VANTA Launcher").resolve("instances").resolve("vanta-1.21.11")));
        assertTrue(WorldsFolder.isVantaLauncherInstance(home.resolve("Library").resolve("Application Support")
                .resolve("VANTA Launcher").resolve("instances").resolve("vanta-1.21.11")));
        assertTrue(WorldsFolder.isVantaLauncherInstance(gameDir));
        Path moved = tmp.resolve("Games").resolve("vanta-data");
        assertFalse(WorldsFolder.isVantaLauncherInstance(moved.resolve("instances").resolve("vanta-1.21.11")));
        Files.createDirectories(moved.resolve("versions").resolve("vanta-client"));
        assertTrue(WorldsFolder.isVantaLauncherInstance(moved.resolve("instances").resolve("vanta-1.21.11")),
                "a data directory moved with the launcher's home override holds versions/vanta-client");
        assertFalse(WorldsFolder.isVantaLauncherInstance(home.resolve(".minecraft")));
        assertFalse(WorldsFolder.isVantaLauncherInstance(tmp.getRoot()));
    }

    @Test
    void theVantaFolderSettingKeepsTheVanillaPaths() throws IOException {
        Files.createDirectories(official.resolve("saves"));
        WorldsFolder folder = resolve(WorldsFolderMode.VANTA_FOLDER, Optional.empty(), null);
        assertFalse(folder.redirected());
        assertEquals(gameDir.resolve("saves"), folder.savesDir());
        assertEquals(gameDir.resolve("backups"), folder.backupsDir());
        assertTrue(folder.reason().contains("VANTA folder"), folder.reason());
    }

    @Test
    void theSystemPropertyKeepsTheVantaFolderWhateverTheSetting() throws IOException {
        Files.createDirectories(official.resolve("saves"));
        assertEquals("vanta.worlds.folder", WorldsFolder.PROPERTY);
        assertFalse(resolve(WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(), "vanta").redirected());
        assertFalse(resolve(WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(), " VANTA ").redirected());
        assertTrue(resolve(WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(), "").redirected(),
                "any other value does not change the default");
    }

    @Test
    void aMissingMinecraftFolderOrSavesFolderKeepsTheVantaFolder() throws IOException {
        WorldsFolder none = resolve(WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(), null);
        assertFalse(none.redirected());
        assertEquals(gameDir.resolve("saves"), none.savesDir());
        assertTrue(none.reason().contains("does not exist"), none.reason());

        Files.createDirectories(official);
        WorldsFolder noSaves = resolve(WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(), null);
        assertFalse(noSaves.redirected());
        assertTrue(noSaves.reason().contains("no saves folder"), noSaves.reason());
        assertFalse(Files.exists(official.resolve("saves")), "the saves folder is never created");

        Files.writeString(official.resolve("saves"), "a file, not a folder");
        WorldsFolder savesIsFile = resolve(WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(), null);
        assertFalse(savesIsFile.redirected());
        assertTrue(savesIsFile.reason().contains("not a directory"), savesIsFile.reason());
    }

    @Test
    void aMinecraftFolderThatIsAFileKeepsTheVantaFolder() throws IOException {
        Files.createDirectories(home);
        Files.writeString(official, "not a folder");
        WorldsFolder folder = resolve(WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(), null);
        assertFalse(folder.redirected());
        assertTrue(folder.reason().contains("not a directory"), folder.reason());
    }

    @Test
    void theLauncherNoteWinsOverThePlatformDefault() throws IOException {
        Files.createDirectories(official.resolve("saves"));
        Path custom = tmp.resolve("D").resolve("Games").resolve("minecraft");
        Files.createDirectories(custom.resolve("saves"));

        WorldsFolder folder = resolve(WorldsFolderMode.MINECRAFT_FOLDER, Optional.of(custom), null);
        assertTrue(folder.redirected());
        assertEquals(custom.resolve("saves"), folder.savesDir());
        assertTrue(folder.reason().contains("recorded by the VANTA launcher"), folder.reason());
    }

    @Test
    void aLauncherNoteForAMissingFolderKeepsTheVantaFolderInsteadOfGuessing() throws IOException {
        Files.createDirectories(official.resolve("saves"));
        Path gone = tmp.resolve("removed-drive").resolve("minecraft");
        WorldsFolder folder = resolve(WorldsFolderMode.MINECRAFT_FOLDER, Optional.of(gone), null);
        assertFalse(folder.redirected(), "the recorded folder is authoritative");
        assertTrue(folder.reason().contains(gone.toString()) && folder.reason().contains("does not exist"),
                folder.reason());
    }

    @Test
    void aManualInstallInTheMinecraftFolderIsANoOp() throws IOException {
        Path mc = home.resolve(".minecraft");
        Files.createDirectories(mc.resolve("saves"));
        WorldsFolder folder = WorldsFolder.resolve(mc, WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(), Map.of(),
                LINUX, home, null);
        assertFalse(folder.redirected());
        assertEquals(mc.resolve("saves"), folder.savesDir());
        assertTrue(folder.reason().contains("already runs"), folder.reason());

        WorldsFolder viaOtherSpelling = WorldsFolder.resolve(mc, WorldsFolderMode.MINECRAFT_FOLDER,
                Optional.of(home.resolve("x").resolve("..").resolve(".minecraft")), Map.of(), LINUX, home, null);
        assertFalse(viaOtherSpelling.redirected(), viaOtherSpelling.reason());
    }

    @Test
    void savesLinkedToTheMinecraftFolderAreANoOp() throws IOException {
        Files.createDirectories(official.resolve("saves"));
        Path linkedGame = tmp.resolve("linked-game");
        Files.createDirectories(linkedGame);
        try {
            Files.createSymbolicLink(linkedGame.resolve("saves"), official.resolve("saves"));
        } catch (UnsupportedOperationException | IOException e) {
            return; // no symbolic links on this file system
        }
        WorldsFolder folder = WorldsFolder.resolve(linkedGame, WorldsFolderMode.MINECRAFT_FOLDER, Optional.empty(),
                Map.of(), LINUX, home, null);
        assertFalse(folder.redirected(), folder.reason());
    }

    @Test
    void platformDefaultsMirrorTheLauncher() {
        Path userHome = Path.of("/home/steve");
        assertEquals(Path.of("/home/steve/.minecraft"), WorldsFolder.platformDefault("Linux", Map.of(), userHome));
        assertEquals(Path.of("/home/steve/.minecraft"), WorldsFolder.platformDefault("FreeBSD", Map.of(), userHome));
        assertEquals(Path.of("/home/steve/Library/Application Support/minecraft"),
                WorldsFolder.platformDefault("Mac OS X", Map.of(), userHome));
        assertEquals(Path.of("/appdata/.minecraft"),
                WorldsFolder.platformDefault("Windows 11", Map.of("APPDATA", "/appdata"), userHome));
        assertEquals(userHome.resolve("AppData").resolve("Roaming").resolve(".minecraft"),
                WorldsFolder.platformDefault("Windows 10", Map.of("APPDATA", "  "), userHome),
                "a blank APPDATA falls back to the roaming profile folder");
        assertEquals(userHome.resolve("AppData").resolve("Roaming").resolve(".minecraft"),
                WorldsFolder.platformDefault("Windows 10", Map.of(), userHome));
    }

    @Test
    void theNoteIsReadTolerantly() throws IOException {
        assertEquals(Optional.empty(), MinecraftFolderHint.read(gameDir), "no note");
        Path file = MinecraftFolderHint.file(gameDir);
        assertEquals(gameDir.resolve("config").resolve("vanta").resolve("minecraft-folder.json"), file);
        Files.createDirectories(file.getParent());

        Path abs = tmp.resolve("mc").toAbsolutePath();
        Files.writeString(file, "{\"minecraftDir\": \"" + abs.toString().replace("\\", "\\\\") + "\"}");
        assertEquals(Optional.of(abs.normalize()), MinecraftFolderHint.read(gameDir));

        for (String bad : List.of("", "not json", "[]", "{}", "{\"minecraftDir\": 3}", "{\"minecraftDir\": \"\"}",
                "{\"minecraftDir\": \"relative/path\"}", "{\"minecraftDir\": null}", "{\"minecraftDir\": {}}")) {
            Files.writeString(file, bad);
            assertEquals(Optional.empty(), MinecraftFolderHint.read(gameDir), bad);
        }
    }

    @Test
    void resolveForThisGameUsesTheNoteAndNeverThrows() throws IOException {
        Path custom = tmp.resolve("custom-mc");
        Files.createDirectories(custom.resolve("saves"));
        Path file = MinecraftFolderHint.file(gameDir);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"minecraftDir\": \"" + custom.toAbsolutePath().toString().replace("\\", "\\\\")
                + "\"}");
        String previous = System.getProperty(WorldsFolder.PROPERTY);
        try {
            System.clearProperty(WorldsFolder.PROPERTY);
            WorldsFolder folder = WorldsFolder.resolveForThisGame(gameDir, WorldsFolderMode.MINECRAFT_FOLDER);
            assertTrue(folder.redirected(), folder.reason());
            assertEquals(custom.toAbsolutePath().normalize().resolve("saves"), folder.savesDir());

            System.setProperty(WorldsFolder.PROPERTY, "vanta");
            assertFalse(WorldsFolder.resolveForThisGame(gameDir, WorldsFolderMode.MINECRAFT_FOLDER).redirected());
        } finally {
            if (previous == null) {
                System.clearProperty(WorldsFolder.PROPERTY);
            } else {
                System.setProperty(WorldsFolder.PROPERTY, previous);
            }
        }
    }

    @Test
    void theSettingDefaultsToTheMinecraftFolderAndNeedsARestart() {
        assertEquals(WorldsFolderMode.MINECRAFT_FOLDER, VantaSettings.WORLDS_FOLDER.defaultValue());
        assertTrue(VantaSettings.WORLDS_FOLDER.needsRestart());
        assertFalse(VantaSettings.WORLDS_FOLDER.isVanilla());
        assertEquals(List.of(WorldsFolderMode.MINECRAFT_FOLDER, WorldsFolderMode.VANTA_FOLDER),
                VantaSettings.WORLDS_FOLDER.options());
        assertTrue(VantaSettings.all().contains(VantaSettings.WORLDS_FOLDER));
    }
}
