# VANTA Launcher

The VANTA Launcher installs and starts **Minecraft Java Edition 1.21.11** with **Fabric Loader 0.19.5**,
**Fabric API 0.141.6+1.21.11** and the **VANTA Client** mod, using only legitimate sources:

| Component | Source | Verification |
| --- | --- | --- |
| Minecraft version manifest, version JSON, client jar, libraries, assets | `piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net` | SHA-1 + size from Mojang metadata |
| Fabric Loader profile and libraries | `meta.fabricmc.net`, `maven.fabricmc.net` | SHA-256/SHA-1 from the profile or the Maven `.sha256`/`.sha1` sidecar |
| Fabric API | `maven.fabricmc.net` | SHA-256 sidecar |
| VANTA Client | release manifest (`<releasesBaseUrl>/client-latest.json`) | SHA-256 from the manifest |
| Java 21 runtime (optional) | Adoptium API (`api.adoptium.net`, Eclipse Temurin JRE) | SHA-256 from the API |
| Sign-in | Microsoft OAuth device code flow → Xbox Live → XSTS → Minecraft services | — |

Every download is written to a temporary file, verified while streaming and moved into place atomically. A file that
fails verification is deleted and reported. The launcher never executes anything it downloaded except the verified
Java runtime it installed for you; archives are extracted with path-traversal protection. Installs are idempotent and
resumable: files that already exist with the right digest are skipped.

The only secret-like data the launcher handles are OAuth tokens. They are stored encrypted (`accounts.dat`, Windows
DPAPI or AES-256-GCM with an owner-only key file) and redacted from every log line and from the printed command line.
There are no passwords: Microsoft sign-in happens in your browser.

## Requirements

- Java 21 to build (a JDK; `jlink`/`jpackage` come with it)
- Internet access to Maven Central for the build; Mojang/Fabric/Microsoft endpoints at run time
- Windows 10/11 is the primary platform for installers; the launcher itself runs on Windows, macOS and Linux

## Build

```bash
cd launcher
./gradlew build            # compile, unit tests, build/libs/vanta-launcher-1.0.0.jar + -all.jar
./gradlew fatJar           # build/libs/vanta-launcher-1.0.0-all.jar (all dependencies except JavaFX)
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

Without a client id the launcher explains exactly this and does not offer a fake login.

### Releases URL

`"releasesBaseUrl"` in `settings.json` (or `--releases-url`) points at the directory holding `client-latest.json` and
`launcher-latest.json` (the release manifests described in `RELEASE.md`). When it is empty the launcher shows
"not configured" and installs only when a local client jar is given. A manifest whose `downloadUrl` is empty means
"no public release yet" and is reported as such.

### settings.json

| Key | Meaning | Default |
| --- | --- | --- |
| `memoryMb` | maximum heap for the game | half of RAM, clamped to 2048–8192 (4096 when unknown) |
| `javaPath` | Java home, `bin` directory or executable to use | auto-detect |
| `jvmArgs` | extra JVM arguments (user flags win over defaults) | `[]` |
| `resolution` | initial window size `{ "width", "height" }` | game default |
| `keepLauncherOpen` | keep the launcher window open while playing | `false` |
| `msClientId` | Microsoft application id | empty → `VANTA_MS_CLIENT_ID` |
| `releasesBaseUrl` | base URL of the release manifests | empty (not configured) |
| `autoUpdateCheck` | check manifests at start | `true` |
| `developerMode` | enables development features (see offline accounts) | `false` |
| `shareOfficialMinecraftFiles` | reuse verified libraries/assets from the official `.minecraft` (read-only) | `true` |
| `theme` | UI theme id | `vanta-dark` |

### Offline accounts

VANTA is a legitimate client. Offline sessions are only possible as an *offline session of a Microsoft account that has
signed in successfully on this computer before* (its verified name and UUID are reused), or — for development and CI
only — with `developerMode` (or `--dev-offline`) **and** the environment variable `VANTA_DEV_OFFLINE=1`.

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
  --launch           launch the installed instance
  --check-java       list detected Java runtimes and the one that would be used
  --install-java     download and install Eclipse Temurin 21 (verified) into runtimes/
  --check-update     check the release manifests for launcher and client updates
  --print-command    print the game command line (secrets redacted) without launching
  --version          print the launcher version
  --help             show help

Options
  --data-dir <path>        launcher data directory
  --client-jar <path>      --install: install a local VANTA client jar instead of the published release
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
  --releases-url <url>     base URL of the release manifests
```

Exit codes: `0` success · `1` unexpected failure · `2` invalid command line · `3` not configured (client id /
releases URL) · `4` integrity check failed · `5` network failure · `6` no Java 21 found · `7` release not published ·
`8` cancelled · `9` authentication failed / no account · `10` game exited with an error · `11` insufficient disk space.

CI uses the CLI to integration-test the real pipeline:

```bash
java -jar build/libs/vanta-launcher-1.0.0-all.jar --install --client-jar ../client/build/libs/vanta-client-1.0.0.jar --data-dir /tmp/vanta
VANTA_DEV_OFFLINE=1 java -jar build/libs/vanta-launcher-1.0.0-all.jar --launch --dev-offline --username CI --exit-after 90 --data-dir /tmp/vanta
```

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
dev.vanta.launcher.core.net             HttpTransport (+JdkHttpTransport), Downloader, Checksums, CancellationToken
dev.vanta.launcher.core.install         MojangService, FabricService, FabricApiService, VantaClientService,
                                        Installer, InstallPlan, InstallProgress, SharedFileSource
dev.vanta.launcher.core.java            JavaDetector, ProcessJavaProbe, AdoptiumService, ArchiveExtractor
dev.vanta.launcher.core.auth            MicrosoftAuthService, AccountStore (DPAPI / AES-GCM), OfflineAccountPolicy
dev.vanta.launcher.core.settings        LauncherSettings, SettingsStore
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
