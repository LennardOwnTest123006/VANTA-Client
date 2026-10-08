#!/usr/bin/env node
/**
 * The Local AI manifest tool: resolves, verifies and installs what Vanta Nexus downloads on a player's PC (the
 * llama.cpp `llama-server` runtime from GitHub Releases and the Qwen3 GGUF model from Hugging Face). The manifest is
 * shared/local-ai/local-ai.json (schema shared/schemas/local-ai.schema.json); the client and the launcher embed a
 * copy at build time and refuse to download anything the manifest does not name with size and SHA-256.
 *
 *   node scripts/release/local-ai.mjs resolve [--manifest <template>] [--out <path>] [--work <dir>]
 *        [--skip-model-download]
 *        reads the template (sizes 0, model sha256 "", serverPath "..." where unknown), asks the GitHub Releases API
 *        for the assets of the release tag (sizes, URLs, digests), downloads every one of the six archives, checks
 *        its SHA-256 against the template (the values GitHub shows on the release page), inspects it for
 *        llama-server / llama-server.exe and records serverPath relative to the extraction root; asks the Hugging
 *        Face API (tree/main, field lfs.oid) for the model's size and SHA-256 and, unless --skip-model-download,
 *        downloads the model once, streamed through SHA-256, to confirm them. Writes the completed manifest with
 *        resolvedAt (to --out, default: in place) after validating it against the schema. Runs in CI, where GitHub
 *        and Hugging Face are reachable (.github/workflows/local-ai-resolve.yml).
 *   node scripts/release/local-ai.mjs verify <manifest> [--full] [--work <dir>]
 *        schema check, then the GitHub API (asset present, same size and URL, digest when GitHub publishes one),
 *        every runtime archive downloaded and re-hashed with its serverPath present, the model checked through the
 *        Hugging Face API only (size and lfs.oid) unless --full downloads and hashes it as well.
 *        --skip-model-download names that default explicitly (it cannot be combined with --full).
 *   node scripts/release/local-ai.mjs prepare --dir <dir> [--platform <key>] [--manifest <path>] [--keep-downloads]
 *        downloads, verifies and extracts the runtime for the given (default: the current) platform and the model
 *        into <dir> with the layout the client and the launcher use: installed.json (the shape of
 *        dev.vanta.core.ai.LocalAiInstalled, see shared/local-ai/fixtures/), runtime/<tag>/<platform>/... (serverPath
 *        relative to it, executable bit set), models/<file>, downloads/<name>.part while downloading. Files already
 *        present and verified are not downloaded again; installed.json is rewritten on every run with the current
 *        modification times (so a run after a cache restore records the restored files). With --keep-downloads the
 *        verified runtime archive stays under downloads/ and the model is hard-linked (copied where links fail)
 *        there as well, so `serve` can offer both under their bare file names. CI uses it to build the cached install
 *        the game test and the launcher integration job start llama-server from (VANTA_LOCAL_AI_DIR).
 *   node scripts/release/local-ai.mjs mirror --dir <prepared dir> --base <http://127.0.0.1:PORT/> --out <path>
 *        [--platform <key>] [--manifest <path>]
 *        writes a copy of the manifest that is identical to the resolved one except that the archive URL of the
 *        prepared platform and the model URL point at <base><file> (other platforms unchanged, resolvedAt kept).
 *        Requires `prepare --keep-downloads` first: both files must be present under <dir>/downloads/ with the
 *        manifest sizes. The base may be https anywhere or plain http on the loopback address only. The launcher
 *        reads it through VANTA_LOCAL_AI_MANIFEST and still verifies every SHA-256; it is not schema-valid (http) and
 *        must never be committed.
 *   node scripts/release/local-ai.mjs serve --dir <dir> --port <n>
 *        a tiny static file server for one flat directory on 127.0.0.1: GET and HEAD of /<file name> with
 *        Content-Length, no directory listing, no subdirectories, no ranges; 404 for everything else, 405 for other
 *        methods. --port 0 picks a free port; the listening URL is printed as "serving <dir> on <url>". Runs until
 *        SIGINT or SIGTERM. Only ever meant for CI and local runs against the files `prepare --keep-downloads` kept.
 *   node scripts/release/local-ai.mjs status [--manifest <path>] [--dir <dir>] [--platform <key>]
 *        says whether the manifest is resolved (schema-valid) and, with --dir, whether that directory holds a
 *        complete install of it. Exit 0 when resolved (and complete), 1 otherwise; prints no error annotations, so
 *        ci.yml can use it as a guard before the first resolver run.
 *   common options: --root <repo root> (schema and default manifest path), --timeout <ms per request>,
 *                   --attempts <tries per request>, --quiet
 *
 * Exit codes: 0 ok, 1 a check, download or verification failed (or the manifest is not resolved), 2 usage error.
 *
 * Nothing here is typed by hand: sizes come from the APIs and the downloads, the model digest from the Hugging Face
 * LFS metadata and the downloaded bytes, serverPath from the archive contents. The SHA-256 of the six archives is the
 * only value a person copies into the template, from the release page, and the resolver refuses a download that
 * does not hash to it.
 */
import { createHash } from 'node:crypto';
import { once } from 'node:events';
import {
  chmodSync, copyFileSync, createReadStream, createWriteStream, existsSync, linkSync, mkdirSync, mkdtempSync, readdirSync, renameSync, rmSync,
  statSync, writeFileSync,
} from 'node:fs';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { basename, dirname, join, posix, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from './lib/args.mjs';
import { hashFile, isSha256Hex } from './lib/hash.mjs';
import { REPO_ROOT, readJson, writeJsonAtomic } from './lib/repo.mjs';
import { extractTarGz, listTarGz } from './lib/tar.mjs';
import { wrapText } from './lib/text.mjs';
import { openZip } from './lib/zip.mjs';
import { userAgent } from './performance-pack.mjs';
import { formatSize } from './release-assets.mjs';
import { validate } from './validate-json.mjs';

const USAGE = `Usage: node scripts/release/local-ai.mjs <command> [options]
  resolve  [--manifest <template>] [--out <path>] [--work <dir>] [--skip-model-download]
  verify   <manifest> [--full | --skip-model-download] [--work <dir>]
  prepare  --dir <dir> [--platform <key>] [--manifest <path>] [--keep-downloads]
  mirror   --dir <prepared dir> --base <http://127.0.0.1:PORT/> --out <path> [--platform <key>] [--manifest <path>]
  serve    --dir <dir> --port <n>
  status   [--manifest <path>] [--dir <dir>] [--platform <key>]
  options: --root <repository root> --timeout <ms per request> --attempts <tries per request> --quiet
  platform keys: ${'windows-x64 windows-arm64 linux-x64 linux-arm64 macos-arm64 macos-x64'}`;

/** Repository-relative path of the manifest. */
export const MANIFEST_PATH = 'shared/local-ai/local-ai.json';
/** Repository-relative path of its schema. */
export const SCHEMA_PATH = 'shared/schemas/local-ai.schema.json';
/** The six platform keys, in manifest order. */
export const PLATFORM_KEYS = Object.freeze(['windows-x64', 'windows-arm64', 'linux-x64', 'linux-arm64', 'macos-arm64', 'macos-x64']);
/** Marker of a value the resolver fills in (serverPath of the template). */
export const PLACEHOLDER = '...';
/** Suffix of a partial download. */
export const PART_SUFFIX = '.part';
/** Default per-request time limit for the API calls and the archives. */
export const DEFAULT_TIMEOUT_MS = 10 * 60_000;
/** Time limit for the model download (about 1.8 GB; a slow connection must not fail the run). */
export const MODEL_TIMEOUT_MS = 6 * 60 * 60_000;
/** Tries per request (API calls and downloads); a wrong digest is never retried. */
export const DEFAULT_ATTEMPTS = 3;

/** The command line or a repository file is unusable (exit 2). */
export class UsageError extends Error {}

/** A check, download or verification failed (exit 1). */
export class CheckError extends Error {}

/** Platform key of a Node platform/arch pair, or null when VANTA has no runtime archive for it. */
export function platformKey({ platform = process.platform, arch = process.arch } = {}) {
  const os = platform === 'win32' ? 'windows' : platform === 'linux' ? 'linux' : platform === 'darwin' ? 'macos' : null;
  const cpu = arch === 'x64' ? 'x64' : arch === 'arm64' ? 'arm64' : null;
  return os && cpu ? `${os}-${cpu}` : null;
}

/** 'zip' or 'tar.gz' from the archive name. */
export function archiveKind(file) {
  if (/\.zip$/i.test(file)) return 'zip';
  if (/\.tar\.gz$/i.test(file) || /\.tgz$/i.test(file)) return 'tar.gz';
  throw new UsageError(`${file}: not a .zip or .tar.gz archive`);
}

/** https://github.com/<owner>/<repo> + tag -> the GitHub Releases API URL of that release. */
export function githubReleaseApiUrl(sourceUrl, tag) {
  const match = /^https:\/\/github\.com\/([A-Za-z0-9_.-]+)\/([A-Za-z0-9_.-]+?)(?:\.git)?\/?$/.exec(sourceUrl ?? '');
  if (!match) throw new UsageError(`runtime.sourceUrl '${sourceUrl}' is not a GitHub repository URL`);
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(tag ?? '')) throw new UsageError(`runtime.tag '${tag}' is not a release tag`);
  return `https://api.github.com/repos/${match[1]}/${match[2]}/releases/tags/${encodeURIComponent(tag)}`;
}

