---
title: Timer
description: The countdown timer of client 1.5.0: presets and custom durations, pause and cancel, the alarm window and its bell, the command palette entry and the /vanta timer chat command.
order: 32
category: Client
---

The **timer** (from client 1.5.0 on) is a countdown inside the game. When the time is up, a window opens over
whatever you are doing and a bell rings every second until you stop it with one click.

## Setting a timer

Open the *Timer* screen from the command palette or the settings search (*Open timer*; "timer", "alarm",
"countdown" and "minutes" find it too) or type `/vanta timer` in chat. On the screen:

- the preset buttons **1, 5, 10, 15, 30** and **60 minutes** start a timer at once;
- or type your own **minutes** and **seconds** (at least 1 second, at most 24 hours) and an optional **label** of
  up to 32 characters, then click **Start**;
- while it runs, the screen shows the time left (`m:ss`, or `h:mm:ss` from one hour on), refreshed every second,
  with **Pause** / **Resume** and **Cancel**.

One timer runs at a time; starting a new one replaces the current one. The timer counts in real time from your
computer's clock, so it keeps going in menus, in the pause menu, without a world and while the game window is in the
background.

## When the time is up

The *Time is up* window opens over the game, the pause menu, a VANTA screen or the main menu. It shows the label and
the time of day the timer ended (12-hour time with AM and PM, for example `2:35 PM`). A bell sound plays at once and
then every second. Click **Stop** (or press Enter or Escape): the ringing ends and the window closes, back to where you
were.

## Chat command

| Command | Effect |
| --- | --- |
| `/vanta timer` | opens the Timer screen |
| `/vanta timer 10` | starts a 10-minute timer (whole minutes from 1 to 1440) |
| `/vanta timer 2:30 Tea` | starts 2 minutes 30 seconds labelled *Tea* |
| `/vanta timer stop` | stops a ringing alarm or cancels a running or paused timer (`cancel` works too) |

The command runs only on your client; nothing is sent to the server.

## Limits

- A running timer lives in memory only: closing the game ends it, and it does not continue at the next start.
- The bell uses Minecraft's sound engine (the note block bell, in the *Master* volume); with the game muted or no
  sound device you still get the window, without sound.
