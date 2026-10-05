#!/usr/bin/env node
/**
 * Builds or updates a release manifest from a real artifact.
 *
 *   node scripts/release/build-manifest.mjs --product client --version 1.0.0 \
 *        --file dist/vanta-client-1.0.0.jar \
 *        --url https://github.com/OWNER/REPO/releases/download/client-v1.0.0/vanta-client-1.0.0.jar \
 *        [--date 2026-10-04] [--channel stable] [--changelog website/content/changelog/client-1.0.0.md] \
 *        [--notes "text"] [--out shared/releases] [--sums dist/SHA256SUMS.txt] [--no-latest] [--dry-run]
 *
 * What it does:
 *   1. Computes the file's size and SHA-256 by streaming it (the only source of truth for those values).
 *   2. Loads shared/releases/<product>-<version>.json when it exists, otherwise starts from the pinned
 *      toolchain (client/gradle.properties) so every field is filled.
 *   3. Upserts the file entry (matched by file name), sets releaseDate/channel/changelog/notes.
 *   4. Validates the result against shared/schemas/release-manifest.schema.json and writes it atomically,
 *      plus a copy at shared/releases/latest/<product>-latest.json (what the launcher's update check reads).
 *      The copy is only written for stable manifests: latest/ follows stable releases only, because the launcher
 *      offers whatever version it finds there as an update. A beta manifest (channel "beta", a GitHub pre-release)
 *      never touches latest/; --no-latest skips the copy for stable manifests too.
 *   5. Appends/updates `<sha256>  <name>` in SHA256SUMS.txt (sha256sum -c compatible).
 *
 * Run it once per artifact; the launcher release calls it for the .msi, .exe, jar and tar.gz in turn.
 */
import { existsSync, readFileSync } from 'node:fs';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from './lib/args.mjs';
import { hashFile } from './lib/hash.mjs';
import { REPO_ROOT, isSemVer, readJson, readToolchain, todayUtc, writeJsonAtomic, writeTextAtomic } from './lib/repo.mjs';
import { validate } from './validate-json.mjs';

const USAGE = `Usage: node scripts/release/build-manifest.mjs --product <client|launcher> --version <semver> --file <path> --url <https url>
         [--name <file name>] [--date YYYY-MM-DD] [--channel stable|beta] [--changelog <repo path>] [--notes <text>]
         [--out <dir>] [--sums <path>] [--no-latest] [--dry-run] [--root <repo root>]`;

export const PRODUCTS = Object.freeze(['client', 'launcher']);

/** Reads the manifest schema from the repository. */
export function loadSchema(root = REPO_ROOT) {
  return readJson(resolve(root, 'shared', 'schemas', 'release-manifest.schema.json'));
}

/**
 * Returns the manifest for product/version: the existing file when present, else a fresh template.
 * @param {{ root: string, outDir: string, product: string, version: string }} params
 */
export function loadOrCreateManifest({ root, outDir, product, version }) {
  const path = join(outDir, `${product}-${version}.json`);
  if (existsSync(path)) {
    const manifest = readJson(path);
    if (manifest.product !== product) throw new Error(`${path} describes product '${manifest.product}', expected '${product}'`);
    if (manifest.version !== version) throw new Error(`${path} has version '${manifest.version}', expected '${version}'`);
    return { path, manifest, created: false };
  }
  const toolchain = readToolchain(root);
  return {
    path,
    created: true,
    manifest: {
      schemaVersion: 1,
      product,
      version,
      minecraftVersion: toolchain.minecraftVersion,
      fabricVersion: toolchain.fabricVersion,
      fabricApiVersion: toolchain.fabricApiVersion,
      javaVersion: toolchain.javaVersion,
      releaseDate: todayUtc(),
      channel: 'stable',
      files: [],
      changelog: `website/content/changelog/${product}-${version}.md`,
    },
  };
}

/**
 * Inserts or replaces a file entry (matched by name) and returns the new files array.
 * @param {Array<{name: string}>} files
 * @param {{ name: string, downloadUrl: string, size: number, sha256: string }} entry
 */
export function upsertFileEntry(files, entry) {
  const index = files.findIndex((f) => f.name === entry.name);
  const next = files.slice();
  if (index >= 0) next[index] = { ...files[index], ...entry };
  else next.push(entry);
  return next;
}

/**
 * Updates the `<sha256>  <name>` line for `name` in a SHA256SUMS document (sha256sum format).
 * @param {string} existing current file content (may be empty)
 */
export function updateSums(existing, name, sha256) {
  const lines = existing.split(/\r?\n/).filter((line) => line.trim() !== '');
  const line = `${sha256}  ${name}`;
  const index = lines.findIndex((l) => l.split(/\s+/).slice(1).join(' ').replace(/^\*/, '') === name);
  if (index >= 0) lines[index] = line;
  else lines.push(line);
  return `${lines.join('\n')}\n`;
}

/**
 * Builds (or updates) a manifest from an artifact. Pure except for reading the artifact and writing outputs.
 *
 * @param {object} params
 * @param {string} params.product      client | launcher
 * @param {string} params.version      SemVer
 * @param {string} params.file         artifact path
 * @param {string} params.url          https download URL
 * @param {string} [params.name]       file name in the manifest (default: basename of file)
 * @param {string} [params.date]       YYYY-MM-DD
 * @param {string} [params.channel]    stable | beta
 * @param {string} [params.changelog]  repository-relative changelog path
 * @param {string} [params.notes]
 * @param {string} [params.outDir]     manifest directory (default <root>/shared/releases)
 * @param {string} [params.sumsPath]   SHA256SUMS.txt path (default next to the artifact)
 * @param {boolean} [params.latest]    also write latest/<product>-latest.json (default true; never for a beta manifest)
 * @param {boolean} [params.dryRun]    do not write anything
 * @param {string} [params.root]       repository root
 * @returns {Promise<{ manifest: object, manifestPath: string, latestPath: string|null, latestSkipped: string|null, sumsPath: string, entry: object, created: boolean }>}
 *          latestSkipped says why latest/ was not written (null when it was)
 */
