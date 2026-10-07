---
product: launcher
version: 1.2.0
date: 2026-10-06
title: VANTA Launcher 1.2.0
minecraftVersion: 1.21.11
---

Maintenance release of the VANTA Launcher. A notification (toast) in a small window could cover the lower Install buttons of the Mods page and, because it stayed open while the mouse rested on it, swallow every click there. Toasts now close on a click, pause for at most 8 seconds, and in narrow windows, or whenever the shown page is taller than the window and scrolls, move to the top-right corner where they cover no controls. Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11 and Java 21 are unchanged, and the launcher installs VANTA Client 1.2.0.

## Added

- For automated tests the launcher reads two more environment variables, which do nothing on their own: `VANTA_UI_SMOKE_PAGE=<home|mods|versions|logs|settings|about>` shows that page before the screenshot, and `VANTA_UI_SMOKE_SIZE=<width>x<height>` sizes the window first (a size below the 960 x 600 minimum is raised to it). Both work only together with `VANTA_UI_SMOKE_SCREENSHOT` or `VANTA_UI_SMOKE_EXIT_AFTER`
- The headless UI test opens the Mods page in a 960 x 600 window and clicks its first and last *Install* button through the platform's robot, the last one after scrolling and after the toast over it was dismissed by the click; it also checks where a toast lands at 960 x 600, 1100 x 600, 1120 x 720 and 1200 x 700

## Fixed

- A notification in the bottom-right corner could swallow clicks on what lay under it. In a small window (960 x 600) that corner holds the lower *Install* buttons of the Mods page, and because a toast stayed as long as the mouse rested on it, a mouse moved onto such a button kept the toast open and every click did nothing (in a maximised window the Install column is nowhere near that corner). A click anywhere on a toast now dismisses it, and the pause while the mouse is over a toast ends after 8 seconds at the latest
- In a window narrower than 1100 px, and in any window in which the shown page is taller than the window and scrolls (at the 1120 x 720 default the Mods page's lowest installed switches and remove buttons sit in the bottom-right corner), the toasts now appear top-right, below the update banner and the page header, and a toast is never wider than 30 percent of the window (at most 360 px), so the Mods page's *Install* buttons, its header actions and the installed list's switches are no longer under a toast. On a page that fits the window the toasts stay bottom-right

## Notes

- Updating from launcher 1.1.0, 1.0.2 or 1.0.1: the launcher offers this update itself with the file that matches how it was installed (the `.msi` for an installed launcher, the portable zip for the portable folder, the `.tar.gz` for the Linux app image, the jar for your system when started with `java -jar`). Verify the SHA-256 of anything you download by hand
- Updating from launcher 1.0.0: launcher 1.0.0 offers the `.msi` on Windows (also for the portable folder or a jar) and the `.tar.gz` on Linux x64 (also for a jar). To keep a portable or jar setup, choose Not now (or Close before downloading), close the launcher and download `VANTA-Launcher-1.2.0-windows-portable.zip` or the jar for your system from the launcher-v1.2.0 release on GitHub, then compare its SHA-256. Launchers 1.0.0 and 1.0.1 save a downloaded update as `1.2.0-<file name>` in `cache/updates`; rename it to the release name before checking it with `sha256sum -c --ignore-missing SHA256SUMS.txt`
- The launcher keeps installing the whole Performance pack from Modrinth, as in 1.1.0. The mods bundle for a manual installation of VANTA Client 1.2.0 now carries the redistributable pack mods itself; that does not change what the launcher does
- Microsoft sign-in inside the launcher still needs an application (client) id approved by Mojang; the launcher does not borrow another launcher's id. Until the project has one, PLAY goes via the official Minecraft Launcher, which signs you in
- Nothing is code-signed yet, so Windows SmartScreen may warn; compare the SHA-256 from `SHA256SUMS.txt` first
