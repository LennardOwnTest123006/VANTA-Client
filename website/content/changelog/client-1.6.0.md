---
product: client
version: 1.6.0
date: 2026-10-11
title: VANTA Client 1.6.0
minecraftVersion: 1.21.11
---

Update of VANTA Client 1.5.0 for Minecraft Java Edition 1.21.11 (Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, Java 21). The clock shows your own time, there is a new timer with a ringing alarm, and the Vanta Nexus assistant does what players asked for: sound, chat size, crosshair, a tidy HUD and instant everyday commands.

## Fixed

- **The HUD clock showed the wrong hour.** It was formatted in UTC, so a player in Germany saw `6:00 AM` at `8:00 AM`. Every time of day VANTA shows (the HUD clock, Statistics sessions, profile cards, the Smart Boost date, the timer) now uses your PC's time zone and follows daylight saving time by itself
- **"Sort the GUIs" no longer puts every element on the same spot.** Whatever the assistant moves or shows again is checked at the end of the turn; overlapping elements are stacked apart and the receipt says so
- **Music, volume, chat and crosshair requests work.** "Make music off", "make the crosshair a circle" and "make the chat smaller" were rejected or ignored by the 1.5.0 assistant

## Added

- **Time zone setting** (*Settings → General → Time zone*): *Automatic (this PC)* by default, or pick one of 54 zones from Honolulu to Tonga, shown with their current offset such as `Berlin (UTC+2)`. It applies to every time of day VANTA shows
- **Timer.** Set a countdown with the presets 1, 5, 10, 15, 30 or 60 minutes or your own minutes and seconds (up to 24 hours), with an optional label; pause, resume or cancel it. It counts in real time, also in menus and with the game in the background. When the time is up, a *Time is up* window opens over whatever you are doing and a bell rings every second until you click **Stop** (Enter and Escape stop it too)
- Open the timer from the command palette (*Open timer*), the **Timer** HUD widget card in the Nexus HUD Designer, the unbound key *Open timer*, or the chat command `/vanta timer` (`/vanta timer 10`, `/vanta timer 2:30 Tea`, `/vanta timer stop`). A **Timer** HUD widget shows the time left while a timer runs and draws nothing otherwise
- **Assistant: sound.** The volume settings (master, music, records, weather, blocks, hostile, neutral, players, ambient, voice) and subtitles can be changed by asking, for example "music off" or "master volume 50 %"
- **Assistant: chat.** New chat settings on the Accessibility screen (text size, width, focused and unfocused height, line spacing); "make the chat smaller" lowers the size by one step and never below a readable minimum
- **Assistant: crosshair.** Ask for a shape, preset, size, thickness, gap, colour, outline or opacity ("make the crosshair a red circle"); Undo restores the previous style
- **Assistant: arrange the HUD.** "Sort the HUD" stacks every visible element in columns without overlaps, per corner or automatically
- **Instant commands.** Everyday requests in English or German (music and volume, crosshair, chat size, removing, showing, arranging and moving HUD elements) are understood by a built-in parser and applied in well under a second without starting the Local AI; they appear in the transcript with receipts and Undo like any other turn. Anything it does not understand still goes to the Local AI

## Notes

- Everything stays on your PC: the assistant sends nothing anywhere but `127.0.0.1`, and the instant commands do not use the Local AI at all
- A running timer lives in memory only: it is not kept when you close the game
- The in-game time of the clock (*In-game time (AM/PM)*) is unchanged
- Update through the VANTA Launcher (it offers 1.6.0 by itself) or replace `vanta-client-1.5.0.jar` with `vanta-client-1.6.0.jar` in your `mods` folder
