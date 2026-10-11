---
title: Vanta Nexus
description: Vanta Nexus (client 1.4.0, more actions from 1.6.0): its seven sections, how the strictly local assistant works, what it can and cannot do (sound, chat size, crosshair and a tidy HUD from client 1.6.0 on), actions with examples, undo, the transcript file, the N key and the palette entry.
order: 29
category: Client
---

**Vanta Nexus** (from client 1.4.0 on) is one screen for the things you change most in VANTA: an assistant you can
ask in plain language, the HUD Designer, profiles, live performance values, waypoints, Vanta Lab and the Nexus
settings. The assistant runs a small language model **on your own PC** through `llama-server` from llama.cpp; it is
the [Local AI](local-ai.md). Nothing you type leaves your computer: the assistant's requests go to `127.0.0.1` and
nowhere else. There is no cloud AI, no API key, no account and no telemetry.

The assistant only ever changes **your own client**: the HUD layout, the crosshair, VANTA settings (from client 1.6.0
on also the sound volumes and the chat size), profiles, performance presets, waypoints and Vanta Lab features. It never acts in the game world, never touches other players, key bindings or the
network, and it cannot run anything that is not one of the actions listed [below](#what-the-assistant-can-do).

## Opening Vanta Nexus

- The **Vanta Nexus** button of the VANTA main menu (between *Multiplayer* and *Options*).
- The key **N** (the *Open Vanta Nexus* binding, rebindable in the [keybind manager](keybinds.md)) in a world while no
  other screen is open.
- `/vanta nexus` in the chat.
- *Open Vanta Nexus* in the command palette (the global search; it also answers to "ai", "assistant", "local ai",
  "hud designer", "waypoints" and "lab").

The left rail lists the seven sections; the content panel shows the selected one. On small windows the rail
collapses the way the other VANTA screens do, and every button stays reachable with the keyboard (Tab, arrows, Enter)
and the mouse. Esc closes the screen.

## The seven sections

### 1. AI Assistant

- A status pill, **Local AI: …**, with the honest state of the Local AI: *Ready*, *Starting*, *Busy*, *Installed*
  (not running yet), *Not installed*, *Incomplete install*, *Not available on this system* or *Failed*, with the
  reason underneath when there is one (for example *Installed by the VANTA Launcher*, or *Local AI is not available
  for Linux/x86* on a system the manifest has no runtime for).
- The transcript: your messages and the assistant's answers as bubbles. Under an answer that changed something stands
  **Applied: …** with every change, and **Not applied: …** with every request the assistant made that VANTA refused
  and why (an unknown widget, a setting it may not change, a waypoint that does not exist in this world, …).
- **Undo** on the last applied turn takes all of its changes back ([Undo](#undo)). **Clear chat** empties the
  transcript.
- A single-line text field; **Enter** (or *Send*) sends. While the transcript is empty, example chips send a
  prompt with one click: *Make my HUD minimal*, *Only show FPS and coordinates*, *Create a recording profile*,
  *Move the FPS counter to the bottom right* and, from client 1.6.0 on, *Sort the HUD* and *Turn the music off*.
- While the Local AI is **not installed**, the section shows the Local AI card instead of the input: runtime and
  model with their versions, the download size, the licences (MIT and Apache-2.0), the two hosts the download
  contacts (`github.com`, `huggingface.co`), the disk and RAM it needs, the sentence *Nothing is downloaded until you
  click Install Local AI. After the download, prompts go to 127.0.0.1 only.*, and the button **Install Local AI**,
  which opens the [Local AI setup](local-ai.md#installing-from-the-game). When the VANTA Launcher manages the Local AI,
  the card says so and has no Install button.
- When *Vanta Nexus assistant* is switched off in the settings, a banner says so with a **Turn on** button.

### 2. HUD Designer

The named HUD layouts and the presets, next to the editor you already know:

- **Presets**: six chips, *Minimal*, *PvP*, *Recording*, *Survival*, *Building* and *Full*. A chip applies the same
  preset the assistant's `hud.preset` action applies, through the same code, and is one undoable change. The widget
  sets are listed in [HUD and crosshair → Nexus presets](hud.md#nexus-presets).
- **Layouts**: the built-in layouts (*Default*, *Minimal*, *PvP*, *Streamer*, *Performance*) and your saved ones, the
  active one marked. Each row has **Load** (the assistant's `hud.layout.load`), **Duplicate** and, for your own
  layouts, **Delete** (asks first). **Save current** stores the live HUD under a name you choose (`hud.layout.save`);
  **New layout** applies a preset and saves the result under a name; a blank, too long or already used name is
  refused with a message.
- **Open editor** opens the [HUD editor](hud.md#the-hud-editor), which remains the place to move, resize, scale and
  set the opacity of every widget.

A layout here is what the HUD editor calls a preset: the same files in `config/vanta/hud/presets/`.

### 3. Profiles

The same profile cards and actions as the [Profiles screen](profiles.md#the-profiles-screen): Activate, Duplicate,
Rename, Delete, Export, Import and *Create from current settings*, plus a link to the full screen. Client 1.4.0 adds
the built-in profiles *Survival* and *Minimal* and gives *PvP*, *Building* and *Recording* the HUD of the matching
Nexus preset ([Profiles → Built-in profiles](profiles.md#built-in-profiles)).

### 4. Performance

Live values only, refreshed every tick while the section is open; a value the game cannot provide reads **n/a**,
nothing is estimated:

| Card | Values | Source |
| --- | --- | --- |
| Frame rate | FPS now; frame time p50 and p99 of the recent frame window; hitches over 50 ms as "n of m frames" | the game's frame counter and VANTA's frame time window |
| Game | ping to the server (*Singleplayer* in a singleplayer world); render distance; GPU name | the server list latency, the vanilla option, the OpenGL renderer string |
| Memory | used (with the percentage), allocated and maximum Java heap | the Java runtime |
| Smart Boost | the current state of [Smart Boost](performance.md#smart-boost): not tuned yet, measuring, or the last result with the preset, measured FPS, target and date | `smart-boost.json` |

Below the cards: the five preset buttons (*Max FPS*, *Low*, *Balanced*, *High*, *Ultra*), **Boost FPS** and **Open
Performance Center**. They do exactly what the [Performance Center](performance.md) does.

### 5. Waypoints

The waypoints of the current world: search, sort (*Name*, *Distance*, *Newest first*), an **All worlds** switch that
groups the waypoints of every world you saved, rows with name, category, distance and an enable switch, edit and
delete (asks first), **Add waypoint** with the coordinates prefilled from your position, and empty states that say
what to do ("No world open", "No waypoints yet", "No waypoint matches"). Everything about waypoints, the screen
markers and the beams is in [Waypoints](waypoints.md).

### 6. Vanta Lab

One row per optional feature with a switch and a one-line description, nothing else: *Dynamic HUD*, *Animated
crosshair*, *Waypoint beams*, *Frame time HUD graph* and *Screen transitions*. All are off by default and each does
exactly what its description says; the same switches are in *Settings → Vanta Lab*
([Settings → Vanta Lab](settings.md#vanta-lab)).

### 7. Settings

The six Nexus settings ([Settings → Vanta Nexus](settings.md#vanta-nexus)), the Local AI status card (what is
installed and where: runtime, model, download size, licences, hosts, requirements, folder, who manages it) with
**Verify files**, **Reinstall** and **Remove Local AI** (asks first) while it is installed, **Install Local AI** while it
is not, and the link **All settings** to the full Settings screen on the Vanta Nexus category. The Install, Reinstall
and Remove buttons are hidden while the VANTA Launcher manages the install, because the client only uses that folder
([Local AI → Two install paths](local-ai.md#two-install-paths)).

## How the assistant works

1. Every question is sent together with a **system prompt built from the live state of your client**: the HUD widget
   ids with their current visibility and position, the widget types that can be added, the crosshair (from client
   1.6.0 on), the settings the assistant may change with their type, allowed values and current value, your profile
   names, the performance presets, your saved HUD layouts, the waypoints of the current world and the Lab features.
   From client 1.6.0 on the on/off settings and the settings from 0 to 1 are listed on one line each to keep the
   prompt short, and the prompt ends with eight short examples (music off, master volume 50 %, crosshair circle, red
   dot crosshair, chat smaller, sort the HUD, remove a widget, only FPS and coordinates). VANTA estimates the prompt
   at one token per three characters and keeps it within 2,600 of the model's 4,096 tokens, so the answer (512
   tokens) and the conversation still fit. Up to the last **8 turns** of the conversation go along as context; a
   question longer than 2,000 characters is cut.
2. The request goes to `llama-server` on `127.0.0.1` (`/v1/chat/completions`), with a **JSON schema** the answer must
   follow. The schema enumerates the real widget ids, preset names, profile names, setting ids and Lab features, so
   the model cannot name a thing that does not exist. Reasoning is off (`enable_thinking: false`, reasoning budget 0);
   the answer is limited to 512 tokens at temperature 0.2, and a request times out after 90 s.
3. The answer is a short message plus a list of **actions**. VANTA validates every action against the same
   registries again (an action that names an unknown element, a forbidden setting or an out-of-range value is
   rejected and listed under *Not applied*), applies the valid ones through the same code paths the screens use, and
   records one undo turn for the whole answer. From client 1.6.0 on, when the answer's `hud.set` or `hud.only`
   actions leave two visible widgets overlapping or on the same spot, VANTA stacks the corners involved apart before
   the turn ends ([A tidy HUD](#a-tidy-hud)) and says so in the receipt.
4. The transcript shows the message, the applied and the rejected actions, and a notification *Vanta Nexus: n
   changes applied* names the number of changes.

The model is **Qwen3-1.7B (Q8_0)**, 1.7 billion parameters, running on the CPU (`-ngl 0`). It is small so that it
fits next to the game, and it is not always right: a request outside its list of actions gets a message and no change,
and a change you do not like is one **Undo** away. How long an answer takes depends on your CPU; the first question
after an idle stop also waits for the server to start (*Initializing Local AI*,
[Local AI → Idle timeout](local-ai.md#idle-timeout-threads-and-settings)).

## What the assistant can do

These are the only actions. Each one goes through the same code a click on the matching VANTA control uses.

| Action | Fields | Effect | Example request |
| --- | --- | --- | --- |
| `hud.set` | `element` (a widget id), optional `visible`, `anchor`, `x`, `y` (0..1 fractions of the screen), `scale` (0.5 to 2.0), `opacity` (0.1 to 1.0) | changes one HUD widget | "Move the FPS counter to the bottom right", "Make the coordinates half transparent" |
| `hud.only` | `elements[]` | shows exactly these widgets and hides every other one (the crosshair stays) | "Only show FPS and coordinates" |
| `hud.arrange` (from 1.6.0) | optional `corner`: `auto` (default), `top_left`, `top_right`, `bottom_left` or `bottom_right` | lays out every visible widget without overlap ([below](#a-tidy-hud)) | "Sort the HUD", "Tidy up my GUIs", "Put everything in the top right" |
| `hud.layout.save` | `name` | saves the live HUD under that name | "Save this HUD as Streaming" |
| `hud.layout.load` | `name` | loads a built-in or saved layout | "Load my Streaming layout" |
| `hud.preset` | `preset`: `minimal`, `pvp`, `recording`, `survival`, `building` or `full` | applies a [Nexus preset](hud.md#nexus-presets) | "Make my HUD minimal", "Set up the HUD for PvP" |
| `crosshair.set` (from 1.6.0) | any of `preset`, `shape`, `size`, `thickness`, `gap`, `color`, `outline`, `opacity`, `vanilla` | changes the crosshair like the [crosshair customizer](hud.md) ([below](#the-crosshair)) | "Make the crosshair a circle", "Red dot crosshair", "Use the normal Minecraft crosshair" |
| `profile.switch` | `name` | activates a profile | "Switch to the Building profile" |
| `profile.create` | `name`, `fromCurrent` | creates a profile from the current settings | "Create a recording profile" |
| `perf.preset` | `preset`: `boost`, `low`, `balanced`, `high` or `ultra` | applies a [performance preset](performance.md#presets) (vanilla video options only) | "Apply the Low preset" |
| `perf.smartBoost` | `run` | runs [Smart Boost](performance.md#smart-boost) again (Re-tune) | "Tune my performance" |
| `setting.set` | `id`, `value` | changes one VANTA setting of the categories Video, Audio (from 1.6.0), HUD, Performance or Accessibility | "Turn off view bobbing", "Set the HUD scale to 1.2", "Music off", "Make the chat smaller" |
| `waypoint.add` | `name`, optional `x`, `y`, `z` (your position when left out), optional `category` | saves a waypoint in the current world | "Add a waypoint called Home here" |
| `waypoint.remove` | `name` | deletes a waypoint of the current world | "Remove the waypoint Farm" |
| `waypoint.toggle` | `name`, `enabled` | shows or hides its marker | "Hide the Portal waypoint" |
| `lab.set` | `feature`, `enabled` | switches a [Vanta Lab](#6-vanta-lab) feature | "Turn on the dynamic HUD" |

### Sound and chat size (from client 1.6.0 on)

`setting.set` may change the **Audio** settings: `audio.master`, `audio.music`, `audio.records`, `audio.weather`,
`audio.blocks`, `audio.hostile`, `audio.neutral`, `audio.players`, `audio.ambient`, `audio.voice` (each a number from
0 to 1, where 0 is off and 1 is 100 %) and `audio.subtitles` (true or false). They are Minecraft's own sound options,
written the way the *Music & Sounds* screen writes them. *Music off* is `audio.music` 0; *master volume 50 %* is
`audio.master` 0.5. Because small models often answer in percent, VANTA reads `"50 %"` and any number above 1 up to
100 as percent (50 is 0.5) for every number setting that ends at 1 (volumes, opacities, the chat size), and for a
volume `false`, `"off"` and `"mute"` mean 0 and `true` and `"on"` mean 100 %.
The receipt shows the value and the percentage, for example *Setting audio.music = 0 (0 %)*.

A number setting can also be changed by a **step** from its current value: a text value that starts with `+` or `-`,
such as `"-0.2"` or `"+0.2"`, is added to the value it has now and then kept within the setting's range like any
other value (`"+10"` on `video.fov` turns 70 into 80). For the settings that end at 1, `"-20 %"` and `"-20"` are a
step of 0.2, the same percent reading as above. A step in percent on another setting (`"+10 %"` on `video.fov`) is
refused. A plain JSON number such as `-0.2` is never a step, it is that value. One Undo puts the old value back.

The chat size has five new [Accessibility settings](accessibility.md#vanilla-options-in-the-same-place) bound to
Minecraft's chat options: `accessibility.chatScale` (text size), `accessibility.chatWidth`,
`accessibility.chatHeightFocused`, `accessibility.chatHeightUnfocused` and `accessibility.chatLineSpacing`, each from
0 to 1 like `accessibility.chatOpacity`. The system prompt tells the model that *make the chat smaller* is the step
`"-0.2"` on the text size and on the width, and *bigger* the step `"+0.2"` (at most 1). Because a step does not depend
on the current size, asking a second time makes the chat smaller again. Whatever the model answers, VANTA never sets
the text size below 0.5 or the width below 0.25 (110 pixels), so the chat stays readable: a smaller value is raised
to that minimum and the receipt says so (*0.5 (50 %, the smallest the assistant sets)*). A size you set below that
minimum yourself in Minecraft's *Chat Settings* is left as it is when you ask for a smaller chat (*not lowered:
already below 0.5*); a bigger chat still works.

### The crosshair

From client 1.6.0 on the action `crosshair.set` changes the crosshair through the same store the crosshair customizer
uses (and sets the customizer's *crosshair preset* setting to the matching preset or *custom*, as the customizer does);
the change is saved with VANTA's other files. All fields are optional and are applied in this order:

| Field | Values |
| --- | --- |
| `preset` | a built-in preset: `default`, `dot`, `thin`, `bold`, `circle`, `precision` (applied first, the other fields change it) |
| `shape` | `cross`, `dot`, `circle`, `square`, `chevron`, `plus_dot` |
| `size`, `thickness`, `gap` | whole numbers, kept within the customizer's ranges: size 1 to 32, thickness 1 to 8, gap 0 to 16 |
| `color` | `#RRGGBB` or one of `white`, `red`, `green` (a bright green), `blue`, `yellow`, `cyan`, `magenta`, `black`, `orange`, `purple`, `pink`, `gray` (also `grey`) |
| `outline` | `true` or `false` |
| `opacity` | 0.1 to 1 (a number above 1 up to 100 counts as percent; the assistant never makes the crosshair invisible) |
| `vanilla` | `true` switches to Minecraft's own crosshair, `false` back to VANTA's |

A shape, colour or other style change while Minecraft's crosshair is active also turns VANTA's crosshair back on, so
you see the change. Unknown shapes, presets and colours are refused with the allowed values. Examples: *make the
crosshair a circle* is `{"type":"crosshair.set","shape":"circle"}`, *red dot crosshair* is `"shape":"dot",
"color":"red"`.

### A tidy HUD

From client 1.6.0 on, "sort", "arrange", "tidy", "organize", "order" or "line up" the HUD (also "sortieren" and
"ordnen") is one action, `hud.arrange`. It lays out every **visible** widget so that none overlaps another:

- `auto` (the default) keeps each widget in the corner or at the edge it is nearest to (a widget in the middle of the
  screen goes to the corner of its quarter) and stacks the widgets of each corner in a column, from the corner
  inwards, with 2 pixels between them; a full column continues in a next column beside it. Edge groups are centred
  along their edge.
- `top_left`, `top_right`, `bottom_left` or `bottom_right` puts all of them into that corner, in the order they had
  from top to bottom.
- Sizes, scale, colours and settings are kept; only the anchor and the position change. Hidden widgets do not move,
  the crosshair does not move, and no widget is placed over the crosshair.
- The layout is computed for the smallest screen VANTA supports (427 x 240 GUI pixels). Every widget then hangs on
  its corner or edge, so on a bigger window the groups only move further apart. When the widgets do not all fit
  there, the layout is computed for 854 x 480 instead. A widget that finds no free spot at all is left where it was
  and named in the receipt.

The receipt reads, for example, *HUD arranged (auto): 9 widgets without overlap, 3 moved*. The same packing repairs
an answer that moved widgets on top of each other with `hud.set` (for example an anchor for every widget and no
position, which put them all on one spot before 1.6.0), or that showed a hidden widget again with `hud.set` or
`hud.only` on a spot another widget took meanwhile: every corner or edge with such an overlap is stacked again
(a widget the answer moved keeps the requested spot, the other widgets of that corner move along its column), the
other corners stay exactly where they are, a widget moved into the middle of the screen only steps to the nearest
free spot, and the receipt says *Overlapping HUD widgets moved apart: …*. One **Undo** takes the whole answer back,
the repair included.

What it **cannot** do, by design:

- change key bindings, the Controls, General, Language, Cosmetics, Privacy, Waypoints, Vanta Nexus or Vanta Lab
  **settings** (Lab features only through `lab.set`; the crosshair only through `crosshair.set`), the Audio settings
  before client 1.6.0, settings of the kind *action* or *key*, or settings that need a restart;
- add or change anything in the game world, move you, use items, send chat messages or talk to a server;
- see or change anything about other players;
- install, download or run anything. The Local AI itself is installed only by your click on **Install Local AI**;
- add the *Frame time graph* widget while the Lab feature is off (it is not offered to the model then).

A rejected action is shown with its reason: *Unknown action*, *Missing value*, *Not allowed*, *No HUD widget named …*,
*No setting named …*, *The assistant may not change …*, *No preset named …*, *No profile named …*, *No HUD layout
named …*, *No waypoint named … in this world*, *No lab feature named …*, *Not available right now* (for example a
waypoint action outside a world) or *Could not apply*.

## Undo

Every answer that changed something is one **undo turn**: a snapshot of the active profile, every setting it changed,
the HUD layout before the change, and a restore step for each other action (a waypoint it added is removed again, a
toggled waypoint or Lab feature goes back, a created profile is deleted, and from client 1.6.0 on a changed crosshair
gets its previous style back; sound and chat settings are settings like any other). **Undo** in the assistant section and in
the receipt takes the last turn back; the last 20 turns are kept while the game runs. The notification *Undone: the
last assistant change was taken back* confirms it. The HUD Designer's preset chips and the *Load* buttons create undo
turns of the same kind.

## The transcript file

The conversation is kept in `config/vanta/nexus-chat.json` in the game folder (with the VANTA Launcher:
`<data directory>/instances/vanta-1.21.11/config/vanta/nexus-chat.json`): the last 100 entries (50 of your messages and
50 answers) with their time, the applied and rejected actions and whether an answer was an error, each text cut at
4,000 characters. It is written when the game saves VANTA's files and read when the game starts, so the conversation
is still there after a restart. **Clear chat** empties it. The file never leaves your computer and nothing but this
screen reads it; delete it to start fresh.

## When the Local AI is not installed

Opening Nexus while the Local AI is not installed shows the install card ([above](#1-ai-assistant)); every other
section works without the Local AI. The first time the VANTA main menu is shown in a game session (and no other popup
is open), client 1.4.0 also shows one dialog, *Vanta Nexus: Local AI is not installed. Open Nexus to install it.
Nothing is downloaded until you click Install Local AI there*, with **Open Nexus** and **Not now**. Either choice is
remembered per client version in `config/vanta/nexus-first-start.json`, so you see the dialog once per version. The
dialog downloads nothing. With *Settings → Vanta Nexus → Vanta Nexus assistant* off, it does not appear.

## Honest limits

- The model runs on the CPU with 4,096 tokens of context. How fast it answers depends on your CPU and on what else
  runs; a request that takes more than 90 s fails with *Could not answer* (nothing is changed then). The manifest
  asks for 3072 MB of free RAM while the server runs, next to the game
  ([Minecraft requirements → Local AI](minecraft-requirements.md#local-ai)). VANTA promises no answer time.
- A small model misreads requests at times. VANTA shows exactly what it applied and lets you undo it; it never applies
  anything outside the action list, and every value is clamped or rejected before it is applied.
- The assistant does not answer general questions well and has no knowledge of the Minecraft world, your inventory or
  other players; it reads only the state listed under [How the assistant works](#how-the-assistant-works).
- The CI game test runs the whole path end to end on a software renderer ("Only show FPS and coordinates" through
  the real `llama-server` and model, with the HUD checked afterwards) when the Local AI is prepared for the run; that
  checks the mechanism, not the quality of every answer.
