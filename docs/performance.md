---
title: Performance Center
description: What the Performance Center measures, the LOW, BALANCED, HIGH and ULTRA presets with their exact vanilla values, render distance suggestions, the Performance pack and honest limits.
order: 23
category: Client
---

The Performance Center is a dashboard for the numbers that matter and a quick way to apply a sensible set of
**vanilla** video options. It does not modify Minecraft's renderer itself and it makes no FPS promises: how fast your
game runs depends on your hardware and on Minecraft. For more than vanilla options can give, it points to the
[Performance pack](#the-performance-pack): optimisation mods from Modrinth that VANTA installs on request but does not
ship in its own files.

Open it from *Settings → Performance → Open Performance Center*, the performance key (unbound by default), the
main menu, or `/vanta perf`.

## What it measures

| Card | Data | Source |
| --- | --- | --- |
| FPS | current frames per second with a 60-sample sparkline | the game's own frame counter |
| Frame time | average and 1 % low over the recent samples | time between rendered frames |
| Memory | used / allocated / maximum JVM heap | the Java runtime |
| Render & simulation distance | current values with inline sliders | vanilla options (changing them here is identical to the video settings) |
| Entity count | entities loaded around you | the client world |
| CPU | process CPU load when the JVM exposes it, otherwise "n/a" | `OperatingSystemMXBean` |
| System | Java version, operating system, CPU count | the Java runtime |

Values are sampled every tick while the screen is open. The FPS and memory widgets of the [HUD](hud.md) show the same
data in game.

## Presets

A preset is a bundle of vanilla video options. Selecting one shows a **before → after table** of exactly which options
change; nothing happens until you press **Apply**, which writes the options through Minecraft's own settings (they end
up in `options.txt`) and shows a notification. The values, with the reasoning:

| Vanilla option | LOW | BALANCED | HIGH | ULTRA |
| --- | --- | --- | --- | --- |
| Graphics | Fast | Fancy | Fancy | Fancy |
| Render distance | 6 chunks | 10 chunks | 16 chunks | 24 chunks |
| Simulation distance | 6 chunks | 8 chunks | 12 chunks | 16 chunks |
| Max framerate | 60 | 120 | Unlimited | Unlimited |
| VSync | Off | Off | Off | Off |
| Smooth lighting | Off | On | On | On |
| Clouds | Off | Fast | Fancy | Fancy |
| Particles | Minimal | Decreased | All | All |
| Entity shadows | Off | On | On | On |
| Entity distance | 50 % | 100 % | 100 % | 125 % |
| Biome blend | Off (0) | 5×5 (2) | 9×9 (4) | 13×13 (6) |
| Mipmap levels | 0 | 2 | 4 | 4 |

- **Render and simulation distance** are the biggest levers: the number of chunks grows with the square of the
  distance. Simulation distance stays at or below render distance so you never simulate chunks you cannot see.
- **Max framerate**: a cap keeps weak GPUs cool and frame pacing even; strong machines run uncapped. "Unlimited" is
  vanilla's value 260.
- **VSync** is off in every preset so the frame-rate limit is the only cap; if you prefer VSync, choose it in the
  frame-rate limit chooser below.
- **Smooth lighting** costs chunk-building time, so only LOW turns it off. **Entity shadows**, **clouds** and
  **particles** are cheap wins on low-end hardware.
- **Biome blend** costs CPU when chunks are built; **mipmaps** cost VRAM and reduce texture flicker at distance.
- **ULTRA stays on Fancy, not Fabulous.** Fabulous graphics add translucency passes that cost a lot of GPU time for a
  small visual difference and conflict with some shader mods. Select Fabulous manually in Video settings if you want it.

Presets never touch FOV, GUI scale, view bobbing, brightness, menu blur, fullscreen/resolution, audio or controls.
Applying a preset also records it as *Settings → Performance → Last applied preset*, and the built-in
[profiles](profiles.md) each carry one.

## Frame-rate limit chooser

Next to the presets, a quick chooser writes the vanilla **Max framerate** and **VSync** options:

| Choice | Max framerate | VSync |
| --- | --- | --- |
| VSync | Unlimited | On (the monitor refresh rate caps the frame rate) |
| 60 | 60 | Off |
| 120 | 120 | Off |
| 144 | 140 (vanilla steps in tens) | Off |
| 240 | 240 | Off |
| Unlimited | Unlimited | Off |

## Individual options

The same screen lists the safe individual options as rows — frame-rate limit slider, VSync, particles, clouds, smooth
lighting, entity shadows, entity distance, biome blend, mipmaps, view bobbing — so you can fine-tune after a preset.
They are the vanilla options under a different roof; *Settings → Video* shows the complete list.

## Render distance suggestions

The advisor watches your average FPS against a target (the configured frame-rate limit, or 60 when unlimited):

| Condition | Suggestion |
| --- | --- |
| average FPS over the last 10 s stays below 75 % of the target | lower the render distance by 2 chunks (never below 4) |
| average FPS over the last 10 s stays above 160 % of the target, and the frame rate is not capped by VSync or a limit | raise the render distance by 2 chunks (never above 32) |
| a suggestion was made less than 60 s ago, or the distance just changed | nothing — the advisor waits for fresh samples |

A suggestion appears as a banner with **Apply suggestion**; it is **never applied automatically** unless you turn on
*Settings → Performance → Apply render distance suggestions automatically* (off by default). Turn the feature off
entirely with *Render distance suggestions*.

## The Performance pack

Below the cards the Performance Center shows *Want much more FPS?* with **Open Mods & Shaders**. Presets can only
make Minecraft render less; the **Performance pack** (from client 1.1.0 and launcher 1.1.0 on) installs separate
Fabric mods that make rendering and game logic faster:

| Mod | What it does |
| --- | --- |
| [Sodium](https://modrinth.com/mod/sodium) | replaces the rendering engine; the largest frame rate gain of the six |
| [Lithium](https://modrinth.com/mod/lithium) | faster game logic (ticking, physics, mob AI) |
| [FerriteCore](https://modrinth.com/mod/ferrite-core) | uses less memory |
| [ImmediatelyFast](https://modrinth.com/mod/immediatelyfast) | faster rendering of the HUD, text and entities |
| [EntityCulling](https://modrinth.com/mod/entityculling) | skips entities and block entities you cannot see |
| [Iris Shaders](https://modrinth.com/mod/iris) | loads shader packs and works together with Sodium |

- **In the game**: *Mods & Shaders → Performance pack*, untick what you do not want, **Install performance pack**,
  then restart the game.
- **In the VANTA Launcher**: installed by default with PLAY, *PLAY via Minecraft Launcher* and *Use with Minecraft
  Launcher*; *Settings → Game → Install the performance pack* turns it off.

The newest Fabric version of each mod for Minecraft 1.21.11 is downloaded from Modrinth at install time and checked
with the SHA-512 Modrinth publishes. The mods are independent projects by their own authors under their own licences.
Everything about installing, disabling and removing them is in
[Mods & Shaders](mods-and-shaders.md#the-performance-pack).

## Honest limits

- VANTA's presets change **vanilla options only**. Everything a preset does, you could do by hand in Video Settings.
- There is no claim such as "+200 % FPS", neither for the presets nor for the Performance pack. Measure on your own
  machine with the cards above.
- The Performance pack mods are not part of VANTA; VANTA installs them on request. CI runs VANTA's game test with
  them loaded; other mod combinations are not tested
  ([Fabric → Using VANTA with other mods](fabric.md#using-vanta-with-other-fabric-mods)).
- The CPU card shows "n/a" when the Java runtime does not provide process CPU load.
