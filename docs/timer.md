---
title: Timer
description: The timer of client 1.6.0: presets or your own time, pause and cancel, the ringing Time is up window with Stop, the HUD widget, the Open timer key and the /vanta timer command.
order: 33
category: Client
---

The **timer** (from client 1.6.0 on) is a countdown inside the game. When the time is up, a *Time is up* window opens
over what you are doing and a bell rings every second until you stop it. One timer runs at a time; it lives on your
own PC and nothing about it is sent anywhere.

## Opening the Timer screen

- the VANTA search (**Ctrl+K** on any VANTA screen, or the search button in the main menu's quick row): *Open timer*;
  "timer", "alarm", "countdown", "minutes", "wecker" and "stoppuhr" find it too;
- the **Open timer** key, unbound by default; assign it in the [keybind manager](keybinds.md). Like the other VANTA
  keys that open a screen, it reacts in a world while no other screen is open;
- [Vanta Nexus](nexus.md#2-hud-designer) → *HUD Designer* → *Timer* → **Open timer**;
- `/vanta timer` in chat.

## Setting a timer

The Timer screen has three cards:

- **Countdown**: the time left in large digits (`m:ss`, or `h:mm:ss` from one hour on) and a status line: *No timer
  is set.*, *Rings at 3:42 PM*, *Paused* or *Time is up*, with your label in front (*Tea: Rings at 3:42 PM*). The
  digits change once per second; partial seconds round up, so a fresh 10 minute timer reads `10:00` and the last
  second reads `0:01`. **Pause** / **Resume** and **Cancel** (it reads **Stop** while the alarm rings) work on the
  current timer.
- **Presets**: **1 min**, **5 min**, **10 min**, **15 min**, **30 min** and **60 min** start a timer at once, with
  the label typed in the third card.
- **Your own time**: **Minutes** and **Seconds** (digits only; they start at 10 and 0) and an optional **Label** of up
  to 32 characters, then **Start** or Enter in any of the three fields. The shortest timer is 1 second, the longest
  24 hours (1440 minutes); seconds go from 0 to 59. Anything else is refused with the reason under the fields and
  nothing starts.

Starting a timer (a preset, Start or the chat command) replaces the current one, also a ringing one. The timer keeps
running when you close the screen. It counts on your computer's clock, so it keeps going in menus, in the pause menu,
while a singleplayer world is paused, without a world and while the game window is in the background. "Rings at" is
the time of day in 12-hour time with AM and PM in your PC's time zone, read every time it is shown, so a change of
the PC's time zone or the switch to or from daylight saving time is followed without a restart.

## When the time is up

Within one game tick (50 ms) of the end, the timer rings:

- The **Time is up** window opens over the game, the pause menu, the inventory, the chat, a VANTA screen, the main
  menu or any other menu that Escape could close. While a screen is showing that Escape cannot close (for example
  while a world loads, or the death screen), the window waits and opens as soon as that screen is gone; the bell
  rings meanwhile. Like the other VANTA screens it pauses a singleplayer world.
- It shows your label (no line without one) and the time of day the timer ended (*Ended at 3:42 PM*, 12-hour time in
  your PC's time zone), and one large **Stop** button that has the keyboard focus.
- A bell rings at once and then every second: Minecraft's note block bell, played the way the game plays its button
  click (as an interface sound, not from a place in the world), so the game's volume settings apply. If the game
  froze for longer than a second, it rings once, not once for every second it missed. Without a sound device, or
  with the sound turned down, you still get the window.

Click **Stop**, or press **Enter** or **Escape**: the bell stops and the window closes, back to the screen that was
open before or to the game. If the alarm is stopped another way, the window closes by itself on the next tick. No
other shortcut works while the window is open (Ctrl+K does not leave it).

## The Timer HUD widget

The **Timer** widget of the [HUD](hud.md#widgets) shows the countdown while you play: your label (or *Timer*) and the
time left; for a paused timer the frozen time and *Paused* in the muted colour; *Time is up* in the widget's accent
colour while the alarm rings. While no timer is set it draws nothing at all, not even its panel. In the HUD editor it shows a sample
`9:59` marked *Preview* while no timer runs, so you can place it. Its option is *Show label*.

It is not part of any built-in layout or Nexus preset except *Full* (every widget): add it in the HUD editor's widget
list. It is anchored to the top-right corner by default.

## Chat command

| Command | Effect |
| --- | --- |
| `/vanta timer` | opens the Timer screen |
| `/vanta timer 10` | starts a 10 minute timer (whole minutes from 1 to 1440) |
| `/vanta timer 2:30 Tea` | starts 2 minutes 30 seconds labelled *Tea* (minutes, a colon and two digits of seconds) |
| `/vanta timer stop` | stops a ringing alarm, or cancels a running or paused timer (`cancel` works too) |

The answer appears in chat: *Timer started: 2:30, rings at 3:42 PM* (with a label: *Timer “Tea” started: ...*),
*Alarm stopped*, *Timer cancelled* or *No timer is set*. Input the command does not understand is answered in red
with the reason (for example *The longest timer is 24 hours (1440 minutes).*) and changes nothing. The command runs
only on your client; nothing is sent to the server.

## Limits

- A timer lives in memory only: closing the game ends it, and it does not continue at the next start.
- One timer at a time, from 1 second to 24 hours, with a label of at most 32 characters (control characters are
  dropped, spaces at both ends are trimmed).
