---
title: Fabric
description: What Fabric Loader 0.19.5 and Fabric API 0.141.6+1.21.11 are, how the VANTA Launcher installs them, using VANTA with other Fabric mods, and manual installation into an existing profile.
order: 4
category: Getting started
---

## What Fabric is

**Fabric Loader** is a lightweight mod loader for Minecraft Java Edition. It starts the game with the official client
jar and loads mods from the `mods/` folder; it is developed by FabricMC and is not affiliated with Mojang. VANTA
Client is a Fabric mod and requires:

| Component | Version | Role |
| --- | --- | --- |
| Fabric Loader | **0.19.5** | loads the game and the mods; provides the Mixin system VANTA uses for its few game hooks |
| Fabric API | **0.141.6+1.21.11** | the common library most Fabric mods share; VANTA uses it for key bindings, HUD rendering, client events and commands |

The version numbers are pinned for every VANTA release and shown in the launcher's Versions screen and in the About
screen in game. Fabric Loader generally accepts newer Fabric API builds for the same Minecraft version, but VANTA is
tested with the pinned pair only.

## How the VANTA Launcher installs Fabric

Installation is fully automatic and uses the same sources the official Fabric installer uses:

1. The launcher downloads the Fabric profile for Minecraft 1.21.11 / Loader 0.19.5 from `meta.fabricmc.net`
   (`/v2/versions/loader/1.21.11/0.19.5/profile/json`). The profile lists the loader libraries (`fabric-loader`,
   `intermediary`, Sponge Mixin, ASM, …) and the main class `net.fabricmc.loader.impl.launch.knot.KnotClient`.
2. Each library is downloaded from `maven.fabricmc.net` (or the repository the profile names) and verified: when the
   profile carries no hash, the launcher fetches the `.sha1`/`.sha256` sibling file from the Maven repository and
   checks against that.
3. Fabric API `0.141.6+1.21.11` is downloaded from the Fabric Maven together with its `.sha256` and placed in
   `instances/vanta-1.21.11/mods/`.
4. The VANTA Client jar from the release manifest (verified by SHA-256) is placed next to it.
5. The game is launched with the Fabric main class, the Fabric libraries first on the classpath, then the vanilla
   libraries and the client jar, and `--version vanta-1.21.11`.

Verified files are never downloaded twice; a checksum mismatch deletes the file and aborts with an error.

## Using VANTA with other Fabric mods

VANTA is an ordinary Fabric mod and coexists with others:

- Put additional mods for **1.21.11** into `instances/vanta-1.21.11/mods/` (launcher) or your profile's `mods/`
  folder. The launcher's *Open game folder* button takes you there.
- Mods must match the Minecraft version; Fabric Loader shows a clear error at start if one does not.
- **Performance mods** (Sodium, Lithium, Iris and similar) are not bundled with VANTA and are not required by it.
  VANTA's Performance Center only changes vanilla options, so it does not conflict with them in principle, but we do
  not test or support third-party mod combinations. If something looks wrong, remove the other mods first.
- **Mods that replace the title screen** will compete with VANTA's main menu. Turn VANTA's off under
  *Settings → General → Replace the title screen* if you prefer the other mod's menu.
- **Mods that add HUD overlays** work alongside VANTA's HUD (VANTA registers its HUD element after the chat with
  Fabric's HUD API); overlapping widgets are a layout question, not a crash. Disable VANTA widgets you do not need in
  the [HUD editor](hud.md).
- VANTA does not require Mod Menu, Cloth Config or any other library beyond Fabric API, and does not add
  dependencies to your profile.

Report incompatibilities with the mod list included; see [Troubleshooting → Mod conflicts](troubleshooting.md#mod-conflicts).

## Manual installation into an existing Fabric profile

You can use VANTA without the VANTA Launcher. Any launcher that runs Fabric 0.19.5 on Minecraft 1.21.11 works;
the client jar does not care who started the game.

**Official Minecraft Launcher**

1. Run the Fabric installer from fabricmc.net, choose Minecraft **1.21.11** and Loader **0.19.5**, keep *Create
   profile* checked and install. This creates a "fabric-loader-0.19.5-1.21.11" profile.
2. Download **Fabric API 0.141.6+1.21.11** (from the Fabric API project page or Modrinth/CurseForge) and
   `vanta-client-<version>.jar` (VANTA Download page). Verify VANTA's SHA-256 as described in
   [Installation](installation.md#2-verify-the-checksum).
3. Put both jars into the `mods/` folder of the game directory (`%APPDATA%\.minecraft\mods` on Windows,
   `~/.minecraft/mods` on Linux, `~/Library/Application Support/minecraft/mods` on macOS). Create the folder if it
   does not exist.
4. Select the Fabric profile in the official launcher and play. VANTA writes its files to `config/vanta/` inside
   that game directory.

**Prism Launcher / MultiMC**

1. Create a 1.21.11 instance, open *Edit → Version → Install Fabric* and pick Loader 0.19.5.
2. *Mods → Add file* (or download via the built-in browser): Fabric API 0.141.6+1.21.11 and the VANTA jar.
3. Launch the instance.

**Without the VANTA Launcher you lose**: automatic updates with checksum verification, rollback to previous client
versions, the Java detection/installation helpers and the Versions/Logs screens. Everything inside the game works
identically.

## Removing or disabling

Delete `vanta-client-<version>.jar` from `mods/` to remove VANTA; Fabric and your other mods are unaffected. To keep
VANTA installed but see the vanilla title screen, turn off *Settings → General → Replace the title screen*.
