package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.config.VantaPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalAiPathsTest {
    @TempDir
    Path dir;

    @Test
    void withoutANoteTheClientFolderIsUsed() {
        VantaPaths paths = VantaPaths.inGameDirectory(dir);
        LocalAiPaths ai = LocalAiPaths.resolve(paths);
        assertFalse(ai.isLauncherManaged());
        assertEquals(paths.localAiDir(), ai.root());
        assertEquals(paths.root().resolve("local-ai"), ai.root());
        assertTrue(paths.contains(ai.root()), "the client writes only below config/vanta");
        assertEquals(ai.root().resolve("installed.json"), ai.installedFile());
        Platform linux = Platform.parse("linux-x64").orElseThrow();
        assertEquals(ai.root().resolve("runtime").resolve("b11429").resolve("linux-x64"),
                ai.runtimeDir("b11429", linux));
        assertEquals(ai.runtimeDir("b11429", linux).resolve("build").resolve("bin").resolve("llama-server"),
                ai.serverExecutable("b11429", linux, "build/bin/llama-server"));
        assertEquals(ai.root().resolve("models").resolve("m.gguf"), ai.modelFile("m.gguf"));
        assertEquals(ai.root().resolve("downloads").resolve("m.gguf.part"), ai.partFile("m.gguf"));
        assertEquals(ai.root().resolve("downloads").resolve("m.gguf"), ai.downloadFile("m.gguf"));
        assertEquals(ai.root().resolve("logs").resolve("llama-server.log"), ai.serverLogFile());
        assertEquals(5, ai.directories().size());
        assertTrue(ai.contains(ai.modelFile("x")));
        assertFalse(ai.contains(dir.resolve("elsewhere")));
    }

    @Test
    void aNotePointsAtTheLauncherInstallReadOnly() throws IOException {
        VantaPaths paths = VantaPaths.inGameDirectory(dir);
        Path launcherDir = dir.resolve("launcher-data").resolve("local-ai");
        LocalAiPaths.writeNote(paths.localAiNoteFile(), launcherDir);
        assertTrue(Files.isRegularFile(paths.localAiNoteFile()));
        assertTrue(Files.readString(paths.localAiNoteFile()).contains("\"localAiDir\""));

        LocalAiPaths ai = LocalAiPaths.resolve(paths);
        assertTrue(ai.isLauncherManaged());
        assertEquals(launcherDir.toAbsolutePath().normalize(), ai.root());
        assertEquals(launcherDir.toAbsolutePath().normalize(), LocalAiPaths.readNote(paths.localAiNoteFile())
                .orElseThrow());
    }

    @Test
    void brokenOrRelativeNotesFallBackToTheClientFolder() throws IOException {
        VantaPaths paths = VantaPaths.inGameDirectory(dir);
        Files.createDirectories(paths.root());
        Files.writeString(paths.localAiNoteFile(), "{ not json", StandardCharsets.UTF_8);
        assertFalse(LocalAiPaths.resolve(paths).isLauncherManaged());
        Files.writeString(paths.localAiNoteFile(), "{\"localAiDir\": \"relative/dir\"}", StandardCharsets.UTF_8);
        assertFalse(LocalAiPaths.resolve(paths).isLauncherManaged());
        Files.writeString(paths.localAiNoteFile(), "{\"other\": 1}", StandardCharsets.UTF_8);
        assertFalse(LocalAiPaths.resolve(paths).isLauncherManaged());
        Files.writeString(paths.localAiNoteFile(), "[1,2]", StandardCharsets.UTF_8);
        assertFalse(LocalAiPaths.resolve(paths).isLauncherManaged());
        assertEquals(paths.localAiDir(), LocalAiPaths.resolve(paths).root());
    }

    @Test
    void vantaPathsIncludeTheLocalAiFiles() throws IOException {
        VantaPaths paths = VantaPaths.inGameDirectory(dir);
        assertEquals("local-ai.json", paths.localAiNoteFile().getFileName().toString());
        assertEquals("nexus-chat.json", paths.nexusChatFile().getFileName().toString());
        assertTrue(paths.directories().contains(paths.localAiDir()));
        paths.createDirectories();
        assertTrue(Files.isDirectory(paths.localAiDir()));
    }
}
