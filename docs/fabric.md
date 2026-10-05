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

**With *Use with Minecraft Launcher*** the VANTA Launcher does steps 3 and 4 the same way (into the same
`instances/vanta-1.21.11/mods/`), writes the Fabric profile from step 1 unchanged as
`versions/fabric-loader-0.19.5-1.21.11/fabric-loader-0.19.5-1.21.11.json` (plus the empty `.jar` next to it) into the
official Minecraft folder — the same two files the official Fabric installer writes — and, also like the Fabric
installer, adds the profile *VANTA 1.21.11* to every profiles file of the Minecraft Launcher that exists there
(`launcher_profiles.json` and/or `launcher_profiles_microsoft_store.json`). The Minecraft Launcher then downloads the
Fabric libraries, Minecraft and Java itself. Details:
[Launcher → Use with the Minecraft Launcher](launcher.md#use-with-the-minecraft-launcher).

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

The release `client-v1.0.1` has what you need: `vanta-client-1.0.1-mods.zip` contains both mods in a `mods/` folder
(`vanta-client-1.0.1.jar` and `fabric-api-0.141.6+1.21.11.jar`) together with `INSTALL.txt` and a `SHA256SUMS` file.
`INSTALL.txt` lists the steps below for the official Minecraft Launcher, starting with step 0: start the official
Minecraft Launcher once. The two jars are also published on their own; `fabric-api-0.141.6+1.21.11.jar` is the
unmodified FabricMC release (Apache-2.0). Verify what you download as described in
[Installation](installation.md#2-verify-the-checksum).

**Official Minecraft Launcher**

1. Start the official Minecraft Launcher once if you never have, then close it: the Fabric installer needs the
   Minecraft folder and the profiles file it creates and otherwise stops with "No launcher directory found!" (no
   Minecraft folder yet) or "No launcher profile.json found!". Then run the Fabric installer from fabricmc.net
   (*Client* tab), choose Minecraft **1.21.11** and Loader **0.19.5**, keep **Create profile** checked and install.
   This creates a profile listed as **fabric-loader-1.21.11** (its version is `fabric-loader-0.19.5-1.21.11`) in
   `launcher_profiles.json`, or in `launcher_profiles_microsoft_store.json` for the Minecraft Launcher from the
   Microsoft Store or the Xbox app.
2. Unzip `vanta-client-1.0.1-mods.zip` and check it with its `SHA256SUMS` (`sha256sum -c SHA256SUMS` on Linux,
   `shasum -a 256 -c SHA256SUMS` on macOS; on Windows
   `Get-FileHash mods\*.jar -Algorithm SHA256 | Format-List Hash, Path` in PowerShell, which prints the full path of
   each jar, then compare the hash of both jars with the line for the same file in `SHA256SUMS`).
3. Copy both jars from its `mods/` folder into the `mods/` folder of the game directory (`%APPDATA%\.minecraft\mods` on
   Windows, `~/.minecraft/mods` on Linux, `~/Library/Application Support/minecraft/mods` on macOS). Create the folder
   if it does not exist.
4. Select the Fabric profile in the official launcher and play. VANTA writes its files to `config/vanta/` inside
   that game directory.

If you have the VANTA Launcher, *Use with Minecraft Launcher* does the Fabric and mods part (steps 1 to 3, apart
from starting the Minecraft Launcher once) for you, with its own profile and game folder
([Launcher](launcher.md#use-with-the-minecraft-launcher)). Like the Fabric installer, it adds that profile to
`launcher_profiles.json`, to `launcher_profiles_microsoft_store.json` or to both, whichever your Minecraft folder has.

**Prism Launcher / MultiMC**

1. Create a 1.21.11 instance, open *Edit → Version → Install Fabric* and pick Loader 0.19.5.
2. *Mods → Add file*: the two jars from the mods bundle (Fabric API 0.141.6+1.21.11 and the VANTA jar).
3. Launch the instance.

**Without the VANTA Launcher you lose**: automatic updates with checksum verification, rollback to previous client
versions, the Java detection/installation helpers and the Versions/Logs screens. Everything inside the game works
identically.

## Removing or disabling

Delete `vanta-client-<version>.jar` from `mods/` to remove VANTA; Fabric and your other mods are unaffected. To keep
VANTA installed but see the vanilla title screen, turn off *Settings → General → Replace the title screen*.
