package dev.vanta.core.modrinth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Turns install requests into an {@link InstallPlan} for one Minecraft version.
 * <ul>
 *   <li>Each requested project gets its best version ({@link VersionSelector}): Fabric for mods, Iris for shaders.
 *       A project without a compatible version is skipped with a note; the other requests still go ahead.</li>
 *   <li>Required dependencies are resolved recursively. A dependency pinned to a version id gets that version when
 *       it fits the game version, otherwise the best version of its project. Projects already present (in the index,
 *       identified by hash, loaded by the game, or Fabric API, which VANTA itself requires) are not downloaded again;
 *       disabled ones in the index are switched back on.</li>
 *   <li>If a version declares a project incompatible that is installed or part of the plan, or a required dependency
 *       cannot be satisfied, that request (with its dependencies) is refused with a note.</li>
 * </ul>
 * Requests are planned one after another; a refused request never affects what earlier ones planned.
 */
public final class InstallPlanner {
    private static final int MAX_PROJECTS = 64;

    private final ModrinthApi api;
    private final String gameVersion;

    public InstallPlanner(ModrinthApi api, String gameVersion) {
        this.api = Objects.requireNonNull(api, "api");
        this.gameVersion = Objects.requireNonNull(gameVersion, "gameVersion");
    }

    /** Planner for {@link ModrinthConstants#GAME_VERSION}. */
    public InstallPlanner(ModrinthApi api) {
        this(api, ModrinthConstants.GAME_VERSION);
    }

    /** Plans the requests against what is present. Network failures of one request become an {@code ERROR} note. */
    public InstallPlan plan(List<InstallRequest> requests, InstallState state) {
        Run run = new Run(state);
        for (InstallRequest request : requests) {
            try {
                run.planRoot(request);
            } catch (ModrinthException e) {
                PlanNote.Kind kind = e.kind() == ModrinthException.Kind.NOT_FOUND ? PlanNote.Kind.NOT_FOUND
                        : PlanNote.Kind.ERROR;
                run.notes.add(new PlanNote(kind, request.label(), e.getMessage()));
            }
        }
        return run.result();
    }

    /** A planned download while planning is in progress. */
    private static final class Draft {
        final String projectId;
        final String slug;
        final String title;
        final ModrinthProjectType type;
        ModrinthVersion version;
        boolean root;
        boolean pinned;
        final LinkedHashSet<String> requiredBy = new LinkedHashSet<>();
        final LinkedHashSet<String> dependsOn = new LinkedHashSet<>();

        Draft(ModrinthProject project, ModrinthProjectType type, ModrinthVersion version, boolean root, boolean pinned) {
            this.projectId = project.id();
            this.slug = project.slug();
            this.title = project.title();
            this.type = type;
            this.version = version;
            this.root = root;
            this.pinned = pinned;
        }

        private Draft(Draft other) {
            this.projectId = other.projectId;
            this.slug = other.slug;
            this.title = other.title;
            this.type = other.type;
            this.version = other.version;
            this.root = other.root;
            this.pinned = other.pinned;
            this.requiredBy.addAll(other.requiredBy);
            this.dependsOn.addAll(other.dependsOn);
        }

        Draft copy() {
            return new Draft(this);
        }

        PlannedInstall toPlanned() {
            ModrinthFile file = version.primaryFile().orElseThrow();
            return new PlannedInstall(projectId, slug, title, type, version, file, root, List.copyOf(requiredBy),
                    List.copyOf(dependsOn));
        }
    }

    /** Changes one request wants to make; merged into the run only when the request is not refused. */
    private static final class Sub {
        final LinkedHashMap<String, Draft> drafts = new LinkedHashMap<>();
        final LinkedHashSet<String> enable = new LinkedHashSet<>();
        final Map<String, Set<String>> requiredByExisting = new LinkedHashMap<>();
    }

    /** State of one {@link #plan} call. */
    private final class Run {
        final InstallState state;
        final LinkedHashMap<String, Draft> planned = new LinkedHashMap<>();
        final LinkedHashSet<String> enable = new LinkedHashSet<>();
        final Map<String, Set<String>> requiredByExisting = new LinkedHashMap<>();
        final List<PlanNote> notes = new ArrayList<>();
        final Map<String, ModrinthProject> projects = new HashMap<>();
        final Set<PlanNote.Kind> onceNotes = new HashSet<>();

