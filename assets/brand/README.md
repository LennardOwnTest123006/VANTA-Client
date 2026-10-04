# VANTA brand guidelines

Everything in this folder is original VANTA artwork. It is **not** covered by the MIT license of the code: the
VANTA name, wordmark and mark may not be used to represent other software (see `LICENSE`). Minecraft is a trademark
of Mojang AB / Microsoft; VANTA is not affiliated with or endorsed by them and never uses their assets.

## The mark

The VANTA mark is a geometric **V** built from two sharp, smooth diagonals that meet in a single point. Where the two
strokes merge, a small **skewed diamond is cut through the shape**, so the inside reads as a lightning bolt or a
diamond facet. It is pure geometry: no text, no gradients required, legible from 16 px to print.

### Construction (64-unit grid, `vanta-mark.svg`)

```
outer V      M 5 9  H 19.5  L 32 36.4  L 44.5 9  H 59  L 32 58  Z
diamond cut  M 32 31.6  L 36.6 40.2  L 33.2 51.6  L 28.4 42.2  Z   (fill-rule: evenodd)
```

- The V spans 54 units horizontally (5 → 59) and 49 vertically (9 → 58); the strokes are 14.5 units wide at the
  top and taper to the apex at (32, 58).
- The inner notch reaches up to y = 36.4, giving the arms a crisp "cut" instead of a rounded join.
- The diamond is deliberately **skewed** (its right edge leans) so it reads as energy rather than a static hole.
  Do not straighten it, do not change its size relative to the V.
- The mark is filled with the accent gradient (`#9B82FF` → `#4F8DFF`, 135°) on dark backgrounds. The gradient starts
  at the lighter *violet hover* tone rather than the base violet so the mark keeps contrast against `#0B0B10`.
- `vanta-mark-mono.svg` uses `currentColor` for single-colour contexts (favicons, embossing, print, text inline).

### Wordmark (`vanta-wordmark.svg`, 512 × 128)

Mark at 1.5× followed by **VANTA** in custom geometric capitals drawn as paths on a 10-unit stroke grid (no font
dependency): sharp diagonals, flat terminals, wide tracking. Letters are `#F5F5F7` (text.primary); the mark keeps the
gradient. The wordmark is the primary signature on the website header, launcher sidebar and the in-game main menu.

### App icon (`vanta-app-icon.svg`, 256 × 256)

A dark graphite tile (`#17171F` → `#0B0B10` vertical gradient, 56 px corner radius, 1 px `#2C2C3A` border) with a
soft violet glow behind the centre and the mark at 3× scale, centred with 32 px padding. Used for the Windows
installer, launcher window, website favicons and the Fabric mod icon.

## Clear space and minimum sizes

| Asset | Clear space | Minimum size |
| --- | --- | --- |
| Mark | at least 1/8 of the mark's width on every side (8 units on the 64 grid) | 16 px (favicon); prefer the mono mark below 24 px |
| Wordmark | at least the height of the letter V on every side | 120 px wide on screen, 30 mm in print |
| App icon | none needed (the tile is the boundary) | 32 px; the glow is dropped by the renderer at 16 px automatically |

Never place the mark on busy imagery; use it on `bg.void`, `bg.base`, a graphite surface or plain white (mono mark).

## Colour

Canonical values live in `shared/design/tokens.json`; the website, the launcher and the in-game UI all derive from it.

| Token | Hex | Use |
| --- | --- | --- |
| `bg.void` | `#07070A` | deepest background, hero backdrops |
| `bg.base` | `#0B0B10` | page / window background |
| `surface.1` / `.2` / `.3` / `.4` | `#111118` / `#17171F` / `#1F1F29` / `#272733` | cards, panels, popovers (increasing elevation) |
| `border.subtle` / `border.strong` | `#232330` / `#2C2C3A` | 1 px borders |
| `border.focus` | `#9B82FF` | keyboard focus rings |
| `text.primary` / `.secondary` / `.muted` | `#F5F5F7` / `#A1A1AA` / `#6B6B78` | body text hierarchy |
| `accent.violet` | `#7C5CFF` | primary actions, links |
| `accent.violetHover` / `.violetPressed` | `#9B82FF` / `#5B3DE0` | interaction states |
| `accent.blue` | `#4F8DFF` | gradient end, informational accents |
| `gradient.accent` | 135°, `#7C5CFF` → `#4F8DFF` | primary buttons, the PLAY button, highlights |
| `state.success` / `.warning` / `.danger` | `#3DDC97` / `#F5B942` / `#FF5C7A` | status only — never decoration |

