#!/usr/bin/env node
/**
 * Builds the full release zip VantaClient-<version>-Release.zip and its bundle manifest from the ALREADY PUBLISHED
 * files of one client release and one launcher release (shared/releases/<product>-<version>.json). Nothing is built
 * here: every file under client/ and launcher/ in the zip is downloaded from the GitHub Release the manifest names and
 * verified against the manifest's size and sha256 before it is copied. Run by .github/workflows/bundle.yml.
 *
 *   node scripts/release/build-bundle.mjs download --client-version <cv> --launcher-version <lv> --out <dir>
 *        fetches every file of both manifests plus each release's SHA256SUMS.txt and <product>-<version>.json into
 *        <dir>/client and <dir>/launcher, verifying size and sha256 of every manifest file (exit 1 on a mismatch or
 *        an unpublished manifest). A failed download is retried a few times.
 *   node scripts/release/build-bundle.mjs assemble --version <v> --client-version <cv> --launcher-version <lv> \
 *        --from <dir> --out <dir>
 *        builds <out>/VantaClient-<v>-Release.zip from the downloaded files and the repository documentation, with a
 *        generated README.txt and SHA256SUMS.txt, verifies the finished zip, writes <out>/SHA256SUMS.txt (the zip's
 *        own checksum) and prints the entries.
 *   node scripts/release/build-bundle.mjs manifest --version <v> --client-version <cv> --launcher-version <lv> \
 *        --zip <path> --url <https url> [--date YYYY-MM-DD] [--out shared/releases/bundles]
 *        writes shared/releases/bundles/vanta-<v>.json from the real zip (size, sha256, contents from the central
 *        directory), validated against shared/schemas/bundle-manifest.schema.json.
 *   node scripts/release/build-bundle.mjs verify <manifest.json> [--local <zip path>]
 *        downloads (or reads) the zip and checks its size and sha256 against the manifest.
 *   node scripts/release/build-bundle.mjs notes --version <v> --client-version <cv> --launcher-version <lv> \
 *        --manifest <path>
 *        prints the GitHub Release body (Markdown).
 *   common options: --root <repo root>, --timeout <ms per download>, --attempts <downloads per file>
 *
 * Zip layout (one top-level folder, paths relative to it):
 *   README.txt                            what is inside, which file to take on which system, how to verify
 *   CHANGELOG.md, LICENSE                 copies of the repository files
 *   SHA256SUMS.txt                        sha256 of every other file in the zip (sha256sum -c format)
 *   LOCAL-AI.txt                          what Vanta Nexus downloads on first use of the Local AI (runtime and model:
 *                                         files, sizes, sources, SHA-256, licences), generated from the manifest
 *   release-notes/<product>-<version>.md  copies of website/content/changelog/<product>-<version>.md
 *   docs/*.md                             copies of every docs/*.md
 *   local-ai/local-ai.json                copy of shared/local-ai/local-ai.json (must be resolved, never the template)
 *   client/                               every file of the client manifest, its SHA256SUMS.txt, client-<cv>.json
 *   launcher/                             every file of the launcher manifest, its SHA256SUMS.txt, launcher-<lv>.json
 *
 * Exit codes: 0 ok, 1 a download, check or verification failed, 2 usage or manifest error.
 */
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { once } from 'node:events';
import { copyFileSync, createWriteStream, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, renameSync, rmSync, statSync } from 'node:fs';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isDeepStrictEqual } from 'node:util';
import { parseArgs } from './lib/args.mjs';
import { hashFile, isSha256Hex } from './lib/hash.mjs';
import { REPO_ROOT, isSemVer, readJson, todayUtc, writeJsonAtomic, writeTextAtomic } from './lib/repo.mjs';
import { wrapText } from './lib/text.mjs';
import { openZip, readZipEntry } from './lib/zip.mjs';
import { MANIFEST_PATH as LOCAL_AI_MANIFEST_PATH, loadLocalAiManifest, localAiNote } from './local-ai.mjs';
import { checkDirectory, formatSize, markdownCell, releaseAssets } from './release-assets.mjs';
import { validate } from './validate-json.mjs';
import { hashUrl } from './verify-manifest.mjs';

export { wrapText };

const USAGE = `Usage: node scripts/release/build-bundle.mjs <command> [options]
  download  --client-version <cv> --launcher-version <lv> --out <dir>
  assemble  --version <v> [--client-version <cv>] [--launcher-version <lv>] --from <dir> --out <dir>
  manifest  --version <v> [--client-version <cv>] [--launcher-version <lv>] --zip <path> --url <https url>
            [--date YYYY-MM-DD] [--out <dir, default shared/releases/bundles>]
  verify    <manifest.json> [--local <zip path>]
  notes     --version <v> [--client-version <cv>] [--launcher-version <lv>] --manifest <path>
  options:  --root <repository root> --timeout <ms per download> --attempts <downloads per file>
  --client-version and --launcher-version default to --version.`;

/** The website's download page, linked from README.txt and the release notes. */
export const WEBSITE_DOWNLOAD_URL = 'https://vanta-client.netlify.app/download';

const HTTPS_URL = /^https:\/\/\S+$/;

/** A check of the inputs or of the result failed (exit 1). */
export class CheckError extends Error {}

/** The command line or a repository file is unusable (exit 2). */
export class UsageError extends Error {}

export function bundleFolder(version) {
  return `VantaClient-${version}-Release`;
}

export function bundleZipName(version) {
  return `${bundleFolder(version)}.zip`;
}

export function bundleTag(version) {
  return `v${version}`;
}

export function bundleManifestName(version) {
  return `vanta-${version}.json`;
}

