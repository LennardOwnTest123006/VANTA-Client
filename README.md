<p align="center">
  <img src="assets/brand/vanta-app-icon.svg" width="96" height="96" alt="VANTA Client icon">
</p>

<h1 align="center">VANTA Client</h1>

<p align="center"><strong>Your Minecraft. Refined.</strong></p>

<p align="center">
  A modern Fabric client for <strong>Minecraft Java Edition 1.21.11</strong> focused on performance,
  customization and a clean Minecraft experience.<br>
  Minecraft 1.21.11 · Fabric Loader 0.19.5 · Fabric API 0.141.6+1.21.11 · Java 21 · Windows 10/11, Linux x64, Apple Silicon macOS
</p>

---

VANTA is a legitimate, purely client-side Minecraft client. It adds a premium interface, a customizable HUD,
a performance center, Mods & Shaders with a one-click Performance pack from Modrinth, profiles, cosmetics, statistics
and accessibility options on top of vanilla Minecraft. It does **not** add cheats, combat automation, packet
manipulation or anything that gives an unfair advantage on multiplayer servers.

## Features

- **VANTA main menu** in place of the vanilla title screen (can be switched off), with every vanilla destination one
  click away.
- **HUD** with sixteen movable widgets (FPS, ping, coordinates, armor, effects, keystrokes, …), a drag-and-drop HUD
  editor and a crosshair designer.
- **Performance Center**: live FPS, frame time, memory and distances, five presets that change vanilla video
  options only and never cap the frame rate, and a one-click Boost FPS.
- **Mods & Shaders** (client 1.1.0): search Modrinth in the game for Fabric mods, shader packs for Iris and resource
  packs for Minecraft 1.21.11; installs required dependencies, checks every file with the SHA-512 Modrinth publishes,
  and lets you disable or remove what VANTA installed.
- **Performance pack** (client and launcher 1.1.0): Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling and
  Iris Shaders, the newest 1.21.11 Fabric versions from Modrinth at install time. One click in the game; installed by
  default by the VANTA Launcher (can be switched off). These are third-party mods under their own licences; from
  client 1.2.0 on, the ones whose licences allow redistribution (all but EntityCulling) also ship unmodified in the
  client's mods bundle `vanta-client-1.3.0-mods.zip`, with their licence texts in `THIRD-PARTY-LICENSES.txt`.
- **Profiles, settings search, keybind manager, cosmetics, local statistics and accessibility options** (UI scale,
  reduced motion, high contrast, larger text, colour-blind palettes, keyboard navigation).
- **VANTA Launcher**: installs Minecraft 1.21.11, Fabric and the client with checksum verification, or sets VANTA up
  in the official Minecraft Launcher with *PLAY via Minecraft Launcher*; a Mods page for Modrinth; Java 21 detection
  and installation; verified updates and rollback.

