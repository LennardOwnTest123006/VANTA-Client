---
title: Cosmetics
description: UI themes, menu backgrounds, HUD themes, menu particles, badges and crosshair presets — all purely visual and free — plus the cosmetic pack format.
order: 26
category: Client
---

Cosmetics change how VANTA looks. They have **no effect on gameplay**, they are **all free**, and nothing is unlocked,
bought or tracked. Open the Cosmetics screen from the main menu's Cosmetics button or *Settings → Cosmetics* (in a
world, **Right Shift** opens the settings).

## UI themes

A theme recolours VANTA's own screens (not Minecraft's). Applying one re-themes the current screen instantly.

| Theme | Character |
| --- | --- |
| **VANTA Dark** (default) | the original palette: black and graphite with violet → blue accents |
| **Midnight** | blue accents |
| **Aurora** | green and cyan accents |
| **Ember** | orange and amber accents |
| **Graphite** | monochrome, light grey accents |

Themes only override accent and state colours; backgrounds, text contrast and spacing stay the same so every theme
remains readable. The active theme id is stored in *Settings → General → Theme* and inside profiles.

## Menu backgrounds

For the VANTA main menu (*Settings → General → Background*):

| Background | Look |
| --- | --- |
| **Gradient Dark** | deep black to graphite with a subtle vignette |
| **Graphite Grid** | graphite with a faint perspective grid |
| **Violet Horizon** (default) | dark sky with a soft violet → blue glow band |
| **Vanilla Panorama** | Minecraft's rotating panorama with a dark vignette |
| **Solid** | flat black |

## Menu particles

An ambient particle layer for the main menu: **None**, **Embers** (default) or **Dust**. Particles are deterministic,
cheap and disabled automatically when *Accessibility → Reduced motion* is on.

## HUD themes

The style of every [HUD widget](hud.md) at once:

| HUD theme | Style |
| --- | --- |
| **Clean** (default) | solid translucent panels, rounded corners |
| **Glass** | lighter, more translucent panels with a soft border |
| **Outline** | almost transparent panels with a violet outline |
| **Minimal** | text only, no panels |

Per-widget background, text and accent colours still apply on top of the theme.

## Badges

A small visual label shown next to your profile name in the main menu: **None**,
**Founder** ✦, **Contributor** ⬡ or **Supporter** ♥. Badges are labels you choose for yourself; they are not
verified, not visible to other players and grant nothing. The "Equipped" state only means it is selected.

## Crosshair presets

Default, Dot, Thin, Bold, Circle and Precision; the gallery links to the [crosshair customizer](hud.md#the-crosshair-customizer)
where every value can be changed.

## Cosmetic packs

Cosmetics are built so that themes can be added later as **data, never code**. A pack is a JSON file in
`config/vanta/cosmetics/`:

```json
{
  "schemaVersion": 1,
  "id": "my-pack",
  "name": "My Pack",
  "author": "Your name",
  "themes": [
    { "id": "my-theme", "name": "My Theme", "tokens": { "accent.violet": "#FF8844", "accent.blue": "#FFB347" } }
  ]
}
```

Rules enforced on load: `id` is 2–32 lower-case letters, digits or hyphens; `name` and `author` up to 48 characters;
1–16 themes per pack; a theme overrides colour tokens only. Invalid packs are skipped with a message in the game log,
never with a crash. The **Installed packs** section of the Cosmetics screen lists the packs it found and shows an
honest empty state when there are none — there is no store, no download and no "coming soon" list.

## What cosmetics never do

Cosmetics never change hitboxes, reach, visibility of other players, item or block appearance for anyone else, or
anything a server could observe. They are files on your computer that change pixels on your screen.
