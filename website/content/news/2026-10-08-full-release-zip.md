---
title: 'One zip with everything, and a new download page'
date: 2026-10-08
summary: VantaClient-1.3.0-Release.zip holds every published file of client 1.3.0 and launcher 1.3.0 in one archive, and the website, now 1.1.0, says which version is the latest, what is new in it and where every older version is. The client and the launcher stay at 1.3.0.
author: VANTA team
tags: [release, website]
---

Two additions since VANTA Client 1.3.0 and VANTA Launcher 1.3.0 came out on October 7: one download with everything
in it, and a Download page that says plainly which version is the latest and where the earlier ones are. Nothing in
the game or in the launcher changed; this is a website release.

## One zip with everything

`VantaClient-1.3.0-Release.zip` (316.4 MB) is attached to the GitHub Release
[`v1.3.0`](https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/v1.3.0). It holds every published file of both releases plus the Windows installer as `.msi` and `.exe`, the portable Windows app, the Linux app, the launcher
jars for Windows, Linux and Apple Silicon macOS, the client jar, the mods bundle with Fabric API and the Performance
pack, Fabric API on its own, the documentation, the changelog, both release notes, the licence and a `SHA256SUMS.txt`.
The bundle workflow built it from the files that were already published as `client-v1.3.0` and `launcher-v1.3.0`,
checked each one against its release manifest and hashed the zip again after uploading it. No client or launcher file was built anew; the workflow only copied the documentation, the changelog,
the release notes and the licence from the repository and wrote the zip's README.txt and SHA256SUMS.txt.

It is large because it holds every launcher build at once. If you need one file for your system, the launcher and
client cards on the [Download page](https://vanta-client.netlify.app/download) are still the shorter way.

## The Download page, website 1.1.0

The website is now version 1.1.0
([release notes](https://vanta-client.netlify.app/changelog#website-1.1.0)). The Download page starts with a _Latest
version_ block that names the newest published client and launcher with their release dates. A third card offers the
full release zip with its size, its SHA-256 and the list of every file inside. _What's new_ quotes the release notes
of the versions on offer. And at the end, _Older versions_ lists every earlier published release of the launcher and
the client, newest first, each with its release date, primary file, SHA-256, a direct download, the GitHub release
page and its release notes.

VANTA Client and VANTA Launcher stay at 1.3.0.

## How to verify the zip

Compare the SHA-256 of the downloaded zip with the one on the Download page or on the release page before you unpack
it. On Windows:

```
certutil -hashfile VantaClient-1.3.0-Release.zip SHA256
```

On macOS and Linux: `shasum -a 256 VantaClient-1.3.0-Release.zip`. Inside the unpacked folder,
`sha256sum -c SHA256SUMS.txt` (on macOS `shasum -a 256 -c SHA256SUMS.txt`) checks every other file, and `README.txt` says which file to take on which system.
