# Building VANTA

All builds are reproducible from a clean checkout. Each product is an independent build; nothing has to be
built in a particular order except that the **client** compiles the **core** sources (it does so automatically).

## Prerequisites

| Tool | Version | Used by |
| --- | --- | --- |
| JDK | 21 (Temurin, Microsoft, Zulu or Oracle) | core, client, launcher |
| Gradle | wrapper included (9.7.1) — no install needed | core, client, launcher |
| Node.js | 22 LTS (npm 10) | website, brand asset generation, release scripts |
| Internet access | Maven Central, Gradle, Mojang (`piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`), Fabric (`maven.fabricmc.net`, `meta.fabricmc.net`), npm registry | first build |
| WiX Toolset 3.x | only for Windows `.msi`/`.exe` installers (preinstalled on GitHub Windows runners) | launcher packaging |

## Core (pure Java library)

```bash
cd core
./gradlew build          # compiles, runs unit tests, produces build/libs/vanta-core-1.0.0.jar
./gradlew test           # tests only
./gradlew previewScreens # renders every VANTA screen to build/previews/*.png with the Java2D canvas
```

CI runs `./gradlew build` for the core (compile and JUnit tests). `previewScreens` and `previewGallery` are local
tools for visual review and are not run in CI.

## Client (Fabric mod for Minecraft 1.21.11)

```bash
cd client
./gradlew build                        # build/libs/vanta-client-<version>.jar (+ sources jar); <version> = mod_version
./gradlew runClient                    # dev launch of Minecraft 1.21.11 with VANTA (offline dev account)
./gradlew runClientGametest            # dev launch running the automated client game tests
./gradlew runProductionClientGametest  # launches the REAL built jar with Fabric Loader 0.19.5 + Fabric API
                                       # and runs the game tests; screenshots in run/production-gametest/screenshots
```

On Linux without a display, prefix dev runs with `xvfb-run -a`. The production game test uses Xvfb
automatically when the `CI` environment variable is set.

The first build downloads Minecraft 1.21.11 and the Mojang mappings through Fabric Loom; this takes a few
minutes. Loom caches everything under `~/.gradle/caches/fabric-loom`.

To verify the exact target: `unzip -p build/libs/vanta-client-<version>.jar fabric.mod.json | grep minecraft`
must print `"minecraft": "~1.21.11"`.

## Launcher (JavaFX)

```bash
cd launcher
./gradlew build          # compiles, runs unit tests, produces build/libs/vanta-launcher-<version>-all.jar
./gradlew run            # starts the launcher UI with the host JDK
./gradlew jlinkImage     # custom Java 21 runtime image with JavaFX under build/runtime
./gradlew jpackage       # app image under build/jpackage ("VANTA Launcher/"); installers with -PjpackageType:
                         #   Windows: -PjpackageType=msi or exe -> VANTA Launcher-<version>.msi / .exe (requires WiX)
                         #   Linux:   -PjpackageType=deb or rpm (needs the distribution's packaging tools)
                         #   macOS:   -PjpackageType=dmg or pkg
```

`vanta-launcher-<version>-all.jar` is a fat jar with every dependency **including the JavaFX native libraries of one
platform**: the build host, or the one chosen with `-PjavafxPlatform=win|linux|mac|mac-aarch64|linux-aarch64`. A jar
built on Windows does not start its window on Linux or macOS and vice versa: started on the wrong system it names the
file to download instead and exits with code 1, while the command line (`--help`, `--version`, ...) works with every
jar. `java -jar <jar> --version` prints the platform it was built for. `<version>` is `launcher_version` in
`launcher/gradle.properties`. The release workflow builds the launcher on Windows x64, Linux x64 and Apple Silicon macOS runners and
publishes the copies as `vanta-launcher-<version>-windows-all.jar`, `-linux-all.jar` and `-macos-aarch64-all.jar`,
next to the `.msi`, `.exe`, Windows portable `.zip` and Linux `.tar.gz` (see [RELEASE.md](RELEASE.md)). Only the
portable zip contains `VANTA Launcher/app/vanta-portable.marker`, which the release workflow adds to the app image
before zipping it; the launcher uses it to update a portable folder with the portable zip.

The launcher build uses only JDK tools (`jlink`, `jpackage`) — no third-party packaging plugins.
Cross-building Windows installers from Linux is not supported by `jpackage`; the release workflow builds them
on a Windows runner.

## Website

```bash
cd website
npm install
npm run build          # production build in dist/
npm run dev            # Vite dev server
npm test               # unit tests (vitest)
npm run test:e2e       # Playwright end-to-end tests against the production build
npm run lint           # eslint + typescript
```

Environment variables (all optional, see `website/.env.example`): `VITE_DOWNLOAD_LAUNCHER_URL`,
`VITE_DOWNLOAD_CLIENT_URL`, `VITE_RELEASES_BASE_URL`, `VITE_SUPPORT_EMAIL`, `VITE_DISCORD_URL`,
`VITE_GITHUB_URL`, `VITE_SITE_URL` (public origin for the sitemap and the absolute link-preview tags of `index.html`;
on Netlify the `URL` build variable is used when it is unset). When a download URL is not configured the download page
says so instead of inventing one. The Netlify build (`netlify.toml`) runs `npm ci && npm run build` in `website/`; the
bundle-size check removes the Vite build manifest (`dist/.vite/`) afterwards, so it is not deployed.

## Brand assets

```bash
npm --prefix scripts install
node scripts/brand/generate-icons.mjs   # renders assets/brand/*.svg into PNG/ICO/ICNS-ready sizes
```

## Everything at once

```bash
./scripts/build-all.sh          # Linux/macOS
powershell ./scripts/build-all.ps1   # Windows
```
