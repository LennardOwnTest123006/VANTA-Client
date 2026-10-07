---
product: client
version: 1.2.1
date: 2026-10-07
title: VANTA Client 1.2.1
minecraftVersion: 1.21.11
---

Bug-fix release for one start-up crash. Smart FPS Booster 1.0.0, a third-party mod on Modrinth, lists Minecraft 1.21.11 but both of its Fabric builds were made for 1.21.4 and 1.21.8: they create key bindings the way Minecraft did before 1.21.9, so 1.21.11 stops while starting with "Could not execute entrypoint stage 'client' due to errors, provided by 'smartfpsbooster'". This happens with or without VANTA. The in-game installer now refuses such builds, and VANTA Launcher 1.2.1 switches them off before the game starts. Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11 and Java 21 are unchanged.

## Fixed

- The *Mods & Shaders* screen refuses to install a mod built for an older Minecraft: VANTA reads the downloaded jar, and when it still creates key bindings the pre-1.21.9 way without a code path for the newer ones, the download is deleted and the message says "This mod was built for an older Minecraft and would stop Minecraft 1.21.11 from starting, so it was not installed." The rest of the same install still goes in
- An identical copy of such a mod that is already in `mods/` is not recorded as installed, and a switched-off copy is never switched back on, also not when another mod needs it

## Notes

- If your game already stops at start, update the VANTA Launcher to 1.2.1 and open it once: it switches the mod off. Without the launcher, remove `smart-fps-booster-*.jar` from the `mods` folder; Fabric itself loads such a jar
- VANTA cannot change another author's mod. When Smart FPS Booster publishes a build made for 1.21.11, it can be installed again
- Nothing is code-signed; compare the SHA-256 from `SHA256SUMS.txt` before installing
