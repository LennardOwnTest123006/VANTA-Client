package dev.vanta.launcher.ui.backend;

import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.auth.AuthException;
import dev.vanta.launcher.core.auth.DeviceCode;
import dev.vanta.launcher.core.install.InstallException;
import dev.vanta.launcher.core.install.InstallListener;
import dev.vanta.launcher.core.install.InstalledClient;
import dev.vanta.launcher.core.install.InstallRequest;
import dev.vanta.launcher.core.install.OfficialLauncher;
import dev.vanta.launcher.core.install.OfficialProfileService;
import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.core.java.JavaInstall;
import dev.vanta.launcher.core.launch.GameProcess;
import dev.vanta.launcher.core.launch.LaunchRequest;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.modrinth.ContentType;
import dev.vanta.launcher.core.modrinth.ModrinthModels;
import dev.vanta.launcher.core.modrinth.ModrinthService;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.core.settings.ReleasesBaseUrl;
import dev.vanta.launcher.core.update.UpdateInfo;
import dev.vanta.launcher.core.update.UpdateService;
import dev.vanta.launcher.core.util.LauncherPackaging;
import dev.vanta.launcher.core.util.OsInfo;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

/**
 * The single seam between the JavaFX UI and the launcher core.
 *
 * <p>Every method is a thin, blocking call into {@code dev.vanta.launcher.core}; {@link CoreBackend} is the
 * production implementation over {@link dev.vanta.launcher.core.LauncherServices}. View models never touch core
 * services directly, which keeps them testable with a fake backend and keeps all decisions (what to download, how
 * to verify, who may play offline) inside the core.</p>
 *
 * <p>All methods may block and must be called from a background thread by the view models.</p>
 */
public interface LauncherBackend extends AutoCloseable {

    // ---------------------------------------------------------------- environment

    /** @return launcher paths */
    LauncherPaths paths();

    /** @return host platform */
    OsInfo os();

    /** @return environment variables */
    Map<String, String> env();

    /** @return clock */
    Clock clock();

    /** @return physical memory in MiB when known */
    OptionalLong totalMemoryMb();

    // ---------------------------------------------------------------- settings

    /** @return current settings */
    LauncherSettings settings();

    /** @return defaults for this machine */
    LauncherSettings defaultSettings();

    /**
     * Persists and activates settings.
     *
     * @param settings settings
     * @throws IOException on failure
     */
    void saveSettings(LauncherSettings settings) throws IOException;

    // ---------------------------------------------------------------- accounts

    /** @return stored accounts */
    List<Account> accounts();

    /** @return the active account */
    Optional<Account> activeAccount();

    /**
     * Makes an account active.
     *
     * @param uuid account uuid
     * @throws IOException on failure
     */
    void setActiveAccount(String uuid) throws IOException;

    /**
     * Removes an account.
     *
     * @param uuid account uuid
     * @throws IOException on failure
     */
    void removeAccount(String uuid) throws IOException;

    /** @return whether a Microsoft client id is configured */
    boolean signInConfigured();

    /**
     * Starts the device code flow.
     *
     * @return device code
     * @throws AuthException        on refusal / not configured
     * @throws IOException          on transport failure
     * @throws InterruptedException when interrupted
     */
    DeviceCode beginSignIn() throws AuthException, IOException, InterruptedException;

    /**
     * Polls for approval, completes the Xbox/Minecraft chain and stores the account.
     *
     * @param code  device code
     * @param token cancellation
     * @return signed-in account
     * @throws AuthException        on refusal
     * @throws IOException          on transport failure
     * @throws InterruptedException when interrupted
     */
    Account completeSignIn(DeviceCode code, CancellationToken token) throws AuthException, IOException, InterruptedException;

    /**
     * Refreshes an account whose access token expired and stores it.
     *
     * @param account account
     * @return fresh account (the same instance when no refresh was needed)
     * @throws AuthException        when the refresh fails
     * @throws IOException          on transport failure
     * @throws InterruptedException when interrupted
     */
    Account refreshIfExpired(Account account) throws AuthException, IOException, InterruptedException;

    /** @return whether the core policy allows an offline session right now */
    boolean offlineSessionAllowed();

    /**
     * Creates an offline session according to the core policy.
     *
     * @return offline account
     * @throws AuthException when offline play is not allowed
     */
    Account createOfflineSession() throws AuthException;

    // ---------------------------------------------------------------- java

    /** @return detected runtimes, newest first */
    List<JavaInstall> detectJava();

