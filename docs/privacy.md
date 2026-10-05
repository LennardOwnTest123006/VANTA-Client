---
title: Privacy
description: What the VANTA Client, the VANTA Launcher and the website do and do not do with your data. No telemetry, local statistics only, encrypted account tokens, no cookies.
order: 31
category: Reference
---

This page describes what actually happens; it is kept in sync with the code and the release notes.

## Summary

- **No telemetry.** Neither the client nor the launcher nor the website sends usage data, crash reports or
  identifiers to the VANTA project or to third parties. VANTA runs no server.
- **Statistics are local** and optional. They stay in a JSON file on your computer.
- **Account tokens** are stored encrypted on your computer by the launcher and sent only to Microsoft, Xbox Live and
  Mojang services as part of signing in — the same services the official launcher uses.
- **The website has no analytics, no cookies and no third-party scripts.**

## VANTA Client (the Fabric mod)

The client stores its configuration in `config/vanta/` inside the game directory: settings, profiles, HUD layouts,
crosshair, cosmetics and `stats.json`. The statistics file may contain your playtime, FPS figures, the names of
singleplayer worlds and the host names of servers you joined, distance travelled and block counts — about **you
only**, never about other players. Each category can be switched off in *Settings → Privacy*, everything can be
exported or deleted, and the file never leaves your computer
([Statistics and privacy](statistics-and-privacy.md)).

The client opens **no network connections of its own**. It does not phone home, check for updates, fetch cosmetics or
load remote content. Minecraft's own connections (Mojang authentication, the servers you join, resource packs a server
sends) are not changed by VANTA.

HUD widgets show information the game already exposes about your own session (position, biome, effects, ping,
server name). The *Server* widget has a "hide address" option for streaming.

## VANTA Launcher

To install and start the game the launcher connects to:

| Service | Purpose |
| --- | --- |
| `piston-meta.mojang.com`, `piston-data.mojang.com`, `resources.download.minecraft.net`, `libraries.minecraft.net` | Minecraft version metadata, client jar, libraries and assets |
| `meta.fabricmc.net`, `maven.fabricmc.net` | Fabric Loader profile and libraries, Fabric API |
| `api.adoptium.net` and its download host | Eclipse Temurin 21 when you ask the launcher to install Java |
| `login.microsoftonline.com`, `user.auth.xboxlive.com`, `xsts.auth.xboxlive.com`, `api.minecraftservices.com` | Microsoft sign-in (device code flow), Xbox Live and Minecraft tokens, ownership and profile — only when a Microsoft client id is configured |
| GitHub: `raw.githubusercontent.com` (the built-in releases URL), `github.com` and its release asset hosts | release manifests (`client-latest.json`, `launcher-latest.json`) and VANTA downloads; a releases URL you configure instead is contacted in place of the built-in one |

Every request identifies the launcher with the user agent `VANTA-Launcher/<version>`. The launcher sends nothing
else and receives nothing it does not need to install or start the game. The privacy policies of Microsoft, Mojang,
FabricMC, Adoptium and GitHub apply to those services.

**Microsoft account data.** The device code flow means you sign in on `microsoft.com/link`; the launcher never sees
your password. It receives an access token and a refresh token, your Minecraft profile name and UUID and your Xbox
user id (xuid), and stores them in `accounts.dat` in its data directory:

- on Windows encrypted with **DPAPI** bound to your Windows user account;
- elsewhere encrypted with **AES-256-GCM** using a random key in `key.bin` readable only by your user.

Tokens are **redacted from all logs**. Signing out deletes the tokens; deleting `accounts.dat` has the same effect.
The game receives the access token as a command line argument exactly as the official launcher passes it.

**Logs.** `logs/launcher-N.log` and `logs/game-<timestamp>.log` contain technical information (versions, paths,
download progress, errors) and whatever the game prints, which includes your profile name and the worlds and servers
you play. They stay on your computer; you decide whether to attach them to a bug report.

**Settings.** `settings.json` holds launcher preferences only, including the Microsoft client id and releases URL if
you configured them.

**Use with Minecraft Launcher.** This writes only local files: Fabric API and the VANTA Client into the VANTA game
folder, the Fabric Loader version files into the official Minecraft folder and the profile *VANTA 1.21.11* into
each profiles file of the official Minecraft Launcher that exists there, `launcher_profiles.json` and/or
`launcher_profiles_microsoft_store.json` (each with a one-time backup).
VANTA reads these files only to keep your other profiles unchanged and sends nothing from them anywhere. Sign-in,
downloads and play are then handled by the official Minecraft Launcher under Microsoft's and Mojang's terms.

## Website

The website is a static site served by Netlify. It sets **no cookies**, loads **no analytics**, fonts or scripts from
third parties (fonts are self-hosted), and has no forms or accounts. Netlify, as the hosting provider, processes
request logs (IP address, user agent, requested URL) under its own privacy policy; the VANTA project does not receive
or store them. Security headers (`Content-Security-Policy`, `X-Frame-Options: DENY`, `Referrer-Policy:
strict-origin-when-cross-origin`, a restrictive `Permissions-Policy`) are set for every page.

Links to GitHub, Microsoft, Mojang, Fabric or other sites lead to services with their own policies.

## Children

VANTA has no accounts, no chat and collects no data, so there is nothing to collect from anyone. Microsoft account
age rules apply to signing in to Minecraft (child accounts need family consent, see
[Troubleshooting](troubleshooting.md#microsoft-sign-in-errors)).

## Contact

Privacy questions can be raised through the contact channel configured by the project maintainers, which is shown on
the Support page of the website when available, or as a GitHub issue if the question is not sensitive.

## Changes

This page changes only when the software changes. Changes are listed in the changelog of the release that introduces
them.
