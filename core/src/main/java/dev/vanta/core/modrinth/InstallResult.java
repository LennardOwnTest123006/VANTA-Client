package dev.vanta.core.modrinth;

import java.util.ArrayList;
import java.util.List;

/**
 * What an install actually changed.
 *
 * @param installed files downloaded, verified and committed
 * @param enabled   installed projects switched back on
 * @param failed    files that were not installed (download failed, checksum mismatch, name conflict, failed dependency)
 * @param notes     the plan's notes followed by one note per failed file
 */
public record InstallResult(List<PlannedInstall> installed, List<String> enabled, List<PlannedInstall> failed,
                            List<PlanNote> notes) {
    public InstallResult {
        installed = List.copyOf(installed);
        enabled = List.copyOf(enabled);
        failed = List.copyOf(failed);
        notes = List.copyOf(notes);
    }

    /** True when at least one mod jar was added or enabled (the game must restart to load it). */
    public boolean modsChanged() {
        return installed.stream().anyMatch(i -> i.type().needsRestart()) || !enabled.isEmpty();
    }

    /** Installed items of a type. */
    public List<PlannedInstall> installed(ModrinthProjectType type) {
        return installed.stream().filter(i -> i.type() == type).toList();
    }

    /** True when nothing failed and nothing was refused or skipped for a problem. */
    public boolean isClean() {
        return failed.isEmpty() && notes.stream().noneMatch(n -> n.kind().isProblem());
    }

    /** Notes that describe a problem. */
    public List<PlanNote> problems() {
        List<PlanNote> out = new ArrayList<>();
        for (PlanNote note : notes) {
            if (note.kind().isProblem()) {
                out.add(note);
            }
        }
        return out;
    }
}