/**
 * https://huggingface.co/<owner>/<repo>/resolve/<revision>/<path> -> the tree API listing of that revision and the
 * path of the file inside the repository.
 */
export function hfTreeApiUrl(modelUrl) {
  const match = /^https:\/\/huggingface\.co\/([^/\s]+)\/([^/\s]+)\/resolve\/([^/\s]+)\/(.+)$/.exec(modelUrl ?? '');
  if (!match) throw new UsageError(`model.url '${modelUrl}' is not a huggingface.co resolve URL`);
  const [, owner, repo, revision, path] = match;
  const folder = posix.dirname(path);
  const api = `https://huggingface.co/api/models/${owner}/${repo}/tree/${revision}${folder === '.' ? '' : `/${folder}`}`;
  return { api, path, owner, repo, revision };
}

/** The UTC instant without milliseconds, as the schema wants it. */
export function isoInstant(now = new Date()) {
  return now.toISOString().replace(/\.\d{3}Z$/, 'Z');
}

/** Reads and parses a JSON manifest file; a UsageError when it cannot be read. */
export function loadManifest(path) {
  try {
    return readJson(path);
  } catch (error) {
    throw new UsageError(`cannot read ${path}: ${error.message}`);
  }
}

/** Validates a manifest against shared/schemas/local-ai.schema.json under `root`. */
export function validateManifest(manifest, root = REPO_ROOT) {
  const schemaPath = resolve(root, SCHEMA_PATH);
  if (!existsSync(schemaPath)) throw new UsageError(`${SCHEMA_PATH} is missing under ${root}`);
  return validate(readJson(schemaPath), manifest, { baseDir: dirname(schemaPath) });
}

/**
 * The template values a manifest still carries (what the resolver fills in), as readable strings; empty when none.
 * Only looks at the fields the resolver owns, so a structurally broken file is reported by the schema instead.
 */
export function templateValues(manifest) {
  const open = [];
  if (!manifest || typeof manifest !== 'object') return ['not a JSON object'];
  if (!manifest.resolvedAt) open.push('resolvedAt is empty');
  const platforms = manifest.runtime?.platforms ?? {};
  for (const [key, entry] of Object.entries(platforms)) {
    if (!entry || typeof entry !== 'object') continue;
    if (!(Number.isInteger(entry.size) && entry.size > 0)) open.push(`runtime.platforms.${key}.size is ${JSON.stringify(entry.size)}`);
    if (!isSha256Hex(entry.sha256)) open.push(`runtime.platforms.${key}.sha256 is ${JSON.stringify(entry.sha256)}`);
    if (!entry.serverPath || entry.serverPath === PLACEHOLDER) open.push(`runtime.platforms.${key}.serverPath is ${JSON.stringify(entry.serverPath)}`);
  }
  const model = manifest.model ?? {};
  if (!(Number.isInteger(model.size) && model.size > 0)) open.push(`model.size is ${JSON.stringify(model.size)}`);
  if (!isSha256Hex(model.sha256)) open.push(`model.sha256 is ${JSON.stringify(model.sha256)}`);
  return open;
}

/**
 * Loads the committed manifest of a repository and requires it to be resolved (schema-valid). Used by the full
 * release zip (build-bundle.mjs), which must never ship a template.
 * @returns {{ path: string, manifest: object }}
 */
export function loadLocalAiManifest(root = REPO_ROOT) {
  const path = resolve(root, MANIFEST_PATH);
  if (!existsSync(path)) throw new UsageError(`${MANIFEST_PATH} is missing`);
  const manifest = loadManifest(path);
  const result = validateManifest(manifest, root);
  if (!result.valid) {
    const open = templateValues(manifest);
    const why = open.length > 0
      ? `it still holds template values (${open.slice(0, 3).join(', ')}${open.length > 3 ? ', ...' : ''}); run the Local AI resolve workflow and commit its result`
      : result.errors.map((e) => `${e.path}: ${e.message}`).join('; ');
    throw new UsageError(`${MANIFEST_PATH} is not resolved: ${why}`);
  }
  return { path, manifest };
}

// ---- HTTP -----------------------------------------------------------------------------------------------------------

function sleep(ms) {
  return new Promise((done) => setTimeout(done, ms));
}

/**
 * JSON API calls and streamed downloads through one injectable fetch, with retries on network errors, timeouts and
 * 5xx answers. A CheckError or UsageError thrown while reading an answer is final (the server answered; the answer
 * is wrong). api.github.com requests carry the GitHub token when one is given (CI rate limits).
 */
export class Client {
  constructor({ fetch: fetchImpl, userAgent: ua, githubToken = '', timeoutMs = DEFAULT_TIMEOUT_MS, attempts = DEFAULT_ATTEMPTS, delayMs = 1000, log = () => {} }) {
    if (typeof fetchImpl !== 'function') throw new TypeError('a fetch implementation is required');
    this.fetch = fetchImpl;
    this.ua = ua;
    this.githubToken = githubToken;
    this.timeoutMs = timeoutMs;
    this.attempts = Math.max(1, attempts);
    this.delayMs = delayMs;
    this.log = log;
    /** Every URL requested, in order (tests and the status output). */
    this.requests = [];
  }

  headersFor(url, accept) {
    const headers = { 'User-Agent': this.ua, Accept: accept };
    if (this.githubToken && new URL(url).hostname === 'api.github.com') {
      headers.Authorization = `Bearer ${this.githubToken}`;
      headers['X-GitHub-Api-Version'] = '2022-11-28';
    }
    return headers;
  }

  /** Fetches `url` and hands the response to `read`; see the class comment for what is retried. */
  async request(url, { accept = '*/*', timeoutMs = this.timeoutMs, read }) {
    let last;
    for (let attempt = 1; attempt <= this.attempts; attempt += 1) {
      this.requests.push(url);
      const controller = new AbortController();
      const timer = timeoutMs > 0 ? setTimeout(() => controller.abort(new DOMException(`no answer within ${timeoutMs} ms`, 'TimeoutError')), timeoutMs) : null;
      try {
        const response = await this.fetch(url, { headers: this.headersFor(url, accept), redirect: 'follow', signal: timer ? controller.signal : undefined });
        if (response.status >= 500 && attempt < this.attempts) {
          last = new CheckError(`${url}: HTTP ${response.status}`);
        } else {
          return await read(response);
        }
      } catch (error) {
        if (error instanceof CheckError || error instanceof UsageError) throw error;
        const reason = error?.name === 'TimeoutError' || error?.name === 'AbortError' ? `no answer within ${timeoutMs} ms` : error.message;
        if (attempt === this.attempts) throw new CheckError(`${url}: ${reason}`);
        last = new CheckError(`${url}: ${reason}`);
      } finally {
        if (timer) clearTimeout(timer);
      }
      this.log(`  retrying ${url} after ${last.message}`);
      if (this.delayMs > 0) await sleep(this.delayMs * attempt);
    }
    throw last;
  }

