---
title: VANTA documentation
description: Overview of the VANTA Client and Launcher documentation, with the pinned versions and links to every guide.
order: 0
category: Getting started
---

VANTA Client is a legitimate, purely client-side Fabric client for **Minecraft Java Edition 1.21.11**. It adds a
refined interface, a customizable HUD, a performance center, profiles, cosmetics, local statistics and accessibility
options on top of vanilla Minecraft. It adds **no cheats** and sends **no data** anywhere.

| Component | Version |
| --- | --- |
| Minecraft Java Edition | 1.21.11 (exactly) |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.141.6+1.21.11 |
| Java | 21 |
| VANTA Client | 1.0.0 |
| VANTA Launcher | 1.0.0 |
| Platform | Windows 10/11 (installer); Linux and macOS with the portable jar |

## Quick start

1. Check the [requirements](minecraft-requirements.md): a Microsoft account that owns Minecraft Java Edition, a
   64-bit system with OpenGL 3.2 and Java 21 (the launcher can install it).
2. Follow the [installation guide](installation.md): download the launcher, verify the SHA-256, install, sign in,
   press **PLAY**.
3. In the game, open the VANTA menu with **Right Shift** (or the OPTIONS button in the main menu) and start with
   [Settings](settings.md) and the [HUD editor](hud.md).

## Guides

### Getting started

- [Installation](installation.md) — download, verify, install, first launch, where files live
- [Minecraft requirements](minecraft-requirements.md) — version, account, operating system, GPU, memory
- [Java 21](java-21.md) — why Java 21, how to check and install it, PATH and JAVA_HOME problems
- [Fabric](fabric.md) — what Fabric Loader and Fabric API are, using VANTA with other mods, manual installation

### Launcher

- [VANTA Launcher](launcher.md) — Play, Microsoft sign-in, Java detection, Versions, Logs, Settings, updates,
  command line reference

### Client

- [Settings](settings.md) — categories, search, reset, tooltips, keyboard navigation, `settings.json`
- [HUD and crosshair](hud.md) — every widget, the HUD editor, presets, keyboard shortcuts, the crosshair customizer
- [Profiles](profiles.md) — built-in profiles, create, duplicate, export and import safely
- [Performance Center](performance.md) — what it measures, the four presets with their exact vanilla values, render
  distance suggestions, honest limits
- [Keybinds](keybinds.md) — VANTA keys, rebinding, conflict detection, zoom
- [Cosmetics](cosmetics.md) — UI themes, menu backgrounds, HUD themes, particles, badges, cosmetic packs
- [Statistics and privacy](statistics-and-privacy.md) — what is recorded, where, how to disable, export or delete it
- [Accessibility](accessibility.md) — UI scale, reduced motion, high contrast, larger text, colour-blind palettes,
  keyboard navigation

### Reference

- [Building from source](building-from-source.md) — the short version of BUILDING.md
- [Privacy](privacy.md) — client, launcher and website
- [Terms of use](terms.md)

### Help

- [Troubleshooting](troubleshooting.md) — Java not found, checksum mismatch, Microsoft sign-in errors, crashes, mod
  conflicts, where the logs are, how to report
- [FAQ](faq.md) — servers, Sodium/OptiFine, supported versions, price, data

## Getting help

Support channels (e-mail, Discord) are configured by the project maintainers and shown on the Support page of the
website when available. GitHub Issues are always open for bug reports and feature requests:
[github.com/LennardOwnTest123006/VANTA-Client/issues](https://github.com/LennardOwnTest123006/VANTA-Client/issues).
Please read [Troubleshooting](troubleshooting.md) first and include the information listed there.

## A note on honesty

Downloads only exist once the release workflow has published them with SHA-256 checksums. Until then the website
shows "not published yet" rather than a link. Screenshots on the website are real captures from the automated game
test; if there are none yet, the page says so. Everything described in these pages is implemented in the version
named above; features that do not exist are not documented as if they did.
