---
product: client
version: 1.5.0
date: 2026-10-10
title: VANTA Client 1.5.0
minecraftVersion: 1.21.11
---

Update of VANTA Client 1.4.1 for Minecraft Java Edition 1.21.11 (Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, Java 21). The game is no longer held at 60 FPS by Minecraft's default VSync, and the Statistics play time counts up live every second. Vanta Nexus, the Local AI and everything else work as in 1.4.1.

## Fixed

- **No more 60 FPS cap.** A new game folder starts with Minecraft's own defaults, VSync on and Max Framerate 120, and VANTA only followed what the game had, so on a 60 Hz monitor the game stayed at 60 FPS even with *Unlimited* chosen. On the first start of 1.5.0 VANTA checks the two options once: with VSync on or Max Framerate below Unlimited it turns VSync off and sets Max Framerate to Unlimited, saves `options.txt` once and shows the notification *Frame rate uncapped*. No other option is touched. The check is recorded in `config/vanta/frame-rate.json` and never runs again, so turning VSync back on or setting a limit afterwards stays your choice
- **Unlimited means unlimited.** The frame-rate choice reads *Unlimited (VSync off)* and *VSync (monitor refresh rate)*, and moving VANTA's Max Framerate slider to Unlimited also turns VSync off. No preset, profile, Smart Boost step, render-distance advice or Nexus action turns VSync on or sets a limit by itself; new tests check each of them
- **Statistics play time is live.** *Playtime* is the stored total plus the running session and counts up every second while the Statistics screen is open and you are in a world (the pause menu counts, the title screen does not), shown as `h:mm:ss` (or `m:ss` below an hour). The *This session* card shows the running session the same way and says when play time stands still outside a world. Distance, blocks, best FPS, worlds and servers include the running session too. Only the text on the screen changes: `stats.json` is still written once per session, when you quit the game

## Notes

- Minecraft itself limits menus without a world (the title screen and the VANTA main menu) to 60 FPS and lowers the frame rate while you are away from the keyboard; VANTA changes neither. Worlds are not capped. How many frames your PC reaches depends on your hardware; VANTA promises no number
- Clear all on the Statistics screen now also restarts the running session from zero, so cleared numbers are not recorded again when you quit
- Update through the VANTA Launcher (it offers 1.5.0 by itself) or replace `vanta-client-1.4.1.jar` with `vanta-client-1.5.0.jar` in your `mods` folder
