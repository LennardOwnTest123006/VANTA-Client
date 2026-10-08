---
title: Performance Center
description: What the Performance Center measures, the five presets with their exact vanilla values, Boost FPS, Smart Boost, render distance suggestions, the Performance pack and honest limits.
order: 23
category: Client
---

The Performance Center is a dashboard for the numbers that matter and a quick way to apply a sensible set of
**vanilla** video options. It does not modify Minecraft's renderer itself and it makes no FPS promises: how fast your
game runs depends on your hardware and on Minecraft. For more than vanilla options can give, it points to the
[Performance pack](#the-performance-pack): optimisation mods from Modrinth that VANTA installs on request; from client
1.2.0 on the mods bundle for the manual installation also carries the redistributable ones
([Mods & Shaders](mods-and-shaders.md#the-pack-in-the-mods-bundle)).

Open it from *Settings → Performance → Open Performance Center*, the performance key (unbound by default), the
main menu, `/vanta perf` or, from client 1.4.0 on, *Open Performance Center* in the
[Performance section of Vanta Nexus](#the-performance-section-of-vanta-nexus).

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

Minecraft 1.21.11 bundles several of these options in its own graphics preset (Fast / Fancy / Fabulous). As soon as
one of them differs from the bundle, the game reports **Custom**, which is the state every VANTA preset leaves. From
client 1.3.0 on, *Settings → Video → Graphics* shows it as *Custom* instead of *Fancy*
([Troubleshooting](troubleshooting.md#graphics-shows-custom-or-a-change-in-sodiums-video-settings-is-lost)).

To let VANTA pick the preset for you, use [Smart Boost](#smart-boost): it measures real gameplay on your PC and applies
the options of the preset that runs smoothly, all but *Graphics*. A preset or Boost FPS you apply yourself counts as
your choice, and Smart Boost's automatic runs leave those options alone afterwards.

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

## Smart Boost

**Smart Boost** (from client 1.3.0 on) picks one of the five presets for your PC by measuring real gameplay. It is a
local, rule-based tuner: there is no model and no network, and nothing leaves your computer.

### When it runs

- **Automatically, once per installed client version**: after the first installation and after every update, while
  *Settings → Performance → Smart Boost: tune automatically after install/update* is on (the default). It starts
  after 20 s of gameplay in a world, never at start-up and never in a menu. The notification *Smart Boost is testing
  settings* says so, and the video options change up to three times, each step 33 s of gameplay (see below).
- **Re-tune** on the Smart Boost card (or *Smart Boost: Re-tune* in the command palette) measures again from the next
  gameplay frames. It also takes back the options you changed by hand, which is why the button says *overrides your
  manual video choices*.
- The JVM option `-Dvanta.smartBoost.auto=false` turns the automatic run off for one game. Re-tune still works.

Only **gameplay** counts: a world is loaded, no screen is open (pause menu, inventory, chat, VANTA screens), the game
is not paused, the window has the focus, and you moved or looked around in the last 30 s. The pause menu, an
unfocused window and vanilla's AFK frame limit therefore never look like a slow PC. Leaving the world during a
measurement stops it; the run starts over next time, and the values already written stay.

### How it measures and decides

1. **Starting guess** from the GPU name (the OpenGL renderer string), the CPU threads and the maximum Java heap:

   | Hardware | First preset | Highest preset it may try |
   | --- | --- | --- |
   | software renderer (llvmpipe, softpipe, SwiftShader, Microsoft Basic Render, GDI Generic) | MAX FPS | MAX FPS |
   | integrated GPU | LOW with 4 threads or fewer, else BALANCED | HIGH |
   | dedicated GPU, 8 threads or more and a heap of 3.5 GiB or more | HIGH | ULTRA |
   | dedicated GPU otherwise, or a GPU it does not recognise | BALANCED | HIGH |

   A heap under 2.5 GiB limits the highest preset to BALANCED, under 3.5 GiB to HIGH. The guess is only where the
   measurement starts.
2. **Measuring a step.** Smart Boost applies the preset's options, discards 8 s of frames (chunks are rebuilt) and
   then measures 25 s of gameplay with at least 600 frames. It records the median frame rate, the 1 % low (the frame
   rate of the 99th percentile frame time) and the hitches (frames longer than 50 ms).
3. **Target.** 60 FPS when the frame-rate limit is *Unlimited* or VSync is on; otherwise your limit, clamped to
   60–144 FPS (a limit below 60 is the target itself).
4. **Pass.** The median reaches 1.1 × the target (0.95 × while a limit or VSync caps the frame rate), the 1 % low
   reaches 0.6 × the target, and there is at most one hitch per 10 s.
5. **Next step.** A pass with headroom (no cap, median at least 1.6 × the target, 1 % low at least the target) tries
   one preset higher, up to the highest preset of the guess; that preset is kept when it passes, otherwise the
   earlier one comes back. A fail steps one preset down until a preset passes or MAX FPS is reached. A run measures
   at most three steps.

The notification *Smart Boost applied* names the preset, the median FPS it measured and the target. With a frame-rate
limit or VSync on, the card notes that the cap hides how much faster the PC could run, so Smart Boost can only lower
settings then.

### What it changes, and what never

- It writes only these vanilla options, with the values from the [preset table](#presets): render distance,
  simulation distance, particles, clouds, smooth lighting, entity shadows, entity distance, biome blend, mipmap levels
  and, for MAX FPS, the menu background blur. Like any change of these options, they are saved to `options.txt`.
- It never changes **Graphics** (Fast / Fancy / Fabulous), **Max Framerate** or **VSync**. Because it changes single
  options of the game's graphics bundle, the *Graphics* row can show *Custom* afterwards.
- **It never overrides your own choices.** Every value it writes is recorded. When an option no longer has that
  value, you changed it (Video Settings, Sodium's screen, a profile, a VANTA preset or Boost FPS), and every later
  automatic run leaves it alone; the card lists such options under *Left alone (you changed them)*. Picking another
  Fast / Fancy / Fabulous hands every option back to you. If you change a video option while it measures, it stops
  (*Smart Boost stopped*) and keeps what it wrote so far.
- **A graphics choice made before its first run** counts too. When the graphics preset is anything but the untouched
  default Fancy at that moment (Fast, Fabulous, or Custom after your own changes, a VANTA preset or Boost FPS), the
  automatic run changes nothing, and the card says *You chose your own graphics settings*. Re-tune lets it take over.

### The Smart Boost card

The card sits in the Performance Center between the *Want much more FPS?* banner and the Presets card, and shows:

- the status: *Not tuned yet*, *Starts when you play*, *Measuring <preset>… <seconds> / 25 s*, *Paused - resume
  playing to continue measuring*, or the last result, *Smart Boost picked <preset>: about <n> FPS measured (target
  <n> FPS) · <date>*;
- the options left alone because you changed them;
- the threads, the Java heap and the GPU name the guess is based on (*GPU not detected* when the game could not read
  it);
- the two switches *Smart Boost: tune automatically after install/update* (on) and *Smart Boost: adjust render
  distance while playing* (off), also in *Settings → Performance*;
- **Re-tune** and **Undo Smart Boost**. *Undo* puts every option Smart Boost still controls back to its value from
  before Smart Boost's first change and turns *tune automatically after install/update* off. Both are also in the
  command palette (*Smart Boost: Re-tune*, *Undo Smart Boost*).

### Adjusting the render distance while playing

*Smart Boost: adjust render distance while playing* (off by default) keeps measuring while you play, in windows of
20 s, and changes only the render distance, and only while it is still the distance Smart Boost set:

| Condition | Change |
| --- | --- |
| two windows in a row with a median below 0.8 × the target or a 1 % low below 0.5 × the target | 2 chunks lower, never below 6 |
| no cap, median above 1.6 × the target and 1 % low at least the target | 2 chunks higher, never above the distance the run picked (at most 32) |

At most one change every 3 minutes, and no step up within 5 minutes after a step down. Each change shows the
notification *Render distance adjusted*. Meanwhile the [render distance suggestions](#render-distance-suggestions) stay
quiet, so only one of them changes the distance.

### Where it keeps its record

`config/vanta/smart-boost.json` in the game folder holds the client version it last ran for, the result, each option it
wrote with the value it wrote and the value before, the options you took over, and the highest render distance for
the switch above. The game log (`latest.log`) lists every measured step and the GPU name.

## The Performance section of Vanta Nexus

From client 1.4.0 on, [Vanta Nexus → Performance](nexus.md#4-performance) shows a compact set of the same numbers,
**live values only**, refreshed every tick while the section is open. Nothing there is estimated: a value the game
cannot provide reads **n/a**.

| Card | Values | Source |
| --- | --- | --- |
| Frame rate | *FPS now*; *Frame time p50* and *Frame time p99* of the recent frame window; *Hitches over 50 ms* as "n of m frames" | the game's frame counter and the same frame time window the Performance Center uses |
| Game | *Ping* to the server (*Singleplayer* in a singleplayer world, n/a outside a world); *Render distance*; *GPU* (the OpenGL renderer string) | the server list latency, the vanilla option, the graphics driver |
| Memory | used (with the percentage of the maximum), allocated, maximum Java heap | the Java runtime |
| Smart Boost | *Not tuned yet*, the measurement in progress, or the last result with preset, measured FPS, target and date | the [Smart Boost](#smart-boost) record |

Below the cards: the five preset buttons, **Boost FPS** and **Open Performance Center**; they run exactly the code the
buttons here run. The [assistant](nexus.md#what-the-assistant-can-do) can apply a preset (`perf.preset`: `boost`,
`low`, `balanced`, `high`, `ultra`; "Apply the Low preset") and start a Smart Boost re-tune (`perf.smartBoost`; "Tune
my performance"), both undoable from the chat; it cannot change the frame-rate limit chooser or install the
Performance pack. Smart Boost itself stays a rule-based tuner without a model: the Local AI is not involved in it.

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

The advisor watches your average FPS against a target (the configured frame-rate limit, or 60 when unlimited). From
client 1.3.0 on it counts only gameplay, by the same rule as [Smart Boost](#smart-boost), and stays quiet while Smart
Boost measures or adjusts the render distance:

| Condition | Suggestion |
| --- | --- |
| average FPS over the last 10 s stays below 75 % of the target | lower the render distance by 2 chunks (never below 6; 4 before client 1.3.0) |
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
- [Smart Boost](#smart-boost) picks among the same five presets. It measures on your PC and reports what it measured;
  it cannot make Minecraft faster than the fastest preset, and a run tries at most three presets. In CI it runs only
  with shortened windows on a software renderer, which checks its rules, not its choice for a real graphics card.
- There is no claim such as "+200 % FPS", neither for the presets nor for the Performance pack. Measure on your own
  machine with the cards above.
- The Performance pack mods are third-party projects, not part of VANTA; VANTA installs them on request, and from
  client 1.2.0 on the mods bundle carries the redistributable ones unmodified
  ([Mods & Shaders](mods-and-shaders.md#the-pack-in-the-mods-bundle)). CI runs VANTA's game test with
  them loaded; other mod combinations are not tested
  ([Fabric → Using VANTA with other mods](fabric.md#using-vanta-with-other-fabric-mods)).
- The CPU card shows "n/a" when the Java runtime does not provide process CPU load.
- **What VANTA itself costs.** The headless game test in CI measures 300 frames each with the VANTA HUD on, off and
  with the GUI hidden (software renderer, no GPU) and writes the frame times and the HUD's wall time to
  `vanta-perf-probe.json` in every run; the test fails when the VANTA HUD element averages more than 4 ms per frame,
  and it passes. That is a bound on a CI machine without a graphics card, not a promise for yours, and it says nothing
  about FPS gains on real GPUs, which were not measured.
- **Short freezes.** From client 1.3.0 on the same game test also measures at least 600 frames with the VANTA HUD on
  and again off while the player turns, and adds the longest frames (maximum, p99, p99.9, frames over 50 ms and over
  100 ms), the garbage collections and the JVM's memory and GC arguments to `vanta-perf-probe.json`. It fails when a
  single VANTA HUD or crosshair call takes more than 8 ms. The frame-time numbers are recorded, not judged: the CI
  machine renders in software and does not stand for your PC.
