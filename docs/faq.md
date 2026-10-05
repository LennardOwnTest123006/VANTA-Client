---
title: FAQ
description: Frequently asked questions about VANTA Client and Launcher — servers, supported versions, downloads, sign-in, the Minecraft Launcher profile, price, data and mods.
order: 41
category: Help
---

## Is VANTA allowed on servers?

VANTA adds no unfair advantage: no combat automation, no packet manipulation, no anti-cheat bypass, no player
tracking, no X-ray, nothing that shows you information about other players or the world beyond what vanilla shows.
Its HUD displays your own FPS, coordinates, armor, effects and keystrokes; the zoom only changes your camera FOV.
That said, **server rules vary**. Some servers forbid all client modifications, some whitelist specific mods, and the
server operators decide, not us. If a server's rules forbid client mods or require approval, respect them and ask.
We cannot promise that any particular server accepts VANTA.

## Does it include Sodium, OptiFine or other performance mods?

No. VANTA bundles no third-party mods. The Performance Center changes **vanilla** video options (render distance,
particles, clouds, …) and shows you exactly which ones before applying. If you want renderer-level optimisation,
install such mods yourself; VANTA does not require, configure or test them.

## Which Minecraft versions are supported?

Exactly **Minecraft Java Edition 1.21.11**, with Fabric Loader 0.19.5 and Fabric API 0.141.6+1.21.11 on Java 21.
The mod refuses to load on any other version. Newer Minecraft versions get a dedicated VANTA release when they are
supported; the version is never changed silently.

## Is it free?

Yes. VANTA Client, the VANTA Launcher and all cosmetics are free. The code is MIT licensed. There is no store, no
subscription, no paid tier and nothing to unlock. Badges and themes are visual labels you choose for yourself.

## Does it collect data?

No. The client keeps optional **local** statistics in a JSON file on your computer and sends nothing anywhere. The
launcher talks only to Mojang, Microsoft/Xbox, Fabric, Adoptium (Java) and GitHub (releases) to do its job. The
website has no analytics, cookies or third-party scripts. Details: [Statistics and privacy](statistics-and-privacy.md),
[Privacy](privacy.md).

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