  /** GET a JSON document; `{ body, headers }`. */
  async json(url, { accept = 'application/json' } = {}) {
    return this.request(url, {
      accept,
      read: async (response) => {
        if (!response.ok) throw new CheckError(`${url}: HTTP ${response.status}`);
        let body;
        try {
          body = await response.json();
        } catch (error) {
          throw new CheckError(`${url}: not JSON (${error.message})`);
        }
        return { body, headers: response.headers };
      },
    });
  }

  /**
   * Downloads `url` to `dest`, hashing while streaming. The bytes go to `<dest>.part` and are renamed when complete;
   * a failed or mismatching download leaves nothing behind. Size and digest are checked when given (a mismatch is a
   * CheckError and is not retried: the published files never change, so wrong bytes are a wrong file).
   * @returns {Promise<{ size: number, sha256: string }>}
   */
  async download(url, dest, { expectedSize, expectedSha256, timeoutMs = this.timeoutMs, label = basename(dest) } = {}) {
    mkdirSync(dirname(dest), { recursive: true });
    const part = `${dest}${PART_SUFFIX}`;
    const result = await this.request(url, {
      accept: 'application/octet-stream, */*',
      timeoutMs,
      read: async (response) => {
        if (!response.ok) throw new CheckError(`${url}: HTTP ${response.status}`);
        if (!response.body) throw new Error('empty response body');
        const out = createWriteStream(part);
        const hash = createHash('sha256');
        let size = 0;
        try {
          for await (const chunk of response.body) {
            size += chunk.length;
            hash.update(chunk);
            if (!out.write(chunk)) await once(out, 'drain');
          }
          await new Promise((done, fail) => {
            out.once('error', fail);
            out.end(done);
          });
        } catch (error) {
          out.destroy();
          rmSync(part, { force: true });
          throw error;
        }
        return { size, sha256: hash.digest('hex') };
      },
    });
    const problems = [];
    if (expectedSize !== undefined && result.size !== expectedSize) problems.push(`${result.size} bytes instead of ${expectedSize}`);
    if (expectedSha256 !== undefined && result.sha256 !== expectedSha256) problems.push(`SHA-256 ${result.sha256} instead of ${expectedSha256}`);
    if (problems.length > 0) {
      rmSync(part, { force: true });
      throw new CheckError(`${label}: downloaded ${problems.join(' and ')} (${url}); the file was deleted`);
    }
    rmSync(dest, { force: true });
    renameSync(part, dest);
    return result;
  }
}

// ---- archives -----------------------------------------------------------------------------------------------------

function isServerName(name, component) {
  const base = posix.basename(name);
  return base === component || base === `${component}.exe`;
}

/**
 * Finds the server executable inside an archive on disk; its path relative to the extraction root. Exactly one entry
 * named <component> or <component>.exe must exist.
 */
