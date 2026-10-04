#!/usr/bin/env node
/**
 * Verifies that the files described by a release manifest really have the advertised size and SHA-256.
 *
 *   node scripts/release/verify-manifest.mjs shared/releases/client-1.0.0.json            # downloads each file
 *   node scripts/release/verify-manifest.mjs shared/releases/client-1.0.0.json --local dist  # reads dist/<name>
 *   options: --allow-unpublished (entries with an empty downloadUrl are skipped instead of failing)
 *            --timeout <ms> (per download, default 600000)
 *
 * Exit codes: 0 all files verified, 1 at least one mismatch/missing file, 2 usage or manifest error.
 * Downloads are streamed through SHA-256 and never written to disk.
 */
import { existsSync, readFileSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from './lib/args.mjs';
import { hashFile, hashWebStream } from './lib/hash.mjs';
import { REPO_ROOT, readJson } from './lib/repo.mjs';
import { validate } from './validate-json.mjs';

const USAGE = `Usage: node scripts/release/verify-manifest.mjs <manifest.json> [--local <dir>] [--allow-unpublished] [--timeout <ms>] [--root <repo root>]`;

/**
 * @typedef {{ name: string, status: 'ok'|'mismatch'|'missing'|'unpublished'|'error', expected?: {size: number, sha256: string}, actual?: {size: number, sha256: string}, message?: string }} FileReport
 */

/** Downloads a URL and returns its size and digest without storing it. */
export async function hashUrl(url, { timeoutMs = 600_000, fetchImpl = fetch } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetchImpl(url, { redirect: 'follow', signal: controller.signal, headers: { 'user-agent': 'VANTA-release-scripts' } });
    if (!response.ok) throw new Error(`HTTP ${response.status} ${response.statusText}`);
    if (!response.body) throw new Error('empty response body');
    return await hashWebStream(response.body);
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Verifies one file entry.
 * @param {{ name: string, downloadUrl: string, size: number, sha256: string }} file
 * @param {{ localDir?: string, timeoutMs?: number, fetchImpl?: typeof fetch }} options
 * @returns {Promise<FileReport>}
 */
export async function verifyFile(file, options = {}) {
  const expected = { size: file.size, sha256: file.sha256 };
  if (!file.downloadUrl && !options.localDir) {
    return { name: file.name, status: 'unpublished', expected, message: 'no downloadUrl (not published yet)' };
  }
  try {
    let actual;
    if (options.localDir) {
      const path = join(options.localDir, basename(file.name));
      if (!existsSync(path)) return { name: file.name, status: 'missing', expected, message: `${path} not found` };
      actual = await hashFile(path);
    } else {
      actual = await hashUrl(file.downloadUrl, { timeoutMs: options.timeoutMs, fetchImpl: options.fetchImpl });
    }
    if (!file.sha256 || file.size <= 0) {
      return { name: file.name, status: 'mismatch', expected, actual, message: 'manifest has no size/sha256 to compare against' };
    }
    const ok = actual.size === file.size && actual.sha256 === file.sha256.toLowerCase();
    return {
      name: file.name,
      status: ok ? 'ok' : 'mismatch',
      expected,
      actual,
      message: ok ? undefined : `expected ${file.size} bytes / ${file.sha256}, got ${actual.size} bytes / ${actual.sha256}`,
    };
  } catch (error) {
    return { name: file.name, status: 'error', expected, message: error.message };
  }
}

/**
 * Validates the manifest against the schema and verifies every file.
 * @param {object} manifest parsed manifest
 * @param {{ localDir?: string, allowUnpublished?: boolean, timeoutMs?: number, fetchImpl?: typeof fetch, root?: string }} options
 * @returns {Promise<{ ok: boolean, reports: FileReport[] }>}
 */
export async function verifyManifest(manifest, options = {}) {
  const root = options.root ?? REPO_ROOT;
  const schema = readJson(resolve(root, 'shared', 'schemas', 'release-manifest.schema.json'));
  const schemaResult = validate(schema, manifest, { baseDir: resolve(root, 'shared', 'schemas') });
  if (!schemaResult.valid) {
    const details = schemaResult.errors.map((e) => `${e.path}: ${e.message}`).join('; ');
    throw new Error(`manifest does not match the schema: ${details}`);
  }
  const reports = [];
  for (const file of manifest.files) {
    reports.push(await verifyFile(file, options));
  }
  const ok = reports.every((r) => r.status === 'ok' || (r.status === 'unpublished' && options.allowUnpublished === true));
  return { ok, reports };
}

/** CLI entry point. */
export async function main(argv, log = console.log, logError = console.error) {
  const parsed = parseArgs(argv, { values: ['local', 'timeout', 'root'], flags: ['allow-unpublished', 'help'], aliases: { h: 'help' } });
  if (parsed.flags.has('help')) {
    log(USAGE);
    return 0;
  }
  if (parsed.positional.length !== 1) parsed.errors.push('expected exactly one manifest path');
  if (parsed.options.timeout !== undefined && !/^\d+$/.test(parsed.options.timeout)) parsed.errors.push('--timeout must be a number of milliseconds');
  if (parsed.errors.length > 0) {
    for (const error of parsed.errors) logError(`error: ${error}`);
    logError(USAGE);
    return 2;
  }
  const manifestPath = parsed.positional[0];
  let manifest;
  try {
    manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  } catch (error) {
    logError(`error: cannot read ${manifestPath}: ${error.message}`);
    return 2;
  }
  try {
    const { ok, reports } = await verifyManifest(manifest, {
      localDir: parsed.options.local ? resolve(parsed.options.local) : undefined,
      allowUnpublished: parsed.flags.has('allow-unpublished'),
      timeoutMs: parsed.options.timeout ? Number(parsed.options.timeout) : undefined,
      root: parsed.options.root ? resolve(parsed.options.root) : undefined,
    });
    log(`${manifest.product} ${manifest.version} (${basename(manifestPath)})`);
    for (const report of reports) {
      const label = report.status.toUpperCase().padEnd(11);
      const detail = report.status === 'ok' ? `${report.actual.size} bytes, sha256 ${report.actual.sha256}` : report.message;
      (report.status === 'ok' || report.status === 'unpublished' ? log : logError)(`  ${label} ${report.name}  ${detail}`);
    }
    log(ok ? 'All files verified.' : 'Verification FAILED.');
    return ok ? 0 : 1;
  } catch (error) {
    logError(`error: ${error.message}`);
    return 2;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(await main(process.argv.slice(2)));
}