    /**
     * Probes a user supplied path.
     *
     * @param path Java home, bin directory or executable
     * @return runtime when valid
     */
    Optional<JavaInstall> probeJava(Path path);

    /**
     * Chooses the runtime to launch with: the configured override when valid, else the best detected one.
     *
     * @param detected detected runtimes
     * @return runtime satisfying Java 21
     */
    Optional<JavaInstall> pickJava(List<JavaInstall> detected);

    /**
     * Downloads, verifies and installs Eclipse Temurin 21.
     *
     * @param listener progress
     * @param token    cancellation
     * @return runtime
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    JavaInstall installJava(DownloadProgressListener listener, CancellationToken token) throws IOException, InterruptedException;

    // ---------------------------------------------------------------- install & launch

    /**
     * @return installed instance metadata
     * @throws IOException when unreadable
     */
    Optional<InstanceInfo> loadInstance() throws IOException;

    /**
     * @param request request
     * @return instance when one matching the request exists (digests not re-verified)
     * @throws IOException when unreadable
     */
    Optional<InstanceInfo> findInstalled(InstallRequest request) throws IOException;

    /**
     * Installs or verifies the instance.
     *
     * @param request  request
     * @param listener progress
     * @param token    cancellation
     * @return instance
     * @throws InstallException     on failure
     * @throws InterruptedException when interrupted
     */
    InstanceInfo install(InstallRequest request, InstallListener listener, CancellationToken token) throws InstallException, InterruptedException;

    /**
     * Builds the command and starts the game.
     *
     * @param request request
     * @param output  receives every output line
     * @return running game
     * @throws IOException when the command cannot be built or started
     */
    RunningGame launch(LaunchRequest request, Consumer<GameProcess.Line> output) throws IOException;

    // ---------------------------------------------------------------- client versions & updates

    /**
     * @return the active VANTA client jar in {@code mods/}
     * @throws IOException on failure
     */
    Optional<Path> activeClientJar() throws IOException;

    /**
     * @return client versions kept for rollback
     * @throws IOException on failure
     */
    List<VantaClientService.KeptVersion> keptClientVersions() throws IOException;

    /**
     * Activates a kept client version.
     *
     * @param version version
     * @return active jar
     * @throws IOException on failure
     */
    Path rollbackClient(String version) throws IOException;

    /** @return whether the releases base URL in effect is a usable http(s) URL */
    boolean updatesConfigured();

    /**
     * @return the releases base URL in effect (settings &gt; {@code VANTA_RELEASES_BASE_URL} &gt; built-in default)
     *     and where it came from
     */
    ReleasesBaseUrl releasesBaseUrl();

    /**
     * @return launcher update when newer
     * @throws IOException          on failure (including not configured / not published)
     * @throws InterruptedException when interrupted
     */
    Optional<UpdateInfo> checkLauncherUpdate() throws IOException, InterruptedException;

    /**
     * The VANTA client actually installed (instance.json / the jar in {@code mods/}); the one source the Home card, the
     * update banner, the update dialog and {@code --check-update} use.
     *
     * @return installed client, empty when none is installed
     * @throws IOException when unreadable
     */
    Optional<InstalledClient> installedClient() throws IOException;

    /**
     * Checks the client release against an installed state. When no client is installed the result names the latest
     * release and never carries an update.
     *
     * @param installed installed client (from {@link #installedClient()})
     * @return latest release and the update, if any
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    UpdateService.ClientCheck checkClient(Optional<InstalledClient> installed) throws IOException, InterruptedException;

    /** @return how the running launcher was installed (selects the self-update file and its instructions) */
    LauncherPackaging packaging();

    /**
     * Downloads and verifies an update file.
     *
     * @param update   update
     * @param listener progress
     * @param token    cancellation
     * @return verified file
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    Path downloadUpdate(UpdateInfo update, DownloadProgressListener listener, CancellationToken token) throws IOException, InterruptedException;

    /**
     * Re-verifies a downloaded launcher installer. The UI asks the user before opening the returned file.
     *
     * @param update update
     * @return installer
     * @throws IOException when missing or failing verification
     */
    Path prepareInstaller(UpdateInfo update) throws IOException;

