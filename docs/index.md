---
title: VANTA documentation
description: Overview of the VANTA Client and Launcher documentation, with the pinned versions and links to every guide.
order: 0
category: Getting started
---

VANTA Client is a legitimate, purely client-side Fabric client for **Minecraft Java Edition 1.21.11**. It adds a
refined interface, a customizable HUD, a performance center, Mods & Shaders with a one-click Performance pack from
Modrinth, profiles, waypoints, cosmetics, local statistics, accessibility options and, from 1.4.0 on, **Vanta Nexus**:
an assistant that changes your HUD, settings, profiles, waypoints and Lab features when you ask, run by a **strictly
local** AI on your own PC. It adds **no cheats** and sends **no data** about you anywhere. The client contacts exactly
these hosts and nothing else, ever: Modrinth while you use Mods & Shaders and, only when you install the Local AI,
`github.com` (the llama.cpp runtime) and `huggingface.co` (the model); the assistant's prompts go to `127.0.0.1` only,
with no cloud AI, no API key, no account and no telemetry ([Privacy](privacy.md)).

| Component | Version |
| --- | --- |
| Minecraft Java Edition | 1.21.11 (exactly) |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.141.6+1.21.11 |
| Java | 21 |
| VANTA Client | 1.4.0 |
| VANTA Launcher | 1.4.0 |
| Launcher platforms | Windows 10/11 x64 (the `.msi` installer, portable app, jar); Linux x64 (app image, jar); Apple Silicon macOS (jar) |
| Local AI (optional) | llama.cpp `llama-server` b11429 (MIT) with Qwen3-1.7B Q8_0 (Apache-2.0), downloaded once after you click Install, about 1.85 GB; Windows x64 and ARM64, Linux x64 and ARM64, macOS Apple Silicon and Intel ([Local AI](local-ai.md)) |
| Downloads | GitHub Releases `client-v1.5.0` and `launcher-v1.5.0` (released 2026-10-10; the earlier releases stay available), the full release zip `VantaClient-1.5.0-Release.zip` on the release [`v1.5.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/v1.5.0) (both releases plus the documentation in one archive, see [the full release zip](installation.md#the-full-release-zip)), and the Download page of [vanta-client.netlify.app](https://vanta-client.netlify.app) |

## Quick start

1. Check the [requirements](minecraft-requirements.md): a Microsoft account that owns Minecraft Java Edition, a
   64-bit system with OpenGL 3.2 and Java 21 (the launcher can install it).
2. Follow the [installation guide](installation.md): download the launcher for your system, verify the SHA-256,
   install it, close the official Minecraft Launcher and click **PLAY via Minecraft Launcher**. It adds the profile
   *VANTA 1.21.11* (with the Performance pack) to the official Minecraft Launcher and opens it; choose that profile
   there and press Play. (Signing in inside the VANTA Launcher needs a Microsoft client id that the published builds
   do not include; the guide explains this and the manual alternative with the mods bundle.)
3. In the game, open the VANTA settings with **Right Shift** (or the OPTIONS button in the main menu) and start with
   [Settings](settings.md) and the [HUD editor](hud.md). **Mods & Shaders** in the main menu adds mods, shader packs
   and resource packs ([Mods & Shaders](mods-and-shaders.md)). Hold **C** to zoom; in Creative mode C is also
   vanilla's *Save Hotbar Activator*, see [Keybinds](keybinds.md#zoom).
4. **Vanta Nexus** (main menu button, or **N** in a world) gathers the assistant, the HUD Designer, profiles, live
   performance values, waypoints and Vanta Lab in one screen ([Vanta Nexus](nexus.md)). The assistant needs the
   [Local AI](local-ai.md), which the VANTA Launcher offers at its first start and Nexus offers with *Install Local
   AI*; nothing is downloaded until you click, and everything else works without it.

## Guides

### Getting started

- [Installation](installation.md): the three ways to install, downloads per system, the full release zip, verify,
  first start, where files live
- [Minecraft requirements](minecraft-requirements.md): version, account, operating system, GPU, memory
- [Java 21](java-21.md): why Java 21, how to check and install it, PATH and JAVA_HOME problems
- [Fabric](fabric.md): what Fabric Loader and Fabric API are, using VANTA with other mods, manual installation

### Launcher

- [VANTA Launcher](launcher.md): PLAY, PLAY via Minecraft Launcher, Use with the Minecraft Launcher, the Mods page,
  the Performance pack setting, the Local AI (first-start offer, Settings section, Home status line, command line
  flags), Microsoft sign-in, Java detection, Versions, Logs, Settings, the releases URL, updates, start-up errors,
  command line reference

### Client

- [Vanta Nexus](nexus.md): the seven sections, how the strictly local assistant works, every action it can take
  with examples, what it cannot do, undo, the transcript file, the N key, the command palette entry
- [Local AI](local-ai.md): llama.cpp `llama-server` and the Qwen3-1.7B model: what is downloaded from where and how
  big, SHA-256 verification, the two install paths (launcher and game), the folders, offline use, idle timeout and
  settings, verify / reinstall / remove, unsupported systems, licences
- [Waypoints](waypoints.md): the waypoint store and per-world keys, screen markers and their settings, the beams of
  Vanta Lab, the Waypoints section of Nexus, the assistant's waypoint actions
- [Settings](settings.md): categories (including Vanta Nexus, Waypoints and Vanta Lab), search, reset, tooltips,
  keyboard navigation, `settings.json`
- [HUD and crosshair](hud.md): every widget (including the frame time graph of Vanta Lab), the HUD editor, presets,
  the HUD Designer of Nexus and its six presets, keyboard shortcuts, the crosshair customizer
- [Profiles](profiles.md): the seven built-in profiles, create, duplicate, export and import safely, the assistant's
  profile actions
- [Performance Center](performance.md): what it measures, the five presets with their exact vanilla values, the
  one-click Boost FPS, Smart Boost, the Performance section of Nexus, render distance suggestions, the Performance
  pack, honest limits
- [Mods & Shaders](mods-and-shaders.md): mods, shader packs and resource packs from Modrinth in game and in the
  launcher, the Performance pack, Iris shaders, disabling and removing, restart, what is sent to Modrinth
- [Keybinds](keybinds.md): VANTA keys, rebinding, conflict detection, zoom
- [Cosmetics](cosmetics.md): UI themes, menu backgrounds, HUD themes, particles, badges, cosmetic packs
- [Statistics and privacy](statistics-and-privacy.md): what is recorded, where, how to disable, export or delete it
- [Accessibility](accessibility.md): UI scale, reduced motion, high contrast, larger text, colour-blind palettes,
  keyboard navigation

### Reference

- [Building from source](building-from-source.md): the short version of BUILDING.md
- [Privacy](privacy.md): client, launcher and website
- [Terms of use](terms.md)

### Help

- [Troubleshooting](troubleshooting.md): "Not published yet", SmartScreen and Smart App Control, a launcher that
  does not start, which launcher file, the Minecraft Launcher profile, Java not found, checksum mismatch, sign-in
  errors, crashes, mod conflicts, 30 FPS after a 1.1.0 preset or profile, short freezes, graphics shows Custom,
  buttons in small windows, Singleplayer worlds, Mods & Shaders, the Local AI (download, checksum, unsupported
  system, llama-server, slow answers, the log file), logs
- [FAQ](faq.md): where to download, servers, the Performance pack and shaders, Vanta Nexus and the Local AI (cloud,
  cost, account, size, offline, removal), supported versions, price, data

## Getting help

Support channels (e-mail, Discord) are configured by the project maintainers and shown on the Support page of the
website when available. GitHub Issues are always open for bug reports and feature requests:
[github.com/LennardOwnTest123006/VANTA-Client/issues](https://github.com/LennardOwnTest123006/VANTA-Client/issues).
Please read [Troubleshooting](troubleshooting.md) first and include the information listed there.

## A note on honesty

Downloads exist only as files the release workflow has published on GitHub Releases, with SHA-256 checksums it
computed from the uploaded files and checked against the public links. A version the workflow has not published yet
is marked "not published yet" on the website rather than linked, and the Download page keeps offering the newest
published release meanwhile. The files are not code-signed, and Microsoft sign-in
inside the VANTA Launcher needs a client id the project does not have; both are documented where they matter. Screenshots on the website are real captures from the automated game
test; if there are none yet, the page says so. Everything described in these pages is implemented in the version
named above; features that do not exist are not documented as if they did.
