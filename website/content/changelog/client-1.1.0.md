---
product: client
version: 1.1.0
date: 2026-10-05
title: VANTA Client 1.1.0
minecraftVersion: 1.21.11
---

Feature release of VANTA Client for Minecraft Java Edition 1.21.11 (Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, Java 21). It adds Mods & Shaders, an in-game browser for Modrinth with a one-click Performance pack (Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling and Iris Shaders), and fixes leaving Create New World, which ended on the vanilla title screen.

## Added

- **Mods & Shaders** screen, opened from the new *Mods & Shaders* button of the VANTA main menu, from Settings > Video and Settings > Performance, from the Performance Center and from the global search: browse and search Modrinth for Fabric mods, shader packs for Iris and resource packs for Minecraft 1.21.11, in the tabs Mods, Shaders, Resource packs and Installed. With an empty search each tab lists the most downloaded projects; every row shows the title, the author, the downloads, the description and whether it is installed, and the panel on the right offers Install, Remove, Disable, Enable and *View on Modrinth*
- **Performance pack**: one click installs Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling and Iris Shaders. The newest stable Minecraft 1.21.11 Fabric build of each is looked up on Modrinth when you install (no version is fixed in VANTA); a mod without a 1.21.11 version is skipped with a message while the others install. Untick the mods you do not want; mods you already have show as *Installed* or, when they are loaded, *Active*
- Required dependencies are installed automatically, in the exact version a mod asks for when it names one (Iris and Sodium, for example). Fabric API is not downloaded again because VANTA already needs it, and an install is refused with a message when the project is marked incompatible with something you have
- Every download comes from the file address Modrinth publishes, over HTTPS, and is checked against the SHA-512 Modrinth publishes; a file that does not match is deleted. A mod is only put into `mods/` when it and all of its dependencies were downloaded and checked
- Installed tab: everything in `mods/`, `shaderpacks/` and `resourcepacks/`. Mods VANTA installed can be removed and switched off and on (the file is renamed between `.jar` and `.jar.disabled`, which Fabric does not load). Files you added yourself are listed by their file name and are never changed or deleted by VANTA
- `config/vanta/modrinth.json` records every installed project (project, version, file, SHA-512, enabled, which project needs it); the VANTA Launcher reads and writes the same file
- After mods change, a *Restart required* banner with *Quit game*. When the VANTA Launcher started the game, the button says *Restart game*: VANTA writes `config/vanta/restart.request` and quits, and the launcher starts the game again
- After a shader pack is installed with Iris loaded, *Open shader settings* opens the Iris shader pack screen (if that is not possible, VANTA tells you to press O, the Iris shader key). Without Iris, the Shaders tab offers *Install Iris*. After a resource pack is installed, *Open resource packs* opens the resource pack screen to turn it on
- Download progress, results and errors appear as VANTA notifications; downloads run in the background, so the game keeps running

## Improved

- The Performance Center points to the Performance pack: VANTA's own presets only change vanilla video options, the pack's mods speed up rendering and game logic far beyond that
- `INSTALL.txt` in `vanta-client-1.1.0-mods.zip` mentions the in-game Mods & Shaders screen and the Performance pack, and says which Fabric installer to use on each system (the `.exe` on Windows, which needs no separate Java; the universal `.jar` with Java 21 on macOS and Linux) and that the installer asks which launcher to use when both Minecraft Launchers are installed

## Fixed

- With no worlds yet, *Singleplayer* opens Create New World; leaving it with *Cancel* or Escape showed the vanilla title screen instead of the VANTA main menu. Every way back to the title screen while no world is loaded now ends on the VANTA main menu, as returning from the world list, Multiplayer and the other vanilla screens already did

## Notes

- Mods & Shaders talks to Modrinth's public API (`api.modrinth.com`, files from `cdn.modrinth.com`) only while you use it: when the screen opens, when you search and when you install. VANTA sends no account and no personal data, only the request and a User-Agent naming VANTA Client
- The Performance pack mods are independent projects by their own authors, downloaded from Modrinth under their own licenses; VANTA does not ship them in its files. New or changed mods load after a restart; shader packs and resource packs work without one
- CI now also runs the full headless game test with the Performance pack loaded (the newest 1.21.11 Fabric versions on Modrinth at the time of the run), and a live check downloads the pack through VANTA's Modrinth client and verifies every SHA-512
- VANTA is a legitimate client: it contains no cheats, no combat automation, no packet manipulation and no anti-cheat bypasses
- Every file is published by the release workflow with its SHA-256 in the release manifest and in `SHA256SUMS.txt`; compare it before installing