export async function buildManifest(params) {
  const root = params.root ?? REPO_ROOT;
  const { product, version, file, url } = params;
  if (!PRODUCTS.includes(product)) throw new Error(`--product must be one of ${PRODUCTS.join(', ')}`);
  if (!isSemVer(version)) throw new Error(`--version '${version}' is not a SemVer version`);
  if (!file || !existsSync(file)) throw new Error(`--file '${file}' does not exist`);
  if (typeof url !== 'string' || !/^https:\/\/\S+$/.test(url)) throw new Error('--url must be an absolute https URL');
  if (params.date !== undefined && !/^\d{4}-\d{2}-\d{2}$/.test(params.date)) throw new Error('--date must be YYYY-MM-DD');
  if (params.channel !== undefined && !['stable', 'beta'].includes(params.channel)) throw new Error('--channel must be stable or beta');

  const outDir = params.outDir ?? resolve(root, 'shared', 'releases');
  const name = params.name ?? basename(file);
  const { size, sha256 } = await hashFile(file);
  if (size === 0) throw new Error(`refusing to publish an empty file: ${file}`);
  const entry = { name, downloadUrl: url, size, sha256 };

  const loaded = loadOrCreateManifest({ root, outDir, product, version });
  const manifest = { ...loaded.manifest };
  manifest.files = upsertFileEntry(manifest.files ?? [], entry);
  if (params.date) manifest.releaseDate = params.date;
  if (params.channel) manifest.channel = params.channel;
  if (params.changelog) manifest.changelog = params.changelog;
  if (params.notes !== undefined) manifest.notes = params.notes;
  if (manifest.notes === '') delete manifest.notes;

  const result = validate(loadSchema(root), manifest, { baseDir: resolve(root, 'shared', 'schemas') });
  if (!result.valid) {
    const details = result.errors.map((e) => `  ${e.path}: ${e.message}`).join('\n');
    throw new Error(`resulting manifest is invalid:\n${details}`);
  }

  let latestSkipped = null;
  if (params.latest === false) latestSkipped = '--no-latest';
  else if (manifest.channel !== 'stable') latestSkipped = `channel is '${manifest.channel}'; latest/ only follows stable releases`;
  const latestPath = latestSkipped ? null : join(outDir, 'latest', `${product}-latest.json`);
  const sumsPath = params.sumsPath ?? join(dirname(resolve(file)), 'SHA256SUMS.txt');
  if (!params.dryRun) {
    writeJsonAtomic(loaded.path, manifest);
    if (latestPath) writeJsonAtomic(latestPath, manifest);
    const existing = existsSync(sumsPath) ? readFileSync(sumsPath, 'utf8') : '';
    writeTextAtomic(sumsPath, updateSums(existing, name, sha256));
  }
  return { manifest, manifestPath: loaded.path, latestPath, latestSkipped, sumsPath, entry, created: loaded.created };
}

/** CLI entry point. */
export async function main(argv, log = console.log, logError = console.error) {
  const parsed = parseArgs(argv, {
    values: ['product', 'version', 'file', 'url', 'name', 'date', 'channel', 'changelog', 'notes', 'out', 'sums', 'root'],
    flags: ['no-latest', 'dry-run', 'help'],
    aliases: { h: 'help' },
  });
  if (parsed.flags.has('help')) {
    log(USAGE);
    return 0;
  }
  for (const required of ['product', 'version', 'file', 'url']) {
    if (!(required in parsed.options)) parsed.errors.push(`--${required} is required`);
  }
  if (parsed.positional.length > 0) parsed.errors.push(`unexpected argument '${parsed.positional[0]}'`);
  if (parsed.errors.length > 0) {
    for (const error of parsed.errors) logError(`error: ${error}`);
    logError(USAGE);
    return 2;
  }
  const o = parsed.options;
  try {
    const result = await buildManifest({
      product: o.product,
      version: o.version,
      file: o.file,
      url: o.url,
      name: o.name,
      date: o.date,
      channel: o.channel,
      changelog: o.changelog,
      notes: o.notes,
      outDir: o.out ? resolve(o.out) : undefined,
      sumsPath: o.sums ? resolve(o.sums) : undefined,
      latest: !parsed.flags.has('no-latest'),
      dryRun: parsed.flags.has('dry-run'),
      root: o.root ? resolve(o.root) : undefined,
    });
    const verb = parsed.flags.has('dry-run') ? 'would write' : 'wrote';
    log(`${result.entry.name}: ${result.entry.size} bytes, sha256 ${result.entry.sha256}`);
    log(`${verb} ${result.manifestPath}${result.created ? ' (new)' : ''}`);
    if (result.latestPath) log(`${verb} ${result.latestPath}`);
    else log(`not writing latest/${result.manifest.product}-latest.json (${result.latestSkipped})`);
    log(`${verb} ${result.sumsPath}`);
    if (parsed.flags.has('dry-run')) log(JSON.stringify(result.manifest, null, 2));
    return 0;
  } catch (error) {
    logError(`error: ${error.message}`);
    return 1;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(await main(process.argv.slice(2)));
}
