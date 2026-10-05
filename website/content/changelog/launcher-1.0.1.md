---
product: launcher
version: 1.0.1
date: 2026-10-05
title: VANTA Launcher 1.0.1
minecraftVersion: 1.21.11
---

Maintenance release of the VANTA Launcher. A fresh launcher no longer offers an update for a client that is not installed, a launcher jar started on the wrong system says which file to download, and the launcher's self-update now uses the file that matches how the launcher was installed.

## Improved

- The launcher's self-update downloads the file that matches how the launcher was installed: the `.msi` for a launcher installed with the `.msi` or `.exe`, `VANTA-Launcher-<v>-windows-portable.zip` for the Windows portable folder, `vanta-launcher-<v>-windows-all.jar` or `vanta-launcher-<v>-linux-all.jar` for a launcher started with `java -jar`, the `.tar.gz` app image for the Linux app, and `vanta-launcher-<v>-macos-aarch64-all.jar` on Apple Silicon Macs. Intel Macs and Windows or Linux on ARM get the release page
- The portable zip is downloaded and its SHA-256 verified, then shown in its folder with instructions: close the launcher, then extract the zip into the folder that contains your portable `VANTA Launcher` folder (for example `D:\Games` for `D:\Games\VANTA Launcher`) and replace the existing files, so the zip's `VANTA Launcher` folder replaces the old one. For a renamed portable folder the instructions say to copy the contents of the zip's `VANTA Launcher` folder into it. The launcher never unpacks or runs the zip
- File sizes are shown in decimal units with one decimal place (1 MB = 1,000,000 bytes, for example "66.9 MB") in the launcher and on the command line, the same numbers the website and the release notes show
- The "Website" entries in the sidebar and on the About page are active and open https://vanta-client.netlify.app
- The launcher README names the platform jars on the command line: `java -jar vanta-launcher-<version>-<system>-all.jar` with `windows`, `linux` or `macos-aarch64`

## Fixed

- Fresh launcher: the VANTA Client card offered "Update" although no client was installed. "Install update" then put the jar into the instance's `mods/` folder, the card still said "Not installed" and the same update was offered again after a restart. Without an installed client the card now shows "Not installed" with "Install now" (the same install PLAY does, without starting the game) or "Use with Minecraft Launcher", and neither the update banner nor the update dialog offers a client update
- The client card, the update banner, the update check and `--check-update` now read the installed client from the same place: `instance.json` and the `vanta-client-<version>.jar` that is actually in `mods/` (a client installed with "Use with Minecraft Launcher" counts as installed). Updates of an installed client work as before
- `--check-update` on a fresh launcher printed "Client (not installed): update available: 1.0.0"; it now prints "Client: not installed (install it with --install or --install-official-profile); latest release 1.0.1"
- The update banner could read "You have Not installed yet"; it now has its own sentence for a client that is not installed
- Home screen at the default window size 1120×720: the Account card cut off "How to configure" and "Settings" ("How to co…", "S…"). Both buttons now show their full labels and have tooltips
- A launcher jar started on the wrong system (for example the Windows jar on Linux) failed inside JavaFX or showed nothing. It now checks the system before JavaFX is loaded, names the file to download instead (for example `vanta-launcher-1.0.1-linux-all.jar` or the app image `VANTA-Launcher-1.0.1-linux-x64.tar.gz`), also in a window when the jar was double-clicked, and exits with code 1. The message describes the Java runtime that started the jar, not the computer: an x64 Java on an Apple Silicon Mac (Rosetta 2) is told to use an arm64 Java 21 with `vanta-launcher-1.0.1-macos-aarch64-all.jar`, a 32-bit Java to use a 64-bit Java 21 or a download that brings its own Java runtime. Command line options such as `--help`, `--version` and `--install` keep working with every jar
- When the user interface cannot start (JavaFX missing, no display), the launcher now exits with code 1 instead of printing the help and exiting with 0
- Sign-in dialog: the expiry countdown of a new sign-in code could be overwritten by its initial value when the first timer tick came early (an intermittent race in the automated tests)
