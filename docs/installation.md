---
title: Installation
description: Download the VANTA Launcher, verify the SHA-256 checksum, install it, launch Minecraft 1.21.11 for the first time and find where files are stored.
order: 1
category: Getting started
---

## Before you start

- A **Microsoft account that owns Minecraft Java Edition**. VANTA never bypasses the ownership check.
- **Windows 10 or 11 (64-bit)** for the installer. The portable `-all.jar` runs on Linux and macOS with Java 21.
- **Java 21** — optional: the launcher finds an existing installation or installs Eclipse Temurin 21 for you.
- A graphics card and driver with **OpenGL 3.2** or newer, and a few gigabytes of free disk space.

Details are in [Minecraft requirements](minecraft-requirements.md) and [Java 21](java-21.md).

## 1. Download

Releases are published as GitHub Releases and listed on the website's Download page, which reads the release
manifests in `shared/releases/`. Each release offers:

| File | What it is |
| --- | --- |
| `VANTA-Launcher-<version>.msi` | Windows installer (recommended) |
| `VANTA-Launcher-<version>.exe` | Windows installer, alternative format for systems that block MSI |
| `vanta-launcher-<version>-all.jar` | portable launcher for Windows, Linux and macOS (`java -jar`) |
| `VANTA-Launcher-<version>-linux-x64.tar.gz` | Linux app image with a bundled Java runtime |
| `vanta-client-<version>.jar` | the Fabric mod on its own, for an existing Fabric 0.19.5 profile (see [Fabric](fabric.md)) |
| `SHA256SUMS.txt` | checksums of all of the above |

If the Download page says **"Not published yet — release pending"**, no release has been produced yet by the
release workflow and there is nothing to download. Nobody else can offer you a legitimate VANTA download in the
meantime; treat any other source as untrusted.

## 2. Verify the checksum

Every download has a SHA-256 checksum on the Download page, in `SHA256SUMS.txt` and in the release manifest.
Compare before you run anything (the installers are not code-signed yet, so this is your integrity check):

```powershell
# Windows (PowerShell or cmd)
certutil -hashfile VANTA-Launcher-1.0.0.msi SHA256
```

```bash
# macOS
shasum -a 256 vanta-launcher-1.0.0-all.jar

# Linux (checks every file listed in SHA256SUMS.txt that is in the current directory)
sha256sum -c SHA256SUMS.txt --ignore-missing
```

The printed value must match character for character. If it does not, delete the file and download it again;
if it still differs, do not install it and open an issue.

## 3. Install the launcher

**Windows installer (`.msi` or `.exe`)**

1. Double-click the installer. Because the installer is not signed, Windows SmartScreen may show "Windows protected
   your PC". Choose **More info → Run anyway** only after you verified the checksum in step 2.
2. The installer is a per-user installation: it needs no administrator rights, creates a Start menu entry in the
   **VANTA** group and a desktop shortcut, and lets you choose the folder.
3. Start **VANTA Launcher** from the Start menu.

**Portable jar (all systems)**

```bash
java -jar vanta-launcher-1.0.0-all.jar
```

