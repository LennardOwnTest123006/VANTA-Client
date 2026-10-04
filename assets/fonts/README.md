# Bundled fonts

| Family | Files | Weights | License | Source |
| --- | --- | --- | --- | --- |
| Inter | `inter/Inter-*.ttf`, `inter/Inter-latin.woff2` (variable, latin subset) | 400, 500, 600, 700 | SIL OFL 1.1 (`inter/LICENSE.txt`) | https://github.com/rsms/inter (via Google Fonts, v20) |
| Space Grotesk | `space-grotesk/SpaceGrotesk-*.ttf`, `space-grotesk/SpaceGrotesk-latin.woff2` | 500, 700 | SIL OFL 1.1 (`space-grotesk/LICENSE.txt`) | https://github.com/floriankarsten/space-grotesk (via Google Fonts, v22) |

Usage:
- **Website**: the `.woff2` latin subsets are copied to `website/public/fonts/` and declared with `@font-face`.
- **Launcher**: the `.ttf` files are loaded with `Font.loadFont` at startup.
- **Minecraft client**: `Inter-Medium.ttf` and `SpaceGrotesk-Bold.ttf` are bundled as TrueType font providers
  (`assets/vanta/font/ui.json`, `assets/vanta/font/display.json`).

Both fonts are redistributed unmodified under the SIL Open Font License 1.1. The license files must stay next to the font files.
