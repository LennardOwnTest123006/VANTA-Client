---
product: launcher
version: 1.0.2
date: 2026-10-05
title: VANTA Launcher 1.0.2
minecraftVersion: 1.21.11
---

Maintenance release of the VANTA Launcher. Launcher updates are saved under their real file names, "Verify files" no longer starts a full install, a link that cannot be opened in your browser shows its address instead of doing nothing, and texts that pointed to PLAY when PLAY cannot work now point to "Use with Minecraft Launcher". The VANTA Client stays at 1.0.1. If you update from launcher 1.0.0 that runs from the portable folder or a jar, or check the download from launcher 1.0.0 or 1.0.1 with SHA256SUMS.txt, read the notes at the end first.

## Added

- The dialog after a verified launcher download shows the full path of the file in a field you can select and copy, and offers "Show in folder" (next to "Open installer" for the Windows installer)
- A VANTA Client update now keeps a rollback copy of the jar it replaces when that jar has a release version but no copy yet (for example a jar that was put into `mods/` by hand), as long as it is among the 3 newest versions afterwards; only the 3 newest versions are kept, as before. The Versions page marks such a copy as "Local copy from mods/": it is the jar exactly as it was, not a downloaded release, and Roll back makes exactly that file active again. The caption above the list now speaks of "client versions" instead of "verified releases"

## Improved

- Windows on ARM: a launcher jar started by an arm64 Java now recommends `VANTA-Launcher-<version>.msi` or the portable zip `VANTA-Launcher-<version>-windows-portable.zip` (both x64 with their own x64 Java runtime; Windows 11 on ARM runs them under x64 emulation) or an x64 Java 21 with `vanta-launcher-<version>-windows-all.jar`, instead of saying that there is no download
- Without Microsoft sign-in the account chip in the sidebar says "Sign-in not available" (it said "Sign in with Microsoft to play", cut off at the default window size 1120×720) and offers "How to configure", like the Home account card, instead of a "Sign in" button; its tooltip explains how to play through the Minecraft Launcher. With sign-in configured it says "Sign in with Microsoft" above the "Sign in" button
- Settings > Appearance: High contrast has its own description instead of repeating the theme text
- Home: "Verify files" has a tooltip that says what it does

## Fixed

- Launcher self-updates were saved as `cache/updates/<version>-<file name>`, so the dialog told you to run for example `java -jar 1.0.2-vanta-launcher-1.0.2-linux-all.jar` and `sha256sum -c SHA256SUMS.txt` could not find the file. Downloads are now saved as `cache/updates/<version>/<file name>` with the exact name of the release file, and the `java -jar` command in the dialog uses the full, quoted path
- "Verify files" on a fresh launcher started a full install of Minecraft, Fabric and the VANTA Client. It is now only shown when an installation exists and never starts a first install; the VANTA Client card's "Install now" (and PLAY) still install deliberately. As before, it also replaces an older VANTA Client with the latest release; its notification now says so instead of "Nothing was missing or damaged"
- The Versions page said "Press PLAY on the Home screen to install …" even when PLAY could not be enabled because Microsoft sign-in is not configured and no account is stored. It now points to "Use with Minecraft Launcher" in that case, and after "Use with Minecraft Launcher" it says that the game is started from the Minecraft Launcher with the profile "VANTA 1.21.11". With sign-in configured, or with a stored account, it still says to press PLAY, like the VANTA Client card on Home
- The "client updated" notification always said "the previous version is kept for roll back". It now names the kept version only when a copy of it really exists on the Versions page. Otherwise it says that no copy of the replaced version is kept, and either that the Versions page lists other kept versions to roll back to or that no other version is kept
- Settings > Advanced: with a long data directory path the "Open" button shrank to "...". The button keeps its label, the path is shortened with "..." in the middle and its tooltip shows the full path
- Opening a link (Website, Support, How to configure, release notes) could silently do nothing when JavaFX could not start a browser; the error only went to the console. The launcher now tries Java's desktop integration, then the system's own opener (`xdg-open` on Linux, `open` on macOS, `rundll32 url.dll,FileProtocolHandler` on Windows, checked by its exit code), then JavaFX. If none of them worked, the address is copied to the clipboard and shown in a notification. If only JavaFX accepted it (it cannot tell whether a browser opened), the address is shown in a notification and the clipboard is left alone, so a sign-in code you just copied stays there

## Notes

- Updating from launcher 1.0.0 or 1.0.1: those versions save the downloaded file in the `cache/updates` folder of the launcher data directory as `1.0.2-<file name>`, for example `1.0.2-vanta-launcher-1.0.2-linux-all.jar`. It is the verified release file under a different name: "Show in folder" opens that folder, and `java -jar 1.0.2-vanta-launcher-1.0.2-linux-all.jar` works there. To check it yourself with `sha256sum -c --ignore-missing SHA256SUMS.txt` from the release, rename it to the release name first (here `vanta-launcher-1.0.2-linux-all.jar`). From 1.0.2 on the file keeps its release name
- Updating from launcher 1.0.0: launcher 1.0.0 offers the `.msi` on Windows (also for the portable folder or a jar) and the `.tar.gz` on Linux x64 (also for a jar). To keep a portable or jar setup, choose Not now (or Close before downloading), close the launcher and download `VANTA-Launcher-1.0.2-windows-portable.zip` or the jar for your system from the launcher-v1.0.2 release on GitHub, then compare its SHA-256. From 1.0.1 on the launcher picks the file that matches how it was installed
