# Changelog

All notable changes to VANTA Client, VANTA Launcher and the website are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the projects use
[Semantic Versioning](https://semver.org/).

Machine-readable release notes live in `website/content/changelog/` and are rendered on the website.

## [Unreleased]

### VANTA Client 1.0.0 — Minecraft 1.21.11 · Fabric Loader 0.19.5 · Fabric API 0.141.6+1.21.11 · Java 21

#### Added
- Premium VANTA main menu replacing the vanilla title screen (PLAY, SINGLEPLAYER, MULTIPLAYER, OPTIONS, LANGUAGE,
  RESOURCE PACKS, ACCESSIBILITY, QUIT) with version label, vanilla fallback setting
- Customizable HUD with movable widgets (FPS, ping, coordinates, direction, biome, server, CPS, clock, armor,
  item durability, potion effects, keystrokes, memory, CPU, entity count, Minecraft version) and a HUD editor with
  drag, scale, opacity, colors and presets
- Performance Center with live FPS / frame time / memory / render and simulation distance / entity count and the
  LOW, BALANCED, HIGH and ULTRA presets built from vanilla video options
- Settings system with categories, search, tooltips, reset-to-default and keyboard navigation
- Keybind manager with search, rebinding, reset and conflict detection on top of vanilla key mappings
- Crosshair customizer with shape, size, thickness, gap, outline, opacity, color and presets
- Cosmetics: UI themes, menu backgrounds, HUD themes, menu particles and profile badges (visual only)
- Local profiles (DEFAULT, PVP, BUILDING, PERFORMANCE, RECORDING) storing settings, HUD, keybinds and visuals,
  with JSON import/export
- Notification system, global settings search, resource pack manager, local statistics dashboard with privacy
  controls, accessibility options (UI scale, reduced motion, high contrast, larger text, reduced transparency)

### VANTA Launcher 1.0.0
- Detects Java 21, installs Minecraft 1.21.11 and Fabric Loader from official sources with checksum verification,
  installs Fabric API and the VANTA Client jar, Microsoft account sign-in (device code flow), logs, settings,
  version information, update checks against SHA-256 verified release manifests

### Website 1.0.0
- Official website with download center, features, performance, screenshots, changelog, news, searchable
  documentation, support center, FAQ, about, privacy and terms pages; Netlify deployment configuration
