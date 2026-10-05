# Test fixtures

Recorded response shapes used by the launcher unit tests. They follow the real schemas of the Mojang version
manifest / version JSON (1.21.x `arguments` with feature rules, LWJGL 3.3.3 natives as separate rule-guarded
library entries, `javaVersion` component `java-runtime-delta`), Fabric meta (loader list and launcher profile),
the Adoptium v3 assets API, the Microsoft / Xbox Live / XSTS / Minecraft services sign-in chain and the VANTA
release manifest schema.

All digests, tokens and codes in these files are synthetic test values. Tests that exercise downloads rewrite the
URLs and digests to match the bytes served by a local fake server (`FakeWorld`), so every download in the suite is
verified end to end without network access.

`modrinth/` holds responses of the Modrinth API v2 (`api.modrinth.com/v2`), recorded on 2026-10-05: the version lists
of the performance pack projects for Minecraft 1.21.11 and Fabric (`/project/<slug>/version`), the projects
(`/projects?ids=...`) and a search page (`/search`). They were trimmed to the fields the launcher reads (no
changelogs, fewer versions) but keep the real ids, version numbers, file names, sizes and hashes. `FakeWorld` serves
them from a local server and, like the other fixtures, rewrites every file URL, size and SHA-512 to the synthetic jar
it serves instead of the real file.
