---
title: FAQ
description: FAQ about VANTA Client and Launcher: servers, versions, downloads, sign-in, the Minecraft Launcher profile, the Performance pack, shaders, Vanta Nexus and the Local AI, price, data and mods.
order: 41
category: Help
---

## Is VANTA allowed on servers?

VANTA adds no unfair advantage: no combat automation, no packet manipulation, no anti-cheat bypass, no player
tracking, no X-ray, nothing that shows you information about other players or the world beyond what vanilla shows.
Its HUD displays your own FPS, coordinates, armor, effects and keystrokes; the zoom only changes your camera FOV; the
Vanta Nexus assistant (from client 1.4.0 on) changes only your own client's HUD, settings, profiles, waypoints and Lab
features and never acts in the game world ([Vanta Nexus](nexus.md)). That said, **server rules vary**. Some servers forbid all client modifications, some whitelist specific mods, and the
server operators decide, not us. If a server's rules forbid client mods or require approval, respect them and ask.
We cannot promise that any particular server accepts VANTA.

## Does it include Sodium, OptiFine or other performance mods?

VANTA's own jar contains no third-party mods. From client 1.1.0 and launcher 1.1.0 on it can **install** the
**Performance pack** for you: Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling and Iris Shaders, downloaded
from Modrinth in their newest Fabric versions for 1.21.11 and checked with the SHA-512 Modrinth publishes. The VANTA
Launcher installs it by default (*Settings → Game → Install the performance pack* turns it off); in the game it is one
click on the Mods & Shaders screen. OptiFine is not part of it; shader packs run with Iris. The mods are made by their
own authors under their own licences. From client 1.2.0 on, the mods bundle for the manual installation also carries
the pack mods whose licences allow redistribution (all but EntityCulling), unmodified and with their licence texts in
`THIRD-PARTY-LICENSES.txt`. VANTA's own Performance Center still changes only **vanilla** video options: a preset shows
you exactly which ones before applying, and [Smart Boost](performance.md#smart-boost) (from client 1.3.0 on) applies
a preset's options (never Fast / Fancy / Fabulous, the frame-rate limit or VSync) by itself once after an install or
update, says so in a notification and can be undone. See
[Mods & Shaders](mods-and-shaders.md#the-performance-pack).

## How do I use shaders?

Shader packs need Iris, which is part of the Performance pack. In the game open **Mods & Shaders** from the VANTA main
menu, install Iris (or the Performance pack) and restart the game. Then pick a pack in the **Shaders** tab, install
it and choose **Open shader settings** (or press **O**) to turn it on. Shader packs cost much more graphics power than
vanilla rendering, so start with a lighter one. More in
[Mods & Shaders → Shaders with Iris](mods-and-shaders.md#shaders-with-iris).

## Which Minecraft versions are supported?

Exactly **Minecraft Java Edition 1.21.11**, with Fabric Loader 0.19.5 and Fabric API 0.141.6+1.21.11 on Java 21.
The mod refuses to load on any other version. Newer Minecraft versions get a dedicated VANTA release when they are
supported; the version is never changed silently.

## Is it free?

Yes. VANTA Client, the VANTA Launcher and all cosmetics are free. The code is MIT licensed. There is no store, no
subscription, no paid tier and nothing to unlock. Badges and themes are visual labels you choose for yourself.

## Does it collect data?

No. The client keeps optional **local** statistics in a JSON file on your computer and sends them nowhere. From client
1.4.0 on the client contacts exactly these hosts and nothing else, ever: Modrinth, only while you use Mods & Shaders
(search text, project ids, no account data), and, only when you install the Local AI, `github.com` (the llama.cpp
runtime archive) and `huggingface.co` (the model) together with the file hosts those two sites redirect the downloads
to (GitHub's release asset host `objects.githubusercontent.com`, Hugging Face's CDN hosts). The Vanta Nexus assistant's
prompts go to `127.0.0.1` only: no cloud AI, no API key, no account, no telemetry. The launcher talks only to Mojang,
Microsoft/Xbox, Fabric, Adoptium (Java), GitHub (releases, and the Local AI runtime when you agreed to it), Hugging Face
(the Local AI model, same condition), the file hosts those two redirect to, and Modrinth (Performance pack, Mods page)
to do its job. The website has no analytics, cookies or third-party scripts.
Details: [Statistics and privacy](statistics-and-privacy.md), [Privacy](privacy.md).

## What is Vanta Nexus?

A screen of client 1.4.0 (main menu button, the **N** key, `/vanta nexus`) with seven sections: an assistant you ask in
plain language ("Only show FPS and coordinates", "Create a recording profile"), the HUD Designer with six presets and
your named layouts, profiles, live performance values, waypoints, Vanta Lab and the Nexus settings. The assistant
applies only a fixed list of actions to your own client, shows exactly what it changed and lets you undo it. Details:
[Vanta Nexus](nexus.md).

## Is the Local AI a cloud service? Does it cost anything? Do I need an account?

No, no and no. The [Local AI](local-ai.md) is `llama-server` from llama.cpp (MIT) running the Qwen3-1.7B model
(Apache-2.0) **on your own PC**, started by the game on `127.0.0.1`. There is no cloud AI behind it, no API key, no
account, no subscription and no telemetry; nothing you type leaves your computer. Both parts are free software by their
authors; VANTA downloads them unchanged from GitHub and Hugging Face on your request.

## How big is the Local AI, and what does it need?

One download of about 1.85 GB: the model `Qwen3-1.7B-Q8_0.gguf` is 1.83 GB (1,834,426,016 bytes) and the `llama-server`
archive for your system 11.5 to 19.4 MB (19.4 MB on Windows x64, 17.7 MB on Linux x64, 12.0 MB on Apple Silicon). The
manifest asks for 2200 MB of free disk space and 3072 MB of free RAM while the server runs, next to the game. It runs on
the CPU; by default it stops after 10 minutes without a question and frees the memory
([Minecraft requirements → Local AI](minecraft-requirements.md#local-ai)). Every file is verified by SHA-256.

## Does the Local AI work offline?

Yes, after the one-time download. The server listens on `127.0.0.1` only and the game talks to it there; no internet
connection is needed to ask the assistant anything, and the Local AI never checks for updates or contacts a host
afterwards ([Local AI → Offline afterwards](local-ai.md#offline-afterwards)).

## Do I have to install the Local AI? How do I remove it?

No. Nothing is downloaded until you click **Install Local AI** in Vanta Nexus or **Download and install** in the VANTA
Launcher; the first-start dialogs only point you there, and *Not now* keeps everything as it is. Every other part of
VANTA, including the other six Nexus sections, works without it. To remove it: Vanta Nexus → Settings → **Remove Local
AI** when the game installed it, or *Settings → Local AI → Remove* (or `--remove-local-ai`) in the VANTA Launcher when
the launcher did. Only the Local AI folder is deleted; settings, profiles and waypoints stay
([Local AI → Verify, Reinstall, Remove](local-ai.md#verify-reinstall-remove)).

## Which systems can run the Local AI?

Windows x64 and ARM64, Linux x64 and ARM64, macOS on Apple Silicon and Intel: every system the llama.cpp release has a
CPU build for, detected from the Java runtime that runs the game. On another system Nexus says *Not available on this
system* and the rest of VANTA is unchanged. The VANTA Launcher itself is still published for Windows x64, Linux x64 and
Apple Silicon only; on an Intel Mac or Linux on ARM you install VANTA by hand and the game installs the Local AI itself.

## Do I need to buy Minecraft?

Yes. You need a Microsoft account that owns Minecraft Java Edition. The launcher verifies ownership the same way the
official launcher does and offers no way around it. VANTA is a client, not a copy of the game.

## Can I play offline?

Only as an "offline session" of an account that has signed in successfully before — the launcher reuses the verified
name and UUID, like Prism Launcher. Servers in online mode still require a valid session.

## Does VANTA work with Bedrock Edition?

No. Bedrock Edition cannot load Fabric mods.

## Does it work on Linux or macOS?

The client jar works wherever Fabric runs Minecraft 1.21.11. The launcher is published for Linux x64 (an app image
with its own Java runtime, and a jar that needs Java 21) and for Macs with Apple Silicon (a jar that needs Java 21).
There is no launcher build for Intel Macs or Linux on ARM; install VANTA there with the mods bundle and the Fabric
installer ([Installation → Manual installation](installation.md#c-manual-installation)). There is no signed or
notarised macOS app.

## Which launcher file do I need on Linux or macOS?

Linux x64: `VANTA-Launcher-1.5.0-linux-x64.tar.gz` (or `vanta-launcher-1.5.0-linux-all.jar` with Java 21). Apple
Silicon Mac: `vanta-launcher-1.5.0-macos-aarch64-all.jar`, started with `java -jar` (Java 21). The jars are not
interchangeable: each contains JavaFX for one system only, so the Windows or Linux jar does not run on a Mac. Started on
the wrong system, a jar does not open its window; it names the file to download instead (in a message window as well
when you double-clicked it) and exits with code 1. The macOS jar is built and tested from the command line in CI; its
window has not been tested yet. Details:
[Troubleshooting → Which launcher file](troubleshooting.md#linux-and-macos-which-launcher-file).

## Can I use VANTA without the VANTA Launcher?

Yes. Download `vanta-client-1.5.0-mods.zip`: it contains `vanta-client-1.5.0.jar`, Fabric API 0.141.6+1.21.11 and,
from client 1.2.0 on, the redistributable Performance pack mods (all but EntityCulling) in a `mods/` folder, plus
`INSTALL.txt` with the steps ([Mods & Shaders](mods-and-shaders.md#the-pack-in-the-mods-bundle)). Start the official Minecraft Launcher once,
install Fabric Loader 0.19.5 for 1.21.11 with the Fabric installer (keep *Create profile* checked) and copy all jars
into the `mods/` folder of that profile (official launcher, Prism, MultiMC). On Windows take the Fabric installer
`.exe`, which needs no separate Java; on macOS and Linux take the universal `.jar`, which needs Java installed (install
Java 21 first, then run `java -jar fabric-installer-<version>.jar`). If both Minecraft Launchers are installed, the
Fabric installer asks which one to use: choose the one you play with. You lose the launcher's verified updates,
rollback and Java helpers, nothing else; the Performance pack and other mods can be installed in the game from
*Mods & Shaders*. See [Installation](installation.md#c-manual-installation) and
[Fabric](fabric.md#manual-installation-into-an-existing-fabric-profile).

## What does "PLAY via Minecraft Launcher" do?

It is the main button of the VANTA Launcher when Microsoft sign-in is not available, which is the case in the
published builds (from launcher 1.1.0 on). One click checks that the official Minecraft Launcher is closed, sets up or
updates the profile **VANTA 1.21.11** in it (the first time after a confirmation that lists every file) and then
opens the Minecraft Launcher, where you choose that profile and press Play. The setup is the same as *Use with
Minecraft Launcher*, described in the next answer, including the Performance pack.

## What does "Use with Minecraft Launcher" do?

It is a button on the VANTA Launcher's Home screen (and the command `--install-official-profile`). It installs Fabric
API, the VANTA Client and (from launcher 1.1.0 on, unless switched off) the Performance pack into VANTA's game folder, adds the Fabric Loader 0.19.5 version to the official Minecraft
folder and writes the profile **VANTA 1.21.11** into the profiles files of the official Minecraft Launcher that exist
there: `launcher_profiles.json` (Minecraft Launcher from minecraft.net) and/or
`launcher_profiles_microsoft_store.json` (Minecraft Launcher from the Microsoft Store or the Xbox app). All your other
profiles are kept, and each profiles file it changes is backed up once (for example as
`launcher_profiles.json.vanta-backup`). You then choose that profile in the Minecraft Launcher and press Play; the
Minecraft Launcher downloads the game and Java and signs you in. CI checks the contents of both files on Linux, but
whether the Minecraft Launcher from the Microsoft Store or the Xbox app then shows the profile has not been tested on a
real Windows PC yet; if it does not, use the [manual installation](installation.md#c-manual-installation). Exactly
what is written: [Launcher → Use with the Minecraft Launcher](launcher.md#use-with-the-minecraft-launcher).

## Why does the profile "VANTA 1.21.11" not show up in the Minecraft Launcher?

The Minecraft Launcher reads its profiles only when it starts. If it was running while VANTA added the profile, close
it completely (on Windows also from the system tray: right-click its icon next to the clock, *Exit*) and start it
again. From launcher 1.1.0 on, VANTA checks for a running Minecraft Launcher first and asks you to close it. More in
[Troubleshooting](troubleshooting.md#the-profile-vanta-12111-does-not-show-up-in-the-minecraft-launcher).

## Why can't I sign in with Microsoft in the VANTA Launcher?

Microsoft sign-in for Minecraft only works with an application id that Mojang has approved for the Minecraft API. The
project does not have one, so the published launcher has sign-in switched off and its main button is *PLAY via
Minecraft Launcher*: the official Minecraft Launcher signs you in. (Launcher 1.0.x showed a disabled PLAY with
*Sign-in unavailable: play through the Minecraft Launcher*; use *Use with Minecraft Launcher* there.) You can also
configure an approved id of your own ([Launcher → Microsoft client id](launcher.md#microsoft-client-id)). VANTA
offers no offline workaround.

## Why does "Use with Minecraft Launcher" say the Minecraft Launcher is not set up?

*Use with Minecraft Launcher* adds its profile to the profiles files the official Minecraft Launcher keeps in your
Minecraft folder and never creates one: `launcher_profiles.json` (Minecraft Launcher from minecraft.net) and
`launcher_profiles_microsoft_store.json` (Minecraft Launcher from the Microsoft Store or the Xbox app). The message
means that neither file is there. The Minecraft Launcher creates its file the first time it starts: start it, sign
in, close it and try again. If your Minecraft folder is not in the default place, pass `--minecraft-dir <path>` on the
command line. More in
[Troubleshooting](troubleshooting.md#use-with-minecraft-launcher-says-the-profiles-file-is-missing).

## Can I use VANTA together with other mods?

Yes, VANTA is an ordinary Fabric mod, and *Mods & Shaders* (in the game) and the launcher's Mods page install other
mods from Modrinth for you. Mods that also replace the title screen compete with VANTA's main menu (turn VANTA's off in
*Settings → General*). Apart from the Performance pack, which CI loads in one of its game tests, we do not test
third-party combinations; see [Fabric](fabric.md#using-vanta-with-other-fabric-mods).

## Where do I download VANTA?

On the website's [Download page](https://vanta-client.netlify.app/download) or directly from
[GitHub Releases](https://github.com/LennardOwnTest123006/VANTA-Client/releases). The newest releases are
`launcher-v1.5.0` and `client-v1.5.0`. Both places offer the same files, built and published by the project's release workflow with their SHA-256 checksums. Do
not take VANTA files from anywhere else.

| Your system | File |
| --- | --- |
| Windows 10/11 x64 | `VANTA-Launcher-1.5.0.msi` (the installer, with its own Java runtime), or `VANTA-Launcher-1.5.0-windows-portable.zip` to run it without installing |
| Linux x64 | `VANTA-Launcher-1.5.0-linux-x64.tar.gz` (app image with its own Java runtime), or `vanta-launcher-1.5.0-linux-all.jar` with Java 21 |
| Mac with Apple Silicon | `vanta-launcher-1.5.0-macos-aarch64-all.jar` with Java 21 |
| Windows 11 on ARM | `VANTA-Launcher-1.5.0.msi` or `VANTA-Launcher-1.5.0-windows-portable.zip` (x64 with their own x64 Java runtime, run under emulation; not tested on such a device; [details](troubleshooting.md#windows-on-arm-which-launcher-file)) |
| Intel Mac, Linux on ARM, or no VANTA Launcher | `vanta-client-1.5.0-mods.zip` and the Fabric installer ([manual installation](installation.md#c-manual-installation)) |

From 1.4.0 on the `.msi` is the only Windows installer: releases before 1.4.0 also offered the same installer as an
`.exe`, which stays on their release pages. The client jar is the same on every system, and the launcher jars exist for
Windows, Linux and Apple Silicon macOS.

Verify the file with `SHA256SUMS.txt` from the same release before you run it
([Installation → Verify the checksum](installation.md#2-verify-the-checksum)). Older versions stay available on
GitHub Releases. The Download page lists every earlier release under *Older versions*, with its main file, checksum and release
notes. The release [`v1.5.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/v1.5.0) adds `VantaClient-1.5.0-Release.zip`, both releases plus the documentation in
one archive for anyone who wants everything at once, packed from the published files with the same checksums; you
still take only one launcher file from it
([Installation → The full release zip](installation.md#the-full-release-zip)).

**"Not published yet"** marks a version whose files the release workflow has not published yet. A new version's
release manifest is committed to the repository before the workflow runs; until the workflow has published its files,
the Download page keeps offering the newest published release and only mentions the new version, and the changelog
says the same. A product without any published release shows "Not published yet — release pending" with a disabled
button: there is nothing to download, and the page never links to a file that does not exist. If GitHub Releases
already lists a version that the Download page still calls not published, the website has not been rebuilt yet: take
the files from that release page and verify them with its `SHA256SUMS.txt`
([Troubleshooting](troubleshooting.md#the-download-page-says-not-published-yet)).

## How do I verify a download?

Compare the SHA-256 shown on the Download page (and in `SHA256SUMS.txt` on the release page) with
`Get-FileHash <file> -Algorithm SHA256` or `certutil -hashfile <file> SHA256` (Windows), `shasum -a 256 <file>` (macOS)
or `sha256sum -c --ignore-missing SHA256SUMS.txt` (Linux). The launcher does this automatically for everything it
downloads. [Installation](installation.md#2-verify-the-checksum).

## Windows says "Windows protected your PC". Is that normal?

Yes, for now: the installers and the portable app are not code-signed, which requires a paid certificate the project
does not have. Verify the checksum first; only if it matches choose *More info → Run anyway*. If it does not match,
do not run the file.
[Troubleshooting](troubleshooting.md#windows-protected-your-pc). If Windows blocks it with no *Run anyway* at all,
Smart App Control may be on; see
[Troubleshooting → Smart App Control](troubleshooting.md#smart-app-control-windows-11).

## Where are my settings, and can I back them up?

In `config/vanta/` inside the game directory (`<data directory>/instances/vanta-1.21.11/config/vanta/` with the
launcher). Copy the folder to back everything up, or export a [profile](profiles.md) for a single, shareable file.

## Can I share my HUD layout or settings with a friend?

Export a profile (Profiles → Export). It is a plain JSON file with settings, HUD layout, key overrides, crosshair and
cosmetics, and imports are validated so a bad file cannot do harm.

## How do I go back to the vanilla main menu?

*Settings → General → VANTA main menu* → off. Everything else in VANTA keeps working. (In client 1.0.x, *Cancel* on
*Create New World* also showed the vanilla title screen by mistake; that is fixed in 1.1.0.)

## How do I hide the HUD?

Bind *Toggle HUD* in the [keybind manager](keybinds.md), or turn *Settings → HUD → HUD enabled* off. Minecraft's F1
hides VANTA's HUD together with the vanilla one.

## Does the zoom key give an advantage?

It narrows your field of view like a spyglass without the item; it does not change reach, aim, hit detection or
anything the server can see. Some competitive servers nonetheless forbid zoom mods — check their rules.

## Why does the zoom key C conflict with a vanilla key?

Vanilla Minecraft also binds C to *Save Hotbar Activator* (Creative mode only: hold it and press a number key to save
your hotbar). VANTA keeps C as the zoom key because that is what players expect from a zoom, so out of the box the two
share a key: in Creative mode, holding C zooms, and pressing a number key while you hold it also saves your hotbar.
In Survival the vanilla binding does nothing. VANTA's keybind manager lists Zoom and Save Hotbar Activator in the C
conflict. If you use saved hotbars, rebind either one in *Options → Controls → Key Binds* (VANTA's keys are in the
VANTA category) or in the VANTA keybind manager (*Settings → Controls*). See [Keybinds → Zoom](keybinds.md#zoom).

The manager also lists other conflicts with every key at its default, for example on A, S, D and the middle mouse
button. With only VANTA and Fabric API installed, those are between vanilla bindings that share a default key; they
behave as in the game without VANTA and can be ignored. The C conflict is the only one VANTA adds
([Keybinds → Conflict detection](keybinds.md#conflict-detection)).

## Does the Performance Center make the game faster?

It can, by making Minecraft render less (lower render distance, fewer particles, …). It does not change the renderer
and we make no FPS claims. Its presets never cap the frame rate: the frame-rate limit and VSync are set only in the
frame-rate limit chooser, and **Boost FPS** applies the MAX FPS preset, removes the limit and turns VSync off in one
click. For more, it points to the Performance pack, separate mods such as Sodium that VANTA installs from Modrinth on
request (Boost FPS installs it too when it is missing). See
[Performance Center → Honest limits](performance.md#honest-limits). If VANTA 1.1.0's *Low* preset or a profile left
you at 60 or exactly 30 FPS, choose *Unlimited* once after updating to 1.2.0:
[Troubleshooting](troubleshooting.md#low-fps-or-exactly-30-fps-after-choosing-a-preset-or-profile-in-vanta-110).
From client 1.5.0 on, the first start turns VSync off and sets Max framerate to Unlimited once when the game still has
Minecraft's defaults (VSync on, 120), which held a 60 Hz monitor at 60 FPS; the title screen and VANTA main menu stay at
Minecraft's own 60 FPS menu limit, worlds are not capped
([Performance Center → One-time uncap](performance.md#one-time-uncap-client-150)).

## Why Java 21 and not the Java I already have?

Minecraft 1.21.11 itself requires Java 21, and VANTA is compiled for it. The launcher finds an installed 21 or
installs Eclipse Temurin 21 for you. [Java 21](java-21.md).

## What does the launcher store about my Microsoft account?

Access and refresh tokens, encrypted (`accounts.dat`, Windows DPAPI or AES-256-GCM with an owner-only key file),
plus your profile name, UUID and Xbox user id. Never a password — the device code flow means you sign in on
microsoft.com, not in the launcher. Signing out deletes the tokens.

## Where do I get help or report a bug?

GitHub Issues: [github.com/LennardOwnTest123006/VANTA-Client/issues](https://github.com/LennardOwnTest123006/VANTA-Client/issues),
with the information listed in [Troubleshooting](troubleshooting.md#how-to-report-a-problem). Other support channels
are configured by the project maintainers and shown on the website's Support page when available.

## Is VANTA affiliated with Mojang or Microsoft?

No. Minecraft is a trademark of Mojang AB / Microsoft. VANTA is an independent, open-source project and uses none of
their assets; vanilla textures are only referenced at runtime from your own game files.

## Can I contribute?

Yes — bug reports, documentation, translations and features that fit the no-cheats principle. See `CONTRIBUTING.md`
in the repository. Contributions that add cheats or unfair advantages are rejected without discussion.