requires Java 21 on your `PATH`. Adding `--help` prints the [command line reference](launcher.md#command-line-reference).

**Linux app image**

```bash
tar -xzf VANTA-Launcher-1.0.0-linux-x64.tar.gz
"./VANTA Launcher/bin/VANTA Launcher"
```

The app image contains its own Java runtime; no system Java is needed.

## 4. First launch

1. **Java.** The Java card on the Home screen shows the runtime the launcher will use. If it says "not found",
   click **Install Java 21 (Temurin)**; the launcher downloads Eclipse Temurin 21 from Adoptium, verifies the
   SHA-256 published by Adoptium and unpacks it into its own `runtimes/` folder. You can also point it at an
   installed Java in Settings.
2. **Sign in.** Click **Sign in** in the sidebar. The launcher shows a code and opens `microsoft.com/link`; enter the
   code there and approve. The launcher then verifies that the account owns Minecraft Java Edition and loads your
   profile name. Passwords are never typed into the launcher. If the launcher says the Microsoft client id is not
   configured, see [Launcher → Microsoft client id](launcher.md#microsoft-client-id).
3. **PLAY.** The first click installs everything into a dedicated instance: Minecraft 1.21.11 (client jar,
   libraries and assets from Mojang's servers — the same files the official launcher downloads, several hundred
   megabytes), Fabric Loader 0.19.5 (from Fabric's servers), Fabric API 0.141.6+1.21.11 and the VANTA Client jar
   (from the release manifest). Every file is checked against its published checksum; the launcher checks free disk
   space first and resumes an interrupted installation without re-downloading verified files.
4. The status line turns to **Running** and the game starts with the VANTA main menu. Logs stream into the
   **Logs** screen while you play.

If the VANTA Client has not been published yet (empty download URL in the manifest), the launcher says so and
installs everything else; it never invents a download. Developers can install a locally built jar with the command
line option `--install --client-jar <path>`.

## 5. Where files live

The launcher keeps everything in its data directory (override it with the environment variable
`VANTA_LAUNCHER_HOME`):

| System | Data directory |
| --- | --- |
| Windows | `%APPDATA%\VANTA Launcher` |
| macOS | `~/Library/Application Support/VANTA Launcher` |
| Linux | `$XDG_DATA_HOME/vanta-launcher`, or `~/.local/share/vanta-launcher` |

| Inside the data directory | Content |
| --- | --- |
| `instances/vanta-1.21.11/` | the game directory: `mods/` (Fabric API + VANTA), `config/vanta/` (VANTA settings), `saves/`, `resourcepacks/`, `screenshots/`, `logs/latest.log` |
| `libraries/`, `assets/`, `versions/` | Minecraft and Fabric files in the standard layout (shared with the official `.minecraft` when enabled in Settings) |
| `runtimes/` | Java runtimes installed by the launcher |
| `logs/` | `launcher-0.log` (rotating) and `game-<timestamp>.log` |
| `cache/updates/` | downloaded updates awaiting verification/installation |
| `settings.json` | launcher settings |
| `accounts.dat` (+ `key.bin` outside Windows) | encrypted account tokens |

VANTA's own configuration lives in the game directory under `config/vanta/`: `settings.json`, `profiles/`,
`hud/layout.json`, `hud/presets/`, `crosshair.json`, `cosmetics.json`, `cosmetics/` (packs), `stats.json`,
`exports/` and `imports/`. See the individual guides for each file.

## Installing into an existing Fabric setup instead

If you already play 1.21.11 with Fabric (official launcher profile, Prism Launcher, MultiMC …), you do not need the
VANTA Launcher: drop `vanta-client-<version>.jar` and Fabric API into that profile's `mods/` folder. Step-by-step
instructions are in [Fabric → Manual installation](fabric.md#manual-installation-into-an-existing-fabric-profile).

## Updating

The launcher checks the release manifests at start (Settings → *Check for updates automatically*) and shows a banner
when a newer launcher or client exists. Client updates are downloaded, verified and swapped into `mods/`; the last
three versions stay available for rollback on the Versions screen. Launcher updates are downloaded and verified, then
the launcher asks before opening the installer. See [Launcher → Updates and rollback](launcher.md#updates-and-rollback).

## Uninstalling

- Windows: *Settings → Apps → Installed apps → VANTA Launcher → Uninstall*. Your worlds, settings and the Java
  runtime stay in the data directory; delete `%APPDATA%\VANTA Launcher` to remove them too (back up `saves/` first).
- Portable jar / Linux app image: delete the files and the data directory.
- VANTA in an existing Fabric profile: remove `vanta-client-<version>.jar` from `mods/` and, if you want a clean
  slate, the `config/vanta/` folder.