        Run(InstallState state) {
            this.state = state;
        }

        ModrinthProject project(String idOrSlug) throws ModrinthException {
            ModrinthProject cached = projects.get(idOrSlug);
            if (cached != null) {
                return cached;
            }
            ModrinthProject project = api.project(idOrSlug);
            projects.put(idOrSlug, project);
            projects.put(project.id(), project);
            projects.put(project.slug(), project);
            return project;
        }

        String titleOf(String projectId) {
            Draft draft = planned.get(projectId);
            if (draft != null) {
                return draft.title;
            }
            try {
                return project(projectId).title();
            } catch (ModrinthException e) {
                return projectId;
            }
        }

        void noteOnce(PlanNote.Kind kind, String subject, String detail) {
            if (onceNotes.add(kind)) {
                notes.add(new PlanNote(kind, subject, detail));
            }
        }

        void planRoot(InstallRequest request) throws ModrinthException {
            ModrinthProject project = project(request.idOrSlug());
            Draft existing = planned.get(project.id());
            if (existing != null) {
                existing.root = true;
                return;
            }
            if (state.isPresent(project.id()) || state.isLoaded(project.slug())) {
                if (ModrinthConstants.FABRIC_API_PROJECT_ID.equals(project.id())
                        && !state.installed().containsKey(project.id())) {
                    noteOnce(PlanNote.Kind.FABRIC_API_PRESENT, project.title(), "");
                } else {
                    notes.add(new PlanNote(PlanNote.Kind.ALREADY_INSTALLED, project.title(), ""));
                }
                if (state.isDisabled(project.id())) {
                    enable.add(project.id());
                }
                return;
            }
            Optional<ModrinthProjectType> type = project.type();
            if (type.isEmpty()) {
                notes.add(new PlanNote(PlanNote.Kind.UNSUPPORTED_TYPE, project.title(), project.projectType()));
                return;
            }
            List<ModrinthVersion> versions = api.versions(project.id(), type.get().loaders(), List.of(gameVersion));
            Optional<ModrinthVersion> best = VersionSelector.best(versions, gameVersion, type.get());
            if (best.isEmpty()) {
                notes.add(new PlanNote(PlanNote.Kind.NO_COMPATIBLE_VERSION, project.title(), gameVersion));
                return;
            }
            Sub sub = new Sub();
            Draft root = new Draft(project, type.get(), best.get(), true, false);
            sub.drafts.put(root.projectId, root);
            Deque<Draft> queue = new ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty()) {
                Draft parent = queue.poll();
                for (ModrinthDependency dep : parent.version.dependencies()) {
                    if (dep.type() == ModrinthDependency.Type.INCOMPATIBLE) {
                        String conflict = incompatibleTarget(dep, sub);
                        if (conflict != null) {
                            notes.add(new PlanNote(PlanNote.Kind.INCOMPATIBLE, project.title(), titleOf(conflict)));
                            return;
                        }
                    } else if (dep.type() == ModrinthDependency.Type.REQUIRED) {
                        if (!required(project, parent, dep, sub, queue)) {
                            return;
                        }
                    }
                }
                if (planned.size() + sub.drafts.size() > MAX_PROJECTS) {
                    notes.add(new PlanNote(PlanNote.Kind.MISSING_DEPENDENCY, project.title(),
                            "more than " + MAX_PROJECTS + " dependencies"));
                    return;
                }
            }
            planned.putAll(sub.drafts);
            enable.addAll(sub.enable);
            for (Map.Entry<String, Set<String>> e : sub.requiredByExisting.entrySet()) {
                requiredByExisting.computeIfAbsent(e.getKey(), k -> new LinkedHashSet<>()).addAll(e.getValue());
            }
        }

        /** The present or planned project a dependency declares incompatible, or {@code null}. */
        String incompatibleTarget(ModrinthDependency dep, Sub sub) throws ModrinthException {
            String target = dep.projectId();
            if (target == null && dep.versionId() != null) {
                try {
                    target = api.version(dep.versionId()).projectId();
                } catch (ModrinthException e) {
                    if (e.kind() != ModrinthException.Kind.NOT_FOUND) {
                        throw e;
                    }
                    return null;
                }
            }
            if (target == null) {
                return null;
            }
            if (state.isPresent(target) || planned.containsKey(target) || sub.drafts.containsKey(target)) {
                return target;
            }
            return null;
        }

