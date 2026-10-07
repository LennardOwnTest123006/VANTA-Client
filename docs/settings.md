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

## Categories

The left rail lists ten categories. Vanilla-bound entries (marked with the Minecraft icon) read and write the real
Minecraft option through the game's options system, so changing them here is identical to changing them in the
vanilla menu and they are saved to `options.txt`, not to VANTA's file.

| Category | What is in it |
| --- | --- |
| **General** | main menu: replace the title screen, background style, particles, version label; interface: notification corner and duration, UI scale (0.75–1.5), UI sounds, theme; *Reset all settings*, *Open config folder* |
| **Video** (vanilla) | graphics mode, render distance, simulation distance, max framerate, VSync, FOV, GUI scale, particles, clouds, smooth lighting, entity shadows, entity distance, biome blend, mipmap levels, view bobbing, menu blur, inactivity FPS limit, screen/FOV effect scale, glint speed/strength, autosave indicator, panorama speed, dark loading screen; *Open vanilla video settings* |
| **Audio** (vanilla) | master, music, jukebox, weather, blocks, hostile, neutral, players, ambient, voice volumes and subtitles; *Open vanilla sound settings* |
| **Controls** | zoom (enable, factor 1.5–8.0, key, smooth animation, scroll to adjust); vanilla mouse sensitivity, invert mouse, wheel sensitivity, discrete scroll, raw input, auto-jump, toggle sneak/sprint; *Open keybind manager* |
| **HUD** | HUD enabled, editor grid, snapping, global scale (0.5–2.0), global opacity (0.1–1.0), text shadow; *Open HUD editor* and the crosshair customizer |
| **Performance** | quick frame-rate limit (VSync / 60 / 120 / 144 / 240 / unlimited), frame-time graph, render-distance suggestions, apply suggestions automatically (off by default), last applied preset, offer the Performance pack at start (on by default; the *Boost your FPS?* dialog of a game the VANTA Launcher did not start); *Open Performance Center* |
| **Accessibility** | reduced motion, high contrast, larger text, reduced transparency, colour-blind palette; vanilla high contrast, text background opacity, chat opacity; *Open vanilla accessibility options* |
| **Language** | *Open vanilla language screen* (VANTA strings follow the game language), *Show translation keys* for translators |
| **Cosmetics** | badge, HUD theme, crosshair preset; *Open crosshair customizer*; link to the Cosmetics screen |
| **Privacy** | record statistics, remember servers, remember worlds; *Clear statistics* |

The exact option lists are defined in one place in the code (`VantaSettings`), so what you see is what exists.

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
