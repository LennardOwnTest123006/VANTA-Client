# Local AI fixtures

Test data shared by the three programs that write or read a Local AI `installed.json`: the client (core,
`dev.vanta.core.ai.LocalAiInstaller` and `LocalAiInstalled`), the VANTA Launcher (`dev.vanta.launcher.core.ai.LocalAiService`
and `LocalAiInstalled`) and the release tooling (`scripts/release/local-ai.mjs prepare`, `installedRecord`). All three must
produce the same file for the same inputs, because the client reads a launcher-managed or CI-prepared directory through
the note `config/vanta/local-ai.json` and runs `LocalAiInstaller.quickCheck()` / `verify()` on it, which parse
`installed.json` with core's `LocalAiInstalled.fromJson`.

Nothing in here is real: the host is `example.invalid`, the archives are a few bytes of text and the "model" is 4 KB.
Every size and SHA-256 is derived from the bytes described below, so the files can be regenerated and checked.

## manifest.example.json

A complete manifest in the shape of `shared/local-ai/local-ai.json` (it passes `shared/schemas/local-ai.schema.json`):

- `resolvedAt` `2026-10-08T12:00:00Z`, runtime `llama.cpp` tag `b11429`, component `llama-server`, MIT; the six platform
  keys in the order of the real manifest; file names and `serverPath` values as the real release (`llama-server.exe` in
  the zips, `llama-b11429/llama-server` in the tar.gz archives).
- Archive `size` and `sha256` for platform entry `<file>`: the ASCII bytes `VANTA Local AI fixture archive <file>` followed
  by one `\n` (64 to 68 bytes).
- Model `Qwen3-1.7B` `Q8_0`, file `Qwen3-1.7B-Q8_0.gguf`, Apache-2.0, `contextSize` 4096: 4096 bytes where byte `i` is
  `(i * 7 + 3) & 0xff`, then the first four bytes replaced by the ASCII text `GGUF`
  (`sha256 371866705d66b547691142fe87e00e55ea74b961365b4193a6fce4b302bed49a`).
- Requirements 2200 MB disk, 3072 MB RAM.

## installed.example.json

What `installed.json` must look like for an install of that manifest on `linux-x64`, generated with core's
`LocalAiInstalled.toJson()` (pretty printed) from these fixed inputs:

| input | value |
| --- | --- |
| platform | `linux-x64` |
| installedAt | `2026-10-08T12:00:00Z` |
| verifiedAt | `2026-10-08T12:05:00Z` |
| runtime | the manifest's runtime section with only the `linux-x64` entry (`Runtime.only(platform)`) |
| model | the manifest's model section, unchanged |
| server executable | `runtime/b11429/linux-x64/llama-b11429/llama-server`: the ASCII bytes `#!/bin/sh\necho fixture llama-server b11429\n` (43 bytes), modification time `1791460800000` ms (2026-10-08T12:00:00Z) |
| model file | `models/Qwen3-1.7B-Q8_0.gguf`: the 4096 model bytes above, modification time `1791460860000` ms (2026-10-08T12:01:00Z) |

Shape (keys in this order, `schemaVersion` 1, instants as ISO-8601 UTC strings as `Instant.toString()` writes them, so with
fractional seconds when the clock has them; the fixture uses whole seconds; file times as epoch milliseconds):

```
{ schemaVersion, platform, installedAt, verifiedAt,
  runtime: { name, component, tag, license, sourceUrl, releaseUrl, platforms: { <platform>: { file, url, size, sha256, serverPath } } },
  model: { name, quantization, file, url, size, sha256, license, licenseUrl, sourceUrl, contextSize },
  files: { serverSize, serverMtime, modelSize, modelMtime } }
```

The recorded hashes are the manifest's (the archive's and the model's); the server executable itself has no hash in the
manifest, so every reader checks it by presence and size (plus modification time in the quick check) and re-hashes
only the model.

## Tests that pin the fixture

- core: `core/src/test/java/dev/vanta/core/ai/LocalAiInstalledFixtureTest.java` parses `installed.example.json` with
  `LocalAiInstalled.fromJson`, checks the round trip through `toJson`, rebuilds the record from `manifest.example.json`
  and the inputs above, and runs `quickCheck()` / `verify()` on a directory laid out like a launcher install.
- launcher: `launcher/src/test/java/dev/vanta/launcher/core/ai/LocalAiInstalledTest.java` builds the record from the
  same inputs and compares its JSON with the fixture, and checks that a real install writes the same key set.
- scripts: `scripts/release/local-ai.test.mjs` ("installedRecord matches the shared fixture") builds the record with
  `installedRecord(...)` from the same inputs and compares it with the fixture.
- CI (`.github/workflows/ci.yml`, launcher integration job) compares the key set of the `installed.json` the launcher
  jar wrote with this fixture.
