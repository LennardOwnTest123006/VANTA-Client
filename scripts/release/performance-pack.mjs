#!/usr/bin/env node
/**
 * Resolves the Performance pack on Modrinth for the mods bundle of a client release and downloads the jars that may be
 * redistributed, together with their licence texts.
 *
 *   node scripts/release/performance-pack.mjs --out <dir> [--fabric-api-jar <jar>] [--game-version 1.21.11] [--root <repo>]
 *
 * The pack members are the slugs of core/src/main/java/dev/vanta/core/modrinth/PerformancePack.java (parsed from the
 * `new Item("<slug>"` lines, so the bundle and the in-game card can never drift apart). For every slug the script reads
 * GET /v2/project/<slug> and /v2/project/<slug>/version?loaders=["fabric"]&game_versions=[<game version>] and picks the
 * version by the rule the whole repository uses (ci.yml, core VersionSelector, launcher): the newest release, else the
 * newest beta, else the newest of any type, by date_published, among the versions that list the loader and the game
 * version and have a primary file with a SHA-512.
 *
 * Licences are checked against an allow-list (LICENSE_ALLOW_LIST). A member under any other licence (EntityCulling's
 * LicenseRef-tr7zw-Protective-License forbids redistribution) is excluded from the bundle with a printed reason and
 * listed in performance-pack.json; that is never an error, the game offers it for download. Required dependencies
 * with a version id are followed: when a member requires another member (Iris -> Sodium), the selected version of
 * that member must be exactly the required one, otherwise the script fails with a clear message. A dependency on
 * Fabric API (project P7dR8mSH) is satisfied by the Fabric API jar that is already in the bundle.
 *
 * Every primary file is downloaded to <out>/mods/<filename> and verified against the published size and SHA-512 (a
 * mismatching file is deleted and the script fails). Every licence text is fetched (license.url, with
 * github.com/<owner>/<repo>/blob/<ref>/<path> rewritten to raw.githubusercontent.com; for an SPDX id without a URL the
 * text comes from the SPDX license-list-data repository, and for the LGPL the GPL it refers to is appended; the Fabric
 * API notice is LICENSE-fabric-api from its jar). The script fails when a licence text cannot be fetched, so the
 * bundle never ships without notices. Written into <out>:
 *
 *   mods/<jar>…                  the verified jars
 *   THIRD-PARTY-LICENSES.txt     per mod: title, version, file, licence id/name/url, source, authors, the full text
 *   performance-pack.json        shared/schemas/performance-pack.schema.json
 *   PERFORMANCE-PACK.txt         the human-readable summary (job summary, release notes, INSTALL.txt)
 *
 * No version number or hash is ever typed into the repository: everything is resolved live and written only into the
 * built zip. Exit codes: 0 ok, 1 resolution/download/licence failure, 2 usage error.
 */
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from './lib/args.mjs';
import { REPO_ROOT, parseProperties, readToolchain, writeJsonAtomic, writeTextAtomic } from './lib/repo.mjs';
import { openZip } from './lib/zip.mjs';
import { validateWithSchemaFile } from './validate-json.mjs';

const USAGE = 'Usage: node scripts/release/performance-pack.mjs --out <dir> [--fabric-api-jar <jar>] [--game-version <mc version>] [--root <repo root>]';

/** Modrinth API v2 base (same as core ModrinthConstants.API_BASE). */
export const API_BASE = 'https://api.modrinth.com/v2/';
/** Modrinth project id of Fabric API (core ModrinthConstants.FABRIC_API_PROJECT_ID). */
export const FABRIC_API_PROJECT_ID = 'P7dR8mSH';
/** Where the texts of SPDX licences come from when a project publishes no licence URL. */
export const SPDX_TEXT_BASE = 'https://raw.githubusercontent.com/spdx/license-list-data/main/text/';
/** The PolyForm Shield terms Sodium is under; passed on in the notices. */
export const POLYFORM_SHIELD_URL = 'https://polyformproject.org/licenses/shield/1.0.0';
export const POLYFORM_SHIELD_ID = 'LicenseRef-Polyform-Shield-1.0.0';

