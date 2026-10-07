package dev.vanta.launcher.core.install;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.launch.MinecraftFolderHint;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.modrinth.ModrinthIndex;
import dev.vanta.launcher.core.modrinth.ModrinthService;
import dev.vanta.launcher.core.modrinth.PerformancePack;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.AtomicFiles;
import dev.vanta.launcher.core.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * "Use with the Minecraft Launcher": makes the VANTA instance playable from the official Minecraft Launcher, which
 * then downloads Minecraft, its libraries, assets and Java and handles the Microsoft sign-in itself. This is the way
 * to play while Microsoft sign-in inside VANTA is unavailable (it needs an Azure application id approved by Mojang).
 *
 * <p>What it writes, and nothing else ({@link #plan} lists every one of these files before anything is written):</p>
 * <ol>
 *   <li>Fabric API (verified with the Maven {@code .sha256} sidecar) and the VANTA client jar (verified with the
 *       release manifest's SHA-256, or a local {@code --client-jar}) into the VANTA instance's {@code mods/} folder,
 *       the same instance the regular install uses ({@code <data>/instances/vanta-1.21.11}). Like the regular install,
 *       older Fabric API and VANTA client jars in that {@code mods/} folder are removed so only one version is loaded,
 *       a release is also kept under {@code <data>/versions/vanta-client/<version>/} for rollback, and an existing
 *       {@code instance.json} records the client version.</li>
 *   <li>The Fabric Loader version JSON exactly as Fabric meta serves it to
 *       {@code <minecraft>/versions/fabric-loader-<loader>-<game>/fabric-loader-<loader>-<game>.json}, plus the empty
 *       {@code .jar} next to it (what the official Fabric installer does).</li>
 *   <li>The profile {@code "vanta-<game>"} in every profiles file of the official launcher that exists:
 *       {@code <minecraft>/launcher_profiles.json} (launcher from minecraft.net) and/or
 *       {@code launcher_profiles_microsoft_store.json} (launcher from the Microsoft Store / Xbox app). Each file is
 *       parsed as a JSON tree so every other key is preserved (including members whose value is {@code null}), a
 *       one-time backup ({@code <file>.vanta-backup}) is kept, and the new file is written atomically.</li>
 * </ol>
 *
 * <p>No profiles file is ever created: when neither exists the Minecraft Launcher has not been started in that
 * directory yet and {@link OfficialLauncherNotFoundException} is raised before anything is downloaded or written.</p>
 */
public final class OfficialProfileService {

    /** Profiles file of the official launcher. */
    public static final String PROFILES_FILE = "launcher_profiles.json";
    /** Profiles file of the Microsoft Store edition of the official launcher. */
    public static final String STORE_PROFILES_FILE = "launcher_profiles_microsoft_store.json";
    /** Suffix of the one-time backup of a profiles file. */
    public static final String BACKUP_SUFFIX = ".vanta-backup";
    /** Class path resource of the 128 px VANTA icon used for the profile. */
    public static final String ICON_RESOURCE = "/dev/vanta/launcher/ui/icon-128.png";

    private static final DateTimeFormatter ISO_INSTANT_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC);
    private static final List<InstallStep> STEPS = List.of(InstallStep.FABRIC_PROFILE, InstallStep.FABRIC_API,
        InstallStep.VANTA_CLIENT, InstallStep.FINALIZE);
    private static final List<InstallStep> STEPS_WITH_PACK = List.of(InstallStep.FABRIC_PROFILE, InstallStep.FABRIC_API,
        InstallStep.VANTA_CLIENT, InstallStep.PERFORMANCE_PACK, InstallStep.FINALIZE);

    private final LauncherPaths paths;
    private final Downloader downloader;
    private final FabricService fabric;
    private final FabricApiService fabricApi;
    private final VantaClientService vantaClient;
    private final Clock clock;
    private final Supplier<byte[]> icon;
    private final PerformancePack performancePack;

    /**
     * Service with the bundled VANTA icon.
     *
     * @param paths       launcher paths (the instance the profile points at)
     * @param downloader  downloader
     * @param fabric      Fabric meta service
     * @param fabricApi   Fabric API service
     * @param vantaClient VANTA client service
     * @param clock       clock for {@code created}/{@code lastUsed}
     */
    public OfficialProfileService(final LauncherPaths paths, final Downloader downloader, final FabricService fabric,
                                  final FabricApiService fabricApi, final VantaClientService vantaClient, final Clock clock) {
        this(paths, downloader, fabric, fabricApi, vantaClient, clock, OfficialProfileService::bundledIcon, null);
    }

    /**
     * Service with the bundled VANTA icon and the performance pack.
     *
     * @param paths           launcher paths (the instance the profile points at)
     * @param downloader      downloader
     * @param fabric          Fabric meta service
     * @param fabricApi       Fabric API service
     * @param vantaClient     VANTA client service
     * @param clock           clock for {@code created}/{@code lastUsed}
     * @param performancePack the performance pack (null: never installed)
     */
    public OfficialProfileService(final LauncherPaths paths, final Downloader downloader, final FabricService fabric,
                                  final FabricApiService fabricApi, final VantaClientService vantaClient, final Clock clock,
                                  final PerformancePack performancePack) {
        this(paths, downloader, fabric, fabricApi, vantaClient, clock, OfficialProfileService::bundledIcon, performancePack);
    }

    /**
     * @param paths       launcher paths
     * @param downloader  downloader
     * @param fabric      Fabric meta service
     * @param fabricApi   Fabric API service
     * @param vantaClient VANTA client service
     * @param clock       clock
     * @param icon        PNG bytes of the profile icon
     */
    public OfficialProfileService(final LauncherPaths paths, final Downloader downloader, final FabricService fabric,
                                  final FabricApiService fabricApi, final VantaClientService vantaClient, final Clock clock,
                                  final Supplier<byte[]> icon) {
        this(paths, downloader, fabric, fabricApi, vantaClient, clock, icon, null);
    }

    /**
     * @param paths           launcher paths
     * @param downloader      downloader
     * @param fabric          Fabric meta service
     * @param fabricApi       Fabric API service
     * @param vantaClient     VANTA client service
     * @param clock           clock
     * @param icon            PNG bytes of the profile icon
     * @param performancePack the performance pack (null: never installed)
     */
    public OfficialProfileService(final LauncherPaths paths, final Downloader downloader, final FabricService fabric,
                                  final FabricApiService fabricApi, final VantaClientService vantaClient, final Clock clock,
                                  final Supplier<byte[]> icon, final PerformancePack performancePack) {
        this.performancePack = performancePack;
        this.paths = Objects.requireNonNull(paths, "paths");
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.fabric = Objects.requireNonNull(fabric, "fabric");
        this.fabricApi = Objects.requireNonNull(fabricApi, "fabricApi");
        this.vantaClient = Objects.requireNonNull(vantaClient, "vantaClient");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.icon = Objects.requireNonNull(icon, "icon");
    }

    // ---------------------------------------------------------------- naming

    /**
     * @param minecraftVersion Minecraft version
     * @return profile key in {@code launcher_profiles.json}, e.g. {@code vanta-1.21.11}
     */
    public static String profileKey(final String minecraftVersion) {
        return "vanta-" + minecraftVersion;
    }

    /**
     * @param minecraftVersion Minecraft version
     * @return profile name shown by the Minecraft Launcher, e.g. {@code VANTA 1.21.11}
     */
    public static String profileName(final String minecraftVersion) {
        return "VANTA " + minecraftVersion;
    }

    /**
     * @param profileName profile name
     * @return the sentence that tells the player what to do next
     */
    public static String nextStep(final String profileName) {
        return "Open the Minecraft Launcher, choose the profile '" + profileName + "' and press Play.";
    }

    /**
     * @return why a running Minecraft Launcher does not show the profile yet, and what to do
     */
    public static String restartHint() {
        return "The Minecraft Launcher reads its profiles only when it starts. Close it completely (also from the system tray"
            + " next to the clock, if it stays there) and start it again, otherwise the new profile does not appear.";
    }

    /** @return the bundled 128 px VANTA icon */
    public static byte[] bundledIcon() {
        try (InputStream in = OfficialProfileService.class.getResourceAsStream(ICON_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + ICON_RESOURCE);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + ICON_RESOURCE, e);
        }
    }

    // ---------------------------------------------------------------- model

    /**
     * What to set up.
     *
     * @param minecraftDir        the official Minecraft directory ({@code .minecraft})
     * @param localClientJar      local VANTA client jar instead of the published release (may be null)
     * @param memoryMb            maximum heap written to the profile's {@code javaArgs}
     * @param minecraftVersion    Minecraft version
     * @param fabricLoaderVersion Fabric Loader version
     * @param fabricApiVersion    Fabric API version
     * @param includePerformancePack whether the performance pack (Modrinth) is installed into the instance as well
     */
    public record Request(Path minecraftDir, Path localClientJar, int memoryMb, String minecraftVersion, String fabricLoaderVersion,
                          String fabricApiVersion, boolean includePerformancePack) {

        public Request {
            Objects.requireNonNull(minecraftDir, "minecraftDir");
            minecraftDir = minecraftDir.toAbsolutePath().normalize();
            localClientJar = localClientJar == null ? null : localClientJar.toAbsolutePath().normalize();
            Objects.requireNonNull(minecraftVersion, "minecraftVersion");
            Objects.requireNonNull(fabricLoaderVersion, "fabricLoaderVersion");
            Objects.requireNonNull(fabricApiVersion, "fabricApiVersion");
            if (memoryMb <= 0) {
                throw new IllegalArgumentException("memoryMb must be positive");
            }
        }

        /**
         * A request without the performance pack.
         *
         * @param minecraftDir        official Minecraft directory
         * @param localClientJar      local client jar (may be null)
         * @param memoryMb            heap in MiB
         * @param minecraftVersion    Minecraft version
         * @param fabricLoaderVersion Fabric Loader version
         * @param fabricApiVersion    Fabric API version
         */
        public Request(final Path minecraftDir, final Path localClientJar, final int memoryMb, final String minecraftVersion,
                       final String fabricLoaderVersion, final String fabricApiVersion) {
            this(minecraftDir, localClientJar, memoryMb, minecraftVersion, fabricLoaderVersion, fabricApiVersion, false);
        }

        /**
         * The pinned VANTA versions, without the performance pack.
         *
         * @param minecraftDir   official Minecraft directory
         * @param localClientJar local client jar (may be null)
         * @param memoryMb       heap in MiB
         * @return request
         */
        public static Request standard(final Path minecraftDir, final Path localClientJar, final int memoryMb) {
            return standard(minecraftDir, localClientJar, memoryMb, false);
        }

        /**
         * The pinned VANTA versions.
         *
         * @param minecraftDir    official Minecraft directory
         * @param localClientJar  local client jar (may be null)
         * @param memoryMb        heap in MiB
         * @param performancePack whether to install the performance pack
         * @return request
         */
        public static Request standard(final Path minecraftDir, final Path localClientJar, final int memoryMb, final boolean performancePack) {
            return new Request(minecraftDir, localClientJar, memoryMb, LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER,
                LauncherVersion.FABRIC_API, performancePack);
        }

        /** @return the local client jar when set */
        public Optional<Path> localClientJarOverride() {
            return Optional.ofNullable(localClientJar);
        }

        /** @return the Fabric version id, e.g. {@code fabric-loader-0.19.5-1.21.11} */
        public String versionId() {
            return FabricService.profileId(minecraftVersion, fabricLoaderVersion);
        }
    }

    /** Kind of a file the setup writes or removes. */
    public enum Kind {
        /** Fabric API jar in the instance's {@code mods/}. */
        FABRIC_API("Fabric API from maven.fabricmc.net (SHA-256 verified)", false),
        /** VANTA client jar of the published release in the instance's {@code mods/}. */
        VANTA_CLIENT("VANTA Client release jar (SHA-256 verified against the release manifest)", false),
        /** VANTA client jar copied from a local {@code --client-jar}. */
        VANTA_CLIENT_LOCAL("VANTA Client copied from the local --client-jar (not verified)", false),
        /** A performance pack mod from Modrinth in the instance's {@code mods/}. */
        PERFORMANCE_MOD("performance pack: newest version for this Minecraft version from Modrinth (SHA-512 verified)", false),
        /** Rollback copy of the release jar, or its manifest, in VANTA's data directory. */
        CLIENT_ROLLBACK_COPY("copy kept in VANTA's data folder for rolling back", false),
        /** The VANTA instance's {@code instance.json}. */
        INSTANCE_RECORD("VANTA instance record: the installed client version is noted", false),
        /** An older Fabric API or VANTA client jar removed from the instance's {@code mods/}. */
        REPLACED_JAR("removed: older version in the VANTA game folder, so only one version is loaded", true),
        /** An old rollback copy removed from VANTA's data directory. */
        PRUNED_ROLLBACK_COPY("removed: old rollback copy (only the " + VantaClientService.KEEP_VERSIONS + " newest versions are kept)", true),
        /** Fabric Loader version JSON in the official {@code versions/}. */
        VERSION_JSON("Fabric Loader version JSON from meta.fabricmc.net", false),
        /** Empty version jar next to it. */
        VERSION_JAR("empty placeholder jar, like the official Fabric installer", false),
        /** One-time backup of {@code launcher_profiles.json}, made before it is changed for the first time. */
        PROFILES_BACKUP("one-time backup of the original launcher_profiles.json", false),
        /** {@code launcher_profiles.json}. */
        PROFILES("profile entry added or updated; every other entry is kept", false),
        /** One-time backup of {@code launcher_profiles_microsoft_store.json}. */
        STORE_PROFILES_BACKUP("one-time backup of the original launcher_profiles_microsoft_store.json", false),
        /** {@code launcher_profiles_microsoft_store.json}. */
        STORE_PROFILES("same profile entry for the Microsoft Store edition of the launcher", false),
        /** {@code <instance>/config/vanta/minecraft-folder.json}: which Minecraft folder's worlds Singleplayer lists. */
        MINECRAFT_FOLDER_NOTE("note for the VANTA Client: Singleplayer lists the worlds of this Minecraft folder", false);

        private final String description;
        private final boolean removal;

        Kind(final String description, final boolean removal) {
            this.description = description;
            this.removal = removal;
        }

        /** @return English description for the command line */
        public String description() {
            return description;
        }

        /** @return whether the file (or directory) is removed rather than written */
        public boolean removal() {
            return removal;
        }
    }

    /**
     * One file the setup writes or removes.
     *
     * @param kind     kind
     * @param location absolute path
     * @param detail   what it is, when the kind alone does not say (for example {@code Sodium mc1.21.11-0.8.14-fabric}); may
     *                 be empty
     */
    public record PlannedFile(Kind kind, String location, String detail) {

        public PlannedFile {
            detail = detail == null ? "" : detail;
        }

        /**
         * @param kind     kind
         * @param location absolute path
         */
        public PlannedFile(final Kind kind, final String location) {
            this(kind, location, "");
        }

        /** @return whether the file (or directory) is removed rather than written */
        public boolean removal() {
            return kind.removal();
        }
    }

    /**
     * Everything the setup will write or remove. A file that is already identical is left as it is.
     *
     * @param minecraftDir       official Minecraft directory
     * @param gameDir            the VANTA instance directory the profile points at
     * @param profileKey         profile key
     * @param profileName        profile name
     * @param versionId          Fabric version id
     * @param vantaClientVersion the VANTA client version that is installed ({@code dev} for a local jar)
     * @param files              files written or removed, in order
     * @param profileExists      whether the profile already exists (it is updated in place)
     * @param notes              remarks about the performance pack (projects that will be skipped, Modrinth unreachable)
     */
    public record Plan(Path minecraftDir, Path gameDir, String profileKey, String profileName, String versionId, String vantaClientVersion,
                       List<PlannedFile> files, boolean profileExists, List<String> notes) {

        public Plan {
            files = List.copyOf(files);
            notes = List.copyOf(notes);
        }

        /**
         * A plan without notes.
         *
         * @param minecraftDir       official Minecraft directory
         * @param gameDir            the VANTA instance directory
         * @param profileKey         profile key
         * @param profileName        profile name
         * @param versionId          Fabric version id
         * @param vantaClientVersion VANTA client version
         * @param files              files
         * @param profileExists      whether the profile exists
         */
        public Plan(final Path minecraftDir, final Path gameDir, final String profileKey, final String profileName, final String versionId,
                    final String vantaClientVersion, final List<PlannedFile> files, final boolean profileExists) {
            this(minecraftDir, gameDir, profileKey, profileName, versionId, vantaClientVersion, files, profileExists, List.of());
        }

        /** @return the files that are written */
        public List<PlannedFile> written() {
            return files.stream().filter(f -> !f.removal()).toList();
        }

        /** @return the files and directories that are removed */
        public List<PlannedFile> removed() {
            return files.stream().filter(PlannedFile::removal).toList();
        }
    }

    /**
     * Outcome of a setup.
     *
     * @param minecraftDir       official Minecraft directory
     * @param gameDir            VANTA instance directory
     * @param profileKey         profile key
     * @param profileName        profile name
     * @param versionId          Fabric version id
     * @param created            whether the profile was new (else updated)
     * @param written            every file written (backups created by this run included)
     * @param backups            backups created by this run (empty when they already existed)
     * @param vantaClientVersion installed VANTA client version ({@code dev} for a local jar)
     * @param vantaClientJar     the active VANTA client jar
     * @param notes              remarks about the performance pack (what was skipped and why)
     */
    public record Result(Path minecraftDir, Path gameDir, String profileKey, String profileName, String versionId, boolean created,
                         List<Path> written, List<Path> backups, String vantaClientVersion, Path vantaClientJar, List<String> notes) {

        public Result {
            written = List.copyOf(written);
            backups = List.copyOf(backups);
            notes = List.copyOf(notes);
        }

        /**
         * A result without notes.
         *
         * @param minecraftDir       official Minecraft directory
         * @param gameDir            VANTA instance directory
         * @param profileKey         profile key
         * @param profileName        profile name
         * @param versionId          Fabric version id
         * @param created            whether the profile was new
         * @param written            files written
         * @param backups            backups created
         * @param vantaClientVersion VANTA client version
         * @param vantaClientJar     active client jar
         */
        public Result(final Path minecraftDir, final Path gameDir, final String profileKey, final String profileName, final String versionId,
                      final boolean created, final List<Path> written, final List<Path> backups, final String vantaClientVersion,
                      final Path vantaClientJar) {
            this(minecraftDir, gameDir, profileKey, profileName, versionId, created, written, backups, vantaClientVersion, vantaClientJar,
                List.of());
        }

        /** @return what the player does next */
        public String nextStep() {
            return OfficialProfileService.nextStep(profileName);
        }
    }

    // ---------------------------------------------------------------- operations

    /**
     * Describes exactly what {@link #install} will write and remove. Reads the profiles files and, for the published
     * release, fetches the client release manifest (one small GET) so the client jar is named exactly; changes nothing.
     * A release that cannot be installed (not published, no SHA-256) or a missing local jar is reported here already,
     * before the user is asked to confirm anything.
     *
     * @param request request
     * @return plan
     * @throws OfficialLauncherNotFoundException when neither profiles file exists
     * @throws InstallException                  (step {@link InstallStep#VANTA_CLIENT}) when the client release manifest
     *                                           cannot be fetched or the release is not installable
     * @throws IOException                       when a profiles file is unreadable or not a JSON object with an object
     *                                           {@code profiles}
     * @throws InterruptedException              when interrupted
     */
    public Plan plan(final Request request) throws IOException, InterruptedException {
        final Path mc = request.minecraftDir();
        final List<Path> profileFiles = existingProfileFiles(mc);
        final String key = profileKey(request.minecraftVersion());
        boolean exists = false;
        for (Path file : profileFiles) {
            exists |= profilesObject(readProfiles(file), file).has(key);
        }
        final ClientSource client = resolveClient(request);
        final List<PlannedFile> files = files(request, client.changes(), profileFiles);
        final List<String> notes = new ArrayList<>();
        if (request.includePerformancePack() && performancePack != null) {
            // Listed right after the VANTA Client jar: the pack goes into the same mods/ folder.
            final List<PlannedFile> pack = packFiles(notes);
            int insertAt = 0;
            for (int i = 0; i < files.size(); i++) {
                final Kind k = files.get(i).kind();
                if (k == Kind.FABRIC_API || k == Kind.VANTA_CLIENT || k == Kind.VANTA_CLIENT_LOCAL || k == Kind.REPLACED_JAR) {
                    insertAt = i + 1;
                }
            }
            files.addAll(insertAt, pack);
        }
        return new Plan(mc, paths.instanceDir(), key, profileName(request.minecraftVersion()), request.versionId(), client.version(),
            files, exists, notes);
    }

    /**
     * The performance pack files (and older versions of them that are replaced), resolved from Modrinth.
     *
     * @param notes receives what will be skipped
     * @return planned files
     */
    private List<PlannedFile> packFiles(final List<String> notes) throws InterruptedException {
        final ModrinthService.Resolution resolution = performancePack.resolve(CancellationToken.NONE);
        notes.addAll(resolution.warnings());
        final ModrinthIndex index = ModrinthIndex.load(paths.modrinthIndexFile());
        final List<PlannedFile> out = new ArrayList<>();
        for (ModrinthService.ResolvedItem item : resolution.items()) {
            final java.util.Optional<ModrinthIndex.Entry> existing = index.byProjectId(item.projectId());
            final boolean enabled = existing.map(ModrinthIndex.Entry::enabled).orElse(true);
            final Path target = paths.instanceDir().resolve(item.relativeFile() + (enabled ? "" : ModrinthIndex.DISABLED_SUFFIX));
            out.add(new PlannedFile(Kind.PERFORMANCE_MOD, target.toString(), item.label()));
            if (existing.isPresent() && !existing.get().file().equals(item.relativeFile())) {
                out.add(new PlannedFile(Kind.REPLACED_JAR, existing.get().path(paths.instanceDir()).toString(), existing.get().title()));
            }
        }
        return out;
    }

    /**
     * Sets up the profile (see the class description). Nothing in the Minecraft directory is touched before every
     * download succeeded; the profile is written last, so it never points at a missing version.
     *
     * @param request  request
     * @param listener progress (steps: Fabric profile, Fabric API, VANTA client, finish)
     * @param token    cancellation
     * @return result
     * @throws OfficialLauncherNotFoundException when neither profiles file exists (nothing was changed)
     * @throws InstallException                  wrapping the failure of a step (network, integrity, not published ...)
     * @throws IOException                       when a profiles file is malformed (nothing was changed)
     * @throws InterruptedException              when interrupted
     */
    public Result install(final Request request, final InstallListener listener, final CancellationToken token)
        throws IOException, InterruptedException {
        final InstallListener l = listener == null ? InstallListener.NONE : listener;
        final CancellationToken t = token == null ? CancellationToken.NONE : token;
        final boolean withPack = request.includePerformancePack() && performancePack != null;
        final List<InstallStep> steps = withPack ? STEPS_WITH_PACK : STEPS;
        final Path mc = request.minecraftDir();
        // Fail fast on a malformed profiles file before anything is downloaded. The parsed tree is not kept: the
        // Minecraft Launcher may still be running and save the file while VANTA downloads, so FINALIZE re-reads it.
        final Map<Path, String> profileSnapshots = new LinkedHashMap<>();
        for (Path file : existingProfileFiles(mc)) {
            profilesObject(readProfiles(file), file);
            profileSnapshots.put(file, Files.readString(file, StandardCharsets.UTF_8));
        }
        final String versionId = request.versionId();
        final String key = profileKey(request.minecraftVersion());
        final String name = profileName(request.minecraftVersion());
        final List<Path> written = new ArrayList<>();
        final List<Path> backups = new ArrayList<>();

        // 1. Fabric Loader version JSON (downloaded first, written at the end)
        progress(l, steps, InstallStep.FABRIC_PROFILE, 0, 1, null);
        t.throwIfCancelled();
        final URI profileUrl = fabric.profileUrl(request.minecraftVersion(), request.fabricLoaderVersion());
        final JsonObject fabricProfile;
        try {
            fabricProfile = fabric.fetchProfileTree(request.minecraftVersion(), request.fabricLoaderVersion());
        } catch (IOException e) {
            throw failed(InstallStep.FABRIC_PROFILE, profileUrl, e);
        }
        progress(l, steps, InstallStep.FABRIC_PROFILE, 1, 1, versionId);

        // 2. Fabric API into the instance's mods/
        progress(l, steps, InstallStep.FABRIC_API, 0, 1, null);
        t.throwIfCancelled();
        final Path apiJar;
        try {
            Files.createDirectories(paths.modsDir());
            final DownloadRequest apiRequest = fabricApi.request(request.fabricApiVersion());
            downloader.download(apiRequest, fileProgress(l, steps, InstallStep.FABRIC_API), t);
            fabricApi.pruneOtherVersions(request.fabricApiVersion()).forEach(p -> l.onLog("Removed old " + p.getFileName()));
            apiJar = apiRequest.target();
        } catch (IOException e) {
            throw failed(InstallStep.FABRIC_API, fabricApi.url(request.fabricApiVersion()), e);
        }
        written.add(apiJar);
        progress(l, steps, InstallStep.FABRIC_API, 1, 1, apiJar.getFileName().toString());

        // 3. VANTA client into the instance's mods/
        progress(l, steps, InstallStep.VANTA_CLIENT, 0, 1, null);
        t.throwIfCancelled();
        final ClientSource client = resolveClient(request);
        final Path clientJar;
        try {
            if (client.manifest() == null) {
                clientJar = vantaClient.installLocalJar(request.localClientJar());
                l.onLog("Installed local client jar " + request.localClientJar());
            } else {
                clientJar = vantaClient.installFromManifest(client.manifest(), fileProgress(l, steps, InstallStep.VANTA_CLIENT), t);
            }
        } catch (IOException e) {
            throw failed(InstallStep.VANTA_CLIENT, null, e);
        }
        written.add(clientJar);
        written.addAll(client.changes().keptFiles());
        client.changes().instanceFile().ifPresent(written::add);
        progress(l, steps, InstallStep.VANTA_CLIENT, 1, 1, clientJar.getFileName().toString());

        // 4. Performance pack from Modrinth into the same mods/ (never fails the setup)
        final List<String> notes = new ArrayList<>();
        if (withPack) {
            progress(l, steps, InstallStep.PERFORMANCE_PACK, 0, 0, "Asking Modrinth for the newest versions for Minecraft "
                + request.minecraftVersion());
            final int[] total = {0};
            final int[] done = {0};
            final PerformancePack.Outcome pack = performancePack.install(new DownloadProgressListener() {
                @Override
                public void onProgress(final DownloadRequest r, final long bytesDone, final long bytesTotal) {
                    if (bytesDone == bytesTotal || bytesDone == 0) {
                        progress(l, steps, InstallStep.PERFORMANCE_PACK, done[0], total[0], "Downloading " + r.description());
                    }
                }

                @Override
                public void onComplete(final DownloadRequest r, final boolean skipped, final long bytes) {
                    done[0]++;
                    progress(l, steps, InstallStep.PERFORMANCE_PACK, done[0], total[0], (skipped ? "Verified " : "Downloaded ") + r.description());
                }
            }, l::onLog, count -> {
                total[0] = count;
                progress(l, steps, InstallStep.PERFORMANCE_PACK, 0, count, null);
            }, t);
            notes.addAll(pack.warnings());
            written.addAll(pack.files());
            progress(l, steps, InstallStep.PERFORMANCE_PACK, total[0], total[0], pack.applied().size() + " mods in place");
        }

        // 5. Version files and profiles in the official directory
        progress(l, steps, InstallStep.FINALIZE, 0, 1, null);
        t.throwIfCancelled();
        final boolean created;
        try {
            Files.createDirectories(paths.instanceDir());
            // Tells the client which Minecraft folder this profile belongs to, so Singleplayer lists its worlds.
            if (MinecraftFolderHint.write(paths.instanceDir(), mc)) {
                written.add(MinecraftFolderHint.file(paths.instanceDir()));
            }
            final Path versionDir = mc.resolve("versions").resolve(versionId);
            final Path versionJson = versionDir.resolve(versionId + ".json");
            AtomicFiles.writeString(versionJson, Json.treeToJson(fabricProfile) + System.lineSeparator());
            written.add(versionJson);
            final Path versionJar = versionDir.resolve(versionId + ".jar");
            if (!Files.isRegularFile(versionJar) || Files.size(versionJar) != 0L) {
                AtomicFiles.write(versionJar, new byte[0]);
            }
            written.add(versionJar);

            final JsonObject profile = profile(name, versionId, request.memoryMb());
            boolean isNew = true;
            for (Map.Entry<Path, String> entry : profileSnapshots.entrySet()) {
                final Path file = entry.getKey();
                // Read again right before writing: only the VANTA profile is merged into whatever the file holds now,
                // so a profile, account or setting the running Minecraft Launcher saved meanwhile survives.
                if (!entry.getValue().equals(Files.readString(file, StandardCharsets.UTF_8))) {
                    l.onLog(file.getFileName() + " changed while VANTA was downloading; the profile is merged into the current file");
                }
                final JsonObject root = readProfiles(file);
                isNew &= !profilesObject(root, file).has(key);
                writeProfiles(file, root, key, profile).ifPresent(backup -> {
                    backups.add(backup);
                    written.add(backup);
                });
                written.add(file);
            }
            created = isNew;
        } catch (IOException e) {
            throw failed(InstallStep.FINALIZE, null, e);
        }
        final List<Path> profileFiles = List.copyOf(profileSnapshots.keySet());
        progress(l, steps, InstallStep.FINALIZE, 1, 1, profileFiles.get(0).getFileName().toString());
        for (Path file : profileFiles) {
            l.onLog((created ? "Added" : "Updated") + " the profile '" + name + "' in " + file);
        }
        return new Result(mc, paths.instanceDir(), key, name, versionId, created, written, backups, client.version(), clientJar, notes);
    }

    // ---------------------------------------------------------------- profiles file

    /**
     * Builds the VANTA profile entry (without {@code created}; that is kept from an existing entry).
     *
     * @param name      profile name
     * @param versionId Fabric version id
     * @param memoryMb  heap in MiB
     * @return profile JSON
     */
    JsonObject profile(final String name, final String versionId, final int memoryMb) {
        final String now = ISO_INSTANT_MILLIS.format(clock.instant());
        final JsonObject p = new JsonObject();
        p.addProperty("name", name);
        p.addProperty("type", "custom");
        p.addProperty("created", now);
        p.addProperty("lastUsed", now);
        p.addProperty("icon", "data:image/png;base64," + Base64.getEncoder().encodeToString(icon.get()));
        p.addProperty("lastVersionId", versionId);
        p.addProperty("gameDir", paths.instanceDir().toAbsolutePath().toString());
        p.addProperty("javaArgs", "-Xmx" + memoryMb + "M");
        return p;
    }

    /**
     * Adds or updates a profile in a parsed profiles document. Keys of an existing entry that VANTA does not manage
     * (for example a custom {@code javaDir} or {@code resolution}) and its {@code created} time are kept.
     *
     * @param root    parsed {@code launcher_profiles.json}
     * @param key     profile key
     * @param profile VANTA profile entry
     * @return whether the profile was new
     * @throws IOException when {@code profiles} exists but is not an object
     */
    static boolean upsertProfile(final JsonObject root, final String key, final JsonObject profile) throws IOException {
        final JsonObject profiles = profilesObject(root, null);
        final JsonElement existing = profiles.get(key);
        final JsonObject merged = existing != null && existing.isJsonObject() ? existing.getAsJsonObject().deepCopy() : new JsonObject();
        final boolean keepCreated = merged.has("created") && merged.get("created").isJsonPrimitive()
            && !merged.get("created").getAsString().isBlank();
        for (var e : profile.entrySet()) {
            if ("created".equals(e.getKey()) && keepCreated) {
                continue;
            }
            merged.add(e.getKey(), e.getValue().deepCopy());
        }
        profiles.add(key, merged);
        return existing == null;
    }

    private Optional<Path> writeProfiles(final Path file, final JsonObject root, final String key, final JsonObject profile) throws IOException {
        upsertProfile(root, key, profile);
        final Path backup = backupOf(file);
        Optional<Path> created = Optional.empty();
        if (!Files.exists(backup)) {
            Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
            created = Optional.of(backup);
        }
        // A dedicated serialiser: members whose value is null belong to the user's file and must survive.
        AtomicFiles.writeString(file, Json.treeToJson(root) + System.lineSeparator());
        return created;
    }

    /**
     * @param profilesFile a profiles file
     * @return its one-time backup, e.g. {@code launcher_profiles.json.vanta-backup}
     */
    public static Path backupOf(final Path profilesFile) {
        return profilesFile.resolveSibling(profilesFile.getFileName() + BACKUP_SUFFIX);
    }

    /**
     * The profiles files of the official launcher that exist in a Minecraft directory: {@code launcher_profiles.json}
     * (Java Edition launcher from minecraft.net) and/or {@code launcher_profiles_microsoft_store.json} (the launcher from
     * the Microsoft Store / Xbox app). VANTA writes its profile into every one that exists (the official Fabric
     * installer instead asks which launcher to use when both exist and writes only that one).
     *
     * @param minecraftDir Minecraft directory
     * @return existing profiles files, {@code launcher_profiles.json} first
     * @throws OfficialLauncherNotFoundException when neither file exists (the launcher was never started there)
     */
    public static List<Path> existingProfileFiles(final Path minecraftDir) throws OfficialLauncherNotFoundException {
        final List<Path> files = new ArrayList<>(2);
        for (String name : List.of(PROFILES_FILE, STORE_PROFILES_FILE)) {
            final Path file = minecraftDir.resolve(name);
            if (Files.isRegularFile(file)) {
                files.add(file);
            }
        }
        if (files.isEmpty()) {
            throw new OfficialLauncherNotFoundException(minecraftDir);
        }
        return List.copyOf(files);
    }

    private static JsonObject readProfiles(final Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException(file + " does not exist");
        }
        final String text = Files.readString(file, StandardCharsets.UTF_8);
        final JsonElement tree;
        try {
            tree = JsonParser.parseString(text);
        } catch (JsonParseException e) {
            throw new IOException(file + " is not valid JSON, so VANTA did not change it: " + e.getMessage(), e);
        }
        if (!tree.isJsonObject()) {
            throw new IOException(file + " is not a JSON object, so VANTA did not change it");
        }
        return tree.getAsJsonObject();
    }

    private static JsonObject profilesObject(final JsonObject root, final Path file) throws IOException {
        final JsonElement profiles = root.get("profiles");
        if (profiles == null || profiles.isJsonNull()) {
            final JsonObject empty = new JsonObject();
            root.add("profiles", empty);
            return empty;
        }
        if (!profiles.isJsonObject()) {
            throw new IOException((file == null ? PROFILES_FILE : file.toString())
                + " has a \"profiles\" value that is not an object, so VANTA did not change it");
        }
        return profiles.getAsJsonObject();
    }

    // ---------------------------------------------------------------- plan helpers

    /**
     * The VANTA client that is installed: the published release (manifest fetched) or the local jar.
     *
     * @param manifest client release manifest, null for a local jar
     * @param version  client version ({@code dev} for a local jar)
     * @param changes  what installing it changes in the VANTA data directory
     */
    private record ClientSource(ReleaseManifest manifest, String version, VantaClientService.ClientChanges changes) {
    }

    private ClientSource resolveClient(final Request request) throws IOException, InterruptedException {
        URI manifestUrl = null;
        try {
            final Optional<Path> local = request.localClientJarOverride();
            if (local.isPresent()) {
                return new ClientSource(null, VantaClientService.DEV_VERSION, vantaClient.changesForLocalJar(local.get()));
            }
            manifestUrl = vantaClient.clientManifestUrl();
            final ReleaseManifest manifest = vantaClient.fetchClientManifest();
            return new ClientSource(manifest, manifest.version(), vantaClient.changesForRelease(manifest));
        } catch (IOException e) {
            throw failed(InstallStep.VANTA_CLIENT, manifestUrl, e);
        }
    }

    private List<PlannedFile> files(final Request request, final VantaClientService.ClientChanges client, final List<Path> profileFiles)
        throws IOException {
        final Path mc = request.minecraftDir();
        final String versionId = request.versionId();
        final Path versionDir = mc.resolve("versions").resolve(versionId);
        final List<PlannedFile> files = new ArrayList<>();
        files.add(new PlannedFile(Kind.FABRIC_API, fabricApi.targetJar(request.fabricApiVersion()).toString()));
        for (Path old : fabricApi.otherVersions(request.fabricApiVersion())) {
            files.add(new PlannedFile(Kind.REPLACED_JAR, old.toString()));
        }
        files.add(new PlannedFile(request.localClientJar() == null ? Kind.VANTA_CLIENT : Kind.VANTA_CLIENT_LOCAL, client.activeJar().toString()));
        for (Path old : client.replacedJars()) {
            files.add(new PlannedFile(Kind.REPLACED_JAR, old.toString()));
        }
        for (Path kept : client.keptFiles()) {
            files.add(new PlannedFile(Kind.CLIENT_ROLLBACK_COPY, kept.toString()));
        }
        for (Path pruned : client.prunedVersions()) {
            files.add(new PlannedFile(Kind.PRUNED_ROLLBACK_COPY, pruned.toString()));
        }
        client.instanceFile().ifPresent(instance -> files.add(new PlannedFile(Kind.INSTANCE_RECORD, instance.toString())));
        files.add(new PlannedFile(Kind.VERSION_JSON, versionDir.resolve(versionId + ".json").toString()));
        files.add(new PlannedFile(Kind.VERSION_JAR, versionDir.resolve(versionId + ".jar").toString()));
        for (Path file : profileFiles) {
            if (STORE_PROFILES_FILE.equals(file.getFileName().toString())) {
                addProfiles(files, file, Kind.STORE_PROFILES_BACKUP, Kind.STORE_PROFILES);
            } else {
                addProfiles(files, file, Kind.PROFILES_BACKUP, Kind.PROFILES);
            }
        }
        if (MinecraftFolderHint.wouldChange(paths.instanceDir(), mc)) {
            files.add(new PlannedFile(Kind.MINECRAFT_FOLDER_NOTE, MinecraftFolderHint.file(paths.instanceDir()).toString()));
        }
        return files;
    }

    private static void addProfiles(final List<PlannedFile> files, final Path file, final Kind backupKind, final Kind kind) {
        final Path backup = backupOf(file);
        if (!Files.exists(backup)) {
            files.add(new PlannedFile(backupKind, backup.toString()));
        }
        files.add(new PlannedFile(kind, file.toString()));
    }

    // ---------------------------------------------------------------- helpers

    private static void progress(final InstallListener listener, final List<InstallStep> steps, final InstallStep step, final long done,
                                 final long total, final String message) {
        listener.onProgress(new InstallProgress(step, steps.indexOf(step), steps.size(), done, total, 0L, message));
    }

    /**
     * @return a listener that names the file being downloaded or verified in the step's progress message
     */
    private static DownloadProgressListener fileProgress(final InstallListener listener, final List<InstallStep> steps, final InstallStep step) {
        return new DownloadProgressListener() {
            private boolean announced;

            @Override
            public void onProgress(final DownloadRequest r, final long bytesDone, final long bytesTotal) {
                if (!announced) {
                    announced = true;
                    progress(listener, steps, step, 0, 1, "Downloading " + r.description());
                }
            }

            @Override
            public void onComplete(final DownloadRequest r, final boolean skipped, final long bytes) {
                progress(listener, steps, step, 0, 1, (skipped ? "Verified " : "Downloaded ") + r.description());
            }
        };
    }

    private static InstallException failed(final InstallStep step, final URI url, final IOException e) {
        if (e instanceof InstallException ie) {
            return ie;
        }
        final String detail = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
        return new InstallException(step, step.label() + " failed" + (url == null ? "" : " (" + url + ")") + ": " + detail, e);
    }
}
