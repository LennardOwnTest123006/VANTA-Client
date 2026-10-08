---
title: Waypoints
description: Saved places per world (client 1.4.0): the waypoints file and per-world keys, screen markers and settings, the beams of Vanta Lab, the Waypoints section of Nexus, the assistant's waypoint actions.
order: 31
category: Client
---

A **waypoint** (from client 1.4.0 on) is a named position you save in a world: your base, a farm, a portal, a
resource. VANTA draws a marker for it on the screen while you play, lists it in [Vanta Nexus](nexus.md#5-waypoints) and
lets the assistant add, remove and toggle it. Waypoints are your own notes about your own worlds: they show nothing
the game does not already tell you (your position and the direction you face), nothing about other players, and
nothing is sent anywhere.

## The waypoint store

Waypoints live in `config/vanta/waypoints.json` in the game folder (with the VANTA Launcher:
`<data directory>/instances/vanta-1.21.11/config/vanta/waypoints.json`), one file for every world:

```json
{
  "schemaVersion": 1,
  "waypoints": [
    {
      "id": "wp1k2x3y4z-1",
      "name": "Home",
      "worldKey": "sp:New World",
      "dimension": "minecraft:overworld",
      "x": 128.5, "y": 64.0, "z": -42.5,
      "color": "#FF7C5CFF",
      "category": "Home",
      "enabled": true,
      "createdAt": 1791460800000
    }
  ]
}
```

- **Per-world keys.** Every waypoint belongs to the world it was made in: `sp:<level folder name>` for a singleplayer
  world (the folder in `saves/`) and `mp:<server address>` for a server (the address you joined, lower-cased). The
  current world's key comes from the game; outside a world there is no key, and nothing
  can be added. A waypoint also stores its **dimension** (`minecraft:overworld`, `minecraft:the_nether`,
  `minecraft:the_end` or a mod's) and shows its marker only there.
- **Names** are 1 to 48 characters, unique per world (ignoring case). Control characters are removed and whitespace
  collapsed; an empty name becomes *Waypoint*.
- **Categories** are free text up to 24 characters; the dialog and the assistant suggest *Home*, *Base*, *Farm*,
  *Portal*, *Resource* and *Other* (the default) plus the categories you already used.
- **Colours**: eight marker colours to pick from; a new waypoint takes the next one in the palette.
- At most **500** waypoints per file. The file is written atomically when something changed (and when the game saves
  VANTA's files), and an unreadable file is moved aside as `waypoints.broken-<timestamp>.json` instead of crashing the
  game, like every other VANTA file.

Profiles do not contain waypoints: switching a profile never changes them.

## Markers on the screen

For every **enabled** waypoint of the current world and dimension within the marker distance, the HUD draws a small
filled diamond in the waypoint's colour at the waypoint's position (one block above it, so it sits on the block you
saved) and, on a dark pill under it, the name and the distance: `Home · 120 m`, with distances of a kilometre and more
as `1.2 km`. Nearer markers are drawn over farther ones. Markers are projected with the game's own camera, so they
follow the world while you turn (with a shader pack that replaces the game's projection, a marker can sit off its
block; VANTA does not correct for that).

Nothing is drawn while *Waypoint markers* is off, outside a world, while the vanilla GUI is hidden (F1), while the F3
debug screen is open, during a HUD-free screenshot and while a VANTA screen is open. The markers have their own
switch and ignore the *Toggle HUD* key and *Settings → HUD → HUD enabled*, which hide the VANTA widgets only.

Settings, in *Settings → Waypoints*:

| Setting | Default | Range |
| --- | --- | --- |
| Waypoint markers | on | the switch for all markers |
| Marker distance | 512 blocks | 16 to 4096 in steps of 16; waypoints farther away get no marker |
| Show distance on markers | on | writes the distance next to the name |
| Marker size | 1.0 | 0.5 to 2.0 |

## Beams in Vanta Lab

*Vanta Lab → Waypoint beams* (off by default, [Settings → Vanta Lab](settings.md#vanta-lab)) adds a thin translucent
column in the waypoint's colour from far below to far above the waypoint (y −64 to 320, about 0.3 blocks wide) for
every waypoint that has a marker, drawn in the world after the terrain. The screen markers work without it. Beams
follow the same rules as the markers (enabled, current world and dimension, marker distance) and, like everything in
Vanta Lab, do exactly that and nothing else.

## The Waypoints section of Vanta Nexus

Open [Vanta Nexus](nexus.md) (main menu, **N**, `/vanta nexus`) and choose **Waypoints**:

- The list shows the waypoints of the current world: a colour dot, the name, the category, the live distance to you
  (*n/a* in another dimension or world), an **enable** switch, **edit** and **delete** (asks first; *The waypoint and
  its marker are removed. Nothing in the world changes*). The row's tooltip names the coordinates and the dimension.
- **Search** filters by name or category while you type. **Sort** by *Name*, *Distance* or *Newest first*.
- **All worlds** shows every waypoint you saved, grouped by world (*Singleplayer: <level>*, *Server: <address>*), with
  the current world marked.
- **Add waypoint** opens the dialog with **Name**, **X / Y / Z** prefilled with your position, **Category** (with the
  suggestions) and the **Colour** swatches; a blank name, a name already used in this world or coordinates that are
  not numbers are refused with a message. The notification *Waypoint added: "<name>" is saved for this world* confirms
  it. **Edit** opens the same dialog for an existing waypoint.
- Outside a world the section says *No world open*: join a world to see and add its waypoints, or switch to *All
  worlds*.

## The assistant's waypoint actions

The [Vanta Nexus assistant](nexus.md#what-the-assistant-can-do) can save, remove and toggle waypoints in the current
world, and nothing else with them:

| Action | Fields | Example |
| --- | --- | --- |
| `waypoint.add` | `name`; optional `x`, `y`, `z` (your position when left out) and `category` | "Add a waypoint called Farm here", "Save a Portal waypoint at 100, 70, -200" |
| `waypoint.remove` | `name` | "Remove the waypoint Farm" |
| `waypoint.toggle` | `name`, `enabled` | "Hide the Home waypoint", "Show the Portal waypoint again" |

The names the model may use come from the live list of this world, so it cannot address a waypoint that does not
exist; outside a world the actions are refused with *Not available right now*. A waypoint the assistant added is
removed again by **Undo**, a toggled one is switched back. Waypoints added by the assistant take the next palette
colour and the current dimension, like the dialog.