/**
 * Licences under which a pack member may be redistributed in the bundle (SPDX ids as Modrinth reports them). Any other
 * licence excludes the member from the zip; the game then offers it as a download.
 */
export const LICENSE_ALLOW_LIST = new Set([
  'MIT',
  'Apache-2.0',
  'LGPL-3.0-only',
  'LGPL-3.0-or-later',
  'LGPL-2.1-or-later',
  'BSD-2-Clause',
  'BSD-3-Clause',
  POLYFORM_SHIELD_ID,
]);

/** Licences whose text refers to another licence that must travel with it. */
const COMPANION_TEXTS = {
  'LGPL-3.0-only': ['GPL-3.0-only'],
  'LGPL-3.0-or-later': ['GPL-3.0-only'],
  'LGPL-2.1-or-later': ['GPL-2.0-only'],
  'LGPL-2.1-only': ['GPL-2.0-only'],
};

const PACK_SOURCE = join('core', 'src', 'main', 'java', 'dev', 'vanta', 'core', 'modrinth', 'PerformancePack.java');
const MAX_ATTEMPTS = 3;

export class PackError extends Error {}

/** Slugs of the pack members, in pack order, from the `new Item("<slug>"` lines of PerformancePack.java. */
export function parsePackSlugs(javaSource) {
  const slugs = [];
  for (const match of javaSource.matchAll(/new Item\(\s*"([a-z0-9][a-z0-9_-]*)"/g)) slugs.push(match[1]);
  if (slugs.length === 0) throw new PackError('no `new Item("<slug>"` lines found in PerformancePack.java');
  if (new Set(slugs).size !== slugs.length) throw new PackError(`duplicate slug in PerformancePack.java: ${slugs.join(', ')}`);
  return slugs;
}

/** Reads the slugs from the repository. */
export function readPackSlugs(root = REPO_ROOT) {
  return parsePackSlugs(readFileSync(join(root, PACK_SOURCE), 'utf8'));
}

/** The User-Agent Modrinth asks for (core ModrinthConstants.userAgent); the client version is mod_version. */
export function userAgent(root = REPO_ROOT) {
  const file = join(root, 'client', 'gradle.properties');
  const version = existsSync(file) ? parseProperties(readFileSync(file, 'utf8')).mod_version ?? 'dev' : 'dev';
  return `LennardOwnTest123006/VANTA-Client/${version} (https://github.com/LennardOwnTest123006/VANTA-Client)`;
}

/** Same rule as core ModrinthFile.isSafeFilename. */
export function isSafeFilename(name) {
  if (typeof name !== 'string' || name.trim() === '' || name.length > 200 || name.startsWith('.')) return false;
  if (/[/\\:]/.test(name) || name.includes('..')) return false;
  // eslint-disable-next-line no-control-regex
  return !/[\x00-\x1f\x7f<>"|?*]/.test(name);
}

const CHANNEL_RANK = { release: 0, beta: 1, alpha: 2 };

/** The primary file of a version (the first file when none is marked primary). */
export function primaryFile(version) {
  const files = Array.isArray(version.files) ? version.files : [];
  return files.find((f) => f.primary) ?? files[0] ?? null;
}

/** True when the version lists the game version and the fabric loader and its primary file has a SHA-512. */
export function isInstallable(version, gameVersion) {
  if (!Array.isArray(version.game_versions) || !version.game_versions.includes(gameVersion)) return false;
  if (!Array.isArray(version.loaders) || !version.loaders.some((l) => String(l).toLowerCase() === 'fabric')) return false;
  const file = primaryFile(version);
  return Boolean(file && /^[0-9a-fA-F]{128}$/.test(file.hashes?.sha512 ?? '') && isSafeFilename(file.filename));
}

/**
 * The shared selection rule: newest release, else newest beta, else newest of any type (by date_published), among the
 * installable versions for the game version and loader fabric. Returns null when nothing fits.
 */
export function selectVersion(versions, gameVersion) {
  const candidates = versions.filter((v) => isInstallable(v, gameVersion));
  candidates.sort((a, b) => {
    const rank = (CHANNEL_RANK[String(a.version_type).toLowerCase()] ?? 3) - (CHANNEL_RANK[String(b.version_type).toLowerCase()] ?? 3);
    if (rank !== 0) return rank;
    return Date.parse(b.date_published ?? 0) - Date.parse(a.date_published ?? 0);
  });
  return candidates[0] ?? null;
}

/** github.com/<owner>/<repo>/blob/<ref>/<path> → raw.githubusercontent.com/<owner>/<repo>/<ref>/<path>; else unchanged. */
export function rawLicenseUrl(url) {
  const m = /^https?:\/\/(?:www\.)?github\.com\/([^/]+)\/([^/]+)\/blob\/(.+)$/.exec(url ?? '');
  return m ? `https://raw.githubusercontent.com/${m[1]}/${m[2]}/${m[3]}` : url;
}

/** The URLs whose texts make up the notice of a licence: the project's URL or the SPDX text, plus referenced licences. */
export function licenseTextUrls(license) {
  const id = license?.id ?? '';
  const urls = [];
  if (license?.url) urls.push({ label: id, url: rawLicenseUrl(license.url) });
  else if (id && !id.startsWith('LicenseRef-')) urls.push({ label: id, url: `${SPDX_TEXT_BASE}${id}.txt` });
  for (const companion of COMPANION_TEXTS[id] ?? []) urls.push({ label: companion, url: `${SPDX_TEXT_BASE}${companion}.txt` });
  return urls;
}

function looksLikeHtml(text) {
  return /^\s*(<!doctype html|<html)/i.test(text.slice(0, 512));
}

/** Modrinth JSON and licence downloads through one injectable fetch with retries on 5xx and network errors. */
export class Client {
  constructor({ fetch: fetchImpl, userAgent: ua, delayMs = 1000, log = () => {} }) {
    if (typeof fetchImpl !== 'function') throw new TypeError('a fetch implementation is required');
    this.fetch = fetchImpl;
    this.ua = ua;
    this.delayMs = delayMs;
    this.log = log;
  }

  async request(url, accept) {
    let last;
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
      try {
        const response = await this.fetch(url, { headers: { 'User-Agent': this.ua, Accept: accept }, redirect: 'follow' });
        if (response.status >= 500 && attempt < MAX_ATTEMPTS) {
          last = new PackError(`${url}: HTTP ${response.status}`);
        } else {
          return response;
        }
      } catch (error) {
        if (attempt === MAX_ATTEMPTS) throw new PackError(`${url}: ${error.message}`);
        last = error;
      }
      this.log(`retrying ${url} after ${last.message}`);
      if (this.delayMs > 0) await new Promise((r) => setTimeout(r, this.delayMs * attempt));
    }
    throw last;
  }

  async json(path) {
    const url = new URL(path, API_BASE).toString();
    const response = await this.request(url, 'application/json');
    if (!response.ok) throw new PackError(`${url}: HTTP ${response.status}`);
    return response.json();
  }

  /** Like json() but null on 404. */
  async jsonOrNull(path) {
    const url = new URL(path, API_BASE).toString();
    const response = await this.request(url, 'application/json');
    if (response.status === 404) return null;
    if (!response.ok) throw new PackError(`${url}: HTTP ${response.status}`);
    return response.json();
  }

  async text(url) {
    const response = await this.request(url, 'text/plain, */*');
    if (!response.ok) throw new PackError(`${url}: HTTP ${response.status}`);
    const text = await response.text();
    if (text.trim() === '') throw new PackError(`${url}: empty response`);
    if (looksLikeHtml(text)) throw new PackError(`${url}: returned an HTML page instead of the licence text`);
    return text;
  }

  async bytes(url) {
    const response = await this.request(url, 'application/java-archive, application/octet-stream, */*');
    if (!response.ok) throw new PackError(`${url}: HTTP ${response.status}`);
    return Buffer.from(await response.arrayBuffer());
  }
}

function versionsPath(projectId, gameVersion) {
  const loaders = encodeURIComponent(JSON.stringify(['fabric']));
  const games = encodeURIComponent(JSON.stringify([gameVersion]));
  return `project/${encodeURIComponent(projectId)}/version?loaders=${loaders}&game_versions=${games}`;
}

/** Authors of a project from its team (owner first); empty on any failure, never fatal. */
async function authorsOf(client, projectId, log) {
  try {
    const members = await client.json(`project/${encodeURIComponent(projectId)}/members`);
    if (!Array.isArray(members)) return [];
    const sorted = [...members].sort((a, b) => (b.role === 'Owner') - (a.role === 'Owner'));
    return sorted.map((m) => m.user?.name || m.user?.username).filter(Boolean);
  } catch (error) {
    log(`note: team of ${projectId} not available (${error.message}); the notice names the source repository instead`);
    return [];
  }
}

/**
 * Resolves projects, versions, licences and dependencies. Downloads nothing.
 * @returns {Promise<{ items: object[], excluded: object[], notes: string[] }>}
 */
export async function resolvePack({ slugs, gameVersion, client, log = () => {} }) {
  const items = [];
  const excluded = [];
  const notes = [];
  for (const slug of slugs) {
    const project = await client.json(`project/${encodeURIComponent(slug)}`);
    if (!project?.id) throw new PackError(`${slug}: project without id`);
    const title = project.title ?? slug;
    const license = project.license ?? {};
    const licenseId = license.id ?? '';
    if (!LICENSE_ALLOW_LIST.has(licenseId)) {
      const reason = `licence ${licenseId || '(none)'}${license.name ? ` (${license.name})` : ''} is not in the redistribution allow-list; the game offers ${title} as a download instead`;
      log(`excluded from the bundle: ${title} (${slug}): ${reason}`);
      excluded.push({ slug, title, projectId: project.id, license: licenseId || '(none)', reason });
      continue;
    }
    const versions = await client.json(versionsPath(project.id, gameVersion));
    if (!Array.isArray(versions)) throw new PackError(`${slug}: version list is not an array`);
    const version = selectVersion(versions, gameVersion);
    if (!version) throw new PackError(`${title} (${slug}) has no Fabric version for Minecraft ${gameVersion} with a SHA-512 primary file`);
    const file = primaryFile(version);
    items.push({
      slug,
      title,
      projectId: project.id,
      versionId: version.id,
      versionNumber: version.version_number ?? version.id,
      versionType: version.version_type ?? 'release',
      publishedAt: version.date_published ?? null,
      file: file.filename,
      url: file.url,
      size: Number(file.size) || 0,
      sha512: file.hashes.sha512.toLowerCase(),
      license: { id: licenseId, name: license.name ?? null, url: license.url ?? null },
      sourceUrl: project.source_url ?? null,
      dependencies: Array.isArray(version.dependencies) ? version.dependencies : [],
      authors: await authorsOf(client, project.id, log),
    });
    log(`${title}: ${items.at(-1).versionNumber} (${version.id}, ${version.version_type}) ${file.filename}, licence ${licenseId}`);
  }
  await checkDependencies({ items, excluded, gameVersion, client, notes });
  return { items, excluded, notes };
}

/** Required dependencies: Fabric API is bundled; another member must be the exactly required version; anything else fails. */
async function checkDependencies({ items, excluded, gameVersion, client, notes }) {
  let fabricApiNoted = false;
  for (const item of items) {
    for (const dep of item.dependencies) {
      if (dep.dependency_type !== 'required') continue;
      let projectId = dep.project_id ?? null;
      let pinned = null;
      if (dep.version_id) {
        pinned = await client.jsonOrNull(`version/${encodeURIComponent(dep.version_id)}`);
        if (pinned?.project_id) projectId = projectId ?? pinned.project_id;
      }
      if (!projectId) throw new PackError(`${item.title} ${item.versionNumber} requires a file Modrinth does not identify (${dep.file_name ?? 'no project id'})`);
      if (projectId === FABRIC_API_PROJECT_ID) {
        if (!fabricApiNoted) {
          notes.push('Fabric API (Apache-2.0) is in the bundle as well; the pack mods that require it are satisfied by that jar.');
          fabricApiNoted = true;
        }
        continue;
      }
      const target = items.find((i) => i.projectId === projectId);
      if (!target) {
        const left = excluded.find((e) => e.projectId === projectId) ?? null;
        throw new PackError(`${item.title} ${item.versionNumber} requires project ${projectId}, which is ${left ? `${left.title}, excluded from the bundle by its licence` : 'not part of the Performance pack'}; the bundle would be incomplete`);
      }
      if (dep.version_id && target.versionId !== dep.version_id) {
        throw new PackError(`${item.title} ${item.versionNumber} requires ${target.title} version ${dep.version_id}${pinned ? ` (${pinned.version_number})` : ''}, but the newest ${target.title} for Minecraft ${gameVersion} is ${target.versionNumber} (${target.versionId}); the two cannot be bundled together until their authors release matching versions`);
      }
      notes.push(dep.version_id
        ? `${item.title} ${item.versionNumber} requires ${target.title} version ${dep.version_id} (${target.versionNumber}); the bundled ${target.title} is exactly that version.`
        : `${item.title} ${item.versionNumber} requires ${target.title}; the bundled ${target.title} ${target.versionNumber} satisfies it.`);
    }
  }
}

/** Downloads one item to <out>/mods and verifies size and SHA-512; a mismatching file is deleted. */
export async function downloadItem(client, item, modsDir) {
  mkdirSync(modsDir, { recursive: true });
  const target = join(modsDir, item.file);
  const bytes = await client.bytes(item.url);
  writeFileSync(target, bytes);
  const sha512 = createHash('sha512').update(bytes).digest('hex');
  if ((item.size > 0 && bytes.length !== item.size) || sha512 !== item.sha512) {
    rmSync(target, { force: true });
    throw new PackError(`${item.file}: downloaded ${bytes.length} bytes with SHA-512 ${sha512}, Modrinth published ${item.size} bytes with SHA-512 ${item.sha512}; the file was deleted`);
  }
  return { path: target, size: bytes.length, sha512 };
}

/** Fetches the licence texts of an item: [{ label, url, text }]. Fails when any text cannot be fetched. */
export async function fetchLicenseTexts(client, item) {
  const urls = licenseTextUrls(item.license);
  if (urls.length === 0) throw new PackError(`${item.title}: licence ${item.license.id || '(none)'} has no URL and is not an SPDX id, so its text cannot be fetched`);
  const texts = [];
  for (const { label, url } of urls) {
    try {
      texts.push({ label, url, text: await client.text(url) });
    } catch (error) {
      throw new PackError(`${item.title}: licence text ${label} could not be fetched: ${error.message}`);
    }
  }
  return texts;
}

/** Fabric API's own notice (LICENSE-fabric-api) and version (fabric.mod.json) from the jar. */
export function fabricApiNotice(jarPath) {
  const zip = openZip(jarPath);
  if (!zip.has('fabric.mod.json')) throw new PackError(`${jarPath} has no fabric.mod.json`);
  if (!zip.has('LICENSE-fabric-api')) throw new PackError(`${jarPath} does not contain LICENSE-fabric-api`);
  const mod = JSON.parse(zip.read('fabric.mod.json').toString('utf8'));
  return {
    title: 'Fabric API',
    versionNumber: mod.version ?? '',
    file: jarPath.split(/[\\/]/).at(-1),
    license: { id: mod.license ?? 'Apache-2.0', name: 'Apache License 2.0', url: null },
    sourceUrl: 'https://github.com/FabricMC/fabric',
    text: zip.read('LICENSE-fabric-api').toString('utf8'),
  };
}

function humanSize(bytes) {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${bytes} B`;
}

const RULE = '-'.repeat(78);

/** THIRD-PARTY-LICENSES.txt. */
export function renderNotices({ gameVersion, resolvedAt, items, fabricApi }) {
  const out = [];
  out.push('Third-party notices for the VANTA Client mods bundle');
  out.push('===================================================');
  out.push('');
  out.push(`Minecraft ${gameVersion} (Fabric); versions resolved on Modrinth at ${resolvedAt}.`);
  out.push('');
  out.push('The Performance pack mods and Fabric API in the mods/ folder are separate third-party projects, redistributed');
  out.push('unchanged under their own licences, which are reproduced below. VANTA Client itself is MIT licensed (see the');
  out.push('LICENSE file of the VANTA repository). Each section names the file in this bundle it applies to.');
  out.push('');
  for (const item of items) {
    out.push(RULE);
    out.push(`${item.title} ${item.versionNumber}`);
    out.push(`File:     mods/${item.file}`);
    out.push(`Licence:  ${item.license.id}${item.license.name ? ` (${item.license.name})` : ''}`);
    if (item.license.url) out.push(`          ${item.license.url}`);
    if (item.license.id === POLYFORM_SHIELD_ID) out.push(`          ${POLYFORM_SHIELD_URL}`);
    out.push(`Source:   ${item.sourceUrl ?? 'see the Modrinth page'}`);
    out.push(`Authors:  ${item.authors.length > 0 ? `${item.authors.join(', ')} (Modrinth project team)` : 'see the source repository'}`);
    out.push(`Modrinth: https://modrinth.com/mod/${item.slug}`);
    out.push('Copyright: held by the authors; see the licence text and the source repository.');
    out.push('');
    for (const { label, url, text } of item.texts) {
      if (label !== item.license.id) out.push(`[${label}, referenced by ${item.license.id}; from ${url}]`);
      out.push(text.replace(/\r\n/g, '\n').trimEnd());
      out.push('');
    }
  }
  if (fabricApi) {
    out.push(RULE);
    out.push(`${fabricApi.title} ${fabricApi.versionNumber}`);
    out.push(`File:     mods/${fabricApi.file}`);
    out.push(`Licence:  ${fabricApi.license.id} (${fabricApi.license.name}); LICENSE-fabric-api from the jar`);
    out.push(`Source:   ${fabricApi.sourceUrl}`);
    out.push('Modrinth: https://modrinth.com/mod/fabric-api');
    out.push('');
    out.push(fabricApi.text.replace(/\r\n/g, '\n').trimEnd());
    out.push('');
  }
  return `${out.join('\n')}\n`;
}

