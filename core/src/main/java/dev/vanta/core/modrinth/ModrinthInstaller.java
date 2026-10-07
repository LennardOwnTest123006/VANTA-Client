package dev.vanta.core.modrinth;

import dev.vanta.core.config.CoreLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Executes an {@link InstallPlan}.
 * <ol>
 *   <li>Every file is downloaded from its Modrinth URL into {@code <folder>/<filename>.vanta-download} (a name Fabric,
 *       Iris and Minecraft ignore) and its SHA-512 is checked against the published value — by the API and again
 *       here. A mismatching file is deleted.</li>
 *   <li>An item is committed only when it and every dependency it has in the same plan succeeded; otherwise its staged
 *       file is deleted and it is reported as failed.</li>
 *   <li>Committing renames the staged file to its real name and records it in {@code modrinth.json}. A file that
 *       already exists under that name is never overwritten: if its SHA-512 equals the expected one it is adopted
 *       (a {@code .jar.disabled} copy is renamed back to {@code .jar}, so the mod really loads after the restart the
 *       result asks for), otherwise the item fails with {@link ModrinthException.Kind#FILE_EXISTS}.</li>
 *   <li>Installed but disabled dependencies are switched back on and {@code requiredBy} links are added.</li>
 * </ol>
 */
public final class ModrinthInstaller {

    /** Progress callback (called on the worker thread). */
    @FunctionalInterface
    public interface Progress {
        /**
         * @param fraction overall progress in [0, 1]
         * @param current  the item being downloaded
         * @param index    its position (0-based)
         * @param count    number of items
         */
        void onProgress(double fraction, PlannedInstall current, int index, int count);
    }

    private final ModrinthApi api;
    private final ModrinthLibrary library;
    private final Clock clock;

    public ModrinthInstaller(ModrinthApi api, ModrinthLibrary library, Clock clock) {
        this.api = Objects.requireNonNull(api, "api");
        this.library = Objects.requireNonNull(library, "library");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Runs the plan. Never throws for a single failed file; see {@link InstallResult#failed()}. */
    public InstallResult execute(InstallPlan plan, Progress progress) {
        List<PlannedInstall> items = plan.installs();
        List<PlanNote> notes = new ArrayList<>(plan.notes());
        Map<String, Path> staged = new LinkedHashMap<>();
        Map<String, Path> disabledCopies = new LinkedHashMap<>();
        Map<String, ModrinthException> errors = new LinkedHashMap<>();
        long total = Math.max(1L, plan.totalBytes());
        long[] done = {0L};
        for (int i = 0; i < items.size(); i++) {
            PlannedInstall item = items.get(i);
            int index = i;
            long before = done[0];
            if (progress != null) {
                progress.onProgress(Math.min(1.0, (double) before / total), item, index, items.size());
            }
            try {
                Path target = target(item);
                if (Files.exists(target) || Files.exists(disabled(target))) {
                    Path present = Files.exists(target) ? target : disabled(target);
                    if (Sha512.matches(present, item.file().sha512())) {
                        // The very file is already there (installed by hand): record it instead of downloading. A
                        // switched-off copy is switched back on when committed: the player asked for the mod, and
                        // an entry reported as installed while the jar stays .disabled would never load.
                        if (!present.equals(target)) {
                            disabledCopies.put(item.projectId(), present);
                        }
                    } else {
                        throw new ModrinthException(ModrinthException.Kind.FILE_EXISTS, item.relativeFile()
                                + " already exists and was not installed by VANTA; it is left untouched");
                    }
                } else {
                    Path stage = target.resolveSibling(target.getFileName() + ModrinthLibrary.STAGING_SUFFIX);
                    Files.deleteIfExists(stage);
                    api.download(item.file(), stage, bytes -> {
                        if (progress != null) {
                            double f = (double) (before + bytes) / total;
                            progress.onProgress(Math.min(1.0, f), item, index, items.size());
                        }
                    });
                    staged.put(item.projectId(), stage);
                    if (!Sha512.matches(stage, item.file().sha512())) {
                        throw new ModrinthException(ModrinthException.Kind.HASH_MISMATCH,
                                item.file().filename() + " does not match its published SHA-512");
                    }
                }
            } catch (ModrinthException e) {
                errors.put(item.projectId(), e);
            } catch (IOException e) {
                errors.put(item.projectId(), new ModrinthException(ModrinthException.Kind.LOCAL_IO,
                        item.relativeFile() + ": " + e.getMessage(), e));
            } catch (RuntimeException e) {
                errors.put(item.projectId(), ModrinthException.wrap(e));
            }
            done[0] = before + Math.max(0L, item.file().size());
        }

        // Decide what can be committed: the item itself and its whole in-plan dependency closure succeeded.
        Map<String, PlannedInstall> byId = new HashMap<>();
        for (PlannedInstall item : items) {
            byId.put(item.projectId(), item);
        }
        Set<String> blocked = new HashSet<>(errors.keySet());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (PlannedInstall item : items) {
                if (!blocked.contains(item.projectId())
                        && item.dependsOn().stream().anyMatch(d -> byId.containsKey(d) && blocked.contains(d))) {
                    blocked.add(item.projectId());
                    changed = true;
                }
            }
        }

        List<PlannedInstall> installed = new ArrayList<>();
        List<PlannedInstall> failed = new ArrayList<>();
        for (PlannedInstall item : items) {
            Path stage = staged.get(item.projectId());
            if (blocked.contains(item.projectId())) {
                deleteQuietly(stage);
                failed.add(item);
                ModrinthException error = errors.get(item.projectId());
                if (error != null) {
                    notes.add(new PlanNote(PlanNote.Kind.DOWNLOAD_FAILED, item.title(), errorText(error)));
                    CoreLog.warn("Modrinth install of {} failed: {}", item.title(), error.getMessage());
                } else {
                    notes.add(new PlanNote(PlanNote.Kind.DEPENDENCY_FAILED, item.title(), ""));
                }
                continue;
            }
            Path disabledCopy = disabledCopies.get(item.projectId());
            if (stage != null || disabledCopy != null) {
                try {
                    Path target = target(item);
                    if (stage != null) {
                        if (Files.exists(target) || Files.exists(disabled(target))) {
                            throw new ModrinthException(ModrinthException.Kind.FILE_EXISTS, item.relativeFile()
                                    + " appeared while downloading");
                        }
                        ModrinthLibrary.move(stage, target);
                    } else {
                        if (Files.exists(target)) {
                            throw new ModrinthException(ModrinthException.Kind.FILE_EXISTS, item.relativeFile()
                                    + " appeared while installing");
                        }
                        ModrinthLibrary.move(disabledCopy, target);
                        CoreLog.info("Switched the hand-disabled {} back on for {}", disabledCopy.getFileName(),
                                item.title());
                    }
                } catch (IOException e) {
                    deleteQuietly(stage);
                    failed.add(item);
                    ModrinthException error = ModrinthException.wrap(e);
                    notes.add(new PlanNote(PlanNote.Kind.DOWNLOAD_FAILED, item.title(), errorText(error)));
                    continue;
                }
            }
            installed.add(item);
        }

        String now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString();
        List<String> enabled = new ArrayList<>();
        for (String projectId : plan.enableExisting()) {
            try {
                library.setEnabled(projectId, true);
                enabled.add(projectId);
            } catch (ModrinthException e) {
                notes.add(new PlanNote(PlanNote.Kind.DOWNLOAD_FAILED, projectId, errorText(e)));
            }
        }
        if (!installed.isEmpty() || !plan.requiredByExisting().isEmpty()) {
            library.update(index -> {
                for (PlannedInstall item : installed) {
                    List<String> parents = new ArrayList<>(item.requiredBy());
                    index.find(item.projectId()).ifPresent(old -> parents.addAll(old.requiredBy()));
                    index.put(new InstalledEntry(item.projectId(), item.slug(), item.title(), item.version().id(),
                            item.version().versionNumber(), item.type().apiName(), item.relativeFile(),
                            item.file().sha512(), true, parents, now, null));
                }
                for (Map.Entry<String, Set<String>> link : plan.requiredByExisting().entrySet()) {
                    index.find(link.getKey()).ifPresent(e -> index.put(e.withRequiredByAdded(link.getValue())));
                }
                return null;
            });
        }
        if (progress != null && !items.isEmpty()) {
            progress.onProgress(1.0, items.get(items.size() - 1), items.size() - 1, items.size());
        }
        for (PlannedInstall item : installed) {
            CoreLog.info("Installed {} {} from Modrinth ({})", item.title(), item.version().versionNumber(),
                    item.relativeFile());
        }
        return new InstallResult(installed, enabled, failed, notes);
    }

    private Path target(PlannedInstall item) throws ModrinthException {
        if (!item.file().hasSafeFilename()) {
            throw new ModrinthException(ModrinthException.Kind.UNSAFE_FILE, "unsafe file name " + item.file().filename());
        }
        if (item.type() == ModrinthProjectType.MOD && !item.file().filename().toLowerCase(java.util.Locale.ROOT)
                .endsWith(".jar")) {
            throw new ModrinthException(ModrinthException.Kind.UNSAFE_FILE, item.file().filename() + " is not a .jar");
        }
        return library.checkedPath(library.directory(item.type()).resolve(item.file().filename()));
    }

    private static Path disabled(Path target) {
        return target.resolveSibling(target.getFileName() + InstalledEntry.DISABLED_SUFFIX);
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            CoreLog.warn(e, "Could not delete {}", path);
        }
    }

    /** Translated short reason for a failure. */
    static String errorText(ModrinthException e) {
        return dev.vanta.core.i18n.Lang.tr(e.kind().langKey());
    }
}
