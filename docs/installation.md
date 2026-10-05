---
title: Installation
description: Three ways to install VANTA for Minecraft 1.21.11, the right download for each system, SHA-256 verification, the first start and where files are stored.
order: 1
category: Getting started
---

## Before you start

- A **Microsoft account that owns Minecraft Java Edition**. VANTA never bypasses the ownership check.
- A **64-bit system**. The VANTA Launcher is published for Windows 10/11 x64, Linux x64 and Apple Silicon macOS
  (see [the download table](#vanta-launcher-files)). On other systems (Intel Macs, Linux on ARM) use the manual
  installation.
- **Java 21** for the game. The Windows installers, the Windows portable app and the Linux app image bring their own
  runtime for the launcher itself; the launcher then finds an installed Java 21 for the game or installs Eclipse
  Temurin 21 for you. The `-all.jar` launcher files need Java 21 installed. With the official Minecraft Launcher
  (installation paths B and C) the Minecraft Launcher provides Java.
- A graphics card and driver with **OpenGL 3.2** or newer, and a few gigabytes of free disk space.

Details are in [Minecraft requirements](minecraft-requirements.md) and [Java 21](java-21.md).

## Choose how to install

| | A. VANTA Launcher | B. VANTA Launcher → *Use with Minecraft Launcher* | C. Manual |
| --- | --- | --- | --- |
| You download | the launcher file for your system | the launcher file for your system | `vanta-client-1.0.1-mods.zip` and the Fabric installer |
| Who installs Minecraft and Java | the VANTA Launcher | the official Minecraft Launcher | the official Minecraft Launcher |
| Who signs you in | the VANTA Launcher (needs a Microsoft client id, see below) | the official Minecraft Launcher | the official Minecraft Launcher |
| Game folder | `<data directory>/instances/vanta-1.21.11` | the same VANTA instance | your `.minecraft` folder |
| Checksums verified for you | every file | Fabric API and the VANTA Client by VANTA; the rest by the Minecraft Launcher | the two jars by you, with `SHA256SUMS` |

**Which one should I use?** Microsoft sign-in inside the VANTA Launcher needs an application id that Mojang has
approved for the Minecraft API. The project does not have one, so in the published launcher **PLAY stays disabled**
and the Home screen says so. Until that changes, play with **path B** (one button in the VANTA Launcher) or
**path C** (no VANTA Launcher at all). Path A works when you configure an approved client id yourself
([Launcher → Microsoft client id](launcher.md#microsoft-client-id)).

## 1. Download

Every file is published on GitHub Releases by the project's release workflow, and the Download page of the website
([vanta-client.netlify.app/download](https://vanta-client.netlify.app/download)) lists the same files with size and
SHA-256:

- all releases: [github.com/LennardOwnTest123006/VANTA-Client/releases](https://github.com/LennardOwnTest123006/VANTA-Client/releases)
- VANTA Launcher 1.0.1: release [`launcher-v1.0.1`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.0.1)
- VANTA Client 1.0.1: release [`client-v1.0.1`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.0.1)
- the previous releases [`launcher-v1.0.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.0.0)
  and [`client-v1.0.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.0.0) stay
  available

Each file's direct address is `https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/<release>/<file>`.
Treat a VANTA download from anywhere else as untrusted. The tables below name the 1.0.1 files; another version has
the same names with its own version number. Sizes on the Download page, in the release notes and in the launcher
are decimal with one decimal place (1 MB = 1,000,000 bytes), so a file of 66,900,000 bytes is shown as "66.9 MB".

### VANTA Launcher files

From the release `launcher-v1.0.1`:

| Your system | File | What it is |
| --- | --- | --- |
| Windows 10/11 x64 | `VANTA-Launcher-1.0.1.msi` | **recommended**: per-user installer, no administrator rights, includes the Java 21 runtime |
| Windows 10/11 x64 | `VANTA-Launcher-1.0.1.exe` | the same installer as an `.exe`, for systems that block `.msi` files |
| Windows 10/11 x64 | `VANTA-Launcher-1.0.1-windows-portable.zip` | no installation: unzip and run `VANTA Launcher\VANTA Launcher.exe`; includes the Java 21 runtime |
| Windows 10/11 x64 | `vanta-launcher-1.0.1-windows-all.jar` | single jar for Windows x64; needs Java 21 installed |
| Linux x64 | `VANTA-Launcher-1.0.1-linux-x64.tar.gz` | **recommended**: app image, includes the Java 21 runtime; run `VANTA Launcher/bin/VANTA Launcher` |
| Linux x64 | `vanta-launcher-1.0.1-linux-all.jar` | single jar for Linux x64; needs Java 21 installed |
| macOS, Apple Silicon (M1 or newer) | `vanta-launcher-1.0.1-macos-aarch64-all.jar` | single jar for Apple Silicon; needs Java 21 installed; not signed. It is built and tested from the command line on a macOS machine in CI; its window has not been tested |
| macOS on Intel, Linux on ARM, other | — | no launcher build: use [path C](#c-manual-installation) |

**There is no single launcher jar for every system.** Each `-all.jar` contains the JavaFX libraries of one system
only: the Windows jar does not start on Linux or macOS, the Linux jar does not start on Windows or macOS, and the
macOS jar needs an Apple Silicon Mac. Pick the file with your system in its name. A jar started on the wrong system
does not open its window: it prints which file to download instead (and shows the same text in a message window when
you double-clicked it) and exits with code 1. Command line options such as `--help` and `--version` work with every
jar.

### VANTA Client files

From the release `client-v1.0.1`:

| File | What it is |
| --- | --- |
| `vanta-client-1.0.1.jar` | the VANTA Client Fabric mod. The VANTA Launcher downloads it for you (paths A and B) |
| `vanta-client-1.0.1-mods.zip` | for path C: a `mods/` folder with `vanta-client-1.0.1.jar` and `fabric-api-0.141.6+1.21.11.jar`, plus `INSTALL.txt` (the steps of path C) and `SHA256SUMS` |
| `fabric-api-0.141.6+1.21.11.jar` | Fabric API, the unmodified FabricMC release (Apache-2.0). VANTA requires it; it is also inside the mods bundle |

Both releases also contain `SHA256SUMS.txt` (the SHA-256 of every file above) and the release manifest
(`launcher-1.0.1.json`, `client-1.0.1.json`) that the website and the launcher read.

If the Download page says a version is **not published yet**, its files are not out yet and the page keeps offering
the newest published release; see
[Troubleshooting → The Download page says "Not published yet"](troubleshooting.md#the-download-page-says-not-published-yet).

## 2. Verify the checksum

The files are **not code-signed**, so the SHA-256 checksum is how you know a file is the one the release workflow
built. Every checksum is on the Download page, in `SHA256SUMS.txt` on the release page and in the release manifest.
Check before you run or install anything.

**Windows** (PowerShell; the second command also works in `cmd`):

```powershell
Get-FileHash .\VANTA-Launcher-1.0.1.msi -Algorithm SHA256
certutil -hashfile VANTA-Launcher-1.0.1.msi SHA256
```

Compare the printed value with the line for the same file in `SHA256SUMS.txt`. `Get-FileHash` prints upper-case
letters and `SHA256SUMS.txt` uses lower-case; that difference does not matter.

**macOS** (Terminal, in the folder with the download and `SHA256SUMS.txt`):

```bash
shasum -a 256 vanta-launcher-1.0.1-macos-aarch64-all.jar
# or let shasum compare it with SHA256SUMS.txt (prints "OK"):
grep -F '  vanta-launcher-1.0.1-macos-aarch64-all.jar' SHA256SUMS.txt | shasum -a 256 -c
```

**Linux** (checks every file from `SHA256SUMS.txt` that is in the current folder and prints `OK` for each):

```bash
sha256sum -c --ignore-missing SHA256SUMS.txt
```

**The mods bundle** carries its own `SHA256SUMS` for the two jars inside. After unzipping, run
`sha256sum -c SHA256SUMS` (Linux) or `shasum -a 256 -c SHA256SUMS` (macOS) in the unzipped folder. On Windows run
`Get-FileHash mods\*.jar -Algorithm SHA256 | Format-List Hash, Path` in PowerShell, which prints the full path of
each jar (or `certutil -hashfile mods\vanta-client-1.0.1.jar SHA256` and
`certutil -hashfile mods\fabric-api-0.141.6+1.21.11.jar SHA256` in the Command Prompt), and compare the hash of
**both** jars with the line for the same file in `SHA256SUMS`. `INSTALL.txt` in the bundle lists the same commands.

The value must match character for character (upper or lower case aside). If it does not, delete the file and download
it again; if it still differs, do not use it and [report it](troubleshooting.md#how-to-report-a-problem).

## 3. Install the launcher

Paths A and B start here. For path C skip to [C. Manual installation](#c-manual-installation).

**Windows installer (`.msi` or `.exe`)**

1. Double-click the installer. Because it is not signed, Windows SmartScreen may show **"Windows protected your
   PC"**. Choose **More info → Run anyway** only after the checksum from step 2 matched. If it did not match, do not
   run the file.
2. The installer is a per-user installation: it needs no administrator rights, creates a Start menu entry in the
   **VANTA** group and a desktop shortcut, and lets you choose the folder.
3. Start **VANTA Launcher** from the Start menu.

**Windows portable app (`-windows-portable.zip`)**

Unzip it into a folder you own (not `C:\Program Files`) and start `VANTA Launcher\VANTA Launcher.exe`. SmartScreen
may warn about this file as well; the same rule applies.

**Linux app image (`-linux-x64.tar.gz`)**

```bash
tar -xzf VANTA-Launcher-1.0.1-linux-x64.tar.gz
"./VANTA Launcher/bin/VANTA Launcher"
```

The app image contains its own Java runtime; no system Java is needed for the launcher.

**Launcher jar (`-windows-all.jar`, `-linux-all.jar`, `-macos-aarch64-all.jar`)**

```bash
java -jar vanta-launcher-1.0.1-macos-aarch64-all.jar   # use the file for your system
```

This needs Java 21 (`java -version` must print 21; see [Java 21](java-21.md)). On macOS start the jar from Terminal
as shown; it is not signed or notarized. Adding `--help` prints the
[command line reference](launcher.md#command-line-reference).

## 4. First start

### A. Play with the VANTA Launcher

1. **Java.** The Java card on the Home screen shows the runtime the launcher will use for the game. If it says "not
   found", click **Install Java 21 (Temurin)**; the launcher downloads Eclipse Temurin 21 from Adoptium, verifies the
   SHA-256 published by Adoptium and unpacks it into its own `runtimes/` folder. You can also point it at an
   installed Java in Settings.
2. **Sign in.** This needs a Microsoft client id ([Launcher → Microsoft client id](launcher.md#microsoft-client-id)).
   Without one the Home screen explains that sign-in is not available and offers *Use with Minecraft Launcher*
   (path B) instead. With an id: click **Sign in**, the launcher shows a code and opens `microsoft.com/link`; enter
   the code there and approve. The launcher then verifies that the account owns Minecraft Java Edition and loads your
   profile name. Passwords are never typed into the launcher.
3. **PLAY.** The first click installs everything into a dedicated instance: Minecraft 1.21.11 (client jar,
   libraries and assets from Mojang's servers — the same files the official launcher downloads, several hundred
   megabytes), Fabric Loader 0.19.5 (from Fabric's servers), Fabric API 0.141.6+1.21.11 and the VANTA Client jar
   (from the release manifest). Every file is checked against its published checksum; the launcher checks free disk
   space first and resumes an interrupted installation without downloading verified files again.
4. The status line turns to **Running** and the game starts with the VANTA main menu. Logs stream into the
   **Logs** screen while you play.

Before the first installation the **VANTA Client** card on the Home screen says **Not installed yet** next to the
latest release. It offers no *Update* (there is nothing to update yet) but the ways to install: **Install now** (the
same installation PLAY does, without starting the game) when sign-in is configured, and **Use with Minecraft
Launcher**. The update banner does not offer a client update either until a client is installed.

If the VANTA Client has not been published (no manifest, or an empty download URL in it), the installation stops at
the VANTA Client step with "not published yet" (exit code 7 on the command line); the files downloaded before that
stay verified for the next attempt, and *Use with Minecraft Launcher* stops before it writes anything to the
Minecraft folder. The launcher never invents a download. Developers can install a locally built jar with
`--install --client-jar <path>` or `--install-official-profile --client-jar <path>`.

### B. Play through the official Minecraft Launcher

1. Install the official **Minecraft Launcher**, **start it once** and sign in. On its first start it creates its
   profiles file in the Minecraft folder: `launcher_profiles.json` (Minecraft Launcher from minecraft.net) or
   `launcher_profiles_microsoft_store.json` (Minecraft Launcher from the Microsoft Store or the Xbox app on Windows).
   VANTA adds its profile to each of these files that exists, like the Fabric installer, and never creates one.
2. In the VANTA Launcher click **Use with Minecraft Launcher** on the Home screen. The confirmation lists exactly
   which files will be written; click **Add profile**.
3. VANTA installs Fabric API and the VANTA Client (both SHA-256 verified) into its own game folder, adds the Fabric
   Loader 0.19.5 version files to the Minecraft folder and adds the profile **VANTA 1.21.11** to every profiles file
   from step 1 that exists. Your other profiles and settings are kept, and each file is backed up once next to it as
   `launcher_profiles.json.vanta-backup` or `launcher_profiles_microsoft_store.json.vanta-backup`.
4. Open the Minecraft Launcher (restart it if it was open), choose the profile **VANTA 1.21.11** and press
   **Play**. The Minecraft Launcher downloads Minecraft 1.21.11, its libraries, assets and Java itself and signs you
   in with Microsoft.

The same from the command line: `--install-official-profile` (add `--minecraft-dir <path>` for a Minecraft folder
in a non-standard place). Exactly what is written is listed in
[Launcher → Use with the Minecraft Launcher](launcher.md#use-with-the-minecraft-launcher). If VANTA reports that the
Minecraft Launcher has not been set up yet (neither profiles file exists), or the profile does not show up in the
Minecraft Launcher, see
[Troubleshooting](troubleshooting.md#use-with-minecraft-launcher-says-the-profiles-file-is-missing). CI checks the
contents VANTA writes into both profiles files on Linux. Whether the Minecraft Launcher from the Microsoft Store or the
Xbox app then shows the profile has not been tested on a real Windows PC yet; if it does not, use path C, whose Fabric
installer supports that launcher too.

Worlds, screenshots and VANTA settings of this profile live in the VANTA game folder
(`<data directory>/instances/vanta-1.21.11`), not in `.minecraft`, because the profile's game directory points there.

### C. Manual installation

No VANTA Launcher involved; any launcher that runs Fabric works the same way.

1. Install the official Minecraft Launcher, **start it once** and close it. The Fabric installer adds its profile
   to the profiles file the Minecraft Launcher creates in the Minecraft folder on its first start
   (`launcher_profiles.json`, or `launcher_profiles_microsoft_store.json` for the Minecraft Launcher from the
   Microsoft Store or the Xbox app); without them it stops with "No launcher directory found!" (no Minecraft folder
   yet) or "No launcher profile.json found!".
2. Download the Fabric installer from fabricmc.net, run it (*Client* tab), choose Minecraft **1.21.11** and Loader
   **0.19.5**, keep **Create profile** checked and click *Install*.
3. Download `vanta-client-1.0.1-mods.zip`, verify it (step 2), unzip it and copy **both** jars from its `mods/`
   folder into the `mods` folder of your Minecraft directory (create it if it does not exist):
   - Windows: `%APPDATA%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
4. In the Minecraft Launcher start the profile the Fabric installer created. It is listed as
   **fabric-loader-1.21.11** (its version is `fabric-loader-0.19.5-1.21.11`).

`INSTALL.txt` in the bundle repeats these steps: it starts with step 0, *start the official Minecraft Launcher
once*, says to keep *Create profile* checked in the Fabric installer, shows how to verify both jars on Linux, macOS and
Windows (PowerShell or Command Prompt), and explains that the zoom key C is also vanilla's *Save Hotbar Activator*
([Keybinds → Zoom](keybinds.md#zoom)). For Prism Launcher, MultiMC and other details see
[Fabric → Manual installation](fabric.md#manual-installation-into-an-existing-fabric-profile).

## 5. Where files live

The VANTA Launcher keeps everything in its data directory (override it with the environment variable
`VANTA_LAUNCHER_HOME` or the command line option `--data-dir`):

| System | Data directory |
| --- | --- |
| Windows | `%APPDATA%\VANTA Launcher` |
| macOS | `~/Library/Application Support/VANTA Launcher` |
| Linux | `$XDG_DATA_HOME/vanta-launcher`, or `~/.local/share/vanta-launcher` |

| Inside the data directory | Content |
| --- | --- |
| `instances/vanta-1.21.11/` | the game directory (paths A and B): `mods/` (Fabric API + VANTA), `config/vanta/` (VANTA settings), `saves/`, `resourcepacks/`, `screenshots/`, `logs/latest.log` |
| `libraries/`, `assets/`, `versions/` | Minecraft and Fabric files in the standard layout for path A (reused read-only from the official `.minecraft` when *Share official Minecraft files* is on) |
| `runtimes/` | Java runtimes installed by the launcher |
| `logs/` | `launcher-0.log` (rotating) and `game-<timestamp>.log` |
| `cache/updates/` | downloaded updates awaiting verification/installation |
| `settings.json` | launcher settings |
| `accounts.dat` (+ `key.bin` outside Windows) | encrypted account tokens |

Path B also writes to the official Minecraft folder: `versions/fabric-loader-0.19.5-1.21.11/` (the Fabric Loader
version files) and the profile `vanta-1.21.11` in each profiles file that exists there, `launcher_profiles.json`
and/or `launcher_profiles_microsoft_store.json`, each with a one-time backup next to it
(`launcher_profiles.json.vanta-backup`, `launcher_profiles_microsoft_store.json.vanta-backup`). With path C
everything is in `.minecraft` (`mods/`, `config/vanta/`, …).

VANTA's own configuration lives in the game directory under `config/vanta/`: `settings.json`, `profiles/`,
`hud/layout.json`, `hud/presets/`, `crosshair.json`, `cosmetics.json`, `cosmetics/` (packs), `stats.json`,
`exports/` and `imports/`. See the individual guides for each file.

## Updating

- **Paths A and B.** The VANTA Launcher checks the release manifests at start (Settings → *Check for updates
  automatically*; the manifests come from the built-in releases URL, see
  [Launcher → Releases URL](launcher.md#releases-url)) and shows a banner when a newer launcher exists, or a newer
  client while a client is installed. Client updates are downloaded, verified and swapped into `mods/` of the VANTA
  instance, which is also the game folder of the *VANTA 1.21.11* profile; the last three versions stay available for
  rollback on the Versions screen. Running *Use with Minecraft Launcher* again also installs the newest published
  client.
- **The launcher itself** updates with the file that matches how you installed it, downloaded and SHA-256 verified
  first:
  - installed with the `.msi` or `.exe`: the new `.msi`; the launcher asks, then hands it to Windows;
  - the Windows portable folder: the new `-windows-portable.zip`. The launcher shows the instructions with your
    folders' paths, and *Show in folder* opens the download. Close the launcher, then extract the zip into the folder
    that **contains** your `VANTA Launcher` folder (its parent) and let it replace the existing files: the zip's
    top-level `VANTA Launcher` folder lands on top of the old one. Do not extract it into the `VANTA Launcher` folder
    itself; that only creates a second `VANTA Launcher` folder inside it and leaves the old version in place. If you
    renamed your portable folder, extract the zip somewhere else instead and copy everything inside its
    `VANTA Launcher` folder into your portable folder, replacing the existing files. The launcher never unpacks or runs
    the zip itself;
  - the Linux app image: the new `-linux-x64.tar.gz`, shown in its folder to extract;
  - a launcher jar started with `java -jar`: the new jar for your system (`-windows-all.jar`, `-linux-all.jar` or
    `-macos-aarch64-all.jar`), shown in its folder;
  - Intel Macs and other systems without a launcher build: the update dialog opens the release page.

  This choice by installation type is new in launcher 1.0.1. **Launcher 1.0.0 still picks the update by system
  only**, and it is the 1.0.0 launcher that offers the update to 1.0.1: on Windows it offers
  `VANTA-Launcher-1.0.1.msi` (also in the portable folder and when started as a jar), on Linux x64 the
  `-linux-x64.tar.gz` (also when started as a jar). Installing that `.msi` from a portable folder or a jar gives you
  a second, installed launcher while your portable copy or jar stays at 1.0.0. To keep a portable or jar setup,
  choose *Not now* when the 1.0.0 launcher offers to open the installer (on Linux, ignore the downloaded `.tar.gz`),
  close the launcher and download `VANTA-Launcher-1.0.1-windows-portable.zip` or the jar for your system yourself
  from the release
  [`launcher-v1.0.1`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.0.1). Verify it
  ([step 2](#2-verify-the-checksum)), then replace the portable folder's files as described above or start the new
  jar instead of the old one. From 1.0.1 on, the launcher picks the matching file itself.

  Details: [Launcher → Updates and rollback](launcher.md#updates-and-rollback).
- **Path C.** Download the new mods bundle, verify it and replace both jars in `.minecraft/mods`.

## Uninstalling

- **Windows installer:** *Settings → Apps → Installed apps → VANTA Launcher → Uninstall*. Your worlds, settings and
  the Java runtime stay in the data directory; delete `%APPDATA%\VANTA Launcher` to remove them too (back up
  `instances\vanta-1.21.11\saves` first).
- **Portable app, Linux app image, launcher jar:** delete the files and the data directory.
- **Path B profile:** in the Minecraft Launcher open *Installations*, choose *VANTA 1.21.11* and delete it. The
  folder `versions/fabric-loader-0.19.5-1.21.11` can be deleted when no other profile uses that Fabric version, and
  the `.vanta-backup` files next to the profiles files when you no longer need the backups.
- **Path C:** remove `vanta-client-<version>.jar` (and Fabric API, if no other mod needs it) from `mods/` and, for a
  clean slate, the `config/vanta/` folder.