Linux x64: `VANTA-Launcher-1.0.1-linux-x64.tar.gz` (or `vanta-launcher-1.0.1-linux-all.jar` with Java 21). Apple
Silicon Mac: `vanta-launcher-1.0.1-macos-aarch64-all.jar`, started with `java -jar` (Java 21). The jars are not
interchangeable: each contains JavaFX for one system only, so the Windows or Linux jar does not run on a Mac. Started on
the wrong system, a jar does not open its window; it names the file to download instead (in a message window as well
when you double-clicked it) and exits with code 1. The macOS jar is built and tested from the command line in CI; its
window has not been tested yet. Details:
[Troubleshooting → Which launcher file](troubleshooting.md#linux-and-macos-which-launcher-file).

## Can I use VANTA without the VANTA Launcher?

Yes. Download `vanta-client-1.0.1-mods.zip`: it contains `vanta-client-1.0.1.jar` and Fabric API
0.141.6+1.21.11 in a `mods/` folder, plus `INSTALL.txt` with the steps. Start the official Minecraft Launcher once,
install Fabric Loader 0.19.5 for 1.21.11 with the Fabric installer (keep *Create profile* checked) and copy both jars
into the `mods/` folder of that profile (official launcher, Prism, MultiMC). You lose the launcher's verified updates,
rollback and Java helpers, nothing else. See [Installation](installation.md#c-manual-installation) and
[Fabric](fabric.md#manual-installation-into-an-existing-fabric-profile).

## What does "Use with Minecraft Launcher" do?

It is a button on the VANTA Launcher's Home screen (and the command `--install-official-profile`). It installs Fabric
API and the VANTA Client into VANTA's game folder, adds the Fabric Loader 0.19.5 version to the official Minecraft
folder and writes the profile **VANTA 1.21.11** into the profiles files of the official Minecraft Launcher that exist
there: `launcher_profiles.json` (Minecraft Launcher from minecraft.net) and/or
`launcher_profiles_microsoft_store.json` (Minecraft Launcher from the Microsoft Store or the Xbox app). All your other
profiles are kept, and each profiles file it changes is backed up once (for example as
`launcher_profiles.json.vanta-backup`). You then choose that profile in the Minecraft Launcher and press Play; the
Minecraft Launcher downloads the game and Java and signs you in. CI checks the contents of both files on Linux, but
whether the Minecraft Launcher from the Microsoft Store or the Xbox app then shows the profile has not been tested on a
real Windows PC yet; if it does not, use the [manual installation](installation.md#c-manual-installation). Exactly
what is written: [Launcher → Use with the Minecraft Launcher](launcher.md#use-with-the-minecraft-launcher).

## Why can't I sign in with Microsoft in the VANTA Launcher?

Microsoft sign-in for Minecraft only works with an application id that Mojang has approved for the Minecraft API. The
project does not have one, so the published launcher has sign-in switched off and PLAY stays disabled with *Sign-in
unavailable: play through the Minecraft Launcher*. Use *Use with Minecraft Launcher* instead, or configure an approved
id of your own ([Launcher → Microsoft client id](launcher.md#microsoft-client-id)). VANTA offers no offline workaround.

## Why does "Use with Minecraft Launcher" say the Minecraft Launcher is not set up?

*Use with Minecraft Launcher* adds its profile to the profiles files the official Minecraft Launcher keeps in your
Minecraft folder and never creates one: `launcher_profiles.json` (Minecraft Launcher from minecraft.net) and
`launcher_profiles_microsoft_store.json` (Minecraft Launcher from the Microsoft Store or the Xbox app). The message
means that neither file is there. The Minecraft Launcher creates its file the first time it starts: start it, sign
in, close it and try again. If your Minecraft folder is not in the default place, pass `--minecraft-dir <path>` on the
command line. More in
[Troubleshooting](troubleshooting.md#use-with-minecraft-launcher-says-the-profiles-file-is-missing).

## Can I use VANTA together with other mods?

Yes, VANTA is an ordinary Fabric mod. Mods that also replace the title screen compete with VANTA's main menu (turn
VANTA's off in *Settings → General*). We do not test third-party combinations; see
[Fabric](fabric.md#using-vanta-with-other-fabric-mods).

## Where do I download VANTA?

On the website's [Download page](https://vanta-client.netlify.app/download) or directly from
[GitHub Releases](https://github.com/LennardOwnTest123006/VANTA-Client/releases). The newest releases are
`launcher-v1.0.1` and `client-v1.0.1`; both places offer the same files, built and published by the project's
release workflow with their SHA-256 checksums. Do not take VANTA files from anywhere else.

| Your system | File |
| --- | --- |
| Windows 10/11 x64 | `VANTA-Launcher-1.0.1.msi` (installer with its own Java runtime), or `VANTA-Launcher-1.0.1.exe` if `.msi` files are blocked, or `VANTA-Launcher-1.0.1-windows-portable.zip` to run it without installing |
| Linux x64 | `VANTA-Launcher-1.0.1-linux-x64.tar.gz` (app image with its own Java runtime), or `vanta-launcher-1.0.1-linux-all.jar` with Java 21 |
| Mac with Apple Silicon | `vanta-launcher-1.0.1-macos-aarch64-all.jar` with Java 21 |
| Intel Mac, Linux on ARM, or no VANTA Launcher | `vanta-client-1.0.1-mods.zip` and the Fabric installer ([manual installation](installation.md#c-manual-installation)) |

Verify the file with `SHA256SUMS.txt` from the same release before you run it
([Installation → Verify the checksum](installation.md#2-verify-the-checksum)). Older versions stay available on
GitHub Releases.

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
[Troubleshooting](troubleshooting.md#windows-protected-your-pc).

## Where are my settings, and can I back them up?

In `config/vanta/` inside the game directory (`<data directory>/instances/vanta-1.21.11/config/vanta/` with the
launcher). Copy the folder to back everything up, or export a [profile](profiles.md) for a single, shareable file.

## Can I share my HUD layout or settings with a friend?

Export a profile (Profiles → Export). It is a plain JSON file with settings, HUD layout, key overrides, crosshair and
cosmetics, and imports are validated so a bad file cannot do harm.

## How do I go back to the vanilla main menu?

*Settings → General → Replace the title screen* → off. Everything else in VANTA keeps working.

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
and we make no FPS claims. See [Performance Center → Honest limits](performance.md#honest-limits).

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
