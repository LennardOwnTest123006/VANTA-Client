package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.ai.LocalAiNote;
import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.core.log.Redactor;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.model.VersionJson;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.core.settings.Resolution;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.OsInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Assembles the game command line from the installed version JSONs and starts the process.
 *
 * <p>The command is: {@code java <jvm args> <main class> <game args>} where the main class comes from the Fabric
 * profile ({@code net.fabricmc.loader.impl.launch.knot.KnotClient}), the class path is Fabric libraries +
 * vanilla libraries + client jar, and the game arguments come from {@code arguments.game} with the standard
 * placeholders ({@code --username}, {@code --version vanta-1.21.11}, {@code --gameDir}, {@code --assetsDir},
 * {@code --assetIndex}, {@code --uuid}, {@code --accessToken}, {@code --clientId}, {@code --xuid},
 * {@code --userType}, {@code --versionType}, optional {@code --width/--height} and quick play).</p>
 */
public final class LaunchService {

    private static final Logger LOG = LauncherLog.get("Launch");
    private static final DateTimeFormatter LOG_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final LauncherPaths paths;
    private final OsInfo os;
    private final Clock clock;
    private final Optional<Path> officialMinecraftDir;

    /**
     * @param paths launcher paths
     * @param os    host platform
     */
    public LaunchService(final LauncherPaths paths, final OsInfo os) {
        this(paths, os, Clock.systemDefaultZone());
    }

    /**
     * @param paths launcher paths
     * @param os    host platform
     * @param clock clock for log file names
     */
    public LaunchService(final LauncherPaths paths, final OsInfo os, final Clock clock) {
        this(paths, os, clock, Optional.empty());
    }

