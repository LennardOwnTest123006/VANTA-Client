---
title: FAQ
description: Frequently asked questions about VANTA Client and Launcher — servers, Sodium and OptiFine, supported versions, price, data, accounts, mods and more.
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

The client jar works wherever Fabric 1.21.11 runs. The launcher ships as a Windows installer, a portable jar
(Linux, macOS, Windows with Java 21) and a Linux app image. There is no notarised macOS build yet.

## Can I use VANTA without the VANTA Launcher?

Yes. Put `vanta-client-<version>.jar` and Fabric API into the `mods/` folder of any Fabric 0.19.5 / 1.21.11 profile
(official launcher, Prism, MultiMC). You lose the launcher's verified updates, rollback and Java helpers, nothing
else. See [Fabric](fabric.md#manual-installation-into-an-existing-fabric-profile).

## Can I use VANTA together with other mods?

Yes, VANTA is an ordinary Fabric mod. Mods that also replace the title screen compete with VANTA's main menu (turn
VANTA's off in *Settings → General*). We do not test third-party combinations; see
[Fabric](fabric.md#using-vanta-with-other-fabric-mods).

## Why is there no download yet?

Downloads are produced by the public release workflow, which builds the files, computes SHA-256 checksums, creates a
GitHub Release and updates the release manifests the website reads. Until that has run, the Download page honestly
says "not published yet" instead of linking to something that does not exist.

## How do I verify a download?

Compare the SHA-256 shown on the Download page (and in `SHA256SUMS.txt`) with `certutil -hashfile <file> SHA256`
(Windows), `shasum -a 256 <file>` (macOS) or `sha256sum <file>` (Linux). The launcher does this automatically for
everything it downloads. [Installation](installation.md#2-verify-the-checksum).

## Windows says the installer is from an unknown publisher. Is that normal?

Yes, for now: the installers are not code-signed, which requires a paid certificate. Verify the checksum first,
then choose *More info → Run anyway*. Signing is planned when the project has a certificate.

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
