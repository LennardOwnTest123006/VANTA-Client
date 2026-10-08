package dev.vanta.core.ai;

import dev.vanta.core.config.CoreLog;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.Notification;
import dev.vanta.core.notifications.NotificationCenter;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The Local AI as the rest of VANTA sees it: where it is installed ({@link LocalAiPaths}), what state it is in
 * ({@link LocalAiStatus}), install / verify / remove in the background with progress, and the {@link LocalAiRuntime}
 * started on demand for {@link #chat}. Every public method is render-thread only; background work runs on the worker
 * executor and every callback, listener call and toast update comes back through the main-thread executor.
 */
public final class LocalAiService implements ChatBackend, AutoCloseable {
    /** Observer of status and install progress (called on the main thread). */
    public interface Listener {
        /** Status changed (install state or server state). */
        default void onStatusChanged(LocalAiStatus status, String reason) {
        }

        /** Install or verification progress. */
        default void onProgress(InstallStep step, long bytesDone, long bytesTotal, double bytesPerSecond) {
        }

        /** An install, verification or removal finished; {@code error} is set when it failed. */
        default void onFinished(Optional<LocalAiException> error) {
        }
    }

    /**
     * Everything the service needs from the outside world; {@link #system(String, int)} builds the real set, tests
     * build their own.
     *
     * @param manifestProblem why {@code manifest} is empty (shown as the FAILED reason)
     */
    public record Dependencies(Optional<Platform> platform, Optional<LocalAiManifest> manifest, String manifestProblem,
                               Supplier<HttpClient> http, LocalAiRuntime.ProcessFactory processFactory,
                               IntSupplier ports, LocalAiRuntime.Sleeper sleeper, Executor worker,
                               int availableProcessors, boolean shutdownHook,
                               LocalAiInstaller.Config installerConfig) {
        public Dependencies {
            Objects.requireNonNull(platform, "platform");
            Objects.requireNonNull(manifest, "manifest");
            Objects.requireNonNull(manifestProblem, "manifestProblem");
            Objects.requireNonNull(http, "http");
            Objects.requireNonNull(processFactory, "processFactory");
            Objects.requireNonNull(ports, "ports");
            Objects.requireNonNull(sleeper, "sleeper");
            Objects.requireNonNull(worker, "worker");
            Objects.requireNonNull(installerConfig, "installerConfig");
        }

        /** The real dependencies: detected platform, bundled manifest, system HTTP client and processes. */
        public static Dependencies system(String clientVersion, int availableProcessors) {
            Optional<LocalAiManifest> manifest;
            String problem = "";
            try {
                manifest = Optional.of(LocalAiManifest.bundled(LocalAiManifest.templateAllowedByProperty()));
            } catch (LocalAiException e) {
                manifest = Optional.empty();
                problem = e.getMessage();
                CoreLog.warn("Local AI unavailable: {}", problem);
            }
            LocalAiInstaller.Config config = LocalAiInstaller.Config.defaults(clientVersion);
            return new Dependencies(Platform.current(), manifest, problem, lazyHttpClient(config),
                    LocalAiRuntime.ProcessFactory.system(), LocalAiRuntime::freePort, LocalAiRuntime.Sleeper.system(),
                    daemonWorker(), availableProcessors, true, config);
        }

        /** Creates the JDK HTTP client (and its selector thread) on first use only. */
        public static Supplier<HttpClient> lazyHttpClient(LocalAiInstaller.Config config) {
            return new Supplier<>() {
                private HttpClient client;

                @Override
                public synchronized HttpClient get() {
                    if (client == null) {
                        client = LocalAiInstaller.defaultHttpClient(config);
                    }
                    return client;
                }
            };
        }

        /** A single daemon worker thread named "VANTA Local AI". */
        public static ExecutorService daemonWorker() {
            return Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "VANTA Local AI");
                t.setDaemon(true);
                return t;
            });
        }
    }

    private final VantaPaths vantaPaths;
    private final SettingsStore settings;
    private final NotificationCenter notifications;
    private final Executor mainThread;
    private final Clock clock;
    private final Dependencies deps;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private LocalAiPaths paths;
    private Optional<LocalAiInstaller> installer = Optional.empty();
    private volatile LocalAiStatus installStatus = LocalAiStatus.NOT_INSTALLED;
    private volatile String installReason = "";
    private Optional<LocalAiInstalled> installed = Optional.empty();
    private LocalAiRuntime runtime;
    private Runnable runtimeUnsubscribe;
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private boolean busy;
    private boolean installing;
    private volatile boolean closed;

    public LocalAiService(VantaPaths vantaPaths, SettingsStore settings, NotificationCenter notifications,
                          Executor mainThread, Clock clock, Dependencies deps) {
        this.vantaPaths = Objects.requireNonNull(vantaPaths, "vantaPaths");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.deps = Objects.requireNonNull(deps, "deps");
        this.paths = LocalAiPaths.clientManaged(vantaPaths.localAiDir());
    }

    // ---- lifecycle -----------------------------------------------------------------------------------------------

    /** Resolves the install directory (note or default) and runs the quick check. Cheap; render thread. */
    public void load() {
        paths = LocalAiPaths.resolve(vantaPaths);
        installer = Optional.empty();
        if (deps.platform().isEmpty()) {
            setInstallStatus(LocalAiStatus.UNSUPPORTED_PLATFORM,
                    Lang.tr("vanta.localai.reason.unsupported", Platform.systemLabel()));
            return;
        }
        if (deps.manifest().isEmpty()) {
            setInstallStatus(LocalAiStatus.FAILED, deps.manifestProblem());
            return;
        }
        LocalAiInstaller inst = new LocalAiInstaller(deps.manifest().get(), paths, deps.platform().get(), deps.http(),
                deps.installerConfig(), clock);
        installer = Optional.of(inst);
        refresh();
    }

    /** Re-runs the quick check (after an external change). */
    public void refresh() {
        if (installer.isEmpty()) {
            return;
        }
        LocalAiInstaller inst = installer.get();
        LocalAiStatus status = inst.quickCheck();
        installed = status.isInstalled() ? inst.readInstalled() : Optional.empty();
        if (status == LocalAiStatus.INSTALLED && runtime != null && runtime.isRunning()) {
            return;
        }
        setInstallStatus(status, status == LocalAiStatus.INSTALLED && paths.isLauncherManaged()
                ? Lang.tr("vanta.localai.reason.launcher_managed") : "");
    }

    /** Once per client tick: idle timeout and live settings. */
    public void tick() {
        LocalAiRuntime r = runtime;
        if (r == null) {
            return;
        }
        LocalAiRuntime.Config current = r.config();
        Duration idle = Duration.ofMinutes(settings.get(VantaSettings.NEXUS_IDLE_TIMEOUT_MINUTES));
        boolean keep = settings.get(VantaSettings.NEXUS_KEEP_RUNNING);
        int threads = configuredThreads();
        if (!current.idleTimeout().equals(idle) || current.keepRunning() != keep || current.threads() != threads) {
            r.setConfig(current.withIdle(idle, keep).withThreads(threads));
        }
        r.tick(clock.millis());
    }

    /** Stops the server and the worker. */
    @Override
    public void close() {
        closed = true;
        if (runtime != null) {
            runtime.close();
        }
        if (deps.worker() instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    /** The combined status: the server's while it runs, else the install state. */
    public LocalAiStatus status() {
        LocalAiRuntime r = runtime;
        if (r != null && (r.isRunning() || r.status() == LocalAiStatus.FAILED)) {
            return r.status();
        }
        return installStatus;
    }

    /** Why the status is FAILED or UNSUPPORTED_PLATFORM, or "installed by the VANTA Launcher"; empty otherwise. */
    public String statusReason() {
        LocalAiRuntime r = runtime;
        if (r != null && r.status() == LocalAiStatus.FAILED) {
            return r.statusReason();
        }
        return installReason;
    }

    public Optional<LocalAiManifest> manifest() {
        return deps.manifest();
    }

    public Optional<Platform> platform() {
        return deps.platform();
    }

    public LocalAiPaths paths() {
        return paths;
    }

    /** {@code installed.json} when the install is complete. */
    public Optional<LocalAiInstalled> installed() {
        return installed;
    }

    public Optional<LocalAiRuntime> runtime() {
        return Optional.ofNullable(runtime);
    }

    /** True when the note points at a launcher install (Install / Remove hidden). */
    public boolean isLauncherManaged() {
        return paths.isLauncherManaged();
    }

    /** True when llama.cpp has a build for this computer and the manifest is usable. */
    public boolean isSupported() {
        return installer.isPresent();
    }

    /** True when runtime and model passed the quick check. */
    public boolean isInstalled() {
        return installStatus.isInstalled();
    }

    /** True while an install, verification or removal runs. */
    public boolean isBusy() {
        return busy;
    }

    /** True while an install runs. */
    public boolean isInstalling() {
        return installing;
    }

    /** The {@code nexus.enabled} setting. */
    public boolean isEnabled() {
        return settings.get(VantaSettings.NEXUS_ENABLED);
    }

    /** Bytes the install downloads on this platform (0 when unknown). */
    public long downloadBytes() {
        return deps.manifest().isPresent() && deps.platform().isPresent()
                ? deps.manifest().get().downloadBytes(deps.platform().get()) : 0L;
    }

    public Runnable addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    // ---- install / verify / remove ---------------------------------------------------------------------------------

    /**
     * Starts the install in the background (after the player clicked Install). Progress goes to the listeners and a
     * progress toast; the outcome to the listeners and a toast.
     *
     * @return false when nothing was started (unsupported, launcher-managed, already busy)
     */
    public boolean install() {
        if (installer.isEmpty() || busy || closed || paths.isLauncherManaged()) {
            return false;
        }
        LocalAiInstaller inst = installer.get();
        busy = true;
        installing = true;
        cancelRequested.set(false);
        Optional<Notification> toast = notifications.localAiDownload(Lang.tr(InstallStep.CHECKING.langKey()));
        long toastId = toast.map(Notification::id).orElse(-1L);
        long total = downloadBytes();
        ProgressRelay relay = new ProgressRelay(toastId, total);
        deps.worker().execute(() -> {
            LocalAiInstalled result = null;
            LocalAiException failure = null;
            try {
                result = inst.install(relay);
            } catch (LocalAiException e) {
                failure = e;
            } catch (RuntimeException e) {
                failure = new LocalAiException(LocalAiException.Kind.LOCAL_IO, String.valueOf(e.getMessage()), e);
            }
            LocalAiInstalled done = result;
            LocalAiException error = failure;
            mainThread.execute(() -> finishInstall(done, error, toastId));
        });
        return true;
    }

    private void finishInstall(LocalAiInstalled result, LocalAiException error, long toastId) {
        busy = false;
        installing = false;
        if (toastId >= 0) {
            notifications.dismiss(toastId);
        }
        if (error == null && result != null) {
            installed = Optional.of(result);
            setInstallStatus(LocalAiStatus.INSTALLED, "");
            notifications.localAiInstalled();
            fireFinished(Optional.empty());
            return;
        }
        LocalAiException e = error == null ? new LocalAiException(LocalAiException.Kind.LOCAL_IO, "no result") : error;
        refresh();
        if (e.kind() != LocalAiException.Kind.CANCELLED) {
            notifications.localAiFailed(Lang.tr(e.kind().langKey()) + ": " + e.getMessage());
            CoreLog.warn("Local AI install failed: {} ({})", e.getMessage(), e.kind());
        }
        fireFinished(Optional.of(e));
    }

    /** Asks a running install to stop at the next buffer. */
    public void cancelInstall() {
        cancelRequested.set(true);
    }

    /** Re-hashes the installed files in the background. */
    public boolean verify() {
        if (installer.isEmpty() || busy || closed) {
            return false;
        }
        LocalAiInstaller inst = installer.get();
        busy = true;
        cancelRequested.set(false);
        ProgressRelay relay = new ProgressRelay(-1, deps.manifest().map(m -> m.model().size()).orElse(0L));
        deps.worker().execute(() -> {
            LocalAiStatus status = null;
            LocalAiException failure = null;
            try {
                status = inst.verify(relay);
            } catch (LocalAiException e) {
                failure = e;
            } catch (RuntimeException e) {
                failure = new LocalAiException(LocalAiException.Kind.LOCAL_IO, String.valueOf(e.getMessage()), e);
            }
            LocalAiStatus done = status;
            LocalAiException error = failure;
            mainThread.execute(() -> {
                busy = false;
                if (error != null) {
                    notifications.localAiFailed(Lang.tr(error.kind().langKey()) + ": " + error.getMessage());
                    fireFinished(Optional.of(error));
                    return;
                }
                installed = done.isInstalled() ? inst.readInstalled() : Optional.empty();
                setInstallStatus(done, done == LocalAiStatus.INSTALLED && paths.isLauncherManaged()
                        ? Lang.tr("vanta.localai.reason.launcher_managed") : "");
                notifications.localAiVerified(done == LocalAiStatus.INSTALLED);
                fireFinished(Optional.empty());
            });
        });
        return true;
    }

    /** Stops the server and deletes the client-managed install in the background. */
    public boolean remove() {
        if (installer.isEmpty() || busy || closed || paths.isLauncherManaged()) {
            return false;
        }
        LocalAiInstaller inst = installer.get();
        busy = true;
        LocalAiRuntime r = runtime;
        deps.worker().execute(() -> {
            LocalAiException failure = null;
            try {
                if (r != null) {
                    r.destroyProcess();
                }
                inst.remove();
            } catch (LocalAiException e) {
                failure = e;
            } catch (RuntimeException e) {
                failure = new LocalAiException(LocalAiException.Kind.LOCAL_IO, String.valueOf(e.getMessage()), e);
            }
            LocalAiException error = failure;
            mainThread.execute(() -> {
                busy = false;
                dropRuntime();
                refresh();
                if (error != null) {
                    notifications.localAiFailed(Lang.tr(error.kind().langKey()) + ": " + error.getMessage());
                } else {
                    notifications.localAiRemoved();
                }
                fireFinished(Optional.ofNullable(error));
            });
        });
        return true;
    }

    // ---- server --------------------------------------------------------------------------------------------------

    /** Starts the server in the background so the first question answers faster. */
    public boolean start() {
        Optional<LocalAiRuntime> r = ensureRuntime();
        r.ifPresent(LocalAiRuntime::start);
        return r.isPresent();
    }

    /** Stops the server (it restarts on the next question). */
    public void stop() {
        if (runtime != null) {
            runtime.stop();
        }
    }

    @Override
    public void chat(LocalAiClient.ChatRequest request, Consumer<LocalAiClient.ChatResponse> onReply,
                     Consumer<LocalAiException> onError) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(onReply, "onReply");
        Objects.requireNonNull(onError, "onError");
        if (!isEnabled()) {
            mainThread.execute(() -> onError.accept(new LocalAiException(LocalAiException.Kind.DISABLED,
                    "Vanta Nexus is turned off in the settings")));
            return;
        }
        Optional<LocalAiRuntime> r = ensureRuntime();
        if (r.isEmpty()) {
            LocalAiException.Kind kind = installStatus == LocalAiStatus.UNSUPPORTED_PLATFORM
                    ? LocalAiException.Kind.UNSUPPORTED_PLATFORM : installStatus == LocalAiStatus.FAILED
                    ? LocalAiException.Kind.INVALID_MANIFEST : LocalAiException.Kind.NOT_INSTALLED;
            String reason = installReason.isEmpty() ? "the Local AI is not installed" : installReason;
            mainThread.execute(() -> onError.accept(new LocalAiException(kind, reason)));
            return;
        }
        r.get().chat(request, onReply, onError);
    }

    private Optional<LocalAiRuntime> ensureRuntime() {
        if (runtime != null) {
            return Optional.of(runtime);
        }
        if (installer.isEmpty() || !installStatus.isInstalled() || installed.isEmpty() || closed) {
            return Optional.empty();
        }
        LocalAiInstaller inst = installer.get();
        LocalAiInstalled state = installed.get();
        int contextSize = deps.manifest().map(m -> m.model().contextSize()).orElse(4096);
        LocalAiRuntime.Config config = LocalAiRuntime.Config.defaults(configuredThreads(), contextSize)
                .withIdle(Duration.ofMinutes(settings.get(VantaSettings.NEXUS_IDLE_TIMEOUT_MINUTES)),
                        settings.get(VantaSettings.NEXUS_KEEP_RUNNING))
                .withShutdownHook(deps.shutdownHook());
        LocalAiRuntime r = new LocalAiRuntime(inst.serverExecutable(state), inst.modelFile(), paths.serverLogFile(),
                config, deps.processFactory(), deps.ports(), deps.http().get(), deps.sleeper(), deps.worker(),
                mainThread, clock);
        runtimeUnsubscribe = r.addListener((status, reason) -> {
            for (Listener listener : listeners) {
                listener.onStatusChanged(status(), statusReason());
            }
        });
        runtime = r;
        return Optional.of(r);
    }

    private void dropRuntime() {
        if (runtimeUnsubscribe != null) {
            runtimeUnsubscribe.run();
            runtimeUnsubscribe = null;
        }
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
    }

    private int configuredThreads() {
        int configured = settings.get(VantaSettings.NEXUS_THREADS);
        return configured > 0 ? configured : LocalAiRuntime.defaultThreads(deps.availableProcessors());
    }

    // ---- notifications and listeners -------------------------------------------------------------------------------

    private void setInstallStatus(LocalAiStatus status, String reason) {
        boolean changed = status != installStatus || !Objects.equals(reason, installReason);
        installStatus = status;
        installReason = reason == null ? "" : reason;
        if (changed) {
            for (Listener listener : listeners) {
                listener.onStatusChanged(status(), statusReason());
            }
        }
    }

    private void fireFinished(Optional<LocalAiException> error) {
        for (Listener listener : listeners) {
            listener.onFinished(error);
        }
    }

    /** Relays installer progress from the worker to the main thread, at most every 100 ms. */
    private final class ProgressRelay implements LocalAiInstaller.Progress {
        private final long toastId;
        private final long totalBytes;
        private long lastRelayAt = Long.MIN_VALUE;
        private InstallStep lastStep;

        ProgressRelay(long toastId, long totalBytes) {
            this.toastId = toastId;
            this.totalBytes = totalBytes;
        }

        @Override
        public void onProgress(InstallStep step, long bytesDone, long bytesTotal, double bytesPerSecond) {
            long now = clock.millis();
            boolean stepChanged = step != lastStep;
            boolean complete = bytesTotal > 0 && bytesDone >= bytesTotal;
            if (!stepChanged && !complete && now - lastRelayAt < LocalAiInstaller.REPORT_INTERVAL_MS) {
                return;
            }
            lastRelayAt = now;
            lastStep = step;
            mainThread.execute(() -> {
                for (Listener listener : listeners) {
                    listener.onProgress(step, bytesDone, bytesTotal, bytesPerSecond);
                }
                if (toastId >= 0) {
                    String body = bytesTotal > 0
                            ? Lang.tr("vanta.localai.progress.bytes", Lang.tr(step.langKey()), formatBytes(bytesDone),
                            formatBytes(bytesTotal), formatBytes(Math.round(bytesPerSecond)))
                            : Lang.tr(step.langKey());
                    notifications.updateBody(toastId, body);
                    double fraction = overallFraction(step, bytesDone, bytesTotal);
                    notifications.updateProgress(toastId, Math.min(0.99, fraction));
                }
            });
        }

        private double overallFraction(InstallStep step, long done, long total) {
            if (totalBytes <= 0) {
                return total > 0 ? (double) done / total : 0;
            }
            return switch (step) {
                case CHECKING -> 0;
                case DOWNLOADING_RUNTIME -> Math.min(done, total) / (double) totalBytes;
                case DOWNLOADING_MODEL -> (totalBytes - (total > 0 ? total : 0) + Math.min(done, total))
                        / (double) totalBytes;
                case VERIFYING, INSTALLING, INITIALIZING, READY -> 0.95;
            };
        }

        @Override
        public boolean isCancelled() {
            return cancelRequested.get() || closed;
        }
    }

    /** "1.8 GB", "345 MB", "12 KB", "0 B". */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, mb < 10 ? "%.1f MB" : "%.0f MB", mb);
        }
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }
}
