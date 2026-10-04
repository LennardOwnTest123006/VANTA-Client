package dev.vanta.launcher.core.install;

import dev.vanta.launcher.core.launch.LibraryResolver;
import dev.vanta.launcher.core.launch.ResolvedLibrary;
import dev.vanta.launcher.core.model.AssetIndexJson;
import dev.vanta.launcher.core.model.Download;
import dev.vanta.launcher.core.model.VersionJson;
import dev.vanta.launcher.core.model.VersionManifest;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.Checksum;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.net.JsonHttp;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.OsInfo;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mojang download services: version manifest, version JSON, client jar, libraries, assets and the log4j config.
 * All endpoints are constructor parameters so tests can point them at a fake server.
 */
public final class MojangService {

    /** Mojang version manifest (v2). */
    public static final URI VERSION_MANIFEST_URL = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    /** Mojang asset objects. */
    public static final URI RESOURCES_BASE = URI.create("https://resources.download.minecraft.net/");

    private final Downloader downloader;
    private final JsonHttp json;
    private final LauncherPaths paths;
    private final URI manifestUrl;
    private final URI resourcesBase;

    /**
     * Production endpoints.
     *
     * @param downloader downloader
     * @param paths      paths
     */
    public MojangService(final Downloader downloader, final LauncherPaths paths) {
        this(downloader, paths, VERSION_MANIFEST_URL, RESOURCES_BASE);
    }

    /**
     * @param downloader    downloader
     * @param paths         paths
     * @param manifestUrl   version manifest URL
     * @param resourcesBase asset objects base URL (ending with a slash)
     */
    public MojangService(final Downloader downloader, final LauncherPaths paths, final URI manifestUrl, final URI resourcesBase) {
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.json = new JsonHttp(downloader);
        this.paths = Objects.requireNonNull(paths, "paths");
        this.manifestUrl = Objects.requireNonNull(manifestUrl, "manifestUrl");
        this.resourcesBase = Objects.requireNonNull(resourcesBase, "resourcesBase");
    }

    /**
     * Fetches the version manifest.
     *
     * @return manifest
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public VersionManifest fetchManifest() throws IOException, InterruptedException {
        return json.get(manifestUrl, VersionManifest.class);
    }

    /**
     * Resolves a version: fetches the manifest, downloads the version JSON (verified by SHA-1) to
     * {@code versions/<id>/<id>.json} and parses it. A cached JSON that matches the manifest's hash is reused.
     *
     * @param id version id
     * @return version JSON
     * @throws IOException          on failure or when the manifest does not list the version
     * @throws InterruptedException when interrupted
     */
    public VersionJson resolveVersion(final String id) throws IOException, InterruptedException {
        final VersionManifest manifest = fetchManifest();
        final VersionManifest.Entry entry = manifest.find(id)
            .orElseThrow(() -> new IOException("Mojang's version manifest does not list Minecraft " + id));
        return resolveVersion(entry);
    }

    /**
     * Downloads (or reuses) the version JSON of a manifest entry.
     *
     * @param entry manifest entry
     * @return version JSON
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public VersionJson resolveVersion(final VersionManifest.Entry entry) throws IOException, InterruptedException {
        final Path file = paths.versionJson(entry.id());
        final Checksum checksum = entry.sha1() == null || entry.sha1().isBlank() ? null : Checksum.sha1(entry.sha1());
        final DownloadRequest request = new DownloadRequest(URI.create(entry.url()), file, -1L, checksum, entry.id() + ".json");
        downloader.download(request, DownloadProgressListener.NONE, CancellationToken.NONE);
        return Json.read(file, VersionJson.class);
    }

    /**
     * Loads a previously downloaded version JSON.
     *
     * @param id version id
     * @return version JSON when present
     * @throws IOException when the file exists but is unreadable
     */
    public Optional<VersionJson> loadCachedVersion(final String id) throws IOException {
        final Path file = paths.versionJson(id);
        return Files.isRegularFile(file) ? Optional.of(Json.read(file, VersionJson.class)) : Optional.empty();
    }

