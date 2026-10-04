#!/usr/bin/env node
/**
 * Bumps a product version everywhere it is pinned in the repository.
 *
 *   node scripts/release/bump-version.mjs --product client   --to 1.0.1 [--dry-run]
 *   node scripts/release/bump-version.mjs --product launcher --to 1.1.0
 *   node scripts/release/bump-version.mjs --product website  --to 1.0.1
 *
 * client   -> client/gradle.properties (mod_version), core/.../VantaVersion.java (CLIENT),
 *             new shared/releases/client-<to>.json (copied from the newest client manifest, unpublished)
 * launcher -> launcher/gradle.properties (launcher_version), new shared/releases/launcher-<to>.json
 * website  -> website/package.json and website/package-lock.json (version fields)
 *
 * The new manifest has empty downloadUrl/sha256 and size 0: the release workflow fills them. The script
 * never touches CHANGELOG.md or website/content/changelog/ — release notes are written by a person; it
 * prints a checklist of what still has to be done.
 */
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from './lib/args.mjs';
import { REPO_ROOT, compareSemVer, isSemVer, readJson, todayUtc, writeJsonAtomic, writeTextAtomic } from './lib/repo.mjs';

const USAGE = `Usage: node scripts/release/bump-version.mjs --product <client|launcher|website> --to <semver> [--date YYYY-MM-DD] [--dry-run] [--root <repo root>]`;

export const PRODUCTS = Object.freeze(['client', 'launcher', 'website']);

/**
 * @typedef {{ path: string, kind: 'edit'|'create', before?: string, after: string }} Change
 */

/** Replaces exactly one match of `pattern` in `text`, throwing when the pattern is absent or ambiguous. */
export function replaceOnce(text, pattern, replacement, description) {
  const matches = text.match(new RegExp(pattern.source, pattern.flags.includes('g') ? pattern.flags : `${pattern.flags}g`));
  if (!matches || matches.length === 0) throw new Error(`could not find ${description}`);
  if (matches.length > 1) throw new Error(`found ${matches.length} occurrences of ${description}, expected one`);
  return text.replace(pattern, replacement);
}

/** Finds the newest manifest of a product in `releasesDir` (by SemVer), or null. */
export function newestManifest(releasesDir, product) {
  if (!existsSync(releasesDir)) return null;
  const candidates = readdirSync(releasesDir)
    .filter((f) => f.startsWith(`${product}-`) && f.endsWith('.json'))
    .map((f) => ({ file: join(releasesDir, f), version: f.slice(product.length + 1, -'.json'.length) }))
    .filter((c) => isSemVer(c.version))
    .sort((a, b) => compareSemVer(b.version, a.version));
  return candidates[0] ?? null;
}

/**
 * Derives an unpublished manifest for `to` from `template` (file names get the new version, URLs/hashes reset).
 */
export function nextManifest(template, to, date) {
  const from = template.version;
  const renamed = (name) => name.split(from).join(to);
  return {
    ...template,
    version: to,
    releaseDate: date,
    files: (template.files ?? []).map((f) => ({ name: renamed(f.name), downloadUrl: '', size: 0, sha256: '' })),
    changelog: `website/content/changelog/${template.product}-${to}.md`,
  };
}

/**
 * Computes every change for a bump without writing anything.
 * @param {{ root: string, product: string, to: string, date?: string }} params
 * @returns {Change[]}
 */