    /**
     * Installs a client update and keeps the previous version for rollback.
     *
     * @param update   update
     * @param listener progress
     * @param token    cancellation
     * @return active jar
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    Path installClientUpdate(UpdateInfo update, DownloadProgressListener listener, CancellationToken token) throws IOException, InterruptedException;

    /**
     * Fetches a small text document (release notes) through the launcher's HTTP transport.
     *
     * @param uri URL
     * @return text
     * @throws IOException          on failure or non-2xx status
     * @throws InterruptedException when interrupted
     */
    String fetchText(URI uri) throws IOException, InterruptedException;

    // ---------------------------------------------------------------- official Minecraft Launcher

    /**
     * Describes exactly what "Use with the Minecraft Launcher" writes and removes (the detected official Minecraft
     * directory and the VANTA instance). Fetches the client release manifest so the client jar is named exactly;
     * changes nothing.
     *
     * @return plan
     * @throws IOException          when the official launcher was never started ({@code launcher_profiles.json} missing,
     *                              {@link dev.vanta.launcher.core.install.OfficialLauncherNotFoundException}), the file is
     *                              unreadable, or the client release cannot be resolved (not published, network)
     * @throws InterruptedException when interrupted
     */
    OfficialProfileService.Plan officialProfilePlan() throws IOException, InterruptedException;

    /**
     * Installs Fabric API and the VANTA client into the instance and adds the VANTA profile to the official
     * Minecraft Launcher (see {@link OfficialProfileService}).
     *
     * @param listener progress
     * @param token    cancellation
     * @return result
     * @throws IOException          on failure (nothing in the Minecraft directory is changed before all downloads succeeded)
     * @throws InterruptedException when interrupted
     */
    OfficialProfileService.Result installOfficialProfile(InstallListener listener, CancellationToken token) throws IOException, InterruptedException;

    /**
     * @return the official Minecraft Launcher processes that run now, described as "MinecraftLauncher.exe (process 1234)"
     *     (empty when none runs); the official launcher reads its profiles only when it starts
     */
    List<String> runningOfficialLaunchers();

    /**
     * Starts the official Minecraft Launcher (never stops one) and waits briefly until it runs.
     *
     * @return outcome
     * @throws InterruptedException when interrupted
     */
    OfficialLauncher.OpenResult openOfficialLauncher() throws InterruptedException;

    // ---------------------------------------------------------------- Modrinth content (Mods page)

    /**
     * @param type   content type
     * @param query  search text (blank: most downloaded)
     * @param offset offset
     * @return one page of Modrinth results for Minecraft 1.21.11
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    ModrinthModels.SearchPage searchModrinth(ContentType type, String query, int offset) throws IOException, InterruptedException;

    /**
     * Installs a Modrinth project and its required dependencies into the VANTA instance.
     *
     * @param type      content type
     * @param projectId project id or slug
     * @param downloads byte progress
     * @param log       log lines
     * @param token     cancellation
     * @return what was installed
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    ModrinthService.ApplyResult installFromModrinth(ContentType type, String projectId, DownloadProgressListener downloads,
                                                    Consumer<String> log, CancellationToken token) throws IOException, InterruptedException;

    /**
     * @return mods, shader packs and resource packs in the VANTA instance
     * @throws IOException on failure
     */
    List<ModrinthService.InstalledContent> installedContent() throws IOException;

    /**
     * @param item    content
     * @param enabled new state
     * @throws IOException on failure or when refused
     */
    void setContentEnabled(ModrinthService.InstalledContent item, boolean enabled) throws IOException;

    /**
     * @param item content
     * @throws IOException on failure or when refused
     */
    void removeContent(ModrinthService.InstalledContent item) throws IOException;

    /**
     * Updates every Modrinth project in the instance to its newest compatible version.
     *
     * @param downloads byte progress
     * @param log       log lines
     * @param token     cancellation
     * @return outcome
     * @throws IOException          when Modrinth cannot be reached
     * @throws InterruptedException when interrupted
     */
    ModrinthService.ApplyResult updateAllContent(DownloadProgressListener downloads, Consumer<String> log, CancellationToken token)
        throws IOException, InterruptedException;

    /**
     * Deletes {@code <instance>/config/vanta/restart.request} when the game wrote it ("Restart game" in VANTA).
     *
     * @return whether the game asked to be started again
     * @throws IOException when the marker cannot be deleted
     */
    boolean consumeRestartRequest() throws IOException;

    // ---------------------------------------------------------------- misc

    /**
     * @param file file
     * @return lower-case hex SHA-256
     * @throws IOException on failure
     */
    String sha256(Path file) throws IOException;

    /** Releases resources (HTTP client, executors). */
    @Override
    void close();
}
