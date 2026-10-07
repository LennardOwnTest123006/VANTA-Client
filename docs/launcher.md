---
title: VANTA Launcher
description: The VANTA Launcher explained: PLAY, PLAY via Minecraft Launcher, the Mods page, the Performance pack, sign-in, Java, Versions, Logs, Settings, updates, start-up errors and the command line.
order: 10
category: Launcher
---

The VANTA Launcher installs and starts Minecraft 1.21.11 with Fabric Loader 0.19.5, Fabric API, the VANTA Client
and, from launcher 1.1.0 on, the [Performance pack](#performance-pack) from Modrinth. It is a JavaFX 21 desktop
application. It is published as Windows x64 installers and a portable app, a Linux x64 app image, and one launcher jar
each for Windows x64, Linux x64 and Apple Silicon macOS (each jar runs only on the system in its name; see
[Installation → Download](installation.md#1-download)). It does three things and nothing more: download verified
files, sign you in with Microsoft or hand the game over to the official Minecraft Launcher, and start the game.

This page describes launcher 1.2.1. Where earlier versions behave differently, the text says so.

## Home

The Home screen has one big main button. Which one depends on whether PLAY can sign you in here:

- **PLAY via Minecraft Launcher** — when Microsoft sign-in is not configured and no account is stored, which is the
  case in the published builds. The title says *Ready to play*, the status *Ready via the Minecraft Launcher*, and the
  line under the button: *Opens the official Minecraft Launcher with the profile 'VANTA 1.21.11'. You sign in there.*
  One click sets up or updates the profile *VANTA 1.21.11* in the official Minecraft Launcher and opens it; see
  [PLAY via Minecraft Launcher](#play-via-minecraft-launcher). A box below, *Why the Minecraft Launcher?*, explains
  in two sentences why the Minecraft Launcher signs you in. (Launcher 1.0.x showed a disabled PLAY here, with
  *Sign-in unavailable: play through the Minecraft Launcher*.)
- **PLAY** — when a Microsoft client id is configured or an account is stored. It tells you why it is disabled when
  it is: *Sign in first*, *No Java 21 found*, *Installing…*, *Verifying…* or *Running*.

Under it the facts line reads `Minecraft 1.21.11 · Fabric 0.19.5 · Java 21 (<detected version>)`.

Pressing PLAY runs the launch flow:

1. **Java** — pick the configured or best detected Java 21 (or stop with *No Java 21 found*).
2. **Install / verify** — compare every required file (client jar, libraries, assets, Fabric libraries, Fabric API,
   VANTA jar) with its checksum; download what is missing or wrong. The [Performance pack](#performance-pack) is
   installed or updated in the same step unless it is switched off. A progress bar shows the current step and bytes.
   Sizes are shown in decimal units with one decimal place (1 MB = 1,000,000 bytes, for example "66.9 MB"), the same
   numbers the website and the release notes show.
3. **Account** — refresh the Microsoft/Minecraft token when it has expired.
4. **Launch** — from launcher 1.3.0 on, first record the standard Minecraft folder in
   `config/vanta/minecraft-folder.json` when that folder exists and no note names an existing folder yet, so
   Singleplayer lists its worlds ([Installation → Where your worlds are](installation.md#where-your-worlds-are)); then
   start `java` with the Fabric main class and stream the game output to the Logs screen. The status
   turns to **Running**; a notification reports the exit code when the game closes. When the game asks for a
   restart after you changed mods in it, the launcher starts it again ([Restart from the game](#restart-from-the-game)).

The secondary button **Use with Minecraft Launcher** sets VANTA up as a profile of the official Minecraft Launcher
without opening it (see [below](#use-with-the-minecraft-launcher)); it is there in both modes. After the profile was
set up, a box *Ready in the Minecraft Launcher* says which profile to choose and offers **Open Minecraft Launcher**
([Opening the Minecraft Launcher](#opening-the-minecraft-launcher)).

Side cards:

- **Account**: name, initials avatar, sign in/out. Without a Microsoft client id the card says that sign-in is not
  configured and offers **How to configure** (opens [Microsoft client id](#microsoft-client-id)) and **Settings**, one
  under the other with their full labels and a tooltip each. The account chip in the sidebar then says *Sign-in not
  available* and offers **How to configure** instead of **Sign in**, and its tooltip explains how to play through the
  Minecraft Launcher; with a client id it says *Sign in with Microsoft* above the **Sign in** button.
- **Java**: detected runtimes, *Install Java 21 (Temurin)*.
- **VANTA Client**: the installed version next to the latest release from the release manifest. The installed version
  is the `vanta-client-<version>.jar` that is actually in `instances/vanta-1.21.11/mods/` (with `instance.json`), so a
  client installed by *Use with Minecraft Launcher* counts too. When a newer release exists the card offers *Update*.
  While **no client is installed** (a fresh launcher) it shows *Not installed yet* and no *Update*: instead it offers
  **Install now** (the installation PLAY does, without starting the game; only when sign-in is configured) and
  **Use with Minecraft Launcher**. The update banner and the update dialog offer no client update then either.
- a recent-log snippet.

**Verify files** re-checks an existing installation without launching: it compares every installed file with its
checksum and downloads missing or damaged files again. Like PLAY it runs the regular installation, so it also replaces
an older VANTA Client with the latest release; its tooltip says so, and its notification then names the version it
installed instead of saying that nothing was missing or damaged. It is shown only when an installation exists
(`instances/vanta-1.21.11/instance.json`) and never starts a first installation; PLAY and the client card's
*Install now* do that. A client set up only with *Use with Minecraft Launcher* has no such installation, so the button
is not shown then. **Open game folder** opens `instances/vanta-1.21.11`.

## Use with the Minecraft Launcher

Microsoft sign-in inside VANTA needs an application id that Mojang has approved
([Microsoft client id](#microsoft-client-id)); the project does not have one. *PLAY via Minecraft Launcher* and *Use
with Minecraft Launcher* make VANTA playable through the **official Minecraft Launcher** instead, which downloads
Minecraft, its libraries, assets and Java itself and signs you in with Microsoft. Both run the same setup; the
differences are below.

**Before you use it**, start the official Minecraft Launcher once on this computer. VANTA only adds to the profiles
files the Minecraft Launcher has created and never creates one: `launcher_profiles.json` (Minecraft Launcher from
minecraft.net) and `launcher_profiles_microsoft_store.json` (Minecraft Launcher from the Microsoft Store or the Xbox
app on Windows). It writes its profile into every one of these files that exists. The official Fabric installer works
differently: when both files exist, it asks which launcher to use and writes only that one.

### PLAY via Minecraft Launcher

The main button when PLAY cannot sign you in (see [Home](#home)). One click:

1. checks whether the Minecraft Launcher is running ([below](#close-the-minecraft-launcher-first));
2. the first time (while the profile does not exist yet), shows the confirmation with the list of files, with the
   button **Add profile and open**; afterwards it updates the profile directly;
3. installs or updates the files listed below. Files that are already in place and verified are not downloaded again,
   and the progress on Home names the step and the file that is being downloaded;
4. opens the Minecraft Launcher. Choose *VANTA 1.21.11* next to its Play button and press Play.

*Use with Minecraft Launcher* does steps 1 to 3 with the confirmation every time (button **Add profile**) and does not
open the Minecraft Launcher.

### Close the Minecraft Launcher first

The Minecraft Launcher reads its profiles files only when it starts. A profile written while it runs appears only
after it was closed completely and started again; with launcher 1.0.x it could therefore show up only after the
Minecraft Launcher (or the PC) had been restarted. Before anything is written, VANTA looks for a running Minecraft
Launcher: `MinecraftLauncher.exe` (from minecraft.net) and the Microsoft Store / Xbox app version on Windows, the Minecraft app
on macOS and `minecraft-launcher` on Linux. If one runs, the dialog *Close the Minecraft Launcher first* names its
process and explains how to close it completely:

- **Windows**: it often keeps running in the system tray after you close its window. Click the arrow next to the
  clock, right-click the Minecraft Launcher icon and choose *Exit*, or end "Minecraft Launcher" in the Task Manager.
- **macOS**: quit it with Command-Q or *Quit* on its Dock icon; closing its window may leave it running.
- **Linux**: close every window and check that no `minecraft-launcher` process is left.

**Check again** looks again; **Continue anyway** writes the profile now (it then appears after the next start of the
Minecraft Launcher). VANTA never closes the Minecraft Launcher for you. `--install-official-profile` prints the same as
a warning and continues.

### Opening the Minecraft Launcher

**Open Minecraft Launcher** (Home, after the profile was set up) and `--open-official-launcher` start:

| System | What is started |
| --- | --- |
| Windows | `MinecraftLauncher.exe` in `Program Files (x86)\Minecraft Launcher` when it exists, else the Minecraft Launcher app from the Microsoft Store / Xbox app |
| macOS | the Minecraft app (`open -a Minecraft`) |
| Linux | `minecraft-launcher` from the `PATH` |

A Minecraft Launcher that is already running is left alone. When none is found, the launcher says *No Minecraft
Launcher was found on this computer* (exit code 3 on the command line) and starts nothing; when it could not be
started, it says why.

### What is written

The confirmation lists exactly the files below that will be written or removed, with their full paths, the VANTA
Client version resolved from the release manifest and the Performance pack mods with their versions. On the command
line:

```text
vanta-launcher --install-official-profile [--minecraft-dir <path>] [--client-jar <path>] [--without-performance-pack] [--data-dir <path>] [--memory <mb>]
vanta-launcher --open-official-launcher [--minecraft-dir <path>]
```

`--minecraft-dir` defaults to the detected official Minecraft folder: `%APPDATA%\.minecraft` on Windows,
`~/Library/Application Support/minecraft` on macOS and `~/.minecraft` on Linux. `--data-dir` selects the VANTA data
directory (and therefore the instance), `--client-jar` installs a local VANTA jar instead of the published release,
`--without-performance-pack` skips the Performance pack for this run, and `--memory` sets the heap written to the
profile. The command prints the resolved VANTA Client version and the same list as the confirmation, under *Files that
will be written* and *Files that will be removed*, before it changes anything.

When the published release cannot be installed (no manifest at the [releases URL](#releases-url), or a manifest whose
download is still empty), this check fails first: nothing is listed, downloaded or written. The launcher then shows
*Could not set up the Minecraft Launcher profile* with the note that no public release is available yet, and the CLI
exits with code 7. `--client-jar` does not need a published release.

**What is written or removed, and nothing else** (`<data>` is the VANTA data directory, `<minecraft>` the Minecraft
folder, `<version>` the VANTA Client version). A file that is already identical is left as it is.

| File | Content |
| --- | --- |
| `<data>/instances/vanta-1.21.11/mods/fabric-api-0.141.6+1.21.11.jar` | Fabric API from `maven.fabricmc.net`, verified with its SHA-256 |
| `<data>/instances/vanta-1.21.11/mods/vanta-client-<version>.jar` | the published VANTA Client, verified with the SHA-256 from the release manifest (or the `--client-jar` file) |
| `<data>/versions/vanta-client/<version>/vanta-client-<version>.jar` and `manifest.json` next to it | the rollback copy of the published release (see [Versions](#versions)); not written with `--client-jar` |
| `<data>/instances/vanta-1.21.11/instance.json` | the installed VANTA Client version is noted; only when this file already exists |
| `<data>/instances/vanta-1.21.11/mods/<file>` and `config/vanta/modrinth.json` | the [Performance pack](#performance-pack) mods and their record, verified with the SHA-512 from Modrinth; only when the pack is switched on |
| `<data>/instances/vanta-1.21.11/config/vanta/minecraft-folder.json` | from launcher 1.3.0 on: `{"minecraftDir": "<minecraft>"}`, the note that tells the VANTA Client which Minecraft folder's worlds Singleplayer lists ([Installation → Where your worlds are](installation.md#where-your-worlds-are)); listed as *Note for the VANTA Client: Singleplayer lists the worlds of this Minecraft folder (nothing in it is changed)* |
| `<minecraft>/versions/fabric-loader-0.19.5-1.21.11/fabric-loader-0.19.5-1.21.11.json` | the Fabric Loader version JSON as `meta.fabricmc.net/v2/versions/loader/1.21.11/0.19.5/profile/json` serves it (the file the official Fabric installer writes) |
| `<minecraft>/versions/fabric-loader-0.19.5-1.21.11/fabric-loader-0.19.5-1.21.11.jar` | an empty file, as the official Fabric installer writes it |
| `<minecraft>/launcher_profiles.json.vanta-backup` | a one-time copy of the original `launcher_profiles.json`, only when that file exists and has no backup yet |
| `<minecraft>/launcher_profiles.json` | the profile `vanta-1.21.11` is added or updated, only when this file exists (the Minecraft Launcher from minecraft.net) |
| `<minecraft>/launcher_profiles_microsoft_store.json.vanta-backup` | a one-time copy of the original `launcher_profiles_microsoft_store.json`, only when that file exists and has no backup yet |
| `<minecraft>/launcher_profiles_microsoft_store.json` | the same profile, only when this file exists (the Minecraft Launcher from the Microsoft Store or the Xbox app) |

**Removed**, exactly as the regular installation does it:

- older `fabric-api-*.jar` and `vanta-client-*.jar` files in `<data>/instances/vanta-1.21.11/mods/`, so only one
  version of each is loaded, and the older file of a Performance pack mod when a newer version replaces it;
- rollback copies in `<data>/versions/vanta-client/` beyond the three newest versions.

Nothing outside these paths is changed.

The mods go into the same instance the normal VANTA installation uses, so both ways of playing share worlds, settings
and client updates. The profile entry looks like this:

```json
"vanta-1.21.11": {
  "name": "VANTA 1.21.11",
  "type": "custom",
  "created": "<time of the first setup>",
  "lastUsed": "<time of this setup>",
  "icon": "data:image/png;base64,<128 px VANTA icon>",
  "lastVersionId": "fabric-loader-0.19.5-1.21.11",
  "gameDir": "<data>/instances/vanta-1.21.11",
  "javaArgs": "-Xmx<memory>M -Xms<memory/4>M -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M <extra JVM arguments> -Dvanta.javaArgs=<checksum>"
}
```

`gameDir` is the absolute path of the VANTA instance and `<memory>` is *Settings → Memory* (or `--memory`).

**`javaArgs`** (from launcher 1.3.0 on; launcher 1.2.1 and earlier wrote only `-Xmx<memory>M`). A profile that sets
`javaArgs` runs the game with exactly these arguments instead of the Minecraft Launcher's defaults, so VANTA writes the
same memory and garbage-collector arguments PLAY uses:

- `-Xmx<memory>M`, then `-Xms` with a quarter of it (at least 512 MiB), then the G1 settings shown above. `-Xms` is
  left out when your *Extra JVM arguments* contain an `-Xms`, the G1 settings when they select a garbage collector
  (`-XX:+Use…GC`).
- Then your *Extra JVM arguments* from [Settings](#settings), when they fit the profile's single space-separated
  line. Left out, with a line in the log: arguments that contain spaces or quotes or do not start with `-`, the class
  path (`-cp`, `-classpath`, `--class-path`), `-jar`, `-m` and `--module`, and an option that takes a value
  (`--add-opens`, `--add-exports`, `--add-modules`, `-p`, …) when its value is missing or does not fit; such an option
  is always written together with its value.
- Last `-Dvanta.javaArgs=<checksum>`, a system property that only marks the line: it holds the CRC-32 of everything
  before it, and nothing in the game reads it.

On a later setup VANTA replaces `javaArgs` only when it wrote them: when the entry is missing or empty, is exactly
`-Xmx<n>M` (what launcher 1.2.1 and earlier wrote), or ends with a `-Dvanta.javaArgs` checksum that still matches the
line. A line you changed in the Minecraft Launcher (the JVM arguments of the installation *VANTA 1.21.11*) fails that
check and is kept; the log then says *Kept the JVM arguments of 'VANTA 1.21.11' …*. To get VANTA's line back, empty
the JVM arguments there and run the setup again.

How the profiles files are treated:

- Each file that exists gets the same entry and is read as a JSON tree: every other profile, the launcher settings and
  any key VANTA does not know are kept, including keys whose value is `null`.
  Running the setup again updates the VANTA entry in place; its `created` time and keys VANTA does not manage (such as
  a `javaDir` you set in the Minecraft Launcher) are kept.
- Before the first change VANTA keeps a copy of each original as `<file>.vanta-backup`
  (`launcher_profiles.json.vanta-backup`, `launcher_profiles_microsoft_store.json.vanta-backup`; made once, never
  overwritten), and the new file is written atomically (temporary file, then replace).
- Every profiles file is read before anything is downloaded; if one is not valid JSON, VANTA stops and changes
  nothing. Nothing in the Minecraft folder is written before every download has succeeded, and the profiles are
  written last, so they never point at a missing version.
- If neither `launcher_profiles.json` nor `launcher_profiles_microsoft_store.json` exists, VANTA stops before
  downloading anything with the message that the Minecraft Launcher has not been set up yet: "Start the Minecraft
  Launcher once, then try again". The CLI exits with code 3. See
  [Troubleshooting](troubleshooting.md#use-with-minecraft-launcher-says-the-profiles-file-is-missing).
- CI checks what VANTA writes into both files on Linux. Whether the Minecraft Launcher from the Microsoft Store or
  the Xbox app shows the profile has not been tested on a real Windows PC; if it does not, use the
  [manual installation](installation.md#c-manual-installation).

When it is done the launcher says: **"Open the Minecraft Launcher, choose the profile 'VANTA 1.21.11' and press
Play."** (*PLAY via Minecraft Launcher* opens it for you.) If the Minecraft Launcher was running, close it completely
and start it again so it reads the new profile.

## Mods page

The **Mods** page (sidebar) searches Modrinth for mods (Fabric), shaders (Iris) and resource packs that have a
version for Minecraft 1.21.11, and installs them into `mods/`, `shaderpacks/` or `resourcepacks/` of the VANTA game
folder (`instances/vanta-1.21.11`). On the first visit it lists the most downloaded projects of the selected tab.

- **Install** takes the newest version for 1.21.11 with its required dependencies; every file comes from the address
  Modrinth returns and is checked against its SHA-512 and size.
- The installed list of each tab shows every file in that folder, also files you added yourself. The switch turns an
  entry off (the file is renamed to `<name>.disabled`, nothing is deleted) or on again (switching a project on also
  switches on the projects it needs); the trash button removes it after a confirmation.
- **Update all** brings every project installed from Modrinth to its newest version for 1.21.11.
- Fabric API and the VANTA Client are marked **VANTA**: the launcher installs and updates them itself, so they cannot
  be switched off or removed here. A project that an enabled project needs cannot be switched off or removed on its
  own.
- Changes load the next time the game starts, also in the Minecraft Launcher profile *VANTA 1.21.11*, which uses the
  same folder. If the game is running, use *Restart game* in VANTA's Mods & Shaders screen or close it and press PLAY.

What the page and the in-game Mods & Shaders screen have in common, and what is sent to Modrinth, is described in
[Mods & Shaders](mods-and-shaders.md).

## Performance pack

From launcher 1.1.0 on, PLAY, *PLAY via Minecraft Launcher*, *Use with Minecraft Launcher*, `--install` and
`--install-official-profile` also install the Performance pack: Sodium, Lithium, FerriteCore, ImmediatelyFast, Entity
Culling and Iris Shaders, plus the dependencies they require (Fabric API is installed by the launcher itself, as
before). No version is fixed in the launcher: each time, it asks Modrinth for the newest Fabric version for Minecraft
1.21.11 (a beta only when there is no release), downloads it from the address Modrinth returns, checks the SHA-512
and size Modrinth publishes, and replaces an older version of the same mod.

- When Modrinth cannot be reached, or a mod has no version for 1.21.11 yet, the installation continues without it and
  lists a warning (also in the confirmation of *Use with Minecraft Launcher*).
- A jar in `mods/` that the launcher did not install but that contains the same Fabric mod is left alone, and the pack
  mod is skipped, so Fabric never loads two copies.
- A pack mod you switched off on the Mods page stays off. A pack mod you removed is installed again by the next
  installation while the pack is switched on.
- *Settings → Game → Install the performance pack* (`installPerformancePack` in `settings.json`, on by default) turns
  it off; `--without-performance-pack` skips it for one run. Switching it off stops installing and updating the pack;
  mods that are already in the game folder stay and can be switched off or removed on the [Mods page](#mods-page).

The mods are third-party projects from Modrinth under their own licences; the launcher installs them unchanged. More
about each mod: [Mods & Shaders → The Performance pack](mods-and-shaders.md#the-performance-pack).

## Restart from the game

The launcher starts the game with `-Dvanta.launcher.restartable=true`. When you change mods in the game, the
*Restart required* banner of the Mods & Shaders screen then offers **Restart game**: the game writes
`config/vanta/restart.request` into the game folder and quits, and the launcher removes the file and starts the game
again with the same Java and account, without running the installation check again. This happens at most 5 times per
PLAY, and also with `--launch` on the command line. Games started from the official Minecraft Launcher do not get the
property; there the banner offers *Quit game*, and you start the game again in the Minecraft Launcher.

## Notifications

Results and errors (an installed mod, a finished update, a failed download, the game's exit code) appear as
notifications (toasts) over the page. A toast closes by itself after a few seconds and pauses while the mouse rests on
it. From launcher 1.2.0 on:

- a click anywhere on a toast dismisses it (the click never silently vanishes; the next one reaches the control under
  it), and the pause while the mouse is over a toast ends after 8 seconds at the latest;
- in a window narrower than 1100 px, and in any window in which the shown page is taller than the window and scrolls
  (its content then reaches the lower edge), the toasts appear top-right, below the update banner and the page header,
  and a toast is never wider than 30 percent of the window (at most 360 px). On a page that fits a wider window they
  stay bottom-right.

In launcher 1.1.0 a toast in the bottom-right corner of a small window (960 x 600) could lie over the lower *Install*
buttons of the Mods page and, because it stayed as long as the mouse rested on it, swallow every click there; in a
maximised window nothing overlapped. Dismissing the toast or maximising the window works around it there.

## Account sign-in

The launcher uses Microsoft's **device code flow**, so you never type a password into it. It works only when a
Microsoft client id is configured (next section); without one the Sign-in button explains that sign-in is not
configured, and the main button is *PLAY via Minecraft Launcher*
([Use with the Minecraft Launcher](#use-with-the-minecraft-launcher)), the way to play.

1. Click **Sign in**. The launcher shows a short code in large letters with a **Copy** button and a button that
   opens `https://www.microsoft.com/link` (you can also open that address on any other device).
2. Enter the code, sign in to your Microsoft account there and approve the request.
3. The launcher polls Microsoft until the approval arrives (it honours the interval Microsoft requests), then signs
   in to Xbox Live, obtains an XSTS token for Minecraft, logs in to `api.minecraftservices.com`, checks the
   **entitlements** (you must own Minecraft Java Edition) and loads your **profile** (name, UUID, skins).
4. The account appears in the sidebar. Tokens are refreshed silently with the refresh token when they expire; you
   sign in again only when Microsoft invalidates it.

Error messages are specific: *no Xbox Live account* (create one at xbox.com), *child account* (needs to be added to
a family by an adult), *Xbox Live unavailable in your region*, *account does not own Minecraft Java Edition*, *not
configured* (see below). The codes behind them are listed in [Troubleshooting](troubleshooting.md#microsoft-sign-in-errors).

**Where tokens are stored.** `accounts.dat` in the launcher data directory holds the access and refresh tokens (never
a password). On Windows it is encrypted with DPAPI for your user account; on Linux and macOS with AES-256-GCM using a
random key in `key.bin` that only your user can read. Tokens are redacted from every log file.

### Microsoft client id

Microsoft requires every launcher to identify itself with an *application (client) id*: an app registered in
Microsoft Entra (Azure) that **Mojang has approved for the Minecraft API**. Only then does
`api.minecraftservices.com` accept its tokens. **The project does not ship such an id**, so in the published builds
Microsoft sign-in stays disabled until you configure one, and the main button plays through the official Minecraft
Launcher instead (*PLAY via Minecraft Launcher*). This is a real limitation, not a
missing setting: an unapproved id fails at the Minecraft login step.

- Set it under *Settings → Microsoft client id* (`"msClientId"` in `settings.json`), or in the environment variable
  `VANTA_MS_CLIENT_ID`. The setting wins when both are present.
- Without an id the Sign-in button explains that sign-in is not configured and links to this section; nothing fake
  happens and no offline workaround is offered.
- To register your own: create an app registration in the Microsoft Entra admin center for *personal Microsoft
  accounts*, enable *Allow public client flows* (device code), and request Mojang's approval for the Minecraft
  API through their developer form.

### Offline sessions

VANTA is a legitimate client. An **offline session** (starting the game without contacting Microsoft) is offered only
for an account that has previously signed in successfully; it reuses that account's verified name and UUID, exactly
like Prism Launcher does. The only other case is **developer mode** together with the environment variable
`VANTA_DEV_OFFLINE=1`, which CI uses to run the game headlessly without an account. It is documented here for
completeness and is not a way to play without owning the game.

## Java detection and Temurin installation

The Java card shows the runtime that will be used. The launcher scans `JAVA_HOME`, `PATH`, its own `runtimes/`
folder and the usual vendor directories (including the official Minecraft Launcher's bundled runtimes), probes each
candidate for version, vendor and architecture and prefers an exact Java 21, 64-bit.

**Install Java 21 (Temurin)** downloads the latest Eclipse Temurin 21 JRE for your system from the Adoptium API,
verifies the SHA-256 published with the package, extracts it to `runtimes/temurin-21-<build>` (refusing archive
entries that would escape that folder) and selects it. The CLI equivalent is `--install-java`. More in
[Java 21](java-21.md).

With *PLAY via Minecraft Launcher* or *Use with Minecraft Launcher* none of this is needed: the Minecraft Launcher
brings its own Java.

## Versions

A table of the installed components with their versions and the **SHA-256 of the installed VANTA jar**:
Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, VANTA Client x.y.z, Java (path and version).
Below it, **previous client versions** kept for rollback (the three newest client versions) each with a **Roll back**
button that swaps the jar in `mods/` without downloading anything. A copy that an update kept of a jar put into
`mods/` by hand is marked *Local copy from mods/*: it is that jar exactly as it was, not a downloaded release, and
*Roll back* makes exactly that file active again. When no release has been published, the screen
says *No release published* instead of showing empty values.

While nothing is installed, the screen says how to install: with Microsoft sign-in configured or an account stored,
*press PLAY on the Home screen*; without sign-in and without a stored account (a fresh published build), PLAY cannot
install anything, so it points to *Use with Minecraft Launcher*, like the VANTA Client card on Home. After *Use with Minecraft Launcher* it says *Set up for the Minecraft Launcher*: the game is started from
the official Minecraft Launcher with the profile *VANTA 1.21.11*, which downloads Minecraft and Fabric Loader itself,
so they are not listed here.

## Logs

Two tabs, **Launcher** and **Game**, with a filter field, level toggles, **Copy**, **Open logs folder** and **Clear
view**. The game tab shows the live output of the running game (`logs/game-<timestamp>.log` in the data directory;
Minecraft's own `latest.log` is in the instance). The launcher log is `logs/launcher-0.log` (rotating, five files of
2 MB). Access tokens are redacted before anything is written.

## Settings

| Setting | Default | Notes |
| --- | --- | --- |
| Memory (MB) | half of physical RAM, clamped to 2048–8192 but never more than half of the RAM, at least 1024 (4096 when unknown) | maximum heap (`-Xmx`) for the game, also written to the Minecraft Launcher profile; minimum 1024. From launcher 1.3.0 on a PC with less than 4 GiB of RAM gets half its memory (before: 2048), and a `settings.json` without a value, or with one below 1024, gets this default instead of 4096 |
| Java path | automatic | executable or installation directory; validated before saving |
| Extra JVM arguments | none | appended after the launcher's defaults (`-Xmx`, `-Xms`, the G1 settings, `-Dfile.encoding=UTF-8`); from launcher 1.3.0 on also written to the Minecraft Launcher profile when they fit its single line ([`javaArgs`](#what-is-written)) |
| Resolution | game default | initial window size `WxH` |
| Keep launcher open | off | keep the window open while the game runs |
| Microsoft client id | empty → `VANTA_MS_CLIENT_ID` | see [Microsoft client id](#microsoft-client-id) |
| Releases base URL | empty → `VANTA_RELEASES_BASE_URL`, else the built-in default | see [Releases URL](#releases-url); shows the URL in use and has *Reset to default* |
| Check for updates automatically | on | at start, against the releases URL in use |
| Share official Minecraft files | on | reuse verified libraries/assets from the official `.minecraft` directory read-only |
| Install the performance pack | on | *Settings → Game*; installs the [Performance pack](#performance-pack) with every installation (`installPerformancePack`) |
| Developer mode | off | reveals *Offline session* only when allowed by the rules above; enables `--dev-offline` |
| Theme | VANTA Dark | the launcher has one dark theme |
| High contrast | off | stronger borders, brighter secondary and muted text and a brighter focus outline |
| Reset to defaults | — | restores every value in this table |

Settings are stored as pretty-printed JSON with a `schemaVersion` in `settings.json`; writes are atomic.

*Settings → Advanced* also shows the [data directory](#data-directory) with an **Open** button that opens it in your
file manager. A long path is shortened with "..." in the middle, and its tooltip shows the full path.

## Releases URL

The launcher finds VANTA releases through two release manifests, `client-latest.json` and `launcher-latest.json`,
in one directory: the *releases base URL*. It is built in, so update checks and the VANTA Client download work
without any configuration:

```text
https://raw.githubusercontent.com/LennardOwnTest123006/VANTA-Client/HEAD/shared/releases/latest
```

That is the folder `shared/releases/latest/` of the VANTA repository on its default branch (`HEAD`), where the
maintainers commit the manifests the release workflow produced (the manifest format is described in
`shared/releases/README.md` in the [repository](https://github.com/LennardOwnTest123006/VANTA-Client)).

The URL in use is chosen in this order:

1. a non-empty *Settings → Releases base URL* (`"releasesBaseUrl"` in `settings.json`); on the command line
   `--releases-url <url>` replaces it for that run;
2. the environment variable `VANTA_RELEASES_BASE_URL`;
3. the built-in default above.

The Settings page shows the URL in use and where it comes from (*set here*, *from the environment variable* or
*built-in default*). **Reset to default** clears your value; an empty field means "use the environment variable or
the default". An empty `"releasesBaseUrl"` in an older `settings.json` therefore also uses the default.

- A value that is not an absolute `http(s)` URL is rejected in Settings; from `settings.json` or the environment it
  disables update checks with a clear message, and `--check-update` exits with code 3. The launcher never guesses
  another location.
- No manifest at the URL (HTTP 404), or a manifest whose `downloadUrl` is still empty, means that no release has been
  published there yet; the launcher says so (exit code 7 on the command line) and never invents a download.

## Updates and rollback

At start (and with `--check-update`) the launcher fetches `launcher-latest.json` and `client-latest.json` from the
[releases URL](#releases-url) and compares the semantic versions with what is installed.

- **Client update**: offered only when a VANTA Client is installed (see the *VANTA Client* card under [Home](#home));
  a fresh launcher installs the client with PLAY, *PLAY via Minecraft Launcher*, *Install now* or *Use with
  Minecraft Launcher* instead. The banner
  shows the changelog; *Update* downloads exactly `vanta-client-<version>.jar` (never the `-mods.zip` bundle or the
  Fabric API jar of the same release) into `versions/vanta-client/<version>/`, verifies the SHA-256 from the manifest
  (a manifest without a digest is refused), keeps it there as the rollback copy and activates it in `mods/`. The three
  newest versions remain for **Roll back**. When the jar it replaces has a release version but no rollback copy yet
  (for example a jar put into `mods/` by hand), the launcher keeps a copy of it first, as long as it is among the three
  newest versions afterwards. The notification *VANTA Client <version> installed* names the previous version as kept
  for roll back only when a copy of it really exists. Otherwise it says that no copy of the replaced version is kept,
  and either that the Versions page lists other kept versions to roll back to or that no other version is kept. The
  *VANTA 1.21.11* profile of the Minecraft Launcher uses the same `mods/` folder, so it gets the update too.
- **Launcher update**: the launcher picks the release file that replaces exactly the kind of installation that is
  running, downloads it to `cache/updates/<version>/<file name>` in the data directory, under the exact name of the
  release file, and verifies its SHA-256 the same way. The dialog afterwards shows the full path of the file in a field
  you can select and copy, and offers *Show in folder* (next to *Open installer* for the Windows installer):

  | Running launcher | Self-update file | What happens after the download |
  | --- | --- | --- |
  | Windows x64, installed with the `.msi` or `.exe` | `VANTA-Launcher-<version>.msi` (`.exe` when a release has no `.msi`) | after your confirmation the installer is handed to Windows; the launcher never runs it itself |
  | Windows x64, portable folder (from `-windows-portable.zip`) | `VANTA-Launcher-<version>-windows-portable.zip` | shown with instructions that name your folders (*Show in folder* opens the download): close the launcher, then extract the zip into the folder that **contains** your `VANTA Launcher` folder (its parent) and let it replace the existing files; the zip's top-level `VANTA Launcher` folder lands on top of the old one. Extracting it into the `VANTA Launcher` folder itself only nests a second `VANTA Launcher` folder and leaves the old version in place. For a renamed portable folder the dialog says to copy everything inside the zip's `VANTA Launcher` folder into your portable folder instead. The launcher never unpacks or runs it |
  | Windows x64, `java -jar` | `vanta-launcher-<version>-windows-all.jar` | shown in its folder: start it with Java 21 using the `java -jar "<full path>"` command the dialog shows |
  | Linux x64 app image (from `-linux-x64.tar.gz`) | `VANTA-Launcher-<version>-linux-x64.tar.gz` | shown in its folder: extract it and start `VANTA Launcher/bin/VANTA Launcher` |
  | Linux x64, `java -jar` | `vanta-launcher-<version>-linux-all.jar` | shown in its folder: start it with Java 21 using the `java -jar "<full path>"` command the dialog shows |
  | macOS, Apple Silicon | `vanta-launcher-<version>-macos-aarch64-all.jar` | shown in its folder: start it with Java 21 using the `java -jar "<full path>"` command the dialog shows |
  | macOS on Intel, Linux on ARM, an arm64 Java on Windows on ARM, other systems | none | the dialog opens the GitHub release page instead |

  The launcher tells these installations apart by how it was started: the installers and app images start it through
  their own launcher program (`VANTA Launcher.exe`, `bin/VANTA Launcher`), a jar is started with `java -jar`, and the
  portable zip carries the file `VANTA Launcher/app/vanta-portable.marker`, which the `.msi` and `.exe` do not
  contain. Rolling the launcher back means installing the previous release from GitHub Releases, where every version
  stays available with its checksum.

  **Updating from launcher 1.0.0.** The table describes launcher 1.0.1 and newer. An update is offered by the
  launcher you are running, and launcher 1.0.0 still picks the file by system only, for the update to 1.2.1 as for
  every earlier one: on Windows it offers `VANTA-Launcher-1.2.1.msi` (also in the portable folder and when started as
  a jar), on Linux x64 `VANTA-Launcher-1.2.1-linux-x64.tar.gz` (also when started as a jar). Installing that `.msi` from a
  portable folder or a jar installs a second launcher and leaves the portable copy or jar at 1.0.0. To keep a portable
  or jar setup, choose *Not now* in the 1.0.0 launcher, close it and download
  `VANTA-Launcher-1.2.1-windows-portable.zip` or the jar for your system from the release
  [`launcher-v1.2.1`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.2.1) yourself
  (verify it as in [Installation → Verify the checksum](installation.md#2-verify-the-checksum) and replace the files
  as described in the table). From 1.0.1 on, the launcher picks the matching file itself.

  **Update files saved by launcher 1.0.0 and 1.0.1.** They save the download as `cache/updates/<version>-<file name>`,
  for example `1.2.1-vanta-launcher-1.2.1-linux-all.jar`. It is the verified release file under a different name:
  *Show in folder* opens that folder and the file starts from there as it is. To check it yourself with
  `sha256sum -c --ignore-missing SHA256SUMS.txt` from the release, rename it to the release name first
  (`vanta-launcher-1.2.1-linux-all.jar`). From launcher 1.0.2 on, the file keeps its release name
  ([Installation → Updating](installation.md#updating)).
- An announced release whose manifest still has an empty download URL is shown as *announced, not downloadable yet*.

`--check-update` prints the same result, for example on a fresh launcher:

```text
Client: not installed (install it with --install or --install-official-profile); latest release 1.2.1
```

## About and links

The **About** page shows the launcher version, the pinned versions it installs, the JavaFX platform, the Java runtime,
the data directory and the licenses, with **Copy build info** for bug reports. The **Website** entries in the sidebar
and on the About page open [vanta-client.netlify.app](https://vanta-client.netlify.app) in your browser; **Support**
and *Report a problem* open the GitHub issues, *Source code on GitHub* the repository. The environment variables
`VANTA_WEBSITE_URL` and `VANTA_SUPPORT_URL` replace the website and support links with another `http(s)` address; an
entry without a usable address is disabled and says *Not configured*.

To open a link (Website, Support, *How to configure*, release notes) the launcher tries Java's desktop integration
first, then the system's own opener (`xdg-open` on Linux, `open` on macOS, `rundll32 url.dll,FileProtocolHandler` on
Windows, checked by its exit code), then JavaFX. Unless the desktop integration or the system opener confirmed that the
browser was started, the address is shown in a notification, so a click never silently does nothing. Only when no
opener accepted the link is the address also copied to the clipboard, so you can paste it into your browser yourself.
After JavaFX accepted it (JavaFX cannot tell whether a browser opened) the clipboard is left alone, so a sign-in code
you just copied stays there.

## Data directory

| System | Location |
| --- | --- |
| Windows | `%APPDATA%\VANTA Launcher` |
| macOS | `~/Library/Application Support/VANTA Launcher` |
| Linux | `$XDG_DATA_HOME/vanta-launcher` or `~/.local/share/vanta-launcher` |

The environment variable `VANTA_LAUNCHER_HOME` (or `--data-dir` on the command line) overrides all three. The layout
(`instances/`, `libraries/`, `assets/`, `versions/`, `runtimes/`, `logs/`, `cache/`, `settings.json`, `accounts.dat`,
`key.bin`) is described in [Installation → Where files live](installation.md#5-where-files-live).

## Start-up errors

Every start writes the launcher log `logs/launcher-0.log` from its first line, including the launcher, Java, JavaFX
and system details. From launcher 1.1.0 on, when the launcher cannot start (for example because its window cannot be
created, JavaFX cannot be loaded or a jar was started on the wrong system), it:

- shows the error with its details in a dialog (a plain system dialog when JavaFX itself could not start);
- writes the error, its stack trace and these details to `logs/startup-error.txt` in the data directory:

  | System | File |
  | --- | --- |
  | Windows | `%APPDATA%\VANTA Launcher\logs\startup-error.txt` |
  | macOS | `~/Library/Application Support/VANTA Launcher/logs/startup-error.txt` |
  | Linux | `~/.local/share/vanta-launcher/logs/startup-error.txt` (or below `$XDG_DATA_HOME/vanta-launcher`) |

  The file of the previous failed start is kept as `startup-error.previous.txt`. When the data directory cannot be
  written, the file goes to the system's temporary folder instead;
- exits with code 1.

Launcher 1.0.x closed without any message in that case. If the launcher does not start for you, attach
`startup-error.txt` and `launcher-0.log` to your report
([Troubleshooting → The launcher does not start](troubleshooting.md#the-launcher-does-not-start-or-windows-blocks-it)).
The CI job *Launcher starts on Windows* installs the `.msi` (built in CI, not the release file), unpacks the portable
zip and runs the Windows jar on a Windows runner, and fails unless each of them opens the launcher window.

## Command line reference

The same program is the GUI and the CLI: any argument other than `--ui` selects the CLI. CI uses it to test the real
install → launch pipeline; you can use it to script installations or diagnose problems. `vanta-launcher` below stands
for `java -jar vanta-launcher-<version>-<system>-all.jar` (`<system>` is `windows`, `linux` or `macos-aarch64`) or,
on Linux, `"VANTA Launcher/bin/VANTA Launcher"` from the app image. The command line needs no JavaFX, so it runs with
every launcher jar on every system; only the window needs the jar for your system. On Windows `VANTA Launcher.exe` is a
window program without console output; use the jar with an installed Java 21 there, or the bundled runtime:
`"VANTA Launcher\runtime\bin\java.exe" -jar "VANTA Launcher\app\vanta-launcher-<version>-all.jar"` inside the
unzipped portable app (for 1.2.1: `vanta-launcher-1.2.1-all.jar`).

```text
vanta-launcher --install [--client-jar <path>] [--without-client] [--without-performance-pack] [--no-assets]
vanta-launcher --install-official-profile [--minecraft-dir <path>] [--client-jar <path>] [--without-performance-pack]
vanta-launcher --open-official-launcher [--minecraft-dir <path>]
vanta-launcher --launch [--dev-offline --username <name>] [--world <name> | --server <host>] [--exit-after <seconds>]
vanta-launcher --check-java [--java <path>]
vanta-launcher --install-java
vanta-launcher --check-update [--releases-url <url>]
vanta-launcher --print-command [--dev-offline --username <name>]
vanta-launcher --version | --help
common options: --data-dir <path>  --memory <mb>  --java <path>  --resolution <WxH>  --releases-url <url>  --verbose
```

| Command | What it does |
| --- | --- |
| `--install` | installs Minecraft 1.21.11, Fabric Loader, Fabric API, the VANTA Client and the [Performance pack](#performance-pack); `--client-jar` uses a local jar instead of the release manifest, `--without-client` skips VANTA, `--without-performance-pack` skips the pack for this run, `--no-assets` skips the asset download |
| `--install-official-profile` | [Use with the Minecraft Launcher](#use-with-the-minecraft-launcher): installs Fabric API, the VANTA Client and the Performance pack into the VANTA instance, writes the Fabric Loader version to `<minecraft>/versions/fabric-loader-0.19.5-1.21.11/` and adds the profile `vanta-1.21.11` to `launcher_profiles.json` and/or `launcher_profiles_microsoft_store.json`, whichever exist; prints a warning when the Minecraft Launcher is running; `--minecraft-dir` selects the Minecraft folder, `--client-jar` a local jar, `--without-performance-pack` skips the pack |
| `--open-official-launcher` | starts the official Minecraft Launcher ([Opening the Minecraft Launcher](#opening-the-minecraft-launcher)); a running one is left alone; exit code 3 when none is found |
| `--launch` | launches the installed instance with the active account and starts it again when the game asks for a [restart](#restart-from-the-game) (at most 5 times); `--dev-offline` requires developer mode and `VANTA_DEV_OFFLINE=1`; `--world`/`--server` use Minecraft's quick play; `--exit-after` stops the game after N seconds (CI) |
| `--check-java` | lists detected runtimes and the one that would be used; `--java` probes a specific path |
| `--install-java` | downloads and installs a verified Temurin 21 into the launcher directory |
| `--check-update` | prints the releases URL in use, the launcher update and, for an installed client, the client update; without an installed client it prints `Client: not installed (…); latest release <version>` and offers no client update |
| `--print-command` | prints the exact game command line with secrets redacted, without launching |
| `--version` | prints the launcher version and `Minecraft 1.21.11 · Fabric 0.19.5 · Java 21` |

Exit codes:

| Code | Meaning |
| --- | --- |
| 0 | success |
| 1 | unexpected failure, or the window could not start: a launcher jar started on another system (the message names the file to download), JavaFX missing or no display ([Start-up errors](#start-up-errors)) |
| 2 | invalid command line |
| 3 | not configured: Microsoft client id missing, releases URL unusable, the Minecraft Launcher was never started (neither `launcher_profiles.json` nor `launcher_profiles_microsoft_store.json` exists), or `--open-official-launcher` found no Minecraft Launcher |
| 4 | integrity check failed (checksum mismatch) |
| 5 | network or server failure: refused connection, DNS, TLS, timeout, HTTP error, or an HTTPS proxy that refuses the connection; the message names the step and the URL |
| 6 | no Java 21 runtime found |
| 7 | the requested release is not published yet |
| 8 | cancelled |
| 9 | authentication failed or no account available |
| 10 | the game exited with an error |
| 11 | insufficient disk space |

## Environment variables

| Variable | Purpose |
| --- | --- |
| `VANTA_MS_CLIENT_ID` | Microsoft application (client) id used when the setting is empty |
| `VANTA_RELEASES_BASE_URL` | releases base URL used when the setting is empty (otherwise the built-in default) |
| `VANTA_LAUNCHER_HOME` | overrides the data directory |
| `VANTA_DEV_OFFLINE` | `1` allows development offline accounts together with developer mode (CI only) |
| `VANTA_UI_SMOKE_SCREENSHOT`, `VANTA_UI_SMOKE_EXIT_AFTER` | for automated tests: write a screenshot of the window once it is shown (`<png>`), close the launcher that many seconds after the window was shown with exit code 0 |
| `VANTA_UI_SMOKE_PAGE`, `VANTA_UI_SMOKE_SIZE` | from launcher 1.2.0 on, only together with one of the two above: show that page (`home`, `mods`, `versions`, `logs`, `settings`, `about`) before the screenshot and size the window first (`<width>x<height>`, raised to the 960 x 600 minimum). On their own they do nothing |
