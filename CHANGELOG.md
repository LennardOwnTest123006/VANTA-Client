# Changelog

All notable changes to VANTA Client, VANTA Launcher and the website are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the projects use
[Semantic Versioning](https://semver.org/).

Machine-readable release notes live in `website/content/changelog/` and are rendered on the website.

## [Unreleased]

### VANTA Client

#### Added
- **Performance pack in the mods bundle.** `vanta-client-<version>-mods.zip` now carries the newest Minecraft 1.21.11
  Fabric build of every Performance pack mod whose licence allows redistribution (Sodium, Lithium, FerriteCore,
  ImmediatelyFast, Iris), unmodified as published on Modrinth and verified by size and SHA-512 when the release is
  built, next to the VANTA jar and Fabric API. The zip gains `THIRD-PARTY-LICENSES.txt` (every licence text, with the
  file it covers, source and authors; Sodium's PolyForm Shield 1.0.0 terms are passed on), `PERFORMANCE-PACK.txt`
  (the resolved versions, also printed into the GitHub Release notes) and `performance-pack.json`
  (`shared/schemas/performance-pack.schema.json`). `INSTALL.txt` lists the pack jars, the Iris/Sodium pairing and a
  warning about duplicate mod copies. No mod version is fixed in the repository: `scripts/release/performance-pack.mjs`
  resolves them live at release time, excludes any member outside the licence allow-list, follows required
  dependencies (the bundled Sodium must be exactly the version Iris requires) and fails the release when a licence
  text cannot be fetched. CI runs the resolver against the live API on every push.
- **EntityCulling stays a download.** Its licence does not allow redistribution, so it is not in the zip; the game
  offers it with one click on the *Performance pack* card of *Mods & Shaders*.
- **One-time offer at start.** A game that the VANTA Launcher did not start asks once at the main menu, *Boost your
  FPS?*, when Performance pack members are missing. *Install* records the pack jars that came with the bundle in
  `config/vanta/modrinth.json` (so the Installed tab manages them) and installs the missing members from Modrinth
  with the usual toasts; *Not now* turns the offer off (new setting *Settings → Performance → Offer the Performance
  pack at start*, `performance.offerPack`). Nothing is downloaded without that click; the game test never sees the
  dialog.

### VANTA Launcher

#### Fixed
- A notification (toast) in the bottom-right corner could swallow clicks on what lay under it: in a small window
  (960 x 600) that corner holds the lower *Install* buttons of the Mods page, and because a toast stayed as long as the
  mouse rested on it, a mouse moved onto such a button kept the toast open and every click did nothing (in a
  maximised window the Install column is nowhere near that corner). A click anywhere on a toast now dismisses it, and
  the pause while the mouse is over a toast ends after 8 seconds at the latest.

#### Added
- For automated tests the launcher also reads `VANTA_UI_SMOKE_PAGE=<home|mods|versions|logs|settings|about>` (the
  page shown before the screenshot) and `VANTA_UI_SMOKE_SIZE=<width>x<height>` (the window size, raised to the
  960 x 600 minimum); both only together with `VANTA_UI_SMOKE_SCREENSHOT` or `VANTA_UI_SMOKE_EXIT_AFTER`.
  `./gradlew uiSmoke -PsmokePage=mods -PsmokeSize=1000x600` renders one page at one window size with fake services.
- The headless UI test opens the Mods page in a 960 x 600 window and clicks the first and the last *Install* button
  through the platform's robot (the last one after scrolling, and after the toast over it was dismissed by the click).

## [1.1.0] - 2026-10-05

Feature release of both products. VANTA Client 1.1.0 and VANTA Launcher 1.1.0 are published on
[GitHub Releases](https://github.com/LennardOwnTest123006/VANTA-Client/releases) as `client-v1.1.0` and
`launcher-v1.1.0` by the release workflow, with the same file names as before and the new version number
(`vanta-client-1.1.0.jar`, `vanta-client-1.1.0-mods.zip`, `fabric-api-0.141.6+1.21.11.jar`;
`VANTA-Launcher-1.1.0.msi`, `.exe`, `-windows-portable.zip`, `-linux-x64.tar.gz` and
`vanta-launcher-1.1.0-windows-all.jar`, `-linux-all.jar`, `-macos-aarch64-all.jar`). Minecraft 1.21.11, Fabric
Loader 0.19.5, Fabric API 0.141.6+1.21.11 and Java 21 are unchanged. The mods of the new Performance pack are not part
of any VANTA release: they are third-party projects that VANTA downloads from Modrinth when they are installed.

### VANTA Client 1.1.0

#### Added
- **Mods & Shaders** screen, opened from the new *Mods & Shaders* button of the VANTA main menu, from Settings > Video
  and Settings > Performance, from the Performance Center and from the global search: browse and search Modrinth for
  Fabric mods, shader packs for Iris and resource packs for Minecraft 1.21.11, in the tabs Mods, Shaders, Resource
  packs and Installed. With an empty search each tab lists the most downloaded projects; each row shows title, author,
  downloads, description and whether the project is installed, and the panel on the right offers Install, Remove,
  Disable, Enable and *View on Modrinth*.
- **Performance pack**: one click installs Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling and Iris
  Shaders. The newest stable Minecraft 1.21.11 Fabric build of each is looked up on Modrinth at install time (no
  version is fixed in VANTA); a mod without a 1.21.11 version is skipped with a message while the others install.
  Unticked mods are left out; installed ones show as *Installed* or, when loaded, *Active*.
- Required dependencies are installed automatically, in the exact version a mod asks for when it names one. Fabric
  API is not downloaded again, and an install is refused with a message when the project is marked incompatible with
  something already installed.
- Every download comes from the file address Modrinth publishes, over HTTPS, and is checked against the SHA-512
  Modrinth publishes; a file that does not match is deleted. A mod is only put into `mods/` when it and all of its
  dependencies were downloaded and checked.
- Installed tab: everything in `mods/`, `shaderpacks/` and `resourcepacks/`. Mods VANTA installed can be removed and
  switched off and on (renamed between `.jar` and `.jar.disabled`). Files added by hand are listed by file name and
  never changed or deleted by VANTA.
- `config/vanta/modrinth.json` records every installed project (project, version, file, SHA-512, enabled, which
  project needs it); the VANTA Launcher reads and writes the same file.
- After mods change, a *Restart required* banner with *Quit game*, or *Restart game* when the VANTA Launcher started
  the game: VANTA writes `config/vanta/restart.request` and quits, and the launcher starts the game again.
- *Open shader settings* opens the Iris shader pack screen after a shader pack was installed (otherwise VANTA tells
  you to press O, the Iris shader key); without Iris the Shaders tab offers *Install Iris*. *Open resource packs*
  opens the resource pack screen after a resource pack was installed.
- Download progress, results and errors appear as VANTA notifications; downloads run in the background.

#### Changed
- The Performance Center points to the Performance pack (*Want much more FPS?* with *Open Mods & Shaders*): VANTA's
  own presets only change vanilla video options.
- `INSTALL.txt` of the mods bundle (template `scripts/release/mods-bundle/INSTALL.txt`) says which Fabric installer
  to use: on Windows the `.exe`, which needs no separate Java; on macOS and Linux the universal `.jar`, which needs
  Java installed (install Java 21 first, `java -jar fabric-installer-<version>.jar`, on macOS *Open Anyway* under
  System Settings > Privacy & Security if Gatekeeper blocks it). It also says that the Fabric installer asks which
  launcher to use when both Minecraft Launchers are installed, and to choose the one you play with, and it mentions
  Mods & Shaders and the Performance pack. First shipped in `vanta-client-1.1.0-mods.zip`.

#### Fixed
- With no worlds yet, *Singleplayer* opens Create New World; leaving it with *Cancel* or Escape showed the vanilla
  title screen instead of the VANTA main menu. Every way back to the title screen while no world is loaded now ends on
  the VANTA main menu (`Minecraft.setScreen(null)` without a world is handled like `setScreen(new TitleScreen())`).

#### Privacy
- Mods & Shaders talks to Modrinth's public API (`api.modrinth.com`, files from `cdn.modrinth.com`) only while it is
  used: when the screen opens, when you search and when you install. It sends the search text, project and version
  ids, when installing the SHA-512 checksums of mod files VANTA did not install (to recognise them), and a User-Agent
  naming VANTA Client; no account or personal data. See [Privacy](docs/privacy.md#modrinth-mods-shaders-and-the-performance-pack).

### VANTA Launcher 1.1.0

#### Added
- **PLAY via Minecraft Launcher**: when PLAY cannot sign you in (no Microsoft sign-in in this build and no stored
  account), the main button adds or updates the profile "VANTA 1.21.11" in the official Minecraft Launcher and opens
  it. The first time it shows the list of files ("Add profile and open"); afterwards it updates the profile directly.
  Verified files are not downloaded again, and the progress names the step and the file being downloaded.
- **Open Minecraft Launcher** on Home after the profile was set up, and `--open-official-launcher` on the command
  line: starts `MinecraftLauncher.exe` from `Program Files (x86)\Minecraft Launcher` or the Microsoft Store / Xbox app
  version on Windows, `open -a Minecraft` on macOS and `minecraft-launcher` from the `PATH` on Linux; says so when none
  was found (exit code 3) or it could not be started. A running Minecraft Launcher is left alone.
- **Running Minecraft Launcher check**: before *Use with Minecraft Launcher* and *PLAY via Minecraft Launcher* write
  the profile, the launcher looks for a running Minecraft Launcher. If it runs, a dialog names its process and explains
  how to close it completely (on Windows also from the system tray), with *Check again* and *Continue anyway*. VANTA
  never closes it. `--install-official-profile` prints the same warning.
- **Performance pack, on by default**: PLAY, `--install`, *Use with Minecraft Launcher* and
  `--install-official-profile` install the newest Minecraft 1.21.11 Fabric versions of Sodium, Lithium, FerriteCore,
  ImmediatelyFast, Entity Culling and Iris Shaders with their required dependencies (Fabric API is installed by the
  launcher itself, as before), each downloaded from the address Modrinth returns and checked against Modrinth's
  SHA-512; a newer version replaces the older file. If Modrinth cannot be reached or a mod has no 1.21.11 version, the
  install continues without it and says so. Settings > Game: "Install the performance pack" (`installPerformancePack`);
  `--without-performance-pack` skips it once.
- **Mods page**: searches Modrinth for mods (Fabric), shaders (Iris) and resource packs for Minecraft 1.21.11 and
  installs the newest version with its required dependencies into `mods/`, `shaderpacks/` or `resourcepacks/` of the
  VANTA game folder, verified with SHA-512. The installed list shows everything in these folders, including files
  added by hand: switch an entry off (renamed to `<name>.disabled`) or remove it; "Update all" updates every project
  installed from Modrinth. Fabric API and the VANTA Client are marked "VANTA" and stay managed by the launcher; a
  project another one needs cannot be removed or switched off on its own.
- `config/vanta/modrinth.json` in the game folder records every project installed from Modrinth, shared with the VANTA
  Client; keys the launcher does not know are kept.
- **Restart from the game**: games are started with `-Dvanta.launcher.restartable=true`; when the game exits after
  leaving `config/vanta/restart.request`, the launcher removes the file and starts the game again, at most 5 times per
  PLAY (also with `--launch`).
- For automated tests: `VANTA_UI_SMOKE_SCREENSHOT=<png>` and `VANTA_UI_SMOKE_EXIT_AFTER=<seconds>`; without them
  nothing changes.

#### Changed
- Home without Microsoft sign-in reads "Ready to play" and "Ready via the Minecraft Launcher" instead of "Not ready",
  and the box *Why the Minecraft Launcher?* explains why the Minecraft Launcher signs you in.
- *Use with Minecraft Launcher* shows its progress on Home (preparing, each step, the file being downloaded).
- Every start writes the launcher log from its first line, with the Java, JavaFX and system details.
- Requests to Modrinth send a User-Agent that names the project, and the launcher waits and retries when Modrinth
  answers HTTP 429.

#### Fixed
- The official Minecraft Launcher did not show the profile "VANTA 1.21.11" until it was restarted, because it reads
  its profiles only when it starts. The launcher now asks you to close it completely first, and *Open Minecraft
  Launcher* starts it afterwards.
- When the launcher could not start (for example because its window could not be created), it closed without any
  message. Any start-up error is now shown in a dialog with the details (a plain system dialog when JavaFX itself
  could not start), saved to `logs/startup-error.txt` in the launcher data directory
  (`%APPDATA%\VANTA Launcher\logs\startup-error.txt` on Windows; the previous one is kept as
  `startup-error.previous.txt`) and the launcher exits with code 1.

#### Updating to 1.1.0
- Launcher 1.0.0 still picks the update by system only: the `.msi` on Windows (also for the portable folder or a
  jar) and the `.tar.gz` on Linux x64 (also for a jar). Portable and jar users choose *Not now* (or *Close* before
  downloading) and download `VANTA-Launcher-1.1.0-windows-portable.zip` or the jar for their system from the release
  `launcher-v1.1.0` instead ([Installation → Updating](docs/installation.md#updating)).
- Launcher 1.0.0 and 1.0.1 save the update as `cache/updates/1.1.0-<file name>`: the verified release file under a
  different name. Rename it to the release name before checking it with
  `sha256sum -c --ignore-missing SHA256SUMS.txt`. Launcher 1.0.2 keeps the release name.

### CI
- **Client game test with the Performance pack**: a second run of the headless production game test with the newest
  Minecraft 1.21.11 Fabric versions of the six Performance pack mods on Modrinth at the time of the run; it fails when
  one of them is not loaded, the game crashes or the test does not finish. Before it, a live check
  (`./gradlew liveTest` in `core/`) resolves and downloads the pack through VANTA's Modrinth client and verifies every
  SHA-512.
- **Launcher integration**: `--install` now installs the Performance pack from the live Modrinth API; every jar is
  checked against `modrinth.json` with `sha512sum` and against Modrinth itself (`/v2/version_file/<sha512>`), Fabric API
  must not be added a second time, and the headless game must load all six mods next to the VANTA Client and reach
  its main menu.
- **Launcher starts on Windows**: a job on a Windows runner installs the CI-built `.msi` (`msiexec /qn`), unpacks the
  portable zip and runs the Windows jar, and fails unless each of them shows the launcher window and exits with 0;
  screenshots and logs go to the `ci-artifacts` branch.

### Website
- Features page and home page: *Mods & Shaders* (Modrinth) and the *Performance pack*. The Download page's *How to
  install* describes *PLAY via Minecraft Launcher* and the Performance pack installed by default. A news post announces
  1.1.0. Texts that said the client opens no connections of its own name the Modrinth exception.

### Documentation
- New page [Mods & Shaders](docs/mods-and-shaders.md): the in-game screen, the launcher's Mods page, the Performance
  pack, shaders with Iris, resource packs, disabling and removing, restart, where files go and what is sent to
  Modrinth.
- [Installation](docs/installation.md), [Launcher](docs/launcher.md), [FAQ](docs/faq.md), [Fabric](docs/fabric.md),
  the documentation index and the README describe *PLAY via Minecraft Launcher*, closing the Minecraft Launcher first,
  *Open Minecraft Launcher*, the Performance pack and the 1.1.0 file names. [Launcher](docs/launcher.md) has sections
  for the Mods page, the Performance pack setting, restart from the game, start-up errors and the new command line
  flags.
- [Troubleshooting](docs/troubleshooting.md): *The profile VANTA 1.21.11 does not show up in the Minecraft Launcher*,
  *The launcher does not start or Windows blocks it* (SmartScreen, Smart App Control, `startup-error.txt`), the vanilla
  title screen after *Create New World* (fixed in 1.1.0) and *Mods & Shaders problems*.
- [Privacy](docs/privacy.md), [Statistics and privacy](docs/statistics-and-privacy.md) and `SECURITY.md` describe the
  Modrinth requests; [Performance Center](docs/performance.md) describes the Performance pack.
- The setting that switches the VANTA main menu off is called *VANTA main menu* (Settings > General); some pages
  called it "Replace the title screen".

## [Launcher 1.0.2] - 2026-10-05

Launcher-only maintenance release. VANTA Launcher 1.0.2 is published on
[GitHub Releases](https://github.com/LennardOwnTest123006/VANTA-Client/releases) as `launcher-v1.0.2` by the release
workflow, with the same file names as 1.0.1 and the new version number (`VANTA-Launcher-1.0.2.msi`, `.exe`,
`-windows-portable.zip`, `-linux-x64.tar.gz` and `vanta-launcher-1.0.2-windows-all.jar`, `-linux-all.jar`,
`-macos-aarch64-all.jar`). **VANTA Client stays at 1.0.1** (`client-v1.0.1`); Minecraft 1.21.11, Fabric Loader 0.19.5,
Fabric API 0.141.6+1.21.11 and Java 21 are unchanged.

### VANTA Launcher 1.0.2

#### Fixed
- **Update download file names**: launcher self-updates were saved as `cache/updates/<version>-<file name>`, so the
  dialog said to run, for example, `java -jar 1.0.2-vanta-launcher-1.0.2-linux-all.jar` and
  `sha256sum -c SHA256SUMS.txt` could not find the file. Downloads are now saved as
  `cache/updates/<version>/<file name>` with the exact name of the release file. The dialog after a verified download
  shows the full path in a field you can select and copy and offers *Show in folder* (next to *Open installer* for the
  Windows installer), and its `java -jar` command uses the full, quoted path.
- **Verify files on a fresh launcher**: it started a full install of Minecraft, Fabric and the VANTA Client. It is now
  shown only when an installation exists, never starts a first install and has a tooltip that says what it does; PLAY
  and the client card's *Install now* still install deliberately. As before, it also replaces an older VANTA Client
  with the latest release; its notification now says so instead of "Nothing was missing or damaged".
- **Versions page while nothing is installed**: it said "Press PLAY on the Home screen to install …" even when PLAY
  could not be enabled because Microsoft sign-in is not configured and no account is stored. It now points to *Use
  with Minecraft Launcher* in that case, and after *Use with Minecraft Launcher* it says that the game is started from
  the Minecraft Launcher with the profile "VANTA 1.21.11". With sign-in configured, or with a stored account, it still
  says to press PLAY, like the VANTA Client card on Home.
- **Rollback message**: the "VANTA Client <version> installed" notification always said that the previous version is
  kept for roll back. It now names the kept version only when a copy of it really exists on the Versions page.
  Otherwise it says that no copy of the replaced version is kept, and either that the Versions page lists other kept
  versions to roll back to or that no other version is kept.
- **Data directory button**: in Settings → Advanced a long data directory path shrank the *Open* button to "...". The
  button keeps its label, the path is shortened with "..." in the middle and its tooltip shows the full path.
- **Opening links**: *Website*, *Support*, *How to configure* and the release notes link could silently do nothing
  when JavaFX could not start a browser; the error only went to the console. The launcher now tries Java's desktop
  integration, then the system's own opener (`xdg-open` on Linux, `open` on macOS,
  `rundll32 url.dll,FileProtocolHandler` on Windows, checked by its exit code), then JavaFX. If none of them accepted
  the link, the address is copied to the clipboard and shown in a notification. If only JavaFX accepted it (it cannot
  tell whether a browser opened), the address is shown in a notification and the clipboard is left alone, so a
  sign-in code you just copied stays there.

#### Added
- **Rollback copy of the replaced jar**: a VANTA Client update keeps a rollback copy of the jar it replaces when that
  jar has a release version but no copy yet (for example a jar put into `mods/` by hand), as long as it is among the
  three newest versions afterwards. Only the three newest versions are kept, as before. The Versions page marks such a
  copy as "Local copy from mods/": it is the jar exactly as it was, not a downloaded release, and *Roll back* makes
  exactly that file active again. The caption above the list now speaks of "client versions" instead of "verified
  releases".

#### Changed
- **Windows on ARM**: a launcher jar started by an arm64 Java on Windows recommends `VANTA-Launcher-<version>.msi` or
  `VANTA-Launcher-<version>-windows-portable.zip` (both x64 with their own x64 Java runtime; Windows 11 on ARM runs
  them under x64 emulation) or an x64 Java 21 with `vanta-launcher-<version>-windows-all.jar`, and
  `--install-official-profile` on the command line where there is no x64 emulation (Windows 10 on ARM), instead of
  saying that there is no download.
- **Sidebar sign-in text**: without Microsoft sign-in the account chip says "Sign-in not available" (it said "Sign in
  with Microsoft to play", cut off at the default window size 1120×720) and offers *How to configure*, like the Home
  account card, instead of a *Sign in* button; its tooltip explains how to play through the Minecraft Launcher. With
  sign-in configured it says "Sign in with Microsoft" above the *Sign in* button.
- **High contrast help**: Settings → Appearance → *High contrast* has its own description (stronger borders,
  brighter secondary and muted text, a brighter focus outline) instead of repeating the theme text.

#### Updating to 1.0.2
- Launcher 1.0.0 still picks the update by system only: the `.msi` on Windows (also for the portable folder or a jar)
  and the `.tar.gz` on Linux x64 (also for a jar). Portable and jar users choose *Not now* (or *Close* before
  downloading) and download `VANTA-Launcher-1.0.2-windows-portable.zip` or the jar for their system from the release
  `launcher-v1.0.2` instead ([Installation → Updating](docs/installation.md#updating)). From 1.0.1 on the launcher
  picks the file by packaging.
- Launcher 1.0.0 and 1.0.1 save the update as `cache/updates/1.0.2-<file name>`: the verified release file under a
  different name. Rename it to the release name before checking it with
  `sha256sum -c --ignore-missing SHA256SUMS.txt`.

### Website
- **Note for launcher 1.0.0 users in the release notes**: the notes of launcher 1.0.1
  (`website/content/changelog/launcher-1.0.1.md`) have a *Notes* section that tells portable and jar users of
  launcher 1.0.0 to choose *Not now* and download the portable zip or their jar from the release page; the notes of
  launcher 1.0.2 carry the same note for the update to 1.0.2, next to a note for checking a download of launcher 1.0.0
  or 1.0.1 with `SHA256SUMS.txt`. The intro of both points to these notes, because the update dialog starts at the
  intro. Launchers load the release notes of an update from
  `raw.githubusercontent.com/LennardOwnTest123006/VANTA-Client/HEAD/<changelog path>`, so the notes reach running
  1.0.0 and 1.0.1 launchers without a new release.
- **Canonical URLs**: the canonical link and `og:url` use the canonical path of the route: the query string and the
  hash are dropped, trailing slashes removed (except for `/`), and a path spelled in another letter case uses the
  route's spelling (`/Download/?ref=x` declares `/download`).
- **Download card layout**: the download button, the "Not published yet" notice and the footnote follow right after
  the release facts, so the button stays within the first screen on a phone. The "In this release" excerpt comes after
  them and quotes at most three bullets, each cut to three lines, from `## Fixed` first, then `## Added` and
  `## Improved` (never `## Notes`).
- **`VITE_SITE_URL` in `netlify.toml`**: `[build.environment]` sets
  `VITE_SITE_URL = "https://vanta-client.netlify.app"`, so a repository-linked Netlify build has the same absolute
  `sitemap.xml` URLs, `Sitemap` line in `robots.txt`, link-preview images and canonical base as a build deployed by
  hand with that variable.
- **Screenshots**: the page description no longer names the current client version (the captures keep showing the
  version they were taken with), and the keybinds caption says that the binding shown as "Open VANTA menu" in the
  1.0.0 capture is called "Open VANTA settings" since 1.0.1.

### Documentation
- *Use with Minecraft Launcher* was described as writing its profile "like the Fabric installer". It writes into every
  profiles file that exists, while the Fabric installer asks which launcher to use when both files exist and writes
  only that one. Corrected in the README, this changelog's 1.0.0 entry, [Installation](docs/installation.md),
  [Launcher](docs/launcher.md), [Fabric](docs/fabric.md) and [Troubleshooting](docs/troubleshooting.md).
- The manual installation (README, [Installation → Manual installation](docs/installation.md#c-manual-installation),
  [Fabric](docs/fabric.md#manual-installation-into-an-existing-fabric-profile), FAQ) says which Fabric installer to
  use: the `.exe` on Windows, which needs no separate Java, and the universal `.jar` on macOS and Linux, which needs
  Java 21 installed first (`java -jar fabric-installer-<version>.jar`; on macOS Gatekeeper may need *Open Anyway*), and
  that the Fabric installer asks which launcher to use when both Minecraft Launchers are installed.
- README, Installation, FAQ, Troubleshooting and the documentation index name the launcher 1.0.2 files and the release
  `launcher-v1.0.2`; *Updating from launcher 1.0.0* covers the update to 1.0.2 and the `1.0.2-<file name>` downloads
  of launcher 1.0.0 and 1.0.1.
- [Launcher](docs/launcher.md) describes the 1.0.2 behaviour (Verify files, the Versions page while nothing is
  installed, the sidebar, update file names and the update dialog, rollback copies and the notification, the data
  directory, how links are opened, High contrast) and that a client update is downloaded into
  `versions/vanta-client/<version>/`, not `cache/updates/`. [Troubleshooting](docs/troubleshooting.md) has a section
  [Windows on ARM: which launcher file?](docs/troubleshooting.md#windows-on-arm-which-launcher-file) and entries for
  the renamed update downloads, a missing *Verify files* and links that do not open.
- [RELEASE.md](RELEASE.md) shows the launcher-only version bump, that release notes reach running launchers from the
  default branch, and the update file names.

## [1.0.1] - 2026-10-05

Maintenance release with fixes for problems found in 1.0.0. VANTA Client 1.0.1 and VANTA Launcher 1.0.1 are published
on [GitHub Releases](https://github.com/LennardOwnTest123006/VANTA-Client/releases) as `client-v1.0.1` and
`launcher-v1.0.1` by the release workflow, with the same file names as 1.0.0 and the new version number
(`vanta-client-1.0.1.jar`, `vanta-client-1.0.1-mods.zip`, `fabric-api-0.141.6+1.21.11.jar`;
`VANTA-Launcher-1.0.1.msi`, `.exe`, `-windows-portable.zip`, `-linux-x64.tar.gz` and
`vanta-launcher-1.0.1-windows-all.jar`, `-linux-all.jar`, `-macos-aarch64-all.jar`). Minecraft 1.21.11, Fabric
Loader 0.19.5, Fabric API 0.141.6+1.21.11 and Java 21 are unchanged.

### VANTA Client 1.0.1

#### Fixed
- The Right Shift key binding was called "Open VANTA menu" with the description "Opens the VANTA hub screen", but it
  opens the VANTA settings. It is now "Open VANTA settings" / "Opens the VANTA settings", and `/vanta menu` reports
  "Opening the VANTA settings…".

#### Changed
- `INSTALL.txt` in `vanta-client-1.0.1-mods.zip` now starts with step 0, "start the official Minecraft Launcher once"
  (until the launcher has created the Minecraft folder and its profiles file, the Fabric installer stops with "No
  launcher directory found!" or "No launcher profile.json found!"), says to keep "Create profile" checked in the Fabric
  installer, shows how to verify both jars on Windows with PowerShell
  (`Get-FileHash mods\*.jar -Algorithm SHA256 | Format-List Hash, Path`) or `certutil`, and explains that the zoom
  key C is also vanilla's "Save Hotbar Activator" (Creative mode only) and how to rebind either one. The default zoom
  key stays C.

### VANTA Launcher 1.0.1

#### Fixed
- **Client card on a fresh launcher**: it offered *Update* although no client was installed; *Install update* put
  the jar into `mods/`, the card still said "Not installed" and the same update came back after a restart. Without an
  installed client the card now shows *Not installed yet* with *Install now* (the installation PLAY does, without
  starting the game; when sign-in is configured) and *Use with Minecraft Launcher*, and neither the update banner nor
  the update dialog offers a client update. The card, the banner, the update check and `--check-update` read the
  installed client from the same place (the `vanta-client-<version>.jar` in `mods/` and `instance.json`), so a client
  installed with *Use with Minecraft Launcher* counts as installed. `--check-update` prints
  `Client: not installed (install it with --install or --install-official-profile); latest release <version>`.
- **Update banner text**: it could read "You have Not installed yet"; a client that is not installed now has its own
  sentence.
- **Account card buttons**: at the default window size (1120×720) *How to configure* and *Settings* were cut off
  ("How to co…", "S…"). They are stacked at the card width with their full labels and have tooltips.
- **Wrong-platform jar**: a launcher jar started on another system (for example the Windows jar on Linux) failed
  inside JavaFX or showed nothing. It now checks the system before JavaFX is loaded, names the file to download instead
  (for example `vanta-launcher-1.0.1-linux-all.jar` or `VANTA-Launcher-1.0.1-linux-x64.tar.gz`), shows the message in
  a window as well when a display is available, and exits with code 1. Any other failed start of the window (JavaFX
  missing, no display) also exits with code 1 instead of printing the help and exiting with 0. Command line options
  keep working with every jar.
- **Sign-in countdown race**: the expiry countdown of a new sign-in code could be overwritten by its initial value when
  the first timer tick came early.

#### Changed
- **Packaging-aware self-update**: the launcher updates itself with the file that replaces the running installation:
  the `.msi` (then `.exe`) for a launcher installed with the `.msi` or `.exe`; `VANTA-Launcher-<version>-windows-portable.zip`
  for the Windows portable folder, recognised by `VANTA Launcher/app/vanta-portable.marker`, which only the portable
  zip contains (downloaded and verified, then shown in its folder with the instruction to close the launcher and
  extract it into the folder that contains the old `VANTA Launcher` folder, replacing the existing files, or, for a
  renamed folder, to copy the contents of the zip's `VANTA Launcher` folder into it; the launcher never unpacks or
  runs it); `vanta-launcher-<version>-windows-all.jar` or `vanta-launcher-<version>-linux-all.jar` for a launcher
  started with `java -jar`; the `.tar.gz` for the Linux app image; `vanta-launcher-<version>-macos-aarch64-all.jar`
  on Apple Silicon Macs. Intel Macs and other systems get the release page. This applies to updates offered by
  launcher 1.0.1 and newer: launcher 1.0.0 still offers the 1.0.1 update by system only (the `.msi` on Windows, also
  in the portable folder and for a jar; the `.tar.gz` on Linux x64, also for a jar). Portable and jar users choose
  *Not now* and download `VANTA-Launcher-1.0.1-windows-portable.zip` or the jar for their system from the release
  `launcher-v1.0.1` instead ([Installation → Updating](docs/installation.md#updating)).
- **Decimal sizes**: file sizes are shown in decimal units with one decimal place (1 MB = 1,000,000 bytes, for example
  "66.9 MB") in the window and on the command line, the same numbers as the website and the release notes.
- **Website link**: the *Website* entries in the sidebar and on the About page are active and open
  https://vanta-client.netlify.app.

### Release process
- The file sizes in the GitHub release notes (`scripts/release/release-assets.mjs`) use decimal units with one decimal
  place, like the website's Download page.
- The release workflow adds `VANTA Launcher/app/vanta-portable.marker` (text `portable`) to the Windows app image
  right before zipping it as the portable build and checks it in the zip; the `.msi` and `.exe` are built from a fresh
  app image and checked not to contain it.
- The CI builds of the launcher (`ci-artifacts` branch) take their file names from `launcher/gradle.properties`
  instead of a fixed 1.0.0, and the CI portable zip carries `vanta-portable.marker` like the release one.

### Website
- **Same sizes everywhere**: the Download page rounds file sizes half up in integer arithmetic, with the same rule as
  the release notes and the launcher (1,450,000 bytes is "1.5 MB", not "1.4 MB", and 999,950 bytes is "1.0 MB", not
  "1000.0 kB").
- **Stale-chunk recovery**: a tab opened before a deploy asked for page chunks that no longer exist and failed. The
  site now reloads once to fetch the new build (at most once per session and build, so a real outage is not a reload
  loop), and an error on one page is cleared when you navigate to another.
- **Absolute link-preview images**: when `VITE_SITE_URL` is set at build time, `index.html` gets absolute `og:image`
  / `og:image:secure_url` / `twitter:image` addresses, so link previews in chat apps and social networks find the
  image. `og:url` and the canonical link stay per page (set at runtime by `PageMeta`), because the same `index.html`
  answers every route.
- **No build metadata in the deploy**: the Vite build manifest (`dist/.vite/`) is deleted after the bundle-size check
  and is no longer published.
- **Download page keeps the newest published release**: a new version is committed with an unpublished manifest before
  the release workflow runs. The Download page used to replace the published release with "Not published yet" in the
  meantime; it now keeps offering the newest published release, mentions the upcoming version, and the changelog marks
  that version as not published yet.
- **Netlify ignore command** (`netlify.toml`): the build was skipped whenever the cached commit equalled the current
  one, which is the case for the first build of a newly linked site and for "Clear cache and deploy", so such a site
  never deployed. The command now always builds in that case (and when there is no cached commit or `git diff` fails)
  and otherwise skips only when nothing under `website/`, `docs/`, `shared/`, `assets/screenshots/` or `netlify.toml`
  changed.

### Documentation
- The FAQ entry "Why is there no download yet?" is now "Where do I download VANTA?" (Download page and GitHub
  Releases, which file for which system, what "Not published yet" means); the website's support page links to it.
- Statements about the Minecraft Launcher from the Microsoft Store or the Xbox app now say what is tested: CI checks
  the contents VANTA writes into `launcher_profiles_microsoft_store.json` on Linux; whether that launcher shows the
  profile has not been tested on a real Windows PC.
- The zoom key C and vanilla's "Save Hotbar Activator" are documented in [Keybinds](docs/keybinds.md#zoom); the
  README no longer claims that CI renders Java2D previews of every screen (CI runs the core's unit tests).
- [Keybinds → Conflict detection](docs/keybinds.md#conflict-detection), the FAQ and Troubleshooting explain that the
  keybind manager already lists several conflicts between vanilla bindings with every key at its default (for example
  on A, S, D and the middle mouse button) and that the conflict on C (Zoom and Save Hotbar Activator) is the only one
  VANTA adds.
- [Installation → Updating](docs/installation.md#updating) says to extract the portable zip into the folder that
  contains the `VANTA Launcher` folder (extracting it into that folder only nests a second copy) and how portable and
  jar users update from launcher 1.0.0, which offers the `.msi` or `.tar.gz`.

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
  `versions/fabric-loader-0.19.5-1.21.11/` of the official Minecraft folder and adds or updates the profile
  `vanta-1.21.11` ("VANTA 1.21.11") in every profiles file of the official Minecraft Launcher that exists there:
  `launcher_profiles.json` (Minecraft Launcher from minecraft.net) and/or `launcher_profiles_microsoft_store.json`
  (Minecraft Launcher from the Microsoft Store or the Xbox app). Unlike the official Fabric installer, which asks
  which launcher to use when both files exist and writes only that one, it writes into every file that exists. Every other
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