export function findServerPath(archivePath, component) {
  const kind = archiveKind(archivePath);
  let candidates;
  if (kind === 'zip') {
    candidates = openZip(archivePath).entries.filter((entry) => !entry.directory && isServerName(entry.name, component)).map((entry) => entry.name.replace(/^\.\//, ''));
  } else {
    candidates = listTarGz(archivePath).entries.filter((entry) => entry.type === 'file' && isServerName(entry.name, component)).map((entry) => entry.name);
  }
  if (candidates.length === 0) throw new CheckError(`${basename(archivePath)}: no ${component} or ${component}.exe inside the archive`);
  if (candidates.length > 1) throw new CheckError(`${basename(archivePath)}: ${candidates.length} entries named ${component} (${candidates.join(', ')}); the server path is ambiguous`);
  const path = candidates[0];
  if (!/^[A-Za-z0-9][A-Za-z0-9._+-]*(\/[A-Za-z0-9][A-Za-z0-9._+-]*)*$/.test(path)) throw new CheckError(`${basename(archivePath)}: server path ${JSON.stringify(path)} has characters the manifest does not allow`);
  return path;
}

/** Unzips every entry into `destDir`, refusing names that would leave it. */
function extractZip(archivePath, destDir) {
  const zip = openZip(archivePath);
  const root = resolve(destDir);
  mkdirSync(root, { recursive: true });
  const files = [];
  for (const entry of zip.entries) {
    const parts = entry.name.split('/').filter((part) => part !== '' && part !== '.');
    if (parts.some((part) => part === '..') || /^[A-Za-z]:/.test(entry.name) || entry.name.startsWith('/')) throw new CheckError(`${basename(archivePath)}: unsafe zip entry name ${JSON.stringify(entry.name)}`);
    if (parts.length === 0) continue;
    const target = join(root, ...parts);
    if (entry.directory) {
      mkdirSync(target, { recursive: true });
      continue;
    }
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, zip.read(entry.name));
    files.push(parts.join('/'));
  }
  return { files, skipped: [] };
}

/** Extracts a .zip or .tar.gz into `destDir`; returns the regular files written (relative, '/' separators). */
export function extractArchive(archivePath, destDir) {
  try {
    return archiveKind(archivePath) === 'zip' ? extractZip(archivePath, destDir) : extractTarGz(archivePath, destDir);
  } catch (error) {
    if (error instanceof CheckError || error instanceof UsageError) throw error;
    throw new CheckError(`${basename(archivePath)}: ${error.message}`);
  }
}

// ---- resolve --------------------------------------------------------------------------------------------------------

function requirePlatforms(manifest) {
  const platforms = manifest?.runtime?.platforms;
  if (!platforms || typeof platforms !== 'object') throw new UsageError('runtime.platforms is missing');
  const keys = Object.keys(platforms);
  const missing = PLATFORM_KEYS.filter((key) => !keys.includes(key));
  const extra = keys.filter((key) => !PLATFORM_KEYS.includes(key));
  if (missing.length > 0 || extra.length > 0) throw new UsageError(`runtime.platforms must hold exactly ${PLATFORM_KEYS.join(', ')}${missing.length ? `; missing ${missing.join(', ')}` : ''}${extra.length ? `; unexpected ${extra.join(', ')}` : ''}`);
  for (const key of PLATFORM_KEYS) {
    const entry = platforms[key];
    if (!entry || typeof entry !== 'object' || typeof entry.file !== 'string' || entry.file === '') throw new UsageError(`runtime.platforms.${key}.file is missing`);
    archiveKind(entry.file);
    if (!isSha256Hex(entry.sha256)) throw new UsageError(`runtime.platforms.${key}.sha256 must be the SHA-256 GitHub shows for ${entry.file} (64 lower-case hex characters), got ${JSON.stringify(entry.sha256)}`);
  }
  return platforms;
}

/** The release's assets by name, from the GitHub Releases API. */
async function fetchReleaseAssets(manifest, client) {
  const url = githubReleaseApiUrl(manifest.runtime.sourceUrl, manifest.runtime.tag);
  const { body } = await client.json(url, { accept: 'application/vnd.github+json' });
  if (!Array.isArray(body?.assets)) throw new CheckError(`${url}: no assets[] in the answer`);
  const assets = new Map();
  for (const asset of body.assets) {
    if (typeof asset?.name === 'string') assets.set(asset.name, asset);
  }
  return { url, assets, release: body };
}

/** The digest GitHub publishes for an asset ("sha256:<hex>"), lower-case hex, or null. */
function assetDigest(asset) {
  const match = /^sha256:([A-Fa-f0-9]{64})$/.exec(asset?.digest ?? '');
  return match ? match[1].toLowerCase() : null;
}

/**
 * Checks one platform entry against its GitHub asset (presence, URL, size when the manifest has one, published
 * digest) and returns the asset's facts. Problems are CheckErrors.
 */
function matchAsset(key, entry, assets, { fillUrl }) {
  const asset = assets.get(entry.file);
  if (!asset) throw new CheckError(`${key}: the release has no asset named ${entry.file} (assets: ${[...assets.keys()].join(', ') || 'none'})`);
  if (!Number.isInteger(asset.size) || asset.size <= 0) throw new CheckError(`${key}: GitHub reports no size for ${entry.file}`);
  const url = asset.browser_download_url;
  if (typeof url !== 'string' || !/^https:\/\/\S+$/.test(url)) throw new CheckError(`${key}: GitHub reports no https download URL for ${entry.file}`);
  if (fillUrl && (!entry.url || entry.url === PLACEHOLDER)) entry.url = url;
  if (entry.url !== url) throw new CheckError(`${key}: the manifest URL ${entry.url} differs from the release asset URL ${url}`);
  const digest = assetDigest(asset);
  if (digest && digest !== entry.sha256) throw new CheckError(`${key}: GitHub publishes SHA-256 ${digest} for ${entry.file}, the manifest says ${entry.sha256}; the file was not downloaded`);
  return { url, size: asset.size, digest };
}

/** The model's entry in the Hugging Face tree listing (following pagination); size and LFS sha256. */
async function fetchModelFacts(model, client) {
  const { api, path } = hfTreeApiUrl(model.url);
  let url = api;
  for (let page = 0; page < 20 && url; page += 1) {
    const { body, headers } = await client.json(url);
    if (!Array.isArray(body)) throw new CheckError(`${url}: the tree listing is not an array`);
    const found = body.find((item) => item?.path === path || item?.path === posix.basename(path));
    if (found) {
      const sha256 = typeof found.lfs?.oid === 'string' ? found.lfs.oid.toLowerCase() : null;
      if (!isSha256Hex(sha256)) throw new CheckError(`${api}: ${path} is not stored in LFS (no lfs.oid), so Hugging Face publishes no SHA-256 for it`);
      const size = Number.isInteger(found.lfs?.size) ? found.lfs.size : found.size;
      if (!Number.isInteger(size) || size <= 0) throw new CheckError(`${api}: no size for ${path}`);
      return { api, size, sha256 };
    }
    const link = typeof headers?.get === 'function' ? headers.get('link') : null;
    const next = /<([^>]+)>;\s*rel="next"/.exec(link ?? '');
    url = next ? next[1] : null;
  }
  throw new CheckError(`${api}: ${path} is not in the repository tree`);
}

/**
 * The `resolve` command: a completed manifest from the template. The template is not modified; the returned object
 * is a deep copy with sizes, URLs, serverPaths, the model digest and resolvedAt filled in and validated.
 */
export async function resolveManifest({ template, client, workDir, skipModelDownload = false, root = REPO_ROOT, now = new Date(), log = () => {} }) {
  const manifest = structuredClone(template);
  if (manifest?.schemaVersion !== 1) throw new UsageError(`schemaVersion must be 1, got ${JSON.stringify(manifest?.schemaVersion)}`);
  const platforms = requirePlatforms(manifest);
  if (!manifest.model || typeof manifest.model !== 'object') throw new UsageError('model is missing');
  const component = manifest.runtime.component;
  if (typeof component !== 'string' || component === '') throw new UsageError('runtime.component is missing');
  mkdirSync(workDir, { recursive: true });

  const { url: apiUrl, assets } = await fetchReleaseAssets(manifest, client);
  log(`GitHub release ${manifest.runtime.tag}: ${assets.size} assets (${apiUrl})`);
  for (const key of PLATFORM_KEYS) {
    const entry = platforms[key];
    const asset = matchAsset(key, entry, assets, { fillUrl: true });
    const dest = join(workDir, entry.file);
    const { size, sha256 } = await client.download(entry.url, dest, { expectedSize: asset.size, expectedSha256: entry.sha256, label: `${key} ${entry.file}` });
    const serverPath = findServerPath(dest, component);
    if (entry.serverPath && entry.serverPath !== PLACEHOLDER && entry.serverPath !== serverPath) {
      log(`  note: ${key}: the template says serverPath ${entry.serverPath}, the archive holds ${serverPath}; the archive wins`);
    }
    entry.size = size;
    entry.serverPath = serverPath;
    log(`  ok  ${key.padEnd(13)} ${entry.file}  ${size} bytes  sha256 ${sha256}  serverPath ${serverPath}`);
    rmSync(dest, { force: true });
  }

  const facts = await fetchModelFacts(manifest.model, client);
  if (isSha256Hex(manifest.model.sha256) && manifest.model.sha256 !== facts.sha256) {
    throw new CheckError(`model: the template pins SHA-256 ${manifest.model.sha256}, Hugging Face publishes ${facts.sha256} for ${manifest.model.file}`);
  }
  if (Number.isInteger(manifest.model.size) && manifest.model.size > 0 && manifest.model.size !== facts.size) {
    throw new CheckError(`model: the template says ${manifest.model.size} bytes, Hugging Face publishes ${facts.size} for ${manifest.model.file}`);
  }
  log(`Hugging Face: ${manifest.model.file}  ${facts.size} bytes  sha256 ${facts.sha256} (${facts.api})`);
  if (skipModelDownload) {
    log('  model download skipped (--skip-model-download): size and SHA-256 come from the Hugging Face LFS metadata only');
  } else {
    const dest = join(workDir, manifest.model.file);
    log(`  downloading ${manifest.model.url} (${formatSize(facts.size)}) to confirm the digest`);
    const result = await client.download(manifest.model.url, dest, { expectedSize: facts.size, expectedSha256: facts.sha256, timeoutMs: Math.max(client.timeoutMs, MODEL_TIMEOUT_MS), label: `model ${manifest.model.file}` });
    log(`  ok  model         ${manifest.model.file}  ${result.size} bytes  sha256 ${result.sha256}`);
    rmSync(dest, { force: true });
  }
  manifest.model.size = facts.size;
  manifest.model.sha256 = facts.sha256;
  manifest.resolvedAt = isoInstant(now);

  const result = validateManifest(manifest, root);
  if (!result.valid) throw new CheckError(`the resolved manifest does not match ${SCHEMA_PATH}:\n${result.errors.map((e) => `  ${e.path}: ${e.message}`).join('\n')}`);
  return manifest;
}

// ---- verify ---------------------------------------------------------------------------------------------------------

/**
 * The `verify` command: a committed manifest against its sources. Every problem is collected; the archives are
 * downloaded and re-hashed, the model is checked through the Hugging Face API (and downloaded with `full`).
 * @returns {Promise<{ ok: boolean, problems: string[] }>}
 */
export async function verifyManifest({ manifest, client, workDir, full = false, root = REPO_ROOT, log = () => {} }) {
  const problems = [];
  const schema = validateManifest(manifest, root);
  if (!schema.valid) {
    for (const error of schema.errors) problems.push(`schema: ${error.path}: ${error.message}`);
    const open = templateValues(manifest);
    if (open.length > 0) problems.push('the manifest is not resolved yet (template values); run `node scripts/release/local-ai.mjs resolve` in the Local AI resolve workflow');
    return { ok: false, problems };
  }
  mkdirSync(workDir, { recursive: true });
  const { assets } = await fetchReleaseAssets(manifest, client);
  for (const key of PLATFORM_KEYS) {
    const entry = manifest.runtime.platforms[key];
    try {
      const asset = matchAsset(key, entry, assets, { fillUrl: false });
      if (asset.size !== entry.size) throw new CheckError(`${key}: GitHub reports ${asset.size} bytes for ${entry.file}, the manifest says ${entry.size}`);
      const dest = join(workDir, entry.file);
      const { size, sha256 } = await client.download(entry.url, dest, { expectedSize: entry.size, expectedSha256: entry.sha256, label: `${key} ${entry.file}` });
      const serverPath = findServerPath(dest, manifest.runtime.component);
      if (serverPath !== entry.serverPath) throw new CheckError(`${key}: ${entry.file} holds the server at ${serverPath}, the manifest says ${entry.serverPath}`);
      log(`  ok  ${key.padEnd(13)} ${entry.file}  ${size} bytes  sha256 ${sha256}  serverPath ${serverPath}`);
      rmSync(dest, { force: true });
    } catch (error) {
      if (!(error instanceof CheckError)) throw error;
      problems.push(error.message);
      log(`  FAIL ${error.message}`);
    }
  }
  try {
    const facts = await fetchModelFacts(manifest.model, client);
    if (facts.size !== manifest.model.size) throw new CheckError(`model: Hugging Face publishes ${facts.size} bytes for ${manifest.model.file}, the manifest says ${manifest.model.size}`);
    if (facts.sha256 !== manifest.model.sha256) throw new CheckError(`model: Hugging Face publishes SHA-256 ${facts.sha256} for ${manifest.model.file}, the manifest says ${manifest.model.sha256}`);
    log(`  ok  model         ${manifest.model.file}  ${facts.size} bytes  sha256 ${facts.sha256} (Hugging Face API)`);
    if (full) {
      const dest = join(workDir, manifest.model.file);
      const result = await client.download(manifest.model.url, dest, { expectedSize: manifest.model.size, expectedSha256: manifest.model.sha256, timeoutMs: Math.max(client.timeoutMs, MODEL_TIMEOUT_MS), label: `model ${manifest.model.file}` });
      log(`  ok  model         ${manifest.model.file}  ${result.size} bytes  sha256 ${result.sha256} (downloaded)`);
      rmSync(dest, { force: true });
    }
  } catch (error) {
    if (!(error instanceof CheckError)) throw error;
    problems.push(error.message);
    log(`  FAIL ${error.message}`);
  }
  return { ok: problems.length === 0, problems };
}

// ---- prepare --------------------------------------------------------------------------------------------------------

/** Paths inside a Local AI directory (the layout of SPEC section 3, shared with the client and the launcher). */
export function installPaths(dir, manifest, platform) {
  const root = resolve(dir);
  const entry = manifest.runtime.platforms[platform];
  const runtimeDir = join(root, 'runtime', manifest.runtime.tag, platform);
  return {
    root,
    installedJson: join(root, 'installed.json'),
    runtimeDir,
    server: entry ? join(runtimeDir, ...entry.serverPath.split('/')) : null,
    modelsDir: join(root, 'models'),
    model: join(root, 'models', manifest.model.file),
    downloadsDir: join(root, 'downloads'),
  };
}

/** Reads installed.json; null when missing or malformed. */
export function readInstalled(path) {
  if (!existsSync(path)) return null;
  try {
    const json = readJson(path);
    return json && typeof json === 'object' ? json : null;
  } catch {
    return null;
  }
}

function fileFacts(path) {
  const stat = statSync(path);
  return { size: stat.size, mtime: Math.floor(stat.mtimeMs) };
}

/**
 * The installed.json record, in the shape the client reads (dev.vanta.core.ai.LocalAiInstalled): the manifest's
 * runtime section with only this platform's archive, the model section, the platform, the two instants and the sizes
 * and modification times of the server executable and the model file.
 */
export function installedRecord(manifest, platform, { installedAt, verifiedAt, server, model }) {
  const { platforms, ...runtime } = manifest.runtime;
  const serverFacts = fileFacts(server);
  const modelFacts = fileFacts(model);
  return {
    schemaVersion: 1,
    platform,
    installedAt,
    verifiedAt,
    runtime: { ...runtime, platforms: { [platform]: platforms[platform] } },
    model: manifest.model,
    files: { serverSize: serverFacts.size, serverMtime: serverFacts.mtime, modelSize: modelFacts.size, modelMtime: modelFacts.mtime },
  };
}

/** True when installed.json describes this platform's archive and model of the manifest. */
export function installedMatches(installed, manifest, platform) {
  if (!installed || installed.platform !== platform) return false;
  const mine = installed.runtime?.platforms?.[platform];
  const theirs = manifest.runtime.platforms[platform];
  return Boolean(mine && theirs && installed.runtime.tag === manifest.runtime.tag && mine.sha256 === theirs.sha256 && mine.serverPath === theirs.serverPath
    && installed.model?.file === manifest.model.file && installed.model?.sha256 === manifest.model.sha256);
}

/**
 * Makes `dest` a verified copy of `source` for `serve`: an existing file is kept when it is the same inode or hashes to
 * `expectedSha256`, otherwise it is replaced; a hard link is tried first (no second copy of a 1.8 GB model), a plain
 * copy when linking fails (another file system, no permission).
 * @returns {'kept'|'linked'|'copied'}
 */
async function keepCopy(source, dest, expectedSize, expectedSha256) {
  if (existsSync(dest) && statSync(dest).isFile()) {
    const a = statSync(source);
    const b = statSync(dest);
    if (a.ino === b.ino && a.dev === b.dev && a.ino !== 0) return 'kept';
    const actual = await hashFile(dest);
    if (actual.size === expectedSize && actual.sha256 === expectedSha256) return 'kept';
    rmSync(dest, { force: true });
  }
  mkdirSync(dirname(dest), { recursive: true });
  try {
    linkSync(source, dest);
    return 'linked';
  } catch {
    copyFileSync(source, dest);
    return 'copied';
  }
}

/**
 * The `prepare` command: a complete, verified Local AI directory for one platform. With `keepDownloads` the verified
 * runtime archive stays under downloads/ (downloaded again, verified, when it is missing) and the model is linked
 * there too, so `serve --dir <dir>/downloads` can offer both to the launcher's `--install-local-ai` in CI.
 * @returns {Promise<{ platform: string, installed: object, downloaded: string[], skipped: string[], kept: string[], paths: object }>}
 */
export async function prepareInstall({ manifest, dir, platform, client, root = REPO_ROOT, now = new Date(), keepDownloads = false, log = () => {} }) {
  const schema = validateManifest(manifest, root);
  if (!schema.valid) {
    const open = templateValues(manifest);
    throw new UsageError(open.length > 0
      ? `the manifest is not resolved yet (${open.slice(0, 3).join(', ')}${open.length > 3 ? ', ...' : ''}); run the Local AI resolve workflow first`
      : `the manifest does not match ${SCHEMA_PATH}: ${schema.errors.map((e) => `${e.path}: ${e.message}`).join('; ')}`);
  }
  if (!PLATFORM_KEYS.includes(platform)) throw new UsageError(`Local AI is not available for platform '${platform}' (one of ${PLATFORM_KEYS.join(', ')})`);
  const entry = manifest.runtime.platforms[platform];
  const paths = installPaths(dir, manifest, platform);
  mkdirSync(paths.root, { recursive: true });
  mkdirSync(paths.modelsDir, { recursive: true });
  mkdirSync(paths.downloadsDir, { recursive: true });
  const downloaded = [];
  const skipped = [];
  const kept = [];
  const existing = readInstalled(paths.installedJson);

  // Model: keep a file whose size and SHA-256 match the manifest; anything else is replaced.
  let modelOk = false;
  if (existsSync(paths.model) && statSync(paths.model).isFile()) {
    const actual = await hashFile(paths.model);
    modelOk = actual.size === manifest.model.size && actual.sha256 === manifest.model.sha256;
    if (!modelOk) {
      log(`  models/${manifest.model.file} does not match the manifest (${actual.size} bytes, sha256 ${actual.sha256}); downloading it again`);
      rmSync(paths.model, { force: true });
    }
  }
  if (modelOk) {
    skipped.push(`models/${manifest.model.file}`);
    log(`  ok  models/${manifest.model.file} already present and verified (${manifest.model.size} bytes)`);
  } else {
    log(`  downloading ${manifest.model.url} (${formatSize(manifest.model.size)})`);
    const staged = join(paths.downloadsDir, manifest.model.file);
    await client.download(manifest.model.url, staged, { expectedSize: manifest.model.size, expectedSha256: manifest.model.sha256, timeoutMs: Math.max(client.timeoutMs, MODEL_TIMEOUT_MS), label: `model ${manifest.model.file}` });
    renameSync(staged, paths.model);
    downloaded.push(`models/${manifest.model.file}`);
    log(`  ok  models/${manifest.model.file}  ${manifest.model.size} bytes  sha256 ${manifest.model.sha256}`);
  }

  // Runtime: keep the extracted archive when installed.json records exactly this archive and the server is there.
  const runtimeOk = installedMatches(existing, manifest, platform) && existsSync(paths.server) && statSync(paths.server).isFile();
  const runtimeRel = posix.join('runtime', manifest.runtime.tag, platform);
  const archive = join(paths.downloadsDir, entry.file);
  if (runtimeOk) {
    skipped.push(runtimeRel);
    log(`  ok  ${runtimeRel}/ already installed (${entry.file}, sha256 ${entry.sha256})`);
  } else {
    log(`  downloading ${entry.url} (${formatSize(entry.size)})`);
    await client.download(entry.url, archive, { expectedSize: entry.size, expectedSha256: entry.sha256, label: `${platform} ${entry.file}` });
    const serverPath = findServerPath(archive, manifest.runtime.component);
    if (serverPath !== entry.serverPath) {
      rmSync(archive, { force: true });
      throw new CheckError(`${platform}: ${entry.file} holds the server at ${serverPath}, the manifest says ${entry.serverPath}`);
    }
    const extracting = `${paths.runtimeDir}.extracting`;
    rmSync(extracting, { recursive: true, force: true });
    const { files, skipped: notExtracted } = extractArchive(archive, extracting);
    for (const line of notExtracted) log(`  note: ${entry.file}: skipped ${line}`);
    if (!files.includes(entry.serverPath)) throw new CheckError(`${platform}: ${entry.serverPath} was not extracted from ${entry.file}`);
    rmSync(paths.runtimeDir, { recursive: true, force: true });
    mkdirSync(dirname(paths.runtimeDir), { recursive: true });
    renameSync(extracting, paths.runtimeDir);
    if (!keepDownloads) rmSync(archive, { force: true });
    downloaded.push(runtimeRel);
    log(`  ok  ${runtimeRel}/  ${files.length} files from ${entry.file}  sha256 ${entry.sha256}`);
  }
  if (!platform.startsWith('windows') && process.platform !== 'win32') chmodSync(paths.server, 0o755);
  if (!existsSync(paths.server) || !statSync(paths.server).isFile()) throw new CheckError(`${platform}: ${entry.serverPath} is missing under ${paths.runtimeDir}`);

  // Kept downloads for `serve`: the verified archive (fetched again when an earlier run removed it) and the model.
  if (keepDownloads) {
    let archiveOk = false;
    if (existsSync(archive) && statSync(archive).isFile()) {
      const actual = await hashFile(archive);
      archiveOk = actual.size === entry.size && actual.sha256 === entry.sha256;
      if (!archiveOk) rmSync(archive, { force: true });
    }
    if (!archiveOk) {
      log(`  downloading ${entry.url} (${formatSize(entry.size)}) to keep under downloads/`);
      await client.download(entry.url, archive, { expectedSize: entry.size, expectedSha256: entry.sha256, label: `${platform} ${entry.file}` });
    }
    kept.push(`downloads/${entry.file}`);
    const how = await keepCopy(paths.model, join(paths.downloadsDir, manifest.model.file), manifest.model.size, manifest.model.sha256);
    kept.push(`downloads/${manifest.model.file}`);
    log(`  ok  downloads/${entry.file} and downloads/${manifest.model.file} kept for serve (model ${how})`);
  }

  for (const name of readdirSync(paths.downloadsDir)) {
    if (name.endsWith(PART_SUFFIX)) rmSync(join(paths.downloadsDir, name), { force: true });
  }
  const at = isoInstant(now);
  const installed = installedRecord(manifest, platform, {
    installedAt: runtimeOk && modelOk && typeof existing?.installedAt === 'string' && existing.installedAt !== '' ? existing.installedAt : at,
    verifiedAt: at,
    server: paths.server,
    model: paths.model,
  });
  writeJsonAtomic(paths.installedJson, installed);
  log(`  wrote ${paths.installedJson}`);
  return { platform, installed, downloaded, skipped, kept, paths };
}

// ---- mirror ---------------------------------------------------------------------------------------------------------

/**
 * Normalises the base URL of a mirror: a URL ending in '/', https anywhere or plain http on the loopback address only
 * (the launcher downloads from it without TLS, which is acceptable on the same machine and nowhere else).
 */
export function mirrorBase(base) {
  let url;
  try {
    url = new URL(base);
  } catch {
    throw new UsageError(`--base '${base}' is not a URL`);
  }
  const loopback = ['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname);
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && loopback)) {
    throw new UsageError(`--base '${base}' must be https, or http on 127.0.0.1 / localhost only`);
  }
  if (url.search || url.hash) throw new UsageError(`--base '${base}' must not carry a query or a fragment`);
  return url.href.endsWith('/') ? url.href : `${url.href}/`;
}

