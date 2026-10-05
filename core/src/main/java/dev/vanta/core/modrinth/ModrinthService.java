package dev.vanta.core.modrinth;

import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.bridge.ModPlatformBridge;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.Notification;
import dev.vanta.core.notifications.NotificationCenter;
import dev.vanta.core.notifications.NotificationKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Asynchronous facade of the Modrinth integration used by the Mods &amp; Shaders screen.
 * <p>
 * Network and file work runs on one background worker (operations never overlap); every callback, every
 * notification and every listener call is delivered on the main (client) thread through the executor given at
 * construction, so screens and the {@link NotificationCenter} are only touched from that thread. Installs show a
 * progress toast and end with a success, warning or error toast; the outcome is also handed to the caller.
 */
public final class ModrinthService implements AutoCloseable {
    private static final int SEARCH_CACHE_SIZE = 48;
    private static final long SEARCH_CACHE_TTL_MS = 5 * 60_000L;
    private static final long MAX_HASHED_JAR_BYTES = 64L * 1024 * 1024;
    private static final long PROGRESS_INTERVAL_NANOS = 100_000_000L;

    private final ModrinthApi api;
    private final ModrinthLibrary library;
    private final ModPlatformBridge platform;
    private final NotificationCenter notifications;
    private final Executor worker;
    private final Executor mainThread;
    private final Clock clock;
    private final ExecutorService ownedWorker;
    private final String gameVersion;
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicLong changes = new AtomicLong();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, CachedSearch> searchCache = new LinkedHashMap<>(16, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedSearch> eldest) {
            return size() > SEARCH_CACHE_SIZE;
        }
    };
    private volatile boolean restartRequired;

    private record CachedSearch(ModrinthSearchResult result, long at) {
    }

    /**
     * @param api           Modrinth access
     * @param library       local index and folders
     * @param platform      loaded mods, Iris screen
     * @param notifications toasts (main thread only)
     * @param worker        background executor (should run one task at a time)
     * @param mainThread    executor that runs tasks on the client thread
     * @param clock         time source
     */
    public ModrinthService(ModrinthApi api, ModrinthLibrary library, ModPlatformBridge platform,
                           NotificationCenter notifications, Executor worker, Executor mainThread, Clock clock) {
        this(api, library, platform, notifications, worker, mainThread, clock, null, ModrinthConstants.GAME_VERSION);
    }

    private ModrinthService(ModrinthApi api, ModrinthLibrary library, ModPlatformBridge platform,
                            NotificationCenter notifications, Executor worker, Executor mainThread, Clock clock,
                            ExecutorService ownedWorker, String gameVersion) {
        this.api = Objects.requireNonNull(api, "api");
        this.library = Objects.requireNonNull(library, "library");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ownedWorker = ownedWorker;
        this.gameVersion = gameVersion;
    }

    /**
     * The production service: HTTPS client for the public API, a daemon worker thread and the index in
     * {@code <gameDir>/config/vanta/modrinth.json}.
     */
    public static ModrinthService create(ModPlatformBridge platform, NotificationCenter notifications,
                                         Executor mainThread, String clientVersion, Clock clock) {
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "VANTA Modrinth");
            t.setDaemon(true);
            return t;
        });
        ModrinthClient client = new ModrinthClient(ModrinthClient.Config.defaults(clientVersion));
        ModrinthLibrary library = new ModrinthLibrary(platform.gameDirectory(), new JsonStore(clock));
        return new ModrinthService(client, library, platform, notifications, worker, mainThread, clock, worker,
                ModrinthConstants.GAME_VERSION);
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    public ModrinthApi api() {
        return api;
    }

    public ModrinthLibrary library() {
        return library;
    }

    public ModPlatformBridge platform() {
        return platform;
    }

    /** The Minecraft version installs target. */
    public String gameVersion() {
        return gameVersion;
    }

    /** True while an install, removal or enable/disable is queued or running (searches do not count). */
    public boolean isBusy() {
        return pending.get() > 0;
    }

    /** True once a mod was installed, removed, enabled or disabled in this session. */
    public boolean restartRequired() {
        return restartRequired;
    }

    /** True when the VANTA launcher started the game and relaunches it after a restart request. */
    public boolean launcherRestartable() {
        return RestartMarker.launcherRestartable();
    }

    /** Everything installed (index + files found on disk). Reads the disk; call on the main thread when needed. */
    public List<LocalItem> installedItems() {
        return library.items();
    }

    /** Project ids in the index. */
    public Set<String> installedProjectIds() {
        Set<String> ids = new HashSet<>();
        for (InstalledEntry entry : library.index().entries()) {
            ids.add(entry.projectId());
        }
        return ids;
    }

    /** Index entry of a project. */
    public Optional<InstalledEntry> installed(String projectId) {
        return library.index().find(projectId);
    }

    /** True when Iris is loaded in this game (shader packs work without a restart). */
    public boolean irisLoaded() {
        return platform.isModLoaded(ModrinthConstants.IRIS_MOD_ID);
    }

    /**
     * Incremented (on the main thread) whenever something installed changed; screens compare it once per tick
     * instead of subscribing, so a screen that is gone never stays referenced.
     */
    public long changeCount() {
        return changes.get();
    }

    /** Called (on the main thread) after anything installed changed. Returns the unsubscribe action. */
    public Runnable addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    // ---- search --------------------------------------------------------------------------------------------------

    /** Searches Modrinth; results are cached for five minutes. Callbacks run on the main thread. */
    public void search(SearchRequest request, Consumer<ModrinthSearchResult> onResult,
                       Consumer<ModrinthException> onError) {
        CachedSearch cached;
        synchronized (searchCache) {
            cached = searchCache.get(request.cacheKey());
        }
        if (cached != null && clock.millis() - cached.at() < SEARCH_CACHE_TTL_MS) {
            mainThread.execute(() -> onResult.accept(cached.result()));
            return;
        }
        submit(() -> {
            ModrinthSearchResult result = api.search(request);
            synchronized (searchCache) {
                searchCache.put(request.cacheKey(), new CachedSearch(result, clock.millis()));
            }
            return result;
        }, onResult, onError, false);
    }

    // ---- install -------------------------------------------------------------------------------------------------

    /**
     * Plans and installs projects with their required dependencies, showing progress and the outcome as toasts.
     *
     * @param requests what to install
     * @param title    toast title, e.g. "Installing Sodium"
     * @param onDone   receives the result on the main thread (may be {@code null})
     */
    public void install(List<InstallRequest> requests, String title, Consumer<InstallResult> onDone) {
        List<InstallRequest> copy = List.copyOf(requests);
        long[] toast = {-1L};
        Optional<Notification> posted = notifications.postProgress(NotificationKind.INFO, title,
                Lang.tr("vanta.mods.progress.resolving"));
        posted.ifPresent(n -> toast[0] = n.id());
        long[] lastPost = {0L};
        submit(() -> {
            InstallState state = currentState();
            InstallPlan plan = new InstallPlanner(api, gameVersion).plan(copy, state);
            ModrinthInstaller installer = new ModrinthInstaller(api, library, clock);
            return installer.execute(plan, (fraction, current, index, count) -> {
                long now = System.nanoTime();
                if (now - lastPost[0] < PROGRESS_INTERVAL_NANOS && fraction < 1.0) {
                    return;
                }
                lastPost[0] = now;
                String body = Lang.tr("vanta.mods.progress.downloading", current.title(), index + 1, count);
                mainThread.execute(() -> {
                    if (toast[0] >= 0) {
                        notifications.updateBody(toast[0], body);
                        notifications.updateProgress(toast[0], Math.min(0.99, fraction));
                    }
                });
            });
        }, result -> {
            finishToast(toast[0]);
            if (result.modsChanged()) {
                restartRequired = true;
            }
            announce(result);
            fireChanged();
            if (onDone != null) {
                onDone.accept(result);
            }
        }, error -> {
            finishToast(toast[0]);
            notifications.post(NotificationKind.ERROR, Lang.tr("vanta.mods.toast.failed.title"),
                    Lang.tr(error.kind().langKey()), 8000);
            fireChanged();
            if (onDone != null) {
                onDone.accept(new InstallResult(List.of(), List.of(), List.of(),
                        List.of(new PlanNote(PlanNote.Kind.ERROR, title, Lang.tr(error.kind().langKey())))));
            }
        });
    }

    /** Installs the selected members of the {@link PerformancePack}. */
    public void installPerformancePack(List<String> slugs, Consumer<InstallResult> onDone) {
        install(PerformancePack.requests(slugs), Lang.tr("vanta.mods.pack.installing"), onDone);
    }

    private void finishToast(long id) {
        if (id >= 0) {
            notifications.updateProgress(id, 1.0);
        }
    }

    /** What is present now: the index, Fabric API, and unknown jars identified on Modrinth by their SHA-512. */
    InstallState currentState() {
        ModrinthIndex index = library.index();
        boolean fabricApi = platform.isModLoaded(ModrinthConstants.FABRIC_API_MOD_ID) || library.hasFabricApiJar();
        Set<String> manual = new HashSet<>();
        List<Path> unknown = library.unknownModJars();
        if (!unknown.isEmpty()) {
            List<String> hashes = new ArrayList<>();
            for (Path jar : unknown) {
                try {
                    if (Files.size(jar) <= MAX_HASHED_JAR_BYTES) {
                        hashes.add(Sha512.hex(jar));
                    }
                } catch (IOException e) {
                    CoreLog.debug("Could not hash {}: {}", jar, e.getMessage());
                }
            }
            try {
                for (ModrinthVersion version : api.versionsByHash(hashes).values()) {
                    manual.add(version.projectId());
                }
            } catch (ModrinthException e) {
                CoreLog.debug("Could not identify installed jars on Modrinth: {}", e.getMessage());
            }
        }
        return InstallState.of(index, manual, fabricApi);
    }

    private void announce(InstallResult result) {
        List<PlannedInstall> installed = result.installed();
        List<PlanNote> problems = result.problems();
        if (installed.isEmpty() && result.enabled().isEmpty()) {
            if (problems.isEmpty()) {
                notifications.post(NotificationKind.INFO, Lang.tr("vanta.mods.toast.nothing.title"),
                        Lang.tr("vanta.mods.toast.nothing.body"));
            } else {
                notifications.post(NotificationKind.ERROR, Lang.tr("vanta.mods.toast.failed.title"),
                        summary(problems), 8000);
            }
            return;
        }
        String names = names(installed, result.enabled());
        String hint = result.modsChanged() ? Lang.tr("vanta.mods.toast.restart_hint")
                : !result.installed(ModrinthProjectType.SHADER).isEmpty() ? Lang.tr("vanta.mods.toast.shader_hint")
                : Lang.tr("vanta.mods.toast.pack_hint");
        if (problems.isEmpty()) {
            notifications.post(NotificationKind.SUCCESS, Lang.tr("vanta.mods.toast.installed.title"),
                    names + " " + hint, 6000);
        } else {
            notifications.post(NotificationKind.WARNING, Lang.tr("vanta.mods.toast.partial.title"),
                    names + " " + summary(problems), 8000);
        }
    }

    private String names(List<PlannedInstall> installed, List<String> enabled) {
        List<String> titles = new ArrayList<>();
        installed.forEach(i -> titles.add(i.title()));
        for (String id : enabled) {
            titles.add(library.index().find(id).map(InstalledEntry::title).orElse(id));
        }
        if (titles.size() <= 3) {
            return Lang.tr("vanta.mods.toast.names", String.join(", ", titles));
        }
        return Lang.tr("vanta.mods.toast.names_more", String.join(", ", titles.subList(0, 3)), titles.size() - 3);
    }

    private static String summary(List<PlanNote> problems) {
        String first = problems.get(0).message();
        return problems.size() == 1 ? first : Lang.tr("vanta.mods.toast.and_more", first, problems.size() - 1);
    }

    // ---- remove / enable -----------------------------------------------------------------------------------------

    /** Deletes a project VANTA installed. */
    public void remove(String projectId, Consumer<InstalledEntry> onDone) {
        submit(() -> library.remove(projectId), entry -> {
            if (entry.projectType().map(ModrinthProjectType::needsRestart).orElse(true)) {
                restartRequired = true;
            }
            notifications.post(NotificationKind.SUCCESS, Lang.tr("vanta.mods.toast.removed.title"),
                    Lang.tr("vanta.mods.toast.removed.body", entry.title()));
            fireChanged();
            if (onDone != null) {
                onDone.accept(entry);
            }
        }, this::failed);
    }

    /** Drops an index entry whose file no longer exists. */
    public void forget(String projectId, Runnable onDone) {
        submit(() -> {
            library.forget(projectId);
            return Boolean.TRUE;
        }, ok -> {
            fireChanged();
            if (onDone != null) {
                onDone.run();
            }
        }, this::failed);
    }

    /** Enables or disables a mod (rename {@code .jar} ↔ {@code .jar.disabled}). */
    public void setEnabled(String projectId, boolean enabled, Consumer<InstalledEntry> onDone) {
        submit(() -> library.setEnabled(projectId, enabled), entry -> {
            restartRequired = true;
            notifications.post(NotificationKind.INFO, Lang.tr(enabled ? "vanta.mods.toast.enabled.title"
                            : "vanta.mods.toast.disabled.title"),
                    Lang.tr("vanta.mods.toast.toggled.body", entry.title()));
            fireChanged();
            if (onDone != null) {
                onDone.accept(entry);
            }
        }, this::failed);
    }

    private void failed(ModrinthException error) {
        notifications.post(NotificationKind.ERROR, Lang.tr("vanta.mods.toast.action_failed.title"),
                Lang.tr(error.kind().langKey()), 8000);
        fireChanged();
    }

    // ---- restart -------------------------------------------------------------------------------------------------

    /**
     * Quits the game so changed mods load. When the VANTA launcher started the game, the restart marker is written
     * first so the launcher starts the game again.
     *
     * @return true when the marker was written (the launcher will relaunch)
     */
    public boolean restartOrQuit(GameBridge game) {
        boolean marked = false;
        if (launcherRestartable()) {
            try {
                Path file = RestartMarker.write(library.gameDir(), clock);
                CoreLog.info("Restart requested ({}); quitting so the VANTA launcher starts the game again", file);
                marked = true;
            } catch (IOException e) {
                CoreLog.warn(e, "Could not write the restart marker; quitting without a restart");
            }
        }
        game.quitGame();
        return marked;
    }

    // ---- plumbing ------------------------------------------------------------------------------------------------

    /** Work that may fail with a {@link ModrinthException}. */
    @FunctionalInterface
    private interface Work<T> {
        T run() throws ModrinthException;
    }

    private <T> void submit(Work<T> work, Consumer<T> onResult, Consumer<ModrinthException> onError) {
        submit(work, onResult, onError, true);
    }

    private <T> void submit(Work<T> work, Consumer<T> onResult, Consumer<ModrinthException> onError,
                            boolean mutating) {
        if (mutating) {
            pending.incrementAndGet();
        }
        try {
            worker.execute(() -> {
                T result = null;
                ModrinthException error = null;
                try {
                    result = work.run();
                } catch (ModrinthException e) {
                    error = e;
                } catch (RuntimeException e) {
                    CoreLog.error(e, "Modrinth task failed");
                    error = ModrinthException.wrap(e);
                }
                T finalResult = result;
                ModrinthException finalError = error;
                mainThread.execute(() -> {
                    if (mutating) {
                        pending.decrementAndGet();
                    }
                    if (finalError != null) {
                        onError.accept(finalError);
                    } else {
                        onResult.accept(finalResult);
                    }
                });
            });
        } catch (RuntimeException e) {
            if (mutating) {
                pending.decrementAndGet();
            }
            onError.accept(ModrinthException.wrap(e));
        }
    }

    private void fireChanged() {
        changes.incrementAndGet();
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                CoreLog.warn(e, "Modrinth listener failed");
            }
        }
    }

    /** Stops the worker thread of a service made by {@link #create}. */
    @Override
    public void close() {
        if (ownedWorker != null) {
            ownedWorker.shutdownNow();
        }
    }
}
