---
product: client
version: 1.0.1
date: 2026-10-05
title: VANTA Client 1.0.1
minecraftVersion: 1.21.11
---

Maintenance release of VANTA Client for Minecraft Java Edition 1.21.11 (Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, Java 21). It fixes the name of the Right Shift key binding and makes the install instructions in the mods bundle easier to follow.

## Improved

- `INSTALL.txt` in `vanta-client-1.0.1-mods.zip` now starts with step 0: install the official Minecraft Launcher and start it once, because until the launcher has created the Minecraft folder and its profile file the Fabric installer stops with "No launcher directory found!" or "No launcher profile.json found!"
- `INSTALL.txt` says to keep "Create profile" checked in the Fabric installer before clicking Install, so the `fabric-loader-1.21.11` profile appears in the Minecraft Launcher
- `INSTALL.txt` shows how to check both jars on Windows, with PowerShell (`Get-FileHash mods\*.jar -Algorithm SHA256 | Format-List Hash, Path`, which prints the full path of each jar) or with `certutil` for the VANTA Client jar and the Fabric API jar, and compare the hashes with `SHA256SUMS`
- `INSTALL.txt` explains that the zoom key C is also vanilla's "Save Hotbar Activator" (Creative mode only) and how to rebind either one in Controls; the default zoom key stays C

## Fixed

- The Right Shift key binding was called "Open VANTA menu" with the description "Opens the VANTA hub screen", but it opens the VANTA settings. It is now "Open VANTA settings" / "Opens the VANTA settings" in the controls screen and the keybind manager, and `/vanta menu` reports "Opening the VANTA settings…"

## Notes

- The file sizes in the GitHub release notes now use decimal units with one decimal place (1 MB = 1,000,000 bytes, rounded half up, for example "1.4 MB"), the same numbers the download page shows
- VANTA is a legitimate client: it contains no cheats, no combat automation, no packet manipulation and no anti-cheat bypasses
- Every file is published by the release workflow with its SHA-256 in the release manifest and in `SHA256SUMS.txt`; compare it before installing
