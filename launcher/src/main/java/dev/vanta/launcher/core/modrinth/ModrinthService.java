package dev.vanta.launcher.core.modrinth;

import dev.vanta.launcher.core.install.FabricApiService;
import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.Checksum;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.net.DownloadResult;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.net.HttpStatusException;
import dev.vanta.launcher.core.paths.LauncherPaths;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Installs Modrinth content into the VANTA instance: mods into {@code mods/}, Iris shader packs into
 * {@code shaderpacks/}, resource packs into {@code resourcepacks/}, tracked in {@code config/vanta/modrinth.json}
 * ({@link ModrinthIndex}).
 *
 * <ol>
 *   <li>{@link #resolve}: for each requested project the newest version for Minecraft {@code gameVersion} and the
 *       content type's loader is chosen (stable releases before betas before alphas, then by publication date), then
 *       its <em>required</em> dependencies, recursively. Fabric API is skipped (VANTA installs it from the Fabric
 *       Maven). A dependency pinned to an exact version ({@code version_id}) gets that version when it is compatible,
 *       also when the same project was requested directly (Iris names the Sodium build it needs). Versions are
 *       resolved at install time; nothing is hard-coded.</li>
 *   <li>{@link #apply}: every file is downloaded only from the URL Modrinth returned and verified against the
 *       SHA-512 (and size) Modrinth returned; an existing file that already matches is not downloaded again. A newer
 *       version of an installed project replaces the old file. A disabled project stays disabled. A mod is not
 *       installed when an untracked jar in {@code mods/} already provides the same Fabric mod id (Fabric would refuse
 *       to start with two copies), and when the verified jar is built for an older Minecraft ({@link ModJarCheck}: it
 *       would stop Minecraft while starting). Such a download is deleted again (unless the file was there before), not
 *       tracked and reported as skipped; an update then keeps the version that is installed.</li>
 * </ol>
 *
 * <p>Every operation that changes {@code modrinth.json} ({@link #apply}, {@link #setEnabled}, {@link #remove},
 * {@link #updateAll}) runs under one lock per index file, so operations that overlap (a mod is removed while another
 * installs, "Update all" next to an install) wait for each other and each one starts from the file the previous one
 * wrote; no stale in-memory copy is ever saved over another operation's change.</p>
 */
public final class ModrinthService {

    private static final Logger LOG = LauncherLog.get("Modrinth");
    private static final int MAX_ITEMS = 64;
    /** One lock per {@code modrinth.json} (per game folder); see the class description. */
    private static final ConcurrentHashMap<Path, ReentrantLock> INDEX_LOCKS = new ConcurrentHashMap<>();

    private final ModrinthApi api;
    private final Downloader downloader;
    private final LauncherPaths paths;
    private final Clock clock;
    private final String gameVersion;

    /**
     * @param api         API client
     * @param downloader  verified downloader
     * @param paths       launcher paths (the instance)
     * @param clock       clock for {@code installedAt}
     * @param gameVersion Minecraft version of the instance
     */
    public ModrinthService(final ModrinthApi api, final Downloader downloader, final LauncherPaths paths, final Clock clock,
                           final String gameVersion) {
        this.api = Objects.requireNonNull(api, "api");
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.paths = Objects.requireNonNull(paths, "paths");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.gameVersion = Objects.requireNonNull(gameVersion, "gameVersion");
    }

    /** @return the API client */
    public ModrinthApi api() {
        return api;
    }

    /** @return the Minecraft version content is resolved for */
    public String gameVersion() {
        return gameVersion;
    }

    // ---------------------------------------------------------------- model

    /**
     * A project to install.
     *
     * @param idOrSlug project id or slug
     * @param type     content type
     */
    public record Root(String idOrSlug, ContentType type) {

        public Root {
            Objects.requireNonNull(idOrSlug, "idOrSlug");
            Objects.requireNonNull(type, "type");
        }
    }

    /**
     * A resolved project version.
     *
     * @param projectId  project id
     * @param slug       slug
     * @param title      title
     * @param type       content type
     * @param version    chosen version
     * @param file       the file that is installed
     * @param requiredBy project ids that required it (empty for a requested project)
     */
    public record ResolvedItem(String projectId, String slug, String title, ContentType type, ModrinthModels.Version version,
                               ModrinthModels.VersionFile file, List<String> requiredBy) {

        public ResolvedItem {
            requiredBy = List.copyOf(requiredBy);
        }

        /** @return {@code <folder>/<file name>} as stored in {@code modrinth.json} */
        public String relativeFile() {
            return type.folder() + "/" + file.filename();
        }

        /** @return e.g. {@code Sodium mc1.21.11-0.8.14-fabric} */
        public String label() {
            return title + " " + version.versionNumber();
        }
    }

    /**
     * Outcome of {@link #resolve}.
     *
     * @param items    projects to install, requested ones first
     * @param warnings what could not be resolved (empty when everything was found)
     */
    public record Resolution(List<ResolvedItem> items, List<String> warnings) {

        public Resolution {
            items = List.copyOf(items);
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * One installed item of {@link #apply}.
     *
     * @param item       resolved item
     * @param path       the file on disk
     * @param downloaded whether it was downloaded (false: an identical verified file was already there)
     * @param replaced   older files of the same project that were removed
     */
    public record Applied(ResolvedItem item, Path path, boolean downloaded, List<Path> replaced) {

        public Applied {
            replaced = List.copyOf(replaced);
        }
    }

    /**
     * Outcome of {@link #apply}.
     *
     * @param applied  installed items
     * @param warnings items that were skipped and why
     */
    public record ApplyResult(List<Applied> applied, List<String> warnings) {

        public ApplyResult {
            applied = List.copyOf(applied);
            warnings = List.copyOf(warnings);
        }

        /** @return number of files that were downloaded */
        public long downloadedCount() {
            return applied.stream().filter(Applied::downloaded).count();
        }
    }

    /**
     * Content found in the instance.
     *
     * @param type       content type
     * @param title      title (the file name for untracked files)
     * @param version    version number (empty for untracked files)
     * @param path       the file on disk (with {@code .disabled} when disabled)
     * @param enabled    whether it loads
     * @param tracked    whether {@code modrinth.json} tracks it (installed from Modrinth by VANTA)
     * @param managed    whether it is Fabric API or the VANTA Client (managed by the launcher; not changeable here)
     * @param projectId  Modrinth project id (empty for untracked files)
     * @param requiredBy titles of tracked projects that need it
     * @param missing    tracked but the file is gone
     */
    public record InstalledContent(ContentType type, String title, String version, Path path, boolean enabled, boolean tracked,
                                   boolean managed, String projectId, List<String> requiredBy, boolean missing) {

        public InstalledContent {
            requiredBy = List.copyOf(requiredBy);
        }

        /** @return the file name without {@code .disabled} */
        public String fileName() {
            final String n = path.getFileName().toString();
            return n.endsWith(ModrinthIndex.DISABLED_SUFFIX) ? n.substring(0, n.length() - ModrinthIndex.DISABLED_SUFFIX.length()) : n;
        }
    }

    // ---------------------------------------------------------------- search

    /**
     * @param type   content type
     * @param query  text (blank: most downloaded)
     * @param offset offset
     * @return one page of results for this instance's Minecraft version and loader
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public ModrinthModels.SearchPage search(final ContentType type, final String query, final int offset)
        throws IOException, InterruptedException {
        return api.search(type, query, gameVersion, offset, ModrinthApi.PAGE_SIZE);
    }

    // ---------------------------------------------------------------- resolve

    /** Why Fabric API is never installed from the browser: the launcher installs the version the VANTA Client needs. */
    static final String FABRIC_API_MANAGED = "Fabric API is installed and kept up to date by the launcher";

    /**
     * @param idOrSlug Modrinth project id or slug
     * @return whether it names Fabric API, which the launcher manages itself
     */
    public static boolean isFabricApi(final String idOrSlug) {
        return ModrinthApi.FABRIC_API_PROJECT_ID.equals(idOrSlug) || "fabric-api".equals(idOrSlug);
    }

    /**
     * Resolves projects and their required dependencies (see the class description).
     *
     * @param roots    requested projects
     * @param tolerant when true a project that cannot be resolved (unknown, no compatible version, Modrinth unreachable)
     *                 becomes a warning and the others continue; when false the first failure is thrown
     * @param token    cancellation
     * @return resolution
     * @throws IOException          (not tolerant) when a project cannot be resolved
     * @throws InterruptedException when interrupted
     */
    public Resolution resolve(final List<Root> roots, final boolean tolerant, final CancellationToken token)
        throws IOException, InterruptedException {
        final CancellationToken t = token == null ? CancellationToken.NONE : token;
        final Map<String, Working> chosen = new LinkedHashMap<>();
        final List<String> warnings = new ArrayList<>();
        final Set<String> pinned = new HashSet<>();
        final Map<String, ModrinthModels.Project> projectInfo = new HashMap<>();

        for (Root root : roots) {
            t.throwIfCancelled();
            try {
                if (isFabricApi(root.idOrSlug())) {
                    throw new IOException(FABRIC_API_MANAGED);
                }
                final List<ModrinthModels.Version> versions;
                try {
                    versions = api.projectVersions(root.idOrSlug(), root.type(), gameVersion);
                } catch (HttpStatusException e) {
                    if (e.status() == 404) {
                        throw new IOException(root.idOrSlug() + " was not found on Modrinth", e);
                    }
                    throw e;
                }
                final Optional<ModrinthModels.Version> best = newest(versions, root.type());
                if (best.isEmpty()) {
                    throw new IOException(root.idOrSlug() + " has no version for Minecraft " + gameVersion + " (" + root.type().loader() + ") on Modrinth");
                }
                final ModrinthModels.Version v = best.get();
                if (isFabricApi(v.projectId())) {
                    throw new IOException(FABRIC_API_MANAGED);
                }
                final Working w = chosen.computeIfAbsent(v.projectId(), id -> new Working(id, root.type()));
                w.version = v;
                w.requested = true;
                if (!root.idOrSlug().equals(v.projectId())) {
                    w.slug = root.idOrSlug();
                }
            } catch (IOException e) {
                if (!tolerant) {
                    throw e;
                }
                warnings.add(root.idOrSlug() + ": " + describe(e));
            }
        }

        final Deque<String> queue = new ArrayDeque<>(chosen.keySet());
        int guard = 0;
        while (!queue.isEmpty() && guard++ < MAX_ITEMS * 4) {
            t.throwIfCancelled();
            final Working parent = chosen.get(queue.poll());
            if (parent == null || parent.version == null) {
                continue;
            }
            for (ModrinthModels.Dependency dep : parent.version.dependencies()) {
                if (!dep.required()) {
                    continue;
                }
                try {
                    resolveDependency(parent, dep, chosen, pinned, projectInfo, queue, warnings);
                } catch (IOException e) {
                    if (!tolerant) {
                        throw e;
                    }
                    warnings.add(parent.label() + ": a required dependency could not be resolved (" + describe(e) + ")");
                    parent.broken = true;
                }
            }
        }
        // A project whose required dependency is missing would stop Fabric at start-up: leave it out, and everything
        // that needs it.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Working w : chosen.values()) {
                if (w.broken || w.version == null) {
                    continue;
                }
                for (String needed : w.needs) {
                    final Working dep = chosen.get(needed);
                    if (dep == null || dep.broken || dep.version == null) {
                        w.broken = true;
                        changed = true;
                        warnings.add(w.label() + " was left out because a project it requires is not available");
                        break;
                    }
                }
            }
        }

        fillProjectInfo(chosen, projectInfo, warnings);
        final List<ResolvedItem> items = new ArrayList<>();
        for (Working w : chosen.values()) {
            if (w.broken || w.version == null) {
                continue;
            }
            final ModrinthModels.VersionFile file = w.version.primaryFile().orElseThrow();
            if (!isPlainFileName(file.filename())) {
                warnings.add(w.label() + ": Modrinth returned an unusable file name '" + file.filename() + "'");
                continue;
            }
            items.add(new ResolvedItem(w.projectId, w.slug == null ? w.projectId : w.slug, w.title == null ? w.slugOrId() : w.title,
                w.type, w.version, file, new ArrayList<>(w.requiredBy)));
        }
        if (items.size() > MAX_ITEMS) {
            throw new IOException("Modrinth resolution produced more than " + MAX_ITEMS + " projects; refusing to continue");
        }
        return new Resolution(items, warnings);
    }

    private void resolveDependency(final Working parent, final ModrinthModels.Dependency dep, final Map<String, Working> chosen,
                                   final Set<String> pinned, final Map<String, ModrinthModels.Project> projectInfo,
                                   final Deque<String> queue, final List<String> warnings) throws IOException, InterruptedException {
        String depProject = blankToNull(dep.projectId());
        ModrinthModels.Version pin = null;
        if (blankToNull(dep.versionId()) != null) {
            pin = api.version(dep.versionId()).orElse(null);
            if (depProject == null && pin != null) {
                depProject = pin.projectId();
            }
        }
        if (depProject == null) {
            warnings.add(parent.label() + " requires a file that is not on Modrinth"
                + (dep.fileName() == null ? "" : " (" + dep.fileName() + ")") + "; install it yourself");
            return;
        }
        if (ModrinthApi.FABRIC_API_PROJECT_ID.equals(depProject)) {
            return;
        }
        parent.needs.add(depProject);
        final Working existing = chosen.get(depProject);
        if (existing != null) {
            existing.requiredBy.add(parent.projectId);
            if (pin != null && pin.compatible(existing.type, gameVersion) && existing.version != null
                && !pin.id().equals(existing.version.id())) {
                if (pinned.add(depProject)) {
                    LOG.log(Level.INFO, "{0} requires {1} version {2}; using it", new Object[] {parent.label(), existing.slugOrId(), pin.versionNumber()});
                    existing.version = pin;
                    queue.add(depProject);
                } else {
                    warnings.add(parent.label() + " requires another version of " + existing.slugOrId() + " than a different project does");
                }
            }
            return;
        }
        if (chosen.size() >= MAX_ITEMS) {
            throw new IOException("too many dependencies");
        }
        ModrinthModels.Project project = projectInfo.get(depProject);
        if (project == null) {
            project = api.project(depProject);
            projectInfo.put(depProject, project);
        }
        final ContentType type = ContentType.fromId(project.projectType()).orElse(ContentType.MOD);
        ModrinthModels.Version v = pin != null && pin.compatible(type, gameVersion) ? pin : null;
        if (v == null) {
            v = newest(api.projectVersions(depProject, type, gameVersion), type).orElse(null);
        } else {
            pinned.add(depProject);
        }
        final Working w = new Working(depProject, type);
        w.slug = project.slug();
        w.title = project.title();
        w.requiredBy.add(parent.projectId);
        chosen.put(depProject, w);
        if (v == null) {
            w.broken = true;
            warnings.add(parent.label() + " requires " + w.slugOrId() + ", which has no version for Minecraft " + gameVersion
                + " (" + type.loader() + ")");
            return;
        }
        w.version = v;
        queue.add(depProject);
    }

    private void fillProjectInfo(final Map<String, Working> chosen, final Map<String, ModrinthModels.Project> known, final List<String> warnings)
        throws InterruptedException {
        final List<String> missing = new ArrayList<>();
        for (Working w : chosen.values()) {
            final ModrinthModels.Project p = known.get(w.projectId);
            if (p != null) {
                w.apply(p);
            } else if (w.title == null && !w.broken && w.version != null) {
                missing.add(w.projectId);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        try {
            for (ModrinthModels.Project p : api.projects(missing)) {
                final Working w = chosen.get(p.id());
                if (w != null) {
                    w.apply(p);
                }
            }
        } catch (IOException e) {
            // Titles are cosmetic: keep slugs.
            LOG.log(Level.FINE, "Could not load project titles", e);
        }
    }

    /**
     * @param versions versions as returned by the API
     * @param type     content type
     * @return the preferred compatible version: a stable release before a beta before an alpha, then the newest
     */
    public Optional<ModrinthModels.Version> newest(final List<ModrinthModels.Version> versions, final ContentType type) {
        return versions.stream().filter(v -> v.compatible(type, gameVersion)).min(ModrinthModels.Version.PREFERRED_FIRST);
    }

    /** Mutable state of one project during {@link #resolve}. */
    private static final class Working {
        final String projectId;
        final ContentType type;
        final Set<String> requiredBy = new LinkedHashSet<>();
        final Set<String> needs = new LinkedHashSet<>();
        ModrinthModels.Version version;
        String slug;
        String title;
        boolean requested;
        boolean broken;

        Working(final String projectId, final ContentType type) {
            this.projectId = projectId;
            this.type = type;
        }

        void apply(final ModrinthModels.Project p) {
            if (p.slug() != null && !p.slug().isBlank()) {
                slug = p.slug();
            }
            if (p.title() != null && !p.title().isBlank()) {
                title = p.title();
            }
        }

        String slugOrId() {
            return slug == null ? projectId : slug;
        }

        String label() {
            return (title == null ? slugOrId() : title) + (version == null ? "" : " " + version.versionNumber());
        }
    }

    // ---------------------------------------------------------------- apply

    /**
     * Downloads and activates resolved items (see the class description) and records them in {@code modrinth.json}.
     *
     * @param resolution resolution
     * @param downloads  byte progress
     * @param log        one line per notable step (downloaded, verified, replaced, skipped)
     * @param tolerant   when true a failed item (network, checksum) becomes a warning and the others continue
     * @param token      cancellation
     * @return what was installed
     * @throws IOException          (not tolerant) on the first failure
     * @throws InterruptedException when interrupted
     */
    public ApplyResult apply(final Resolution resolution, final DownloadProgressListener downloads, final Consumer<String> log,
                             final boolean tolerant, final CancellationToken token) throws IOException, InterruptedException {
        final CancellationToken t = token == null ? CancellationToken.NONE : token;
        final Consumer<String> out = log == null ? s -> { } : log;
        final DownloadProgressListener listener = downloads == null ? DownloadProgressListener.NONE : downloads;
        final ReentrantLock lock = indexLock();
        lock.lockInterruptibly();
        try {
            final ModrinthIndex index = ModrinthIndex.load(paths.modrinthIndexFile());
            final List<Applied> applied = new ArrayList<>();
            final List<String> warnings = new ArrayList<>(resolution.warnings());
            try {
                for (ResolvedItem item : resolution.items()) {
                    t.throwIfCancelled();
                    try {
                        final ApplyOutcome outcome = applyOne(item, index, listener, out, t);
                        if (outcome.applied() != null) {
                            applied.add(outcome.applied());
                        } else {
                            warnings.add(item.label() + " was not installed: " + outcome.skipped());
                        }
                    } catch (CancellationException | InterruptedException e) {
                        throw e;
                    } catch (IOException | RuntimeException e) {
                        if (!tolerant) {
                            throw e instanceof IOException io ? io : new IOException(describe(e), e);
                        }
                        warnings.add(item.label() + " was not installed: " + describe(e));
                    }
                }
            } catch (IOException | RuntimeException | InterruptedException e) {
                // What was installed before the failure is recorded as far as possible; the failure itself is reported.
                saveAfterFailure(index, e);
                throw e;
            }
            // The files are in place: a failed index write must not throw the applied list away (tolerant callers, the
            // performance pack and "Update all", get a warning and the real counts instead).
            try {
                index.save();
            } catch (IOException e) {
                if (!tolerant) {
                    throw e;
                }
                LOG.log(Level.WARNING, "modrinth.json could not be written; the installed files are in place but untracked", e);
                warnings.add("modrinth.json could not be written (" + describeFileError(e)
                    + "); the files are in place but VANTA does not track them");
            }
            return new ApplyResult(applied, warnings);
        } finally {
            lock.unlock();
        }
    }

    /** @return the lock of this instance's {@code modrinth.json}: one per game folder, shared by every service instance */
    private ReentrantLock indexLock() {
        return INDEX_LOCKS.computeIfAbsent(paths.modrinthIndexFile().toAbsolutePath().normalize(), p -> new ReentrantLock());
    }

    /**
     * Takes the index lock for an operation whose contract has no {@link InterruptedException}.
     *
     * @return the held lock
     * @throws IOException when interrupted while waiting (the interrupt flag is set again)
     */
    private ReentrantLock lockIndex() throws IOException {
        final ReentrantLock lock = indexLock();
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for another Modrinth operation to finish", e);
        }
        return lock;
    }

    private static void saveAfterFailure(final ModrinthIndex index, final Throwable cause) {
        try {
            index.save();
        } catch (IOException | RuntimeException e) {
            cause.addSuppressed(e);
        }
    }

    /** @return a file error with its kind; {@link java.nio.file.FileSystemException#getMessage()} is often only the path */
    private static String describeFileError(final IOException e) {
        if (e instanceof java.nio.file.FileSystemException fs) {
            final StringBuilder b = new StringBuilder(e.getClass().getSimpleName());
            if (fs.getFile() != null) {
                b.append(": ").append(fs.getFile());
            }
            if (fs.getReason() != null && !fs.getReason().isBlank()) {
                b.append(" (").append(fs.getReason()).append(')');
            }
            return b.toString();
        }
        return describe(e);
    }

    /** Why a downloaded mod is not installed: a jar in {@code mods/} already provides the same mod. */
    static final String SKIPPED_DUPLICATE = "a jar in mods/ already provides the same mod";

    /**
     * What {@link #applyOne} did: exactly one of the two is set.
     *
     * @param applied the installed item
     * @param skipped why the verified download was not installed (the file is gone again unless it was there before)
     */
    private record ApplyOutcome(Applied applied, String skipped) {
    }

    private ApplyOutcome applyOne(final ResolvedItem item, final ModrinthIndex index, final DownloadProgressListener listener,
                                  final Consumer<String> log, final CancellationToken token) throws IOException, InterruptedException {
        final URI url = URI.create(item.file().url());
        if (!"https".equalsIgnoreCase(url.getScheme()) && !"http".equalsIgnoreCase(url.getScheme())) {
            throw new IOException("Modrinth returned a download URL that is not http(s): " + url);
        }
        final Optional<ModrinthIndex.Entry> existing = index.byProjectId(item.projectId());
        final boolean enabled = existing.map(ModrinthIndex.Entry::enabled).orElse(true);
        final Path folder = paths.instanceDir().resolve(item.type().folder());
        Files.createDirectories(folder);
        final String name = item.file().filename();
        final Path target = folder.resolve(enabled ? name : name + ModrinthIndex.DISABLED_SUFFIX);
        final boolean wasPresent = Files.exists(target);
        final DownloadRequest request = new DownloadRequest(url, target, item.file().size() > 0 ? item.file().size() : -1L,
            Checksum.sha512(item.file().sha512()), item.label() + " (" + name + ")");
        final DownloadResult result = downloader.download(request, listener, token);
        if (item.type() == ContentType.MOD) {
            // Verified, but would it start? A mod built for an older Minecraft stops 1.21.11 while starting: it is not
            // installed (an update keeps the version that is installed now).
            final ModJarCheck.Result check = ModJarCheck.check(target);
            if (check.flagged()) {
                if (!wasPresent) {
                    Files.deleteIfExists(target);
                }
                LOG.log(Level.INFO, "{0} was not installed: {1} ({2} in {3} uses the constructor {4})", new Object[] {item.label(),
                    ModJarCheck.REASON, check.className(), name, check.descriptor()});
                log.accept("Skipped " + item.label() + ": " + ModJarCheck.REASON);
                return new ApplyOutcome(null, ModJarCheck.REASON);
            }
        }
        if (item.type() == ContentType.MOD && enabled && existing.isEmpty()) {
            final Optional<Path> duplicate = untrackedDuplicate(target, index);
            if (duplicate.isPresent()) {
                if (!wasPresent) {
                    Files.deleteIfExists(target);
                }
                log.accept("Skipped " + item.label() + ": " + duplicate.get().getFileName() + " in mods/ already provides this mod");
                return new ApplyOutcome(null, SKIPPED_DUPLICATE);
            }
        }
        final List<Path> replaced = new ArrayList<>();
        if (existing.isPresent() && !existing.get().file().equals(item.relativeFile())) {
            final Path oldPlain = paths.instanceDir().resolve(existing.get().file());
            for (Path old : List.of(oldPlain, oldPlain.resolveSibling(oldPlain.getFileName() + ModrinthIndex.DISABLED_SUFFIX))) {
                if (!old.equals(target) && Files.deleteIfExists(old)) {
                    replaced.add(old);
                    log.accept("Removed the older version " + old.getFileName());
                }
            }
        }
        final ModrinthIndex.Entry entry = index.upsert(item.projectId());
        final boolean changed = existing.isEmpty() || !existing.get().versionId().equals(item.version().id())
            || existing.get().installedAt().isEmpty();
        final Set<String> requiredBy = new LinkedHashSet<>(existing.map(ModrinthIndex.Entry::requiredBy).orElse(List.of()));
        requiredBy.addAll(item.requiredBy());
        entry.slug(item.slug()).title(item.title()).versionId(item.version().id()).versionNumber(item.version().versionNumber())
            .type(item.type()).file(item.relativeFile()).sha512(item.file().sha512()).enabled(enabled).requiredBy(requiredBy);
        if (changed) {
            entry.installedAt(Instant.now(clock).toString());
        }
        log.accept((result.skipped() ? "Verified " : "Downloaded ") + item.label() + " (" + name + ")");
        return new ApplyOutcome(new Applied(item, target, !result.skipped(), replaced), null);
    }

    /**
     * @param newJar a mod jar that was just installed
     * @param index  the index (tracked files are not duplicates)
     * @return an enabled jar in {@code mods/} that VANTA does not track and that declares the same Fabric mod id
     */
    private Optional<Path> untrackedDuplicate(final Path newJar, final ModrinthIndex index) throws IOException {
        final Optional<String> id = fabricModId(newJar);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        final Set<String> tracked = new HashSet<>();
        for (ModrinthIndex.Entry e : index.entries()) {
            tracked.add(e.fileName());
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(paths.modsDir(), "*.jar")) {
            for (Path p : stream) {
                final String n = p.getFileName().toString();
                if (p.equals(newJar) || tracked.contains(n) || isManaged(n)) {
                    continue;
                }
                if (fabricModId(p).filter(id.get()::equals).isPresent()) {
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * @param jar a mod jar
     * @return the {@code id} in its {@code fabric.mod.json}
     */
    static Optional<String> fabricModId(final Path jar) {
        return FabricModInfo.read(jar).map(FabricModInfo::id);
    }

    // ---------------------------------------------------------------- installed content

    /**
     * Everything in {@code mods/}, {@code shaderpacks/} and {@code resourcepacks/}: the projects {@code modrinth.json}
     * tracks first, then the other files (installed by hand or by another tool; folders are not listed).
     *
     * @return content
     * @throws IOException when a folder cannot be read
     */
    public List<InstalledContent> installed() throws IOException {
        final ModrinthIndex index = ModrinthIndex.load(paths.modrinthIndexFile());
        final Map<String, String> titles = new HashMap<>();
        for (ModrinthIndex.Entry e : index.entries()) {
            titles.put(e.projectId(), e.title());
        }
        final List<InstalledContent> out = new ArrayList<>();
        final Set<Path> seen = new HashSet<>();
        for (ModrinthIndex.Entry e : index.entries()) {
            final Optional<ContentType> type = e.type();
            if (type.isEmpty() || e.file().isEmpty() || !isPlainFileName(e.fileName())) {
                continue;
            }
            final Path path = e.path(paths.instanceDir());
            final Path plain = paths.instanceDir().resolve(e.file());
            seen.add(plain);
            seen.add(plain.resolveSibling(plain.getFileName() + ModrinthIndex.DISABLED_SUFFIX));
            final boolean disabledOnDisk = path.getFileName().toString().endsWith(ModrinthIndex.DISABLED_SUFFIX);
            out.add(new InstalledContent(type.get(), e.title(), e.versionNumber(), path, !disabledOnDisk, true, false, e.projectId(),
                requiredByTitles(index, e.projectId(), titles), !Files.exists(path)));
        }
        for (ContentType type : ContentType.values()) {
            final Path folder = paths.instanceDir().resolve(type.folder());
            if (!Files.isDirectory(folder)) {
                continue;
            }
            final List<Path> files = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
                for (Path p : stream) {
                    if (Files.isRegularFile(p) && !seen.contains(p) && looksLike(type, p.getFileName().toString())) {
                        files.add(p);
                    }
                }
            }
            files.sort(null);
            for (Path p : files) {
                final String n = p.getFileName().toString();
                final boolean disabled = n.endsWith(ModrinthIndex.DISABLED_SUFFIX);
                final String plain = disabled ? n.substring(0, n.length() - ModrinthIndex.DISABLED_SUFFIX.length()) : n;
                out.add(new InstalledContent(type, plain, "", p, !disabled, false, type == ContentType.MOD && isManaged(plain), "",
                    List.of(), false));
            }
        }
        return out;
    }

    private static List<String> requiredByTitles(final ModrinthIndex index, final String projectId, final Map<String, String> titles) {
        final List<String> out = new ArrayList<>();
        index.byProjectId(projectId).ifPresent(e -> e.requiredBy().forEach(id -> {
            if (titles.containsKey(id)) {
                out.add(titles.get(id));
            }
        }));
        return out;
    }

    private static boolean looksLike(final ContentType type, final String name) {
        final String n = name.toLowerCase(Locale.ROOT);
        final String plain = n.endsWith(ModrinthIndex.DISABLED_SUFFIX) ? n.substring(0, n.length() - ModrinthIndex.DISABLED_SUFFIX.length()) : n;
        return type == ContentType.MOD ? plain.endsWith(".jar") : plain.endsWith(".zip");
    }

    /**
     * @param fileName file name in {@code mods/}
     * @return whether the launcher manages it (Fabric API, VANTA Client)
     */
    public static boolean isManaged(final String fileName) {
        return fileName.startsWith(FabricApiService.ARTIFACT + "-") || fileName.startsWith(VantaClientService.JAR_PREFIX);
    }

    /**
     * Enables or disables content by renaming {@code <file>} to {@code <file>.disabled} and back. Enabling a tracked
     * project also enables the tracked projects it requires; disabling a project another enabled tracked project
     * requires is refused.
     *
     * @param item    content
     * @param enabled new state
     * @throws ContentChangeRefusedException when the change would break another project or concerns Fabric API / VANTA
     * @throws IOException                   on failure
     */
    public void setEnabled(final InstalledContent item, final boolean enabled) throws IOException {
        if (item.managed()) {
            throw new ContentChangeRefusedException(ContentChangeRefusedException.Reason.MANAGED, item.title(), List.of());
        }
        final ReentrantLock lock = lockIndex();
        try {
            final ModrinthIndex index = ModrinthIndex.load(paths.modrinthIndexFile());
            if (!enabled && item.tracked()) {
                final List<String> needers = new ArrayList<>();
                for (ModrinthIndex.Entry e : index.entries()) {
                    if (e.enabled() && index.byProjectId(item.projectId()).map(d -> d.requiredBy().contains(e.projectId())).orElse(false)) {
                        needers.add(e.title());
                    }
                }
                if (!needers.isEmpty()) {
                    throw new ContentChangeRefusedException(ContentChangeRefusedException.Reason.REQUIRED_BY, item.title(), needers);
                }
            }
            rename(item.path(), enabled);
            if (item.tracked()) {
                index.byProjectId(item.projectId()).ifPresent(e -> e.enabled(enabled));
                if (enabled) {
                    for (ModrinthIndex.Entry dep : index.entries()) {
                        if (!dep.enabled() && dep.requiredBy().contains(item.projectId())) {
                            rename(dep.path(paths.instanceDir()), true);
                            dep.enabled(true);
                        }
                    }
                }
                index.save();
            }
        } finally {
            lock.unlock();
        }
    }

    private static void rename(final Path current, final boolean enabled) throws IOException {
        final String n = current.getFileName().toString();
        final boolean disabledNow = n.endsWith(ModrinthIndex.DISABLED_SUFFIX);
        if (disabledNow != enabled) {
            return;
        }
        final Path target = enabled ? current.resolveSibling(n.substring(0, n.length() - ModrinthIndex.DISABLED_SUFFIX.length()))
            : current.resolveSibling(n + ModrinthIndex.DISABLED_SUFFIX);
        if (!Files.exists(current)) {
            return;
        }
        if (Files.exists(target)) {
            throw new IOException(target.getFileName() + " already exists in " + target.getParent());
        }
        Files.move(current, target);
    }

    /**
     * Removes content: deletes its file (enabled or disabled) and its {@code modrinth.json} entry. Removing a project
     * another tracked project requires is refused.
     *
     * @param item content
     * @throws ContentChangeRefusedException when another project needs it, or for Fabric API / VANTA Client
     * @throws IOException                   on failure
     */
    public void remove(final InstalledContent item) throws IOException {
        if (item.managed()) {
            throw new ContentChangeRefusedException(ContentChangeRefusedException.Reason.MANAGED, item.title(), List.of());
        }
        final ReentrantLock lock = lockIndex();
        try {
            final ModrinthIndex index = ModrinthIndex.load(paths.modrinthIndexFile());
            if (item.tracked()) {
                final Optional<ModrinthIndex.Entry> entry = index.byProjectId(item.projectId());
                final List<String> needers = new ArrayList<>();
                if (entry.isPresent()) {
                    for (String id : entry.get().requiredBy()) {
                        index.byProjectId(id).ifPresent(e -> needers.add(e.title()));
                    }
                }
                if (!needers.isEmpty()) {
                    throw new ContentChangeRefusedException(ContentChangeRefusedException.Reason.REQUIRED_BY, item.title(), needers);
                }
            }
            final Path plain = item.path().resolveSibling(item.fileName());
            Files.deleteIfExists(plain);
            Files.deleteIfExists(plain.resolveSibling(plain.getFileName() + ModrinthIndex.DISABLED_SUFFIX));
            if (item.tracked()) {
                index.remove(item.projectId());
                index.save();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Updates every project {@code modrinth.json} tracks to its newest compatible version (and installs new required
     * dependencies). Unchanged projects are only verified.
     *
     * @param downloads byte progress
     * @param log       log lines
     * @param token     cancellation
     * @return outcome; {@link Applied#downloaded()} marks the projects that changed
     * @throws IOException          when Modrinth cannot be reached at all
     * @throws InterruptedException when interrupted
     */
    public ApplyResult updateAll(final DownloadProgressListener downloads, final Consumer<String> log, final CancellationToken token)
        throws IOException, InterruptedException {
        // Held across the resolution as well: an install that finished meanwhile is part of the update, and an install
        // that starts meanwhile waits (the lock is reentrant; apply() takes it again).
        final ReentrantLock lock = indexLock();
        lock.lockInterruptibly();
        try {
            final ModrinthIndex index = ModrinthIndex.load(paths.modrinthIndexFile());
            final List<Root> roots = new ArrayList<>();
            for (ModrinthIndex.Entry e : index.entries()) {
                e.type().ifPresent(type -> roots.add(new Root(e.projectId(), type)));
            }
            if (roots.isEmpty()) {
                return new ApplyResult(List.of(), List.of());
            }
            final Resolution resolution = resolve(roots, true, token);
            if (resolution.items().isEmpty() && !resolution.warnings().isEmpty()) {
                throw new IOException("Modrinth could not be reached or knows none of the installed projects: " + resolution.warnings().get(0));
            }
            return apply(resolution, downloads, log, true, token);
        } finally {
            lock.unlock();
        }
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isPlainFileName(final String name) {
        return name != null && !name.isBlank() && !name.contains("/") && !name.contains("\\") && !name.contains(":")
            && !name.equals(".") && !name.equals("..") && !name.startsWith(".");
    }

    private static String blankToNull(final String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String describe(final Throwable e) {
        final String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
