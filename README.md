<p align="center">
  <img src="assets/brand/vanta-app-icon.svg" width="96" height="96" alt="VANTA Client icon">
</p>

<h1 align="center">VANTA Client</h1>

<p align="center"><strong>Your Minecraft. Refined.</strong></p>

<p align="center">
  A modern Fabric client for <strong>Minecraft Java Edition 1.21.11</strong> focused on performance,
  customization and a clean Minecraft experience.<br>
  Minecraft 1.21.11 · Fabric Loader 0.19.5 · Fabric API 0.141.6+1.21.11 · Java 21 · Windows 10/11
</p>

---

VANTA is a legitimate, purely client-side Minecraft client. It adds a premium interface, a customizable HUD,
a performance center, profiles, cosmetics, statistics and accessibility options on top of vanilla Minecraft.
It does **not** add cheats, combat automation, packet manipulation or anything that gives an unfair
advantage on multiplayer servers.

## Repository

This is a monorepo. Each product is an independent build with its own README.

| Directory | What it is | Build |
| --- | --- | --- |
| [`client/`](client/) | The Fabric mod for Minecraft 1.21.11 (Mojang official mappings, Loom 1.17.21) | `./gradlew build` |
| [`core/`](core/) | Pure Java 21 library with the whole UI kit, settings, HUD engine, profiles and statistics logic. No Minecraft dependency, fully unit tested. Compiled into the client jar. | `./gradlew build` |
| [`launcher/`](launcher/) | VANTA Launcher (JavaFX 21). Installs Minecraft 1.21.11 + Fabric legitimately, handles Microsoft sign-in, detects Java 21 and launches the client. | `./gradlew build` / `./gradlew jpackage` |
| [`website/`](website/) | Official website (Vite, React, TypeScript, Tailwind). Deployed to Netlify. | `npm install && npm run build` |
| [`shared/`](shared/) | Design tokens, JSON schemas, release manifests, i18n strings shared by all products | – |
| [`assets/`](assets/) | Brand sources, fonts (SIL OFL), screenshots captured by CI | – |
| [`docs/`](docs/) | User and developer documentation (also rendered on the website) | – |
| [`scripts/`](scripts/) | Build, release and CI helper scripts | – |

## Documentation

- [BUILDING.md](BUILDING.md) — how to build every part of the project
- [DEVELOPMENT.md](DEVELOPMENT.md) — development environment, architecture notes, testing
- [RELEASE.md](RELEASE.md) — release process, manifests, checksums
- [CONTRIBUTING.md](CONTRIBUTING.md) — contribution guidelines
- [CHANGELOG.md](CHANGELOG.md) — release notes
- [docs/](docs/) — user documentation: installation, launcher, settings, HUD, profiles, troubleshooting, FAQ, privacy

## Requirements

- Minecraft Java Edition (a Microsoft account that owns the game)
- Java 21 (the launcher can detect an existing installation or install a verified Eclipse Temurin runtime)
- Windows 10/11 is the primary platform; the client and launcher are plain Java and also run on Linux and macOS

## Status

See [CHANGELOG.md](CHANGELOG.md) for what is implemented. Release downloads are published through the
release manifests in [`shared/releases/`](shared/releases/) and never hardcoded in the website or launcher.
No release has been published yet, so the download page and the launcher report "not published" until the
first release workflow run fills in the manifests.

### What CI verifies on every push ([`ci.yml`](.github/workflows/ci.yml))

- **core**: JUnit tests and Java2D previews of every VANTA screen.
- **client**: compiled against the real Minecraft 1.21.11 (Mojang mappings); the production game test then starts
  the built jar with Fabric Loader 0.19.5 and Fabric API in a headless game, opens every screen, creates a world,
  applies a performance preset and screenshots everything. The captures in [`assets/screenshots/`](assets/screenshots/)
  come from that test.
- **launcher**: unit tests on Ubuntu and Windows, `jlink` + `jpackage`, and an integration job that installs
  Minecraft 1.21.11 + Fabric from the official endpoints with the launcher's fat jar, launches the game headlessly and
  checks that the VANTA main menu came up.
- **website**: lint, unit tests, production build with a bundle budget, Playwright end-to-end and accessibility checks.

## License

Code is licensed under the [MIT License](LICENSE). The VANTA name, wordmark and logo are not covered by the
license. Minecraft is a trademark of Mojang AB / Microsoft; this project is not affiliated with or endorsed by them.