/**
 * The `mirror` command as data: a deep copy of `manifest` whose URL of `platform`'s archive and of the model point at
 * `<base><file>`; every other value, including resolvedAt and the other platforms, stays as it is.
 */
export function mirrorManifest(manifest, platform, base) {
  if (!PLATFORM_KEYS.includes(platform)) throw new UsageError(`Local AI is not available for platform '${platform}' (one of ${PLATFORM_KEYS.join(', ')})`);
  const prefix = mirrorBase(base);
  const mirrored = structuredClone(manifest);
  const entry = mirrored?.runtime?.platforms?.[platform];
  if (!entry || typeof entry.file !== 'string') throw new UsageError(`the manifest lists no archive for ${platform}`);
  if (!mirrored.model || typeof mirrored.model.file !== 'string') throw new UsageError('the manifest has no model.file');
  entry.url = `${prefix}${entry.file}`;
  mirrored.model.url = `${prefix}${mirrored.model.file}`;
  return mirrored;
}

/**
 * Checks that `prepare --keep-downloads` left the archive of `platform` and the model under `<dir>/downloads/` with the
 * manifest sizes (the SHA-256 is checked by whoever downloads them). Returns the two paths.
 */
export function mirrorFiles(dir, manifest, platform) {
  const paths = installPaths(dir, manifest, platform);
  const entry = manifest.runtime.platforms[platform];
  const files = [[join(paths.downloadsDir, entry.file), entry.size, `downloads/${entry.file}`],
    [join(paths.downloadsDir, manifest.model.file), manifest.model.size, `downloads/${manifest.model.file}`]];
  for (const [path, size, label] of files) {
    if (!existsSync(path) || !statSync(path).isFile()) throw new CheckError(`${label} is missing under ${paths.root}; run \`prepare --dir ${dir} --platform ${platform} --keep-downloads\` first`);
    const actual = statSync(path).size;
    if (actual !== size) throw new CheckError(`${label} has ${actual} bytes, the manifest says ${size}; run \`prepare --keep-downloads\` again`);
  }
  return { archive: files[0][0], model: files[1][0], downloadsDir: paths.downloadsDir };
}

