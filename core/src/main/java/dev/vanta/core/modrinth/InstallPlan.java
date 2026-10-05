package dev.vanta.core.modrinth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Result of {@link InstallPlanner#plan}: the files to download (requested projects and their required dependencies),
 * installed-but-disabled dependencies to switch back on, new {@code requiredBy} links for projects already installed
 * and notes about everything skipped or refused.
 *
 * @param installs           files to download, dependencies before the projects needing them
 * @param enableExisting     installed (disabled) projects that are required and get enabled
 * @param requiredByExisting installed project id → new parents
 * @param notes              skipped / refused / informational notes
 */
public record InstallPlan(List<PlannedInstall> installs, List<String> enableExisting,
                          Map<String, Set<String>> requiredByExisting, List<PlanNote> notes) {
    public InstallPlan {
        installs = List.copyOf(installs);
        enableExisting = List.copyOf(enableExisting);
        requiredByExisting = Map.copyOf(requiredByExisting);
        notes = List.copyOf(notes);
    }

    /** True when nothing will change on disk. */
    public boolean isEmpty() {
        return installs.isEmpty() && enableExisting.isEmpty();
    }

    /** Sum of the file sizes to download. */
    public long totalBytes() {
        long total = 0;
        for (PlannedInstall install : installs) {
            total += Math.max(0L, install.file().size());
        }
        return total;
    }

    /** The planned item of a project. */
    public Optional<PlannedInstall> find(String projectId) {
        return installs.stream().filter(i -> i.projectId().equals(projectId)).findFirst();
    }

    /** Notes of one kind. */
    public List<PlanNote> notes(PlanNote.Kind kind) {
        return notes.stream().filter(n -> n.kind() == kind).toList();
    }
}
