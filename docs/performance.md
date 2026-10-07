---
title: Performance Center
description: What the Performance Center measures, the five presets with their exact vanilla values (never a frame-rate cap), Boost FPS, render distance suggestions, the Performance pack and honest limits.
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

| Vanilla option | MAX FPS | LOW | BALANCED | HIGH | ULTRA |
| --- | --- | --- | --- | --- | --- |
| Graphics | Fast | Fast | Fancy | Fancy | Fancy |
| Render distance | 5 chunks | 6 chunks | 10 chunks | 16 chunks | 24 chunks |
| Simulation distance | 5 chunks | 6 chunks | 8 chunks | 12 chunks | 16 chunks |
| Smooth lighting | Off | Off | On | On | On |
| Clouds | Off | Off | Fast | Fancy | Fancy |
| Particles | Minimal | Minimal | Decreased | All | All |
| Entity shadows | Off | Off | On | On | On |
| Entity distance | 50 % | 50 % | 100 % | 100 % | 125 % |
| Biome blend | Off (0) | Off (0) | 5×5 (2) | 9×9 (4) | 13×13 (6) |
| Mipmap levels | 0 | 0 | 2 | 4 | 4 |
| Menu background blur | Off (0) | unchanged | unchanged | unchanged | unchanged |

- **Presets never set Max framerate or VSync** (from client 1.2.0 on). VANTA 1.1.0 shipped a 60 FPS cap with LOW and
  a 120 FPS cap with BALANCED, and the built-in *Performance* (LOW) and *Default* (BALANCED) profiles carried those
  caps along; a player who picked LOW to get *more* frames was capped at 60 and, with a driver-forced VSync at
  60 Hz on top of Minecraft's limiter, could land at exactly 30. The limit now lives only in the
  [frame-rate limit chooser](#frame-rate-limit-chooser); applying a preset leaves whatever limit and VSync you have.
  A cap that 1.1.0 already wrote stays in `options.txt` until you choose *Unlimited* (or *Boost FPS*) once
  ([Troubleshooting](troubleshooting.md#low-fps-or-exactly-30-fps-after-choosing-a-preset-or-profile-in-vanta-110)).
- **Render and simulation distance** are the biggest levers: the number of chunks grows with the square of the
  distance. Simulation distance stays at or below render distance so you never simulate chunks you cannot see; 5 is
  vanilla's minimum simulation distance, which is why MAX FPS stops there.
- **Smooth lighting** costs chunk-building time, so MAX FPS and LOW turn it off. **Entity shadows**, **clouds** and
  **particles** are cheap wins on low-end hardware.
- **Biome blend** costs CPU when chunks are built; **mipmaps** cost VRAM and reduce texture flicker at distance.
- **Menu background blur** runs a blur pass every frame a screen is open; only MAX FPS turns it off, the other
  presets leave it alone.
- **ULTRA stays on Fancy, not Fabulous.** Fabulous graphics add translucency passes that cost a lot of GPU time for a
  small visual difference and conflict with some shader mods. Select Fabulous manually in Video settings if you want it.

Presets never touch FOV, GUI scale, view bobbing, brightness, fullscreen/resolution, audio or controls, and never the
frame-rate limit or VSync. Applying a preset also records it as *Settings → Performance → Last applied preset*, and
the built-in [profiles](profiles.md) each carry one.

## Boost FPS

**Boost FPS** (from client 1.2.0 on), the button at the top of the Presets card (also in the search / command
palette), does in one click what most people open the Performance Center for:

1. applies the **MAX FPS** preset,
2. sets the frame-rate limit to **Unlimited** and turns **VSync off** (the chooser below shows the result),
3. switches the VANTA menu to a solid background without particles,
4. installs the [Performance pack](#the-performance-pack) when a member is neither loaded nor installed. This is the
   only part that downloads anything, and only when you press the button; when every member is already present, or
   the Modrinth integration is not available, nothing is downloaded.

The confirmation notification says *Restart the game to load the Performance pack* when mods were installed. Every
step is a vanilla option or a VANTA setting you can change back by hand.

## Frame-rate limit chooser

Next to the presets, a quick chooser writes the vanilla **Max framerate** and **VSync** options. It is the only place
in VANTA that sets them, apart from [Boost FPS](#boost-fps) and [profiles](profiles.md), which carry the choice made
here:

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
- **What VANTA itself costs.** The headless game test in CI measures 300 frames each with the VANTA HUD on, off and
  with the GUI hidden (software renderer, no GPU) and writes the frame times and the HUD's wall time to
  `vanta-perf-probe.json` in every run; the test fails when the VANTA HUD element averages more than 4 ms per frame,
  and it passes. That is a bound on a CI machine without a graphics card, not a promise for yours, and it says nothing
  about FPS gains on real GPUs, which were not measured.
