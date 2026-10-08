---
product: website
version: 1.1.0
date: 2026-10-08
title: Website 1.1.0
minecraftVersion: 1.21.11
---

The Download page now offers the full release zip, says which versions are the latest, shows what is new in them and lists every older version. VANTA Client and VANTA Launcher stay at 1.3.0; nothing in the game or the launcher changed.

## Added

- **Full release zip.** `VantaClient-1.3.0-Release.zip` (316.4 MB) is one archive with every published file of client 1.3.0 and launcher 1.3.0: the Windows installer as `.msi` and `.exe`, the portable Windows app, the Linux app, the launcher jars for Windows, Linux and Apple Silicon macOS, the client jar, the mods bundle, Fabric API, the documentation, `CHANGELOG.md`, both release notes, `LICENSE` and a `SHA256SUMS.txt`. The bundle workflow built it from the files already published on the GitHub Releases `client-v1.3.0` and `launcher-v1.3.0`, checked each one against its release manifest, uploaded the zip to the GitHub Release `v1.3.0`, downloaded it again and recorded its size, its SHA-256 and the SHA-256 of every file inside (all but its own `SHA256SUMS.txt`) in `shared/releases/bundles/vanta-1.3.0.json`, which the website reads
- **Latest version** block at the top of the Download page: the newest published client and launcher versions with their release dates, from the release manifests
- **Full release (zip)** card next to the launcher and client cards: version, release date, size, SHA-256 with a copy button, the download, the GitHub release page and, folded away, every file in the zip with its size and SHA-256. The card exists only while a bundle manifest with a published zip exists, so it never carries a dead link
- **What's new** section: up to five bullets from the release notes of the client and launcher versions on offer, the fixes first, with a link to the full notes of each and to the newest news post
- **Older versions** section at the end of the page: every published release older than the one the cards offer, for the launcher and the client (and for the full release zip once more than one is published), newest first, each with its release date, its primary file with size and SHA-256, a direct download, the GitHub release page and its release notes. The line "Looking for older versions or checksum files?" under the cards links to the section

## Improved

- Release-note excerpts on the Download page no longer show single-asterisk italics markers

## Notes

- VANTA Client and VANTA Launcher stay at 1.3.0 (releases `client-v1.3.0` and `launcher-v1.3.0` of 2026-10-07); nothing in the game or the launcher changed with this website version
- The zip is 316.4 MB because it holds every launcher build: the installer as `.msi` and `.exe`, the portable Windows app and the Linux app, each with its own Java runtime, and the three jars with their JavaFX natives, next to the client files and the docs. The cards offer the single file for your system when you do not need all of them
- Compare the SHA-256 of the zip before you unpack it, like any other download; inside, `SHA256SUMS.txt` checks every other file with `sha256sum -c` (macOS: `shasum -a 256 -c`) and `README.txt` says which file to take on which system
- Website changes between 1.0.0 and this version shipped together with the client and launcher releases and are recorded under their sections in `CHANGELOG.md` (1.0.1, Launcher 1.0.2, 1.1.0, 1.2.0, 1.3.0): the Download page keeps offering the newest published release while a newer version is committed but not published yet, canonical URLs, the download card layout, the same size rounding as the launcher, and the page texts for Mods & Shaders, the Performance pack and Boost FPS
