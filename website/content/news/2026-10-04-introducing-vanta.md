---
title: Introducing VANTA Client
date: 2026-10-04
summary: VANTA is a legitimate Fabric client for Minecraft Java Edition 1.21.11 with a refined interface, a customizable HUD, a performance center and local-only statistics. Here is what it is, what it is not, and how releases will work.
author: VANTA team
tags: [announcement, client, launcher]
---

VANTA Client is a modern Fabric client for **Minecraft Java Edition 1.21.11**. It runs on Fabric Loader 0.19.5 with
Fabric API 0.141.6+1.21.11 and Java 21, and it is built on top of vanilla Minecraft rather than against it.

## What VANTA is

- **A refined interface.** The vanilla title screen is replaced by the VANTA main menu; settings get categories,
  search, tooltips and keyboard navigation; every VANTA screen uses the same dark, quiet design language.
- **A customizable HUD.** Seventeen widgets (FPS, ping, coordinates, direction, biome, server, CPS, clock, armor,
  item durability, potion effects, keystrokes, memory, CPU, entity count, Minecraft version and the crosshair) that you
  arrange in a drag-and-drop editor with anchors, snapping, scale, opacity, colours and presets.
- **A Performance Center.** Live FPS, frame time, memory, render and simulation distance and entity count, plus four
  presets (LOW, BALANCED, HIGH, ULTRA) that change *vanilla* video options and show you exactly which ones before you
  apply them. VANTA does not replace the renderer and makes no performance claims it cannot keep.
- **Profiles, cosmetics, accessibility.** Switchable profiles that bundle settings, HUD, key overrides and visuals;
  UI themes, menu backgrounds, HUD themes, particles and badges that are purely cosmetic and free; UI scale, reduced
  motion, high contrast, larger text, reduced transparency and colour-blind palettes.
- **Local statistics.** Playtime, sessions, FPS, worlds, servers, distance, blocks broken and placed, kept in a JSON
  file on your computer with switches to disable, export or delete all of it.
- **A launcher.** The VANTA Launcher installs Minecraft 1.21.11, Fabric and the client from the official sources
  with checksum verification, finds or installs Java 21 and keeps the client up to date. Signing in inside VANTA
  needs a Microsoft application id approved by Mojang, which the project does not have yet; until then, "Use with
  Minecraft Launcher" adds a "VANTA 1.21.11" profile to the official Minecraft Launcher, which signs you in.

## What VANTA is not

VANTA contains **no cheats**: no combat automation, no packet manipulation, no anti-cheat bypasses, no player
tracking and nothing else that gives an unfair advantage on multiplayer servers. It does not bundle Sodium, OptiFine
or other mods, does not include AI features, and never sends data anywhere. Whether a server allows client mods at
all is that server's decision; VANTA gives you nothing a server could reasonably object to, but it cannot speak for
server rules.

## How releases will work

**Update, 2026-10-05:** VANTA Client 1.0.0 and VANTA Launcher 1.0.0 are published. The
[Download page](/download) lists every file with its size, SHA-256 and a direct link.

Every release is built by the public release workflow in the repository: the client jar on Linux, the launcher
installers on Windows and the Linux and Apple Silicon macOS launcher builds on their own systems. The workflow
computes SHA-256 checksums, publishes the files as a GitHub Release and updates the release manifests that the
Download page and the launcher read. **No download link exists until that workflow has
run**, so until then the Download page says "not published yet" instead of linking to something that is not there.
Once a release is published, the Download page lists every file of it with size, SHA-256 and a direct link, and the
changelog shows its release notes.

## Open source

The code is MIT licensed and developed in the open on GitHub. Bug reports, documentation fixes and features that fit
the no-cheats principle are welcome; see the contributing guide in the repository. The Space Grotesk and Inter fonts
are bundled under the SIL Open Font License, and the VANTA name and logo stay with the project.

Thanks for reading. Your Minecraft, refined.
