---
product: client
version: 1.3.0
date: 2026-10-07
title: VANTA Client 1.3.0
minecraftVersion: 1.21.11
---

Release of VANTA Client for Minecraft Java Edition 1.21.11 (Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, Java 21). It fixes the graphics setting that "jumps back to Fancy", lets Singleplayer list the worlds of your normal Minecraft folder instead of opening *Create New World*, and removes file writes that ran on the render thread every time a VANTA screen closed. It also adds Smart Boost: a local, rule-based tuner that measures real gameplay on your PC and picks the video preset that runs smoothly. Smart Boost uses no model and no network.

## Added

- **Smart Boost** runs once per installed client version (the first install and every update) and picks Max FPS, Low, Balanced, High or Ultra for this PC. It counts only gameplay frames: a world is loaded, no screen is open, the game is not paused, the window has the focus and you moved or looked around in the last 30 s. The pause menu, an unfocused window and standing idle do not count. It starts after 20 s of gameplay in a world, never at start-up or in a menu
- How Smart Boost decides: each step discards 8 s of frames after a change and measures 25 s of gameplay (at least 600 frames); a run measures at most three steps. The target is 60 FPS when the frame rate is unlimited or VSync is on, otherwise your frame-rate limit clamped to 60–144. A step passes when the median frame rate reaches 1.1 × the target (0.95 × while a limit or VSync caps it), the 1 % low reaches 0.6 × the target and there is at most one hitch (a frame over 50 ms) per 10 s. The first step is a guess from the GPU name, the CPU threads and the Java heap; with headroom it tries one preset higher, after a fail it steps down until a preset passes or Max FPS is reached
- What Smart Boost writes: render distance, simulation distance, particles, clouds, smooth lighting, entity shadows, entity distance, biome blend, mipmap levels and, for Max FPS, the menu blur. It never changes Fast / Fancy / Fabulous, the frame-rate limit or VSync. An option you change afterwards (Video Settings, Sodium's screen, a profile, a preset, Boost FPS) is left alone by every later automatic run, and when your graphics settings were already your own before its first run (Fast, Fabulous or Custom instead of the untouched default Fancy), the automatic run changes nothing. It keeps its record in `config/vanta/smart-boost.json`
- **Smart Boost card** in the Performance Center, above the presets: the last result or the measurement in progress, the options left alone because you changed them, the threads, Java heap and GPU name the guess is based on, **Re-tune** (takes back the options you changed and measures again) and **Undo Smart Boost** (puts the options it still controls back to their earlier values and turns automatic tuning off). Both are also in the command palette
- Two switches on the card and in *Settings → Performance*: *Smart Boost: tune automatically after install/update* (on) and *Smart Boost: adjust render distance while playing* (off). The second one changes only the render distance, while it is still the one Smart Boost set: 2 chunks at a time, never below 6 chunks or above the distance the run picked, at most once every 3 minutes
- **Settings → General → Singleplayer worlds**: *Minecraft folder* (default) or *VANTA folder*. With *Minecraft folder*, Singleplayer lists, creates, loads and backs up worlds in `saves/` and `backups/` of your normal Minecraft folder: the folder the VANTA Launcher recorded in `config/vanta/minecraft-folder.json`, or `.minecraft` in a VANTA Launcher instance without that note. Nothing is moved, copied or deleted. The change takes effect at the next game start
- **Escape keeps changes in Sodium's video settings**: with the Performance pack, Sodium's screen replaces vanilla Video Settings, and on its own Sodium throws away changes that were not applied when you leave with Escape. With VANTA, Escape now applies them, like *Apply* (Alt+A), before the screen closes. The first time that screen opens in a game session, a notification says so

## Improved

- Built-in profiles change only what they are about: the options of their performance preset, the frame-rate limit and VSync of their frame-rate choice, and Building's FOV 85. Activating one no longer resets volumes, mouse sensitivity, GUI scale, FOV (except Building) or other vanilla options. Built-in profiles you never changed are refreshed once on the first start of 1.3.0; renamed or edited built-ins and your own profiles keep their content
- Activating a profile applies its graphics preset before the options that preset bundles, so render distance, clouds and particles end up as the profile says
- The frame-rate choice follows the game: after you set Max Framerate and VSync in vanilla Video Settings or Sodium, opening Settings or the Performance Center records the matching choice in `settings.json`, and a profile saved afterwards keeps that limit
- Render distance suggestions count only gameplay frames, never suggest less than 6 chunks (was 4) and stay quiet while Smart Boost controls the render distance
- The FPS widget's 1 % low no longer copies and sorts its 240 frame times every tick (same result, no allocation)

## Fixed

- **Graphics "jumps back to Fancy".** Minecraft 1.21.11 switches its graphics preset to *Custom* as soon as one option of the preset bundle changes (render distance, clouds, particles, …). VANTA showed that state as *Fancy*, its default; choosing *Fancy* there did nothing, and a profile saved in that state stored a *Fancy* you never chose. Now the *Graphics* row shows *Custom*, choosing Fast, Fancy or Fabulous applies that preset, and a profile stores *Custom*, which activation skips. Opening and closing VANTA's screens and starting the game write no Minecraft option and do not save `options.txt`
- **Singleplayer opened *Create New World* instead of your worlds** with a VANTA Launcher installation: VANTA runs in its own game folder, so Singleplayer listed only that folder's worlds, none for a new VANTA player. See *Singleplayer worlds* above
- **Short freezes when a VANTA screen closed**: every close, also Escape back into the game, rewrote all profiles, `profiles/state.json` and `cosmetics.json` on the render thread. Now only what changed is written, and nothing when nothing changed

## Notes

- Your worlds: Singleplayer now uses the Minecraft folder's worlds. The VANTA folder stays in use when the Minecraft folder has no `saves/` folder, when its `saves/` holds no world while the VANTA folder's does, when the game already runs in the Minecraft folder (manual installation) and in another launcher's instance without the VANTA Launcher's note. Worlds you made with an earlier VANTA stay in `instances/vanta-1.21.11/saves`; to see them while the Minecraft folder has worlds, choose *VANTA folder* and restart, or copy them yourself
- A world opened with mods that add blocks or items loses those blocks when vanilla Minecraft opens it later. VANTA and the Performance pack add none
- Smart Boost's first run: about 20 s after you start playing, *Smart Boost is testing settings* appears and the video options change up to three times, each step 33 s of play (8 s settling, 25 s measuring). Turn off *Smart Boost: tune automatically after install/update* before you join a world to skip it; *Undo Smart Boost* restores your earlier values. Afterwards the *Graphics* row can show *Custom*, because Smart Boost changes single options and never Fast / Fancy / Fabulous
- Smart Boost measures on your PC and picks from vanilla options. It does not change the renderer, and VANTA promises no FPS number
- Playing through the Minecraft Launcher: run *PLAY via Minecraft Launcher* once with VANTA Launcher 1.3.0, so the profile gets the tuned JVM arguments and the Minecraft folder note
- Nothing new is sent anywhere. VANTA is a legitimate client: no cheats, no combat automation, no packet manipulation and no anti-cheat bypasses
- Every file is published by the release workflow with its SHA-256 in the release manifest and in `SHA256SUMS.txt`; compare it before installing. Nothing is code-signed
