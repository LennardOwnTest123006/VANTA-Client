# Development guide

## Architecture in one page

```
                 ┌──────────────────────────────────────────────┐
                 │                 core (pure Java 21)          │
                 │  ui kit · settings · hud engine · profiles   │
                 │  stats · keybinds · search · notifications   │
                 │  perf presets · cosmetics · crosshair        │
                 │  Canvas / GameBridge / OptionsBridge / ...   │  ← interfaces
                 └───────────────▲──────────────────────────────┘
                                 │ compiled in
 ┌───────────────────────────────┴───────────────┐     ┌──────────────────────────┐
 │ client (Fabric mod, Minecraft 1.21.11)         │     │ launcher (JavaFX)        │
 │ GuiGraphicsCanvas · VantaScreen host           │     │ install · auth · launch  │
 │ HUD element · mixins · bridges · gametest      │     │ update manifests         │
 └────────────────────────────────────────────────┘     └──────────────────────────┘
                 shared/ (tokens, schemas, releases)            website/ (React)
```

- **core** contains every screen, widget and behaviour of the VANTA interface, rendered through the `Canvas`
  abstraction. It is compiled and unit tested with plain Java 21 and can render every screen to PNG with a
  Java2D canvas (`./gradlew previewScreens`). It has a single runtime dependency (Gson).
- **client** is intentionally thin: it implements the bridges on top of Minecraft (`GuiGraphics`, `Options`,
  `KeyMapping`, `PackRepository`, …), hosts core screens inside a vanilla `Screen`, registers the HUD element
  with Fabric API, and contains the handful of mixins the client needs (title screen replacement, click
  counting for the CPS widget, zoom FOV).
- **launcher** is a modular JavaFX application; everything that can be tested without a UI (manifests, rule
  evaluation, downloads with checksums, Fabric profile merging, Java detection, auth flows) lives in
  `dev.vanta.launcher.core` and has unit tests with recorded fixtures.
- **website** is a static SPA. Content (news, changelog, releases) is data in `website/content/` and
  `shared/releases/`, validated against `shared/schemas/` at build time.

## Toolchain facts (verified 2026-10-04)

| Component | Version | Source of truth |
| --- | --- | --- |
| Minecraft Java Edition | 1.21.11 (released 2025-12-09) | `client/gradle.properties` |
| Fabric Loader | 0.19.5 | FabricMC/fabric-loader tags |
| Fabric API | 0.141.6+1.21.11 | FabricMC/fabric branch `1.21.11` |
| Fabric Loom | 1.18.2 (`net.fabricmc.fabric-loom-remap`) | FabricMC/fabric-loom tags |
| Mappings | Mojang official mappings | official 1.21.11 Fabric template |
| Java | 21 | all Gradle builds use `options.release = 21` |
| Gradle | 9.7.1 (wrapper) | `gradle/wrapper/gradle-wrapper.properties` |

Important 1.21.11 API facts for contributors (Mojang mappings): `ResourceLocation` is now
`net.minecraft.resources.Identifier`; screen input uses `MouseButtonEvent`, `KeyEvent` and `CharacterEvent`
from `net.minecraft.client.input`; `GuiGraphics.pose()` returns a JOML `Matrix3x2fStack`; key binding categories
are `KeyMapping.Category` objects registered with `KeyMapping.Category.register(Identifier)`.

## Running the client during development

```bash
cd client && ./gradlew runClient
```
Loom launches Minecraft 1.21.11 with an offline development account. Config files are written to
`client/run/config/vanta/`. Use `./gradlew runClientGametest` to run the automated UI tests
(`dev.vanta.client.gametest.VantaClientGameTest`), which take screenshots of every VANTA screen.

## Testing strategy

| Layer | How |
| --- | --- |
| core | JUnit 5 unit tests; Java2D screen previews for visual review |
| client | Compiles against real Minecraft in CI; Fabric client game tests launch the real game headlessly (Xvfb) and screenshot the main menu, settings, HUD editor, in-game HUD |
| launcher | JUnit 5 with fixture JSON (version manifest, version json, Fabric profile); integration test for the install pipeline runs in CI against the real Mojang/Fabric endpoints and launches the game in development offline mode |
| website | vitest + Testing Library; Playwright end-to-end against the production build; link checker |

## Conventions

See [CONTRIBUTING.md](CONTRIBUTING.md). Config files use Gson with `schemaVersion`; writes are atomic
(temp file + move). Never block the render thread with I/O: the client uses a single daemon executor for disk
writes and the launcher uses JavaFX `Task`s.
