---
title: Statistics and privacy
description: What the local statistics record, where they are stored, and how to disable, export or delete them. Nothing ever leaves your computer.
order: 27
category: Client
---

VANTA keeps a small set of statistics about **your own** play, stored in a JSON file on your computer. There is no
account, no upload, no analytics endpoint and no identifier; the Statistics screen reads the same file you can open in
a text editor.

## What is recorded

For the running session, and summarised when it ends:

| Value | How it is measured |
| --- | --- |
| Playtime | time with a world or server loaded (the pause menu counts; the title screen and the other menus outside a world do not) |
| FPS | average and highest frame rate sampled while playing |
| Worlds played | names of singleplayer worlds you entered (optional, see privacy switches) |
| Servers visited | host names of servers you joined — only the host name, never the full address with port, never chat or players (optional) |
| Distance travelled | summed from your position changes between ticks; a jump of more than 50 blocks in one tick (teleport, dimension change, respawn) is ignored |
| Blocks broken / placed | counted from your own block interactions on the client |
| Screenshots | the number of screenshots you took |

The Vanta Nexus conversation (`config/vanta/nexus-chat.json`, [Vanta Nexus → The transcript file](nexus.md#the-transcript-file))
and your waypoints (`config/vanta/waypoints.json`, [Waypoints](waypoints.md)) are not statistics and are not shown on
the Statistics screen; they are local files of the same kind, never uploaded, and you delete them by deleting the file
(or with *Clear chat* in Nexus).

Lifetime totals (playtime, sessions, distance, blocks, screenshots, best FPS, distinct worlds and servers — at most 200
names each) and the **last 30 sessions** are kept. Nothing about other players is ever recorded.

## Where it is stored

`config/vanta/stats.json` in the game directory (with the VANTA Launcher:
`<data directory>/instances/vanta-1.21.11/config/vanta/stats.json`). It is pretty-printed JSON with a
`schemaVersion`, written atomically. Deleting the file resets everything.

## The Statistics screen

Open it from the main menu or *Settings → Privacy* (`/vanta stats` prints the current session in the chat instead).
It shows summary cards (total playtime, sessions, average FPS, highest FPS, worlds, servers, distance, blocks broken,
blocks placed) with small bars for the last 30 sessions, the current session, a sessions table (date, duration,
average FPS, world or server) and a privacy panel. A fresh installation shows honest empty states, not sample
numbers.

From client 1.5.0 on the totals are live. *Playtime* is the stored total plus the running session, shown with
seconds (`h:mm:ss`, or `m:ss` below an hour) and counting up every second while the screen is open and you are in a
world; the *This session* card shows the running session's play time the same way and says so when you are outside a
world, where play time stands still. Distance, blocks broken and placed, best FPS, worlds and servers include the
running session too. Only the screen's text changes: `stats.json` is still written once per session, when you quit
the game, so a crash loses the running session, as in earlier versions. With *Record statistics* off the totals stay
at the stored values, because a session that is not recorded is not added.

## Privacy switches

All in *Settings → Privacy* and mirrored on the Statistics screen:

| Switch | Default | Effect when off |
| --- | --- | --- |
| **Record statistics** | on | nothing is recorded at all; the session counter does not even start |
| **Remember servers** | on | server host names are not stored; existing names are removed from the lifetime list |
| **Remember worlds** | on | world names are not stored; existing names are removed |

The built-in **Recording** profile turns *Remember servers* off.

## Export and delete

- **Export JSON** writes a copy of the statistics to `config/vanta/exports/` (the notification shows the exact file
  name) and copies the same JSON to your clipboard. You decide where it goes from there.
- **Clear all** deletes every statistic after a confirmation. The file is rewritten empty; there is no backup and no
  server copy, because there is no server. The running session starts again from zero, so the cleared numbers are
  not recorded again when you quit.

## What never happens

- No statistic, setting, crash report or identifier is sent to the VANTA project or anyone else. From client 1.4.0
  on the client contacts exactly these hosts and nothing else, ever: Modrinth while you use
  [Mods & Shaders](mods-and-shaders.md), and, only when you install the [Local AI](local-ai.md), `github.com` (the
  llama.cpp runtime archive) and `huggingface.co` (the model) together with the file hosts those two sites redirect
  the downloads to (GitHub's release asset host `objects.githubusercontent.com`, Hugging Face's CDN hosts). None of
  them receives statistics. The Vanta Nexus assistant's prompts go to `127.0.0.1` only; there is no cloud AI, no API
  key, no account and no telemetry. The game's own connections (Mojang services, the servers you join) are unchanged.
- No statistic is shown to other players or servers.
- The launcher and the website follow the same rule — see [Privacy](privacy.md).
