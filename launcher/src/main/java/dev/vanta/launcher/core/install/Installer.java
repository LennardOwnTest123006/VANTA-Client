package dev.vanta.launcher.core.install;

import dev.vanta.launcher.core.model.AssetIndexJson;
import dev.vanta.launcher.core.model.FabricProfileJson;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.model.VersionJson;
import dev.vanta.launcher.core.model.VersionManifest;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.net.IntegrityException;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.OsInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs an installation end to end: manifest → version JSON → client jar → libraries → assets → Fabric profile →
 * Fabric libraries → Fabric API → VANTA client → {@code instance.json}.
 *
 * <p>The installer is idempotent and resumable: every file that already exists with the expected size and digest
 * is skipped, so re-running after a failure or a cancellation only fetches what is missing. Free disk space is
 * verified once the version JSON is known and before any large transfer starts.</p>
 */
public final class Installer {

    private static final Logger LOG = Logger.getLogger("VANTA.Installer");

    private final LauncherPaths paths;
    private final OsInfo os;
    private final Downloader downloader;
    private final MojangService mojang;
    private final FabricService fabric;
    private final FabricApiService fabricApi;
    private final VantaClientService vantaClient;
    private final Optional<SharedFileSource> shared;
    private final Clock clock;

    /**
     * @param paths       paths
     * @param os          target platform
     * @param downloader  downloader
     * @param mojang      Mojang service
     * @param fabric      Fabric service
     * @param fabricApi   Fabric API service
     * @param vantaClient VANTA client service
     * @param shared      optional read-only source of already downloaded official files
     * @param clock       clock for {@code installedAt}
     */
    public Installer(final LauncherPaths paths, final OsInfo os, final Downloader downloader, final MojangService mojang,
                     final FabricService fabric, final FabricApiService fabricApi, final VantaClientService vantaClient,
                     final Optional<SharedFileSource> shared, final Clock clock) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.os = Objects.requireNonNull(os, "os");
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.mojang = Objects.requireNonNull(mojang, "mojang");
        this.fabric = Objects.requireNonNull(fabric, "fabric");
        this.fabricApi = Objects.requireNonNull(fabricApi, "fabricApi");
        this.vantaClient = Objects.requireNonNull(vantaClient, "vantaClient");
        this.shared = Objects.requireNonNull(shared, "shared");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Loads the installed instance metadata.
     *
     * @return instance info when installed
     * @throws IOException when the file is unreadable
     */
    public Optional<InstanceInfo> loadInstance() throws IOException {
        if (!Files.isRegularFile(paths.instanceFile())) {
            return Optional.empty();
        }
        return Optional.of(Json.read(paths.instanceFile(), InstanceInfo.class));
    }

    /**
     * Quick check whether an instance that satisfies the request exists (metadata, version JSONs and client jar
     * present). File digests are not re-verified here; {@link #install} does that.
     *
     * @param request request
     * @return instance info when present and matching
     * @throws IOException when metadata is unreadable
     */
    public Optional<InstanceInfo> findInstalled(final InstallRequest request) throws IOException {
        final Optional<InstanceInfo> info = loadInstance();
        if (info.isEmpty()) {
            return Optional.empty();
        }
        final InstanceInfo i = info.get();
        final boolean matches = request.minecraftVersion().equals(i.minecraftVersion())
            && request.fabricLoaderVersion().equals(i.fabricLoaderVersion())
            && request.fabricApiVersion().equals(i.fabricApiVersion())
            && (!request.includeVantaClient() || i.hasVantaClient())
            && Files.isRegularFile(paths.versionJson(i.vanillaVersionId()))
            && Files.isRegularFile(paths.versionJson(i.fabricProfileId()))
            && Files.isRegularFile(paths.versionJar(i.vanillaVersionId()));
        return matches ? info : Optional.empty();
    }

