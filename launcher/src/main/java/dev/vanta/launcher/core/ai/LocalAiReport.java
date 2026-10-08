package dev.vanta.launcher.core.ai;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the launcher knows about the Local AI at one moment: its state, the folder, the manifest of this build (when
 * usable), the installed record (when present), the bytes the folder occupies and every problem found.
 *
 * @param state       install state
 * @param platform    platform key of the host ({@code linux-x64}, ...)
 * @param dir         the Local AI folder ({@code <data>/local-ai})
 * @param manifest    the manifest of this build, empty when it is missing or invalid (then {@link #problems()} says why)
 * @param installed   {@code installed.json}, empty when nothing is installed
 * @param bytesOnDisk bytes the folder occupies right now
 * @param problems    what is wrong, in plain English (empty when the state is {@link LocalAiState#INSTALLED} and current)
 * @param outdated    whether the manifest of this build names a newer runtime or model than the installed one
 */
public record LocalAiReport(LocalAiState state, String platform, Path dir, Optional<LocalAiManifest> manifest,
                            Optional<LocalAiInstalled> installed, long bytesOnDisk, List<String> problems, boolean outdated) {

    public LocalAiReport {
        Objects.requireNonNull(state, "state");
        platform = platform == null ? "" : platform;
        Objects.requireNonNull(dir, "dir");
        manifest = manifest == null ? Optional.empty() : manifest;
        installed = installed == null ? Optional.empty() : installed;
        bytesOnDisk = Math.max(0L, bytesOnDisk);
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    /** @return whether runtime and model are in place */
    public boolean isInstalled() {
        return state == LocalAiState.INSTALLED;
    }

    /** @return whether an install can be started (a manifest exists and lists this platform) */
    public boolean installable() {
        return manifest.isPresent() && manifest.get().platform(platform).isPresent();
    }

    /** @return bytes an install would download on this platform (archive plus model), {@code 0} when unsupported */
    public long downloadBytes() {
        return manifest.map(m -> m.downloadBytes(platform)).orElse(0L);
    }

    /** @return {@code llama.cpp b11429, Qwen3-1.7B Q8_0} of what is installed, else of the manifest, else empty */
    public String describeVersions() {
        return installed.map(LocalAiInstalled::describe).or(() -> manifest.map(LocalAiManifest::describe)).orElse("");
    }

    /** @return the installed server executable (absolute), when recorded */
    public Optional<Path> serverExecutable() {
        return installed.flatMap(i -> i.file(LocalAiInstalled.ROLE_SERVER)).map(f -> dir.resolve(f.path()));
    }

    /** @return the installed model file (absolute), when recorded */
    public Optional<Path> modelFile() {
        return installed.flatMap(i -> i.file(LocalAiInstalled.ROLE_MODEL)).map(f -> dir.resolve(f.path()));
    }
}
