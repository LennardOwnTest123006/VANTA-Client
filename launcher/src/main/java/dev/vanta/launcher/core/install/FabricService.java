package dev.vanta.launcher.core.install;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.vanta.launcher.core.launch.LibraryResolver;
import dev.vanta.launcher.core.launch.ResolvedLibrary;
import dev.vanta.launcher.core.model.FabricLoaderVersion;
import dev.vanta.launcher.core.model.FabricProfileJson;
import dev.vanta.launcher.core.net.Checksum;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.net.HashAlgorithm;
import dev.vanta.launcher.core.net.HttpStatusException;
import dev.vanta.launcher.core.net.IntegrityException;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Fabric meta / Maven services: loader versions, the launcher profile JSON and loader libraries.
 *
 * <p>Fabric profile libraries carry {@code sha1}/{@code sha256} in current meta responses. When a library has no
 * hash, the Maven sidecar ({@code <artifact>.sha256}, then {@code .sha1}) is fetched so every download is still
 * verified; a library whose hash cannot be obtained is refused.</p>
 */
public final class FabricService {

    /** Fabric meta API. */
    public static final URI META_BASE = URI.create("https://meta.fabricmc.net/v2/");
    /** Fabric Maven repository. */
    public static final URI MAVEN_BASE = URI.create("https://maven.fabricmc.net/");

    private final Downloader downloader;
    private final JsonHttp json;
    private final LauncherPaths paths;
    private final URI metaBase;

    /**
     * Production endpoints.
     *
     * @param downloader downloader
     * @param paths      paths
     */
    public FabricService(final Downloader downloader, final LauncherPaths paths) {
        this(downloader, paths, META_BASE);
    }

    /**
     * @param downloader downloader
     * @param paths      paths
     * @param metaBase   meta API base (ending with a slash)
     */
    public FabricService(final Downloader downloader, final LauncherPaths paths, final URI metaBase) {
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.json = new JsonHttp(downloader);
        this.paths = Objects.requireNonNull(paths, "paths");
        this.metaBase = Objects.requireNonNull(metaBase, "metaBase");
    }

    /**
     * Profile id for a loader/game combination.
     *
     * @param gameVersion   Minecraft version
     * @param loaderVersion loader version
     * @return {@code fabric-loader-<loader>-<game>}
     */
    public static String profileId(final String gameVersion, final String loaderVersion) {
        return "fabric-loader-" + loaderVersion + "-" + gameVersion;
    }

    /**
     * Lists loader versions available for a game version.
     *
     * @param gameVersion Minecraft version
     * @return loader versions (newest first, as returned by meta)
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public List<FabricLoaderVersion> listLoaders(final String gameVersion) throws IOException, InterruptedException {
        final URI url = metaBase.resolve("versions/loader/" + gameVersion);
        return json.get(url, new TypeToken<List<FabricLoaderVersion>>() {
        });
    }

    /**
     * @param gameVersion   Minecraft version
     * @param loaderVersion loader version
     * @return the Fabric meta URL of the launcher profile JSON
     */
    public URI profileUrl(final String gameVersion, final String loaderVersion) {
        return metaBase.resolve("versions/loader/" + gameVersion + "/" + loaderVersion + "/profile/json");
    }

    /**
     * Fetches the launcher profile JSON exactly as Fabric meta serves it (every field kept), for writing into the
     * official Minecraft Launcher's {@code versions/} directory like the official Fabric installer does. Nothing is
     * cached or written here.
     *
     * @param gameVersion   Minecraft version
     * @param loaderVersion loader version
     * @return the profile with {@code id} set to {@link #profileId} and {@code inheritsFrom} checked
     * @throws IOException          on failure or when the profile does not describe this game/loader combination
     * @throws InterruptedException when interrupted
     */
    public JsonObject fetchProfileTree(final String gameVersion, final String loaderVersion) throws IOException, InterruptedException {
        final URI url = profileUrl(gameVersion, loaderVersion);
        final JsonElement tree = json.getTree(url);
        if (!tree.isJsonObject()) {
            throw new IOException("Unexpected response from " + url + ": not a JSON object");
        }
        final JsonObject profile = tree.getAsJsonObject().deepCopy();
        final String mainClass = profile.has("mainClass") && profile.get("mainClass").isJsonPrimitive()
            ? profile.get("mainClass").getAsString() : "";
        if (mainClass.isBlank()) {
            throw new IOException("Fabric profile from " + url + " has no main class");
        }
        if (!profile.has("libraries") || !profile.get("libraries").isJsonArray() || profile.getAsJsonArray("libraries").isEmpty()) {
            throw new IOException("Fabric profile from " + url + " lists no libraries");
        }
        final String inherits = profile.has("inheritsFrom") && profile.get("inheritsFrom").isJsonPrimitive()
            ? profile.get("inheritsFrom").getAsString() : "";
        if (inherits.isEmpty()) {
            profile.addProperty("inheritsFrom", gameVersion);
        } else if (!inherits.equals(gameVersion)) {
            throw new IOException("Fabric profile from " + url + " inherits from " + inherits + ", expected " + gameVersion);
        }
        profile.addProperty("id", profileId(gameVersion, loaderVersion));
        return profile;
    }