// ---- serve ----------------------------------------------------------------------------------------------------------

/** The bare file name a request path asks for, or null when it is not one (directory, subdirectory, dot names). */
export function servedName(pathname) {
  let name;
  try {
    name = decodeURIComponent(pathname);
  } catch {
    return null;
  }
  if (!name.startsWith('/')) return null;
  name = name.slice(1);
  if (name === '' || name === '.' || name === '..' || name.includes('/') || name.includes('\\') || name.includes('\0')) return null;
  return name;
}

/**
 * Starts the `serve` file server: one flat directory on the loopback address, GET and HEAD with Content-Length, no
 * listing, no subdirectories, no ranges. Resolves once it listens.
 * @returns {Promise<{ server: import('node:http').Server, url: string, port: number, close: () => Promise<void> }>}
 */
export async function startFileServer({ dir, port = 0, host = '127.0.0.1', log = () => {} }) {
  const root = resolve(dir);
  if (!existsSync(root) || !statSync(root).isDirectory()) throw new UsageError(`--dir ${dir} is not a directory`);
  const server = createServer((req, res) => {
    const method = req.method ?? '';
    const name = servedName(new URL(req.url ?? '/', 'http://localhost').pathname);
    const answer = (status, headers, body) => {
      res.writeHead(status, headers);
      res.end(body);
      log(`${method} ${req.url} ${status}`);
    };
    if (method !== 'GET' && method !== 'HEAD') return answer(405, { Allow: 'GET, HEAD', 'Content-Length': 0 });
    const file = name === null ? null : join(root, name);
    let stat = null;
    try {
      stat = file && statSync(file);
    } catch {
      stat = null;
    }
    if (!stat || !stat.isFile()) return answer(404, { 'Content-Type': 'text/plain', 'Content-Length': 9 }, 'not found');
    res.writeHead(200, { 'Content-Type': 'application/octet-stream', 'Content-Length': stat.size, 'Accept-Ranges': 'none' });
    if (method === 'HEAD') {
      res.end();
      log(`HEAD ${req.url} 200 ${stat.size}`);
      return undefined;
    }
    const stream = createReadStream(file);
    stream.on('error', () => res.destroy());
    stream.pipe(res);
    log(`GET ${req.url} 200 ${stat.size}`);
    return undefined;
  });
  await new Promise((listening, failed) => {
    server.once('error', failed);
    server.listen(port, host, listening);
  });
  const actualPort = server.address().port;
  const url = `http://${host}:${actualPort}/`;
  return {
    server,
    url,
    port: actualPort,
    close: () => new Promise((closed) => {
      server.closeAllConnections?.();
      server.close(() => closed());
    }),
  };
}

