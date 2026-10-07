package dev.vanta.client.gametest;

import static dev.vanta.client.gametest.VantaClientGameTest.check;
import static dev.vanta.client.gametest.VantaClientGameTest.pollFor;
import static dev.vanta.client.gametest.VantaClientGameTest.step;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vanta.client.WorldsFolderHook;
import dev.vanta.client.screen.VantaScreen;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.worlds.WorldsFolder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * "Singleplayer worlds" in the real game: Minecraft's level storage uses the folder {@link WorldsFolderHook}
 * resolved, so Singleplayer lists the worlds of the official Minecraft folder.
 * <p>
 * CI starts the game with a stand-in official folder ({@code run/production-gametest/official-minecraft} with an
 * empty {@code saves/}), the launcher's note {@code config/vanta/minecraft-folder.json} pointing at it and
 * {@code -D}{@value #EXPECTED_PROPERTY}{@code =<that folder>} (see {@code runProductionClientGametest} in
 * {@code client/build.gradle}). By the time this step runs the earlier steps created the game test world with vanilla
 * code. The step checks:
 * <ol>
 *   <li>{@code getLevelSource().getBaseDir()} / {@code getBackupPath()} are the resolved folders (the mixin applied);</li>
 *   <li>with the stand-in folder: the redirect is active, the game test world is in the stand-in {@code saves/} and
 *       not in the VANTA folder's own;</li>
 *   <li>a copy of that world placed in the stand-in folder by plain file copy (a world VANTA never created) and the
 *       original are both listed when Singleplayer opens {@link SelectWorldScreen} → {@code 45_worlds_folder_list};
 *       Escape returns to the VANTA menu.</li>
 * </ol>
 * Without the property (for example the dev run) only check 1 runs. The step only ever writes inside the stand-in
 * folder; it never touches a real Minecraft folder.
 */
final class WorldsFolderStep {
    /** JVM property with the stand-in official Minecraft folder (set by the production game test task). */
    static final String EXPECTED_PROPERTY = "vanta.gametest.minecraftDir";
    /** Folder name of the copied world. */
    static final String COPIED_WORLD = "VANTA worlds folder check";

    private static final int LIST_TIMEOUT_TICKS = 600;

    private WorldsFolderStep() {
    }

    static void run(ClientGameTestContext context) {
        WorldsFolder folder = WorldsFolderHook.active().orElse(null);
        check(folder != null, "the Singleplayer worlds folder was not resolved by the client entrypoint");
        Path baseDir = context.computeOnClient(client -> client.getLevelSource().getBaseDir());
        Path backupDir = context.computeOnClient(client -> client.getLevelSource().getBackupPath());
        step("worlds folder: " + folder.reason() + "; level storage " + baseDir + ", backups " + backupDir);
        check(same(baseDir, folder.savesDir()), "the level storage uses " + baseDir + " but VANTA resolved "
                + folder.savesDir() + " (MinecraftLevelSourceMixin did not apply)");
        check(same(backupDir, folder.backupsDir()), "the world backups go to " + backupDir + " but VANTA resolved "
                + folder.backupsDir());

        String expected = System.getProperty(EXPECTED_PROPERTY, "").trim();
        if (expected.isEmpty()) {
            step("worlds folder: no stand-in Minecraft folder (-D" + EXPECTED_PROPERTY + "), redirect not exercised");
            return;
        }
        Path official = Path.of(expected).toAbsolutePath().normalize();
        check(folder.redirected() && folder.minecraftDir().map(dir -> same(dir, official)).orElse(false),
                "the worlds should come from the stand-in Minecraft folder " + official + ": " + folder.reason());
        check(same(baseDir, official.resolve("saves")), "the level storage should be " + official.resolve("saves")
                + " but is " + baseDir);

        List<String> worlds = worldIds(baseDir);
        check(!worlds.isEmpty(), "the game test world was not created in the Minecraft folder " + baseDir);
        Path vantaSaves = FabricLoader.getInstance().getGameDir().resolve("saves");
        for (String id : worlds) {
            check(!Files.exists(vantaSaves.resolve(id).resolve("level.dat")),
                    "world " + id + " was also written to the VANTA folder " + vantaSaves);
        }
        step("worlds folder: " + worlds.size() + " world(s) in the Minecraft folder " + baseDir
                + ", none of them in the VANTA folder " + vantaSaves);

        String original = worlds.stream().filter(id -> !COPIED_WORLD.equals(id)).findFirst().orElseThrow();
        copyWorld(baseDir.resolve(original), baseDir.resolve(COPIED_WORLD));
        List<String> expectedIds = List.of(original, COPIED_WORLD);

        clickSingleplayer(context);
        boolean listed = pollFor(context, client -> listedWorlds(client.screen).containsAll(expectedIds),
                LIST_TIMEOUT_TICKS);
        List<String> shown = context.computeOnClient(client -> listedWorlds(client.screen));
        check(listed, "Singleplayer should list " + expectedIds + " from the Minecraft folder but shows " + shown
                + " on " + context.computeOnClient(client -> client.screen == null ? "no screen"
                : client.screen.getClass().getName()));
        context.waitTicks(5);
        Path shot = context.takeScreenshot("45_worlds_folder_list");
        step("worlds folder: Singleplayer lists " + shown + " from the Minecraft folder → " + shot.getFileName());

        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        boolean menu = pollFor(context, client -> client.screen instanceof VantaScreen vanta
                && vanta.screenId() == ScreenId.MAIN_MENU, 100);
        check(menu, "Escape on the world list did not return to the VANTA main menu");
        step("worlds folder: world list → Escape → VANTA main menu");
    }

