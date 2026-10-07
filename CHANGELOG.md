# Changelog

All notable changes to VANTA Client, VANTA Launcher and the website are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the projects use
[Semantic Versioning](https://semver.org/).

Machine-readable release notes live in `website/content/changelog/` and are rendered on the website.

## [Unreleased]

No changes yet.

## [1.3.0] - 2026-10-07

Release of both products. VANTA Client 1.3.0 fixes three reports: the graphics setting that "jumps back to Fancy",
*Singleplayer* that opened *Create New World* instead of listing the player's worlds, and short freezes while playing.
It also adds Smart Boost, a local, rule-based tuner that measures real gameplay on the player's PC and picks the video
preset that runs smoothly. It uses no model and no network. VANTA Launcher 1.3.0 gives the Minecraft Launcher profile
the same tuned Java memory and garbage-collector arguments as PLAY, picks the default memory from the PC's RAM and
tells the client which Minecraft folder holds the player's worlds. Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric
API 0.141.6+1.21.11 and Java 21 are unchanged.

### VANTA Client 1.3.0

#### Added
- **Smart Boost.** It runs once per installed client version (the first install and every update) and picks one of
  the presets Max FPS, Low, Balanced, High or Ultra for this PC.
  - It counts only gameplay frames: a world is loaded, no screen is open, the game is not paused, the window has the
    focus and the player moved or looked around in the last 30 s. The pause menu, an unfocused window and standing
    idle do not count.
  - The automatic run starts after 20 s of gameplay in a world, never at start-up and never in a menu. Each step
    discards 8 s of frames after the change and then measures 25 s of gameplay (at least 600 frames). A run measures
    at most three steps.
  - Target: 60 FPS when the frame rate is unlimited or VSync is on, otherwise the frame-rate limit clamped to 60–144
    (a limit below 60 is the target itself). A step passes when the median frame rate reaches 1.1 × the target
    (0.95 × while a limit or VSync caps the frame rate), the 1 % low reaches 0.6 × the target and there is at most
    one hitch (a frame over 50 ms) per 10 s.
  - The first step is a guess from the GPU name, the CPU threads and the Java heap; a software renderer starts and
    stays at Max FPS. A pass with headroom (no cap, median at least 1.6 × the target, 1 % low at least the target)
    tries one preset higher. A fail steps down until a preset passes or Max FPS is reached.
  - It writes only render distance, simulation distance, particles, clouds, smooth lighting, entity shadows, entity
    distance, biome blend, mipmap levels and, for Max FPS, the menu blur. It never changes Fast / Fancy / Fabulous,
    the frame-rate limit or VSync.
  - It never overrides the player's own choices. An option changed after Smart Boost wrote it (Video Settings,
    Sodium's screen, a profile, a preset, Boost FPS) is left alone by every later automatic run. When the graphics
    settings were already the player's own before its first run (Fast, Fabulous or Custom instead of the untouched
    default Fancy), the automatic run changes nothing.
  - What it wrote, the values before and what it measured are kept in `config/vanta/smart-boost.json`.