Details: [docs/](docs/) and the [Features page](https://vanta-client.netlify.app/features) of the website.

## Repository

This is a monorepo. Each product is an independent build with its own README.

| Directory | What it is | Build |
| --- | --- | --- |
| [`client/`](client/) | The Fabric mod for Minecraft 1.21.11 (Mojang official mappings, Loom 1.17.21) | `./gradlew build` |
| [`core/`](core/) | Pure Java 21 library with the whole UI kit, settings, HUD engine, profiles and statistics logic. No Minecraft dependency, fully unit tested. Compiled into the client jar. | `./gradlew build` |
| [`launcher/`](launcher/) | VANTA Launcher (JavaFX 21). Installs Minecraft 1.21.11 + Fabric legitimately, detects or installs Java 21, installs the Performance pack and other mods from Modrinth, launches the client, and can add a VANTA profile to the official Minecraft Launcher. | `./gradlew build` / `./gradlew jpackage` |
| [`website/`](website/) | Official website (Vite, React, TypeScript, Tailwind). Deployed to Netlify. | `npm install && npm run build` |
| [`shared/`](shared/) | Design tokens, JSON schemas, release manifests, i18n strings shared by all products | – |
| [`assets/`](assets/) | Brand sources, fonts (SIL OFL), screenshots captured by CI | – |
| [`docs/`](docs/) | User and developer documentation (also rendered on the website) | – |
| [`scripts/`](scripts/) | Build, release and CI helper scripts | – |

## Documentation

- [BUILDING.md](BUILDING.md) — how to build every part of the project
- [DEVELOPMENT.md](DEVELOPMENT.md) — development environment, architecture notes, testing
- [RELEASE.md](RELEASE.md) — release process, manifests, checksums
- [CONTRIBUTING.md](CONTRIBUTING.md) — contribution guidelines
- [CHANGELOG.md](CHANGELOG.md) — release notes
- [docs/](docs/) — user documentation: installation, launcher, settings, HUD, profiles, mods and shaders,
  troubleshooting, FAQ, privacy

## Requirements

- Minecraft Java Edition (a Microsoft account that owns the game)
- Java 21 for the game (the VANTA Launcher detects an installed one or installs a verified Eclipse Temurin runtime;
  the official Minecraft Launcher brings its own)
- Windows 10/11 x64 is the primary platform. The launcher is also published for Linux x64 and Apple Silicon macOS;
  on other systems (Intel Macs, Linux on ARM) the client is installed by hand with the Fabric installer

## Status

The latest releases are **VANTA Client 1.3.0** and **VANTA Launcher 1.3.0**, released on 2026-10-07 as the GitHub
Releases [`client-v1.3.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.3.0) and
[`launcher-v1.3.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.3.0). A graphics
choice now stays as you left it: VANTA shows the game's *Custom* preset instead of *Fancy*, built-in profiles change
only what they are about, and with the Performance pack Escape keeps your changes in Sodium's video settings.
Singleplayer lists the worlds of your Minecraft folder again. Short freezes are gone: VANTA's screens no longer
rewrite files on every close, and the Minecraft Launcher profile gets the same tuned JVM arguments as *PLAY*. New is
*Smart Boost*, a local, rule-based tuner (no AI, no network): while you play it measures your frame rate and picks the
best preset your PC holds, and it never overrides graphics settings you chose yourself. Before them:
[`client-v1.2.1`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.2.1) and
[`launcher-v1.2.1`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.2.1) (2026-10-07: a
mod built for an older Minecraft, such as Smart FPS Booster 1.0.0, no longer stops the game while it starts),
[`client-v1.2.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.2.0) and
[`launcher-v1.2.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.2.0) (2026-10-07: the
frame-rate caps that 1.1.0's presets and profiles wrote fixed, *Max FPS* and one-click *Boost FPS*, every button
clickable in small windows, the redistributable Performance pack mods inside the mods bundle),
[`client-v1.1.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.1.0)
and [`launcher-v1.1.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.1.0)
(2026-10-05: Mods & Shaders, the Performance pack, the launcher's Mods page and *PLAY via Minecraft Launcher*),
[`launcher-v1.0.2`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.0.2) (launcher-only
maintenance release), [`client-v1.0.1`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.0.1)
and [`launcher-v1.0.1`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.0.1) (fixes for
1.0.0), and the first releases
[`client-v1.0.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.0.0) and
[`launcher-v1.0.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.0.0). The earlier
releases stay available. Every release is listed at
**[github.com/LennardOwnTest123006/VANTA-Client/releases](https://github.com/LennardOwnTest123006/VANTA-Client/releases)**,
the only place the files are distributed; the Download page of the website
**[vanta-client.netlify.app](https://vanta-client.netlify.app)** lists the same files and links to them there. The
release workflow ([`release.yml`](.github/workflows/release.yml)) builds the files, uploads them, computes their
SHA-256 checksums, downloads every public link again to check it, and fills in the release manifests committed to
[`shared/releases/`](shared/releases/), which the website's Download page and the launcher read. Download URLs, sizes
and checksums are never typed by hand ([RELEASE.md](RELEASE.md)). A new version's manifest is committed before the
workflow runs, with an empty `downloadUrl` for every file. Until the workflow has published the files and the filled
manifests are committed, the Download page keeps offering the newest published release and marks the new version
"Not published yet", and the launcher keeps reading the previous release from `shared/releases/latest/`. What each
version contains is in [CHANGELOG.md](CHANGELOG.md).

### Downloads

| Release | File | For |
| --- | --- | --- |
| `launcher-v1.3.0` | `VANTA-Launcher-1.3.0.msi` | Windows 10/11 x64, per-user installer with the Java 21 runtime (recommended) |
| `launcher-v1.3.0` | `VANTA-Launcher-1.3.0.exe` | Windows 10/11 x64, the same installer as `.exe` |
| `launcher-v1.3.0` | `VANTA-Launcher-1.3.0-windows-portable.zip` | Windows 10/11 x64, no installation: run `VANTA Launcher/VANTA Launcher.exe` |
| `launcher-v1.3.0` | `vanta-launcher-1.3.0-windows-all.jar` | Windows x64 with Java 21 installed |
| `launcher-v1.3.0` | `VANTA-Launcher-1.3.0-linux-x64.tar.gz` | Linux x64, app image with the Java 21 runtime: run `VANTA Launcher/bin/VANTA Launcher` |
| `launcher-v1.3.0` | `vanta-launcher-1.3.0-linux-all.jar` | Linux x64 with Java 21 installed |
| `launcher-v1.3.0` | `vanta-launcher-1.3.0-macos-aarch64-all.jar` | Apple Silicon macOS with Java 21 installed |
| `client-v1.3.0` | `vanta-client-1.3.0.jar` | the Fabric mod (the launcher installs it for you) |
| `client-v1.3.0` | `vanta-client-1.3.0-mods.zip` | manual installation: `mods/` with the VANTA jar, Fabric API and the redistributable Performance pack mods (Sodium, Lithium, FerriteCore, ImmediatelyFast, Iris; not EntityCulling), plus `INSTALL.txt`, `PERFORMANCE-PACK.txt`, `THIRD-PARTY-LICENSES.txt`, `performance-pack.json` and `SHA256SUMS` |
| `client-v1.3.0` | `fabric-api-0.141.6+1.21.11.jar` | Fabric API, the unmodified FabricMC release (Apache-2.0), required by VANTA |

Each release also has `SHA256SUMS.txt` and its manifest (`client-1.3.0.json`, `launcher-1.3.0.json`). Every launcher
jar contains JavaFX for one system only, so take the jar with your system in its name; started on another system it
says which file to download and exits with code 1. From 1.0.1 on, the launcher updates itself with the file that
matches how it was installed: the `.msi` for a launcher installed with the `.msi` or `.exe`, the portable `.zip` for
the portable folder, the `.tar.gz` for the Linux app image and the jar for your system when it was started with
`java -jar` ([docs/launcher.md](docs/launcher.md#updates-and-rollback)). Launcher 1.0.0 still picks the update by
system only, also for the update to 1.3.0: on Windows it offers the `.msi` (also in the portable folder and for the
jar), on Linux x64 the `.tar.gz` (also for the jar). To keep a portable or jar setup, choose *Not now*, close the
1.0.0 launcher and download `VANTA-Launcher-1.3.0-windows-portable.zip` or the jar for your system from the
`launcher-v1.3.0` release instead ([docs/installation.md](docs/installation.md#updating)). Launchers 1.0.0 and 1.0.1
save a downloaded update as `cache/updates/<version>-<file name>` (for example
`1.3.0-vanta-launcher-1.3.0-linux-all.jar`), the verified release file under another name; from 1.0.2 on it keeps
its release name. The launcher downloads the Performance pack from Modrinth when it installs it; the client's mods
bundle carries the pack mods whose licences allow redistribution, resolved on Modrinth when the release is built (the
versions are listed in the release notes and in `PERFORMANCE-PACK.txt` inside the zip).

### How to install

1. **VANTA Launcher → *PLAY via Minecraft Launcher*** (the way to play with the published builds): install the
   launcher, start the official Minecraft Launcher once, then close it completely (on Windows also from the system
   tray; it reads new profiles only when it starts, and VANTA asks you to close it if it still runs). Click *PLAY via
   Minecraft Launcher*: VANTA installs Fabric API, the VANTA Client and the Performance pack (on by default) into its
   game folder, adds or updates the profile *VANTA 1.21.11* and opens the official Minecraft Launcher, which handles
   Microsoft sign-in, Minecraft and Java; choose *VANTA 1.21.11* there and press Play. *Open Minecraft Launcher* starts
   it again later, and *Use with Minecraft Launcher* does the setup without opening it. VANTA adds the profile to every
   profiles file the Minecraft Launcher has created: `launcher_profiles.json` (Minecraft
   Launcher from minecraft.net) and/or `launcher_profiles_microsoft_store.json` (Minecraft Launcher from the
   Microsoft Store or the Xbox app on Windows). The Fabric installer works differently: when both files exist, it
   asks which launcher to use and writes only that one. CI checks the contents of both files on Linux; whether the
   Microsoft Store / Xbox app launcher then shows the profile has not been tested on a real Windows PC yet. If it does
   not, use the manual installation (3).
2. **VANTA Launcher → PLAY**: installs and starts everything itself, including the Performance pack, but needs a
   Microsoft client id (see below).
3. **Manual**: start the official Minecraft Launcher once, install Fabric Loader 0.19.5 for 1.21.11 with the Fabric
   installer (keep *Create profile* checked) and copy **all** jars from the `mods/` folder of
   `vanta-client-1.3.0-mods.zip` into `.minecraft/mods` (delete older copies of Sodium, Iris, Lithium, FerriteCore
   or ImmediatelyFast first; Fabric refuses to start with two copies of one mod). On Windows use the Fabric installer `.exe`, which needs no separate Java. On macOS and Linux use
   the universal `.jar`, which needs Java installed: install Java 21 first and run
   `java -jar fabric-installer-<version>.jar` (on macOS, if Gatekeeper blocks it, choose *Open Anyway* under
   *System Settings → Privacy & Security*). If both Minecraft Launchers are installed, the Fabric installer asks which
   one to use; choose the one you play with. `INSTALL.txt` in the zip lists these steps (from 1.1.0 on including the
   Fabric installer per system and the launcher choice). From client 1.2.0 on, the zip also carries
   the Performance pack mods whose licences allow redistribution (third-party, unmodified, licence texts in
   `THIRD-PARTY-LICENSES.txt`); EntityCulling is not among them, so the game offers it with one click (the one-time
   *Boost your FPS?* dialog, or *Mods & Shaders → Performance pack*).

Step by step, with checksum verification for every system: [docs/installation.md](docs/installation.md).

### What CI verifies

On every push ([`ci.yml`](.github/workflows/ci.yml)):

- **core**: `./gradlew build`, which compiles the library and runs its JUnit tests. The Java2D screen previews
  (`./gradlew previewScreens`) are a local tool and do not run in CI.
- **client**: compiled against the real Minecraft 1.21.11 (Mojang mappings); the production game test then starts
  the built jar with Fabric Loader 0.19.5 and Fabric API in a headless game, opens every screen, creates a world,
  applies a performance preset and screenshots everything. The captures in [`assets/screenshots/`](assets/screenshots/)
  come from that test.
- **client with the Performance pack**: the same game test runs again with the newest Minecraft 1.21.11 Fabric
  versions of Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling and Iris on Modrinth at the time of the run,
  and fails when one of them is not loaded, the game crashes or the test does not finish. Before it, a live check
  resolves and downloads the pack through VANTA's own Modrinth client (`./gradlew liveTest` in `core/`) and verifies
  every SHA-512.
- **Performance pack resolver**: `node --test scripts/release/` (the release scripts' tests) and a real run of
  `scripts/release/performance-pack.mjs` against the live Modrinth API, exactly as the release builds the mods bundle:
  five redistributable jars downloaded and verified by size and SHA-512, EntityCulling listed as excluded by its
  licence, every licence text fetched into `THIRD-PARTY-LICENSES.txt`, `performance-pack.json` valid against its
  schema. The summary and JSON are uploaded as the `performance-pack` artifact.
- **launcher**: unit tests, `jlink` + `jpackage` app image and a command line smoke test (`--version`,
  `--check-java`) on Ubuntu and Windows; on Windows also the portable zip and a non-blocking `.msi` build. An integration job on
  Linux installs Minecraft 1.21.11 + Fabric from the official endpoints with the launcher's fat jar, launches the game
  headlessly and checks that the VANTA main menu came up. That install includes the Performance pack from the live
  Modrinth API: every jar is checked against `modrinth.json` with `sha512sum` and against Modrinth
  (`/v2/version_file/<sha512>`), and the game must load all six mods next to the VANTA client. The job also runs
  `--install-official-profile` against a prepared `.minecraft` folder and checks with `jq` that the VANTA profile, the
  Fabric version files and the backup are written and that the other profiles and keys are unchanged. Once a client
  release is published, it installs that release through the built-in releases URL and compares the jar's SHA-256
  with the manifest.
- **launcher on Windows**: the job *Launcher starts on Windows* builds the launcher on a Windows runner, installs the
  `.msi` with `msiexec /qn`, unpacks the portable zip and runs the Windows jar, and fails unless each of them shows the
  launcher window and exits with code 0; its screenshots and logs are published to the `ci-artifacts` branch. These
  are CI builds of the same sources, not the release files.
- **website**: lint, unit tests, production build with a bundle budget, Playwright end-to-end and accessibility checks.

On every release ([`release.yml`](.github/workflows/release.yml)):

- **client**: build, jar metadata (Minecraft `~1.21.11`, version), the headless production game test, the Fabric API
  jar checked against the checksum Gradle recorded and its license, the Performance pack resolved live on Modrinth
  (jars verified by size and SHA-512, licence texts fetched or the release fails), and the mods bundle checked with
  `sha256sum -c`; the resolved pack versions go into the release notes.
- **launcher**: one fat jar each on Windows x64, Linux x64 and Apple Silicon macOS, each checked to contain the
  JavaFX native libraries of exactly that system and started with `--version` (Linux and macOS also `--check-java`);
  the bundled runtime of the Windows portable app image runs its jar with `--version`, the Linux app image runs
  `bin/VANTA Launcher --version`, and both archives are checked for their expected contents (the portable zip also
  for `VANTA Launcher/app/vanta-portable.marker`, which tells the launcher to update itself with the portable zip);
  the `.msi` and `.exe` installers are built from a fresh app image without that marker.
- **publish**: the manifest is built from the real files and checked (names, order, sizes, SHA-256), the GitHub
  Release is created, then every public download URL is downloaded again and re-hashed; the run fails on a single
  mismatch.

### Known limitations

- **Not code-signed.** Windows SmartScreen may show "Windows protected your PC" for the installers and the portable
  app, and the macOS jar is neither signed nor notarized. Verify the SHA-256 from `SHA256SUMS.txt` first (see
  [installation](docs/installation.md#2-verify-the-checksum)).
- **Microsoft sign-in inside the VANTA Launcher needs a client id** — an Azure application that Mojang has approved
  for the Minecraft API. The project does not have one, so the VANTA Launcher's main button is *PLAY via Minecraft
  Launcher* and the official Minecraft Launcher signs you in; PLAY inside VANTA works once you set `msClientId` /
  `VANTA_MS_CLIENT_ID`. The manual installation works without the VANTA Launcher.
- **Windows packages**: the release workflow builds and statically checks the `.msi`, `.exe` and portable zip but does
  not run them. The CI job *Launcher starts on Windows* installs and starts a CI build of the `.msi`, the portable zip
  and the jar on a Windows runner; the `.exe` installer is not started in CI. Smart App Control on Windows 11 can block
  the unsigned installers and `VANTA Launcher.exe` without a *Run anyway* option; see
  [Troubleshooting](docs/troubleshooting.md#smart-app-control-windows-11).
- **Third-party mods**: the Performance pack and everything from Mods & Shaders are independent projects from
  Modrinth under their own licences. CI loads the six Performance pack mods in one game test; other combinations are
  not tested.
- **macOS**: the Apple Silicon jar is built and tested from the command line on a macOS runner; its window has not
  been tested. There is no build for Intel Macs, no `.dmg` and no notarization.
- **Linux**: the app image is built for x64 only.
- **Windows on ARM**: there is no arm64 build. Windows 11 on ARM runs x64 programs under emulation, so the x64
  installer, the portable app or an x64 Java 21 with the Windows jar are the way there; this has not been tested on a
  Windows on ARM device ([Troubleshooting](docs/troubleshooting.md#windows-on-arm-which-launcher-file)).

## License

Code is licensed under the [MIT License](LICENSE). The VANTA name, wordmark and logo are not covered by the
license. Minecraft is a trademark of Mojang AB / Microsoft; this project is not affiliated with or endorsed by them.
