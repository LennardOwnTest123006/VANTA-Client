package dev.vanta.core.modrinth;

import java.util.List;

/**
 * One file the plan downloads.
 *
 * @param projectId  project id
 * @param slug       project slug
 * @param title      project title
 * @param type       project type (decides the folder)
 * @param version    chosen version
 * @param file       the version's primary file
 * @param root       true when the player asked for it, false for a dependency
 * @param requiredBy project ids (in this plan or installed) that need it
 * @param dependsOn  project ids in this plan it needs (an item is only committed when all of them succeeded)
 */
public record PlannedInstall(String projectId, String slug, String title, ModrinthProjectType type,
                             ModrinthVersion version, ModrinthFile file, boolean root, List<String> requiredBy,
                             List<String> dependsOn) {
    public PlannedInstall {
        requiredBy = List.copyOf(requiredBy);
        dependsOn = List.copyOf(dependsOn);
    }

    /** Path relative to the game directory, forward slashes. */
    public String relativeFile() {
        return type.directory() + "/" + file.filename();
    }
}