// ---- status ---------------------------------------------------------------------------------------------------------

/**
 * The `status` command as data: whether the manifest is resolved and, with `dir`, whether it holds a complete
 * install for `platform` (quick check: installed.json matches, files present with the recorded sizes).
 * @returns {{ ok: boolean, lines: string[] }}
 */
export function statusReport({ manifest, manifestPath, dir, platform, root = REPO_ROOT }) {
  const lines = [];
  const schema = validateManifest(manifest, root);
  let ok = schema.valid;
  if (schema.valid) {
    const platforms = manifest.runtime.platforms;
    const runtimeSizes = PLATFORM_KEYS.map((key) => `${key} ${formatSize(platforms[key].size)}`).join(', ');
    lines.push(`manifest ${manifestPath}: resolved ${manifest.resolvedAt}`);
    lines.push(`  runtime ${manifest.runtime.name} ${manifest.runtime.tag} (${manifest.runtime.component}, ${manifest.runtime.license}): ${runtimeSizes}`);
    lines.push(`  model ${manifest.model.name} ${manifest.model.quantization} (${manifest.model.license}): ${manifest.model.file} ${formatSize(manifest.model.size)} sha256 ${manifest.model.sha256}`);
  } else {
    const open = templateValues(manifest);
    lines.push(`manifest ${manifestPath}: not resolved yet`);
    if (open.length > 0) {
      lines.push(`  template values: ${open.join(', ')}`);
      lines.push('  run the Local AI resolve workflow (.github/workflows/local-ai-resolve.yml) and commit its local-ai.json');
    } else {
      for (const error of schema.errors) lines.push(`  schema: ${error.path}: ${error.message}`);
    }
  }
  if (dir !== undefined && schema.valid) {
    if (!PLATFORM_KEYS.includes(platform)) {
      lines.push(`install ${dir}: Local AI is not available for platform '${platform}'`);
      return { ok: false, lines };
    }
    const paths = installPaths(dir, manifest, platform);
    const installed = readInstalled(paths.installedJson);
    const problems = [];
    if (!installed) problems.push('installed.json is missing or malformed');
    else if (!installedMatches(installed, manifest, platform)) problems.push('installed.json does not describe this manifest for this platform');
    if (!existsSync(paths.server) || !statSync(paths.server).isFile()) problems.push(`${posix.join('runtime', manifest.runtime.tag, platform, manifest.runtime.platforms[platform].serverPath)} is missing`);
    else if (installed?.files && statSync(paths.server).size !== installed.files.serverSize) problems.push('the server executable has another size than installed.json records');
    if (!existsSync(paths.model) || !statSync(paths.model).isFile()) problems.push(`models/${manifest.model.file} is missing`);
    else if (statSync(paths.model).size !== manifest.model.size) problems.push(`models/${manifest.model.file} has ${statSync(paths.model).size} bytes, the manifest says ${manifest.model.size}`);
    if (problems.length === 0) {
      lines.push(`install ${paths.root} (${platform}): complete, installed ${installed.installedAt}, verified ${installed.verifiedAt}`);
    } else {
      ok = false;
      lines.push(`install ${paths.root} (${platform}): incomplete`);
      for (const problem of problems) lines.push(`  ${problem}`);
    }
  }
  return { ok, lines };
}

// ---- LOCAL-AI.txt ---------------------------------------------------------------------------------------------------

/**
 * The LOCAL-AI.txt note of the full release zip: what the client downloads on first use of Vanta Nexus, from where,
 * how large, under which licences. Plain ASCII, lines of at most 100 columns, every number from the manifest.
 */
export function localAiNote(manifest) {
  const { runtime, model, requirements } = manifest;
  const title = 'VANTA Local AI: what Vanta Nexus downloads on first use';
  const lines = [title, '='.repeat(title.length), ''];
  lines.push(...wrapText(`Vanta Nexus, the assistant in the VANTA Client, runs a language model on your own PC. This archive does not contain the model or the runtime. They are downloaded once, only after you choose to install the Local AI (in Vanta Nexus or in the VANTA Launcher), verified by SHA-256 against the values below and then used offline. The assistant talks to 127.0.0.1 only; nothing you type is sent anywhere.`));
  lines.push('', `Runtime: ${runtime.name} ${runtime.tag} (${runtime.component}), licence ${runtime.license}`, '');
  lines.push(`  Source:   ${runtime.sourceUrl}`);
  lines.push(`  Release:  ${runtime.releaseUrl}`);
  lines.push('  One archive, the one for your system, is downloaded from that release:');
  for (const key of PLATFORM_KEYS) {
    const entry = runtime.platforms[key];
    lines.push(`    ${key.padEnd(14)} ${entry.file}  (${formatSize(entry.size)})`);
    lines.push(`    ${''.padEnd(14)} sha256 ${entry.sha256}`);
  }
  lines.push('', `Model: ${model.name} ${model.quantization}, licence ${model.license}`, '');
  lines.push(`  File:     ${model.file}  (${formatSize(model.size)})`);
  lines.push(`  From:     ${model.url}`);
  lines.push(`  Source:   ${model.sourceUrl}`);
  lines.push(`  Licence:  ${model.licenseUrl}`);
  lines.push(`  sha256:   ${model.sha256}`);
  lines.push(`  Context:  ${model.contextSize} tokens`);
  lines.push('');
  lines.push(...wrapText(`Requirements: about ${requirements.diskMb} MB of free disk space for the runtime and the model and ${requirements.ramMb} MB of RAM while the assistant runs. The download hosts are github.com (runtime) and huggingface.co (model); nothing else is contacted for the Local AI.`));
  lines.push('');
  lines.push(...wrapText(`local-ai/local-ai.json in this archive is the exact list the client and the launcher use (resolved ${manifest.resolvedAt}); both refuse any file whose size or SHA-256 differs from it.`));
  lines.push('');
  lines.push(...wrapText(`Licences: ${runtime.name} is published under the ${runtime.license} licence by its authors (see its repository above); the ${model.name} model is published by its authors under the ${model.license} licence (see the licence link above). VANTA redistributes neither; your PC downloads them from their publishers.`));
  return `${lines.join('\n')}\n`;
}

// ---- CLI ------------------------------------------------------------------------------------------------------------

/**
 * CLI entry point.
 * @param {string[]} argv
 * @param {{ fetch?: Function, log?: Function, logError?: Function, env?: object, now?: Date, platform?: string, arch?: string, delayMs?: number,
 *           onServe?: (server: { url: string, port: number, close: () => Promise<void> }) => void, signals?: boolean }} deps
 *        `onServe` (tests) receives the running `serve` server; `signals` (default true) installs the SIGINT/SIGTERM handlers that stop it
 */
