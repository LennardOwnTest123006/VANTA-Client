---
product: launcher
version: 1.0.0
date: 2026-10-04
title: VANTA Launcher 1.0.0
minecraftVersion: 1.21.11
---

First public release of the VANTA Launcher for Windows 10/11 (JavaFX 21; the portable jar also runs on Linux and macOS).

## Added

- Installs Minecraft 1.21.11 and Fabric Loader 0.19.5 from the official Mojang and Fabric sources with checksum verification of every downloaded file, resumable and idempotent (verified files are never downloaded twice)
- Installs Fabric API 0.141.6+1.21.11 and the VANTA Client jar into a dedicated instance (`instances/vanta-1.21.11`), optionally reusing libraries and assets from an existing official `.minecraft` directory read-only
- Microsoft account sign-in with the device code flow, Xbox Live and XSTS authentication, Minecraft entitlement and profile checks, token refresh; clear messages for accounts without Xbox Live, child accounts, unavailable regions and accounts that do not own Minecraft
- Detects an existing Java 21 (JAVA_HOME, PATH, vendor directories, the official launcher runtimes) or installs a verified Eclipse Temurin 21 runtime from Adoptium with path-traversal-safe extraction
- Home screen with PLAY, status line, install progress, Java and account cards; Versions screen with installed components, the SHA-256 of the client jar and rollback to the last three verified client versions; Logs screen with launcher and game logs; Settings for memory, Java path, JVM arguments, resolution, Microsoft client id, releases URL, update checks and developer mode; About screen
- Update checks against the release manifests (`client-latest.json`, `launcher-latest.json`) with SHA-256 verification of every downloaded update; installers are opened only after you confirm
- Command line interface (`--install`, `--launch`, `--check-java`, `--install-java`, `--check-update`, `--print-command`, `--version`) with documented exit codes

## Notes

- The launcher never executes downloaded files other than the verified Java runtime it installs
- Account tokens are encrypted with Windows DPAPI (AES-256-GCM with an owner-only key file on other systems); passwords are never seen or stored and tokens are redacted from logs
- Offline sessions are only offered after a successful Microsoft sign-in has verified game ownership
- The installers are not code-signed yet; verify the SHA-256 shown on the Download page before installing
