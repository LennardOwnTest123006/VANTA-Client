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
| **Clock** | system time (24 h or 12 h) or in-game time | format, show seconds |
| **Armor** | armor points and the durability of each of the four pieces | show durability, vertical/horizontal |
| **Item durability** | durability bar of the held items | show offhand, show percentage |
| **Potion effects** | active effects with colour, amplifier and remaining time (∞ for infinite) | show duration, compact |
| **Keystrokes** | W A S D, mouse buttons, space bar and sneak, lit while pressed | show mouse, show space, show CPS |
| **Memory** | used / allocated / maximum JVM memory | show percentage, show maximum |
| **CPU** | process CPU load when the JVM can report it, otherwise "n/a" | show label |
| **Entity count** | entities currently loaded around you | show label |
| **Minecraft version** | `1.21.11`, optionally the Fabric Loader version | show Fabric |
| **Crosshair** | VANTA's crosshair, always centred | configured in the crosshair customizer |

Every widget except the crosshair can be moved, scaled (0.5×–2×), made translucent, given its own background, text
and accent colours, and resized (keystrokes keeps its aspect). The HUD honours the global **HUD scale**, **HUD
opacity** and **text shadow** settings from *Settings → HUD* and the **HUD theme** from Cosmetics
(Clean, Glass, Outline, Minimal — see [Cosmetics](cosmetics.md)).

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
| Enable / disable widgets | the sidebar lists all widgets with a search field and a toggle each |
| Presets | toolbar dropdown: built-in **Default, Minimal, PvP, Streamer, Performance** plus your saved presets; **Save preset…** stores the current layout under a name you choose |
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