/** PERFORMANCE-PACK.txt: plain text that also reads as Markdown (job summary, release notes). */
export function renderSummary({ gameVersion, resolvedAt, items, excluded, notes }) {
  const out = [];
  out.push(`Performance pack for Minecraft ${gameVersion} (Fabric), resolved on Modrinth at ${resolvedAt}.`);
  out.push('');
  out.push('Included in the mods bundle (mods/):');
  for (const item of items) {
    out.push(`- ${item.title} ${item.versionNumber} (${item.versionType}, version id ${item.versionId}): mods/${item.file}, ${humanSize(item.size)}, licence ${item.license.id}${item.license.name ? ` (${item.license.name})` : ''}`);
  }
  out.push('');
  out.push('Not in the bundle:');
  if (excluded.length === 0) out.push('- none');
  for (const e of excluded) {
    out.push(`- ${e.title}: ${e.reason}. It is downloaded in the game with one click under Mods & Shaders (Performance pack card).`);
  }
  out.push('');
  out.push('Notes:');
  for (const note of notes) out.push(`- ${note}`);
  if (items.some((i) => i.license.id === POLYFORM_SHIELD_ID)) {
    const names = items.filter((i) => i.license.id === POLYFORM_SHIELD_ID).map((i) => i.title).join(', ');
    out.push(`- ${names}: PolyForm Shield 1.0.0 (${POLYFORM_SHIELD_URL}); redistribution is allowed and the terms are passed on in THIRD-PARTY-LICENSES.txt. A person reads the terms before each release.`);
  }
  out.push('- The full licence texts are in THIRD-PARTY-LICENSES.txt; the machine-readable list is performance-pack.json.');
  return `${out.join('\n')}\n`;
}

