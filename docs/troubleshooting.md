---
title: Troubleshooting
description: Fixes for the launcher not finding Java, checksum mismatches, Microsoft sign-in errors and XErr codes, crashes on start, mod conflicts, where the logs are and how to report a problem.
order: 40
category: Help
---

## Before anything else

- VANTA supports **Minecraft 1.21.11 with Fabric Loader 0.19.5 and Java 21** only. Check the Versions screen of the
  launcher or the About screen in game.
- Update your graphics driver.
- Read the first error line, not the last: Minecraft's crash reports and the launcher log both start with the cause.

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
| Sign-in is not configured | — | no Microsoft client id is set. Set *Settings → Microsoft client id* or `VANTA_MS_CLIENT_ID`; see [Launcher → Microsoft client id](launcher.md#microsoft-client-id) |
| The code expired | — | you did not finish on microsoft.com/link in time; click Sign in again |
| This Microsoft account has no Xbox Live account | XErr 2148916233 | create an Xbox profile at xbox.com with the same Microsoft account, then retry |
| Xbox Live is not available in your country or region | XErr 2148916235 | Microsoft does not offer Xbox Live there; nothing a launcher can do |
| The account needs adult verification | XErr 2148916236 / 2148916237 | complete the verification Microsoft asks for in some regions (e.g. South Korea), then retry |
| This is a child account | XErr 2148916238 | the account must be added to a Microsoft family by an adult before it can use Xbox Live |
| The account does not own Minecraft Java Edition | — | sign in with the account that bought the game. PC Game Pass may not be recognised by third-party launchers; use the official launcher's Fabric profile instead ([Fabric](fabric.md#manual-installation-into-an-existing-fabric-profile)) |
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

- **PLAY stays disabled**: the reason is written under the button (*Sign in first*, *No Java 21 found*,
  *Installing…*). Hover for details.
- **"Not published yet"** in the client version card: no VANTA release has been published through the release
  workflow, or the launcher's releases URL is not configured. There is nothing to download; the website's Download
  page shows the same state.
- **The launcher window does not open** (portable jar): run `java -jar vanta-launcher-<version>-all.jar` from a
  terminal to see the error; JavaFX needs a desktop session (not a headless server).
- **Installation stops with insufficient disk space**: free space on the drive that holds the data directory or
  move it with `VANTA_LAUNCHER_HOME`.
- **SmartScreen warns about the installer**: expected for unsigned installers; verify the checksum first, then
  *More info → Run anyway* ([Installation](installation.md#3-install-the-launcher)).

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
