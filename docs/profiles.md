---
title: Profiles
description: Built-in profiles, creating and duplicating profiles, what a profile contains, exporting and importing JSON safely.
order: 22
category: Client
---

A profile is a complete, switchable configuration: your settings, HUD layout, VANTA key overrides, crosshair style and
cosmetics. Switching profiles changes all of them at once, which is handy when you alternate between building,
PvP and recording.

## Built-in profiles

Five profiles are created on first run. They are ordinary profiles afterwards: rename, change, duplicate or delete
them (except the last remaining one).

| Profile | HUD preset | Performance preset | Crosshair | Other differences from defaults |
| --- | --- | --- | --- | --- |
| **Default** | Default | BALANCED | Default | — |
| **PvP** | PvP | HIGH | Bold | frame rate unlimited, menu particles off, HUD text shadow on |
| **Building** | Minimal | ULTRA | Thin | FOV 85, HUD opacity 80 % |
| **Performance** | Performance | LOW | Default | solid menu background, particles off, no text shadow, frame rate limited to 60 |
| **Recording** | Streamer | HIGH | Dot | HUD opacity 85 %, shorter notifications (2.5 s), servers not recorded in statistics, version label hidden |

The performance preset inside a profile is applied through the vanilla options when the profile is activated, like
pressing *Apply* in the [Performance Center](performance.md).

## The Profiles screen

Open it from the main menu's Profiles button, `/vanta profiles`, or the in-game VANTA menu. Each card shows the
icon, name, an **Active** badge, a summary (HUD preset, performance preset, crosshair, last change) and these actions:

| Action | Effect |
| --- | --- |
| **Activate** | applies the profile to settings, HUD, key overrides, crosshair and cosmetics; shows a "Profile loaded" notification |
| **Duplicate** | copies the profile under a new name |
| **Rename** | inline; names are 1–64 characters |
| **Delete** | asks for confirmation; the last profile cannot be deleted, and deleting the active one switches to another first |
| **Export** | writes `config/vanta/exports/vanta-profile-<name>.json` and copies the same JSON to the clipboard; the notification shows the path |
| **Import** | from the clipboard, or from a file in `config/vanta/imports/` |
| **Create from current settings** | a new profile from whatever is active right now |

When you change settings while a profile is active, a banner offers **Save current to active profile**; until you do,
the profile keeps its stored values and the live settings differ from it.

## What a profile contains

Profiles are JSON files in `config/vanta/profiles/<id>.json` (`state.json` next to them remembers which one is active):

```json
{
  "schemaVersion": 1,
  "id": "pvp",
  "name": "PvP",
  "icon": "crosshair",
  "createdAt": 1791187200000,
  "updatedAt": 1791187200000,
  "settings": { "hud.globalScale": 1.0, "performance.perfPreset": "high", "video.renderDistance": 16 },
  "hud": { "schemaVersion": 1, "widgets": [ { "id": "fps", "type": "fps", "anchor": "top_left", "offsetX": 4, "offsetY": 4 } ] },
  "keybinds": { "key.vanta.zoom": "keyboard:67:key.keyboard.c" },
  "crosshair": { "shape": "cross", "size": 6, "thickness": 2, "gap": 2, "outline": true, "color": "#FFFFFFFF" },
  "cosmetics": { "theme": "vanta-dark", "menuBackground": "violet_horizon", "menuParticles": "none", "hudTheme": "clean", "badge": "none", "crosshairPreset": "bold" }
}
```

- `settings` is a snapshot of VANTA setting ids to values, including the vanilla-bound ones (so a profile can carry
  a render distance or FOV).
- `keybinds` only contains **VANTA's own** key mappings (`key.vanta.*`); vanilla key bindings are never stored in or
  changed by a profile.
- The full structure is documented by `shared/schemas/profile.schema.json` in the repository.

## Exporting and importing

**Export** produces a self-contained file you can share. It contains no paths, no account data and no statistics.

**Import** accepts a file from `config/vanta/imports/` (the dialog lists that folder, explains where to drop files
and has a *Refresh* button) or the JSON currently on your clipboard. Imported profiles get a
fresh id derived from the file name and never overwrite an existing profile.

### Safety of imports

Profiles are data, not code, and the importer treats them as untrusted input:

- files larger than **1 MiB** are rejected;
- the JSON must be an object with a supported `schemaVersion` (newer versions are refused with a clear message);
- the stored `id` is ignored and the file name is sanitised (`a-z`, `0-9`, `-`, `_`; no path separators, no leading dots);
- `name` is cut to 64 characters, `icon` must be a short identifier (unknown icons fall back to the default);
- at most 512 settings and 64 key bindings; setting ids up to 128 characters; string values up to 1024 characters;
  only primitive values are accepted;
- the HUD layout, crosshair and cosmetics are parsed through the same tolerant readers as the built-in files, with
  every number clamped to its valid range;
- unknown keys are ignored. Nothing in a profile can run code, change files outside `config/vanta/` or alter vanilla
  key bindings.

If an import fails, the dialog tells you why (not JSON, too large, unsupported schema, invalid field).
