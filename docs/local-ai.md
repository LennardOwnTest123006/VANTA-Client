---
title: Local AI
description: The Local AI behind Vanta Nexus: llama-server b11429 and Qwen3-1.7B Q8_0, what is downloaded from where and how big, SHA-256 checks, the two install paths, offline use, settings, removal, licences.
order: 30
category: Client
---

The **Local AI** is the language model behind the [Vanta Nexus](nexus.md) assistant. It runs on **your own PC** as a
child process of the game and nowhere else:

- the runtime is **`llama-server` from [llama.cpp](https://github.com/ggml-org/llama.cpp)**, release
  [`b11429`](https://github.com/ggml-org/llama.cpp/releases/tag/b11429), MIT licence;
- the model is **[Qwen3-1.7B](https://huggingface.co/Qwen/Qwen3-1.7B-GGUF)** in the GGUF format, quantisation
  **Q8_0**, Apache-2.0 licence, 4,096 tokens of context.

Both are downloaded **once**, only after you click **Install Local AI** (in the game) or **Download and install** (in
the VANTA Launcher), verified by SHA-256 and kept in a folder on your computer. Afterwards the assistant works
**offline**: the only network connections the Local AI ever makes are those two downloads. The assistant's requests go
to `127.0.0.1` (your own computer) and nowhere else. There is **no cloud AI, no API key, no account and no telemetry**,
and nothing you type is sent anywhere ([Privacy](privacy.md#the-local-ai)).

VANTA Client 1.4.0 works without the Local AI: every section of Vanta Nexus except the assistant, and everything else
in VANTA, needs nothing from it.

## What is downloaded, from where, how big

Every file name, URL, size and SHA-256 comes from one committed manifest, `shared/local-ai/local-ai.json` in the
repository, which CI filled in from the llama.cpp release page and the Hugging Face API and verified by downloading
the files (`node scripts/release/local-ai.mjs resolve`); nobody types these values by hand. The client and the launcher
embed a copy of that manifest when they are built, and the [full release zip](installation.md#the-full-release-zip)
carries it as `local-ai/local-ai.json` next to a readable `LOCAL-AI.txt`.

| File | From | Size | Licence |
| --- | --- | --- | --- |
| `llama-b11429-bin-win-cpu-x64.zip` (Windows x64) | `github.com`, the llama.cpp release `b11429` | 19.4 MB | MIT |
| `llama-b11429-bin-win-cpu-arm64.zip` (Windows on ARM) | `github.com` | 12.2 MB | MIT |
| `llama-b11429-bin-ubuntu-x64.tar.gz` (Linux x64) | `github.com` | 17.7 MB | MIT |
| `llama-b11429-bin-ubuntu-arm64.tar.gz` (Linux on ARM) | `github.com` | 13.7 MB | MIT |
| `llama-b11429-bin-macos-arm64.tar.gz` (Apple Silicon) | `github.com` | 12.0 MB | MIT |
| `llama-b11429-bin-macos-x64.tar.gz` (Intel Mac) | `github.com` | 11.5 MB | MIT |
| `Qwen3-1.7B-Q8_0.gguf` (every system) | `huggingface.co`, the repository `Qwen/Qwen3-1.7B-GGUF` | 1.83 GB (1,834,426,016 bytes) | Apache-2.0 |

Only **one** runtime archive is downloaded, the one for your system, plus the model: about **1.85 GB** on Windows x64,
a little less on the other systems. Sizes here are decimal (1 MB = 1,000,000 bytes), as on the Download page and in the
launcher; the card in the game rounds in 1024-byte units, so it shows the same model as about 1.71 GB. The manifest
asks for **2200 MB of free disk space** and **3072 MB of free RAM** while the server runs
([Minecraft requirements → Local AI](minecraft-requirements.md#local-ai)).

The downloads are plain HTTPS requests to those two sites (following the redirects they send to their file hosts),
with a User-Agent that names the client (`VANTA-Client/<version> (https://github.com/LennardOwnTest123006/VANTA-Client)`)
or the launcher (`VANTA-Launcher/<version>`). Nothing about you goes along: no account, no name, no settings, no
statistics. Like any web server, GitHub and Hugging Face see your IP address; their privacy policies apply to that.

## Verification

Every file is streamed to disk through SHA-256 and compared with the size **and** the SHA-256 from the manifest. A
file that does not match is deleted and reported (*A downloaded file did not match its checksum*, *A downloaded file
has the wrong size*); nothing is installed from it and VANTA never offers to skip the check. The runtime archive is
extracted only after it matched, into a staging folder that is moved into place when `llama-server` is really
inside; archive entries that would land outside the folder are refused. On Linux and macOS the server gets its
executable bit.

What was installed is recorded in `installed.json` in the Local AI folder: the platform, the install and verification
times, the manifest's runtime and model sections with their SHA-256 values, and the size and modification time of the
server executable and the model. At every game start a **quick check** compares the recorded sizes and times with the
files (*Installed*, *Incomplete install* or *Not installed*). **Verify files** in Nexus and in the launcher hashes the
model again in full; the launcher also re-hashes after every download.

## Two install paths

Which one you use follows from how you installed VANTA
([Installation → Choose how to install](installation.md#choose-how-to-install)):

| | VANTA Launcher (paths A and B) | Game only (path C, or a launcher without the Local AI) |
| --- | --- | --- |
| Who downloads and verifies | the launcher | the game (Vanta Nexus) |
| Folder | `<launcher data directory>/local-ai/` | `<game folder>/config/vanta/local-ai/` |
| Install, Verify, Remove | Settings → Local AI in the launcher, or the command line | Vanta Nexus → Settings (and the install card of the assistant) |
| What the game does | starts `llama-server` from the launcher's folder and uses it **read-only**; its own Install and Remove buttons are hidden | everything |

### Installing with the VANTA Launcher

- **First start.** When launcher 1.4.0 starts with *Install the Local AI automatically* on (the default) and nothing is
  installed yet, it asks once per session: **Install the Local AI?** The dialog names the runtime and the model with
  their versions, file names, sizes, licences and hosts, the folder, the total download and the disk and memory the
  manifest asks for. **Download and install** records your consent (`localAiAccepted` in `settings.json`) and starts
  the download; **Not now** switches the automatic installation off, so the question is not asked again. Nothing is
  downloaded before you agree.
- **Afterwards.** Once you agreed, the launcher keeps the files complete: at every start with consent recorded and
  files missing or outdated it installs them right away without asking again, and PLAY and `--install` (the regular
  installation) run the step **Installing the Local AI** after the Performance pack: *Checking Local AI*, *Downloading
  Local AI runtime*, *Downloading Local AI model*, *Verifying files*, *Installing*, *Local AI ready*, with the usual
  progress bar, bytes and speed. Files that are already there and verified are not downloaded again. Like the
  Performance pack, a failed Local AI step never fails the installation: the log says *Local AI skipped: <reason>* and
  the game starts without it. *PLAY via Minecraft Launcher*, *Use with Minecraft Launcher* and
  `--install-official-profile` do not install the Local AI themselves; they only write the note below. The model
  download may take a while on a slow connection; the launcher allows the request up to six hours and gives up when
  the transfer stalls for 60 s.
- **Settings → Local AI** shows the status and the installed versions, the size on disk or the download size, problems
  when there are any, the folder with an **Open** button, **Install** (or **Update** when the manifest of a newer
  launcher names other files), **Cancel**, **Verify files**, **Remove** (asks first) and the switch *Install the Local
  AI automatically*. The Home screen has the line *Local AI: ready* / *not installed* / *incomplete* / *not available
  for <system>* with **Install Local AI** or **Local AI settings** ([Launcher → Local AI](launcher.md#local-ai)).
- **The note for the game.** Every VANTA game folder the launcher sets up gets `config/vanta/local-ai.json`,
  `{"localAiDir": "<absolute path of the launcher's local-ai folder>"}`, next to `minecraft-folder.json`. The game
  starts `llama-server` from that folder, checks the files and never writes into them (apart from the server log,
  see below); Nexus shows *Installed by the VANTA Launcher*. The note is written whether or not the Local AI is
  installed; while the folder it points at holds no complete install, the game falls back to its own folder.
- **Command line**: `--install-local-ai`, `--local-ai-status`, `--remove-local-ai`, and `--with-local-ai` /
  `--without-local-ai` for `--install` ([Launcher → Command line reference](launcher.md#command-line-reference)).

### Installing from the game

Without the launcher's note, the game manages the Local AI itself:

1. Open **Vanta Nexus** (main menu, **N**, `/vanta nexus`). While the Local AI is not installed, the assistant section
   shows the Local AI card with the facts above (in client 1.4.0 always; the switch *Settings → Vanta Nexus → Offer the
   Local AI install when Nexus opens* is stored but not read yet). The first time the main menu is shown in a game
   session, a dialog also points you to Nexus
   ([Vanta Nexus → When the Local AI is not installed](nexus.md#when-the-local-ai-is-not-installed)).
2. Click **Install Local AI**. The **Local AI setup** screen opens with the same facts, the hosts and the sentence
   *Nothing is downloaded until you click Install Local AI*. Click **Install Local AI** there: that click is the only
   thing that starts a download.
3. The seven steps run with a progress bar, bytes and speed each: *Checking Local AI*, *Downloading Local AI
   runtime*, *Downloading Local AI model*, *Verifying files*, *Installing*, *Initializing Local AI* (the server starts
   for the first time) and *Vanta Nexus ready*. **Cancel** stops the download (verified files are kept, `.part` files
   are removed). You can keep playing meanwhile; a notification follows the progress.
4. On success the screen says **Vanta Nexus ready** and offers **Open the assistant**. On failure it shows the reason
   (connection problem, checksum mismatch, the archive could not be read, a file could not be written, …) and
   **Retry**.

The install lands in `config/vanta/local-ai/` of the game folder, and Nexus → Settings shows *Managed by: VANTA Client
(config/vanta/local-ai)*.

## Where the files are

Inside the Local AI folder (`<launcher data directory>/local-ai/` or `<game folder>/config/vanta/local-ai/`):

```text
installed.json                       what is installed: platform, times, the manifest sections, sizes and SHA-256
runtime/b11429/<platform>/           the extracted archive; llama-server (or llama-server.exe) inside it
models/Qwen3-1.7B-Q8_0.gguf          the model
downloads/                           <name>.part while a download runs; empty afterwards
logs/llama-server.log                the output of the server process (the game writes it when it starts the server)
```

`<platform>` is `windows-x64`, `windows-arm64`, `linux-x64`, `linux-arm64`, `macos-arm64` or `macos-x64`. The launcher
data directory is `%APPDATA%\VANTA Launcher` (Windows), `~/Library/Application Support/VANTA Launcher` (macOS) or
`~/.local/share/vanta-launcher` (Linux); the game folder is the VANTA instance or `.minecraft`
([Installation → Where files live](installation.md#5-where-files-live)). The conversation itself is not in this
folder but in `config/vanta/nexus-chat.json` ([Vanta Nexus → The transcript file](nexus.md#the-transcript-file)).

## Offline afterwards

Once `installed.json` and the two files are in place, nothing of the Local AI touches the network again: the server
listens on `127.0.0.1` on a free port the game picks, the game talks to it over that port, and the only check at start
is the quick check of the local files. You can play and use the assistant without an internet connection. A new VANTA
release can name a newer runtime or model in its manifest; the launcher then shows **Update** and Nexus shows
*Incomplete install* with **Install Local AI**, and again nothing is downloaded before you click.

## Idle timeout, threads and settings

The game starts `llama-server` the first time the assistant needs it (*Initializing Local AI*), with these arguments
and nothing else:

```text
llama-server -m <model> --host 127.0.0.1 --port <free port> -c 4096 -t <threads> -ngl 0 --jinja --reasoning-budget 0 --no-webui
```

`-ngl 0` keeps the model on the CPU; `--no-webui` switches off the server's own web page, so the port serves only the
game. The settings in *Settings → Vanta Nexus* (also in Nexus → Settings):

| Setting | Default | Effect |
| --- | --- | --- |
| Vanta Nexus assistant | on | off: the assistant section shows a banner with *Turn on*; the server is not started |
| Offer the Local AI install when Nexus opens | on | meant to show the install card with sizes and licences while nothing is installed; in client 1.4.0 the switch is stored but not read, and the card is shown whenever nothing is installed. Nothing downloads before you click Install either way |
| Stop the Local AI after idle minutes | 10 (1 to 120) | the server process is stopped after this many minutes without a question and started again on the next one, which frees its RAM while you play |
| Local AI CPU threads | 0 = automatic (0 to 32) | threads for `-t`; automatic is at least 2, at most 8, two fewer than your CPU's cores. A change applies at the next start of the server |
| Keep the Local AI running | off | never stop the server for idleness while the game runs; answers come without the start-up wait, the model stays in RAM |
| Show the model's reasoning | off | would show a reasoning text in the transcript; Nexus asks for answers without a thinking phase, so with the bundled model it stays empty |

The server is always stopped when the game exits (a shutdown hook also covers a crash of the game) and when you remove
the Local AI. Its output goes to `logs/llama-server.log` in the Local AI folder.

## Verify, Reinstall, Remove

In **Vanta Nexus → Settings** while the Local AI is installed by the game:

- **Verify files** hashes the model again and checks the server, then reports *Runtime and model match the manifest*
  or *Some files do not match; reinstall to fix them*.
- **Reinstall** opens the setup screen; the install downloads only what is missing or wrong and checks the rest.
- **Remove Local AI** asks *Remove the Local AI? Deletes the runtime and the model from <folder>. Nothing else is
  touched.*, stops the server and deletes the Local AI folder, nothing else: your settings, profiles, waypoints and the
  conversation stay. Install it again at any time.

When the launcher manages the Local AI, these buttons are in the launcher (*Settings → Local AI*: **Verify files**,
**Install** / **Update**, **Remove**), and the game shows only the status. The launcher's **Remove** also switches
*Install the Local AI automatically* off, so the next start does not download it again; `--remove-local-ai` does the
same and says so.

## Unsupported systems

The manifest has runtimes for Windows (x64, ARM64), Linux (x64, ARM64) and macOS (Apple Silicon, Intel). VANTA detects
the system from the Java runtime that runs the game (`os.name`, `os.arch`): an x64 Java on an Apple Silicon Mac installs
the Intel build, and a 32-bit Java or another architecture gets the honest state **Not available on this system** (the
launcher: *Local AI: not available for <os>/<arch>*). Nothing else in VANTA changes then; the assistant section shows
the state and the other six sections work as always. A client or launcher built from a repository whose manifest was
not resolved reports *The Local AI manifest of this build is incomplete* and installs nothing.

## Licences

- `llama-server` is part of [llama.cpp](https://github.com/ggml-org/llama.cpp) by ggml-org and contributors, released
  under the [MIT licence](https://github.com/ggml-org/llama.cpp/blob/master/LICENSE); the archives come unchanged from
  its GitHub release [`b11429`](https://github.com/ggml-org/llama.cpp/releases/tag/b11429).
- The model `Qwen3-1.7B-Q8_0.gguf` is published by Qwen in the repository
  [Qwen/Qwen3-1.7B-GGUF](https://huggingface.co/Qwen/Qwen3-1.7B-GGUF) under the
  [Apache License 2.0](https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/blob/main/LICENSE); it is downloaded unchanged.
- Neither is part of VANTA or published as a VANTA file; VANTA downloads them from their authors' pages on your request.
  The Local AI card, the launcher dialog and `LOCAL-AI.txt` in the full release zip show the same licence lines, taken
  from the manifest.

## Troubleshooting

[Troubleshooting → Local AI problems](troubleshooting.md#local-ai-problems) covers a download that fails, a checksum
mismatch, an unsupported system, a server that does not start, slow answers and where the log file is.
