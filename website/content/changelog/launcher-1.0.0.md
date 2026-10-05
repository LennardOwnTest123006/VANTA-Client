---
product: launcher
version: 1.0.0
date: 2026-10-04
title: VANTA Launcher 1.0.0
minecraftVersion: 1.21.11
---

First public release of the VANTA Launcher for Windows 10/11 (JavaFX 21), with builds for Linux x64 and Apple Silicon macOS.

## Added

- Installs Minecraft 1.21.11 and Fabric Loader 0.19.5 from the official Mojang and Fabric sources with checksum verification of every downloaded file, resumable and idempotent (verified files are never downloaded twice)
- Installs Fabric API 0.141.6+1.21.11 and the VANTA Client jar into a dedicated instance (`instances/vanta-1.21.11`), optionally reusing libraries and assets from an existing official `.minecraft` directory read-only
- "Use with Minecraft Launcher" (Home screen or `--install-official-profile`): installs Fabric API and the VANTA Client into the VANTA instance and adds the profile "VANTA 1.21.11" for it to the official Minecraft Launcher, which then downloads the game, signs you in with Microsoft and starts it; other launcher profiles are kept and a backup is made once
- Downloads for each platform: `.msi` and `.exe` installers and a portable app for Windows x64 and an app for Linux x64, all with Java 21 included, plus launcher jars for Windows x64, Linux x64 and Apple Silicon macOS that need Java 21 installed
- Microsoft account sign-in with the device code flow, Xbox Live and XSTS authentication, Minecraft entitlement and profile checks, token refresh; clear messages for accounts without Xbox Live, child accounts, unavailable regions and accounts that do not own Minecraft. It stays switched off until a Microsoft application id approved by Mojang is configured (Settings or `VANTA_MS_CLIENT_ID`), which this release does not include
- Detects an existing Java 21 (JAVA_HOME, PATH, vendor directories, the official launcher runtimes) or installs a verified Eclipse Temurin 21 runtime from Adoptium with path-traversal-safe extraction
- Home screen with PLAY, status line, install progress, Java and account cards; Versions screen with installed components, the SHA-256 of the client jar and rollback to the last three verified client versions; Logs screen with launcher and game logs; Settings for memory, Java path, JVM arguments, resolution, Microsoft client id, releases URL, update checks and developer mode; About screen
- Update checks against the release manifests (`client-latest.json`, `launcher-latest.json`) with SHA-256 verification of every downloaded update; launcher updates use the file for your platform and installers are opened only after you confirm
- Built-in releases URL: update checks work out of the box against `https://raw.githubusercontent.com/LennardOwnTest123006/VANTA-Client/HEAD/shared/releases/latest`; a URL in Settings or the `VANTA_RELEASES_BASE_URL` environment variable overrides it, and Settings shows the URL in use
- Command line interface (`--install`, `--install-official-profile`, `--launch`, `--check-java`, `--install-java`, `--check-update`, `--print-command`, `--version`) with documented exit codes

## Notes

- Files that fail checksum verification are deleted and never used; a downloaded launcher update is only opened after you confirm it
- Account tokens are encrypted with Windows DPAPI (AES-256-GCM with an owner-only key file on other systems); passwords are never seen or stored and tokens are redacted from logs
- Offline sessions are only offered after a successful Microsoft sign-in has verified game ownership
- The installers are not code-signed yet; verify the SHA-256 shown on the Download page before installing
- Each launcher jar contains JavaFX for one platform only: the Windows jar does not start on Linux or macOS and the other way round. The Apple Silicon jar is built and tested from the command line on a macOS runner; its window has not been tested yet and it is not signed
