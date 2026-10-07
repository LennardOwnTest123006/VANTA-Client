---
title: Troubleshooting
description: Fixes for "Not published yet", SmartScreen, a launcher that does not start, the Minecraft Launcher profile, Java, checksums, sign-in, crashes, 30 FPS after a 1.1.0 preset, mods and shaders.
order: 40
category: Help
---

## Before anything else

- VANTA supports **Minecraft 1.21.11 with Fabric Loader 0.19.5 and Java 21** only. Check the Versions screen of the
  launcher or the About screen in game.
- Update your graphics driver.
- Read the first error line, not the last: Minecraft's crash reports and the launcher log both start with the cause.

## The Download page says "Not published yet"

The website's Download page is built from the release manifests in the repository (`shared/releases/`). A new
version's manifest is committed before the release workflow publishes its files, with no download URLs yet. Until the
workflow has published the files and the completed manifest is committed and the website rebuilt, the Download page
keeps offering the **newest published release** of that product and adds a note that the new version is not published
yet; the changelog page says the same next to that version. Only a product that has no published release at all shows
**"Not published yet — release pending"** with a disabled button. The page never links to a file that does not exist.

1. Open [GitHub Releases](https://github.com/LennardOwnTest123006/VANTA-Client/releases). If the release you want
   (for example `launcher-v1.2.0` or `client-v1.2.0`) is listed, the website has not been rebuilt yet: download the
   file from its release page and compare it with `SHA256SUMS.txt` from the same page
   ([Installation → Verify the checksum](installation.md#2-verify-the-checksum)).
2. If GitHub Releases does not list it either, that version has not been published yet and there is nothing to
   download for it; use the newest version that is listed. Do not take VANTA files from any other source.

The VANTA Launcher shows the same state in its *VANTA Client* card ("No public release has been published yet." or
"Version … is announced but not downloadable yet."). It reads `client-latest.json` and `launcher-latest.json` from the
releases URL in use: check *Settings → Releases base URL*. It should say *built-in default*; a URL set there or in the
environment variable `VANTA_RELEASES_BASE_URL` may point somewhere without manifests — use **Reset to default** (and
remove the variable). `--check-update` prints the URL in use and what it found there
([Launcher → Releases URL](launcher.md#releases-url)). "Unknown — the releases URL in Settings is not valid" means the
configured value is not an `http(s)` URL; correct it or reset it.

## "Windows protected your PC"

Windows SmartScreen shows this for programs without a code-signing certificate. The VANTA installers
(`VANTA-Launcher-1.2.0.msi`, `.exe`) and the portable app are **not code-signed** yet (a certificate is a paid item
the project does not have), so the warning is expected for the files from GitHub Releases.

1. First compare the file's SHA-256 with `SHA256SUMS.txt` from the release page
   ([Installation → Verify the checksum](installation.md#2-verify-the-checksum)).
2. Only if it matches: click **More info**, check that the file name is the one you downloaded, then **Run anyway**.
3. If it does not match, do not run the file. Delete it, download it again, and if it still differs
   [report it](#how-to-report-a-problem).

Your browser may also warn that the file is not commonly downloaded; the same rule applies. VANTA never asks you to
turn SmartScreen or your antivirus off.

If Windows blocks the file without a *Run anyway* option, it is not SmartScreen but Smart App Control or a policy; see
the next section.

## The launcher does not start or Windows blocks it

### Smart App Control (Windows 11)

Smart App Control is a Windows 11 security feature (*Windows Security → App & browser control → Smart App Control*).
When it is **on**, it blocks apps that are not signed with a trusted certificate and that Microsoft's cloud service
does not consider safe, and it offers **no "Run anyway"**. What this means for VANTA, as far as we know:

- The `.msi`, the `.exe` installer and the program `VANTA Launcher.exe` (installed, and in the portable zip) are not
  signed, so Smart App Control can block all of them. The portable zip is therefore no way around it.
- The launcher jars (`vanta-launcher-1.2.0-windows-all.jar`) are started by an installed Java 21 (`java` or `javaw`
  from Eclipse Temurin or another vendor), not by a VANTA program, so they may start where the other files are
  blocked. We have **not tested** this on a PC with Smart App Control turned on; the jar also unpacks JavaFX libraries
  when it starts, and we cannot say whether Smart App Control lets them load. Install
  [Java 21](java-21.md#install-options), then run `java -jar vanta-launcher-1.2.0-windows-all.jar` in PowerShell
  in the folder with the download (verify its SHA-256 first).
- The [manual installation](installation.md#c-manual-installation) runs no VANTA program at all: the VANTA Client
  and Fabric API are mods that the game loads. Its Fabric installer is a separate program from fabricmc.net; with
  Java 21 installed you can use the universal `.jar` installer on Windows too
  (`java -jar fabric-installer-<version>.jar`).
- Turning Smart App Control off is a decision about your PC's security that VANTA does not ask you to make. Read
  Microsoft's documentation before you change it.

If you try one of these ways on a PC with Smart App Control on, please [report](#how-to-report-a-problem) whether it
worked; it helps others.

### The `.msi` is blocked or will not install

- **"Windows protected your PC"**: SmartScreen; see [above](#windows-protected-your-pc).
- **Blocked by your organisation** (a work or school PC with a policy against installers): use
  `VANTA-Launcher-1.2.0.exe` only if your policy allows it; otherwise ask your administrator. Do not try to get
  around a policy.
- **Other installer errors**: try `VANTA-Launcher-1.2.0.exe` (the same installer as an `.exe`), or the portable zip
  `VANTA-Launcher-1.2.0-windows-portable.zip`, which needs no installation: unzip it into a folder you own and start
  `VANTA Launcher\VANTA Launcher.exe`.

### The window does not open

From launcher 1.1.0 on, a launcher that cannot start shows a dialog with the reason and writes
`startup-error.txt` into the `logs` folder of its data directory, then exits with code 1
([Launcher → Start-up errors](launcher.md#start-up-errors)):

| System | File |
| --- | --- |
| Windows | `%APPDATA%\VANTA Launcher\logs\startup-error.txt` (type this path into the address bar of the File Explorer) |
| macOS | `~/Library/Application Support/VANTA Launcher/logs/startup-error.txt` |
| Linux | `~/.local/share/vanta-launcher/logs/startup-error.txt` |

When the data directory cannot be written, the file is in the system's temporary folder instead. The first lines say
what failed; the rest lists the launcher, Java and system details.

1. Read the message in the dialog or the first lines of `startup-error.txt`.
2. A launcher jar for another system says which file to download
   ([which launcher file](#linux-and-macos-which-launcher-file)).
3. With a launcher jar, check that `java -version` prints 21. Run the jar from a terminal
   (`java -jar vanta-launcher-<version>-<system>-all.jar`) to see the output.
4. On Windows, if the installed launcher or the portable app does not start, try the other one, or the Windows jar with
   an installed Java 21; if Smart App Control is on, see [above](#smart-app-control-windows-11).
5. [Report it](#how-to-report-a-problem) with `startup-error.txt` and `launcher-0.log` from the same `logs` folder.

Launcher 1.0.x closed without any message when its window could not start; update to 1.1.0 or newer so you get the message
and the file. CI installs the `.msi`, unpacks the portable zip and runs the Windows jar on a Windows runner and
requires each of them to open the launcher window; that does not cover every Windows setup, so reports help.

## Linux and macOS: which launcher file?

Each launcher jar contains the JavaFX libraries for one system only, so take the file for yours:

| Your computer | File | Needs |
| --- | --- | --- |
| Linux x64 (`uname -m` prints `x86_64`) | `VANTA-Launcher-1.2.0-linux-x64.tar.gz` (recommended) or `vanta-launcher-1.2.0-linux-all.jar` | nothing for the `.tar.gz`; Java 21 for the jar |
| Mac with Apple Silicon (*About This Mac* shows an Apple M chip; `uname -m` prints `arm64`) | `vanta-launcher-1.2.0-macos-aarch64-all.jar` | Java 21; start it with `java -jar` from Terminal |
| Mac with an Intel processor (`uname -m` prints `x86_64`) | none | use the [manual installation](installation.md#c-manual-installation) with `vanta-client-1.2.0-mods.zip` |
| Linux on ARM (`aarch64`) | none | use the manual installation |

The Windows jar (`-windows-all.jar`) does not start its window on Linux or macOS, and the Linux jar does not start it
on Windows or macOS, even though all of them are `.jar` files. Started on the wrong system, a jar says so before it
tries to open a window: *"This jar is for Windows x64, but it was started by a Java runtime for Linux x64. …"* followed
by the file to download instead (for example `vanta-launcher-1.2.0-linux-all.jar` or
`VANTA-Launcher-1.2.0-linux-x64.tar.gz`) and the releases page. The check looks at the Java runtime, not at the
computer: an x64 (Intel) Java on an Apple Silicon Mac runs under Rosetta 2 and is told to install an arm64 (aarch64)
Java 21 and start `vanta-launcher-1.2.0-macos-aarch64-all.jar` with it, and a 32-bit Java is told to use a 64-bit
Java 21 or a download that brings its own Java runtime (the `.msi`, the portable app or the Linux `.tar.gz`). The jar
prints the message, also shows it in a message window when a display is available (a double-clicked jar has no
console), and exits with code 1. Command line options such as `--help`, `--version` and `--install-official-profile`
need no window and work with every jar. `java -jar <file> --version` prints the JavaFX platform the jar was built for
and the system it is running on. The macOS jar is built and tested from the command line on an Apple Silicon machine
in CI; its window has not been tested there yet, and it is not signed or notarized. If it does not open for you, use
the manual installation and [report it](#how-to-report-a-problem).

## Windows on ARM: which launcher file?

There is no native arm64 build of the VANTA Launcher for Windows, but Windows 11 on ARM runs x64 programs under
emulation. Use `VANTA-Launcher-1.2.0.msi` or the portable app `VANTA-Launcher-1.2.0-windows-portable.zip`: both are
x64 and bring their own x64 Java runtime. Alternatively install an x64 Java 21 and start
`vanta-launcher-1.2.0-windows-all.jar` with it. The project has not tested the launcher on a Windows on ARM device;
if it does not work for you, use the [manual installation](installation.md#c-manual-installation) and
[report it](#how-to-report-a-problem).

Started with an arm64 Java, the Windows jar cannot open its window. From launcher 1.0.2 on it recommends the downloads
above instead (launcher 1.0.1 said that there is no download for Windows on ARM and pointed only to building from
source or `--install-official-profile`) and exits with code 1. Windows 10
on ARM cannot run x64 programs; there the jar's command line still works with an arm64 Java:
`java -jar vanta-launcher-1.2.0-windows-all.jar --install-official-profile` sets VANTA up for the official Minecraft
Launcher ([Launcher → Use with the Minecraft Launcher](launcher.md#use-with-the-minecraft-launcher)).

## PLAY is disabled or sign-in is not configured

The published launcher has no Microsoft client id: sign-in needs an application id that Mojang has approved for the
Minecraft API, and the project does not have one. From launcher 1.1.0 on, the main button is therefore **PLAY via
Minecraft Launcher**, which sets up the profile *VANTA 1.21.11* in the official Minecraft Launcher and opens it
([Launcher → Use with the Minecraft Launcher](launcher.md#use-with-the-minecraft-launcher)). Launcher 1.0.x showed a
disabled PLAY with **Sign-in unavailable: play through the Minecraft Launcher** instead; there, use **Use with
Minecraft Launcher** on the Home screen, or update the launcher. You can also configure an approved id of your own
under *Settings → Microsoft client id* / `VANTA_MS_CLIENT_ID`
([Launcher → Microsoft client id](launcher.md#microsoft-client-id)). There is no offline workaround.

When PLAY itself is shown (a client id is configured or an account is stored), the reason it is disabled is written
under it:

- **Sign in first** — a client id is configured but no account is signed in.
- **No Java 21 found** — see [the next sections](#the-launcher-cannot-find-java).
- *Installing…*, *Verifying…*, *Running* — wait for the current step, or cancel it.

## "Use with Minecraft Launcher" says the profiles file is missing

*Use with Minecraft Launcher* adds its profile to the profiles files of the official Minecraft Launcher in your
Minecraft folder and never creates one. It writes to every one that exists: `launcher_profiles.json` (Minecraft
Launcher from minecraft.net) and `launcher_profiles_microsoft_store.json` (Minecraft Launcher from the Microsoft Store
or the Xbox app on Windows). That differs from the Fabric installer, which asks which launcher to use when both files
exist and writes only that one. It stops only when neither file is there,
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

After a successful run, choose the profile **VANTA 1.21.11** in the Minecraft Launcher. Each profiles file VANTA
changed keeps its original next to it as `launcher_profiles.json.vanta-backup` or
`launcher_profiles_microsoft_store.json.vanta-backup`. If the profile is not there, see the next section.

## The profile VANTA 1.21.11 does not show up in the Minecraft Launcher

The Minecraft Launcher reads its profiles only when it starts. If it was running while VANTA added the profile, it
does not show the profile until it has been **closed completely** and started again. Closing its window is often not
enough:

1. **Windows**: click the arrow next to the clock (the system tray), right-click the Minecraft Launcher icon and
   choose *Exit*. If there is no icon, open the Task Manager (Ctrl+Shift+Esc) and end "Minecraft Launcher" if it is
   listed. **macOS**: quit it with Command-Q or *Quit* on its Dock icon. **Linux**: close it and check that no
   `minecraft-launcher` process is left.
2. Start the Minecraft Launcher again. *PLAY via Minecraft Launcher* in VANTA opens it for you, and right after a
   setup the Home screen also offers **Open Minecraft Launcher**.
3. Choose **VANTA 1.21.11** in the list next to the Play button (or under *Installations*).

From launcher 1.1.0 on, *PLAY via Minecraft Launcher* and *Use with Minecraft Launcher* check for a running Minecraft
Launcher before they write anything and ask you to close it first (*Close the Minecraft Launcher first*, with **Check
again** and **Continue anyway**). If you chose *Continue anyway*, the profile appears after the next start of the
Minecraft Launcher. With launcher 1.0.x there was no such check, which is why the profile sometimes appeared only
after a restart of the PC; closing the Minecraft Launcher completely as above has the same effect.

Still missing?

- **Two Minecraft Launchers**: VANTA writes the profile into every profiles file that exists
  (`launcher_profiles.json` for the launcher from minecraft.net, `launcher_profiles_microsoft_store.json` for the one
  from the Microsoft Store or the Xbox app). Check that you opened the launcher you play with.
- **Another Minecraft folder**: the window always uses the default folder (`%APPDATA%\.minecraft` on Windows). If your
  Minecraft Launcher uses another one, run `--install-official-profile --minecraft-dir <path>` on the command line.
- **Microsoft Store / Xbox app launcher**: CI checks the file VANTA writes for that launcher on Linux, but whether that
  launcher shows the profile has not been tested on a real Windows PC. If it does not, install with
  [path C](installation.md#c-manual-installation) instead and [report the problem](#how-to-report-a-problem).

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
   is for another Minecraft version, or two mods do not fit together. Remove it, or switch it off on the launcher's
   Mods page; see [Mods & Shaders problems](#mods--shaders-problems) when it came from Modrinth.
6. **Mixin errors** mentioning another mod: see mod conflicts below.
7. **A crash inside VANTA** (stack trace with `dev.vanta`): please report it — see below. As a workaround, delete
   `config/vanta/settings.json` (or the specific `hud/layout.json`, `profiles/`, `crosshair.json` file named in the
   log); VANTA recreates defaults and keeps the broken file as `*.broken-<timestamp>.json` for the report.

## Mod conflicts

VANTA touches the game in few places: it replaces the title screen (optional), draws a HUD element after the chat,
counts attack/use clicks for the CPS widget, and changes the FOV while the zoom key is held.

- **Two main menus / a flash of the vanilla menu**: another mod also replaces the title screen. Turn off
  *Settings → General → VANTA main menu* or remove the other mod.
- **Overlapping HUDs**: disable the duplicate VANTA widgets in the HUD editor or move them.
- **The keybind manager reports a conflict on C, or C + a number key saves your hotbar in Creative**: VANTA's zoom key
  (C) shares its default key with vanilla's *Save Hotbar Activator* (Creative mode only). That is expected; rebind
  either one in *Options → Controls → Key Binds* or in the VANTA keybind manager ([Keybinds → Zoom](keybinds.md#zoom)).
- **The keybind manager reports many conflicts right after installation** (for example on A, S, D or the middle mouse
  button): apart from C, the conflicts you see with only VANTA and Fabric API installed are between vanilla bindings
  that share a default key. They behave as in the game without VANTA and can be ignored; the only conflict VANTA adds
  is the one on C ([Keybinds → Conflict detection](keybinds.md#conflict-detection)).
- **Crash on start after adding a mod**: remove mods one at a time to find the pair; include both names in the
  report. Keep Fabric API — it is required.
- **Performance pack mods** (Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling, Iris): CI runs VANTA's
  game test with the newest 1.21.11 versions of these six loaded. Their later versions and other mods are not tested
  with VANTA. Before reporting a rendering problem, disable them (and turn shaders off) and check whether it remains.

## Low FPS or exactly 30 FPS after choosing a preset or profile in VANTA 1.1.0

In VANTA Client 1.1.0 the Performance Center's **LOW** preset wrote a **60 FPS** cap and **BALANCED** a **120 FPS** cap
into Minecraft's *Max framerate* option, and the built-in profiles carried those caps along: the *Performance*
profile (LOW) a **60 FPS** cap, *Default* (BALANCED) a **120 FPS** cap. Someone who picked *Low* or the
*Performance* profile to get *more* frames was capped instead, and with a graphics driver that forces VSync at 60 Hz
on top of Minecraft's own limiter the result could be exactly **30 FPS**. The VANTA HUD was not the cause: the CI game
test measures its cost in the real game and fails when the HUD averages more than 4 ms per frame
([Performance Center → Honest limits](performance.md#honest-limits)).

**Fixed in client 1.2.0**: presets and profiles never write a frame-rate cap any more; the limit lives only in the
frame-rate limit chooser, in *Boost FPS* and in a profile's own frame-rate choice
([Performance Center → Presets](performance.md#presets)). The fix does not undo a cap that 1.1.0 already wrote into
`options.txt`, so after updating:

1. Open the Performance Center (*Settings → Performance → Open Performance Center*) and choose **Unlimited** in the
   frame-rate limit chooser, or press **Boost FPS** (Max FPS preset, no cap, VSync off, plain menu, and the
   Performance pack when it is missing). The same chooser is the *Frame rate limit* row in *Settings → Performance*,
   which from 1.2.0 on really applies to the vanilla options.
2. Still capped? Check *Options → Video Settings*: *Max Framerate* should read *Unlimited* and *VSync* *OFF* (or the
   values you want). A frame rate stuck at your monitor's refresh rate, or at half of it, points to VSync forced in the
   graphics driver's control panel (NVIDIA Control Panel, AMD Software, Intel Graphics Command Center); set it to
   *Use the 3D application setting* for Minecraft.
3. With 1.1.0 and no update yet: choose *Unlimited* in the chooser after every preset or profile change; the cap only
   returns when a preset or profile is applied again.

How much faster the game runs without the cap depends on your computer; VANTA promises no number.

## Buttons that cannot be clicked in a small window

In VANTA Client 1.1.0 and earlier, several buttons were drawn but did nothing when the game window was small
(854 x 480 at GUI scale 2, the 320 x 240 minimum, or large text): *Install*, *View on Modrinth*, *Disable* and
*Remove* on the Mods & Shaders detail panel, *Quit Game* and the quick-access row of the main menu at 640 x 360,
*Vanilla options* in Settings and the HUD editor's grid and snap switches. Their panel had run out of room, so the
click never reached them; in a maximised window everything worked. **Fixed in client 1.2.0**: the detail text scrolls
above a footer that keeps the actions on screen, the main menu, the Settings footer and the HUD editor toolbar
rearrange themselves to fit, and the interface never shrinks below Minecraft's 320 x 240. With 1.1.0, maximise the
window or lower the GUI scale as a workaround.

## The vanilla title screen appears instead of the VANTA main menu

In VANTA Client 1.0.x, *Singleplayer* without any world opens *Create New World*, and leaving that screen with
*Cancel* or Escape showed the vanilla title screen. **This is fixed in client 1.1.0**: every way back to the title
screen while no world is loaded ends on the VANTA main menu. Update the client (the launcher does it with PLAY,
*PLAY via Minecraft Launcher* or the update banner; for a manual installation replace the jar).

If you still see the vanilla title screen with 1.1.0 or newer:

- *Settings → General → VANTA main menu* may be off; turn it on.
- Another mod may replace the title screen too ([Mod conflicts](#mod-conflicts)).
- The game may have been started with the JVM option `-Dvanta.forceVanillaMenu=true` (meant for diagnostics); remove
  it from the profile's JVM arguments.

## Mods & Shaders problems

Problems with mods, shader packs and resource packs from [Mods & Shaders](mods-and-shaders.md) or the launcher's Mods
page:

- **"Modrinth could not be reached"** or **"Modrinth is busy right now"**: check the internet connection, firewall and
  proxy (`api.modrinth.com` and `cdn.modrinth.com` must be reachable) and try again later. Your installed mods are not
  affected. The launcher installs the game without the Performance pack in that case and says so.
- **"has no version for Minecraft 1.21.11 yet and was skipped"**: the project's authors have not published a build for
  1.21.11. VANTA installs nothing for that project.
- **"was not installed: it is incompatible with …"**: Modrinth marks the project as incompatible with something you
  have. Remove one of the two if you need the other.
- **"A download did not match its SHA-512 checksum and was deleted"**: try again; if it happens again, something
  between you and Modrinth changes downloads (a proxy or a "security" product). VANTA never installs such a file.
- **"A file with the same name is already there and was left untouched"**: a file with that name is already in the
  folder but differs from Modrinth's. VANTA does not overwrite it; remove or rename it yourself if you want VANTA's.
- **The game crashes or does not start after you installed a mod**: on the launcher's Mods page switch the newest mod
  off (or remove it) and start again. Without the launcher, rename the file in `mods/` from `.jar` to `.jar.disabled`
  or move it out of the folder. Then [report the crash](#how-to-report-a-problem) to the mod's authors, or to VANTA
  if the stack trace names `dev.vanta`.
- **A new mod does not show up in the game**: mods load only when the game starts; use **Restart game** or **Quit
  game** on the *Restart required* banner ([Mods & Shaders → Restarting](mods-and-shaders.md#restarting-the-game)).
- **A pack mod you removed is back**: while *Settings → Game → Install the performance pack* is on, the launcher
  installs removed pack mods again with the next PLAY. Disable the mod instead, or turn the setting off.
- **Shader packs are missing or do nothing**: they need Iris, which is part of the Performance pack, and Iris needs a
  restart after it was installed. Then choose the pack with *Open shader settings*, the **O** key or *Options → Video
  Settings → Shader Packs*.
- **The game is slow or looks wrong with shaders**: turn shaders off in the Iris shader pack screen, then try a
  lighter pack ([Mods & Shaders → Shaders with Iris](mods-and-shaders.md#shaders-with-iris)).
- **A resource pack does nothing**: installed resource packs are not turned on automatically. Enable them in the
  resource pack screen (*Open resource packs*).

## Launcher problems

- **PLAY stays disabled** (launcher 1.0.x) or the main button says *PLAY via Minecraft Launcher*: see
  [PLAY is disabled](#play-is-disabled-or-sign-in-is-not-configured).
- **The launcher does not start at all**, or Windows blocks it: see
  [The launcher does not start or Windows blocks it](#the-launcher-does-not-start-or-windows-blocks-it).
- **"Close the Minecraft Launcher first"**: the official Minecraft Launcher is running; see
  [The profile VANTA 1.21.11 does not show up](#the-profile-vanta-12111-does-not-show-up-in-the-minecraft-launcher).
- **"No Minecraft Launcher was found on this computer"** after *Open Minecraft Launcher*: VANTA looked for
  `MinecraftLauncher.exe` in `Program Files (x86)\Minecraft Launcher` and the Microsoft Store / Xbox app version on
  Windows, the Minecraft app on macOS and `minecraft-launcher` on the `PATH` on Linux. Install the Minecraft Launcher
  from minecraft.net, or start yours yourself ([Launcher → Opening the Minecraft Launcher](launcher.md#opening-the-minecraft-launcher)).
- **"No public release has been published yet"** in the VANTA Client card: see
  [The Download page says "Not published yet"](#the-download-page-says-not-published-yet); the launcher reads the
  same release manifests.
- **The VANTA Client card says "Not installed yet" and offers no *Update***: expected on a fresh launcher. There is
  nothing to update until a client is installed; use **Install now** (when sign-in is configured), PLAY, **PLAY via
  Minecraft Launcher** or **Use with Minecraft Launcher**. A client installed by *Use with Minecraft Launcher* counts as installed and gets updates
  ([Launcher → Home](launcher.md#home)).
- **The launcher window does not open** (launcher jar): from launcher 1.1.0 on a dialog shows the reason and
  `logs/startup-error.txt` keeps it ([details](#the-window-does-not-open)). Run
  `java -jar vanta-launcher-<version>-<system>-all.jar` from a terminal to see the message as well. A jar for another system names the right file and exits with code 1
  ([which file](#linux-and-macos-which-launcher-file)); otherwise check that `java -version` prints 21. JavaFX also
  needs a desktop session (not a headless server); without one the launcher says that its user interface could not
  start and exits with code 1. The command line (`--help`) works without a window.
- **A launcher update was downloaded but nothing was installed**: only the Windows installer is opened (after you
  confirm). Every other update file is verified and shown in its folder: close the launcher and extract the portable
  `.zip` into the folder that contains your `VANTA Launcher` folder (its parent, not the `VANTA Launcher` folder
  itself), replacing the existing files; extract the Linux `.tar.gz`; or start the new jar with `java -jar`
  ([Installation → Updating](installation.md#updating)).
- **After a portable update there is a `VANTA Launcher` folder inside your `VANTA Launcher` folder and the version did
  not change**: the zip was extracted into the portable folder instead of its parent. Close the launcher, delete the
  inner `VANTA Launcher` folder and extract the zip again into the folder that contains your portable folder.
- **Launcher 1.0.0 offers `VANTA-Launcher-1.2.0.msi` although you use the portable folder or a jar** (or the
  `.tar.gz` for a Linux jar): launcher 1.0.0 picks the update by system only. Choose *Not now* and download the
  portable zip or the jar for your system from the release `launcher-v1.2.0` yourself; from 1.0.1 on the launcher
  picks the matching file ([Installation → Updating](installation.md#updating)). If you already installed the `.msi`,
  you have an installed launcher next to the portable copy or jar; both use the same data directory, so you can keep
  the installed one and delete the portable folder or jar.
- **The downloaded update is called `1.2.0-vanta-launcher-1.2.0-linux-all.jar`** (or `1.2.0-` followed by another
  release file name) and `sha256sum -c --ignore-missing SHA256SUMS.txt` does not find it: launcher 1.0.0 and 1.0.1
  save an update as `cache/updates/<version>-<file name>`. It is the verified release file under a different name; it
  starts as it is from that folder (*Show in folder* opens it). Rename it to the release name (here
  `vanta-launcher-1.2.0-linux-all.jar`) before you check it yourself. From launcher 1.0.2 on, updates are saved as
  `cache/updates/<version>/<file name>` with the release name.
- **"Verify files" is missing on the Home screen**: expected while nothing is installed, and after setting up only
  *Use with Minecraft Launcher*. From launcher 1.0.2 on, *Verify files* works only on an existing installation and is
  shown only then: it checks the installed files and, as before, also replaces an older VANTA Client with the latest
  release (its notification says so). It never starts a first installation (before 1.0.2 it started a full install
  of Minecraft, Fabric and the VANTA Client on a fresh launcher). Install with PLAY or *Install now* (both need
  Microsoft sign-in) or use *PLAY via Minecraft Launcher* / *Use with Minecraft Launcher*.
- **A link does not open your browser** (Website, Support, *How to configure*, release notes): from launcher 1.0.2 on,
  the address is shown in a notification whenever the launcher cannot confirm that a browser was started. When no
  opener accepted the link it is also copied to the clipboard; paste it into your browser. When JavaFX accepted it
  (JavaFX cannot tell whether a browser opened), the clipboard is left alone, so a sign-in code you just copied stays
  there; copy the address from the notification. Older versions could silently do nothing; open
  the address yourself ([Launcher → About and links](launcher.md#about-and-links)).
- **Network failure (exit code 5)**: the message names the step and the URL that failed. Check the internet
  connection, firewall and proxy; an HTTPS proxy that refuses the connection is reported the same way.
- **Installation stops with insufficient disk space**: free space on the drive that holds the data directory or
  move it with `VANTA_LAUNCHER_HOME`.
- **SmartScreen warns about the installer**: see ["Windows protected your PC"](#windows-protected-your-pc).

## Where the logs are

| What | Location |
| --- | --- |
| Launcher log | `<data directory>/logs/launcher-0.log` (older: `launcher-1.log` … `launcher-4.log`) |
| Failed start of the launcher (from 1.1.0 on) | `<data directory>/logs/startup-error.txt` (the one before it: `startup-error.previous.txt`) |
| Game output captured by the launcher | `<data directory>/logs/game-<timestamp>.log` |
| Minecraft's own log | `<data directory>/instances/vanta-1.21.11/logs/latest.log` (or `.minecraft/logs/latest.log` for a manual install) |
| Crash reports | `instances/vanta-1.21.11/crash-reports/` |
| VANTA configuration | `instances/vanta-1.21.11/config/vanta/` |
| What was installed from Modrinth | `instances/vanta-1.21.11/config/vanta/modrinth.json` |

Data directory: `%APPDATA%\VANTA Launcher` (Windows), `~/Library/Application Support/VANTA Launcher` (macOS),
`~/.local/share/vanta-launcher` (Linux). *Logs → Open logs folder* takes you there. Access tokens never appear in any
of these files; your username and world names do, so remove them before posting if you prefer.

## How to report a problem

Open an issue at
[github.com/LennardOwnTest123006/VANTA-Client/issues](https://github.com/LennardOwnTest123006/VANTA-Client/issues)
using the *Bug report* template and include:

- VANTA Client and Launcher versions, Minecraft version (must be 1.21.11), Java version (`java -version`), OS;
- what you did, what you expected, what happened;
- the relevant log (`latest.log` and/or `launcher-0.log`), `startup-error.txt` when the launcher did not start, and
  the crash report if there is one;
- your other mods, including those from the Performance pack or Mods & Shaders (the list is in `modrinth.json`).

Security problems (anything about downloads, tokens, extraction or sign-in) go through the private channel described
in the repository's `SECURITY.md`, not a public issue. Support channels beyond GitHub are configured by the project
maintainers and shown on the Support page of the website when available.
