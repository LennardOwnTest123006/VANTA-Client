---
title: Settings
description: How VANTA's settings screen is organised: categories, live search, tooltips, reset to default, keyboard navigation and the settings.json file.
order: 20
category: Client
---

## Opening the settings

- Main menu → **OPTIONS** (VANTA replaces the vanilla options button with its own settings; the vanilla screens stay
  one click away).
- In a world: press **Right Shift** (the *Open VANTA settings* key) or run `/vanta menu`.
- From anywhere in VANTA: open the global search (the magnifier in the main menu, or **Ctrl+F** inside the settings
  screen), type the setting's name and press Enter — the settings screen opens scrolled to that row and highlights it.
- From [Vanta Nexus](nexus.md) (from client 1.4.0 on): *Settings → All settings* opens this screen on the *Vanta
  Nexus* category.

## Categories

The left rail lists thirteen categories (ten before client 1.4.0). Vanilla-bound entries (marked with the Minecraft icon) read and write the real
Minecraft option through the game's options system, so changing them here is identical to changing them in the
vanilla menu and they are saved to `options.txt`, not to VANTA's file.

| Category | What is in it |
| --- | --- |
| **General** | main menu: replace the title screen, background style, particles, version label; interface: notification corner and duration, UI scale (0.75–1.5), UI sounds, theme; *Singleplayer worlds* (*Minecraft folder* or *VANTA folder*, from client 1.3.0 on, see below); *Reset all settings*, *Open config folder* |
| **Video** (vanilla) | graphics mode (Fast, Fancy, Fabulous; shows *Custom* when the game reports it, see below), render distance, simulation distance, max framerate, VSync, FOV, GUI scale, particles, clouds, smooth lighting, entity shadows, entity distance, biome blend, mipmap levels, view bobbing, menu blur, inactivity FPS limit, screen/FOV effect scale, glint speed/strength, autosave indicator, panorama speed, dark loading screen; *Open vanilla video settings* |
| **Audio** (vanilla) | master, music, jukebox, weather, blocks, hostile, neutral, players, ambient, voice volumes and subtitles; *Open vanilla sound settings* |
| **Controls** | zoom (enable, factor 1.5–8.0, key, smooth animation, scroll to adjust); vanilla mouse sensitivity, invert mouse, wheel sensitivity, discrete scroll, raw input, auto-jump, toggle sneak/sprint; *Open keybind manager* |
| **HUD** | HUD enabled, editor grid, snapping, global scale (0.5–2.0), global opacity (0.1–1.0), text shadow; *Open HUD editor* and the crosshair customizer |
| **Waypoints** | from client 1.4.0 on: waypoint markers on/off, marker distance, show distance on markers, marker size ([below](#waypoints)) |
| **Performance** | quick frame-rate limit (VSync / 60 / 120 / 144 / 240 / unlimited), frame-time graph, render-distance suggestions, apply suggestions automatically (off by default), *Smart Boost: tune automatically after install/update* (on by default) and *Smart Boost: adjust render distance while playing* (off by default; both from client 1.3.0 on, see [Smart Boost](performance.md#smart-boost)), last applied preset, offer the Performance pack at start (on by default; the *Boost your FPS?* dialog of a game the VANTA Launcher did not start); *Open Performance Center* |
| **Accessibility** | reduced motion, high contrast, larger text, reduced transparency, colour-blind palette; vanilla high contrast, text background opacity, chat opacity; *Open vanilla accessibility options* |
| **Language** | *Open vanilla language screen* (VANTA strings follow the game language), *Show translation keys* for translators |
| **Cosmetics** | badge, HUD theme, crosshair preset; *Open crosshair customizer*; link to the Cosmetics screen |
| **Vanta Nexus** | from client 1.4.0 on: the assistant on/off, offer the Local AI install, idle timeout, CPU threads, keep the Local AI running, show the model's reasoning ([below](#vanta-nexus)) |
| **Privacy** | record statistics, remember servers, remember worlds; *Clear statistics* |
| **Vanta Lab** | from client 1.4.0 on: Dynamic HUD, Animated crosshair, Waypoint beams, Frame time HUD graph, Screen transitions, all off by default ([below](#vanta-lab)) |

The exact option lists are defined in one place in the code (`VantaSettings`), so what you see is what exists.

**Graphics and *Custom*.** Minecraft 1.21.11 switches its graphics preset to *Custom* as soon as one option of the
preset bundle changes (render distance, clouds, particles, …). From client 1.3.0 on the *Graphics* row shows *Custom*
then (before, VANTA showed *Fancy*). Choosing Fast, Fancy or Fabulous applies that preset with all its options;
*Custom* itself cannot be chosen, and the row goes back to what the game has. Like every vanilla-bound row, it
applies at once and is saved to `options.txt`
([Troubleshooting](troubleshooting.md#graphics-shows-custom-or-a-change-in-sodiums-video-settings-is-lost)).

**Singleplayer worlds** (from client 1.3.0 on) chooses the `saves/` folder Singleplayer uses:

| Value | Singleplayer lists, creates and loads worlds in |
| --- | --- |
| *Minecraft folder* (default) | `saves/` of your normal Minecraft folder (the one the VANTA Launcher recorded in `config/vanta/minecraft-folder.json`, else `.minecraft`); world backups go to its `backups/` |
| *VANTA folder* | `saves/` of VANTA's own game folder |

With *Minecraft folder* the VANTA folder still stays in use when the Minecraft folder has no `saves/` folder, when its
`saves/` holds no world while the VANTA folder's does, when the game already runs in the Minecraft folder, and in
another launcher's instance without the VANTA Launcher's note. Nothing is moved, copied or deleted, and the change
takes effect at the next game start (the row is marked *Restart required*). Details:
[Installation → Where your worlds are](installation.md#where-your-worlds-are).

### Vanta Nexus

The settings of the [Vanta Nexus](nexus.md) assistant and the [Local AI](local-ai.md) that runs it (from client 1.4.0
on; the same rows are in Nexus → Settings):

| Setting | Default | What it does |
| --- | --- | --- |
| **Vanta Nexus assistant** (`nexus.enabled`) | on | lets the assistant change your HUD, settings, profiles, performance presets, waypoints and Lab features when you ask. Off: the assistant section shows a banner with *Turn on*, the Local AI server is not started, and the first-start dialog does not appear |
| **Offer the Local AI install when Nexus opens** (`nexus.autoInstall`) | on | on: while the Local AI is not installed, the assistant section shows the install card with sizes and licences, and the main menu shows its one-time notice. Off: the assistant shows a one-line pointer instead and the Install button stays in Nexus → Settings. Nothing downloads until you click Install either way |
| **Stop the Local AI after idle minutes** (`nexus.idleTimeoutMinutes`) | 10 | 1 to 120; the `llama-server` process stops after this many minutes without a question and starts again on the next one, which frees its RAM while you play |
| **Local AI CPU threads** (`nexus.threads`) | 0 (automatic) | 0 to 32; 0 picks a value from your CPU: at least 2, at most 8, two fewer than the cores. A change takes effect at the next start of the server |
| **Keep the Local AI running** (`nexus.keepRunning`) | off | never stop `llama-server` for idleness while the game runs; answers come without the start-up wait, the model stays in RAM |
| **Show the model's reasoning** (`nexus.showThinking`) | off | shows a reasoning text in the transcript when the Local AI returns one. Nexus asks for answers without a thinking phase, so with the bundled model this stays empty |

The assistant itself may change settings of the categories **Video, HUD, Performance and Accessibility** only (through
its `setting.set` action, with the allowed values of each row), never keys, actions, settings that need a restart or
anything in General, Audio, Controls, Language, Cosmetics, Privacy, Waypoints, Vanta Nexus or Vanta Lab (Lab features
only through `lab.set`). See [Vanta Nexus → What the assistant can do](nexus.md#what-the-assistant-can-do).

### Waypoints

The screen markers of your [waypoints](waypoints.md) (from client 1.4.0 on):

| Setting | Default | What it does |
| --- | --- | --- |
| **Waypoint markers** (`waypoints.enabled`) | on | shows a marker on the screen for every enabled waypoint of the current world and dimension |
| **Marker distance** (`waypoints.maxMarkerDistance`) | 512 | 16 to 4096 blocks in steps of 16; waypoints farther away get no marker |
| **Show distance on markers** (`waypoints.showDistance`) | on | writes the distance in blocks (or kilometres) next to the waypoint name |
| **Marker size** (`waypoints.markerScale`) | 1.0 | 0.5 to 2.0, the scale of the markers on the screen |

The waypoints themselves are managed in Nexus → Waypoints; the file is `config/vanta/waypoints.json`.

### Vanta Lab

Optional features you switch on one by one, all off by default (from client 1.4.0 on; the same switches are the
[Vanta Lab](nexus.md#6-vanta-lab) section of Nexus). Each does exactly what its description says, nothing else:

| Feature | Setting id | What it does |
| --- | --- | --- |
| **Dynamic HUD** | `lab.dynamicHud` | the VANTA HUD fades out (over half a second) after 10 seconds without input while you are in a world and comes back on any input (key, mouse button, wheel, mouse movement, turning). While a screen is open the HUD never fades |
| **Animated crosshair** | `lab.animatedCrosshair` | the VANTA crosshair spreads by up to 4 px while you move (more at sprint speed) and pulses when you attack; it eases back within half a second. A visual effect only, like the vanilla attack indicator |
| **Waypoint beams** | `lab.waypointBeams` | a translucent column in the waypoint's colour in the world at every waypoint that has a marker ([Waypoints → Beams](waypoints.md#beams-in-vanta-lab)). The screen markers work without it |
| **Frame time HUD graph** | `lab.frametimeGraph` | makes the HUD widget *Frame time graph* available: the last 120 frame times as a bar graph with p50 and p99 labels, frames over 50 ms in the danger colour ([HUD → Frame time graph](hud.md#widgets)). With the feature off the widget is not drawn and not listed in the HUD editor |
| **Screen transitions** | `lab.screenTransitions` | VANTA screens fade and slide 14 px when they open and close, over 160 ms. *Reduced motion* in Accessibility switches the slide off; input is never blocked by the animation |

A profile stores these switches like every other VANTA setting. The assistant can switch them through its `lab.set`
action ("Turn on the dynamic HUD").

## Rows

Each row shows the title, a one-line description and a control matched to the setting's kind: toggle, slider with
value label, dropdown, colour field, text field, key capture field or an action button. Hovering shows a tooltip with
extra detail. A **modified indicator** marks rows that differ from their default and a small **reset icon** appears
on them; clicking it restores the default for that single setting. Rows that only open a vanilla screen (language,
controls, video, audio) say so.

## Search

The search field at the top filters rows across **all** categories while you type. Matching is tokenised, prefix
based and tolerant of a typo (one edit for words of five letters or more), weighted title > keywords > description,
so "crosshiar" finds the crosshair settings and "fps" finds the frame-rate limit, the FPS widget and the performance
preset. **Ctrl+F** focuses the field.

## Reset

- Per setting: the reset icon on a modified row.
- Per category: **Reset category** in the footer.
- Everything: **Reset all** in the footer (asks for confirmation) or *General → Reset all settings*.

Resetting vanilla-bound settings restores Minecraft's defaults for those options. Resetting never touches your
profiles, HUD presets or statistics.

## Keyboard navigation

| Key | Action |
| --- | --- |
| Tab / Shift+Tab | move focus between controls (a violet focus ring shows where you are) |
| ↑ / ↓ | move between rows; ← / → change sliders and dropdowns |
| Enter / Space | toggle, open a dropdown, press a button |
| Ctrl+F | focus the search field |
| Esc | close the screen (changes are already saved) |

## Saving and `settings.json`

Changes are saved immediately (debounced) to `config/vanta/settings.json` inside the game directory — when you use
the VANTA Launcher that is `<data directory>/instances/vanta-1.21.11/config/vanta/settings.json`, otherwise
`.minecraft/config/vanta/settings.json`. The file is pretty-printed JSON:

```json
{
  "schemaVersion": 1,
  "values": {
    "menu.customMainMenu": true,
    "menu.background": "violet_horizon",
    "general.uiScale": 1.0,
    "hud.globalOpacity": 1.0,
    "zoom.factor": 3.0
  }
}
```

Keys are stable setting ids. Unknown keys are ignored, missing keys fall back to defaults, and a file that cannot be
parsed is moved aside as `settings.broken-<timestamp>.json` and replaced with defaults instead of crashing the game.
Writes are atomic (temp file + rename). Vanilla-bound values are **not** in this file; they live in Minecraft's
`options.txt` as usual.

Profiles store a snapshot of these values — see [Profiles](profiles.md).
