---
title: Keybinds
description: VANTA's own key bindings and their defaults, the keybind manager with search, rebinding, reset and conflict detection, and the zoom key.
order: 24
category: Client
---

## VANTA's keys

VANTA registers its own key mappings in the **VANTA** category of Minecraft's controls, so they appear in the vanilla
controls screen as well and are saved in `options.txt` like every other binding.

| Binding | Default | Action |
| --- | --- | --- |
| Open VANTA menu | **Right Shift** | opens the VANTA menu (in a world) with Settings, HUD editor, Performance, Profiles, Statistics and more |
| Toggle HUD | unbound | shows/hides the VANTA HUD without touching the vanilla HUD (F1) |
| HUD editor | unbound | opens the [HUD editor](hud.md) directly |
| Performance Center | unbound | opens the [Performance Center](performance.md) |
| Zoom | **C** | hold to zoom (see below) |
| HUD-free screenshot | unbound | takes a screenshot with the VANTA HUD hidden for that frame |

Unbound keys do nothing until you assign them.

## The keybind manager

Open it from *Settings → Controls → Open keybind manager*, the global search, or `/vanta menu` → Keybinds. It
shows **every** key binding the game knows — VANTA's first, then the vanilla categories (Movement, Gameplay,
Inventory, Multiplayer, Miscellaneous, …) and those of other installed mods.

| Element | Behaviour |
| --- | --- |
| Search field | filters bindings by name across all categories |
| Key field | click it, then press the key or mouse button you want. **Esc** cancels, **Backspace** or **Delete** unbinds |
| Reset icon | appears when a binding differs from its default; restores the default for that binding |
| Conflict icon | a warning next to bindings that share a key; the tooltip lists the others |
| Conflict banner | "N conflicts" at the top with a **Show only conflicts** filter |
| Reset all | restores every default after a confirmation |
| Open vanilla controls | the vanilla key binding screen, for completeness |

Changes are written immediately through Minecraft's key mapping system — the same code the vanilla controls screen
uses — so there is nothing to save and nothing VANTA-specific is stored for vanilla bindings. Conflicts are
recomputed after every change.

### Conflict detection

Two or more bindings that are bound to the same key form a conflict group. Unbound keys never conflict. Each group
has a severity: **high** when the bindings belong to the same category or are both vanilla bindings (the game will
trigger both at once), **medium** when a mod's key overlaps a key from another category. The banner lists high
conflicts first. Minecraft itself allows duplicate keys, so VANTA only warns; it never changes a binding on its own.

## Zoom

Hold the zoom key (**C** by default) to narrow the field of view, like a spyglass without the item. The zoom is a
camera FOV change only; it does not affect reach, aim or anything a server could see.

| Setting (Settings → Controls) | Default | Range |
| --- | --- | --- |
| Zoom enabled | on | — |
| Zoom factor | 3.0× | 1.5×–8.0× in 0.5 steps |
| Zoom key | C | mirrors the `Zoom` key mapping |
| Smooth zoom | on | animates the transition |
| Scroll to adjust | on | the mouse wheel changes the zoom level while zooming |

Your normal FOV is restored the moment you release the key.

## Profiles and keys

A [profile](profiles.md) can store overrides for **VANTA's** key mappings only. Activating a profile applies those
overrides through the key mapping system; vanilla and other mods' bindings are never part of a profile.
