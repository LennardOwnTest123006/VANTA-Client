# Security policy

VANTA Client is a legitimate, client-side Minecraft client. We take the security of the people who install it
seriously: the launcher downloads and runs software, stores Microsoft account tokens and talks to Mojang,
Microsoft and Fabric services, so bugs there matter.

## Reporting a vulnerability

Please **do not open a public issue** for a security problem.

1. Use GitHub's private vulnerability reporting: open the repository's **Security** tab and choose
   **Report a vulnerability**. Only the maintainers can read these reports.
2. If private reporting is unavailable for the repository, open a regular issue that says only "security report,
   please contact me" without any details, and a maintainer will arrange a private channel.
3. Additional contact channels (e-mail, Discord) are configured by the project maintainers and, when available,
   are listed on the Support page of the website.

Include what you can: affected product and version (client, launcher or website), steps to reproduce, impact, and
any proof of concept. Encrypting the report is welcome but not required.

What to expect: we aim to acknowledge reports within 7 days, keep you informed while we work on a fix, publish the
fix as a regular release with a changelog entry and credit you if you wish. **There is no bug bounty**; this is a
volunteer open-source project and we cannot promise payments or deadlines.

## Scope

In scope:

- **VANTA Launcher** — download verification (SHA-1/SHA-256), archive extraction, Microsoft/Xbox/Minecraft
  authentication flow, storage of account tokens (`accounts.dat`, DPAPI / AES-GCM key file), log redaction,
  construction of the game command line, update and rollback logic.
- **VANTA Client** (Fabric mod) — configuration parsing (`config/vanta/*.json`), profile import, cosmetic pack
  loading, anything that reads files or user input.
- **Website** — content rendering (markdown), security headers, build and deployment configuration.
- **Release pipeline** — `scripts/release/*`, `.github/workflows/*`, manifests and checksums.

Out of scope:

- Minecraft, Mojang/Microsoft services, Fabric Loader/API and other third-party mods (report to their maintainers).
- Reports that require a compromised machine or physical access.
- Server-side anti-cheat decisions about the client (VANTA contains no cheats; whether a server allows client mods
  is the server's policy).
- Automated scanner output without a demonstrated impact.

## Supported versions

Only the latest released version of each product receives fixes. Minecraft support is pinned: VANTA Client 1.x
targets Minecraft Java Edition 1.21.11 exclusively.

| Product | Supported |
| --- | --- |
| VANTA Client (latest 1.x release) | yes |
| VANTA Launcher (latest 1.x release) | yes |
| Website | current deployment |
| Anything older | no — please update |

## Design notes for reviewers

- Every file the launcher downloads is verified against a checksum from Mojang/Fabric metadata or from our release
  manifest before it is used; a mismatch deletes the file. The launcher never executes downloaded files except the
  verified Java runtime it installs.
- Archive extraction rejects path traversal. Writes are atomic (temp file + rename).
- Account tokens are encrypted at rest; passwords are never seen (OAuth device code flow). Tokens are redacted from
  all logs.
- Nothing is sent anywhere by the client; statistics are local files. The website has no analytics or cookies.
- The Microsoft client id is configuration, never a constant in the repository.
