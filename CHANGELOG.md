# Changelog

All notable changes to VANTA Client, VANTA Launcher and the website are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the projects use
[Semantic Versioning](https://semver.org/).

Machine-readable release notes live in `website/content/changelog/` and are rendered on the website.

## [Unreleased]

No changes yet.

## [1.0.0] - 2026-10-05

First public release. VANTA Client 1.0.0 and VANTA Launcher 1.0.0 are published on
[GitHub Releases](https://github.com/LennardOwnTest123006/VANTA-Client/releases) as `client-v1.0.0` and
`launcher-v1.0.0` by the release workflow. The date is the `releaseDate` the workflow wrote into
`shared/releases/client-1.0.0.json` and `shared/releases/launcher-1.0.0.json`.

### VANTA Client 1.0.0 — Minecraft 1.21.11 · Fabric Loader 0.19.5 · Fabric API 0.141.6+1.21.11 · Java 21

#### Added
- Premium VANTA main menu replacing the vanilla title screen (PLAY, SINGLEPLAYER, MULTIPLAYER, OPTIONS, LANGUAGE,
  RESOURCE PACKS, ACCESSIBILITY, QUIT) with version label, vanilla fallback setting
- Customizable HUD with movable widgets (FPS, ping, coordinates, direction, biome, server, CPS, clock, armor,
  item durability, potion effects, keystrokes, memory, CPU, entity count, Minecraft version) and a HUD editor with
  drag, scale, opacity, colors and presets
- Performance Center with live FPS / frame time / memory / render and simulation distance / entity count and the
  LOW, BALANCED, HIGH and ULTRA presets built from vanilla video options
- Settings system with categories, search, tooltips, reset-to-default and keyboard navigation
- Keybind manager with search, rebinding, reset and conflict detection on top of vanilla key mappings
- Crosshair customizer with shape, size, thickness, gap, outline, opacity, color and presets
- Cosmetics: UI themes, menu backgrounds, HUD themes, menu particles and profile badges (visual only)
- Local profiles (DEFAULT, PVP, BUILDING, PERFORMANCE, RECORDING) storing settings, HUD, keybinds and visuals,
  with JSON import/export
- Notification system, global settings search, resource pack manager, local statistics dashboard with privacy
  controls, accessibility options (UI scale, reduced motion, high contrast, larger text, reduced transparency)

#### Release files
- `vanta-client-1.0.0.jar` — the Fabric mod
- `vanta-client-1.0.0-mods.zip` — mods bundle for a manual installation: `mods/vanta-client-1.0.0.jar` and
  `mods/fabric-api-0.141.6+1.21.11.jar`, `INSTALL.txt` with the steps for the Fabric installer and the official
  Minecraft Launcher, and `SHA256SUMS` (`sha256sum -c` compatible)
- `fabric-api-0.141.6+1.21.11.jar` — the unmodified FabricMC Fabric API jar (Apache-2.0), which VANTA requires,
  published next to the mod
- `SHA256SUMS.txt` and the release manifest `client-1.0.0.json`

### VANTA Launcher 1.0.0

#### Added
- Detects Java 21, installs Minecraft 1.21.11 and Fabric Loader from official sources with checksum verification,
  installs Fabric API and the VANTA Client jar, Microsoft account sign-in (device code flow), logs, settings,
  version information, update checks against SHA-256 verified release manifests
- **Use with Minecraft Launcher** (Home screen button, CLI `--install-official-profile [--minecraft-dir <path>]`):
  installs Fabric API and the VANTA Client into the VANTA instance, writes the Fabric Loader 0.19.5 version files to
  `versions/fabric-loader-0.19.5-1.21.11/` of the official Minecraft folder and, like the official Fabric installer,
  adds or updates the profile `vanta-1.21.11` ("VANTA 1.21.11") in every profiles file of the official Minecraft
  Launcher that exists there: `launcher_profiles.json` (Minecraft Launcher from minecraft.net) and/or
  `launcher_profiles_microsoft_store.json` (Minecraft Launcher from the Microsoft Store or the Xbox app). Every other
  entry is kept, each file gets a one-time backup `<file>.vanta-backup` (`launcher_profiles.json.vanta-backup`,
  `launcher_profiles_microsoft_store.json.vanta-backup`) and is written atomically, and VANTA never creates a
  profiles file. The official Minecraft Launcher then downloads Minecraft and Java and handles Microsoft
  sign-in. The Home screen offers it prominently while Microsoft sign-in is not configured. CI checks what VANTA writes
  into both files on Linux; whether the Minecraft Launcher from the Microsoft Store or the Xbox app shows the profile
  has not been tested on a real Windows machine.
- **Built-in releases URL**
  `https://raw.githubusercontent.com/LennardOwnTest123006/VANTA-Client/HEAD/shared/releases/latest`: update checks
  and the VANTA Client download work without configuration. A non-empty Settings value (`releasesBaseUrl`, or
  `--releases-url` for one run) wins over `VANTA_RELEASES_BASE_URL`, which wins over the default; Settings shows the
  URL in use and where it comes from, and *Reset to default* clears an override.
- **Per-platform release files**: Windows x64 `.msi` and `.exe` installers and a portable `.zip` (all with the
  Java 21 runtime), a Linux x64 app image `.tar.gz` with the runtime, and three launcher jars with the JavaFX natives
  of one platform each — `vanta-launcher-1.0.0-windows-all.jar`, `vanta-launcher-1.0.0-linux-all.jar` and
  `vanta-launcher-1.0.0-macos-aarch64-all.jar` (Apple Silicon; built and tested from the command line on a macOS
  runner, window not tested, unsigned)
- Launcher self-update picks the file for the running platform: the `.msi` on Windows, the `.tar.gz` on Linux x64,
  the Apple Silicon jar on Apple Silicon macOS, and the release page on other platforms. Client installs and updates
  take exactly `vanta-client-<version>.jar` from the client release.

#### Fixed
- Network failures now always end with exit code 5 (network failure) and a message that names the step and the URL:
  TLS errors, connection resets and HTTPS proxies that refuse the `CONNECT` tunnel (403/407) used to fall through to
  exit code 1. Exit code 3 now means a missing Microsoft client id, an unusable releases URL or a Minecraft folder
  with neither `launcher_profiles.json` nor `launcher_profiles_microsoft_store.json`; a release that is not published
  is always exit code 7.

#### Known limitations
- Microsoft sign-in inside the launcher needs an application id approved by Mojang for the Minecraft API
  (`msClientId` / `VANTA_MS_CLIENT_ID`). This release does not include one, so PLAY stays disabled; use
  *Use with Minecraft Launcher* instead.
- The installers, the portable app and the jars are not code-signed. The Windows packages are built and checked in CI
  but not installed or run there.

### Website 1.0.0
- Official website with download center, features, performance, screenshots, changelog, news, searchable
  documentation, support center, FAQ, about, privacy and terms pages; Netlify deployment configuration
- The Download page lists every file of each release in manifest order — what it is, size, SHA-256 with a copy
  button and its own download link — plus a link to the GitHub release page; the client card offers the mods bundle
  as a second download and the three ways to install. Files without a published URL are listed but never linked.

### Release process
- Real GitHub Releases: `.github/workflows/release.yml` (tag push or *Run workflow*; one concurrency group per
  product, so client and launcher releases can run side by side) builds the client on Linux and the launcher on
  Windows, Linux and Apple Silicon macOS, uploads exactly the files defined in `scripts/release/release-assets.mjs`,
  then downloads every public URL again and re-hashes it. The completed manifests come back on the `ci-artifacts`
  branch (`release-<tag>/`) and, when allowed, as a pull request; see [RELEASE.md](RELEASE.md).