    /** Level ids of the world entries the vanilla world list shows (empty while it is still loading). */
    private static List<String> listedWorlds(net.minecraft.client.gui.screens.Screen screen) {
        List<String> ids = new ArrayList<>();
        if (!(screen instanceof SelectWorldScreen)) {
            return ids;
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof WorldSelectionList list) {
                for (WorldSelectionList.Entry entry : list.children()) {
                    LevelSummary summary = entry.getLevelSummary();
                    if (summary != null) {
                        ids.add(summary.getLevelId());
                    }
                }
            }
        }
        return ids;
    }

    /** Presses Singleplayer on the VANTA main menu through the core widget, like a mouse click. */
    private static void clickSingleplayer(ClientGameTestContext context) {
        boolean menu = pollFor(context, client -> client.screen instanceof VantaScreen vanta
                && vanta.screenId() == ScreenId.MAIN_MENU, 100);
        check(menu, "the VANTA main menu is not open before the worlds folder check");
        boolean clicked = context.computeOnClient(client -> {
            if (!(client.screen instanceof VantaScreen vanta) || vanta.screenId() != ScreenId.MAIN_MENU) {
                return false;
            }
            UiNode node = vanta.ui().root().findById("menu.singleplayer");
            if (!(node instanceof Button button)) {
                return false;
            }
            button.click(vanta.ui().context());
            return true;
        });
        check(clicked, "VANTA main menu button menu.singleplayer was not found");
    }

    /** World folders (with a level.dat) directly below {@code saves}, sorted by name. */
    private static List<String> worldIds(Path saves) {
        if (!Files.isDirectory(saves)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(saves)) {
            return dirs.filter(dir -> Files.isRegularFile(dir.resolve("level.dat")))
                    .map(dir -> dir.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Copies a closed world folder (without its session.lock) inside the stand-in folder; keeps an existing copy. */
    private static void copyWorld(Path from, Path to) {
        if (Files.isRegularFile(to.resolve("level.dat"))) {
            step("worlds folder: copied world already present from an earlier run: " + to.getFileName());
            return;
        }
        try (Stream<Path> files = Files.walk(from)) {
            for (Path source : files.toList()) {
                if (source.getFileName().toString().equals("session.lock")) {
                    continue;
                }
                Path target = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not copy the game test world to " + to, e);
        }
        step("worlds folder: copied " + from.getFileName() + " to " + to.getFileName() + " in the Minecraft folder");
    }

    private static boolean same(Path a, Path b) {
        return a != null && b != null && a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }
}
