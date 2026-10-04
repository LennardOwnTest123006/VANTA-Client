# Screenshots

This folder holds the **real** screenshots shown on the website's Screenshots page and in the documentation.
VANTA never ships mock-ups, renders or edited images as screenshots. The current set was captured by the automated
game test described below from the development build (`captions.json` records the client, Minecraft and Fabric
versions of each capture); it is replaced when the UI changes. If the folder is ever empty, the website shows
"Screenshots will be published with the first release".

## Where screenshots come from

The client's automated game test (`dev.vanta.client.gametest.VantaClientGameTest`) launches the real game
(Minecraft 1.21.11, Fabric Loader 0.19.5, the built VANTA jar) headlessly in GitHub Actions and captures every
VANTA screen:

| File | Content |
| --- | --- |
| `01_main_menu.png` | VANTA main menu replacing the title screen |
| `02_settings.png` … `13_about.png` | settings, HUD editor, performance center, profiles, keybinds, crosshair, cosmetics, statistics, resource packs, accessibility, search, about |
| `20_hud_ingame.png` | in-world HUD with several widgets enabled |
| `21_ingame_menu.png` | VANTA menu opened while in a world |

The test resizes the window to 1920×1080 at GUI scale 2 (960×540 logical pixels, the desktop layout of every
screen) and parks the mouse in a corner before each capture, so no hover state or tooltip ends up in the picture. The
files land in `client/run/production-gametest/screenshots/` on the runner (prefixed with a running counter, e.g.
`0003_04_performance.png`), are attached to the workflow run as the `client-gametest` artifact, and
`scripts/ci/publish-screenshots.sh` mirrors them to the orphan `ci-artifacts` branch for review. Nothing is copied
here automatically.

## Promoting screenshots to this folder

A maintainer reviews a run and copies the files that should be public:

1. Download the `client-gametest` artifact of a green run on the default branch (or a release run).
2. Copy the PNGs here keeping their names without the counter prefix (`NN_name.png`). Do not retouch them; lossless
   PNG re-encoding (`oxipng`, `zopflipng`, or `sharp` with `png({ compressionLevel: 9 })`) is the only allowed edit.
3. Describe each image in `captions.json` (the website reads it when present):

   ```json
   {
     "01_main_menu.png": {
       "caption": "The VANTA main menu with the Violet Horizon background.",
       "alt": "Dark main menu with a violet gradient horizon, the VANTA wordmark and a column of menu buttons.",
       "capturedWith": "client 1.0.0 · Minecraft 1.21.11 · gametest run 2026-10-04"
     }
   }
   ```

   `caption` is shown under the image, `alt` is the accessible description (describe what is visible, not what it
   means), `capturedWith` is free text. Images without an entry are shown with their file name as caption.
4. Commit with `docs(screenshots): ...`. Netlify redeploys the website.

## Rules

- Real captures only, from the automated test or from a real game session with the released build.
- No personal data: game-test sessions use the development offline account name; manual captures must not show real
  usernames, server addresses or chat.
- No third-party content beyond vanilla Minecraft rendered by the game itself; no resource packs that we cannot
  redistribute.
- Keep the set small (one image per screen) and replace images when the UI changes instead of adding variants.
- The Screenshots page lists what is here and nothing else; never reference images that do not exist.
