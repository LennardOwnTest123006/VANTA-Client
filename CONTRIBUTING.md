# Contributing to VANTA Client

Thanks for your interest. VANTA is a legitimate Minecraft client: contributions that add cheats, combat
automation, packet manipulation, anti-cheat bypasses, player tracking or any unfair multiplayer advantage are
rejected without discussion.

## Ground rules

1. **Target versions are fixed per release.** Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11,
   Java 21. Version bumps are their own pull requests and must update `client/gradle.properties`,
   `core/src/main/java/dev/vanta/core/VantaVersion.java`, the release manifests and the docs together.
2. **Logic lives in `core/`, Minecraft glue lives in `client/`.** `core` must stay free of Minecraft classes so it
   can be unit tested anywhere. If you need something from the game, add a method to one of the bridge interfaces
   in `core` and implement it in `client`.
3. **No fake UI.** Every button does what it says. Unfinished features are left out or labelled "Coming soon".
4. **No secrets, no tracking.** Nothing is sent anywhere without the user asking for it. Statistics stay local.
5. **Original branding and assets only.** Do not add Minecraft textures or third-party assets without a license
   that permits redistribution; record it in `assets/` next to the file.

## Workflow

- Fork, create a branch (`feat/...`, `fix/...`, `docs/...`), open a pull request against the repository's default
  branch.
- Use conventional commit messages: `feat(client): ...`, `fix(launcher): ...`, `docs: ...`, `ci: ...`.
- CI must be green: `core` tests, `client` build (compiles against real Minecraft 1.21.11 in CI), `launcher`
  tests, `website` build + unit + end-to-end tests.
- Run the relevant local checks before pushing (see [BUILDING.md](BUILDING.md)).

## Code style

- Java: 4 spaces, `final` where sensible, records for immutable data, Javadoc on public API, no wildcard imports.
- TypeScript/React: strict mode, functional components, Tailwind utility classes, no inline styles except
  for data-driven values, accessible markup (labels, roles, focus states).
- JSON config files are pretty printed and carry a `schemaVersion`.

## Reporting bugs

Open an issue with: VANTA version, launcher version, Minecraft version (must be 1.21.11), Java version, OS, the
`logs/latest.log` of the game and the launcher log. Remove any personal data first.
