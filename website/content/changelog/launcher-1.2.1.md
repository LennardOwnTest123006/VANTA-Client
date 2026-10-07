---
product: launcher
version: 1.2.1
date: 2026-10-07
title: VANTA Launcher 1.2.1
minecraftVersion: 1.21.11
---

Bug-fix release for one start-up crash. A mod in VANTA's game folder that was built for an older Minecraft stopped Minecraft 1.21.11 while starting, for example Smart FPS Booster 1.0.0 from Modrinth ("Could not execute entrypoint stage 'client' due to errors, provided by 'smartfpsbooster'"). Both of its 1.21.11-tagged builds were made for 1.21.4 and 1.21.8 and use a key binding constructor that Minecraft removed in 1.21.9, so the game stops with or without VANTA. The launcher now switches such mods off before the game starts and recovers from a crash report that names a mod. Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11 and Java 21 are unchanged, and the launcher installs VANTA Client 1.2.1.

## Fixed

- Start check: before PLAY, before *PLAY via Minecraft Launcher* or *Use with Minecraft Launcher* opens the Minecraft Launcher, before a restart the game asks for, once when the launcher window opens, and before the command-line `--launch` and `--install-official-profile`, the launcher checks every mod in VANTA's game folder except VANTA and Fabric API. A mod that still creates key bindings the pre-1.21.9 way, without a code path for the newer ones, is switched off: its file is renamed to `.jar.disabled`, nothing is deleted, a notification names the mod and the reason, and the Mods page can switch it on again
- Crash-report recovery: when the newest crash report in VANTA's game folder says that a mod's start-up code failed and the report is newer than that mod's file, the launcher switches the mod off once. VANTA, Fabric API and Fabric's modules are never switched off, only reported. After opening the Minecraft Launcher it watches for such a report for 30 minutes and then asks you to press Play again
- The Mods page no longer installs such a build: the download is deleted again and the page says why; an update keeps the version you have

## Notes

- If your game already stops at start: launcher 1.2.0 offers this update itself. Install it and open the launcher once; it switches the mod off. Without updating, switch the mod off on the Mods page or delete `smart-fps-booster-*.jar` from the `mods` folder of VANTA's game folder (*Home → Open game folder*)
- Before the start only this kind of incompatibility is recognised; other start-up crashes are caught through the crash report, and there only Fabric's entrypoint message counts. A mod that another enabled mod requires is reported, not switched off
- Updating from launcher 1.0.0: it offers the `.msi` on Windows and the `.tar.gz` on Linux x64; portable and jar users download their file from the release `launcher-v1.2.1` and compare its SHA-256
- Nothing is code-signed yet, so Windows SmartScreen may warn; compare the SHA-256 from `SHA256SUMS.txt` first
