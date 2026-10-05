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
a performance center, profiles, cosmetics, statistics and accessibility options on top of vanilla Minecraft.
It does **not** add cheats, combat automation, packet manipulation or anything that gives an unfair
advantage on multiplayer servers.

## Repository

This is a monorepo. Each product is an independent build with its own README.

| Directory | What it is | Build |
| --- | --- | --- |
| [`client/`](client/) | The Fabric mod for Minecraft 1.21.11 (Mojang official mappings, Loom 1.17.21) | `./gradlew build` |
| [`core/`](core/) | Pure Java 21 library with the whole UI kit, settings, HUD engine, profiles and statistics logic. No Minecraft dependency, fully unit tested. Compiled into the client jar. | `./gradlew build` |
| [`launcher/`](launcher/) | VANTA Launcher (JavaFX 21). Installs Minecraft 1.21.11 + Fabric legitimately, detects or installs Java 21, launches the client, and can add a VANTA profile to the official Minecraft Launcher. | `./gradlew build` / `./gradlew jpackage` |
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
- [docs/](docs/) — user documentation: installation, launcher, settings, HUD, profiles, troubleshooting, FAQ, privacy

## Requirements

- Minecraft Java Edition (a Microsoft account that owns the game)
- Java 21 for the game (the VANTA Launcher detects an installed one or installs a verified Eclipse Temurin runtime;
  the official Minecraft Launcher brings its own)
- Windows 10/11 x64 is the primary platform. The launcher is also published for Linux x64 and Apple Silicon macOS;
  on other systems (Intel Macs, Linux on ARM) the client is installed by hand with the Fabric installer

## Status

