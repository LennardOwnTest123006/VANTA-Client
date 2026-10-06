---
title: Minecraft requirements
description: Which Minecraft edition and version VANTA supports, the account you need, and the operating system, graphics, memory and disk requirements.
order: 2
category: Getting started
---

## Minecraft Java Edition 1.21.11 only

VANTA Client 1.x is built for **Minecraft Java Edition 1.21.11** and nothing else:

- The mod declares `"minecraft": "~1.21.11"` in its `fabric.mod.json`; Fabric Loader refuses to load it on another
  version. This is deliberate: VANTA is compiled against the 1.21.11 game code with Mojang's official mappings, and
  a client that "mostly works" on another version is not something we want to ship.
- **Bedrock Edition** (Windows Store, consoles, mobile) is a different game and cannot load Fabric mods.
- Snapshots, pre-releases and release candidates are not supported.
- When Mojang publishes a new version, VANTA moves to it in a dedicated release; the Minecraft version is never
  changed silently. See [FAQ](faq.md).

The VANTA Launcher installs exactly 1.21.11 with Fabric Loader 0.19.5 and Fabric API 0.141.6+1.21.11. Using VANTA
with another Fabric profile requires the same versions — see [Fabric](fabric.md).

## Account

You need a **Microsoft account that owns Minecraft Java Edition**. With *PLAY via Minecraft Launcher* / *Use with
Minecraft Launcher* or a manual Fabric installation the official Minecraft Launcher signs you in and checks ownership, as it does for vanilla
Minecraft.

The VANTA Launcher's own sign-in uses Microsoft's device code flow, obtains Xbox Live and Minecraft tokens exactly like
the official launcher, and checks the ownership (`entitlements`) and profile endpoints before it will start the game.
It needs a Microsoft application id approved by Mojang, which the published launcher does not include, so it is off
until one is configured ([Launcher → Microsoft client id](launcher.md#microsoft-client-id)).

- Legacy Mojang accounts no longer work anywhere; they had to be migrated to Microsoft accounts.
- Accounts without an Xbox Live profile, child accounts without family consent and accounts in regions where Xbox
  Live is unavailable are reported with a clear message during sign-in (see [Troubleshooting](troubleshooting.md#microsoft-sign-in-errors)).
- If you play Java Edition only through **PC Game Pass**, the ownership check used by third-party launchers may not
  recognise the subscription. In that case play through the official launcher: *PLAY via Minecraft Launcher* or
  *Use with Minecraft Launcher* ([Launcher](launcher.md#use-with-the-minecraft-launcher)) or a Fabric profile
  ([Fabric → Manual installation](fabric.md#manual-installation-into-an-existing-fabric-profile)).
- Offline play is not a way around ownership: the launcher only offers an "offline session" for an account that has
  already signed in successfully, by reusing its verified name and UUID.

## Operating system

| System | Support |
| --- | --- |
| Windows 10 / 11, 64-bit | primary platform: `.msi`/`.exe` installers and a portable app (all with the Java 21 runtime), a launcher jar for an installed Java 21; Java detection including the official launcher's bundled runtimes, DPAPI token encryption |
| Linux (x86-64) | app image `.tar.gz` with the Java 21 runtime and a launcher jar; the automated game test and the launcher's install-and-launch test run in CI on Ubuntu 24.04 |
| macOS, Apple Silicon | launcher jar for an installed Java 21; built and tested from the command line in CI, its window has not been tested; not signed or notarised, no `.dmg` |
| macOS on Intel, Linux on ARM | no launcher build; use the client with the [manual installation](installation.md#c-manual-installation) in a launcher that runs Fabric 1.21.11 there |
| 32-bit systems | not supported (Java 21 for Minecraft is 64-bit only) |

Each launcher jar contains JavaFX for one system only; the Windows jar does not run on Linux or macOS and the other
way round (started on the wrong system it names the right file and exits with code 1). The client jar
(`vanta-client-<version>.jar`, for example `vanta-client-1.2.0.jar`) is the same on every system.

## Graphics

Minecraft 1.21.11 requires **OpenGL 3.2 or newer** with up-to-date drivers; VANTA does not change that. Integrated
graphics from the last ten years are fine for the LOW/BALANCED presets. If the game crashes on start with messages
about `GLFW`, `OpenGL` or `pixel format`, update the graphics driver first ([Troubleshooting](troubleshooting.md#the-game-crashes-on-start)).

VANTA draws its interface and HUD with the game's own renderer; it adds no shaders and does not replace the
rendering pipeline.

## Memory

- Mojang recommends 4 GB of RAM for the game; 8 GB total system memory gives a comfortable margin for the game,
  the launcher and your browser.
- The launcher assigns the game a maximum heap of **half your physical memory, between 2 GB and 8 GB** (4 GB when
  the memory cannot be determined). Change it in *Settings → Memory*; more than 8 GB rarely helps vanilla Minecraft
  and can make garbage-collection pauses longer.
- The launcher itself needs around 300 MB while installing.

## Disk space

- Minecraft 1.21.11 with assets and libraries: roughly 1 GB.
- Java 21 runtime installed by the launcher: roughly 50 MB (Temurin JRE).
- Worlds grow with play; keep a few GB free. The launcher checks free space before it starts an installation.
- If you already have the official launcher installed, enable *Settings → Share official Minecraft files* to reuse
  its libraries and assets read-only instead of downloading them again.

## Network

The launcher downloads from `piston-meta.mojang.com`, `piston-data.mojang.com`, `resources.download.minecraft.net`,
`libraries.minecraft.net`, `meta.fabricmc.net`, `maven.fabricmc.net`, `api.adoptium.net` (Java), the Microsoft and
Xbox Live sign-in endpoints, `api.minecraftservices.com` and GitHub (release downloads). After installation the game
only needs the network for multiplayer and the usual Minecraft services. VANTA itself contacts no server of its own.
