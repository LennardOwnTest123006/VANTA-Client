#!/usr/bin/env node
/**
 * Enforces the JavaScript size budget of the production build.
 *
 * Reads `dist/.vite/manifest.json`, follows the entry chunk's static imports (dynamic imports are
 * route chunks and do not count towards the initial load) and sums the gzip size of every file on
 * that critical path. Fails the build when the total exceeds the budget. Also prints every chunk so
 * regressions are visible in the build log.
 *
 * Budget: 180 kB gzip for the initial JavaScript, as set in the project brief.
 */
import { readFileSync, statSync } from 'node:fs';
import { gzipSync } from 'node:zlib';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const BUDGET_GZIP_BYTES = 180 * 1024;

const here = dirname(fileURLToPath(import.meta.url));
const dist = resolve(here, '../dist');
const manifestPath = resolve(dist, '.vite/manifest.json');

/** @type {Record<string, {file: string, isEntry?: boolean, imports?: string[], dynamicImports?: string[], css?: string[]}>} */
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));

const gzipSize = (relative) => gzipSync(readFileSync(resolve(dist, relative))).length;
const rawSize = (relative) => statSync(resolve(dist, relative)).size;
const kb = (bytes) => `${(bytes / 1024).toFixed(1)} kB`;

const entries = Object.entries(manifest).filter(([, chunk]) => chunk.isEntry);
if (entries.length === 0) {
  console.error('check-bundle-size: no entry chunk found in manifest');
  process.exit(1);
}

/** Collects the entry chunk and all statically imported chunks (transitively). */
const initial = new Set();
const visit = (key) => {
  if (initial.has(key)) return;
  const chunk = manifest[key];
  if (!chunk) return;
  initial.add(key);
  for (const dep of chunk.imports ?? []) visit(dep);
};
for (const [key] of entries) visit(key);

let totalJsGzip = 0;
let totalCssGzip = 0;
const rows = [];
for (const key of initial) {
  const chunk = manifest[key];
  const gz = gzipSize(chunk.file);
  totalJsGzip += gz;
  rows.push({ kind: 'initial js', file: chunk.file, raw: rawSize(chunk.file), gzip: gz });
  for (const css of chunk.css ?? []) {
    const cssGz = gzipSize(css);
    totalCssGzip += cssGz;
    rows.push({ kind: 'initial css', file: css, raw: rawSize(css), gzip: cssGz });
  }
}
for (const [key, chunk] of Object.entries(manifest)) {
  if (initial.has(key) || !chunk.file.endsWith('.js')) continue;
  rows.push({ kind: 'lazy js', file: chunk.file, raw: rawSize(chunk.file), gzip: gzipSize(chunk.file) });
}

const pad = (s, n) => String(s).padEnd(n);
process.stdout.write('\nBundle report (gzip):\n');
for (const row of rows) {
  process.stdout.write(`  ${pad(row.kind, 12)} ${pad(row.file, 48)} ${pad(kb(row.raw), 10)} → ${kb(row.gzip)}\n`);
}
process.stdout.write(
  `\n  initial JS: ${kb(totalJsGzip)} gzip (budget ${kb(BUDGET_GZIP_BYTES)}) · initial CSS: ${kb(totalCssGzip)} gzip\n\n`,
);

if (totalJsGzip > BUDGET_GZIP_BYTES) {
  console.error(
    `check-bundle-size: initial JavaScript ${kb(totalJsGzip)} exceeds the ${kb(BUDGET_GZIP_BYTES)} budget`,
  );
  process.exit(1);
}
