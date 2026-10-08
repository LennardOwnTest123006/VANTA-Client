# VANTA Client — Fabric mod (`client/`)

The Fabric mod for **Minecraft Java Edition 1.21.11** (Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, Java 21,
Mojang official mappings). It is the thin in-game shell around the pure-Java `core` library: every screen, setting,
HUD widget, profile, statistic and keybind rule lives in `../core` and is unit-tested there; this module only adapts
Minecraft to the interfaces `core` defines.

## What the mod does

- Replaces the title screen with the VANTA main menu (switchable in Settings → General).
- Adds the VANTA settings screens, HUD editor, Performance Center, profiles, keybind manager, crosshair editor,
  cosmetics, statistics, resource pack manager, accessibility screen, global search and About screen — all rendered
  by `core` through a `Canvas` implemented on `GuiGraphics`.
- Draws the VANTA HUD (FPS, coordinates, biome, clock, memory, keystrokes, armour, effects, …) and the custom
  crosshair as Fabric HUD elements, and a notification overlay on every screen.
- Hold-to-zoom (default `C`), a `/vanta` client command and seven key mappings in the "VANTA" Controls category.
- Applies performance presets by writing the same vanilla video options the user could set in Video Settings.
- From 1.4.0 on: Vanta Nexus (default `N`) with the strictly local AI assistant (`llama-server` on 127.0.0.1, no cloud,
  nothing sent anywhere), waypoint markers projected onto the screen for the waypoints of the current world, the Vanta
  Lab client sides (input, movement and attacks for the Dynamic HUD and the animated crosshair, 3D waypoint beams),
  and a one-time main-menu notice when the Local AI is not installed. Nothing downloads without a click on
  [Install Local AI] in Nexus.

## Architecture (thin adapters over `core`)