/** Files attached to a component release that are not listed in its manifest's files[]. */
function releaseExtras(product, version) {
  return ['SHA256SUMS.txt', `${product}-${version}.json`];
}

function requireSemVer(value, what) {
  if (!isSemVer(value)) throw new UsageError(`${what} '${value}' is not a SemVer version`);
  return value;
}

function sleep(ms) {
  return new Promise((done) => setTimeout(done, ms));
}

/** True when the entry carries an https URL, a size and a digest (the release workflow filled it in). */
export function isPublishedFile(file) {
  return typeof file.downloadUrl === 'string' && HTTPS_URL.test(file.downloadUrl)
    && Number.isInteger(file.size) && file.size > 0 && isSha256Hex(file.sha256);
}

/**
 * Reads shared/releases/<product>-<version>.json, validates it against the release schema and requires every file
 * to be published (the bundle only ever carries files that exist on a GitHub Release).
 * @returns {{ path: string, manifest: object }}
 */
export function loadPublishedManifest(root, product, version) {
  requireSemVer(version, `${product} version`);
  const rel = `shared/releases/${product}-${version}.json`;
  const path = resolve(root, rel);
  if (!existsSync(path)) throw new UsageError(`${rel} is missing; the ${product} ${version} manifest must be committed and published first`);
  const manifest = readJson(path);
  const schema = readJson(resolve(root, 'shared', 'schemas', 'release-manifest.schema.json'));
  const result = validate(schema, manifest, { baseDir: resolve(root, 'shared', 'schemas') });
  if (!result.valid) {
    throw new UsageError(`${rel} does not match the release manifest schema: ${result.errors.map((e) => `${e.path}: ${e.message}`).join('; ')}`);
  }
  if (manifest.product !== product || manifest.version !== version) {
    throw new UsageError(`${rel} describes ${manifest.product} ${manifest.version}, expected ${product} ${version}`);
  }
  const unpublished = manifest.files.filter((file) => !isPublishedFile(file)).map((file) => file.name);
  if (unpublished.length > 0) {
    throw new CheckError(`${rel} is not published: ${unpublished.join(', ')} ${unpublished.length === 1 ? 'has' : 'have'} no downloadUrl, size and sha256`);
  }
  return { path, manifest };
}

/**
 * The URL folder every file of a published manifest is downloaded from (the GitHub Release's download folder,
 * ending in '/'); SHA256SUMS.txt and <product>-<version>.json live there too.
 */
export function releaseBaseUrl(manifest) {
  const bases = new Set();
  for (const file of manifest.files) {
    const slash = file.downloadUrl.lastIndexOf('/');
    if (file.downloadUrl.slice(slash + 1) !== file.name) {
      throw new CheckError(`${manifest.product} ${manifest.version}: the downloadUrl of ${file.name} does not end in its name (${file.downloadUrl})`);
    }
    bases.add(file.downloadUrl.slice(0, slash + 1));
  }
  if (bases.size !== 1) {
    throw new CheckError(`${manifest.product} ${manifest.version}: the files are not served from one folder (${[...bases].join(', ')})`);
  }
  return [...bases][0];
}

/** The GitHub release page behind a release download folder, or the folder itself when it is not one. */
export function releasePageUrl(baseUrl) {
  const match = /^(https:\/\/github\.com\/[^/]+\/[^/]+)\/releases\/download\/([^/]+)\/$/.exec(baseUrl);
  return match ? `${match[1]}/releases/tag/${match[2]}` : baseUrl;
}

/**
 * Parses a SHA256SUMS document (sha256sum format) into name -> digest.
 * @param {string} text
 * @returns {Map<string, string>}
 */
export function parseSums(text) {
  const sums = new Map();
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (line === '') continue;
    const match = /^([a-f0-9]{64}) [ *](.+)$/.exec(line);
    if (!match) throw new CheckError(`unexpected line in SHA256SUMS: ${JSON.stringify(line)}`);
    sums.set(match[2], match[1]);
  }
  return sums;
}

/** Requires a release's SHA256SUMS.txt to list exactly the manifest files with the manifest digests. */
export function checkReleaseSums(manifest, sumsText) {
  const sums = parseSums(sumsText);
  const problems = [];
  for (const file of manifest.files) {
    const listed = sums.get(file.name);
    if (listed === undefined) problems.push(`${file.name} is not listed`);
    else if (listed !== file.sha256) problems.push(`${file.name} is listed with ${listed}, the manifest says ${file.sha256}`);
  }
  for (const name of sums.keys()) {
    if (!manifest.files.some((file) => file.name === name)) problems.push(`${name} is listed but not in the manifest`);
  }
  if (problems.length > 0) {
    throw new CheckError(`SHA256SUMS.txt of ${manifest.product} ${manifest.version} does not match its manifest: ${problems.join('; ')}`);
  }
}

/**
 * Downloads a URL to a file, hashing the bytes as they are written (nothing is held in memory). The file is written
 * under a temporary name and renamed when the download is complete, so a failed download leaves nothing behind.
 * @returns {Promise<{ size: number, sha256: string }>}
 */
export async function fetchToFile(url, dest, { fetchImpl = fetch, timeoutMs = 600_000 } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const tmp = `${dest}.part`;
  try {
    const response = await fetchImpl(url, { redirect: 'follow', signal: controller.signal, headers: { 'user-agent': 'VANTA-release-scripts' } });
    if (!response.ok) throw new Error(`HTTP ${response.status} ${response.statusText}`);
    if (!response.body) throw new Error('empty response body');
    mkdirSync(dirname(dest), { recursive: true });
    const out = createWriteStream(tmp);
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
      throw error;
    }
    renameSync(tmp, dest);
    return { size, sha256: hash.digest('hex') };
  } finally {
    clearTimeout(timer);
    rmSync(tmp, { force: true });
  }
}

