---
product: client
version: 1.4.1
date: 2026-10-09
title: VANTA Client 1.4.1
minecraftVersion: 1.21.11
---

Small update of VANTA Client 1.4.0 for Minecraft Java Edition 1.21.11 (Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, Java 21). Every time of day VANTA shows is now in 12-hour time with AM and PM. Everything else, including Vanta Nexus and the Local AI, is unchanged from 1.4.0.

## Improved

- **The HUD clock shows AM/PM only.** The system time reads like `2:32 PM` (with seconds `2:32:07 PM`) and the in-game time like `6:00 AM`; 12:00 is `12:00 PM` at noon and `12:00 AM` at midnight. The clock's *Time format* setting now offers *System time (AM/PM)* (the default) and *In-game time (AM/PM)*; the 24-hour option is gone
- A HUD layout or profile saved with the former 24-hour clock shows the system time in AM/PM from now on, without any action from you. With *Show seconds* on, the clock is at least 68 px wide so the longest time, `12:59:59 PM`, is never cut off
- **Statistics** shows session start times in AM/PM as well (for example `9 Oct 2026, 2:05 PM`)

## Notes

- Durations (session length, play time) keep their `h:mm:ss` form; they are not a time of day
- Update through the VANTA Launcher (it offers 1.4.1 by itself) or replace `vanta-client-1.4.0.jar` with `vanta-client-1.4.1.jar` in your `mods` folder