**VANTA Client 1.0.0** and **VANTA Launcher 1.0.0** are distributed only through GitHub Releases, as the releases
[`client-v1.0.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.0.0) and
[`launcher-v1.0.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.0.0) that the release
workflow ([`release.yml`](.github/workflows/release.yml)) creates; every published release is listed at
**[github.com/LennardOwnTest123006/VANTA-Client/releases](https://github.com/LennardOwnTest123006/VANTA-Client/releases)**.
The workflow uploads the files, computes their SHA-256 checksums and fills in the release manifests that are committed
to [`shared/releases/`](shared/releases/), which the website's Download page and the launcher read. Download URLs,
sizes and checksums are never typed by hand ([RELEASE.md](RELEASE.md)). A release that is not listed on GitHub
Releases has not been published yet: its manifest still has an empty `downloadUrl`, and the Download page and the
launcher say "not published yet" instead of offering a link. What each version contains is in
[CHANGELOG.md](CHANGELOG.md).

### Downloads

| Release | File | For |
| --- | --- | --- |
| `launcher-v1.0.0` | `VANTA-Launcher-1.0.0.msi` | Windows 10/11 x64, per-user installer with the Java 21 runtime (recommended) |
| `launcher-v1.0.0` | `VANTA-Launcher-1.0.0.exe` | Windows 10/11 x64, the same installer as `.exe` |
| `launcher-v1.0.0` | `VANTA-Launcher-1.0.0-windows-portable.zip` | Windows 10/11 x64, no installation: run `VANTA Launcher/VANTA Launcher.exe` |
| `launcher-v1.0.0` | `vanta-launcher-1.0.0-windows-all.jar` | Windows x64 with Java 21 installed |
| `launcher-v1.0.0` | `VANTA-Launcher-1.0.0-linux-x64.tar.gz` | Linux x64, app image with the Java 21 runtime: run `VANTA Launcher/bin/VANTA Launcher` |
| `launcher-v1.0.0` | `vanta-launcher-1.0.0-linux-all.jar` | Linux x64 with Java 21 installed |
| `launcher-v1.0.0` | `vanta-launcher-1.0.0-macos-aarch64-all.jar` | Apple Silicon macOS with Java 21 installed |
| `client-v1.0.0` | `vanta-client-1.0.0.jar` | the Fabric mod (the launcher installs it for you) |
| `client-v1.0.0` | `vanta-client-1.0.0-mods.zip` | manual installation: `mods/` with the VANTA jar and Fabric API, `INSTALL.txt`, `SHA256SUMS` |
| `client-v1.0.0` | `fabric-api-0.141.6+1.21.11.jar` | Fabric API, the unmodified FabricMC release (Apache-2.0), required by VANTA |

Each release also has `SHA256SUMS.txt` and its manifest (`client-1.0.0.json`, `launcher-1.0.0.json`). Every launcher
jar contains JavaFX for one system only, so take the jar with your system in its name.

### How to install

1. **VANTA Launcher → *Use with Minecraft Launcher*** (the way to play with the published builds): install the
   launcher, start the official Minecraft Launcher once, click *Use with Minecraft Launcher*, then start the profile
   *VANTA 1.21.11* in the official Minecraft Launcher, which handles Microsoft sign-in, Minecraft and Java. Like the
   Fabric installer, VANTA adds the profile to every profiles file the Minecraft Launcher has created:
   `launcher_profiles.json` (Minecraft Launcher from minecraft.net) and/or `launcher_profiles_microsoft_store.json`
   (Minecraft Launcher from the Microsoft Store or the Xbox app on Windows).
2. **VANTA Launcher → PLAY**: installs and starts everything itself, but needs a Microsoft client id (see below).
3. **Manual**: install Fabric Loader 0.19.5 for 1.21.11 with the Fabric installer and copy the two jars from
   `vanta-client-1.0.0-mods.zip` into `.minecraft/mods`.

Step by step, with checksum verification for every system: [docs/installation.md](docs/installation.md).

### What CI verifies

On every push ([`ci.yml`](.github/workflows/ci.yml)):

- **core**: JUnit tests and Java2D previews of every VANTA screen.
- **client**: compiled against the real Minecraft 1.21.11 (Mojang mappings); the production game test then starts
  the built jar with Fabric Loader 0.19.5 and Fabric API in a headless game, opens every screen, creates a world,
  applies a performance preset and screenshots everything. The captures in [`assets/screenshots/`](assets/screenshots/)
  come from that test.
- **launcher**: unit tests, `jlink` + `jpackage` app image and a command line smoke test (`--version`,
  `--check-java`) on Ubuntu and Windows; on Windows also the portable zip and a non-blocking `.msi` build. An integration job on
  Linux installs Minecraft 1.21.11 + Fabric from the official endpoints with the launcher's fat jar, launches the game
  headlessly and checks that the VANTA main menu came up; it also runs `--install-official-profile` against a
  prepared `.minecraft` folder and checks with `jq` that the VANTA profile, the Fabric version files and the backup are
  written and that the other profiles and keys are unchanged. Once a client release is published, it installs that
  release through the built-in releases URL and compares the jar's SHA-256 with the manifest.
- **website**: lint, unit tests, production build with a bundle budget, Playwright end-to-end and accessibility checks.

On every release ([`release.yml`](.github/workflows/release.yml)):

- **client**: build, jar metadata (Minecraft `~1.21.11`, version), the headless production game test, the Fabric API
  jar checked against the checksum Gradle recorded and its license, and the mods bundle checked with `sha256sum -c`.
- **launcher**: one fat jar each on Windows x64, Linux x64 and Apple Silicon macOS, each checked to contain the
  JavaFX native libraries of exactly that system and started with `--version` (Linux and macOS also `--check-java`);
  the bundled runtime of the Windows portable app image runs its jar with `--version`, the Linux app image runs
  `bin/VANTA Launcher --version`, and both archives are checked for their expected contents; the `.msi` and `.exe`
  installers are built.
- **publish**: the manifest is built from the real files and checked (names, order, sizes, SHA-256), the GitHub
  Release is created, then every public download URL is downloaded again and re-hashed; the run fails on a single
  mismatch.

### Known limitations

- **Not code-signed.** Windows SmartScreen may show "Windows protected your PC" for the installers and the portable
  app, and the macOS jar is neither signed nor notarized. Verify the SHA-256 from `SHA256SUMS.txt` first (see
  [installation](docs/installation.md#2-verify-the-checksum)).
- **Microsoft sign-in inside the VANTA Launcher needs a client id** — an Azure application that Mojang has approved
  for the Minecraft API. The project does not have one, so PLAY in the VANTA Launcher stays disabled until you set
  `msClientId` / `VANTA_MS_CLIENT_ID`; *Use with Minecraft Launcher* and the manual installation work without it.
- **Windows packages are built and statically checked in CI, not executed there**: the `.msi` and `.exe` are never
  installed or run and the launcher window is never opened on Windows in CI; only command line checks
  (`--version`, `--check-java`) run there, with the jar and the portable app image's bundled runtime.
- **macOS**: the Apple Silicon jar is built and tested from the command line on a macOS runner; its window has not
  been tested. There is no build for Intel Macs, no `.dmg` and no notarization.
- **Linux**: the app image is built for x64 only.

## License

Code is licensed under the [MIT License](LICENSE). The VANTA name, wordmark and logo are not covered by the
license. Minecraft is a trademark of Mojang AB / Microsoft; this project is not affiliated with or endorsed by them.