/** Runs `attempt` up to `attempts` times; a CheckError is final, anything else is retried after a growing delay. */
async function withRetries(attempt, { attempts = 3, retryDelayMs = 5000, log = () => {}, label }) {
  for (let n = 1; ; n += 1) {
    try {
      return await attempt();
    } catch (error) {
      if (error instanceof CheckError || n >= attempts) throw error;
      const delay = retryDelayMs * n;
      log(`  ${label}: attempt ${n} failed (${error.message}); retrying in ${delay} ms`);
      await sleep(delay);
    }
  }
}

/**
 * Downloads one manifest file to `dest` and verifies size and sha256; a mismatch deletes the file and is not retried
 * (the release files never change, so a wrong digest is a wrong file, not a bad connection).
 */
export async function downloadVerified(file, dest, options = {}) {
  return withRetries(async () => {
    const actual = await fetchToFile(file.downloadUrl, dest, options);
    if (actual.size !== file.size || actual.sha256 !== file.sha256) {
      rmSync(dest, { force: true });
      throw new CheckError(`${file.name}: expected ${file.size} bytes / ${file.sha256}, got ${actual.size} bytes / ${actual.sha256}`);
    }
    return actual;
  }, { ...options, label: file.name });
}

/**
 * Downloads every file of one published release plus its SHA256SUMS.txt and manifest into <outDir>/<product>.
 * @returns {Promise<Array<{ name: string, size: number, sha256: string }>>} in manifest order, extras last
 */
export async function downloadRelease(product, version, outDir, options = {}) {
  const root = options.root ?? REPO_ROOT;
  const log = options.log ?? (() => {});
  const { manifest } = loadPublishedManifest(root, product, version);
  const base = releaseBaseUrl(manifest);
  const dir = join(outDir, product);
  mkdirSync(dir, { recursive: true });
  const downloaded = [];
  for (const file of manifest.files) {
    const dest = join(dir, file.name);
    const actual = await downloadVerified(file, dest, { ...options, log });
    log(`  ok  ${product}/${file.name}  ${actual.size} bytes, sha256 ${actual.sha256}`);
    downloaded.push({ name: file.name, ...actual });
  }
  for (const name of releaseExtras(product, version)) {
    const dest = join(dir, name);
    const actual = await withRetries(() => fetchToFile(`${base}${name}`, dest, options), { ...options, log, label: `${product}/${name}` });
    if (actual.size === 0) throw new CheckError(`${product}/${name} is empty`);
    if (name === 'SHA256SUMS.txt') {
      checkReleaseSums(manifest, readFileSync(dest, 'utf8'));
    } else {
      // The manifest attached to the release must say what the committed manifest says (the release workflow
      // compared the two byte for byte when it published them).
      let published;
      try {
        published = JSON.parse(readFileSync(dest, 'utf8'));
      } catch (error) {
        throw new CheckError(`${product}/${name} attached to the release is not JSON: ${error.message}`);
      }
      if (!isDeepStrictEqual(published, manifest)) {
        throw new CheckError(`${product}/${name} attached to the release differs from shared/releases/${name}`);
      }
    }
    log(`  ok  ${product}/${name}  ${actual.size} bytes, sha256 ${actual.sha256}`);
    downloaded.push({ name, ...actual });
  }
  return downloaded;
}

/** The `download` command: both releases into <outDir>/client and <outDir>/launcher. */
export async function downloadBundleInputs({ clientVersion, launcherVersion, outDir, ...options }) {
  return {
    client: await downloadRelease('client', clientVersion, outDir, options),
    launcher: await downloadRelease('launcher', launcherVersion, outDir, options),
  };
}

/**
 * Checks that <fromDir>/<product> holds exactly the release files (verified against the manifest), the release's
 * SHA256SUMS.txt (matching the manifest) and the manifest itself (equal to the committed one).
 */
async function checkReleaseInputs(product, manifest, fromDir) {
  const dir = join(fromDir, product);
  const expected = [...manifest.files.map((file) => file.name), ...releaseExtras(product, manifest.version)];
  const result = checkDirectory(dir, expected);
  if (!result.ok) throw new CheckError(`${dir} does not hold exactly the ${product} ${manifest.version} release files: ${result.problems.join('; ')}`);
  for (const file of manifest.files) {
    const actual = await hashFile(join(dir, file.name));
    if (actual.size !== file.size || actual.sha256 !== file.sha256) {
      throw new CheckError(`${product}/${file.name}: expected ${file.size} bytes / ${file.sha256}, got ${actual.size} bytes / ${actual.sha256}`);
    }
  }
  checkReleaseSums(manifest, readFileSync(join(dir, 'SHA256SUMS.txt'), 'utf8'));
  const attached = readJson(join(dir, `${product}-${manifest.version}.json`));
  if (!isDeepStrictEqual(attached, manifest)) {
    throw new CheckError(`${product}/${product}-${manifest.version}.json differs from the committed manifest`);
  }
  return dir;
}

