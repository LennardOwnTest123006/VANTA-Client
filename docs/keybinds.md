---
title: Keybinds
description: VANTA's own key bindings and their defaults, the keybind manager with search, rebinding, reset and conflict detection, and the zoom key.
order: 25
category: Client
---

## VANTA's keys

VANTA registers its own key mappings in the **VANTA** category of Minecraft's controls, so they appear in the vanilla
controls screen as well and are saved in `options.txt` like every other binding.

| Binding | Default | Action |
| --- | --- | --- |
| Open VANTA settings | **Right Shift** | opens the VANTA settings in a world; their categories link to the HUD editor, the Performance Center, the keybind manager, cosmetics, accessibility and statistics |
| Toggle HUD | unbound | shows/hides the VANTA HUD without touching the vanilla HUD (F1) |
| HUD editor | unbound | opens the [HUD editor](hud.md) directly |
| Performance Center | unbound | opens the [Performance Center](performance.md) |
| Zoom | **C** | hold to zoom (see below); shares C with vanilla's *Save Hotbar Activator* |
| HUD-free screenshot | unbound | takes a screenshot with the VANTA HUD hidden for that frame |

Unbound keys do nothing until you assign them. The keys that open a screen react in a world when no other screen is
open; `/vanta menu` opens the VANTA settings as well.

## The keybind manager

Open it from *Settings → Controls → Open keybind manager* (in a world, **Right Shift** or `/vanta menu` opens the
settings) or the global search. It shows **every** key binding the game knows — VANTA's first, then the vanilla categories (Movement, Gameplay,
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

**Conflicts you will see out of the box.** Even with every key at its default the manager already lists several
conflicts. Most of them are between vanilla bindings that share a default key; the automated test capture of VANTA
1.0.0 (only VANTA and Fabric API installed, all keys at their defaults) shows 14 conflicts involving 30 actions and
flags, among others, *Strafe Left* (A), *Walk Backward* (S), *Strafe Right* (D) and *Pick Block* (middle mouse
button). Those vanilla-only conflicts come from Minecraft's own default keys and behave exactly as in the game
without VANTA, so you can ignore them even though the banner suggests rebinding one of each pair. Other mods can add
more. The only conflict
VANTA adds is **Zoom** on C: vanilla's **Save Hotbar Activator** (Creative Mode category) also defaults to C, so the
manager lists Zoom and Save Hotbar Activator in the C conflict. See [Zoom](#zoom).

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

**C is also a vanilla key.** Vanilla Minecraft binds C to *Save Hotbar Activator*: in Creative mode, holding it and
pressing a number key saves your hotbar (in Survival the binding does nothing). VANTA keeps C as the default zoom key
because that is what players expect, so in Creative mode holding C zooms, and pressing a number key while you hold it
also saves the hotbar. The keybind manager lists Zoom and Save Hotbar Activator in the C conflict (next to the
vanilla-only conflicts described under [Conflict detection](#conflict-detection)). If you use saved hotbars, rebind
either one in *Options → Controls → Key Binds* (VANTA's keys are in the VANTA category) or in the keybind manager
(*Settings → Controls → Open keybind manager*). The zoom key setting in *Settings → Controls* changes the same
binding.

## Profiles and keys

A [profile](profiles.md) can store overrides for **VANTA's** key mappings only. Activating a profile applies those
overrides through the key mapping system; vanilla and other mods' bindings are never part of a profile.
