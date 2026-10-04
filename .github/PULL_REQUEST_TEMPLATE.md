## Summary

<!-- What does this change and why? Link the issue it closes (e.g. "Closes #12"). -->

## Component

- [ ] `core` (pure Java, unit tested)
- [ ] `client` (Fabric mod, Minecraft 1.21.11)
- [ ] `launcher` (JavaFX)
- [ ] `website`
- [ ] `docs` / `shared` / `scripts` / CI

## Checklist

- [ ] **No gameplay advantage**: this change adds no cheats, combat automation, packet manipulation, anti-cheat
      bypass, player tracking or any other unfair multiplayer advantage.
- [ ] Target versions are unchanged: Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, Java 21
      (or this PR is a dedicated version bump that updates `client/gradle.properties`, `VantaVersion.java`,
      the manifests and the docs together).
- [ ] Logic lives in `core` with unit tests; Minecraft-specific code in `client` stays thin.
- [ ] Every new user-facing string is in `en_us.json`; no placeholder text, no dead buttons.
- [ ] Nothing is sent anywhere without the user asking; no secrets, no analytics.
- [ ] Documentation updated where behaviour changed (`docs/`, `CHANGELOG.md`, `website/content/changelog/`).
- [ ] Local checks pass for the touched components (`./gradlew build`, `npm run lint && npm test && npm run build`,
      `node --test scripts/release/`, `node scripts/release/check-links.mjs docs website/content`).
- [ ] Commits follow the conventional format (`feat(client): ...`, `fix(launcher): ...`, `docs: ...`).

## Screenshots / previews

<!-- For UI changes: core previews (core/build/previews/*.png), launcher screenshots or website screenshots. -->

## Notes for reviewers

<!-- Anything uncertain, follow-ups, or things to test manually. -->