/** One line of release-assets.mjs Markdown as plain text (code spans shown literally). */
function plainDescription(text) {
  return text.replace(/`/g, '');
}

/**
 * The README.txt of the zip: what is inside, which file to take on which system, how to verify, where the files
 * come from. Plain ASCII, no claims beyond what the manifests and release-assets.mjs say.
 */
export function bundleReadme({ version, client, launcher, docFiles }) {
  const cv = client.version;
  const lv = launcher.version;
  const toolchain = { fabricApiVersion: client.fabricApiVersion, minecraftVersion: client.minecraftVersion };
  const descriptions = new Map();
  for (const product of [client, launcher]) {
    for (const asset of releaseAssets(product.product, product.version, toolchain)) descriptions.set(`${product.product}/${asset.name}`, asset.description);
  }
  const title = `VANTA ${version} full release`;
  const lines = [title, '='.repeat(title.length), ''];
  lines.push(...wrapText(`This archive bundles the published files of VANTA Client ${cv} and VANTA Launcher ${lv} for Minecraft Java Edition ${client.minecraftVersion} (Fabric Loader ${client.fabricVersion}, Fabric API ${client.fabricApiVersion}, Java ${client.javaVersion}) together with the documentation of the repository. Every file under client/ and launcher/ is byte for byte the file attached to its GitHub Release; nothing was rebuilt for this archive.`));
  lines.push('', 'Where the files come from', '');
  lines.push(`  VANTA Client ${cv}:    ${releasePageUrl(releaseBaseUrl(client))}`);
  lines.push(`  VANTA Launcher ${lv}:  ${releasePageUrl(releaseBaseUrl(launcher))}`);
  lines.push(`  Download page:         ${WEBSITE_DOWNLOAD_URL}`);
  lines.push('', 'What is inside', '');
  const inside = [
    ['README.txt', 'this file'],
    ['CHANGELOG.md', 'the changelog of the repository, every version'],
    ['LICENSE', 'the licence of VANTA'],
    ['SHA256SUMS.txt', 'SHA-256 of every other file in this archive (sha256sum -c format)'],
    ['LOCAL-AI.txt', 'what Vanta Nexus downloads on first use of the Local AI (the llama-server runtime and the model: files, sizes, sources, SHA-256, licences); none of it is in this archive'],
    ['release-notes/', `the release notes of VANTA Client ${cv} and VANTA Launcher ${lv}`],
    ['docs/', `the documentation (${docFiles.length} Markdown pages, the same text the website shows)`],
    ['local-ai/', 'local-ai.json, the manifest the client and the launcher use for those Local AI downloads (the same copy both embed)'],
    ['client/', `the ${client.files.length} files of the client release, its SHA256SUMS.txt and its manifest client-${cv}.json`],
    ['launcher/', `the ${launcher.files.length} files of the launcher release, its SHA256SUMS.txt and its manifest launcher-${lv}.json`],
  ];
  for (const [name, what] of inside) {
    const [first, ...rest] = wrapText(what, { width: 100, indent: ' '.repeat(28) });
    lines.push(`  ${name.padEnd(26)}${first.trimStart()}`, ...rest);
  }
  for (const product of [launcher, client]) {
    const label = product.product === 'launcher' ? 'VANTA Launcher' : 'VANTA Client';
    lines.push('', `Which file to take: ${product.product}/ (${label} ${product.version})`, '');
    for (const file of product.files) {
      lines.push(`  ${file.name}  (${formatSize(file.size)})`);
      const description = descriptions.get(`${product.product}/${file.name}`);
      lines.push(...wrapText(description ? plainDescription(description) : 'See the release page.', { width: 100, indent: '      ' }));
    }
  }
  lines.push('');
  lines.push(...wrapText('LICENSE is the licence of VANTA itself. Third-party files under client/ (Fabric API, the Performance pack mods inside the mods zip) keep their own licences, which travel inside those files. The Local AI runtime and model are not in this archive: LOCAL-AI.txt says what the game downloads for them, from where and under which licences.'));
  lines.push('', 'Verify the files', '');
  lines.push(...wrapText('SHA256SUMS.txt lists every other file of this archive with its SHA-256. From inside the extracted folder:', { indent: '  ' }));
  lines.push('');
  lines.push('    Linux:    sha256sum -c SHA256SUMS.txt');
  lines.push('    macOS:    shasum -a 256 -c SHA256SUMS.txt');
  lines.push('    Windows:  certutil -hashfile <file> SHA256   (PowerShell: Get-FileHash <file> -Algorithm SHA256)');
  lines.push('              and compare the hash with the line for that file in SHA256SUMS.txt');
  lines.push('');
  lines.push(...wrapText(`client/SHA256SUMS.txt and launcher/SHA256SUMS.txt are the checksum files of the two releases; they list the same digests as the release pages and as the manifests client-${cv}.json and launcher-${lv}.json.`, { indent: '  ' }));
  lines.push('');
  lines.push(...wrapText('The files are not code-signed yet, so Windows SmartScreen or macOS may warn before opening them. Compare the SHA-256 first.'));
  lines.push('');
  lines.push(...wrapText(`Minecraft ${client.minecraftVersion}, Fabric Loader ${client.fabricVersion}, Java ${client.javaVersion}. VANTA is not affiliated with Mojang or Microsoft.`));
  return `${lines.join('\n')}\n`;
}

/** Lists docs/*.md of the repository (sorted), the pages the website renders. */
function listDocs(root) {
  const dir = resolve(root, 'docs');
  if (!existsSync(dir)) throw new UsageError('docs/ is missing');
  const files = readdirSync(dir).filter((name) => name.endsWith('.md') && statSync(join(dir, name)).isFile()).sort();
  if (files.length === 0) throw new UsageError('docs/ holds no Markdown files');
  return files;
}

function requireRepoFile(root, rel) {
  const path = resolve(root, rel);
  if (!existsSync(path) || !statSync(path).isFile()) throw new UsageError(`${rel} is missing`);
  if (statSync(path).size === 0) throw new UsageError(`${rel} is empty`);
  return path;
}

/** The committed Local AI manifest, resolved (the zip never ships the template); a UsageError otherwise. */
function requireLocalAiManifest(root) {
  try {
    return loadLocalAiManifest(root);
  } catch (error) {
    throw new UsageError(error.message);
  }
}

/** Runs the system zip binary on a list of entry names relative to `cwd`, in that order, without directory entries. */
function zipEntries(zipPath, cwd, entryNames) {
  // lib/zip-writer.mjs holds every entry and then the whole archive in memory (Buffer.concat) and writes no ZIP64
  // records; this archive is hundreds of megabytes, so the system zip binary streams it instead. -X leaves out the
  // platform extra fields, -D writes no directory entries (the entry list is exactly the file list), -@ reads the
  // names from stdin so a long list never hits the command-line limit.
  rmSync(zipPath, { force: true });
  try {
    execFileSync('zip', ['-X', '-D', '-q', zipPath, '-@'], { cwd, input: `${entryNames.join('\n')}\n`, stdio: ['pipe', 'inherit', 'inherit'] });
  } catch (error) {
    if (error.code === 'ENOENT') throw new UsageError('zip is not installed (the archive is written by the system zip binary)');
    throw new CheckError(`zip failed: ${error.message}`);
  }
}

/** sha256 of a buffer. */
function digest(buffer) {
  return createHash('sha256').update(buffer).digest('hex');
}

/**
 * Reads a finished bundle zip: the top-level folder, every file entry (relative path, size, sha256 of the
 * uncompressed bytes, in zip order) and the SHA256SUMS.txt it carries, cross-checked against the entries.
 * @returns {{ folder: string, entries: Array<{ path: string, size: number, sha256: string }>, sums: Map<string, string> }}
 */
export function readBundleZip(zipPath, version) {
  const folder = bundleFolder(version);
  const zip = openZip(zipPath);
  const entries = [];
  for (const entry of zip.entries) {
    if (entry.directory) continue;
    if (entry.encrypted) throw new CheckError(`${entry.name} is encrypted`);
    if (!entry.name.startsWith(`${folder}/`)) throw new CheckError(`${entry.name} is outside the top-level folder ${folder}/`);
    const path = entry.name.slice(folder.length + 1);
    if (path === '' || path.split('/').some((part) => part === '' || part === '.' || part === '..')) throw new CheckError(`unsafe entry name ${entry.name}`);
    const bytes = zip.read(entry.name);
    entries.push({ path, size: bytes.length, sha256: digest(bytes) });
  }
  const sumsEntry = entries.find((entry) => entry.path === 'SHA256SUMS.txt');
  if (!sumsEntry) throw new CheckError(`${zipPath} has no ${folder}/SHA256SUMS.txt`);
  const sums = parseSums(zip.read(`${folder}/SHA256SUMS.txt`).toString('utf8'));
  const problems = [];
  for (const entry of entries) {
    if (entry.path === 'SHA256SUMS.txt') continue;
    const listed = sums.get(entry.path);
    if (listed === undefined) problems.push(`${entry.path} is not listed`);
    else if (listed !== entry.sha256) problems.push(`${entry.path} is listed with ${listed}, its bytes hash to ${entry.sha256}`);
    if (entry.size === 0) problems.push(`${entry.path} is empty`);
  }
  for (const path of sums.keys()) {
    if (!entries.some((entry) => entry.path === path)) problems.push(`${path} is listed but not in the zip`);
  }
  if (problems.length > 0) throw new CheckError(`SHA256SUMS.txt in ${zipPath} does not match the entries: ${problems.join('; ')}`);
  return { folder, entries, sums };
}

/**
 * The `assemble` command. Stages the layout under <outDir>, zips it with the system zip binary, re-reads the finished
 * zip (entry list, SHA256SUMS.txt against the bytes) and writes <outDir>/SHA256SUMS.txt with the zip's own digest.
 * @returns {Promise<{ zipPath: string, sumsPath: string, zip: { name: string, size: number, sha256: string }, entries: Array<{ path: string, size: number, sha256: string }> }>}
 */
export async function assembleBundle({ version, clientVersion, launcherVersion, fromDir, outDir, root = REPO_ROOT, log = () => {} }) {
  requireSemVer(version, 'bundle version');
  const client = loadPublishedManifest(root, 'client', clientVersion).manifest;
  const launcher = loadPublishedManifest(root, 'launcher', launcherVersion).manifest;
  const sources = {
    client: await checkReleaseInputs('client', client, fromDir),
    launcher: await checkReleaseInputs('launcher', launcher, fromDir),
  };
  const changelog = requireRepoFile(root, 'CHANGELOG.md');
  const license = requireRepoFile(root, 'LICENSE');
  const notes = {
    client: requireRepoFile(root, `website/content/changelog/client-${clientVersion}.md`),
    launcher: requireRepoFile(root, `website/content/changelog/launcher-${launcherVersion}.md`),
  };
  const docFiles = listDocs(root);
  const localAi = requireLocalAiManifest(root);

  const folder = bundleFolder(version);
  mkdirSync(outDir, { recursive: true });
  const stageRoot = mkdtempSync(join(outDir, '.bundle-stage-'));
  try {
    const top = join(stageRoot, folder);
    /** @type {string[]} paths relative to the top-level folder, in zip order */
    const paths = [];
    const stage = (path, source) => {
      if (statSync(source).size === 0) throw new CheckError(`${source} is empty`);
      const dest = join(top, path);
      mkdirSync(dirname(dest), { recursive: true });
      copyFileSync(source, dest);
      paths.push(path);
    };
    // README.txt, SHA256SUMS.txt and LOCAL-AI.txt are written below but keep their place in the order.
    paths.push('README.txt');
    stage('CHANGELOG.md', changelog);
    stage('LICENSE', license);
    paths.push('SHA256SUMS.txt');
    paths.push('LOCAL-AI.txt');
    stage(`release-notes/client-${clientVersion}.md`, notes.client);
    stage(`release-notes/launcher-${launcherVersion}.md`, notes.launcher);
    for (const name of docFiles) stage(`docs/${name}`, resolve(root, 'docs', name));
    stage('local-ai/local-ai.json', localAi.path);
    for (const manifest of [client, launcher]) {
      const product = manifest.product;
      for (const file of manifest.files) stage(`${product}/${file.name}`, join(sources[product], file.name));
      for (const name of releaseExtras(product, manifest.version)) stage(`${product}/${name}`, join(sources[product], name));
    }
    writeTextAtomic(join(top, 'README.txt'), bundleReadme({ version, client, launcher, docFiles }));
    writeTextAtomic(join(top, 'LOCAL-AI.txt'), localAiNote(localAi.manifest));
    const sumLines = [];
    for (const path of paths) {
      if (path === 'SHA256SUMS.txt') continue;
      const { sha256 } = await hashFile(join(top, path));
      sumLines.push(`${sha256}  ${path}`);
    }
    writeTextAtomic(join(top, 'SHA256SUMS.txt'), `${sumLines.join('\n')}\n`);

    const zipPath = join(outDir, bundleZipName(version));
    zipEntries(zipPath, stageRoot, paths.map((path) => `${folder}/${path}`));

    const finished = readBundleZip(zipPath, version);
    const expected = JSON.stringify(paths);
    const actual = JSON.stringify(finished.entries.map((entry) => entry.path));
    if (actual !== expected) throw new CheckError(`${zipPath} does not hold exactly the staged entries in order:\n  expected ${expected}\n  actual   ${actual}`);
    for (const entry of finished.entries) {
      const staged = await hashFile(join(top, entry.path));
      if (staged.size !== entry.size || staged.sha256 !== entry.sha256) throw new CheckError(`${entry.path} in the zip differs from the staged file`);
    }
    const zipHash = await hashFile(zipPath);
    const sumsPath = join(outDir, 'SHA256SUMS.txt');
    writeTextAtomic(sumsPath, `${zipHash.sha256}  ${bundleZipName(version)}\n`);
    for (const entry of finished.entries) log(`  ${entry.path}  ${entry.size} bytes  ${entry.sha256}`);
    log(`${zipPath}: ${finished.entries.length} entries, ${zipHash.size} bytes, sha256 ${zipHash.sha256}`);
    return { zipPath, sumsPath, zip: { name: bundleZipName(version), ...zipHash }, entries: finished.entries };
  } finally {
    rmSync(stageRoot, { recursive: true, force: true });
  }
}

/** Reads shared/schemas/bundle-manifest.schema.json. */
export function loadBundleSchema(root = REPO_ROOT) {
  return readJson(resolve(root, 'shared', 'schemas', 'bundle-manifest.schema.json'));
}

/** Validates a bundle manifest against the schema; throws a UsageError naming every problem. */
export function validateBundleManifest(manifest, root = REPO_ROOT) {
  const result = validate(loadBundleSchema(root), manifest, { baseDir: resolve(root, 'shared', 'schemas') });
  if (!result.valid) {
    throw new UsageError(`bundle manifest does not match the schema:\n${result.errors.map((e) => `  ${e.path}: ${e.message}`).join('\n')}`);
  }
  return manifest;
}

/**
 * The `manifest` command: the bundle manifest of the contract from the real zip. Every component release file must
 * be in the zip under <product>/<name> with the manifest's size and sha256, and the zip's SHA256SUMS.txt must match
 * the entries (readBundleZip). Validated against the schema before it is written.
 * @returns {Promise<{ manifest: object, path: string }>}
 */
export async function writeBundleManifest({ version, clientVersion, launcherVersion, zipPath, url, date, outDir, root = REPO_ROOT, dryRun = false }) {
  requireSemVer(version, 'bundle version');
  if (typeof url !== 'string' || !HTTPS_URL.test(url)) throw new UsageError('--url must be an absolute https URL');
  if (date !== undefined && !/^\d{4}-\d{2}-\d{2}$/.test(date)) throw new UsageError('--date must be YYYY-MM-DD');
  if (!zipPath || !existsSync(zipPath)) throw new UsageError(`--zip '${zipPath}' does not exist`);
  const name = bundleZipName(version);
  if (basename(zipPath) !== name) throw new UsageError(`the zip must be named ${name}, got ${basename(zipPath)}`);
  const client = loadPublishedManifest(root, 'client', clientVersion).manifest;
  const launcher = loadPublishedManifest(root, 'launcher', launcherVersion).manifest;
  const { entries } = readBundleZip(zipPath, version);
  const byPath = new Map(entries.map((entry) => [entry.path, entry]));
  const problems = [];
  for (const manifest of [client, launcher]) {
    for (const file of manifest.files) {
      const entry = byPath.get(`${manifest.product}/${file.name}`);
      if (!entry) problems.push(`${manifest.product}/${file.name} is not in the zip`);
      else if (entry.size !== file.size || entry.sha256 !== file.sha256) problems.push(`${manifest.product}/${file.name} differs from the ${manifest.product} manifest`);
    }
    for (const extra of releaseExtras(manifest.product, manifest.version)) {
      if (!byPath.has(`${manifest.product}/${extra}`)) problems.push(`${manifest.product}/${extra} is not in the zip`);
    }
  }
  for (const path of ['README.txt', 'CHANGELOG.md', 'LICENSE', 'LOCAL-AI.txt', `release-notes/client-${clientVersion}.md`, `release-notes/launcher-${launcherVersion}.md`, `local-ai/${basename(LOCAL_AI_MANIFEST_PATH)}`]) {
    if (!byPath.has(path)) problems.push(`${path} is not in the zip`);
  }
  if (problems.length > 0) throw new CheckError(`${zipPath} is not a complete ${version} bundle: ${problems.join('; ')}`);
  const { size, sha256 } = await hashFile(zipPath);
  const manifest = {
    schemaVersion: 1,
    kind: 'bundle',
    version,
    clientVersion,
    launcherVersion,
    minecraftVersion: client.minecraftVersion,
    releaseDate: date ?? todayUtc(),
    channel: client.channel === 'stable' && launcher.channel === 'stable' ? 'stable' : 'beta',
    file: { name, downloadUrl: url, size, sha256 },
    contents: entries.filter((entry) => entry.path !== 'SHA256SUMS.txt').map(({ path, size: entrySize, sha256: entrySha }) => ({ path, size: entrySize, sha256: entrySha })),
  };
  validateBundleManifest(manifest, root);
  const path = join(outDir ?? resolve(root, 'shared', 'releases', 'bundles'), bundleManifestName(version));
  if (!dryRun) writeJsonAtomic(path, manifest);
  return { manifest, path };
}

/**
 * The `verify` command: the zip the manifest names (downloaded, or `localZip`) has the manifest's size and sha256.
 * @returns {Promise<{ ok: boolean, status: 'ok'|'mismatch'|'unpublished', expected: object, actual?: object, message?: string }>}
 */
export async function verifyBundle(manifest, { localZip, fetchImpl, timeoutMs, root = REPO_ROOT } = {}) {
  validateBundleManifest(manifest, root);
  const expected = { size: manifest.file.size, sha256: manifest.file.sha256 };
  if (!localZip && !HTTPS_URL.test(manifest.file.downloadUrl)) {
    return { ok: false, status: 'unpublished', expected, message: 'no downloadUrl (not published yet)' };
  }
  if (localZip && !existsSync(localZip)) throw new UsageError(`${localZip} does not exist`);
  const actual = localZip ? await hashFile(localZip) : await hashUrl(manifest.file.downloadUrl, { timeoutMs, fetchImpl });
  if (!isSha256Hex(manifest.file.sha256) || manifest.file.size <= 0) {
    return { ok: false, status: 'mismatch', expected, actual, message: 'manifest has no size/sha256 to compare against' };
  }
  const ok = actual.size === expected.size && actual.sha256 === expected.sha256;
  return {
    ok,
    status: ok ? 'ok' : 'mismatch',
    expected,
    actual,
    message: ok ? undefined : `expected ${expected.size} bytes / ${expected.sha256}, got ${actual.size} bytes / ${actual.sha256}`,
  };
}

/**
 * The `notes` command: the GitHub Release body in Markdown. Sizes and digests come from the bundle manifest, the
 * toolchain line from the client manifest, the release links from the component manifests' download URLs.
 */
export function releaseNotes({ version, clientVersion, launcherVersion, manifest, root = REPO_ROOT }) {
  validateBundleManifest(manifest, root);
  for (const [field, value] of [['version', version], ['clientVersion', clientVersion], ['launcherVersion', launcherVersion]]) {
    if (manifest[field] !== value) throw new UsageError(`the bundle manifest has ${field} ${manifest[field]}, expected ${value}`);
  }
  const client = loadPublishedManifest(root, 'client', clientVersion).manifest;
  const launcher = loadPublishedManifest(root, 'launcher', launcherVersion).manifest;
  const clientPage = releasePageUrl(releaseBaseUrl(client));
  const launcherPage = releasePageUrl(releaseBaseUrl(launcher));
  const name = manifest.file.name;
  const folder = bundleFolder(version);
  const lines = [];
  lines.push(`One zip with every published file of VANTA Client ${clientVersion} and VANTA Launcher ${launcherVersion}, plus the documentation of the repository. The files under \`client/\` and \`launcher/\` are byte for byte the files attached to [client-v${clientVersion}](${clientPage}) and [launcher-v${launcherVersion}](${launcherPage}); nothing was rebuilt for this zip, and each folder carries the release's own \`SHA256SUMS.txt\` and manifest. Single downloads with a description of every file are on those two release pages and on the [website download page](${WEBSITE_DOWNLOAD_URL}).`);
  lines.push('');
  lines.push(`\`README.txt\` inside the zip says which file to take on which system. The archive extracts to one folder, \`${folder}/\`.`);
  lines.push('');
  lines.push(`## Contents of \`${name}\``);
  lines.push('');
  lines.push('| Path | Size | SHA-256 |');
  lines.push('| --- | --- | --- |');
  for (const entry of manifest.contents) {
    lines.push(`| \`${markdownCell(entry.path)}\` | ${formatSize(entry.size)} | \`${entry.sha256}\` |`);
  }
  lines.push('');
  lines.push(`\`SHA256SUMS.txt\` inside the folder lists the same digests (\`cd ${folder} && sha256sum -c SHA256SUMS.txt\`).`);
  lines.push('');
  lines.push('## Verify your download');
  lines.push('');
  lines.push('```');
  lines.push(isSha256Hex(manifest.file.sha256) ? `${manifest.file.sha256}  ${name}` : `${name}: not published yet`);
  lines.push('```');
  lines.push('');
  lines.push(`Windows: \`certutil -hashfile ${name} SHA256\` · macOS: \`shasum -a 256 ${name}\` · Linux: \`sha256sum -c --ignore-missing SHA256SUMS.txt\``);
  lines.push('');
  lines.push('The files are not code-signed yet, so Windows SmartScreen or macOS may warn before opening them. Compare the SHA-256 first.');
  lines.push('');
  lines.push(`Also attached: \`SHA256SUMS.txt\` (SHA-256 of the zip) and \`${bundleManifestName(version)}\` (the bundle manifest the website's download page reads).`);
  lines.push('');
  lines.push(`Minecraft ${client.minecraftVersion} · Fabric Loader ${client.fabricVersion} · Java ${client.javaVersion}. Not affiliated with Mojang or Microsoft.`);
  return `${lines.join('\n')}\n`;
}