Rules: violet/blue are accents, used sparingly on a black and graphite canvas; keep text contrast at 4.5:1 or better;
status colours mean something (success/warning/danger) and are never used as decoration.

## Typography

| Role | Family | Weights | Notes |
| --- | --- | --- | --- |
| Display (headlines, hero, screen titles) | **Space Grotesk** | 500, 700 | tracking −0.02em; wordmark-style labels use +0.18em uppercase |
| UI / body | **Inter** | 400, 500, 600, 700 | 16 px base on the web; Minecraft provider at 10 px |
| Code / checksums | system monospace stack | — | `ui-monospace, SFMono-Regular, Menlo, Consolas, monospace` |

Both families are redistributed unmodified under the **SIL Open Font License 1.1**; the license files must stay next
to the font files in `assets/fonts/`. The fonts may be bundled with VANTA products but must not be sold on their own.

## Do and don't

**Do**

- Use the supplied SVG/PNG files; regenerate rasters with `node scripts/brand/generate-icons.mjs` instead of
  exporting by hand.
- Keep the mark upright and in its original proportions.
- Use the mono mark on light or single-colour backgrounds.
- Pair the mark with the wordmark or with "VANTA Client" set in Space Grotesk 700.

**Don't**

- Don't rotate, mirror, outline, bevel or add drop shadows to the mark.
- Don't recolour the mark outside the palette above (the gradient, `text.primary`, or `currentColor`).
- Don't straighten or remove the diamond cut-out, or extend the V into a chevron/checkmark.
- Don't set the wordmark in Inter or any other font; it is a drawing, not text.
- Don't combine the mark with Minecraft logos, Mojang/Microsoft marks or block textures.
- Don't use the brand to imply endorsement of cheats, unofficial servers or unrelated software.

## File index

| File | Size | Purpose |
| --- | --- | --- |
| `vanta-mark.svg` | 64 × 64 (scalable) | gradient mark, master source |
| `vanta-mark-mono.svg` | 64 × 64 | single-colour mark (`currentColor`) |
| `vanta-wordmark.svg` | 512 × 128 | mark + VANTA letters |
| `vanta-app-icon.svg` | 256 × 256 | tiled application icon, master source |
| `png/vanta-mark-{64,256,1024}.png` | raster mark | documents, stores, social |
| `png/vanta-app-icon-{256,1024}.png` | raster icon | stores, social, macOS ICNS source |
| `png/vanta-wordmark-1024.png` | 1024 × 256 | documents, press |

Generated into the products by `scripts/brand/generate-icons.mjs` (all outputs are tracked in git):

| Output | Sizes | Platform |
| --- | --- | --- |
| `launcher/packaging/icon.ico` | 16, 24, 32, 48, 64, 128, 256 (PNG-compressed entries) | Windows installer, taskbar, Explorer |
| `launcher/packaging/icon-512.png` | 512 | Linux `jpackage` icon |
| `launcher/src/main/resources/dev/vanta/launcher/ui/icon-{16..512}.png` | 16, 24, 32, 48, 64, 128, 256, 512 | JavaFX window icons (`stage.getIcons()`) |
| `client/src/main/resources/assets/vanta/icon.png` | 128 | Fabric mod icon (mod lists) |
| `client/src/main/resources/assets/vanta/textures/gui/logo.png` | 256 | in-game main menu mark |
| `client/src/main/resources/assets/vanta/textures/gui/wordmark.png` | 512 × 128 | in-game wordmark |
| `website/public/favicon.svg`, `favicon-32.png` | scalable, 32 | browser tab |
| `website/public/apple-touch-icon.png` | 180 | iOS home screen |
| `website/public/icon-192.png`, `icon-512.png` | 192, 512 | web manifest / Android |
| `website/public/vanta-mark.svg`, `vanta-wordmark.svg` | scalable | header, footer, Open Graph |

macOS: an `.icns` is not generated yet because no macOS build is published. When one is, create it from
`png/vanta-app-icon-1024.png` with `iconutil` (sizes 16–1024 at 1× and 2×) and add it to the index above.
