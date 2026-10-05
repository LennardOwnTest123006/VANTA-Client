---
title: Building from source
description: The short version of BUILDING.md — prerequisites and the commands to build the core library, the Fabric client, the launcher and the website from a clean checkout.
order: 30
category: Reference
---

Everything builds from a clean checkout with a JDK 21, Node.js 22 and the Gradle wrappers in the repository. The full
guide with explanations is `BUILDING.md` and the architecture is in `DEVELOPMENT.md`, both in the root folder of the
[repository](https://github.com/LennardOwnTest123006/VANTA-Client).

## Prerequisites

| Tool | Version | Needed for |
| --- | --- | --- |
| JDK | 21 (Temurin, Microsoft, Zulu, Oracle …) — a JDK, not a JRE | core, client, launcher |
| Gradle | wrapper included (9.7.1), nothing to install | core, client, launcher |
| Node.js | 22 (npm 10) | website, release scripts, brand asset generation |
| Network | Maven Central and Gradle for everything; **Mojang and Fabric hosts for the client**; npm registry for the website | first build |
| WiX Toolset 3.x | Windows `.msi`/`.exe` installers only (preinstalled on GitHub's Windows runners) | launcher packaging |

```bash
git clone https://github.com/LennardOwnTest123006/VANTA-Client.git
cd VANTA-Client
```

## Core — the pure Java library

All UI logic, settings, HUD engine, profiles, statistics and performance presets live here with no Minecraft
dependency, so it builds and tests anywhere.

```bash
cd core
./gradlew build            # compile + unit tests → build/libs/vanta-core-1.0.0.jar
./gradlew previewScreens   # renders every VANTA screen to build/previews/*.png with a Java2D canvas
```

## Client — the Fabric mod

Compiles the core sources together with the thin Minecraft adapters against Minecraft 1.21.11 (Mojang official
mappings, Fabric Loom 1.17.21). The first build downloads the game and mappings from Mojang/Fabric and takes a few
minutes.

```bash
cd client
./gradlew build                        # build/libs/vanta-client-1.0.0.jar
./gradlew runClient                    # start Minecraft 1.21.11 with VANTA (development account)
./gradlew runClientGametest            # automated client game tests in the development environment
./gradlew runProductionClientGametest  # runs the real built jar with Fabric Loader + Fabric API headlessly (Xvfb when CI=true)
```

Check the target: `unzip -p build/libs/vanta-client-1.0.0.jar fabric.mod.json | grep minecraft` prints
`"minecraft": "~1.21.11"`.

## Launcher — JavaFX

```bash
cd launcher
./gradlew build            # tests + build/libs/vanta-launcher-1.0.0-all.jar (fat jar incl. JavaFX for this system)
./gradlew run              # start the launcher UI with the host JDK
./gradlew jlinkImage       # trimmed Java 21 + JavaFX runtime under build/runtime
./gradlew jpackage         # app image under build/jpackage; -PjpackageType=msi|exe on Windows (WiX), deb/rpm on Linux
```

The fat jar contains the JavaFX native libraries of one platform: the build host's, or the one chosen with
`-PjavafxPlatform=win|linux|mac|mac-aarch64|linux-aarch64`. A jar built on Windows does not start on Linux or macOS.
The release workflow therefore builds the launcher on Windows, Linux and Apple Silicon macOS and publishes renamed
copies (`vanta-launcher-<version>-windows-all.jar`, `-linux-all.jar`, `-macos-aarch64-all.jar`). Packaging uses only
the JDK's `jlink` and `jpackage`; installers for Windows must be built on Windows.

## Website

```bash
cd website
npm ci
npm run lint && npm test && npm run build   # eslint + tsc, vitest, production build in dist/
npm run dev                                 # Vite dev server
PLAYWRIGHT_CHROMIUM_PATH=/path/to/chromium npm run test:e2e   # Playwright against the production build
```

Optional environment variables (`website/.env.example`): `VITE_DOWNLOAD_LAUNCHER_URL`, `VITE_DOWNLOAD_CLIENT_URL`,
`VITE_RELEASES_BASE_URL`, `VITE_SUPPORT_EMAIL`, `VITE_DISCORD_URL`, `VITE_GITHUB_URL`. Without them the download page
reads the release manifests and shows "not published yet" where no URL exists.

## Everything at once

```bash
./scripts/build-all.sh                  # core → client → launcher → website, plus manifest/docs validation
./scripts/build-all.sh --skip-client    # on machines without access to the Mojang/Fabric hosts
powershell -File scripts/build-all.ps1 -SkipClient   # Windows
```

## Release metadata and docs checks

```bash
node --test scripts/release/                                                                 # release script tests
node scripts/release/validate-json.mjs shared/schemas/release-manifest.schema.json shared/releases/*.json
node scripts/release/release-assets.mjs check-manifest shared/releases/client-1.0.0.json            # release file names and order
node scripts/release/check-links.mjs docs website/content                                    # markdown link check
```

Releases themselves are cut by the release workflow (*Run workflow* with product and version, or a pushed tag
`client-v1.0.0` / `launcher-v1.0.0`); it builds, checksums, publishes the GitHub Release, verifies the public download
links and hands the completed manifests back — see `RELEASE.md` in the root folder of the repository.