/** CLI entry point. */
export async function main(argv, log = console.log, logError = console.error) {
  const parsed = parseArgs(argv, {
    values: ['version', 'client-version', 'launcher-version', 'out', 'from', 'zip', 'url', 'date', 'manifest', 'local', 'root', 'timeout', 'attempts'],
    flags: ['dry-run', 'help'],
    aliases: { h: 'help' },
  });
  if (parsed.flags.has('help')) {
    log(USAGE);
    return 0;
  }
  const [command, ...rest] = parsed.positional;
  const o = parsed.options;
  const commands = ['download', 'assemble', 'manifest', 'verify', 'notes'];
  if (!command) parsed.errors.push('a command is required');
  else if (!commands.includes(command)) parsed.errors.push(`unknown command '${command}'`);
  const wantedPositionals = command === 'verify' ? 1 : 0;
  if (command && rest.length !== wantedPositionals) parsed.errors.push(`${command} takes ${wantedPositionals === 1 ? 'exactly one manifest path' : 'no positional arguments'}`);
  const require = (names) => {
    for (const name of names) if (!(name in o)) parsed.errors.push(`--${name} is required for ${command}`);
  };
  if (command === 'download') require(['client-version', 'launcher-version', 'out']);
  if (command === 'assemble') require(['version', 'from', 'out']);
  if (command === 'manifest') require(['version', 'zip', 'url']);
  if (command === 'notes') require(['version', 'manifest']);
  for (const name of ['timeout', 'attempts']) {
    if (o[name] !== undefined && !/^[1-9]\d*$/.test(o[name])) parsed.errors.push(`--${name} must be a positive integer`);
  }
  if (o.version !== undefined && !isSemVer(o.version)) parsed.errors.push(`--version '${o.version}' is not a SemVer version`);
  const clientVersion = o['client-version'] ?? o.version;
  const launcherVersion = o['launcher-version'] ?? o.version;
  for (const [name, value] of [['client-version', clientVersion], ['launcher-version', launcherVersion]]) {
    if (value !== undefined && !isSemVer(value)) parsed.errors.push(`--${name} '${value}' is not a SemVer version`);
  }
  if (parsed.errors.length > 0) {
    for (const error of parsed.errors) logError(`error: ${error}`);
    logError(USAGE);
    return 2;
  }
  const root = o.root ? resolve(o.root) : REPO_ROOT;
  const timeoutMs = o.timeout ? Number(o.timeout) : undefined;
  const attempts = o.attempts ? Number(o.attempts) : undefined;
  try {
    if (command === 'download') {
      const outDir = resolve(o.out);
      log(`Downloading client ${clientVersion} and launcher ${launcherVersion} into ${outDir}`);
      const result = await downloadBundleInputs({ clientVersion, launcherVersion, outDir, root, timeoutMs, attempts, log });
      log(`Downloaded and verified ${result.client.length + result.launcher.length} files.`);
      return 0;
    }
    if (command === 'assemble') {
      const result = await assembleBundle({ version: o.version, clientVersion, launcherVersion, fromDir: resolve(o.from), outDir: resolve(o.out), root, log });
      log(`wrote ${result.zipPath}`);
      log(`wrote ${result.sumsPath}`);
      return 0;
    }
    if (command === 'manifest') {
      const result = await writeBundleManifest({
        version: o.version,
        clientVersion,
        launcherVersion,
        zipPath: resolve(o.zip),
        url: o.url,
        date: o.date,
        outDir: o.out ? resolve(o.out) : undefined,
        root,
        dryRun: parsed.flags.has('dry-run'),
      });
      log(`${result.manifest.file.name}: ${result.manifest.file.size} bytes, sha256 ${result.manifest.file.sha256}, ${result.manifest.contents.length} files inside`);
      log(`${parsed.flags.has('dry-run') ? 'would write' : 'wrote'} ${result.path}`);
      if (parsed.flags.has('dry-run')) log(JSON.stringify(result.manifest, null, 2));
      return 0;
    }
    if (command === 'verify') {
      let manifest;
      try {
        manifest = readJson(rest[0]);
      } catch (error) {
        throw new UsageError(`cannot read ${rest[0]}: ${error.message}`);
      }
      const report = await verifyBundle(manifest, { localZip: o.local ? resolve(o.local) : undefined, timeoutMs, root });
      const label = report.status.toUpperCase().padEnd(11);
      const detail = report.status === 'ok' ? `${report.actual.size} bytes, sha256 ${report.actual.sha256}` : report.message;
      (report.ok ? log : logError)(`  ${label} ${manifest.file?.name ?? basename(rest[0])}  ${detail}`);
      log(report.ok ? 'Bundle verified.' : 'Verification FAILED.');
      return report.ok ? 0 : 1;
    }
    // notes
    let manifest;
    try {
      manifest = readJson(o.manifest);
    } catch (error) {
      throw new UsageError(`cannot read ${o.manifest}: ${error.message}`);
    }
    log(releaseNotes({ version: o.version, clientVersion, launcherVersion, manifest, root }).trimEnd());
    return 0;
  } catch (error) {
    logError(`error: ${error.message}`);
    return error instanceof UsageError ? 2 : 1;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(await main(process.argv.slice(2)));
}
