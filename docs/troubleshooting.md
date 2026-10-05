---
title: Troubleshooting
description: Fixes for "Not published yet", SmartScreen warnings, the right launcher file, disabled PLAY, the Minecraft Launcher profile, Java, checksums, sign-in errors, crashes and mod conflicts.
order: 40
category: Help
---

## Before anything else

- VANTA supports **Minecraft 1.21.11 with Fabric Loader 0.19.5 and Java 21** only. Check the Versions screen of the
  launcher or the About screen in game.
- Update your graphics driver.
- Read the first error line, not the last: Minecraft's crash reports and the launcher log both start with the cause.

## The Download page says "Not published yet"

The website's Download page is built from the release manifests in the repository (`shared/releases/`). A product
shows **"Not published yet — release pending"** and a disabled button while the manifest in the deployed website has
no download URL for it. That happens before the release workflow has published the product, and also in the short
time after a release is published but before its completed manifest has been committed and the website rebuilt.

1. Open [GitHub Releases](https://github.com/LennardOwnTest123006/VANTA-Client/releases). If the release
   (`launcher-v1.0.0` or `client-v1.0.0`) is listed, download the file from its release page and compare it with
   `SHA256SUMS.txt` from the same page ([Installation → Verify the checksum](installation.md#2-verify-the-checksum)).
2. If GitHub Releases does not list it either, the release does not exist yet and there is nothing to download.
   Do not take VANTA files from any other source.

The VANTA Launcher shows the same state in its *VANTA Client* card ("No public release has been published yet." or
"Version … is announced but not downloadable yet."). It reads `client-latest.json` and `launcher-latest.json` from the
releases URL in use: check *Settings → Releases base URL*. It should say *built-in default*; a URL set there or in the
environment variable `VANTA_RELEASES_BASE_URL` may point somewhere without manifests — use **Reset to default** (and
remove the variable). `--check-update` prints the URL in use and what it found there
([Launcher → Releases URL](launcher.md#releases-url)). "Unknown — the releases URL in Settings is not valid" means the
configured value is not an `http(s)` URL; correct it or reset it.

## "Windows protected your PC"

Windows SmartScreen shows this for programs without a code-signing certificate. The VANTA installers
(`VANTA-Launcher-1.0.0.msi`, `.exe`) and the portable app are **not code-signed** yet (a certificate is a paid item
the project does not have), so the warning is expected for the files from GitHub Releases.

1. First compare the file's SHA-256 with `SHA256SUMS.txt` from the release page
   ([Installation → Verify the checksum](installation.md#2-verify-the-checksum)).
2. Only if it matches: click **More info**, check that the file name is the one you downloaded, then **Run anyway**.
3. If it does not match, do not run the file. Delete it, download it again, and if it still differs
   [report it](#how-to-report-a-problem).

Your browser may also warn that the file is not commonly downloaded; the same rule applies. VANTA never asks you to
turn SmartScreen or your antivirus off.

## Linux and macOS: which launcher file?

Each launcher jar contains the JavaFX libraries for one system only, so take the file for yours:

| Your computer | File | Needs |
| --- | --- | --- |
| Linux x64 (`uname -m` prints `x86_64`) | `VANTA-Launcher-1.0.0-linux-x64.tar.gz` (recommended) or `vanta-launcher-1.0.0-linux-all.jar` | nothing for the `.tar.gz`; Java 21 for the jar |
| Mac with Apple Silicon (*About This Mac* shows an Apple M chip; `uname -m` prints `arm64`) | `vanta-launcher-1.0.0-macos-aarch64-all.jar` | Java 21; start it with `java -jar` from Terminal |
| Mac with an Intel processor (`uname -m` prints `x86_64`) | none | use the [manual installation](installation.md#c-manual-installation) with `vanta-client-1.0.0-mods.zip` |
| Linux on ARM (`aarch64`) | none | use the manual installation |

The Windows jar (`-windows-all.jar`) does not start on Linux or macOS, and the Linux jar does not start on Windows or
macOS, even though all of them are `.jar` files. `java -jar <file> --version` prints the JavaFX platform the jar was built
for and the system it is running on. The macOS jar is built and tested from the command line on an Apple Silicon
machine in CI; its window has not been tested there yet, and it is not signed or notarized. If it does not open
for you, use the manual installation and [report it](#how-to-report-a-problem).

## PLAY is disabled or sign-in is not configured

The reason is written under PLAY:

- **Sign-in unavailable: play through the Minecraft Launcher** — the launcher has no Microsoft client id. Sign-in
  needs an application id that Mojang has approved for the Minecraft API, and the published launcher does not include
  one. Play with **Use with Minecraft Launcher** on the Home screen
  ([Launcher → Use with the Minecraft Launcher](launcher.md#use-with-the-minecraft-launcher)), or configure an
  approved id of your own under *Settings → Microsoft client id* / `VANTA_MS_CLIENT_ID`
  ([Launcher → Microsoft client id](launcher.md#microsoft-client-id)). There is no offline workaround.
- **Sign in first** — a client id is configured but no account is signed in.
- **No Java 21 found** — see [the next sections](#the-launcher-cannot-find-java).
- *Installing…*, *Verifying…*, *Running* — wait for the current step, or cancel it.

## "Use with Minecraft Launcher" says the profiles file is missing

*Use with Minecraft Launcher* adds its profile to the profiles files of the official Minecraft Launcher in your
Minecraft folder and never creates one. Like the Fabric installer, it writes to every one that exists:
`launcher_profiles.json` (Minecraft Launcher from minecraft.net) and `launcher_profiles_microsoft_store.json`
(Minecraft Launcher from the Microsoft Store or the Xbox app on Windows). It stops only when neither file is there,
with the message *"The Minecraft Launcher has not been set up on this computer yet: there is no launcher_profiles.json
or launcher_profiles_microsoft_store.json in …"* (in the VANTA Launcher) or *"No launcher_profiles.json or
launcher_profiles_microsoft_store.json in …. Start the Minecraft Launcher once, then try again"* (from
`--install-official-profile`, exit code 3). Nothing was downloaded or changed.

Open the folder named in the message (by default `%APPDATA%\.minecraft` on Windows,
`~/Library/Application Support/minecraft` on macOS, `~/.minecraft` on Linux):

1. **The Minecraft Launcher has not been started with this folder yet.** Install the official Minecraft Launcher
   (from minecraft.net, or on Windows from the Microsoft Store or the Xbox app), start it once, sign in and close it.
   It then creates its profiles file; click *Use with Minecraft Launcher* again.
2. **The Minecraft Launcher is installed and you have played with it, but the folder has neither file.** Your
   Minecraft folder is somewhere else. The VANTA Launcher window always uses the default folder; on the command line
   pass the right one with `--minecraft-dir <path>`.
3. Other errors from this step: *network failure* (exit code 5; Fabric meta, the Fabric Maven or GitHub was not
   reachable — the message names the URL), *integrity check failed* (exit code 4), or a profiles file that is not
   valid JSON. In the last case VANTA changes nothing; restore the broken one from a backup (for example
   `launcher_profiles.json.vanta-backup`, if an earlier run made one).

After a successful run, restart the Minecraft Launcher if it was open and choose the profile **VANTA 1.21.11**. Each
profiles file VANTA changed keeps its original next to it as `launcher_profiles.json.vanta-backup` or
`launcher_profiles_microsoft_store.json.vanta-backup`.

If the profile does not show up in the Minecraft Launcher from the Microsoft Store or the Xbox app, close that
launcher completely and start it again. If it is still missing, install with
[path C](installation.md#c-manual-installation) instead and [report the problem](#how-to-report-a-problem): CI checks
the file VANTA writes for that launcher on Linux, but it has not been tested on a real Windows machine.

## The launcher cannot find Java

Symptoms: the Java card says *not found*, PLAY is disabled with *No Java 21 found*, or `--check-java` lists nothing.

1. Click **Install Java 21 (Temurin)** on the Home screen — the simplest fix. It installs a verified runtime into
   the launcher's own folder without touching your system.
2. If you want to use an existing Java, run `java -version` in a terminal. It must say version `21` and `64-Bit`.
   Java 17 or 8 is not enough; see [Java 21](java-21.md).
3. Point the launcher at it: *Settings → Java path* → select the installation directory or the `java`/`java.exe`
   file. The launcher probes it and shows the version it found.
4. Still nothing? Open `logs/launcher-0.log` in the data directory and search for `JavaDetector`: it lists every
   candidate it probed and why it was rejected (wrong major version, 32-bit, probe timed out). A runtime inside a
   folder that needs administrator rights to read, or an antivirus that blocks `java -version`, shows up here.

## Checksum mismatch

Symptoms: *integrity check failed* during installation or update; `--install` exits with code 4; verifying a download
by hand gives a different SHA-256.

- **During installation** (Minecraft libraries, assets, Fabric): the launcher deletes the file and stops. Run the
  installation again — a transient download error is the usual cause. If the same file fails repeatedly, a proxy or
  "security" product is modifying downloads; try another network.
- **For the VANTA jar or a launcher update**: the file does not match the release manifest. Do not use it. Download
  the release again from GitHub Releases, compare with `SHA256SUMS.txt`, and if the mismatch persists open an issue —
  either the manifest is wrong (we need to know) or something between you and GitHub is tampering.
- **"The release manifest has no SHA-256; refusing to download"**: the manifest is incomplete (not published yet).
  Nothing to fix on your side.
- VANTA never offers to "skip verification".

## Microsoft sign-in errors

| Message | Code | Meaning and fix |
| --- | --- | --- |
| Sign-in is not configured | — | no Microsoft client id is set; the published launcher includes none. Play with *Use with Minecraft Launcher* ([above](#play-is-disabled-or-sign-in-is-not-configured)) or set an approved id under *Settings → Microsoft client id* / `VANTA_MS_CLIENT_ID`; see [Launcher → Microsoft client id](launcher.md#microsoft-client-id) |
| The code expired | — | you did not finish on microsoft.com/link in time; click Sign in again |
| This Microsoft account has no Xbox Live account | XErr 2148916233 | create an Xbox profile at xbox.com with the same Microsoft account, then retry |
| Xbox Live is not available in your country or region | XErr 2148916235 | Microsoft does not offer Xbox Live there; nothing a launcher can do |
| The account needs adult verification | XErr 2148916236 / 2148916237 | complete the verification Microsoft asks for in some regions (e.g. South Korea), then retry |
| This is a child account | XErr 2148916238 | the account must be added to a Microsoft family by an adult before it can use Xbox Live |
| The account does not own Minecraft Java Edition | — | sign in with the account that bought the game. PC Game Pass may not be recognised by third-party launchers; play through the official launcher with *Use with Minecraft Launcher* ([Launcher](launcher.md#use-with-the-minecraft-launcher)) or a Fabric profile ([Fabric](fabric.md#manual-installation-into-an-existing-fabric-profile)) |
| Network error during sign-in | — | the launcher could not reach `login.microsoftonline.com`, `user.auth.xboxlive.com`, `xsts.auth.xboxlive.com` or `api.minecraftservices.com`; check firewall/proxy |

Signing out and in again refreshes all tokens. Tokens are stored encrypted in `accounts.dat`; deleting that file
(and `key.bin` outside Windows) forces a fresh sign-in.

## The game crashes on start

1. **Look at the log**: Launcher → Logs → Game, or `instances/vanta-1.21.11/logs/latest.log`, or the crash report in
   `crash-reports/`. The first `Caused by:` line usually names the culprit.
2. **GPU / OpenGL**: messages containing `GLFW error`, `OpenGL`, `WGL`, `pixel format`, `0x10008` or a crash inside
   `ig*.dll`/`nvoglv64.dll`/`atio6axx.dll` mean the graphics driver. Update it from the GPU vendor (not Windows
   Update), make sure the game runs on the dedicated GPU on laptops (Windows → Graphics settings → add `javaw.exe` →
   High performance), and check that the GPU supports OpenGL 3.2.
3. **`UnsupportedClassVersionError` / `class file version 65`**: Java older than 21 was used. Fix the Java path.
4. **`OutOfMemoryError`** or stuttering: lower *Settings → Memory* if it exceeds what your system has free, or raise
   it if it is below 2 GB; do not exceed 8 GB.
5. **Fabric Loader errors** (`Incompatible mods found`, `requires version ~1.21.11 of minecraft`): a mod in `mods/`
   is for another Minecraft version. Remove it.
6. **Mixin errors** mentioning another mod: see mod conflicts below.
7. **A crash inside VANTA** (stack trace with `dev.vanta`): please report it — see below. As a workaround, delete
   `config/vanta/settings.json` (or the specific `hud/layout.json`, `profiles/`, `crosshair.json` file named in the
   log); VANTA recreates defaults and keeps the broken file as `*.broken-<timestamp>.json` for the report.

## Mod conflicts

VANTA touches the game in few places: it replaces the title screen (optional), draws a HUD element after the chat,
counts attack/use clicks for the CPS widget, and changes the FOV while the zoom key is held.

- **Two main menus / a flash of the vanilla menu**: another mod also replaces the title screen. Turn off
  *Settings → General → Replace the title screen* or remove the other mod.
- **Overlapping HUDs**: disable the duplicate VANTA widgets in the HUD editor or move them.
- **Crash on start after adding a mod**: remove mods one at a time to find the pair; include both names in the
  report. Keep Fabric API — it is required.
- Performance mods (Sodium, Lithium, Iris, …) are not tested with VANTA. VANTA changes vanilla options only, so they
  should coexist, but please remove them before reporting rendering issues.

## Launcher problems

- **PLAY stays disabled**: the reason is written under the button; see
  [PLAY is disabled](#play-is-disabled-or-sign-in-is-not-configured).
- **"No public release has been published yet"** in the VANTA Client card: see
  [The Download page says "Not published yet"](#the-download-page-says-not-published-yet); the launcher reads the
  same release manifests.
- **The launcher window does not open** (launcher jar): run `java -jar vanta-launcher-1.0.0-<system>-all.jar` from a
  terminal to see the error. Make sure the jar is the one for your system
  ([which file](#linux-and-macos-which-launcher-file)) and that `java -version` prints 21; JavaFX also needs a
  desktop session (not a headless server).
- **Network failure (exit code 5)**: the message names the step and the URL that failed. Check the internet
  connection, firewall and proxy; an HTTPS proxy that refuses the connection is reported the same way.
- **Installation stops with insufficient disk space**: free space on the drive that holds the data directory or
  move it with `VANTA_LAUNCHER_HOME`.
- **SmartScreen warns about the installer**: see ["Windows protected your PC"](#windows-protected-your-pc).

## Where the logs are

| What | Location |
| --- | --- |
| Launcher log | `<data directory>/logs/launcher-0.log` (older: `launcher-1.log` … `launcher-4.log`) |
| Game output captured by the launcher | `<data directory>/logs/game-<timestamp>.log` |
| Minecraft's own log | `<data directory>/instances/vanta-1.21.11/logs/latest.log` (or `.minecraft/logs/latest.log` for a manual install) |
| Crash reports | `instances/vanta-1.21.11/crash-reports/` |
| VANTA configuration | `instances/vanta-1.21.11/config/vanta/` |

Data directory: `%APPDATA%\VANTA Launcher` (Windows), `~/Library/Application Support/VANTA Launcher` (macOS),
`~/.local/share/vanta-launcher` (Linux). *Logs → Open logs folder* takes you there. Access tokens never appear in any
of these files; your username and world names do, so remove them before posting if you prefer.

## How to report a problem

Open an issue at
[github.com/LennardOwnTest123006/VANTA-Client/issues](https://github.com/LennardOwnTest123006/VANTA-Client/issues)
using the *Bug report* template and include:

- VANTA Client and Launcher versions, Minecraft version (must be 1.21.11), Java version (`java -version`), OS;
- what you did, what you expected, what happened;
- the relevant log (`latest.log` and/or `launcher-0.log`) and the crash report if there is one;
- your other mods.

Security problems (anything about downloads, tokens, extraction or sign-in) go through the private channel described
in the repository's `SECURITY.md`, not a public issue. Support channels beyond GitHub are configured by the project
maintainers and shown on the Support page of the website when available.