    /**
     * Performs the installation.
     *
     * @param request  request
     * @param listener progress listener
     * @param token    cancellation token
     * @return instance metadata
     * @throws InstallException     wrapping the underlying failure ({@link NotPublishedException},
     *                              {@link IntegrityException}, {@link InsufficientDiskSpaceException}, network ...)
     * @throws InterruptedException when interrupted
     * @throws CancellationException when cancelled
     */
    public InstanceInfo install(final InstallRequest request, final InstallListener listener, final CancellationToken token)
        throws InstallException, InterruptedException {
        final InstallListener l = listener == null ? InstallListener.NONE : listener;
        final CancellationToken t = token == null ? CancellationToken.NONE : token;
        final List<InstallStep> steps = InstallPlan.stepsFor(request);
        final Progress progress = new Progress(steps, l);
        InstallStep current = InstallStep.MANIFEST;
        try {
            paths.createDirectories();

            // 1. Manifest
            progress.begin(InstallStep.MANIFEST, 1);
            t.throwIfCancelled();
            final VersionManifest manifest = mojang.fetchManifest();
            final VersionManifest.Entry entry = manifest.find(request.minecraftVersion()).orElseThrow(
                () -> new IOException("Mojang's version manifest does not list Minecraft " + request.minecraftVersion()));
            progress.advance(1, "Minecraft " + entry.id() + " (" + entry.type() + ")");

            // 2. Version JSON
            current = InstallStep.VERSION_JSON;
            progress.begin(InstallStep.VERSION_JSON, 1);
            final VersionJson version = mojang.resolveVersion(entry);
            progress.advance(1, entry.id() + ".json");

            // Plan + disk space
            final DownloadRequest clientJar = mojang.clientJarRequest(version);
            final List<DownloadRequest> libraries = mojang.libraryRequests(version, os);
            final long remaining = remainingBytes(clientJar) + remainingBytes(libraries)
                + (request.includeAssets() && version.assetIndex() != null ? version.assetIndex().totalSize() : 0L);
            final InstallPlan plan = new InstallPlan(steps, remaining);
            plan.checkDiskSpace(paths.dataDir());
            l.onLog("Install plan: " + steps.size() + " steps, up to " + (plan.remainingBytes() / (1024L * 1024L)) + " MB to download");

            // 3. Client jar
            current = InstallStep.CLIENT_JAR;
            progress.begin(InstallStep.CLIENT_JAR, 1);
            downloader.download(clientJar, progress.downloadListener(), t);
            progress.advance(1, clientJar.description());

            // 4. Libraries
            current = InstallStep.LIBRARIES;
            progress.begin(InstallStep.LIBRARIES, libraries.size());
            reuseShared(libraries, true);
            downloader.downloadAll(libraries, progress.downloadListener(), t);
            mojang.logConfigRequest(version).ifPresent(req -> {
                try {
                    downloader.download(req, DownloadProgressListener.NONE, t);
                } catch (IOException | InterruptedException e) {
                    LOG.log(Level.FINE, "Optional log4j config not downloaded", e);
                }
            });

            // 5. Assets
            if (request.includeAssets()) {
                current = InstallStep.ASSETS;
                progress.begin(InstallStep.ASSETS, 0);
                final AssetIndexJson index = mojang.fetchAssetIndex(version);
                final List<DownloadRequest> assets = mojang.assetRequests(index);
                progress.begin(InstallStep.ASSETS, assets.size());
                reuseShared(assets, false);
                downloader.downloadAll(assets, progress.downloadListener(), t);
            }

            // 6. Fabric profile
            current = InstallStep.FABRIC_PROFILE;
            progress.begin(InstallStep.FABRIC_PROFILE, 1);
            final FabricProfileJson profile = fabric.fetchProfile(request.minecraftVersion(), request.fabricLoaderVersion());
            progress.advance(1, profile.id());

            // 7. Fabric libraries
            current = InstallStep.FABRIC_LIBRARIES;
            progress.begin(InstallStep.FABRIC_LIBRARIES, 0);
            final List<DownloadRequest> fabricLibs = fabric.libraryRequests(profile, os);
            progress.begin(InstallStep.FABRIC_LIBRARIES, fabricLibs.size());
            downloader.downloadAll(fabricLibs, progress.downloadListener(), t);

            // 8. Fabric API
            current = InstallStep.FABRIC_API;
            progress.begin(InstallStep.FABRIC_API, 1);
            final DownloadRequest apiRequest = fabricApi.request(request.fabricApiVersion());
            downloader.download(apiRequest, progress.downloadListener(), t);
            fabricApi.pruneOtherVersions(request.fabricApiVersion()).forEach(p -> l.onLog("Removed old " + p.getFileName()));
            progress.advance(1, apiRequest.description());

            // 9. VANTA client
            String clientVersion = "";
            String clientJarName = "";
            if (request.includeVantaClient()) {
                current = InstallStep.VANTA_CLIENT;
                progress.begin(InstallStep.VANTA_CLIENT, 1);
                final Optional<Path> local = request.localClientJarOverride();
                final Path active;
                if (local.isPresent()) {
                    active = vantaClient.installLocalJar(local.get());
                    clientVersion = VantaClientService.DEV_VERSION;
                    l.onLog("Installed local client jar " + local.get());
                } else {
                    final ReleaseManifest clientManifest = vantaClient.fetchClientManifest();
                    active = vantaClient.installFromManifest(clientManifest, progress.downloadListener(), t);
                    clientVersion = clientManifest.version();
                }
                clientJarName = active.getFileName().toString();
                progress.advance(1, clientJarName);
            }

            // 10. Finalize
            current = InstallStep.FINALIZE;
            progress.begin(InstallStep.FINALIZE, 1);
            final InstanceInfo info = new InstanceInfo(InstanceInfo.SCHEMA_VERSION, paths.instanceId(), version.id(),
                request.fabricLoaderVersion(), request.fabricApiVersion(), clientVersion, clientJarName, version.id(),
                profile.id(), profile.mainClass(), version.assetIndexId(), version.javaMajor(),
                Instant.now(clock).toString());
            Json.write(paths.instanceFile(), info);
            progress.advance(1, "instance.json");
            l.onLog("Installation complete: " + progress.bytes() / 1024L + " KB downloaded");
            return info;
        } catch (InstallException e) {
            throw e;
        } catch (CancellationException e) {
            throw e;
        } catch (IOException e) {
            throw new InstallException(current, current.label() + " failed: " + e.getMessage(), e);
        }
    }