/** performance-pack.json (shared/schemas/performance-pack.schema.json). */
export function toJson({ gameVersion, resolvedAt, items, excluded }) {
  return {
    schemaVersion: 1,
    gameVersion,
    resolvedAt,
    items: items.map((i) => ({
      slug: i.slug,
      title: i.title,
      projectId: i.projectId,
      versionId: i.versionId,
      versionNumber: i.versionNumber,
      file: i.file,
      size: i.size,
      sha512: i.sha512,
      license: { id: i.license.id, name: i.license.name, url: i.license.url },
      sourceUrl: i.sourceUrl,
    })),
    excluded: excluded.map((e) => ({ slug: e.slug, title: e.title, license: e.license, reason: e.reason })),
  };
}

/**
 * Resolves, downloads, fetches the notices and writes every output file.
 * @param {{ out: string, slugs: string[], gameVersion: string, client: Client, fabricApiJar?: string, root?: string,
 *           now?: () => Date, log?: (line: string) => void }} params
 */
export async function buildPack({ out, slugs, gameVersion, client, fabricApiJar, root = REPO_ROOT, now = () => new Date(), log = () => {} }) {
  const resolvedAt = now().toISOString().replace(/\.\d{3}Z$/, 'Z');
  const { items, excluded, notes } = await resolvePack({ slugs, gameVersion, client, log });
  if (items.length === 0) throw new PackError('no Performance pack member may be bundled');
  const fabricApi = fabricApiJar ? fabricApiNotice(fabricApiJar) : null;
  mkdirSync(out, { recursive: true });
  const modsDir = join(out, 'mods');
  for (const item of items) {
    item.texts = await fetchLicenseTexts(client, item);
    const downloaded = await downloadItem(client, item, modsDir);
    log(`downloaded mods/${item.file} (${downloaded.size} bytes, SHA-512 verified)`);
  }
  const data = toJson({ gameVersion, resolvedAt, items, excluded });
  const schema = join(root, 'shared', 'schemas', 'performance-pack.schema.json');
  if (existsSync(schema)) {
    const result = validateWithSchemaFile(schema, data);
    if (!result.valid) throw new PackError(`performance-pack.json is invalid: ${result.errors.map((e) => `${e.path}: ${e.message}`).join('; ')}`);
  }
  writeJsonAtomic(join(out, 'performance-pack.json'), data);
  writeTextAtomic(join(out, 'THIRD-PARTY-LICENSES.txt'), renderNotices({ gameVersion, resolvedAt, items, fabricApi }));
  writeTextAtomic(join(out, 'PERFORMANCE-PACK.txt'), renderSummary({ gameVersion, resolvedAt, items, excluded, notes }));
  return { items, excluded, notes, resolvedAt };
}

