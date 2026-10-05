---
product: client
version: 1.0.0
date: 2026-10-04
title: VANTA Client 1.0.0
minecraftVersion: 1.21.11
---

First public release of VANTA Client for Minecraft Java Edition 1.21.11 (Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, Java 21).

## Added

- VANTA main menu replacing the vanilla title screen: PLAY (continues your last world when there is one), SINGLEPLAYER, MULTIPLAYER, OPTIONS, LANGUAGE, RESOURCE PACKS, ACCESSIBILITY and QUIT, quick access to Profiles, HUD editor, Statistics, Cosmetics, Search and About, five background styles, optional particles, a version label and a setting to fall back to the vanilla menu
- Customizable HUD with movable widgets: FPS, ping, coordinates, direction, biome, server, CPS, clock, armor, item durability, potion effects, keystrokes, memory, CPU, entity count and Minecraft version
- HUD editor with drag and drop, nine anchors, 2 px grid and edge snapping, resize handles, scale, opacity, colours, per-widget options, undo/redo, keyboard nudging and the built-in presets Default, Minimal, PvP, Streamer and Performance
- Performance Center with live FPS, frame time (average and 1 % low), memory, render and simulation distance and entity count, the LOW, BALANCED, HIGH and ULTRA presets built from vanilla video options with a before/after table, a frame-rate limit chooser and render-distance suggestions that are never applied without your consent
- Settings with ten categories (General, Video, Audio, Controls, HUD, Performance, Accessibility, Language, Cosmetics, Privacy), live search with fuzzy matching, tooltips, per-setting reset, modified indicators and full keyboard navigation; vanilla video, audio and control options are edited in place
- Global search (command palette) over settings, screens and key bindings
- Keybind manager with search, rebinding, reset, a VANTA category and conflict detection across all key mappings
- Crosshair customizer with six shapes (cross, dot, circle, square, chevron, plus-dot), size, thickness, gap, outline, opacity, colours, dynamic expansion, third-person hiding and the presets Default, Dot, Thin, Bold, Circle and Precision
- Cosmetics: five UI themes (VANTA Dark, Midnight, Aurora, Ember, Graphite), menu backgrounds, HUD themes (Clean, Glass, Outline, Minimal), menu particles and profile badges; all purely visual, all free
- Profiles (Default, PvP, Building, Performance, Recording) storing settings, HUD layout, key overrides, crosshair and cosmetics, with duplicate, rename, export to JSON and validated import
- Notifications, resource pack manager with enable/disable/reorder/apply, local statistics dashboard with privacy controls and export, accessibility options (UI scale, reduced motion, high contrast, larger text, reduced transparency, colour-blind palettes) and a zoom key
- Mods folder bundle `vanta-client-1.0.0-mods.zip` with the VANTA Client and Fabric API 0.141.6+1.21.11 jars in `mods/`, an `INSTALL.txt` and a `SHA256SUMS` file; Fabric API, which VANTA requires, is also published unmodified as its own file
- Three ways to install: the VANTA Launcher; the launcher's "Use with Minecraft Launcher", which adds the profile "VANTA 1.21.11" to the official Minecraft Launcher; or by hand with the Fabric installer and the two jars from the mods bundle

## Notes

- VANTA is a legitimate client: it contains no cheats, no combat automation, no packet manipulation and no anti-cheat bypasses, and it only changes vanilla options when you ask it to
- All statistics stay on your computer; nothing is sent anywhere
- Every file is published by the release workflow with its SHA-256 in the release manifest and in `SHA256SUMS.txt`; compare it before installing
