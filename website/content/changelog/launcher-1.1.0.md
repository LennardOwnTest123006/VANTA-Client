---
product: launcher
version: 1.1.0
date: 2026-10-05
title: VANTA Launcher 1.1.0
minecraftVersion: 1.21.11
---

Feature release of the VANTA Launcher. Without Microsoft sign-in, PLAY now sets up the profile "VANTA 1.21.11" in the official Minecraft Launcher and opens it in one click. Before it changes the Minecraft Launcher's profiles, the launcher checks whether the Minecraft Launcher is still running, because it only reads new profiles when it starts. A performance pack (Sodium, Lithium, FerriteCore, ImmediatelyFast, Entity Culling and Iris Shaders) is installed from Modrinth, and the new Mods page searches Modrinth for mods, shaders and resource packs. If the launcher cannot start, it now says why instead of closing without a message.

## Added

- PLAY via Minecraft Launcher: when PLAY cannot sign you in (this build has no Microsoft sign-in, or no account is stored), the main button becomes "PLAY via Minecraft Launcher". One click adds or updates the profile "VANTA 1.21.11" in the official Minecraft Launcher and opens it; you pick "VANTA 1.21.11" next to its Play button and it signs you in with Microsoft. The first time you confirm the list of files it writes ("Add profile and open"); afterwards it updates the profile directly. Files that are already in place and verified are not downloaded again, and the progress shows the step and the file that is being downloaded
- "Open Minecraft Launcher" on Home after the profile was added (PLAY via Minecraft Launcher opens it by itself). It starts the classic Minecraft Launcher on Windows (`MinecraftLauncher.exe` in `Program Files (x86)\Minecraft Launcher`) or the Minecraft Launcher app from the Microsoft Store / Xbox app, the Minecraft app on macOS (`open -a Minecraft`) and `minecraft-launcher` from the `PATH` on Linux, and says so when none was found or it could not be started. On the command line: `--open-official-launcher`
- Running Minecraft Launcher check: before "Use with Minecraft Launcher" and "PLAY via Minecraft Launcher" write the profile, the launcher looks for a running Minecraft Launcher (`MinecraftLauncher.exe`, the Microsoft Store / Xbox app version, the macOS app and `minecraft-launcher` on Linux). If it runs, a dialog names its process and explains how to close it completely, on Windows also from the system tray; "Check again" looks again and "Continue anyway" writes the profile now (it then appears after the next start of the Minecraft Launcher). VANTA never closes the Minecraft Launcher for you. `--install-official-profile` prints the same warning
- Performance pack, on by default: PLAY, `--install` and "Use with Minecraft Launcher" install the newest Fabric versions for Minecraft 1.21.11 of Sodium, Lithium, FerriteCore, ImmediatelyFast, Entity Culling and Iris Shaders, plus the dependencies they require (Fabric API is installed by the launcher itself, as before). The versions are looked up on Modrinth at install time, every file is downloaded from the address Modrinth returns and checked against the SHA-512 Modrinth publishes for it, and a newer version replaces the older file. When Modrinth cannot be reached or a mod has no version for 1.21.11 yet, the install continues without it and says so. Settings > Game: "Install the performance pack" (`installPerformancePack` in settings.json); on the command line `--without-performance-pack` skips it once. The confirmation of "Use with Minecraft Launcher" lists the pack files
- Mods page: searches Modrinth for mods (Fabric), shaders (Iris) and resource packs with a version for Minecraft 1.21.11 (the most downloaded first while the search field is empty), and installs the newest version with its required dependencies into `mods/`, `shaderpacks/` or `resourcepacks/` of the VANTA game folder, verified with SHA-512. The installed list shows everything in these folders, also files you added yourself: switch an entry off (the file is renamed to `<name>.disabled`, nothing is deleted) or remove it, and "Update all" brings every project installed from Modrinth to its newest version. Fabric API and the VANTA Client are marked "VANTA" and stay managed by the launcher; a project another one needs cannot be removed or switched off on its own. Changes load the next time the game starts, also in the Minecraft Launcher profile
- `config/vanta/modrinth.json` in the game folder records every project installed from Modrinth (project, version, file, SHA-512, enabled, which project needs it, install time) for the launcher and the VANTA Client; keys the launcher does not know are kept
- Restart from the game: the launcher starts the game with `-Dvanta.launcher.restartable=true`. When the game exits after leaving `config/vanta/restart.request` in the game folder, the launcher removes the file and starts the game again, at most 5 times per PLAY (also with `--launch`)

## Improved

- Home without Microsoft sign-in reads "Ready to play" and "Ready via the Minecraft Launcher" instead of "Not ready", and the box below explains in two sentences why the Minecraft Launcher signs you in
- "Use with Minecraft Launcher" shows its progress on Home (preparing, each step, the file being downloaded) instead of waiting without feedback before the confirmation
- Every start writes the launcher log from its first line, including the Java, JavaFX and system details, so a failed start on Windows can be diagnosed from `%APPDATA%\VANTA Launcher\logs`
- Requests to Modrinth identify the launcher with a User-Agent that names the project, and the launcher waits and retries when Modrinth asks it to slow down (HTTP 429)

## Fixed

- The official Minecraft Launcher did not show the profile "VANTA 1.21.11" until it was restarted, because it reads its profiles only when it starts. The launcher now asks you to close it completely first (see "Running Minecraft Launcher check" above) and "Open Minecraft Launcher" starts it afterwards
- When the launcher could not start, for example because its window could not be created, it closed without any message. Now any start-up error is shown in a dialog with the details (a plain system dialog when JavaFX itself could not start), saved to `logs/startup-error.txt` in the launcher data directory (`%APPDATA%\VANTA Launcher\logs\startup-error.txt` on Windows) and the launcher exits with code 1

## Notes

- If the launcher still does not start for you on Windows, attach `startup-error.txt` and `launcher-0.log` from `%APPDATA%\VANTA Launcher\logs` to your report. Every commit is now checked on a Windows runner by installing the `.msi`, unpacking the portable zip and running the Windows jar, each of which must open the launcher window
- Switching the performance pack off stops installing and updating it; mods that are already in the game folder stay and can be switched off or removed on the Mods page
- The performance pack and everything from the Mods page are third-party projects from Modrinth under their own licences; the launcher installs them unchanged
- Microsoft sign-in inside the launcher still needs an application (client) id approved by Mojang; the launcher does not borrow another launcher's id. Until the project has one, the official Minecraft Launcher signs you in
- For automated tests the launcher reads `VANTA_UI_SMOKE_SCREENSHOT=<png>` (writes a screenshot of the window once it is shown) and `VANTA_UI_SMOKE_EXIT_AFTER=<seconds>` (closes the launcher with exit code 0 after that time); without them nothing changes
