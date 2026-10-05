---
title: Accessibility
description: UI scale, reduced motion, high contrast, larger text, reduced transparency, colour-blind palettes and keyboard navigation in VANTA's screens.
order: 28
category: Client
---

VANTA's screens are built with a small UI kit that applies these options everywhere at once; changing one takes effect
immediately in the screen you are looking at. Open them from the main menu's **ACCESSIBILITY** button or *Settings →
Accessibility* (in a world, **Right Shift** opens the settings).

## Options

| Option | Default | Effect |
| --- | --- | --- |
| **UI scale** | 1.0 | scales every VANTA screen from 0.75× to 1.5× (independent of Minecraft's GUI scale, which still applies underneath) |
| **Reduced motion** | off | disables transitions, hover animations, the staggered menu entrance and menu particles; values snap instead of animating |
| **High contrast** | off | stronger borders, brighter secondary text and an opaque focus ring |
| **Larger text** | off | increases text size in VANTA screens and tooltips |
| **Reduced transparency** | off | opaque panels instead of translucent ones; no background blur |
| **Colour-blind palette** | none | replaces the success/warning/danger colours used by badges, ping colours, conflict markers and notifications |

### Colour-blind palettes

The alternatives come from the Okabe–Ito palette, which stays distinguishable under the three common forms of colour
vision deficiency:

| Palette | Success | Warning | Danger |
| --- | --- | --- | --- |
| None (VANTA defaults) | `#3DDC97` green | `#F5B942` amber | `#FF5C7A` red |
| Deuteranopia (red–green) | `#56B4E9` sky blue | `#F0E442` yellow | `#D55E00` vermilion |
| Protanopia (red–green) | `#56B4E9` sky blue | `#F0E442` yellow | `#0072B2` blue |
| Tritanopia (blue–yellow) | `#009E73` bluish green | `#E69F00` orange | `#D55E00` vermilion |

Status is never conveyed by colour alone: icons and text labels accompany every coloured state.

### Vanilla options in the same place

The Accessibility screen also exposes Minecraft's **high contrast** (the vanilla high-contrast resource pack), **text
background opacity** and **chat opacity**, and has an *Open Minecraft accessibility options* row for everything else
(narrator, subtitles, auto-jump, distortion effects, …).

## Keyboard navigation

Every VANTA screen is fully operable without a mouse:

| Key | Action |
| --- | --- |
| Tab / Shift+Tab | next / previous control; a violet focus ring shows the focused control |
| ↑ ↓ ← → | move within lists and rows, adjust sliders, change dropdown values |
| Enter / Space | activate buttons and toggles, open dropdowns |
| Esc | close dropdowns and dialogs, then the screen |
| Ctrl+F | focus the search field in Settings and the keybind manager |

The HUD editor has its own shortcuts for nudging and editing widgets — see [HUD editor → Keyboard shortcuts](hud.md#keyboard-shortcuts).

## Text and contrast

VANTA uses Inter for interface text and Space Grotesk for titles, both chosen for legibility at small sizes, and keeps
text contrast at 4.5:1 or better against its graphite panels (the *high contrast* option raises it further). The
Minecraft font remains available for the HUD widgets so they match the rest of the game when you prefer that.

## Profiles

Accessibility options are ordinary settings, so a [profile](profiles.md) can carry a complete accessible configuration.

## Feedback

If an option is missing or something is hard to use with a screen reader, keyboard or at a particular size, please
open an issue; accessibility reports are treated as bugs, not feature requests.
