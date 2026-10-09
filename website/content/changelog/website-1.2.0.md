---
product: website
version: 1.2.0
date: 2026-10-09
title: Website 1.2.0
minecraftVersion: 1.21.11
---

The website for VANTA 1.4.0: the Download page is split into the sections WINDOWS, CROSS-PLATFORM and COMPLETE RELEASE and carries a note on what the optional Local AI downloads, the Features page describes Vanta Nexus, the Local AI, the HUD Designer, the seven built-in profiles, the live performance values, waypoints and Vanta Lab, and every privacy statement names the hosts the client contacts. Nothing on the site is typed by hand where a manifest decides: versions, files, sizes and checksums come from the release manifests, the Local AI facts from `shared/local-ai/local-ai.json`.

## Added

- **Download page in three sections.** *WINDOWS*: the Launcher Setup card with the `.msi` from the newest published launcher manifest and the Windows files of that release (the `.exe` only where a release before 1.4.0 lists one, the portable app). *CROSS-PLATFORM*: the client jar for Windows, Linux and macOS with the mods bundle and the install options, next to a new card with the launcher jars for Windows x64, Linux x64 and Apple Silicon macOS and the Linux app. *COMPLETE RELEASE*: the full release zip from the newest published bundle manifest, and an honest sentence instead of a card while none is published. An in-page navigation in the hero jumps to the sections
- **Local AI note** on the Download page: what the first start of Vanta Nexus downloads when you install the Local AI (the llama.cpp `llama-server` archive for each system with its size, the Qwen3-1.7B Q8_0 model with its size in bytes), from which hosts (github.com, huggingface.co), the licences (MIT, Apache-2.0), the disk space and RAM it needs, that it is optional and asks first, where it is installed and that the assistant works offline afterwards. Every value is read from the Local AI manifest; an unresolved manifest shows no sizes instead of guessed ones
- A *Local AI (optional)* row in the system requirements with the disk space and RAM from the manifest
- **Features page**: new sections *Vanta Nexus* (the seven sections, what the assistant may change, Undo, the example prompts, what it never does), *Local AI* (strictly local; the two downloads, two install paths, verification, offline operation, requirements, supported systems, the hosts paragraph), *HUD Designer* (named layouts and the six presets with their widget sets), *Waypoints* and *Vanta Lab* (the five features with their exact behaviour); updated *Profiles* (the seven built-ins and exactly what each sets), *Performance Center* (the live values of the Nexus Performance section), *HUD widgets* (the frame time graph behind Vanta Lab), *Main menu*, *Settings* (the new categories) and *Keybind manager* (N). No screenshots of Nexus are shown because none exists yet; the site never fakes one
- Home page: *Vanta Nexus* and *Waypoints* cards (twelve modules), and the trust pillar *Local only* says that the assistant's prompts go to 127.0.0.1 only
- Support page: the topic *Vanta Nexus and Local AI* with guides to the Features and Download sections and the privacy page, and troubleshooting links for a checksum mismatch of a Local AI file, the log file and how to report a problem (nine topics)
- Release notes `client-1.4.0.md`, `launcher-1.4.0.md` and this entry; the news post "VANTA 1.4: Vanta Nexus and a strictly local AI"

## Improved

- Every statement about network use on the site now says the same thing: the client contacts Modrinth (Mods & Shaders) and, only when you install the Local AI, github.com and huggingface.co, which redirect the two downloads to their own file hosts (GitHub's release asset host objects.githubusercontent.com, Hugging Face's CDN hosts); nothing else, ever; the assistant's prompts go to 127.0.0.1 only; no cloud AI, no API key, no account, no telemetry (Features, About, home page, Download page, navigation descriptions)
- The launcher card's footnote names the `.exe` only when the offered release lists one, and the file labels, verification text and install options mention the Local AI where the launcher 1.4.0 behaviour matters
- The site's unit tests render the three sections and the Local AI note from fixture manifests and check the repository's Local AI manifest (hosts, licences, platform keys); the end-to-end tests check the split file lists against `shared/releases/` and the support topics count

## Notes

- Until the 1.4.0 release manifests are filled by the release workflow, the cards keep offering the newest published release of each product (1.3.0) and mention 1.4.0 as upcoming; the 1.4.0 notes carry a "Release pending" badge on the changelog page. Nothing on the Download page names 1.4.0 where a manifest decides
- The Local AI itself is never inside a release file or the full release zip; the note describes what the client or the launcher download later, with your consent
- VANTA Client 1.4.0 and VANTA Launcher 1.4.0 are released separately (releases `client-v1.4.0` and `launcher-v1.4.0`); this website version is deployed with them