| Package | Role |
| --- | --- |
| `dev.vanta.client.VantaClient` | Fabric `ClientModInitializer`: wires logging, translations, bridges, `VantaServices`, screens, keys, HUD elements, events and commands. |
| `dev.vanta.client.VantaRuntime` | Holds the long-lived objects (services, `HudRenderer`, `CrosshairRenderer`, `NotificationOverlay`, zoom, key mappings, scheduler, theme). |
| `dev.vanta.client.bridge` | `GameBridge`, `OptionsBridge`, `KeybindBridge`, `ResourcePackBridge`, `ScreenshotBridge`, `ClipboardBridge` implementations over the public Minecraft API. |
| `dev.vanta.client.render` | `GuiGraphicsCanvas` (the core `Canvas` on `GuiGraphics`), text metrics and the VANTA font styles; `WorldCamera` (the camera of the current world frame: view matrix and position from Fabric's `WorldRenderEvents.END_EXTRACTION`, field of view from the `GameRendererMixin`) and `WaypointBeamRenderer` (`END_EXTRACTION` captures the camera, `BEFORE_TRANSLUCENT` draws the Lab waypoint beams through `RenderTypes.debugFilledBox()`). |
| `dev.vanta.client.screen` | `VantaScreen` (vanilla `Screen` hosting a core `UiScreen`), `VantaScreens`, the `UiHost`, theme factory, title-screen replacement policy, vanilla screen opener, notification overlay hook for vanilla screens, `NexusFirstStart` (the one-time Local AI notice on the main menu). |
| `dev.vanta.client.hud` | Fabric `HudElement`s for the HUD, the waypoint markers and the crosshair, click counters, frame timer. |
| `dev.vanta.client.lab` | `LabInputs`: relays input, movement and attacks to the core `LabEffects` (Dynamic HUD, animated crosshair). |
| `dev.vanta.client.zoom` | `ZoomController` (hold-to-zoom state machine). |
| `dev.vanta.client.keys` | VANTA key mappings and the `zoom.key` ↔ `key.vanta.zoom` sync. |
| `dev.vanta.client.command` | The `/vanta` command. |
| `dev.vanta.client.event` | Fabric event wiring (ticks, lifecycle, join/leave, block break/place). |
| `dev.vanta.client.mixin` | Four small, documented mixins (see below). |
| `dev.vanta.client.gametest` | The Fabric client game test. |

Rendering contract: `GuiGraphicsCanvas` maps fills to `GuiGraphics.fill`, vertical gradients to `fillGradient`, text to
`drawString` with a `Component` styled by the VANTA font provider (`vanta:ui` Inter 10 px, `vanta:ui_bold`,
`vanta:display` Space Grotesk 14 px; the vanilla font is drawn as a plain string), images to `blit` with
`RenderPipelines.GUI_TEXTURED`, transforms to `pose()` and clips to `enableScissor` under an identity pose (the core
intersects nested clips). Line heights are 9 px for Inter and the vanilla font and 14 px for the display font, shared
with the Java2D preview canvas in `core`.

## Running

```bash
cd client
./gradlew build                         # compiles the mod jar (needs Fabric/Mojang maven access)
./gradlew runClient                     # development client
./gradlew runClientGametest             # dev-environment client game test (-Dfabric.client.gametest)
./gradlew runProductionClientGametest   # real built jar + Fabric API, like a user install
```

The game tests need a display; on Linux CI Loom runs them under `xvfb-run` automatically when `CI` is set.
Screenshots are written to `run/<run dir>/screenshots/` as `01_main_menu.png` … `17_local_ai_setup.png`,
`20_hud_ingame.png`, `21_ingame_menu.png`, the click reproduction (`30_*`, `40_*`), the Nexus sections (`50_*`) and the
waypoint marker and beam (`60_*`, `61_*`). JVM properties: `-Dvanta.forceVanillaMenu=true` keeps the vanilla title
screen. Environment: `VANTA_LOCAL_AI_DIR=<absolute path of a prepared Local AI install>` makes the client write the
launcher note `config/vanta/local-ai.json` at start-up and lets the game test run the Local AI end to end (start
`llama-server`, ask "Only show FPS and coordinates", stop it, and install from a loopback mirror); without it that
step logs that it is skipped.

## Mixins (`vanta.client.mixins.json`)

| Mixin | Target | Purpose |
| --- | --- | --- |
| `MinecraftMixin` | `Minecraft.setScreen(Screen)` (HEAD, cancellable) | Swaps an incoming vanilla `TitleScreen` for the VANTA main menu when the setting allows it. |
| `KeyMappingMixin` | static `KeyMapping.click(InputConstants.Key)` (HEAD) | Counts attack/use presses for the clicks-per-second widget. Observes only. |
| `GameRendererMixin` | `GameRenderer.getFov(Camera, float, boolean)` (RETURN) | Divides the world FOV by the zoom factor while the zoom key is held (spyglass-like); hands the resulting world FOV to `WorldCamera` for the waypoint marker projection. |
| `MouseHandlerMixin` | `MouseHandler.onScroll(long, double, double)` (HEAD, cancellable) | Lets the mouse wheel adjust the zoom level while zooming; untouched otherwise. |

## Key mappings (category "VANTA")

| Mapping | Default | Action |
| --- | --- | --- |
| `key.vanta.open_menu` | Right Shift | Opens the VANTA settings |
| `key.vanta.toggle_hud` | unbound | Shows/hides all VANTA widgets |
| `key.vanta.hud_editor` | unbound | Opens the HUD editor |
| `key.vanta.performance` | unbound | Opens the Performance Center |
| `key.vanta.open_nexus` | N | Opens Vanta Nexus |
| `key.vanta.zoom` | C (hold) | Zoom; mouse wheel adjusts the level. C is also vanilla's *Save Hotbar Activator* (Creative mode only); rebind either one if you use saved hotbars |
| `key.vanta.screenshot_hud_free` | unbound | Screenshot without the GUI and the VANTA HUD |

## Commands

`/vanta` (client-side only): `menu`, `hud`, `perf`, `nexus`, `profiles` (list) / `profiles <name>` (activate),
`stats` (current session), `reload` (re-read all VANTA configuration files).

## Configuration files

`<gameDir>/config/vanta/`: `settings.json`, `hud/layout.json`, `hud/presets/*.json`, `profiles/*.json`,
`profiles/state.json`, `crosshair.json`, `cosmetics.json`, `cosmetics/*.json`, `stats.json` (local only; never
uploaded), `waypoints.json`, `nexus-chat.json` (the Nexus conversation, local only), `nexus-first-start.json` (which
client version dismissed the Local AI notice), `local-ai.json` (the launcher's note pointing at its Local AI folder,
when the launcher set the game up) and `local-ai/` (the client-managed Local AI install: runtime, model, logs).
Vanilla options are read and written through `Options` and saved to the game's own `options.txt`.

## No-cheat policy

VANTA is a legitimate client. It contains no combat automation, no packet manipulation, no anti-cheat bypass, no
player tracking and nothing that gives an unfair multiplayer advantage. Every mixin above is listed with its exact
target and purpose; HUD widgets only show information the game already exposes (F3, inventory, effects); the zoom is
a client-side camera effect equivalent to the spyglass; performance presets only write vanilla video options.
