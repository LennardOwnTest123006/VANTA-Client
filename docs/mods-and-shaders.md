---
title: Mods & Shaders
description: Install mods, shader packs and resource packs from Modrinth in game or on the launcher's Mods page, the Performance pack, Iris shaders, disabling, removing, restarting and what is sent to Modrinth.
order: 24
category: Client
---

From VANTA Client 1.1.0 and VANTA Launcher 1.1.0 on, VANTA can download Fabric mods, shader packs and resource packs
for Minecraft 1.21.11 from [Modrinth](https://modrinth.com), the public mod platform. There are two places to do it:
the **Mods & Shaders** screen in the game and the **Mods** page of the VANTA Launcher. Both write into the same game
folder and keep the same list of what they installed, so you can use either one.

The projects on Modrinth are made by their own authors and published under their own licences. VANTA does not ship
or change them: it downloads the file Modrinth publishes, checks it and puts it into the right folder.

## Mods & Shaders in the game

Open it with the **Mods & Shaders** button of the VANTA main menu, from *Settings → Video* or *Settings → Performance*
(*Open Mods & Shaders*), with *Open Mods & Shaders* in the Performance Center, or from the global search (the
magnifier in the main menu) with words such as "mods", "shaders" or "modrinth".

The screen has four tabs:

| Tab | What it lists | Installed into |
| --- | --- | --- |
| **Mods** | Fabric mods with a version for Minecraft 1.21.11 | `mods/` |
| **Shaders** | shader packs that Modrinth lists for Iris, with a version for 1.21.11 | `shaderpacks/` |
| **Resource packs** | resource packs with a version for 1.21.11 | `resourcepacks/` |
| **Installed** | everything in these three folders, also files you added yourself | — |

With an empty search field each tab shows the most downloaded projects; type a name and press Enter (or wait a
moment) to search Modrinth. Every row shows the title, the author, the downloads, the description and whether the
project is installed. Select a row to see it in the panel on the right, with **Install**, **Remove**, **Disable**,
**Enable** and **View on Modrinth** (opens the project page in your browser).

**Install** picks the newest version of the project for Minecraft 1.21.11 (for mods: a Fabric version) and installs
it together with the projects it requires:

- Required dependencies are installed automatically, in the exact version a project asks for when it names one
  (Iris, for example, names the Sodium version it needs).
- Fabric API is not downloaded again: VANTA already needs it, so it is always there.
- A project that Modrinth marks as incompatible with something you have is not installed; VANTA says why.
- Stable releases are preferred. A beta is only taken when a project has no release for 1.21.11.

Downloads run in the background, so you can keep playing; progress, results and errors appear as VANTA
notifications. If something fails, the message names the project and the reason (for example "has no version for
Minecraft 1.21.11 yet and was skipped").

### How every download is checked

- Each file comes from the address Modrinth publishes for it, over HTTPS (Modrinth serves files from
  `cdn.modrinth.com`).
- It is checked against the SHA-512 checksum Modrinth publishes. A file that does not match is deleted.
- A mod is only put into `mods/` when it and all of its dependencies were downloaded and checked. An existing file
  with the same name is never overwritten.

## The Mods page in the VANTA Launcher

The launcher's **Mods** page (sidebar) does the same for the VANTA game folder
(`<data directory>/instances/vanta-1.21.11`): tabs **Mods**, **Shaders** and **Resource packs**, a search field
(*Search Modrinth…*), the most downloaded projects while the search is empty, and **Install** on every result. It
installs the newest version for Minecraft 1.21.11 with its required dependencies and checks every file with its
SHA-512, like the game.

The installed list of the current tab (*Installed mods*, *Installed shaders*, *Installed resource packs*) shows
everything in that folder, including files you added yourself (*Added by hand (not from this launcher)*):

- the switch next to an entry turns it off or on. Turning it off renames the file to `<file name>.disabled`;
  nothing is deleted;
- the trash button (*Remove …*) deletes the file after a confirmation;
- **Update all** brings every project installed from Modrinth to its newest version for 1.21.11;
- Fabric API and the VANTA Client are marked **VANTA**. The launcher installs and updates them itself, so they cannot
  be switched off or removed here, and Fabric API shows **Included** in the search results;
- a project that an enabled project needs cannot be switched off or removed on its own (*… is needed by …. Disable or
  remove that first.*).

Changes load the next time the game starts, also in the profile *VANTA 1.21.11* of the official Minecraft Launcher,
which uses the same folder. **Open folder** opens the folder of the current tab.

## The Performance pack

The Performance pack is six well-known Fabric mods, installed in one step:

| Mod | What it does | Modrinth page |
| --- | --- | --- |
| Sodium | replaces the rendering engine; the largest frame rate gain of the six | [modrinth.com/mod/sodium](https://modrinth.com/mod/sodium) |
| Lithium | faster game logic (ticking, physics, mob AI) | [modrinth.com/mod/lithium](https://modrinth.com/mod/lithium) |
| FerriteCore | uses less memory | [modrinth.com/mod/ferrite-core](https://modrinth.com/mod/ferrite-core) |
| ImmediatelyFast | faster rendering of the HUD, text and entities | [modrinth.com/mod/immediatelyfast](https://modrinth.com/mod/immediatelyfast) |
| EntityCulling | skips entities and block entities you cannot see | [modrinth.com/mod/entityculling](https://modrinth.com/mod/entityculling) |
| Iris Shaders | loads shader packs and works together with Sodium | [modrinth.com/mod/iris](https://modrinth.com/mod/iris) |

No version is fixed in VANTA. At install time VANTA asks Modrinth for the newest Fabric version of each mod for
Minecraft 1.21.11. A mod that has no such version yet is skipped with a message, and the others are installed. How
much faster the game runs depends on your computer; VANTA does not promise a number.

**In the game**, the *Performance pack* card on the Mods & Shaders screen lists the six mods with a tick box each.
Untick what you do not want and click **Install performance pack** (or *Install … selected*). Mods you already have
show *Installed · restart* until the next start and *Active* once they are loaded; when all are there the card says
*Everything installed*.

**In the VANTA Launcher** the pack is on by default: PLAY, *PLAY via Minecraft Launcher*, *Use with Minecraft
Launcher* and `--install` install it together with Fabric API and the VANTA Client, and each later install replaces a
mod with its newer version when there is one. The confirmation of *Use with Minecraft Launcher* lists the pack files.
When Modrinth cannot be reached, or a mod has no version for 1.21.11 yet, the install continues without it and says
so. A mod you added to `mods/` yourself that contains the same Fabric mod is left alone, and the pack's copy is not
installed, so Fabric never sees two copies.

- Turn it off in *Settings → Game → Install the performance pack* (`installPerformancePack` in `settings.json`), or
  skip it once on the command line with `--without-performance-pack`. Turning it off stops installing and updating
  the pack; mods that are already in the game folder stay until you switch them off or remove them on the Mods page.
- While the setting is on, a pack mod you **remove** is installed again by the launcher's next installation (PLAY,
  *PLAY via Minecraft Launcher*, *Use with Minecraft Launcher*). To keep one of them out, **disable** it instead (it
  stays disabled across installations), or turn the setting off.

CI runs VANTA's headless game test with the newest 1.21.11 Fabric versions of these six mods loaded, and fails when
one of them is not loaded, the game crashes or a step of the test fails. Other mod combinations are not tested.

### The pack in the mods bundle

From client 1.2.0 on, the manual-installation download `vanta-client-<version>-mods.zip`
([Installation → path C](installation.md#c-manual-installation)) also contains the Performance pack: next to the
VANTA jar and Fabric API, its `mods/` folder holds the newest 1.21.11 Fabric build of every pack mod whose licence
allows redistribution, exactly as published on Modrinth (same file name, same bytes, checked by size and SHA-512 when
the release is built and listed in the bundle's `SHA256SUMS`). The bundle of 1.1.0 holds only the two jars.

- **EntityCulling is the exception.** Its licence does not allow redistribution, so it is not in the zip. The game
  offers it instead: the *Performance pack* card shows it as *Not installed* with its tick box, and **Install**
  downloads it from Modrinth. Nothing is downloaded without that click.
- **Iris and Sodium belong together.** The Iris build in the bundle requires exactly the Sodium build in the bundle
  (Iris names the Sodium version it needs). Always copy or update the two as a pair.
- **Do not mix with copies you already have.** Fabric refuses to start when two jars carry the same mod id
  (*Duplicate mod*). If your `mods/` folder already holds a Sodium, Iris, Lithium, FerriteCore or ImmediatelyFast
  jar, delete the older one before copying the bundle's. A leftover `.jar.disabled` is fine.
- **Licences.** The pack mods are third-party projects by their own authors under their own licences, shipped
  unmodified. The zip contains `THIRD-PARTY-LICENSES.txt` (the full licence text of every third-party jar, with the
  file it applies to, its source repository and authors), `PERFORMANCE-PACK.txt` (which versions the bundle holds and
  what is not in it and why) and `performance-pack.json` (the same list, machine-readable). Sodium is licensed under
  PolyForm Shield 1.0.0; its terms are reproduced in the notices. Fabric API is Apache-2.0 (its notice is in there
  too). VANTA Client itself stays MIT.
- **Which versions?** None is fixed in the repository. The release workflow resolves the newest 1.21.11 Fabric build of
  each mod on Modrinth when it builds the release, verifies every download, fetches every licence text (the release
  fails rather than ship without them) and prints the resolved list into the GitHub Release notes
  (*Performance pack in the mods bundle*). A bundled mod without a 1.21.11 build, or an Iris that needs a Sodium other
  than the newest one, stops the release instead of producing an incomplete zip; a person then decides.

### The offer at start

A game that was not started by the VANTA Launcher (path C, Prism, MultiMC, …) asks once per start when it reaches
the VANTA main menu and at least one pack member is missing: **Boost your FPS?** names the missing mods and that they
come from Modrinth. **Install** first records the pack jars that came with the bundle in `config/vanta/modrinth.json`
(so the Installed tab manages them: *Remove* and *Disable* work, and they no longer show as added by hand), then
installs the missing members with the usual progress and result toasts and asks for a restart. **Not now** turns
the offer off for good (*Settings → Performance → Offer the Performance pack at start*, `performance.offerPack` in
`settings.json`, switches it back on). Games started by the VANTA Launcher never see it: the launcher installs the
pack itself. Nothing is downloaded without your click.

## Shaders with Iris

Shader packs need **Iris**, which is part of the Performance pack. Without Iris the Shaders tab says *Shader packs
need Iris* and offers **Install Iris** (with Sodium, which Iris requires). Iris is a mod, so it loads after a restart.

Once Iris is loaded, install a shader pack from the Shaders tab. VANTA then asks whether to open the shader settings;
**Open shader settings** opens the Iris shader pack screen, where you choose the pack. If VANTA cannot open it, press
**O** (the Iris shader key) or go to *Options → Video Settings → Shader Packs*. A new shader pack needs no restart.
The same screen is where you switch to another pack or turn shaders off again.

**Picking a shader pack.** Shader packs change lighting, shadows, water and sky, and they cost much more graphics
power than vanilla rendering. Some tips:

- Read the description and open **View on Modrinth**: many packs say which hardware they are made for, offer quality
  profiles, or list known problems.
- Start with a pack that describes itself as lightweight or performance-friendly, especially on a laptop or an
  integrated GPU, and watch the FPS in the Performance Center or the FPS widget of the HUD.
- You can install several packs and switch between them in the Iris screen; only one is active at a time.
- If the game becomes slow or the picture looks broken, turn shaders off in the Iris screen first, then remove the
  pack.

## Resource packs

Resource packs from the Resource packs tab go to `resourcepacks/`. They are not turned on automatically: after the
install VANTA offers **Open resource packs**, which opens the VANTA resource pack screen, where you enable them and
choose their order. No restart is needed.

## Disabling and removing

**In the game** (Installed tab, or a search result that is installed):

- **Disable** / **Enable** is available for mods VANTA installed. The file is renamed between `.jar` and
  `.jar.disabled`; Fabric does not load `.disabled` files. Shader packs are turned on and off in the Iris screen,
  resource packs in the resource pack screen.
- **Remove** deletes the file VANTA installed after a confirmation. If another installed project needs it, the
  confirmation says so: that project may stop working until you install it again.
- **Remove from the list** appears when a file VANTA installed is no longer there.
- Files you added yourself are listed by their file name with *Added by hand*. VANTA never changes or deletes them.

**In the launcher** you can also switch off or remove files you added yourself; see
[the Mods page](#the-mods-page-in-the-vanta-launcher).

## Restarting the game

Fabric loads mods only when the game starts. After you install, disable, enable or remove a mod, the Mods & Shaders
screen shows a **Restart required** banner:

- **Restart game** appears when the VANTA Launcher started the game with PLAY. VANTA writes
  `config/vanta/restart.request` and quits, and the launcher starts the game again (at most 5 times per PLAY; the
  same works with `--launch`).
- **Quit game** appears otherwise, for example when the game was started from the official Minecraft Launcher. Start
  it again from there.
- In a world the button is disabled: leave the world first.

Shader packs and resource packs need no restart.

## Where files go

Everything goes into the game folder of the running game: with the VANTA Launcher (PLAY or the Minecraft Launcher
profile *VANTA 1.21.11*) that is `<data directory>/instances/vanta-1.21.11`
([Installation → Where files live](installation.md#5-where-files-live)); with a manual installation it is your
`.minecraft` folder.

| Path in the game folder | Content |
| --- | --- |
| `mods/` | mods (`.jar`; disabled ones end in `.disabled`) |
| `shaderpacks/` | shader packs for Iris |
| `resourcepacks/` | resource packs |
| `config/vanta/modrinth.json` | the list of everything installed from Modrinth: project, version, file, SHA-512, enabled, which project needs it |
| `config/vanta/restart.request` | written only for a moment when the game asks the VANTA Launcher for a restart |

The game and the launcher read and write the same `modrinth.json`, so what one installs the other shows. Keys one of
them does not know are kept.

## What is sent to Modrinth

The game contacts Modrinth (`api.modrinth.com`, files from `cdn.modrinth.com`) only while you use Mods & Shaders:
when the screen opens and lists projects, when you search and when you install. The launcher contacts it when the
Performance pack is installed, when you open or use the Mods page and for *Update all*. What is sent:

- your search text, the tab (mods, shaders or resource packs) and the Minecraft version 1.21.11;
- the ids of the projects and versions VANTA looks up or downloads;
- when you install in the game: the SHA-512 checksums of the mod files in `mods/` that VANTA did not install, so it
  can recognise mods you added yourself and does not install them twice;
- a User-Agent that names the VANTA project and its version, as Modrinth asks API clients to do.

No Microsoft or Minecraft account data, no player name, no statistics and no settings are sent. As with any website,
Modrinth sees your IP address. Details: [Privacy](privacy.md#modrinth-mods-shaders-and-the-performance-pack).
