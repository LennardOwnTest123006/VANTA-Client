---
title: HUD and crosshair
description: Every HUD widget and what it shows, the HUD editor with drag, resize, scale, opacity, colours, snapping, presets and keyboard shortcuts, and the crosshair customizer.
order: 21
category: Client
---

VANTA's HUD is a set of small, movable widgets drawn over the game. Every widget shows information the game already
exposes (the F3 screen, your inventory, your effects, your own input); none shows anything about other players.
The HUD is drawn after the vanilla chat and hides together with the vanilla HUD (F1) and while the F3 debug screen
is open.

## Widgets

| Widget | Shows | Options |
| --- | --- | --- |
| **FPS** | current frames per second, optionally the frame time | show label, show frame time |
| **Ping** | your latency to the server, colour-coded; "offline" in singleplayer | show label |
| **Coordinates** | X Y Z of your position | decimals (0–2), show dimension, single line |
| **Direction** | cardinal direction you face, yaw | show yaw, show axis (+X/−Z) |
| **Biome** | the biome you are standing in | show namespace |
| **Server** | server name and address; hide the address when streaming | show address |
| **CPS** | clicks per second, left and right button | show right button, show label |
| **Clock** | system time or in-game time, both in 12-hour time with AM/PM (for example `2:32 PM`) | format, show seconds (system time) |
| **Armor** | armor points and the durability of each of the four pieces | show durability, vertical/horizontal |
| **Item durability** | durability bar of the held items | show offhand, show percentage |
| **Potion effects** | active effects with colour, amplifier and remaining time (∞ for infinite) | show duration, compact |
| **Keystrokes** | W A S D, mouse buttons, space bar and sneak, lit while pressed | show mouse, show space, show CPS |
| **Memory** | used / allocated / maximum JVM memory | show percentage, show maximum |
| **CPU** | process CPU load when the JVM can report it, otherwise "n/a" | show label |
| **Entity count** | entities currently loaded around you | show label |
| **Minecraft version** | `1.21.11`, optionally the Fabric Loader version | show Fabric |
| **Frame time graph** (Vanta Lab, from client 1.4.0 on) | the last 120 frame times as a bar graph, frames over 50 ms in the danger colour, with p50 and p99 labels; "n/a" without history | show p50 and p99 labels. Available only while *Vanta Lab → Frame time HUD graph* is on (see below) |
| **Crosshair** | VANTA's crosshair, always centred | configured in the crosshair customizer |

Every widget except the crosshair can be moved, scaled (0.5×–2×), made translucent, given its own background, text
and accent colours, and resized (keystrokes keeps its aspect). The HUD honours the global **HUD scale**, **HUD
opacity** and **text shadow** settings from *Settings → HUD* and the **HUD theme** from Cosmetics
(Clean, Glass, Outline, Minimal — see [Cosmetics](cosmetics.md)).