- **Smart Boost card** in the Performance Center, above the presets: the last result ("Smart Boost picked …: about
  … FPS measured (target … FPS)") or the measurement in progress, the options left alone because the player changed
  them, the threads, Java heap and GPU name the guess is based on, **Re-tune** (takes back the options the player
  changed and measures again) and **Undo Smart Boost** (puts the options it still controls back to their values from
  before its first change and turns automatic tuning off). Both are also in the command palette. Notifications say
  when Smart Boost starts, what it applied and when it stopped because the player changed a video option.
- Two switches on the card and in *Settings → Performance*: *Smart Boost: tune automatically after install/update*
  (on) and *Smart Boost: adjust render distance while playing* (off). The second one changes only the render
  distance, and only while it is still the one Smart Boost set: 2 chunks at a time, never below 6 chunks or above the
  distance the run picked, at most once every 3 minutes, and not up again within 5 minutes after a step down. The JVM
  option `-Dvanta.smartBoost.auto=false` turns the automatic run off for one game.
- **Settings → General → Singleplayer worlds**: *Minecraft folder* (default) or *VANTA folder*. With *Minecraft
  folder*, Singleplayer lists, creates, loads and backs up worlds in `saves/` and `backups/` of the normal Minecraft
  folder: the folder the VANTA Launcher recorded in `config/vanta/minecraft-folder.json`, or, in a VANTA Launcher
  instance without that note, `.minecraft` (`%APPDATA%\.minecraft`, `~/Library/Application Support/minecraft`,
  `~/.minecraft`). Nothing is moved, copied or deleted. The VANTA folder stays in use when the Minecraft folder has no
  `saves/` folder, when its `saves/` holds no world while the VANTA folder's does, when the game already runs in the
  Minecraft folder (manual installation), and in another launcher's instance without the VANTA Launcher's note. A
  change takes effect at the next game start; `latest.log` names the folder in use and why. A world opened with mods
  that add blocks or items loses those blocks when vanilla Minecraft opens it later; VANTA adds none.
- **Sodium tip.** With the Performance pack, Sodium's screen replaces vanilla Video Settings. There a change is kept
  only after *Apply* (Alt+A); Escape closes the screen and discards changes that were not applied. The first time
  that screen opens in a game session, a notification says so.

#### Changed
- **Built-in profiles change only what they are about**: the options of their performance preset, the frame-rate
  limit and VSync of their frame-rate choice, and Building's FOV 85. Activating one no longer resets volumes, mouse
  sensitivity, GUI scale, FOV (except Building) or any other vanilla option to vanilla's default. On the first start
  of 1.3.0, built-in profiles the player never changed are refreshed once (marker `builtInContent` in
  `profiles/state.json`; the profile schema is unchanged, so older releases still read every profile). Renamed or
  edited built-ins and the player's own profiles keep their content.
- Activating a profile applies its graphics preset before the options that preset bundles, so render distance,
  clouds and particles end up as the profile says.
- The frame-rate choice follows the game: when Max Framerate and VSync were changed in vanilla Video Settings or
  Sodium, opening Settings or the Performance Center records the matching choice in `settings.json` (no game option
  is written), and a profile saved afterwards keeps that limit. A limit no choice stands for (for example 75 FPS) is
  saved as it is.
- Render distance suggestions count only gameplay frames (the same rule as Smart Boost), never suggest less than
  6 chunks (was 4) and stay quiet while Smart Boost controls the render distance.

#### Fixed
- **Graphics "jumps back to Fancy".** Minecraft 1.21.11 switches its graphics preset to *Custom* as soon as one option
  of the preset bundle changes (render distance, clouds, particles, …). VANTA could not show *Custom* and showed its
  default, *Fancy*, instead. Choosing *Fancy* there did nothing, because VANTA took it for the current value, and a
  profile saved in that state stored a *Fancy* the player never chose. Now the *Graphics* row shows *Custom*, and
  choosing Fast, Fancy or Fabulous while the game is Custom applies that preset. *Custom* itself cannot be chosen.
  A profile stores *Custom*, which activation skips, so the profile's own options decide. A value the game does not
  take is no longer remembered or shown. Opening and closing VANTA's screens and starting the game write no Minecraft
  option and do not save `options.txt`; only a VANTA control the player uses, a profile and Smart Boost change
  options.
- **Singleplayer opened *Create New World* instead of the player's worlds** (VANTA Launcher installations). VANTA runs
  in its own game folder, so Singleplayer listed only that folder's worlds, none for a new VANTA player. See
  *Singleplayer worlds* above.
- **Short freezes when a VANTA screen closed.** Closing any VANTA screen, also with Escape back into the game,
  rewrote every profile, `profiles/state.json` and `cosmetics.json` on the render thread. It now writes only what
  changed and nothing when nothing changed; a failed write is tried again at the next close.
- The FPS widget's 1 % low no longer copies and sorts its 240 frame times every tick (same result, no allocation).

### VANTA Launcher 1.3.0

#### Changed
- **JVM arguments of the Minecraft Launcher profile.** *PLAY via Minecraft Launcher*, *Use with Minecraft Launcher*
  and `--install-official-profile` write the profile's `javaArgs` with the memory and garbage-collector arguments
  PLAY uses: `-Xmx<memory>M`, `-Xms` (a quarter of it, at least 512 MiB), `-XX:+UseG1GC`,
  `-XX:+UnlockExperimentalVMOptions`, `-XX:G1NewSizePercent=20`, `-XX:G1ReservePercent=20`,
  `-XX:MaxGCPauseMillis=50` and `-XX:G1HeapRegionSize=32M`. `-Xms` and the G1 arguments are left out when the extra
  JVM arguments set `-Xms` or select a garbage collector. The *Extra JVM arguments* from the launcher's Settings follow
  when they fit the profile's single line: no spaces or quotes, no class path, `-jar` or module switch, and an option
  such as `--add-opens` only together with its value. The log names every argument that is left out. The line ends
  with `-Dvanta.javaArgs=<checksum>`, a system property that marks it as VANTA's.
- **JVM arguments edited in the Minecraft Launcher are kept.** A later setup replaces `javaArgs` only when VANTA wrote
  them: missing, empty, exactly the `-Xmx<n>M` of launcher 1.2.1 and earlier, or a line whose `-Dvanta.javaArgs`
  checksum still matches. Otherwise the line is kept and the log says so.
- **Default memory from the PC's RAM**: half of the physical memory, between 2048 and 8192 MiB, but never more than
  half of it (4096 MiB when the memory is unknown). A PC with less than 4 GiB gets half its memory, at least
  1024 MiB (before: 2048 MiB). A `settings.json` without a memory value, or with one below 1024 MiB, now gets this
  default instead of 4096 MiB; a stored value of 1024 MiB or more is kept.
- **Minecraft folder note** for the client's *Singleplayer worlds*: `config/vanta/minecraft-folder.json` in VANTA's
  game folder, `{"minecraftDir": "<absolute path>"}`. *PLAY via Minecraft Launcher*, *Use with Minecraft Launcher*
  and `--install-official-profile` record the Minecraft folder the profile went to (also a custom
  `--minecraft-dir`); the confirmation lists the file. Before PLAY the launcher records the detected Minecraft folder
  when it exists and no note names an existing folder yet; a failure there is only logged. Nothing in the Minecraft
  folder is changed, and an identical file is not rewritten.

#### Fixed
- **Short freezes when playing through the Minecraft Launcher.** The *VANTA 1.21.11* profile carried only
  `-Xmx<memory>M`. A profile with `javaArgs` runs with exactly those arguments instead of the Minecraft Launcher's
  defaults, so the game ran on plain JVM defaults: a 200 ms pause target and a heap that starts small and grows. The
  profile now gets PLAY's tuning (see *Changed*).

### CI
- CI runs four new steps in the client game test, in the job without and the job with the Performance pack:
  - `OptionsPersistenceStep` sets Fast in vanilla Video Settings and leaves with Escape, opens and closes VANTA's main
    menu, Settings (Video) and Performance Center, and reloads the options from disk as a restart does. It then
    changes clouds through the vanilla API that a slider and Sodium's *Apply* use and requires that the game reports
    Custom and that VANTA shows Custom. With Sodium loaded it opens Sodium's video settings and requires the Apply
    tip. Last it picks Fast in VANTA's own *Graphics* row. After every stage the game, VANTA's row and `options.txt`
    must agree.
  - `WorldsFolderStep`: the production game test task creates a stand-in Minecraft folder
    (`run/production-gametest/official-minecraft` with an empty `saves/`) and the launcher's note pointing at it.
    The step checks that Minecraft's level storage uses the folders VANTA resolved, that the test world was created
    in the stand-in `saves/` and not in VANTA's own, and that Singleplayer lists that world and a copy placed there
    by plain file copy. It never touches a real `.minecraft`.
  - `HitchProbeStep` measures at least 600 frames with the VANTA HUD on and again off while the player turns on the
    spot, and adds to `vanta-perf-probe.json`: frame-time maximum, p99 and p99.9, frames over 50 ms and over 100 ms,
    the slowest single call of the HUD and crosshair elements, garbage collections in that window and the JVM's heap
    and GC arguments. It fails when one VANTA HUD or crosshair call takes more than 8 ms (after taking off a garbage
    collection in the same tick) or when, with the HUD on, an element was never called. The frame-time hitch counts
    are recorded, not judged, because the CI machine renders in software.
  - `SmartBoostStep` runs Re-tune with shortened windows and requires a result, the picked render distance, a
    written `smart-boost.json` and an unchanged graphics preset, frame-rate limit and VSync. It then changes the
    render distance by hand and requires that a run with the automatic rules leaves it alone. Last, Undo must
    restore the simulation distance from before the first run (skipped with a warning when the game reported another
    graphics preset after the hand change). The automatic run itself stays off in the game test.
- New core and launcher unit tests cover the worlds folder rules, Smart Boost's rules, state and starting guess,
  frame statistics, the built-in profile refresh, saving on screen close, the *Custom* graphics row and the profile's
  `javaArgs`.

#### Updating to 1.3.0
- **Your worlds.** After the update Singleplayer lists the worlds of your normal Minecraft folder, as the vanilla
  game does. Worlds you created with an earlier VANTA stay in VANTA's game folder (`instances/vanta-1.21.11/saves` in
  the launcher's data directory). While the Minecraft folder has worlds, those VANTA worlds are not listed: switch
  *Settings → General → Singleplayer worlds* to *VANTA folder* and restart, or copy the world folders yourself. A
  manual installation already runs in `.minecraft` and sees no change.
- **Smart Boost's first run.** About 20 s after you start playing in a world, *Smart Boost is testing settings*
  appears and the video options change up to three times, each step 33 s of play (8 s settling, 25 s measuring).
  Then *Smart Boost applied* names the preset and the measured FPS. To skip it, turn off *Settings → Performance → Smart Boost: tune automatically
  after install/update* before you join a world. *Undo Smart Boost* in the Performance Center restores your earlier
  values. If your graphics settings were already your own, the card says so and nothing changes. Afterwards the
  *Graphics* row can show *Custom*: Smart Boost changes single options and never Fast / Fancy / Fabulous.
- **Sodium.** With the Performance pack, *Options → Video Settings* is Sodium's screen. Press *Apply* (Alt+A) before
  you leave it; Escape discards changes that were not applied. VANTA reminds you once per game session.
- **Minecraft Launcher profile.** Run *PLAY via Minecraft Launcher* or *Use with Minecraft Launcher* once with
  launcher 1.3.0, so the profile gets the new JVM arguments and the Minecraft folder note. JVM arguments you edited in
  the Minecraft Launcher are kept; empty them there if you want VANTA's line.

## [1.2.1] - 2026-10-07

Bug-fix release of both products for one start-up crash. With a mod in VANTA's game folder that was built for an older
Minecraft, Minecraft 1.21.11 stopped while starting. The report that led to it came from Smart FPS Booster 1.0.0, a
third-party mod on Modrinth:

```
java.lang.RuntimeException: Could not execute entrypoint stage 'client' due to errors, provided by 'smartfpsbooster' at 'com.smartclient.fpsbooster.SmartFPSBoosterClient'!
Caused by: java.lang.NoSuchMethodError: 'void net.minecraft.class_304.<init>(java.lang.String, net.minecraft.class_3675$class_307, int, java.lang.String)'
```

Both Fabric builds that Smart FPS Booster lists for Minecraft 1.21.11 were made for older versions
(`smart-fps-booster-1.0.0+mc1.21.4.jar` and `smart-fps-booster-1.0.0+mc1.21.8.jar`). They create key bindings with a
text category, a constructor that Minecraft removed in 1.21.9, and their `fabric.mod.json` (`~1.21.4`, `~1.21.8`) lets
Fabric load them on 1.21.11 anyway. The real 1.21.11 client stops with that `NoSuchMethodError`, with or without
VANTA; the new diagnostic workflow reproduced it for both builds. VANTA cannot change another author's mod. From 1.2.1
on it keeps such a mod from stopping the game. Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11 and
Java 21 are unchanged.

### VANTA Launcher 1.2.1

#### Fixed
- **Start check.** Before PLAY, before *PLAY via Minecraft Launcher* or *Use with Minecraft Launcher* opens the
  Minecraft Launcher, before a restart the game asks for, once when the launcher window opens, and before the
  command-line `--launch` and `--install-official-profile`, the launcher checks every mod in VANTA's game folder
  except VANTA itself and Fabric API. A mod whose code still creates key bindings the way Minecraft did before 1.21.9,
  and that has no code path for the newer key bindings, is switched off: its file is renamed to `.jar.disabled`,
  nothing is deleted, a notification names the mod and the reason, and the Mods page can switch it on again. Jars that
  passed are remembered by size and date, so the check does not read them again on every start.
- **Crash-report recovery.** When the newest crash report in VANTA's game folder says that a mod's start-up code
  failed (`Could not execute entrypoint stage … provided by '<mod id>'`) and the report is newer than that mod's file,
  the launcher switches that mod off, once per report. VANTA, Fabric API and Fabric's own modules are never switched
  off, only reported. After the launcher opened the Minecraft Launcher it watches for such a report for 30 minutes and
  then asks you to press Play in the Minecraft Launcher again; after a game started with PLAY ends, it looks at the
  report right away.
- **Mods page.** It no longer installs a build that the start check would switch off: the download is deleted again,
  the page says why, and an update keeps the version you have.
- Limits: before the start only this kind of incompatibility is recognised; other start-up crashes are caught through
  the crash report, and there only Fabric's entrypoint message counts (not Mixin errors). A mod that another enabled
  mod requires is reported, not switched off.

### VANTA Client 1.2.1

#### Fixed
- The in-game *Mods & Shaders* screen refuses to install a mod built for an older Minecraft, with the same check: the
  download is deleted, the message says "This mod was built for an older Minecraft and would stop Minecraft 1.21.11
  from starting, so it was not installed.", and the rest of the same install still goes in. An identical copy already
  in `mods/` is not recorded as installed, and a switched-off copy is never switched back on, also not when another
  mod needs it.

### CI
- The launcher integration job puts the real Smart FPS Booster jars from Modrinth (versions XhY98l33 and hE70j3c1,
  SHA-512 checked) into VANTA's game folder and checks that `--install-official-profile` and the headless `--launch`
  switch them off and that the game then starts without a crash report. The live Modrinth test checks that the in-game
  installer refuses `smart-fps-booster`.
- New diagnostic workflow `mod-crash-repro.yml`: it starts the real 1.21.11 client with one extra Modrinth mod version
  and prints the crash report and the mod's metadata.

#### Updating to 1.2.1
- If the game already stops at start: launcher 1.2.0 offers the update to 1.2.1 itself. Install it and open the
  launcher once; it switches the mod off and says so. Without updating: open the VANTA Launcher, go to *Mods*, and
  switch off Smart FPS Booster in the installed list, or delete `smart-fps-booster-*.jar` from the `mods` folder of
  VANTA's game folder (*Home → Open game folder*).
- Manual installations without the VANTA Launcher: remove `smart-fps-booster-*.jar` from your `mods` folder. Fabric
  itself loads such a jar, so only you can take it out there.

## [1.2.0] - 2026-10-07

Release of both products. VANTA Client 1.2.0 and VANTA Launcher 1.2.0 are published on
[GitHub Releases](https://github.com/LennardOwnTest123006/VANTA-Client/releases) as `client-v1.2.0` and
`launcher-v1.2.0` by the release workflow, with the same file names as before and the new version number
(`vanta-client-1.2.0.jar`, `vanta-client-1.2.0-mods.zip`, `fabric-api-0.141.6+1.21.11.jar`;
`VANTA-Launcher-1.2.0.msi`, `.exe`, `-windows-portable.zip`, `-linux-x64.tar.gz` and
`vanta-launcher-1.2.0-windows-all.jar`, `-linux-all.jar`, `-macos-aarch64-all.jar`). Minecraft 1.21.11, Fabric
Loader 0.19.5, Fabric API 0.141.6+1.21.11 and Java 21 are unchanged. The client release fixes the frame-rate caps that
VANTA 1.1.0's presets and profiles wrote (the cause of the "30 FPS after choosing Low" reports), adds *Max FPS* and
one-click *Boost FPS*, makes every button reachable in small windows and ships the redistributable Performance pack
mods inside the mods bundle. The launcher release fixes a notification that could swallow clicks on the Mods page in a
small window, lost changes when two Mods-page operations ran at once, a stale restart request and profiles the
Minecraft Launcher saved during an install.

### VANTA Client 1.2.0

#### Added
- **Max FPS preset** (`BOOST`): Fast graphics, 5 chunks render and simulation distance, minimal particles, clouds off,
  smooth lighting and entity shadows off, entity distance 50 %, biome blend 0, mipmaps 0 and the menu background blur
  off. It is the first tab of the Performance Center's preset card, next to LOW, BALANCED, HIGH and ULTRA.
- **Boost FPS**, the primary button of the preset card and an entry of the command palette: applies Max FPS, sets the
  frame-rate limit to Unlimited and VSync off, switches the VANTA menu to a solid background without particles and
  installs the Performance pack when a member is neither loaded nor installed (only on that click; nothing is
  downloaded when the pack is present or the Modrinth integration is not available). The notification says when a
  restart is needed to load newly installed mods.
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
- **One-time offer at start.** A game that the VANTA Launcher did not start asks once per game start, at the main
  menu, *Boost your FPS?*, when Performance pack members are missing. *Install* records the pack jars that came with the bundle in
  `config/vanta/modrinth.json` (so the Installed tab manages them) and installs the missing members from Modrinth
  with the usual toasts; *Not now* turns the offer off (new setting *Settings → Performance → Offer the Performance
  pack at start*, `performance.offerPack`). Nothing is downloaded without that click; the game test never sees the
  dialog.

#### Changed
- **Presets never cap the frame rate.** Every preset only tunes render work; the frame-rate limit and VSync are set
  only by the *Frame rate limit* chooser, by Boost FPS and by profiles, which carry the choice made there. The preset
  descriptions no longer mention a cap, and the built-in *Performance* profile uses Max FPS with the limit unlimited.
- **The interface never shrinks below Minecraft's 320 x 240.** The UI scale (UI scale x large text) is lowered, never
  below 1, so the logical screen is always at least 320 x 240 px: 320 x 240 at scale 1.25 or 1.5 renders at scale 1,
  480 x 270 at 1.5 at 1.125. Layout, drawing and input use the same clamped scale, so every corner stays clickable.
- **Less drawing work per frame.** Horizontal gradients (the main menu's horizon line, vignette edges and PLAY button)
  are one native gradient each instead of one fill per column (those columns were 1 544 of the default main menu's
  2 795 fills per frame at 960 x 540); icons are rasterised once per size and replayed as rectangles (fills per icon at
  16 px: 19 to 11 on average, worst 48 to 21); text widths and styled components are cached (4096-entry LRU, cleared
  on resource reload); a clipped label is ellipsized by binary search (65 to 9 width measurements for a typical
  Modrinth description); the HUD captures only what enabled widgets show (25 to 15 bridge calls per tick for the
  default layout; HUD width measurements 98 to 70); the crosshair samples inputs once per tick and caches its
  geometry; the CPU reading is reused for 250 ms; frame times are sampled once per frame; themes are rebuilt only
  when their sources change. Pixel output is unchanged and locked by identity tests.

#### Fixed
- **Exactly 30 FPS (or a 60 / 120 FPS cap) after choosing a preset or profile in 1.1.0.** The LOW preset wrote a
  60 FPS cap and BALANCED a 120 FPS cap into Minecraft's *Max framerate* option, and the built-in profiles carried
  those caps along: *Performance* (LOW) a 60 FPS cap, *Default* (BALANCED) a 120 FPS cap. A player who picked *Low*
  or the *Performance* profile to get more frames was capped and, with a driver-forced 60 Hz VSync on top of
  Minecraft's limiter, could land at exactly 30 FPS. Presets and profiles no longer write a cap (see *Changed*); the
  built-in profiles derive their vanilla limit and VSync from their own frame-rate choice, so none of them caps the
  frame rate any more. Anyone who applied a 1.1.0 preset or profile keeps the cap it wrote in `options.txt` until they
  choose *Unlimited* (or press *Boost FPS*) once.
- The **Frame rate limit** row in *Settings → Performance* now really writes the vanilla *Max framerate* and *VSync*
  options when it is changed there or by a profile; before, only the Performance Center's own chooser did. Loading the
  settings at start never writes `options.txt`.
- In a small game window (854 x 480 at GUI scale 2, the 320 x 240 minimum, or with large text) several buttons were
  drawn but could not be clicked because their panel had run out of room: *Install*, *View on Modrinth*, *Disable*,
  *Remove* and the other actions of the Mods & Shaders detail panel, *Quit Game* and the quick-access row of the main
  menu at 640 x 360, *Vanilla options* in Settings and the HUD editor's grid and snap switches, which overlapped the
  panel toggles. The detail text now scrolls above a footer that keeps the actions on screen, the main menu picks an
  arrangement that fits the window, the Settings footer wraps, the HUD editor toolbar wraps onto two lines and the
  settings category rail scrolls when the window is too short for all entries. Every screen is now checked at 19
  window sizes, at scale 1 and with large text, for buttons that cannot be clicked or that overlap each other, and the
  Mods & Shaders screen in 15 states at every size.
- Profiles written by 1.1.0 or earlier no longer re-apply a 60 / 120 FPS cap on an upgraded install: on the first
  start, built-in profiles you never changed are refreshed from the current defaults (profile files move to schema
  version 2; renamed or edited built-ins and your own profiles keep their content), and activating any profile derives
  the vanilla frame-rate limit and VSync from the profile's own frame-rate choice. *Reset all settings* now ends at
  *Unlimited* with VSync off. If you downgrade, a 1.1.0 client ignores schema-2 profile files and recreates its
  built-ins.
- *Boost FPS* and the *Boost your FPS?* offer install only the Performance pack members that are really missing; a
  member Fabric already loaded from a jar Modrinth does not recognise is never downloaded a second time, not even as
  Iris' dependency. While a Modrinth download is still running, *Boost FPS* applies its settings, queues no second
  pack install and shows *Download still running*; the Performance Center's button waits until the install is done,
  and one *Boost applied* toast follows per click.
- A pack jar you switched off by hand (`.jar.disabled`), or one built for another Minecraft version, is no longer
  taken over into VANTA's index behind your back; *Install* then switches an identical disabled copy back on and says
  so, or downloads the current version next to it.
- The Mods & Shaders restart banner's *Restart* / *Quit* button waits until a running download has landed, so quitting
  can no longer abandon a half-finished install.
- Escape (or any other way of closing) the *Boost your FPS?* dialog counts as *Not now*, and the dialog says so.
- Cosmetics cards no longer overlap the section below or sit beyond the end of the list after switching sections in a
  small window; layouts that need a second pass now get it in the same frame.
- Confirmation, profile-name, import and save-preset dialogs follow the window after a resize, a fullscreen toggle or
  a UI-scale change instead of sitting off-screen while blocking every click; dropdown lists and the colour palette
  stay on the screen on short windows.
- Ctrl+F and other screen shortcuts no longer steal the keyboard from an open dialog or dropdown; Enter keeps
  activating the dialog's default button.
- Wrapped text no longer draws over the control below it right after a screen is built or rebuilt. A wrapped label
  reported the height of a single line until it had been laid out once, so a one-pass layout placed the next button or
  field on top of its text (visible in Cosmetics, the crosshair editor descriptions and the info banners until
  something triggered another layout). Columns, Row flex children, grids, panels, stacks, cards and scroll panels now
  measure their children against the width they are about to give them, so wrapped labels get their full height in the
  first pass, and scroll panels account for the scrollbar column in the same pass.
- The info banners of Mods & Shaders (the Iris banner) and Cosmetics measured themselves at a 240 px minimum
  regardless of the column they sit in, so in a column narrower than 240 px (the Mods list at 320 x 240 or 427 x 240)
  the banner was too short and its stacked button overlapped its text on every layout pass; they now take the width
  they are offered.

#### Privacy
- Nothing new is sent anywhere. The *Boost your FPS?* dialog and *Boost FPS* contact Modrinth only after your click,
  exactly like an *Install* on the Mods & Shaders screen; the bundled pack jars are recognised by their SHA-512 when
  you accept the offer (the same lookup an install already did for mods you added by hand).

### VANTA Launcher 1.2.0

#### Added
- For automated tests the launcher also reads `VANTA_UI_SMOKE_PAGE=<home|mods|versions|logs|settings|about>` (the
  page shown before the screenshot) and `VANTA_UI_SMOKE_SIZE=<width>x<height>` (the window size, raised to the
  960 x 600 minimum); both only together with `VANTA_UI_SMOKE_SCREENSHOT` or `VANTA_UI_SMOKE_EXIT_AFTER`.
  `./gradlew uiSmoke -PsmokePage=mods -PsmokeSize=1000x600` renders one page at one window size with fake services
  (`-PsmokeToast` shows a toast first).
- The headless UI test opens the Mods page in a 960 x 600 window and clicks the first and the last *Install* button
  through the platform's robot (the last one after scrolling, and after the toast over it was dismissed by the click).

#### Fixed
- A notification (toast) in the bottom-right corner could swallow clicks on what lay under it: in a small window
  (960 x 600) that corner holds the lower *Install* buttons of the Mods page, and because a toast stayed as long as the
  mouse rested on it, a mouse moved onto such a button kept the toast open and every click did nothing (in a
  maximised window the Install column is nowhere near that corner). A click anywhere on a toast now dismisses it, and
  the pause while the mouse is over a toast ends after 8 seconds at the latest.
- In a window narrower than 1100 px, and in any window in which the shown page is taller than the window and scrolls
  (its content then reaches the lower edge: at the 1120 x 720 default the Mods page's lowest installed switches and
  remove buttons sit in the bottom-right corner), the toasts now appear top-right, below the update banner and the
  page header, and a toast is never wider than 30 percent of the window (at most 360 px), so the Mods page's *Install*
  buttons, its header actions and the installed list's switches are no longer under a toast; on a page that fits the
  window the toasts stay bottom-right. The headless UI test checks this at 960 x 600, 1100 x 600, 1120 x 720 and
  1200 x 700.
- Mods page: removing, enabling, disabling, installing or updating mods while another install runs no longer loses
  changes. Every operation that writes `config/vanta/modrinth.json` now waits for the one before it and starts from
  the file it wrote, so a mod removed during an install stays removed and two installs started together are both
  tracked. A removal or toggle started during a long install (or during *Update all*, which keeps its turn across the
  Modrinth lookup) waits in the background until that install is done; the page shows no waiting state for it yet.
- A left-over restart request (for example *Restart game* pressed after the launcher had been closed while the game
  ran) no longer restarts the game after its next normal quit: the marker is removed before every launch and is
  consumed even once the restart limit is reached.
- *PLAY via Minecraft Launcher* / *Use with Minecraft Launcher* re-reads `launcher_profiles.json` (and the Microsoft
  Store variant) right before writing and merges only the VANTA profile, so profiles, account switches and settings
  the still-running Minecraft Launcher saved during the downloads survive; the log says when the file changed
  meanwhile. If that file is unreadable at that point, the install fails at its last step instead of overwriting the
  file with the earlier copy.
- When the Performance pack's jars are installed but `modrinth.json` cannot be written, the launcher reports the real
  counts (for example "6 mods in place, 1 skipped") with a warning that names the cause, instead of "0 mods in place".
- In-window dialogs (Remove mod, sign-in, account, update and Minecraft Launcher confirmations) no longer leave a
  window-height listener behind each time they are shown; closed dialogs are released instead of being kept for the
  whole session.
- A dismissed toast lets clicks through at once while it fades out, so a quick second click on the *Install* button
  underneath is no longer swallowed during the 120 ms fade; the headless UI test clicks 0, 10 and 100 ms after the
  dismiss.

#### Updating to 1.2.0
- Launcher 1.1.0 (and 1.0.1, 1.0.2) sees the update by itself once the release is published and offers the file that
  matches how it was installed. Launcher 1.0.0 still picks the update by system only: the `.msi` on Windows (also for
  the portable folder or a jar) and the `.tar.gz` on Linux x64 (also for a jar); portable and jar users choose *Not
  now* and download `VANTA-Launcher-1.2.0-windows-portable.zip` or the jar for their system from the release
  `launcher-v1.2.0` instead ([Installation → Updating](docs/installation.md#updating)). Launchers 1.0.0 and 1.0.1 save
  the update as `cache/updates/1.2.0-<file name>`; rename it to the release name before checking it with
  `sha256sum -c --ignore-missing SHA256SUMS.txt`.
- **Manual installations (mods bundle):** copy **all** jars from `mods/` of `vanta-client-1.2.0-mods.zip` into your
  `mods` folder, and delete older copies of Sodium, Iris, Lithium, FerriteCore and ImmediatelyFast first (Fabric
  refuses to start with two copies of one mod; Iris and Sodium belong together). EntityCulling is not in the zip: accept
  the *Boost your FPS?* offer at the main menu or install it on the *Performance pack* card of Mods & Shaders.
- If a 1.1.0 preset or profile capped your frame rate, choose *Unlimited* in the frame-rate limit chooser (or press
  *Boost FPS*) once; 1.2.0 never writes a cap by itself but does not undo one already in `options.txt`.

### CI
- **Frame-cost probe in the real game**: the headless production game test measures 300 rendered frames each with the
  VANTA HUD enabled, disabled and with the GUI hidden (frame time avg / p50 / p95 / fps, the wall time of the VANTA
  HUD and crosshair elements, drawing primitives per HUD frame) and writes `screenshots/vanta-perf-probe.json`; the
  only assertion is that the VANTA HUD element averages under 4 ms per call; it passes in CI (software renderer, no
  GPU), and the measured values are in that file of every run. The 30 FPS reports are explained by the caps above,
  not by the HUD.
- **Windowed click reproduction**: the same test opens Mods & Shaders at 854 x 480 / GUI scale 2, 1920 x 1080 / scale 4
  and 1920 x 1080 / scale 2 and clicks a result row, the detail *Install* button, the *Performance pack* Install
  button and the Shaders tab through the real mouse path; a click without effect fails the test (geometry explained
  as `REPRODUCED`, otherwise `UNEXPECTED`). The in-game Install actions are swapped for a flag, so nothing is
  downloaded in CI.
- **Performance pack resolver job**: `node --test scripts/release/` plus a live run of `performance-pack.mjs`
  against Modrinth on every push (five redistributable jars verified by size and SHA-512, EntityCulling excluded by
  licence, every licence text fetched, `performance-pack.json` schema-valid), uploaded as the `performance-pack`
  artifact. The release workflow runs the resolver before the mods bundle, prints `PERFORMANCE-PACK.txt` into the job
  summary and into the GitHub Release notes (*Performance pack in the mods bundle*).
- **Launcher**: the headless UI test clicks the Mods page's Install buttons at 960 x 600 with the platform robot and
  checks the toast position and width at 960 x 600, 1100 x 600, 1120 x 720 and 1200 x 700; `./gradlew uiSmoke`
  renders one page at one size.
- **Core**: every screen is walked for unreachable or overlapping controls at 19 window sizes, both scales and all
  Mods states; render-cost bounds pin the optimised primitive counts and pixel-identity tests lock the output.
- The Performance pack game test now picks Modrinth versions with the same rule as the in-game installer, the launcher
  and the release bundle (primary file with a SHA-512 and a safe file name; newest release, else beta, else alpha,
  else any). The rule lives in `scripts/ci/pick-version.jq`, and the release-script tests check it against the
  resolver with shared fixtures (file-name length in UTF-16 units, blank names, a trailing newline in the hash,
  trimmed and case-insensitive channel names, fractional-second dates), so the game test loads the jars the release
  ships.
- The frame probe also counts the native horizontal and rounded-horizontal gradients, so the "gradients per HUD frame"
  figure in `vanta-perf-probe.json` no longer undercounts the primary buttons and bars drawn with them.

### Release tooling
- `scripts/release/build-mods-bundle.sh` accepts every pack file name the resolver and the in-game installer accept
  (Modrinth names with spaces or parentheses such as `Iris Shaders 4.4.4.jar`) instead of aborting the release with
  "names an unsafe pack file"; path separators, `..`, leading dots or dashes, control characters and `<>"|?*` are
  still refused, and the licence-notice check copes with regex metacharacters in file names.
- `scripts/release/bump-version.mjs` prints, after its checklist, every line of `README.md`, `RELEASE.md`, the product
  READMEs, `docs/` and `website/src` that still names the previous version as a `[ ] path:line` checklist (changelog
  history, news posts and old manifests excluded); prose is never edited automatically.

### Website
- Download page: the mods bundle is described as a complete `mods/` folder (VANTA, Fabric API and the redistributable
  Performance pack mods; EntityCulling is downloaded in game) and *How to install → Manual* says to copy all jars and
  to remove older copies of the pack mods first. Features page: *New in 1.2.0* for Boost FPS and the pack in the
  bundle, the preset copy without caps. Performance page: the presets table with Max FPS and no framerate / VSync rows,
  the 30 FPS trap explained, the "not bundled" note replaced by what the 1.2.0 bundle holds. Support page: a link to
  the new troubleshooting section on 30 FPS after a 1.1.0 preset. A news post announces 1.2.0; the 1.1 post carries a
  dated update note where the bundle contents changed.

### Documentation
- [Performance Center](docs/performance.md): Max FPS column, framerate / VSync rows removed with the 60 / 30 FPS trap
  explained, the Boost FPS section, what the CI game test measures of VANTA's own frame cost under *Honest limits*.
  [Profiles](docs/profiles.md): the
  Performance profile uses Max FPS with the limit unlimited.
- [Mods & Shaders](docs/mods-and-shaders.md): the pack in the mods bundle, the EntityCulling exception, the offer at
  start. [Installation](docs/installation.md), [Fabric](docs/fabric.md), [FAQ](docs/faq.md), the documentation index
  and the README name the 1.2.0 files and say to copy all jars of the bundle.
- [Troubleshooting](docs/troubleshooting.md): *Low FPS or exactly 30 FPS after choosing a preset or profile in
  VANTA 1.1.0*, buttons that cannot be clicked in a small window (fixed in 1.2.0), and the 1.2.0 update file names.
- [Launcher](docs/launcher.md): notifications (click to dismiss, hover limit, position in small windows) and the
  `VANTA_UI_SMOKE_PAGE` / `VANTA_UI_SMOKE_SIZE` variables; `RELEASE.md` and `shared/releases/README.md` describe the
  bundle contents and the cases that stop a release on purpose.

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