        /** Resolves one required dependency; false when the request has to be refused. */
        boolean required(ModrinthProject rootProject, Draft parent, ModrinthDependency dep, Sub sub, Deque<Draft> queue)
                throws ModrinthException {
            String pid = dep.projectId();
            ModrinthVersion pinned = null;
            if (dep.versionId() != null) {
                try {
                    pinned = api.version(dep.versionId());
                } catch (ModrinthException e) {
                    if (e.kind() != ModrinthException.Kind.NOT_FOUND) {
                        throw e;
                    }
                }
                if (pid == null && pinned != null) {
                    pid = pinned.projectId();
                }
            }
            if (pid == null) {
                notes.add(new PlanNote(PlanNote.Kind.EXTERNAL_DEPENDENCY, rootProject.title(),
                        dep.fileName() == null ? "" : dep.fileName()));
                return false;
            }
            if (pid.equals(parent.projectId)) {
                return true;
            }
            if (ModrinthConstants.FABRIC_API_PROJECT_ID.equals(pid) && state.fabricApiPresent()) {
                noteOnce(PlanNote.Kind.FABRIC_API_PRESENT, "Fabric API", "");
                return true;
            }
            if (state.isPresent(pid)) {
                if (state.installed().containsKey(pid)) {
                    sub.requiredByExisting.computeIfAbsent(pid, k -> new LinkedHashSet<>()).add(parent.projectId);
                    if (state.isDisabled(pid)) {
                        sub.enable.add(pid);
                    }
                }
                return true;
            }
            Draft existing = sub.drafts.get(pid);
            if (existing == null && planned.containsKey(pid)) {
                existing = planned.get(pid).copy();
                sub.drafts.put(pid, existing);
            }
            if (existing != null) {
                existing.requiredBy.add(parent.projectId);
                parent.dependsOn.add(pid);
                if (pinned != null && !existing.pinned && !existing.version.id().equals(pinned.id())
                        && VersionSelector.isInstallable(pinned, gameVersion, existing.type.loaders())) {
                    existing.version = pinned;
                    existing.pinned = true;
                    queue.add(existing);
                }
                return true;
            }
            ModrinthProject depProject = project(pid);
            if (state.isLoaded(depProject.slug())) {
                return true;
            }
            Optional<ModrinthProjectType> depType = depProject.type();
            if (depType.isEmpty()) {
                notes.add(new PlanNote(PlanNote.Kind.MISSING_DEPENDENCY, rootProject.title(), depProject.title()));
                return false;
            }
            ModrinthVersion chosen = null;
            boolean usedPin = false;
            if (pinned != null && VersionSelector.isInstallable(pinned, gameVersion, depType.get().loaders())) {
                chosen = pinned;
                usedPin = true;
            } else {
                chosen = VersionSelector.best(api.versions(depProject.id(), depType.get().loaders(),
                        List.of(gameVersion)), gameVersion, depType.get()).orElse(null);
            }
            if (chosen == null) {
                notes.add(new PlanNote(PlanNote.Kind.MISSING_DEPENDENCY, rootProject.title(), depProject.title()));
                return false;
            }
            Draft draft = new Draft(depProject, depType.get(), chosen, false, usedPin);
            draft.requiredBy.add(parent.projectId);
            parent.dependsOn.add(depProject.id());
            sub.drafts.put(draft.projectId, draft);
            queue.add(draft);
            return true;
        }

        InstallPlan result() {
            List<PlannedInstall> ordered = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            for (Draft draft : planned.values()) {
                visit(draft, visited, ordered);
            }
            Map<String, Set<String>> links = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> e : requiredByExisting.entrySet()) {
                links.put(e.getKey(), Set.copyOf(e.getValue()));
            }
            return new InstallPlan(ordered, List.copyOf(enable), links, notes);
        }

        private void visit(Draft draft, Set<String> visited, List<PlannedInstall> out) {
            if (!visited.add(draft.projectId)) {
                return;
            }
            for (String dep : draft.dependsOn) {
                Draft child = planned.get(dep);
                if (child != null) {
                    visit(child, visited, out);
                }
            }
            out.add(draft.toPlanned());
        }
    }
}