**The Frame time graph** is a [Vanta Lab](nexus.md#6-vanta-lab) widget. While *Frame time HUD graph* is off (the
default) it is not drawn, not listed in the HUD editor's widget list and not offered to the Nexus assistant, and the
Nexus presets hide it like every widget outside their set; a layout that already contains it keeps it, and it comes
back when you switch the feature on. The frame times are collected only while the widget is enabled. With *Vanta Lab →
Dynamic HUD* on, the whole HUD fades out after 10 seconds without input in a world and returns on any input
([Settings → Vanta Lab](settings.md#vanta-lab)).

## The HUD editor

Open it from *Settings → HUD → Open HUD editor*, the HUD editor key (unbound by default, see [Keybinds](keybinds.md)),
the main menu's HUD button, or `/vanta hud`. The editor dims the game and shows every enabled widget live; outside a
world it uses clearly labelled sample data.

### Controls

| Control | How |
| --- | --- |
| Select | click a widget; the selection shows an outline and eight resize handles where resizing is supported |
| Move | drag. Widgets snap to a **2 px grid** and to the screen edges and other widgets within **4 px**; turn snapping off with the toolbar toggle or *Settings → HUD → Snap* |
| Resize | drag a handle (minimum 8 px) |
| Anchor | the 3×3 anchor picker in the inspector. A widget is positioned relative to its anchor, so a widget anchored top-right keeps hugging the corner at any window size or GUI scale |
| Offset X / Y | exact position from the anchor in GUI pixels |
| Scale / opacity | sliders in the inspector |
| Colours | background, text and accent colour fields (hex with alpha, swatch palette) |
| Widget options | the per-widget options from the table above |
| Enable / disable widgets | the sidebar lists all widgets with a search field and a toggle each (the Frame time graph only while its Vanta Lab feature is on) |
| Presets | toolbar dropdown: built-in **Default, Minimal, PvP, Streamer, Performance** plus your saved presets; **Save preset…** stores the current layout under a name you choose. From client 1.4.0 on the same presets are the **layouts** of the [HUD Designer in Vanta Nexus](#the-hud-designer-in-vanta-nexus) |
| Reset layout | restores the Default preset |
| Undo / redo | toolbar buttons or Ctrl+Z / Ctrl+Y, 50 steps deep |
| Grid | toggle the grid overlay (also *Settings → HUD → Editor grid*) |
| Done / Esc | closes the editor and saves the layout |

On small windows (GUI scale 3 on a small screen) the sidebar and inspector collapse into toggle buttons.

### Keyboard shortcuts

| Keys | Action |
| --- | --- |
| ← ↑ → ↓ | nudge the selected widget by 1 px |
| Shift + arrows | nudge by 10 px |
| Delete | disable the selected widget |
| Ctrl+D | duplicate the selected widget |
| Ctrl+Z / Ctrl+Y | undo / redo |
| Tab / Shift+Tab | move focus through the toolbar and inspector controls |
| Esc | close and save |

### Where layouts are stored

- The live layout: `config/vanta/hud/layout.json`.
- Your presets: `config/vanta/hud/presets/<name>.json` (file names are sanitised to `a-z0-9-_`).
- Built-in presets ship inside the mod and cannot be overwritten; saving a modified built-in preset creates a new one.

Layouts are JSON documents validated by the shared schema `hud-preset.schema.json`; each widget has `id`, `type`,
`enabled`, `anchor`, `offsetX`, `offsetY`, `scale`, `opacity`, `width`, `height`, `background`, `backgroundColor`,
`textColor`, `accentColor` and `props`. Profiles embed a copy of the layout — see [Profiles](profiles.md).

## The HUD Designer in Vanta Nexus

From client 1.4.0 on, [Vanta Nexus → HUD Designer](nexus.md#2-hud-designer) is a second front for the same layouts
and the home of the six **Nexus presets**. It does not replace the editor: *Open editor* opens the editor above,
which remains the place to move, resize, scale and set the opacity of single widgets.

- **Presets**: six chips, *Minimal*, *PvP*, *Recording*, *Survival*, *Building*, *Full*. A chip applies the preset to
  your live HUD through the same code the assistant's `hud.preset` action uses, as one undoable change: the widgets
  of the set are enabled (and added at their default place when your layout does not have them) with the preset's
  scale, every other widget is hidden, the crosshair stays, and the positions of widgets you already placed are kept.
- **Layouts**: the built-in presets (*Default*, *Minimal*, *PvP*, *Streamer*, *Performance*) and your saved ones from
  `config/vanta/hud/presets/`, with *Load*, *Duplicate*, *Delete* (your own only, asks first), *Save current* and *New
  layout* (a preset applied and saved under a name). *Load* is the assistant's `hud.layout.load`, *Save current* its
  `hud.layout.save`.
- The assistant can also change single widgets (`hud.set`: visible, anchor, position as fractions of the screen,
  scale 0.5 to 2.0, opacity 0.1 to 1.0) and show exactly a set of widgets (`hud.only`); everything it may do is listed
  in [Vanta Nexus → What the assistant can do](nexus.md#what-the-assistant-can-do), and **Undo** takes a change back.

### Nexus presets

| Preset | Widgets | Scale |
| --- | --- | --- |
| **Minimal** | FPS, Coordinates | 1.0 |
| **PvP** | FPS, Ping, CPS, Keystrokes, Armor, Item durability, Potion effects | 1.0 |
| **Recording** | FPS, Clock, Coordinates, Direction, Keystrokes, Minecraft version (no Server widget, so no server address on a recording) | 0.9 |
| **Survival** | FPS, Coordinates, Direction, Biome, Clock, Armor, Item durability, Potion effects | 1.0 |
| **Building** | Coordinates, Direction, Biome, Clock | 0.9 |
| **Full** | every widget | 1.0 |

The crosshair is always kept; the Frame time graph is never added by a preset (it belongs to Vanta Lab). The built-in
profiles *PvP*, *Survival*, *Building*, *Recording* and *Minimal* carry the matching preset applied to the *Default*
layout, so a profile and the assistant produce the same HUD ([Profiles → Built-in profiles](profiles.md#built-in-profiles)).
The Nexus presets are not the same thing as the editor's built-in presets of the same name (*Minimal* and *PvP* exist in
both): a Nexus preset is a widget set applied to your current layout, an editor preset is a complete stored layout.

## The crosshair customizer

Open it from *Settings → HUD* or *Settings → Cosmetics → Open crosshair customizer*. A live preview shows the
crosshair on dark, light and noisy sample backgrounds while you adjust:

| Field | Range |
| --- | --- |
| Shape | cross, dot, circle, square, chevron, plus-dot |
| Size | 1–32 px (arm length, radius or dot size) |
| Thickness | 1–8 px |
| Gap | 0–16 px between the centre and the arms |
| Outline | on/off, 1–3 px, own colour |
| Opacity | 0–100 % |
| Colour / outline colour | hex with alpha |
| Dynamic | widens the gap while you move or attack (visual only, like the vanilla attack indicator) |
| Hide in third person | hides the crosshair in F5 views |

**Presets**: Default (vanilla-like white cross, 1 px, black outline), Dot, Thin, Bold, Circle, Precision. **Use
vanilla crosshair** hands the centre back to Minecraft's own crosshair texture. The style is stored in
`config/vanta/crosshair.json` and inside profiles.

The crosshair is a drawing. It does not aim, lock, highlight entities or react to anything other than your own
movement and attack animation.