export async function main(argv, deps = {}) {
  const log = deps.log ?? console.log;
  const logError = deps.logError ?? console.error;
  const env = deps.env ?? process.env;
  const parsed = parseArgs(argv, {
    values: ['manifest', 'out', 'work', 'dir', 'platform', 'root', 'timeout', 'attempts', 'base', 'port'],
    flags: ['skip-model-download', 'full', 'keep-downloads', 'quiet', 'help'],
    aliases: { h: 'help', q: 'quiet' },
  });
  if (parsed.flags.has('help')) {
    log(USAGE);
    return 0;
  }
  const [command, ...rest] = parsed.positional;
  const o = parsed.options;
  const commands = ['resolve', 'verify', 'prepare', 'mirror', 'serve', 'status'];
  if (!command) parsed.errors.push('a command is required');
  else if (!commands.includes(command)) parsed.errors.push(`unknown command '${command}'`);
  const wantedPositionals = command === 'verify' ? 1 : 0;
  if (command && rest.length !== wantedPositionals) parsed.errors.push(`${command} takes ${wantedPositionals === 1 ? 'exactly one manifest path' : 'no positional arguments'}`);
  if (['prepare', 'mirror', 'serve'].includes(command) && !o.dir) parsed.errors.push(`--dir <dir> is required for ${command}`);
  if (command === 'mirror' && !o.base) parsed.errors.push('--base <url> is required for mirror');
  if (command === 'mirror' && !o.out) parsed.errors.push('--out <path> is required for mirror');
  if (command === 'serve' && o.port === undefined) parsed.errors.push('--port <n> is required for serve (0 picks a free port)');
  if (parsed.flags.has('full') && command !== 'verify') parsed.errors.push('--full only applies to verify');
  if (parsed.flags.has('full') && parsed.flags.has('skip-model-download')) parsed.errors.push('--full and --skip-model-download exclude each other');
  if (parsed.flags.has('skip-model-download') && !['resolve', 'verify'].includes(command)) parsed.errors.push('--skip-model-download only applies to resolve and verify');
  if (parsed.flags.has('keep-downloads') && command !== 'prepare') parsed.errors.push('--keep-downloads only applies to prepare');
  if (o.out !== undefined && !['resolve', 'mirror'].includes(command)) parsed.errors.push('--out only applies to resolve and mirror');
  if (o.dir !== undefined && !['prepare', 'mirror', 'serve', 'status'].includes(command)) parsed.errors.push('--dir only applies to prepare, mirror, serve and status');
  if (o.base !== undefined && command !== 'mirror') parsed.errors.push('--base only applies to mirror');
  if (o.port !== undefined && command !== 'serve') parsed.errors.push('--port only applies to serve');
  if (o.port !== undefined && !(/^(0|[1-9]\d*)$/.test(o.port) && Number(o.port) <= 65535)) parsed.errors.push('--port must be a port number (0 to 65535)');
  if (o.manifest !== undefined && command === 'serve') parsed.errors.push('--manifest does not apply to serve');
  if (o.platform !== undefined && !PLATFORM_KEYS.includes(o.platform)) parsed.errors.push(`--platform must be one of ${PLATFORM_KEYS.join(', ')}`);
  for (const name of ['timeout', 'attempts']) {
    if (o[name] !== undefined && !/^[1-9]\d*$/.test(o[name])) parsed.errors.push(`--${name} must be a positive integer`);
  }
  if (parsed.errors.length > 0) {
    for (const error of parsed.errors) logError(`error: ${error}`);
    logError(USAGE);
    return 2;
  }
  const quiet = parsed.flags.has('quiet');
  const say = quiet ? () => {} : log;
  const root = o.root ? resolve(o.root) : REPO_ROOT;
  const manifestPath = command === 'verify' ? resolve(rest[0]) : resolve(o.manifest ?? resolve(root, MANIFEST_PATH));
  const platform = o.platform ?? platformKey({ platform: deps.platform ?? process.platform, arch: deps.arch ?? process.arch });
  let workDir = o.work ? resolve(o.work) : null;
  const ownWork = workDir === null && ['resolve', 'verify'].includes(command);
  try {
    if (command === 'serve') {
      const served = await startFileServer({ dir: resolve(o.dir), port: Number(o.port), log: say });
      log(`serving ${resolve(o.dir)} on ${served.url}`);
      const closed = new Promise((done) => served.server.once('close', done));
      const stop = () => {
        say('stopping');
        served.close();
      };
      if (deps.signals !== false) {
        process.once('SIGINT', stop);
        process.once('SIGTERM', stop);
      }
      deps.onServe?.(served);
      await closed;
      if (deps.signals !== false) {
        process.off('SIGINT', stop);
        process.off('SIGTERM', stop);
      }
      return 0;
    }
    const manifest = loadManifest(manifestPath);
    if (command === 'status') {
      if (o.dir !== undefined && platform === null) {
        log(`Local AI is not available for ${process.platform}/${process.arch}`);
        return 1;
      }
      const report = statusReport({ manifest, manifestPath, dir: o.dir, platform, root });
      for (const line of report.lines) say(line);
      return report.ok ? 0 : 1;
    }
    if (command === 'mirror') {
      if (platform === null) throw new UsageError(`Local AI is not available for ${deps.platform ?? process.platform}/${deps.arch ?? process.arch}; pass --platform <key>`);
      const schema = validateManifest(manifest, root);
      if (!schema.valid) throw new UsageError(`${manifestPath} is not a resolved manifest: ${schema.errors.map((e) => `${e.path}: ${e.message}`).join('; ')}`);
      const mirrored = mirrorManifest(manifest, platform, o.base);
      const files = mirrorFiles(resolve(o.dir), manifest, platform);
      const out = resolve(o.out);
      writeJsonAtomic(out, mirrored);
      say(`  ${platform}: ${mirrored.runtime.platforms[platform].url} -> ${files.archive}`);
      say(`  model: ${mirrored.model.url} -> ${files.model}`);
      log(`wrote ${out} (mirror of ${manifestPath} for ${platform}; local use only, never commit it)`);
      return 0;
    }
    const client = new Client({
      fetch: deps.fetch ?? fetch,
      userAgent: userAgent(root),
      githubToken: env.GITHUB_TOKEN ?? '',
      timeoutMs: o.timeout ? Number(o.timeout) : DEFAULT_TIMEOUT_MS,
      attempts: o.attempts ? Number(o.attempts) : DEFAULT_ATTEMPTS,
      delayMs: deps.delayMs ?? 1000,
      log: say,
    });
    if (ownWork) workDir = mkdtempSync(join(tmpdir(), 'vanta-local-ai-'));
    if (command === 'resolve') {
      say(`Resolving ${manifestPath}`);
      const resolved = await resolveManifest({ template: manifest, client, workDir, skipModelDownload: parsed.flags.has('skip-model-download'), root, now: deps.now ?? new Date(), log: say });
      const out = o.out ? resolve(o.out) : manifestPath;
      writeJsonAtomic(out, resolved);
      log(`wrote ${out} (resolved ${resolved.resolvedAt})`);
      return 0;
    }
    if (command === 'verify') {
      say(`Verifying ${manifestPath}`);
      const report = await verifyManifest({ manifest, client, workDir, full: parsed.flags.has('full'), root, log: say });
      if (!report.ok) {
        for (const problem of report.problems) logError(`  ${problem}`);
        logError(`${manifestPath} does not match its sources.`);
        return 1;
      }
      log(`${manifestPath}: runtime archives and model verified${parsed.flags.has('full') ? ' (model downloaded)' : ' (model through the Hugging Face API)'}.`);
      return 0;
    }
    // prepare
    if (platform === null) throw new UsageError(`Local AI is not available for ${deps.platform ?? process.platform}/${deps.arch ?? process.arch}; pass --platform <key> to prepare another platform`);
    say(`Preparing the Local AI for ${platform} in ${resolve(o.dir)}`);
    const result = await prepareInstall({
      manifest, dir: resolve(o.dir), platform, client, root, now: deps.now ?? new Date(), keepDownloads: parsed.flags.has('keep-downloads'), log: say,
    });
    log(`${result.paths.root}: Local AI ${manifest.runtime.tag} + ${manifest.model.file} ready for ${platform} (${result.downloaded.length} downloaded, ${result.skipped.length} already present${result.kept.length > 0 ? `, ${result.kept.length} kept under downloads/` : ''})`);
    return 0;
  } catch (error) {
    if (error instanceof UsageError) {
      logError(`error: ${error.message}`);
      return 2;
    }
    if (error instanceof CheckError) {
      logError(`error: ${error.message}`);
      return 1;
    }
    logError(`error: ${error.stack ?? error.message}`);
    return 1;
  } finally {
    if (ownWork && workDir) rmSync(workDir, { recursive: true, force: true });
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(await main(process.argv.slice(2)));
}
