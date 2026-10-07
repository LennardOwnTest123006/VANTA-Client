package dev.vanta.core.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeClipboardBridge;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.FakeScreenshotBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link VantaServices#saveAll()} runs on the render thread every time a VANTA screen closes, including the Escape
 * back into the game. With nothing changed it must not touch a single file (it used to rewrite every profile, the
 * profile state and {@code cosmetics.json} each time); with a change it writes only that change.
 */
class SaveOnCloseTest {
    private static final FileTime OLD = FileTime.fromMillis(946_684_800_000L); // 2000-01-01

    @TempDir
    Path dir;

    private VantaPaths paths;
    private FakeOptionsBridge options;
    private VantaServices services;

    @BeforeEach
    void setUp() {
        paths = VantaPaths.inGameDirectory(dir);
        options = new FakeOptionsBridge();
        services = VantaServices.create(paths, new FakeGameBridge(), options, new FakeKeybindBridge(),
                new FakeResourcePackBridge(), new FakeScreenshotBridge(), new FakeClipboardBridge(),
                MutableClock.standard());
        services.load();
    }

    /** Sets every file under the config root to an old time stamp and returns the files. */
    private Map<Path, FileTime> ageAllFiles() throws IOException {
        Map<Path, FileTime> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(paths.root())) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Files.setLastModifiedTime(file, OLD);
                out.put(file, OLD);
            }
        }
        return out;
    }

    private List<Path> rewrittenSince(Map<Path, FileTime> aged) throws IOException {
        try (Stream<Path> files = Files.walk(paths.root())) {
            return files.filter(Files::isRegularFile)
                    .filter(f -> {
                        try {
                            return !OLD.equals(Files.getLastModifiedTime(f)) || !aged.containsKey(f);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
        }
    }

    @Test
    void closingScreensWithoutChangesWritesNothing() throws IOException {
        // The first close after start-up may record the active profile and the loaded cosmetic packs once.
        services.saveAll();
        assertTrue(Files.exists(paths.profilesStateFile()), "the active profile is recorded once");
        assertTrue(Files.exists(paths.cosmeticsFile()), "the loaded packs are recorded once");
        assertFalse(services.profiles().hasUnsavedChanges());
        assertFalse(services.cosmetics().isDirty());

        Map<Path, FileTime> aged = ageAllFiles();
        assertTrue(aged.size() >= 7, "5 built-in profiles, the state file and cosmetics.json: " + aged.keySet());
        for (int i = 0; i < 10; i++) {
            services.saveAll();
        }
        assertEquals(List.of(), rewrittenSince(aged), "ten screen closes without a change rewrite no file");
    }

    @Test
    void aChangeIsWrittenAndOnlyThatChange() throws IOException {
        services.saveAll();
        Map<Path, FileTime> aged = ageAllFiles();

        services.settings().set(VantaSettings.HUD_TEXT_SHADOW, false);
        services.saveAll();
        assertEquals(List.of(paths.settingsFile()), rewrittenSince(aged), "only settings.json changed");

        aged = ageAllFiles();
        Profile created = services.profiles().createFromCurrent("Mine", "profile");
        Path createdFile = paths.profilesDir().resolve(created.id() + ".json");
        assertEquals(List.of(createdFile), rewrittenSince(aged), "a new profile is written when it is created");
        aged = ageAllFiles();
        services.saveAll();
        assertEquals(List.of(), rewrittenSince(aged), "and not again when the screen closes");

        assertTrue(services.activateProfile(created.id()));
        List<Path> afterActivation = rewrittenSince(aged);
        assertTrue(afterActivation.contains(paths.profilesStateFile()), afterActivation.toString());
        aged = ageAllFiles();
        services.saveAll();
        List<Path> afterClose = rewrittenSince(aged);
        assertFalse(afterClose.stream().anyMatch(p -> p.startsWith(paths.profilesDir())),
                "activation already wrote the state file: " + afterClose);
    }

    @Test
    void aFailedProfileWriteIsRetriedOnTheNextClose() throws IOException {
        services.saveAll();
        // A directory in the way makes the atomic move of the new profile file fail.
        Path blocked = paths.profilesDir().resolve("blocked.json");
        Files.createDirectories(blocked.resolve("inside"));
        assertThrows(UncheckedIOException.class, () -> services.profiles().createFromCurrent("Blocked", "profile"));
        assertTrue(services.profiles().find("blocked").isPresent(), "the profile exists in memory");
        assertTrue(services.profiles().hasUnsavedChanges());

        services.saveAll(); // still blocked: logged, kept pending, the close itself does not fail
        assertTrue(services.profiles().hasUnsavedChanges());

        Files.delete(blocked.resolve("inside"));
        Files.delete(blocked);
        services.saveAll();
        assertFalse(services.profiles().hasUnsavedChanges());
        assertTrue(Files.isRegularFile(blocked), "written on the next close");
    }
}
