package dev.vanta.core.modrinth;

import java.util.Optional;

/**
 * One entry of the "Installed" list: either a project from {@code modrinth.json} or a file found in {@code mods/},
 * {@code shaderpacks/} or {@code resourcepacks/} that VANTA did not install (shown by file name, never deleted).
 *
 * @param entry        the index entry, empty for files installed by other means
 * @param type         project type (from the folder for unknown files)
 * @param name         display name (title, or file name for unknown files)
 * @param relativeFile path relative to the game directory as it is on disk now
 * @param enabled      whether the file is active (not {@code .disabled})
 * @param state        where the item comes from and whether its file exists
 */
public record LocalItem(Optional<InstalledEntry> entry, ModrinthProjectType type, String name, String relativeFile,
                        boolean enabled, State state) {

    /** Origin and file state. */
    public enum State {
        /** Installed by VANTA; the file exists. */
        INSTALLED,
        /** Listed in {@code modrinth.json} but the file is gone. */
        MISSING,
        /** Found on disk, not installed by VANTA. */
        MANUAL
    }

    /** True for items VANTA manages (can remove / disable). */
    public boolean managed() {
        return entry.isPresent();
    }

    /** Project id of a managed item. */
    public Optional<String> projectId() {
        return entry.map(InstalledEntry::projectId);
    }

    /** Stable id for list rows. */
    public String key() {
        return entry.map(e -> "project:" + e.projectId()).orElse("file:" + relativeFile);
    }
}