    /**
     * Fetches the launcher profile JSON and stores it as {@code versions/<profileId>/<profileId>.json}. A cached
     * profile is reused.
     *
     * @param gameVersion   Minecraft version
     * @param loaderVersion loader version
     * @return profile
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public FabricProfileJson fetchProfile(final String gameVersion, final String loaderVersion) throws IOException, InterruptedException {
        final String id = profileId(gameVersion, loaderVersion);
        final Path file = paths.versionJson(id);
        if (Files.isRegularFile(file)) {
            try {
                final FabricProfileJson cached = Json.read(file, FabricProfileJson.class);
                if (cached.mainClass() != null && !cached.libraries().isEmpty()) {
                    return cached;
                }
            } catch (IOException ignored) {
                // re-download below
            }
        }
        final URI url = profileUrl(gameVersion, loaderVersion);
        final FabricProfileJson profile = json.get(url, FabricProfileJson.class);
        if (profile.mainClass() == null || profile.mainClass().isBlank()) {
            throw new IOException("Fabric profile for " + id + " has no main class");
        }
        // Persist the profile with the id we use locally so LaunchService can find it.
        final FabricProfileJson stored = new FabricProfileJson(id, profile.inheritsFrom() == null ? gameVersion : profile.inheritsFrom(),
            profile.releaseTime(), profile.time(), profile.type(), profile.mainClass(), profile.arguments(), profile.libraries());
        Json.write(file, stored);
        return stored;
    }

    /**
     * Resolves the loader libraries for a platform and fills in missing checksums from Maven sidecar files.
     *
     * @param profile profile
     * @param os      platform
     * @return resolved libraries with checksums
     * @throws IOException          when a checksum cannot be obtained
     * @throws InterruptedException when interrupted
     */
    public List<ResolvedLibrary> resolveLibraries(final FabricProfileJson profile, final OsInfo os) throws IOException, InterruptedException {
        final List<ResolvedLibrary> resolved = new LibraryResolver(os).resolve(profile.libraries());
        final List<ResolvedLibrary> out = new ArrayList<>(resolved.size());
        for (ResolvedLibrary lib : resolved) {
            if (lib.expectedChecksum().isPresent()) {
                out.add(lib);
                continue;
            }
            final String url = lib.downloadUrl().orElseThrow(() -> new IOException("No URL for " + lib.coordinate()));
            final Path file = lib.file(paths.librariesDir());
            if (Files.isRegularFile(file) && (lib.size() < 0 || Files.size(file) == lib.size())) {
                // Already present from a previous verified install; keep without a sidecar round trip.
                out.add(lib);
                continue;
            }
            out.add(lib.withChecksum(fetchSidecarChecksum(URI.create(url))));
        }
        return out;
    }

    /**
     * @param profile profile
     * @param os      platform
     * @return download requests for the loader libraries
     * @throws IOException          when a checksum cannot be obtained
     * @throws InterruptedException when interrupted
     */
    public List<DownloadRequest> libraryRequests(final FabricProfileJson profile, final OsInfo os) throws IOException, InterruptedException {
        final List<DownloadRequest> out = new ArrayList<>();
        for (ResolvedLibrary lib : resolveLibraries(profile, os)) {
            out.add(lib.downloadRequest(paths.librariesDir()));
        }
        return out;
    }

    /**
     * Fetches a Maven checksum sidecar ({@code .sha256} preferred, then {@code .sha1}).
     *
     * @param artifactUrl artifact URL
     * @return checksum
     * @throws IOException          when neither sidecar exists or is malformed
     * @throws InterruptedException when interrupted
     */
    public Checksum fetchSidecarChecksum(final URI artifactUrl) throws IOException, InterruptedException {
        final Optional<Checksum> sha256 = tryFetchSidecar(artifactUrl, HashAlgorithm.SHA256);
        if (sha256.isPresent()) {
            return sha256.get();
        }
        final Optional<Checksum> sha1 = tryFetchSidecar(artifactUrl, HashAlgorithm.SHA1);
        if (sha1.isPresent()) {
            return sha1.get();
        }
        throw new IntegrityException(null, "No checksum available for " + artifactUrl + " (neither .sha256 nor .sha1 sidecar exists)");
    }

    private Optional<Checksum> tryFetchSidecar(final URI artifactUrl, final HashAlgorithm algorithm) throws IOException, InterruptedException {
        final String suffix = algorithm == HashAlgorithm.SHA256 ? ".sha256" : ".sha1";
        final URI url = URI.create(artifactUrl.toString() + suffix);
        try {
            final String text = downloader.fetchString(url).trim();
            // Sidecars are either the bare hex or "hex  filename".
            final String hex = text.split("\\s+")[0].toLowerCase(Locale.ROOT);
            return Optional.of(new Checksum(algorithm, hex));
        } catch (HttpStatusException e) {
            if (e.status() == 404 || e.status() == 403) {
                return Optional.empty();
            }
            throw e;
        } catch (IllegalArgumentException e) {
            throw new IntegrityException(null, "Malformed checksum sidecar " + url + ": " + e.getMessage());
        }
    }
}
