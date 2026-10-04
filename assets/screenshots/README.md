# Screenshots

This folder holds the **real** screenshots shown on the website's Screenshots page and in the documentation.
It is intentionally empty until the first release: VANTA never ships mock-ups, renders or edited images as
screenshots. While it is empty, the website shows "Screenshots will be published with the first release".

## Where screenshots come from

The client's automated game test (`dev.vanta.client.gametest.VantaClientGameTest`) launches the real game
(Minecraft 1.21.11, Fabric Loader 0.19.5, the built VANTA jar) headlessly in GitHub Actions and captures every
VANTA screen:

| File | Content |
| --- | --- |
| `01_main_menu.png` | VANTA main menu replacing the title screen |
| `02_settings.png` … `12_about.png` | settings, HUD editor, performance center, profiles, keybinds, crosshair, cosmetics, statistics, resource packs, accessibility, search, about |
| `20_hud_ingame.png` | in-world HUD with several widgets enabled |
| `21_ingame_menu.png` | VANTA menu opened while in a world |

They land in `client/run/production-gametest/screenshots/` on the runner, are attached to the workflow run as the
`client-gametest` artifact, and `scripts/ci/publish-screenshots.sh` mirrors them to the orphan `ci-artifacts` branch
for review. Nothing is copied here automatically.

## Promoting screenshots to this folder

A maintainer reviews a run and copies the files that should be public:

1. Download the `client-gametest` artifact of a green run on the default branch (or a release run).
2. Copy the PNGs here keeping their names (`NN_name.png`). Do not retouch them; cropping to the window and lossless
   PNG optimisation (`oxipng`/`zopflipng`) are the only allowed edits.
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
