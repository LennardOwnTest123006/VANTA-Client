/**
 * Test entry point for `node --test scripts/release/`.
 *
 * Node 22 treats a positional `--test` argument as a test file, not a directory, so this module is what
 * runs when the directory is given: it imports every `*.test.mjs` next to it and the node:test
 * registrations inside them report back to the runner. `node --test` with no arguments (default
 * patterns) and `node --test 'scripts/release/*.test.mjs'` run the same files one process each.
 * This file is ESM because scripts/package.json declares "type": "module".
 */
import { readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const dir = dirname(fileURLToPath(import.meta.url));
for (const file of readdirSync(dir).filter((f) => f.endsWith('.test.mjs')).sort()) {
  await import(pathToFileURL(join(dir, file)).href);
}
