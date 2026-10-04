# Test fixtures

Recorded response shapes used by the launcher unit tests. They follow the real schemas of the Mojang version
manifest / version JSON (1.21.x `arguments` with feature rules, LWJGL 3.3.3 natives as separate rule-guarded
library entries, `javaVersion` component `java-runtime-delta`), Fabric meta (loader list and launcher profile),
the Adoptium v3 assets API, the Microsoft / Xbox Live / XSTS / Minecraft services sign-in chain and the VANTA
release manifest schema.

All digests, tokens and codes in these files are synthetic test values. Tests that exercise downloads rewrite the
URLs and digests to match the bytes served by a local fake server (`FakeWorld`), so every download in the suite is
verified end to end without network access.