    /**
     * @param paths                launcher paths
     * @param os                   host platform
     * @param clock                clock for log file names
     * @param officialMinecraftDir the official Minecraft folder when it exists; recorded for the client's
     *                             Singleplayer before each start unless a usable note exists ({@link MinecraftFolderHint})
     */
    public LaunchService(final LauncherPaths paths, final OsInfo os, final Clock clock, final Optional<Path> officialMinecraftDir) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.os = Objects.requireNonNull(os, "os");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.officialMinecraftDir = Objects.requireNonNull(officialMinecraftDir, "officialMinecraftDir");
    }

    /**
     * Loads the vanilla and Fabric version JSONs of an instance and merges them.
     *
     * @param instance instance
     * @return merged version
     * @throws IOException when a version JSON is missing or unreadable
     */
    public VersionJson loadMergedVersion(final InstanceInfo instance) throws IOException {
        final VersionJson vanilla = Json.read(paths.versionJson(instance.vanillaVersionId()), VersionJson.class);
        if (instance.fabricProfileId() == null || instance.fabricProfileId().isBlank()) {
            return vanilla;
        }
        final VersionJson fabric = Json.read(paths.versionJson(instance.fabricProfileId()), VersionJson.class);
        return fabric.mergeOnto(vanilla);
    }

    /**
     * Builds the command line without starting anything.
     *
     * @param request launch request
     * @return command
     * @throws IOException when version JSONs cannot be read or placeholders stay unresolved
     */
    public LaunchCommand buildCommand(final LaunchRequest request) throws IOException {
        final InstanceInfo instance = request.instance();
        final VersionJson vanilla = Json.read(paths.versionJson(instance.vanillaVersionId()), VersionJson.class);
        final VersionJson fabric = instance.fabricProfileId() == null || instance.fabricProfileId().isBlank()
            ? null : Json.read(paths.versionJson(instance.fabricProfileId()), VersionJson.class);
        final VersionJson merged = fabric == null ? vanilla : fabric.mergeOnto(vanilla);
        return buildCommand(request, vanilla, fabric, merged);
    }

    /**
     * Builds the command line from already loaded version JSONs.
     *
     * @param request launch request
     * @param vanilla vanilla version JSON
     * @param fabric  Fabric profile as version JSON ({@code null} for vanilla launches)
     * @param merged  merged version
     * @return command
     * @throws IOException when placeholders stay unresolved
     */
    public LaunchCommand buildCommand(final LaunchRequest request, final VersionJson vanilla, final VersionJson fabric,
                                      final VersionJson merged) throws IOException {
        final InstanceInfo instance = request.instance();
        final Account account = request.account();
        final LauncherSettings settings = request.settings();
        final LibraryResolver resolver = new LibraryResolver(os);
        final List<ResolvedLibrary> fabricLibs = fabric == null ? List.of() : resolver.resolve(fabric.libraries());
        final List<ResolvedLibrary> vanillaLibs = resolver.resolve(vanilla.libraries());
        final Path clientJar = paths.versionJar(instance.vanillaVersionId());
        final List<Path> classpath = new ClasspathBuilder(paths.librariesDir()).build(fabricLibs, vanillaLibs, clientJar);

        final Map<String, Boolean> features = new HashMap<>();
        features.put(GameArgsBuilder.FEATURE_DEMO, Boolean.FALSE);
        features.put(GameArgsBuilder.FEATURE_RESOLUTION, settings.resolutionOverride().isPresent());
        final boolean quickWorld = request.quickPlayWorld().isPresent();
        final boolean quickServer = !quickWorld && request.quickPlayServer().isPresent();
        features.put(GameArgsBuilder.FEATURE_QUICK_PLAYS, Boolean.FALSE);
        features.put(GameArgsBuilder.FEATURE_QUICK_PLAY_SINGLEPLAYER, quickWorld);
        features.put(GameArgsBuilder.FEATURE_QUICK_PLAY_MULTIPLAYER, quickServer);
        features.put(GameArgsBuilder.FEATURE_QUICK_PLAY_REALMS, Boolean.FALSE);

        final Map<String, String> values = new HashMap<>();
        values.put("auth_player_name", account.name());
        values.put("version_name", instance.instanceId());
        values.put("game_directory", paths.instanceDir().toString());
        values.put("assets_root", paths.assetsDir().toString());
        values.put("game_assets", paths.assetsDir().toString());
        values.put("assets_index_name", instance.assetIndexId());
        values.put("auth_uuid", account.undashedUuid());
        values.put("auth_access_token", account.accessToken().isEmpty() ? Account.OFFLINE_TOKEN : account.accessToken());
        values.put("auth_session", account.accessToken().isEmpty() ? Account.OFFLINE_TOKEN : account.accessToken());
        values.put("clientid", LauncherVersion.USER_AGENT);
        values.put("auth_xuid", account.xuid().isEmpty() ? "0" : account.xuid());
        values.put("user_type", account.userType());
        values.put("user_properties", "{}");
        values.put("version_type", "release");
        values.put("natives_directory", paths.nativesDir().toString());
        values.put("launcher_name", "VANTA-Launcher");
        values.put("launcher_version", LauncherVersion.VERSION);
        values.put("classpath", ClasspathBuilder.join(classpath, os.classpathSeparator()));
        values.put("classpath_separator", os.classpathSeparator());
        values.put("library_directory", paths.librariesDir().toString());
        values.put("primary_jar", clientJar.toString());
        settings.resolutionOverride().ifPresent(r -> {
            values.put("resolution_width", Integer.toString(r.width()));
            values.put("resolution_height", Integer.toString(r.height()));
        });
        request.quickPlayWorld().ifPresent(w -> values.put("quickPlaySingleplayer", w));
        request.quickPlayServer().ifPresent(s -> values.put("quickPlayMultiplayer", s));
        values.put("quickPlayPath", paths.logsDir().resolve("quickPlay.json").toString());
        values.put("quickPlayRealms", "");

        final ArgumentExpander expander = new ArgumentExpander(values);
        final RuleEvaluator jvmEvaluator = new RuleEvaluator(os);
        final RuleEvaluator gameEvaluator = new RuleEvaluator(os, features);
        final List<String> jvmArgs = new JvmArgsBuilder(jvmEvaluator, LauncherVersion.VERSION)
            .build(merged, expander, settings.memoryMb(), settings.jvmArgs());
        final List<String> gameArgs = new GameArgsBuilder(gameEvaluator).build(merged, expander);

        if (!expander.unresolved().isEmpty()) {
            throw new IOException("Unresolved launch placeholders: " + expander.unresolved());
        }
        final String mainClass = merged.mainClass() == null || merged.mainClass().isBlank()
            ? instance.mainClass() : merged.mainClass();
        if (mainClass == null || mainClass.isBlank()) {
            throw new IOException("The version JSON does not declare a main class");
        }

        final List<String> command = new ArrayList<>();
        command.add(request.javaExecutable().toString());
        command.addAll(jvmArgs);
        command.add(mainClass);
        command.addAll(gameArgs);

        final Set<String> secrets = new LinkedHashSet<>();
        if (!account.accessToken().isEmpty() && !Account.OFFLINE_TOKEN.equals(account.accessToken())) {
            secrets.add(account.accessToken());
        }
        return new LaunchCommand(command, paths.instanceDir(), Map.of(), secrets);
    }

    /**
     * Starts the game. A left-over {@code config/vanta/restart.request} in the game directory is deleted first, so only
     * a marker the process started here writes counts as a restart request after it exits (a marker nobody consumed,
     * for example because the launcher was closed while the game ran, must not restart the game after its next
     * normal quit).
     *
     * @param command   command
     * @param listeners output listeners
     * @return running process
     * @throws IOException when the process cannot be started
     */
    public GameProcess start(final LaunchCommand command, final List<Consumer<GameProcess.Line>> listeners) throws IOException {
        Files.createDirectories(command.workingDirectory());
        Files.createDirectories(paths.logsDir());
        if (Files.deleteIfExists(RestartRequest.markerFile(command.workingDirectory()))) {
            LOG.log(Level.INFO, "Removed a left-over {0} before the start", RestartRequest.FILE_NAME);
        }
        recordMinecraftFolder(command.workingDirectory());
        recordLocalAiFolder(command.workingDirectory());
        final Redactor redactor = Redactor.global();
        command.secrets().forEach(redactor::register);
        final ProcessBuilder builder = new ProcessBuilder(command.command())
            .directory(command.workingDirectory().toFile());
        builder.environment().putAll(command.environment());
        final String stamp = LocalDateTime.now(clock.withZone(ZoneId.systemDefault())).format(LOG_STAMP);
        final Path logFile = paths.logsDir().resolve("game-" + stamp + ".log");
        LOG.log(Level.INFO, "Starting game: {0}", command.toDisplayString());
        LOG.log(Level.INFO, "Game log: {0}", logFile);
        final GameProcess process = GameProcess.start(builder, logFile, redactor, listeners);
        process.exitCode().thenAccept(code -> LOG.log(Level.INFO, "Game exited with code {0}", code));
        return process;
    }

    /**
     * Tells the client which official Minecraft folder's worlds Singleplayer lists. A note written by "Use with the
     * Minecraft Launcher" (possibly for a custom {@code --minecraft-dir}) is kept; a failure only logs, the game starts
     * anyway and keeps its own worlds folder.
     */
    private void recordMinecraftFolder(final Path gameDir) {
        officialMinecraftDir.ifPresent(dir -> {
            try {
                if (MinecraftFolderHint.writeUnlessRecorded(gameDir, dir)) {
                    LOG.log(Level.INFO, "Recorded the Minecraft folder {0} for Singleplayer in {1}",
                        new Object[] {dir, MinecraftFolderHint.file(gameDir)});
                }
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not record the Minecraft folder for Singleplayer: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Tells the client where the launcher keeps the Local AI ({@code config/vanta/local-ai.json}), so the client starts
     * llama-server from there and never downloads a second copy. A failure only logs; the game starts anyway.
     */
    private void recordLocalAiFolder(final Path gameDir) {
        try {
            if (LocalAiNote.write(gameDir, paths.localAiDir())) {
                LOG.log(Level.INFO, "Recorded the Local AI folder {0} in {1}", new Object[] {paths.localAiDir(), LocalAiNote.file(gameDir)});
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not record the Local AI folder for the client: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience: build and start.
     *
     * @param request   request
     * @param listeners output listeners
     * @return running process
     * @throws IOException on failure
     */
    public GameProcess launch(final LaunchRequest request, final List<Consumer<GameProcess.Line>> listeners) throws IOException {
        return start(buildCommand(request), listeners);
    }

    /**
     * @param resolution resolution
     * @return the {@code --width}/{@code --height} pair (used by callers that build arguments manually)
     */
    public static List<String> resolutionArgs(final Resolution resolution) {
        return List.of("--width", Integer.toString(resolution.width()), "--height", Integer.toString(resolution.height()));
    }
}