    /**
     * @param version version JSON
     * @return download request for the client jar ({@code versions/<id>/<id>.jar})
     * @throws IOException when the version has no client download
     */
    public DownloadRequest clientJarRequest(final VersionJson version) throws IOException {
        final Download client = version.clientDownload()
            .orElseThrow(() -> new IOException("Version " + version.id() + " has no client download"));
        return new DownloadRequest(URI.create(client.url()), paths.versionJar(version.id()), client.size(),
            client.hasSha1() ? Checksum.sha1(client.sha1()) : null, "minecraft-" + version.id() + ".jar");
    }

    /**
     * @param version version JSON
     * @param os      target platform
     * @return resolved libraries for the platform
     */
    public List<ResolvedLibrary> resolveLibraries(final VersionJson version, final OsInfo os) {
        return new LibraryResolver(os).resolve(version.libraries());
    }

    /**
     * @param version version JSON
     * @param os      target platform
     * @return download requests for all libraries of the platform
     */
    public List<DownloadRequest> libraryRequests(final VersionJson version, final OsInfo os) {
        final List<DownloadRequest> out = new ArrayList<>();
        for (ResolvedLibrary lib : resolveLibraries(version, os)) {
            if (lib.downloadUrl().isPresent()) {
                out.add(lib.downloadRequest(paths.librariesDir()));
            }
        }
        return out;
    }

    /**
     * Downloads (or reuses) the asset index of a version and parses it.
     *
     * @param version version JSON
     * @return asset index
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public AssetIndexJson fetchAssetIndex(final VersionJson version) throws IOException, InterruptedException {
        final VersionJson.AssetIndexRef ref = version.assetIndex();
        if (ref == null || ref.url() == null) {
            throw new IOException("Version " + version.id() + " has no asset index");
        }
        final Path file = paths.assetIndexesDir().resolve(ref.id() + ".json");
        final Checksum checksum = ref.sha1() == null || ref.sha1().isBlank() ? null : Checksum.sha1(ref.sha1());
        downloader.download(new DownloadRequest(URI.create(ref.url()), file, ref.size(), checksum, "asset index " + ref.id()),
            DownloadProgressListener.NONE, CancellationToken.NONE);
        return Json.read(file, AssetIndexJson.class);
    }

    /**
     * @param index asset index
     * @return download requests for every object (duplicates by hash removed)
     */
    public List<DownloadRequest> assetRequests(final AssetIndexJson index) {
        final Map<String, DownloadRequest> byHash = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, AssetIndexJson.AssetObject> e : index.objects().entrySet()) {
            final AssetIndexJson.AssetObject obj = e.getValue();
            if (obj.hash() == null || obj.hash().length() < 3 || byHash.containsKey(obj.hash())) {
                continue;
            }
            final Path target = paths.assetObjectsDir().resolve(obj.hash().substring(0, 2)).resolve(obj.hash());
            byHash.put(obj.hash(), new DownloadRequest(resourcesBase.resolve(obj.path()), target, obj.size(),
                Checksum.sha1(obj.hash()), e.getKey()));
        }
        return new ArrayList<>(byHash.values());
    }

    /**
     * @param version version JSON
     * @return request for the log4j configuration file ({@code assets/log_configs/<id>}) when the version has one
     */
    public Optional<DownloadRequest> logConfigRequest(final VersionJson version) {
        if (version.logging() == null || version.logging().client() == null || version.logging().client().file() == null) {
            return Optional.empty();
        }
        final VersionJson.LoggingFile f = version.logging().client().file();
        if (f.url() == null || f.id() == null) {
            return Optional.empty();
        }
        return Optional.of(new DownloadRequest(URI.create(f.url()), paths.logConfigsDir().resolve(f.id()), f.size(),
            f.sha1() == null || f.sha1().isBlank() ? null : Checksum.sha1(f.sha1()), f.id()));
    }

    /** @return the downloader */
    public Downloader downloader() {
        return downloader;
    }
}
