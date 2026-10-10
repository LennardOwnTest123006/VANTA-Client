package dev.vanta.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Resolves every file and directory VANTA writes below its configuration root.
 * <p>
 * In game the root is {@code <gameDir>/config/vanta/}; tests inject a temporary directory. Nothing outside the root
 * is ever written by {@code core}.
 */
public final class VantaPaths {
    private final Path root;

    /**
     * @param root configuration root directory (created lazily by {@link #createDirectories()})
     */
    public VantaPaths(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    /** Builds the standard layout below {@code <gameDir>/config/vanta}. */
    public static VantaPaths inGameDirectory(Path gameDirectory) {
        return new VantaPaths(gameDirectory.resolve("config").resolve("vanta"));
    }

    /** Configuration root ({@code config/vanta}). */
    public Path root() {
        return root;
    }

    /** {@code settings.json}: every VANTA setting value. */
    public Path settingsFile() {
        return root.resolve("settings.json");
    }

    /** {@code profiles/}: one JSON file per profile plus {@code state.json}. */
    public Path profilesDir() {
        return root.resolve("profiles");
    }

    /** {@code profiles/state.json}: which profile is active. */
    public Path profilesStateFile() {
        return profilesDir().resolve("state.json");
    }

    /** {@code hud/}: current layout and user presets. */
    public Path hudDir() {
        return root.resolve("hud");
    }

    /** {@code hud/layout.json}: the live HUD layout. */
    public Path hudLayoutFile() {
        return hudDir().resolve("layout.json");
    }

    /** {@code hud/presets/}: user-created HUD presets. */
    public Path hudPresetsDir() {
        return hudDir().resolve("presets");
    }

    /** {@code stats.json}: local statistics (never leaves the computer). */
    public Path statsFile() {
        return root.resolve("stats.json");
    }

    /** {@code crosshair.json}: the active crosshair style. */
    public Path crosshairFile() {
        return root.resolve("crosshair.json");
    }

    /** {@code smart-boost.json}: what Smart Boost measured and which video options it wrote. */
    public Path smartBoostFile() {
        return root.resolve("smart-boost.json");
    }

    /** {@code frame-rate.json}: the one-time frame-rate uncap (which client version ran it and what it found). */
    public Path frameRateFile() {
        return root.resolve("frame-rate.json");
    }

    /** {@code cosmetics.json}: cosmetics state (installed packs). */
    public Path cosmeticsFile() {
        return root.resolve("cosmetics.json");
    }

    /** {@code cosmetics/}: user-installed cosmetic packs ({@code *.json}). */
    public Path cosmeticPacksDir() {
        return root.resolve("cosmetics");
    }

    /**
     * {@code local-ai/}: the client-managed Local AI install (llama.cpp runtime, model, logs). The only place the
     * client itself writes Local AI files to; a launcher-managed install lives outside the game folder and is named
     * by {@link #localAiNoteFile()}.
     */
    public Path localAiDir() {
        return root.resolve("local-ai");
    }

    /**
     * {@code local-ai.json}: the launcher's note {@code {"localAiDir": "<absolute path>"}} telling the client to use
     * the launcher's Local AI install read-only. Absent for manual jar installs.
     */
    public Path localAiNoteFile() {
        return root.resolve("local-ai.json");
    }

    /** {@code nexus-chat.json}: the Vanta Nexus conversation (last turns, local only). */
    public Path nexusChatFile() {
        return root.resolve("nexus-chat.json");
    }

    /** {@code waypoints.json}: the player's waypoints for every world (local only, never sent anywhere). */
    public Path waypointsFile() {
        return root.resolve("waypoints.json");
    }

    /** All directories that must exist before the stores write. */
    public List<Path> directories() {
        return List.of(root, profilesDir(), hudDir(), hudPresetsDir(), cosmeticPacksDir(), localAiDir());
    }

    /**
     * Creates every directory of {@link #directories()}.
     *
     * @throws IOException when a directory cannot be created
     */
    public void createDirectories() throws IOException {
        for (Path dir : directories()) {
            Files.createDirectories(dir);
        }
    }

    /** True when {@code path} lies inside the configuration root. */
    public boolean contains(Path path) {
        return path.toAbsolutePath().normalize().startsWith(root);
    }

    @Override
    public String toString() {
        return "VantaPaths[" + root + "]";
    }
}