export function planBump({ root, product, to, date = todayUtc() }) {
  if (!PRODUCTS.includes(product)) throw new Error(`--product must be one of ${PRODUCTS.join(', ')}`);
  if (!isSemVer(to)) throw new Error(`--to '${to}' is not a SemVer version`);
  const changes = [];
  const edit = (relPath, mutate) => {
    const path = resolve(root, relPath);
    if (!existsSync(path)) throw new Error(`${relPath} not found`);
    const before = readFileSync(path, 'utf8');
    const after = mutate(before);
    if (before !== after) changes.push({ path, kind: 'edit', before, after });
  };
  const releasesDir = resolve(root, 'shared', 'releases');

  if (product === 'client') {
    edit('client/gradle.properties', (text) =>
      replaceOnce(text, /^mod_version=.*$/m, `mod_version=${to}`, 'mod_version in client/gradle.properties'));
    edit('core/src/main/java/dev/vanta/core/VantaVersion.java', (text) =>
      replaceOnce(text, /(public static final String CLIENT = ")[^"]*(";)/, `$1${to}$2`, 'CLIENT constant in VantaVersion.java'));
  } else if (product === 'launcher') {
    edit('launcher/gradle.properties', (text) =>
      replaceOnce(text, /^launcher_version=.*$/m, `launcher_version=${to}`, 'launcher_version in launcher/gradle.properties'));
  } else {
    edit('website/package.json', (text) => {
      const pkg = JSON.parse(text);
      pkg.version = to;
      return `${JSON.stringify(pkg, null, 2)}\n`;
    });
    const lock = resolve(root, 'website', 'package-lock.json');
    if (existsSync(lock)) {
      edit('website/package-lock.json', (text) => {
        const data = JSON.parse(text);
        data.version = to;
        if (data.packages && data.packages['']) data.packages[''].version = to;
        return `${JSON.stringify(data, null, 2)}\n`;
      });
    }
  }

  if (product !== 'website') {
    const newest = newestManifest(releasesDir, product);
    if (!newest) throw new Error(`no existing ${product} manifest in shared/releases to derive the new one from`);
    if (compareSemVer(to, newest.version) <= 0) {
      throw new Error(`--to ${to} is not newer than the newest ${product} manifest ${newest.version}`);
    }
    const target = join(releasesDir, `${product}-${to}.json`);
    if (existsSync(target)) throw new Error(`${relative(root, target)} already exists`);
    const manifest = nextManifest(readJson(newest.file), to, date);
    changes.push({ path: target, kind: 'create', after: `${JSON.stringify(manifest, null, 2)}\n` });
  }
  return changes;
}

/** Writes planned changes atomically. */
export function applyChanges(changes) {
  for (const change of changes) {
    if (change.path.endsWith('.json')) writeJsonAtomic(change.path, JSON.parse(change.after));
    else writeTextAtomic(change.path, change.after);
  }
}

/** Human checklist printed after a bump. */
export function checklist(product, to) {
  const items = [
    `Write website/content/changelog/${product}-${to}.md (front matter: product, version, date, title${product === 'website' ? '' : ', minecraftVersion'}) and update CHANGELOG.md.`,
  ];
  if (product === 'client') items.push('Confirm docs/ pages that mention the client version are still accurate.');
  if (product !== 'website') {
    items.push(`Commit, then tag: git tag ${product}-v${to} && git push origin ${product}-v${to} (the release workflow builds and publishes).`);
  } else {
    items.push('Commit and push: Netlify deploys the website from the default branch.');
  }
  return items;
}

/** CLI entry point. */
export function main(argv, log = console.log, logError = console.error) {
  const parsed = parseArgs(argv, { values: ['product', 'to', 'date', 'root'], flags: ['dry-run', 'help'], aliases: { h: 'help' } });
  if (parsed.flags.has('help')) {
    log(USAGE);
    return 0;
  }
  for (const required of ['product', 'to']) if (!(required in parsed.options)) parsed.errors.push(`--${required} is required`);
  if (parsed.options.date !== undefined && !/^\d{4}-\d{2}-\d{2}$/.test(parsed.options.date)) parsed.errors.push('--date must be YYYY-MM-DD');
  if (parsed.positional.length > 0) parsed.errors.push(`unexpected argument '${parsed.positional[0]}'`);
  if (parsed.errors.length > 0) {
    for (const error of parsed.errors) logError(`error: ${error}`);
    logError(USAGE);
    return 2;
  }
  const root = parsed.options.root ? resolve(parsed.options.root) : REPO_ROOT;
  try {
    const changes = planBump({ root, product: parsed.options.product, to: parsed.options.to, date: parsed.options.date });
    const dryRun = parsed.flags.has('dry-run');
    if (!dryRun) applyChanges(changes);
    for (const change of changes) log(`${dryRun ? 'would ' : ''}${change.kind === 'create' ? 'create' : 'edit'}  ${relative(root, change.path)}`);
    log('');
    log('Next steps:');
    for (const item of checklist(parsed.options.product, parsed.options.to)) log(`  - ${item}`);
    return 0;
  } catch (error) {
    logError(`error: ${error.message}`);
    return 1;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(main(process.argv.slice(2)));
}
