# VANTA Launcher

The VANTA Launcher installs and starts **Minecraft Java Edition 1.21.11** with **Fabric Loader 0.19.5**,
**Fabric API 0.141.6+1.21.11** and the **VANTA Client** mod, using only legitimate sources:

| Component | Source | Verification |
| --- | --- | --- |
| Minecraft version manifest, version JSON, client jar, libraries, assets | `piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net` | SHA-1 + size from Mojang metadata |
| Fabric Loader profile and libraries | `meta.fabricmc.net`, `maven.fabricmc.net` | SHA-256/SHA-1 from the profile or the Maven `.sha256`/`.sha1` sidecar |
| Fabric API | `maven.fabricmc.net` | SHA-256 sidecar |
| VANTA Client | release manifest (`<releasesBaseUrl>/client-latest.json`, see [Releases URL](#releases-url)); exactly `vanta-client-<version>.jar` | SHA-256 from the manifest |
| Java 21 runtime (optional) | Adoptium API (`api.adoptium.net`, Eclipse Temurin JRE) | SHA-256 from the API |
| Sign-in | Microsoft OAuth device code flow → Xbox Live → XSTS → Minecraft services | — |

Every download is written to a temporary file, verified while streaming and moved into place atomically. A file that
fails verification is deleted, reported and never used. Apart from the verified Java runtime it installed for you, the
only downloaded file the launcher hands to the operating system is a verified launcher installer (Windows
`.msi`/`.exe`), and only after you confirm; archives are extracted with path-traversal protection. Installs are
idempotent and resumable: files that already exist with the right digest are skipped.

The only secret-like data the launcher handles are OAuth tokens. They are stored encrypted (`accounts.dat`, Windows
DPAPI or AES-256-GCM with an owner-only key file) and redacted from every log line and from the printed command line.
There are no passwords: Microsoft sign-in happens in your browser.

## Requirements

- Java 21 to build (a JDK; `jlink`/`jpackage` come with it)
- Internet access to Maven Central for the build; Mojang/Fabric/Microsoft endpoints at run time
- Windows 10/11 is the primary platform for installers; the launcher itself runs on Windows, macOS and Linux
- A fat jar (`-all.jar`) contains the JavaFX native libraries of **one** platform only: a jar built on Windows does not
  start its user interface on Linux or macOS, and vice versa. Releases therefore ship one fat jar per platform.

## Build

```bash
cd launcher
./gradlew build            # compile, unit tests, build/libs/vanta-launcher-1.0.0.jar + -all.jar
./gradlew fatJar           # build/libs/vanta-launcher-1.0.0-all.jar: every dependency incl. JavaFX for -PjavafxPlatform
./gradlew run              # start the launcher with the host JDK (UI when present, otherwise CLI help)
./gradlew jdeps            # print the JDK modules the fat jar needs (maintenance aid)
./gradlew jlinkImage       # build/runtime — trimmed Java 21 + JavaFX runtime for the target platform
./gradlew jpackage         # build/jpackage — app image (default) or installer, see below
```

Gradle properties:

| Property | Values | Default |
| --- | --- | --- |
| `-PjavafxPlatform` | `win`, `linux`, `mac`, `mac-aarch64`, `linux-aarch64` | host platform |
| `-PjpackageType` | `app-image`, `msi`, `exe`, `dmg`, `pkg`, `deb`, `rpm` | `app-image` |

Only the JDK's own `jlink` and `jpackage` are used — no third-party packaging plugins. `jpackage` cannot cross-build,
so each platform package is produced on that platform.

`fatJar` runs with any Java 21 (`java -jar vanta-launcher-1.0.0-all.jar`); JavaFX then loads from the class path and
prints the harmless warning "Unsupported JavaFX configuration: classes were loaded from 'unnamed module'". jpackage
bundles the same jar; inside the app image the JavaFX modules of the jlink runtime take precedence. `--version`
prints the JavaFX platform a jar was built for. The release workflow publishes renamed copies:
`vanta-launcher-<v>-windows-all.jar`, `vanta-launcher-<v>-linux-all.jar` and `vanta-launcher-<v>-macos-aarch64-all.jar`
(built and command-line tested on a macOS runner; its user interface is untested there and the jar is unsigned).

### Windows installers

On a Windows machine (or the GitHub `windows-latest` runner, which has WiX 3 preinstalled):

```powershell
cd launcher
.\gradlew.bat build fatJar jlinkImage jpackage -PjpackageType=msi
.\gradlew.bat jpackage -PjpackageType=exe
```

Output: `build\jpackage\VANTA Launcher-1.0.0.msi` / `.exe`. The installer is per-user, adds a Start menu entry and a
desktop shortcut, lets the user choose the directory and uses a fixed upgrade UUID so newer versions replace older
ones. The icon comes from `packaging/icon.ico`.

### Linux / macOS

`./gradlew jpackage` produces an app image under `build/jpackage/VANTA Launcher/` that runs with
`bin/VANTA\ Launcher`. Use `-PjpackageType=deb` (needs `dpkg`), `rpm` or `dmg` for native packages.

## Configuration

### Microsoft sign-in (client id)

Minecraft sign-in requires an Azure/Microsoft Entra **application (client) id** that Mojang has approved for the
Minecraft API. It is configuration, never a constant in the code:

1. Register an application in the Microsoft Entra admin center: public client, supported account types
   "Personal Microsoft accounts", **Allow public client flows** enabled (device code flow).
2. Request Minecraft API access for that application from Mojang (see the Minecraft developer documentation).
3. Provide the id to the launcher either in `settings.json` (`"msClientId": "..."`) or through the environment
   variable `VANTA_MS_CLIENT_ID`.

Without a client id the launcher explains exactly this and does not offer a fake login. The project does not have an
approved id, so released builds play through the official Minecraft Launcher instead: see
[Use with the Minecraft Launcher](#use-with-the-minecraft-launcher).

### Releases URL

The releases base URL is the directory holding `client-latest.json` and `launcher-latest.json` (the release manifests
described in `RELEASE.md`). The launcher uses, in this order:

1. `--releases-url <url>` (this run only) or a non-empty `"releasesBaseUrl"` in `settings.json`,
2. the environment variable `VANTA_RELEASES_BASE_URL`,
3. the built-in default `https://raw.githubusercontent.com/LennardOwnTest123006/VANTA-Client/HEAD/shared/releases/latest`
   (`LauncherSettings.DEFAULT_RELEASES_BASE_URL`; `HEAD` is the repository's default branch).

Existing `settings.json` files with `"releasesBaseUrl": ""` therefore use the default. Settings → Releases base URL
shows the URL in effect and where it came from, and "Reset to default" clears an override. Only an explicitly
configured value that is not an absolute http(s) URL is reported as "not configured" (exit code 3); the launcher never
guesses another location. No manifest at the URL (HTTP 404) or a manifest whose `downloadUrl` is empty means "no public
release yet" (exit code 7) and is reported as such.

From a client manifest the launcher installs exactly `vanta-client-<version>.jar`; the `-mods.zip` bundle and the
Fabric API jar listed in the same release are never mistaken for it (Fabric API always comes from the Fabric Maven).

Launcher self-update assets per platform: Windows `VANTA-Launcher-<v>.msi` (then `.exe`), Linux x64
`VANTA-Launcher-<v>-linux-x64.tar.gz` (extract it and start `VANTA Launcher/bin/VANTA Launcher`), Apple Silicon macOS
`vanta-launcher-<v>-macos-aarch64-all.jar` (start it with Java 21). Every other platform (for example an Intel Mac)
gets no download; the update dialog opens the GitHub release page instead. Only the Windows installer is handed to the
operating system after confirmation; the archive and the jar are shown in their folder.

### settings.json

| Key | Meaning | Default |
| --- | --- | --- |
| `memoryMb` | maximum heap for the game | half of RAM, clamped to 2048–8192 (4096 when unknown) |
| `javaPath` | Java home, `bin` directory or executable to use | auto-detect |
| `jvmArgs` | extra JVM arguments (user flags win over defaults) | `[]` |
| `resolution` | initial window size `{ "width", "height" }` | game default |
| `keepLauncherOpen` | keep the launcher window open while playing | `false` |
| `msClientId` | Microsoft application id | empty → `VANTA_MS_CLIENT_ID` |
| `releasesBaseUrl` | base URL of the release manifests | empty → `VANTA_RELEASES_BASE_URL`, else the built-in default |
| `autoUpdateCheck` | check manifests at start | `true` |
| `developerMode` | enables development features (see offline accounts) | `false` |
| `shareOfficialMinecraftFiles` | reuse verified libraries/assets from the official `.minecraft` (read-only) | `true` |
| `theme` | UI theme id | `vanta-dark` |

### Offline accounts

VANTA is a legitimate client. Offline sessions are only possible as an *offline session of a Microsoft account that has
signed in successfully on this computer before* (its verified name and UUID are reused), or — for development and CI
only — with `developerMode` (or `--dev-offline`) **and** the environment variable `VANTA_DEV_OFFLINE=1`.

## Use with the Minecraft Launcher

Microsoft sign-in inside VANTA needs an application id that Mojang has approved, which the project does not have (see
above). "Use with Minecraft Launcher" on the Home screen (offered prominently while sign-in is not configured) and
`--install-official-profile` on the command line make VANTA playable through the **official Minecraft Launcher**
instead; it downloads Minecraft, its libraries, assets and Java and signs you in with Microsoft. VANTA writes exactly:

| File | Content |
| --- | --- |
| `<data>/instances/vanta-1.21.11/mods/fabric-api-0.141.6+1.21.11.jar` | Fabric API from `maven.fabricmc.net`, SHA-256 verified |
| `<data>/instances/vanta-1.21.11/mods/vanta-client-<v>.jar` | the published VANTA Client (SHA-256 from the manifest) or `--client-jar` |
| `<data>/versions/vanta-client/<v>/vanta-client-<v>.jar` and `manifest.json` | rollback copy of a published release (not for `--client-jar`) |
| `<data>/instances/vanta-1.21.11/instance.json` | the installed client version, only when the instance file exists |
| `<minecraft>/versions/fabric-loader-0.19.5-1.21.11/fabric-loader-0.19.5-1.21.11.json` | the Fabric Loader version JSON exactly as `meta.fabricmc.net` serves it |
| `<minecraft>/versions/fabric-loader-0.19.5-1.21.11/fabric-loader-0.19.5-1.21.11.jar` | empty, as the official Fabric installer writes it |
| `<minecraft>/launcher_profiles.json` | profile `vanta-1.21.11` ("VANTA 1.21.11", custom, VANTA icon, `lastVersionId` above, `gameDir` = the VANTA instance, `javaArgs` `-Xmx<memoryMb>M`), only when that file exists (launcher from minecraft.net) |
| `<minecraft>/launcher_profiles_microsoft_store.json` | the same profile, only when that file exists (launcher from the Microsoft Store / Xbox app) |
| `<file>.vanta-backup` next to each profiles file above | one-time backup of the original file, only when no backup exists yet |

Like the regular install, older `fabric-api-*.jar` and `vanta-client-*.jar` files in the instance's `mods/` folder are
removed so only one version is loaded, and only the 3 newest rollback copies are kept. Before anything is written the
exact list (with `<v>` resolved from the release manifest, and every file that will be removed) is shown: in the
confirmation dialog on the Home screen, and as "Files that will be written" / "Files that will be removed" on the command
line. When the release cannot be installed (no manifest at the releases URL, or no download yet) this check fails first
with exit code 7 and nothing is listed, downloaded or written.

`<minecraft>` is `--minecraft-dir` or the platform default (`%APPDATA%\.minecraft`, `~/Library/Application
Support/minecraft`, `~/.minecraft`). The game directory is the same instance the regular install uses. The profiles
file is parsed as a JSON tree: every other profile and key is kept (including keys whose value is `null`), an existing VANTA entry is updated in place (its
`created` time and keys VANTA does not manage, such as `javaDir`, survive), the first run keeps a backup
`<file>.vanta-backup`, and the file is replaced atomically. VANTA writes the profile into every profiles file that
exists, like the Fabric installer does. Nothing in the Minecraft folder is written before every download succeeded.
When neither `launcher_profiles.json` nor `launcher_profiles_microsoft_store.json` exists, the Minecraft Launcher has
never been started there: the command stops with "Start the Minecraft Launcher once, then try again" (exit code 3)
and changes nothing. Afterwards: open the Minecraft Launcher, choose the profile 'VANTA 1.21.11' and press Play (restart it first
if it was open).

## Directory layout

Data directory: Windows `%APPDATA%\VANTA Launcher`, macOS `~/Library/Application Support/VANTA Launcher`, Linux
`$XDG_DATA_HOME/vanta-launcher` (or `~/.local/share/vanta-launcher`). `VANTA_LAUNCHER_HOME` or `--data-dir` override it.

```
<data>/
├── instances/vanta-1.21.11/     game directory: mods/ (fabric-api, vanta-client), config/, saves/, instance.json
├── libraries/                   Maven layout shared by vanilla and Fabric
├── assets/indexes/, objects/, log_configs/
├── versions/1.21.11/            1.21.11.json + 1.21.11.jar
├── versions/fabric-loader-0.19.5-1.21.11/   Fabric profile JSON
├── versions/vanta-client/<v>/   last 3 verified client jars + manifests (rollback)
├── runtimes/                    Java runtimes installed by the launcher (temurin-21-<release>)
├── logs/                        launcher-N.log (rotating), game-<timestamp>.log
├── cache/updates/               downloaded updates (verified before use)
├── settings.json
├── accounts.dat                 encrypted accounts (DPAPI on Windows, AES-256-GCM elsewhere)
└── key.bin                      AES key, owner-only permissions (not on Windows)
```

## Command line

The fat jar and the packaged launcher accept the same flags; without flags the JavaFX UI starts.

```
java -jar vanta-launcher-1.0.0-all.jar <command> [options]

Commands
  --install          install Minecraft, Fabric Loader, Fabric API and VANTA Client
  --install-official-profile
                     install Fabric API + VANTA Client and add the profile 'VANTA 1.21.11' to the official
                     Minecraft Launcher (see "Use with the Minecraft Launcher")
  --launch           launch the installed instance
  --check-java       list detected Java runtimes and the one that would be used
  --install-java     download and install Eclipse Temurin 21 (verified) into runtimes/
  --check-update     check the release manifests for launcher and client updates
  --print-command    print the game command line (secrets redacted) without launching
  --version          print the launcher version
  --help             show help

Options
  --data-dir <path>        launcher data directory
  --client-jar <path>      --install / --install-official-profile: install a local VANTA client jar instead of the
                           published release
  --minecraft-dir <path>   --install-official-profile: the official Minecraft directory (default: platform .minecraft)
  --without-client         --install: plain Fabric instance without the VANTA client
  --no-assets              --install: skip assets (development only)
  --dev-offline            --launch/--print-command: development offline account (needs VANTA_DEV_OFFLINE=1)
  --username <name>        player name for --dev-offline (default Dev)
  --world <name>           --launch: open a singleplayer world directly
  --server <host[:port]>   --launch: join a server directly
  --exit-after <seconds>   --launch: stop the game after N seconds, success if it was still running
  --java <path>            Java home or executable
  --memory <mb>            maximum heap
  --resolution <WxH>       initial window size
  --releases-url <url>     base URL of the release manifests for this run (default: settings, then
                           $VANTA_RELEASES_BASE_URL, then the built-in default)
```

Exit codes: `0` success · `1` unexpected failure · `2` invalid command line · `3` not configured (client id /
unusable releases URL / Minecraft Launcher never started) · `4` integrity check failed · `5` network failure (refused
connections, DNS, TLS, timeouts, HTTP errors and HTTPS proxy tunnels that answer `CONNECT` with 403/407; the message
names the step and the URL) · `6` no Java 21 found · `7` release not published · `8` cancelled · `9` authentication
failed / no account · `10` game exited with an error · `11` insufficient disk space.

CI uses the CLI to integration-test the real pipeline:

```bash
java -jar build/libs/vanta-launcher-1.0.0-all.jar --install --client-jar ../client/build/libs/vanta-client-1.0.0.jar --data-dir /tmp/vanta
VANTA_DEV_OFFLINE=1 java -jar build/libs/vanta-launcher-1.0.0-all.jar --launch --dev-offline --username CI --exit-after 90 --data-dir /tmp/vanta
java -jar build/libs/vanta-launcher-1.0.0-all.jar --install-official-profile --client-jar ../client/build/libs/vanta-client-1.0.0.jar \
  --minecraft-dir /tmp/dotminecraft --data-dir /tmp/vanta   # CI checks the result with jq
```

Once a client release is published, CI also runs `--install` without `--client-jar` to prove the launcher finds the
published jar through the built-in releases URL.

## Code map

```
dev.vanta.launcher.Main                 bootstrap: starts the JavaFX UI reflectively or the CLI
dev.vanta.launcher.LauncherVersion      pinned versions (generated from gradle.properties)
dev.vanta.launcher.cli                  LauncherCli, CliArgs, CliCommand, ExitCode
dev.vanta.launcher.core.LauncherServices  composition root used by UI and CLI
dev.vanta.launcher.core.paths           LauncherPaths
dev.vanta.launcher.core.model           Gson models: VersionManifest, VersionJson, AssetIndexJson, FabricProfileJson,
                                        FabricLoaderVersion, MavenCoordinate, ReleaseManifest, InstanceInfo, AdoptiumAsset
dev.vanta.launcher.core.launch          RuleEvaluator, ArgumentExpander, LibraryResolver, ClasspathBuilder,
                                        JvmArgsBuilder, GameArgsBuilder, LaunchService, GameProcess
dev.vanta.launcher.core.net             HttpTransport (+JdkHttpTransport), Downloader, Checksums, CancellationToken,
                                        NetworkErrors (transport failure classification)
dev.vanta.launcher.core.install         MojangService, FabricService, FabricApiService, VantaClientService,
                                        Installer, InstallPlan, InstallProgress, SharedFileSource,
                                        OfficialProfileService ("Use with the Minecraft Launcher")
dev.vanta.launcher.core.java            JavaDetector, ProcessJavaProbe, AdoptiumService, ArchiveExtractor
dev.vanta.launcher.core.auth            MicrosoftAuthService, AccountStore (DPAPI / AES-GCM), OfflineAccountPolicy
dev.vanta.launcher.core.settings        LauncherSettings, SettingsStore, ReleasesBaseUrl (URL in effect + source)
dev.vanta.launcher.core.update          UpdateService, UpdateInfo, SemVer
dev.vanta.launcher.core.log             LauncherLog (java.util.logging, rotating), Redactor
dev.vanta.launcher.ui                   JavaFX user interface (loaded reflectively by Main), see below
```

## User interface

`dev.vanta.launcher.ui.LauncherApp` is the JavaFX application. It never talks to the core services directly:

```
dev.vanta.launcher.ui.backend   LauncherBackend (the single seam between UI and core), CoreBackend (delegates every
                                call to LauncherServices), RunningGame (game process as seen by the UI)
dev.vanta.launcher.ui.model     view models: SessionModel (account, Java, instance, settings), HomeViewModel
                                (NOT_READY → READY → INSTALLING/VERIFYING → RUNNING → READY/ERROR), SignInViewModel
                                (device code flow), SettingsViewModel, VersionsViewModel, LogsViewModel + LogBuffer,
                                UpdateViewModel, ToastModel, NavigationModel, ErrorMessages (core exceptions → text)
dev.vanta.launcher.ui.view      MainWindow, Sidebar, pages (Home, Versions, Logs, Settings, About), in-window dialogs
                                (sign-in, update/changelog, accounts, confirm), toasts, custom controls
dev.vanta.launcher.ui.prefs     ui-preferences.json (reduced motion, window size, last page) — presentation only
resources .../ui/               theme/vanta.css (design tokens), i18n/launcher_en.properties (every UI string),
                                fonts/ (Inter, Space Grotesk, OFL), links.properties (external links), icon-*.png
```

View models run blocking core calls on a background executor and publish results on the JavaFX thread
(`UiExecutors`, `Async`); the views only bind. The launch flow is: pick Java → `Installer.install` (verifies every
file, downloads what is missing) → refresh the account token → `LaunchService` → stream the process output into the
Logs page → notify on exit. Settings changes go through `LauncherServices.saveSettings`; a launcher update is
downloaded and re-verified by `UpdateService` and only then, after an explicit confirmation, handed to the operating
system.

Tests: the view models are covered with a scriptable `FakeBackend` (`src/test/.../ui/testutil`), and
`LauncherAppSmokeTest` starts the real application on the Monocle headless platform, visits every page and dialog
and fails on any stylesheet warning or uncaught exception. `./gradlew screenshots` (run under `xvfb-run -a`, or with
`-Pheadless`) renders every screen with fake services to `build/screenshots/*.png` for visual review.

All of `dev.vanta.launcher.core` is headless and covered by unit tests (`./gradlew test`). Network-facing code is
tested against local fake servers with recorded JSON fixtures under `src/test/resources/fixtures/`; nothing in the
test suite reaches the internet.