    private void reuseShared(final List<DownloadRequest> requests, final boolean libraries) {
        if (shared.isEmpty()) {
            return;
        }
        int reused = 0;
        for (DownloadRequest r : requests) {
            try {
                if (downloader.isValid(r)) {
                    continue;
                }
            } catch (IOException ignored) {
                // fall through and try to copy
            }
            final boolean copied = libraries
                ? shared.get().copyLibrary(r, paths.librariesDir())
                : shared.get().copyAsset(r, paths.assetsDir());
            if (copied) {
                reused++;
            }
        }
        if (reused > 0) {
            LOG.log(Level.INFO, "Reused {0} verified files from the official Minecraft directory", reused);
        }
    }

    private long remainingBytes(final DownloadRequest request) {
        try {
            return downloader.isValid(request) ? 0L : Math.max(0L, request.expectedSize());
        } catch (IOException e) {
            return Math.max(0L, request.expectedSize());
        }
    }

    private long remainingBytes(final List<DownloadRequest> requests) {
        long sum = 0;
        for (DownloadRequest r : requests) {
            sum += remainingBytes(r);
        }
        return sum;
    }

    /** Progress bookkeeping shared with the download listener. */
    private static final class Progress {

        private final List<InstallStep> steps;
        private final InstallListener listener;
        private final AtomicLong bytes = new AtomicLong();
        private final AtomicLong done = new AtomicLong();
        private volatile InstallStep step = InstallStep.MANIFEST;
        private volatile long total;

        Progress(final List<InstallStep> steps, final InstallListener listener) {
            this.steps = steps;
            this.listener = listener;
        }

        void begin(final InstallStep s, final long totalUnits) {
            step = s;
            total = totalUnits;
            done.set(0);
            publish(null);
        }

        void advance(final long units, final String message) {
            done.addAndGet(units);
            publish(message);
        }

        long bytes() {
            return bytes.get();
        }

        DownloadProgressListener downloadListener() {
            return new DownloadProgressListener() {
                @Override
                public void onProgress(final DownloadRequest request, final long bytesDone, final long bytesTotal) {
                    // byte-level progress is reported per completed file to keep UI updates cheap
                }

                @Override
                public void onComplete(final DownloadRequest request, final boolean skipped, final long transferred) {
                    bytes.addAndGet(transferred);
                    if (total > 1) {
                        done.incrementAndGet();
                    }
                    publish((skipped ? "Verified " : "Downloaded ") + request.description());
                }

                @Override
                public void onRetry(final DownloadRequest request, final int attempt, final Exception error) {
                    listener.onLog("Retrying " + request.description() + " (attempt " + (attempt + 1) + "): " + error.getMessage());
                }
            };
        }

        private void publish(final String message) {
            final int index = Math.max(0, steps.indexOf(step));
            final List<InstallStep> all = new ArrayList<>(steps);
            listener.onProgress(new InstallProgress(step, index, all.size(), done.get(), total, bytes.get(), message));
        }
    }
}