/** CLI entry point. `deps.fetch` defaults to the global fetch. */
export async function main(argv, deps = {}) {
  const log = deps.log ?? console.log;
  const logError = deps.logError ?? console.error;
  const parsed = parseArgs(argv, { values: ['out', 'fabric-api-jar', 'game-version', 'root'], flags: ['help'], aliases: { h: 'help' } });
  if (parsed.flags.has('help')) {
    log(USAGE);
    return 0;
  }
  if (parsed.positional.length > 0) parsed.errors.push(`unexpected argument '${parsed.positional[0]}'`);
  if (!parsed.options.out) parsed.errors.push('--out is required');
  if (parsed.errors.length > 0) {
    for (const error of parsed.errors) logError(`error: ${error}`);
    logError(USAGE);
    return 2;
  }
  const o = parsed.options;
  const root = o.root ? resolve(o.root) : REPO_ROOT;
  try {
    const gameVersion = o['game-version'] ?? readToolchain(root).minecraftVersion;
    const slugs = readPackSlugs(root);
    const client = deps.client ?? new Client({ fetch: deps.fetch ?? globalThis.fetch, userAgent: userAgent(root), delayMs: deps.delayMs ?? 1000, log });
    log(`Performance pack for Minecraft ${gameVersion}: ${slugs.join(', ')}`);
    const result = await buildPack({
      out: resolve(o.out), slugs, gameVersion, client, root, log, now: deps.now,
      fabricApiJar: o['fabric-api-jar'] ? resolve(o['fabric-api-jar']) : undefined,
    });
    log(`bundled ${result.items.length} of ${slugs.length} pack members into ${resolve(o.out)}; excluded: ${result.excluded.map((e) => e.slug).join(', ') || 'none'}`);
    log(resolve(o.out));
    return 0;
  } catch (error) {
    logError(`error: ${error.message}`);
    return 1;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(await main(process.argv.slice(2)));
}
