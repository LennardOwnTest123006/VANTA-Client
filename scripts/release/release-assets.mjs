#!/usr/bin/env node
/**
 * The exact set of files a GitHub Release of each product ships, in manifest order (primary file first).
 * This module is the single source of truth for those names: the release workflow, bump-version.mjs and the
 * checks below all read it, so a renamed or missing asset fails the release instead of reaching users.
 *
 *   node scripts/release/release-assets.mjs list --product client --version 1.0.0
 *   node scripts/release/release-assets.mjs check-dir dist --product launcher --version 1.0.0 [--with-extras]
 *   node scripts/release/release-assets.mjs check-manifest shared/releases/client-1.0.0.json
 *        [--published --repo OWNER/REPO --tag client-v1.0.0]
 *   node scripts/release/release-assets.mjs notes --product client --version 1.0.0 [--dir dist]
 *   common option: --root <repo root> (the Fabric API version comes from <root>/client/gradle.properties)
 *
 * Exit codes: 0 ok, 1 check failed, 2 usage error.
 *
 * Besides the files listed in the manifest, every release also carries SHA256SUMS.txt and the manifest itself
 * (<product>-<version>.json); those two are "extras" and are never listed in files[].
 */
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from './lib/args.mjs';
import { REPO_ROOT, isSemVer, readToolchain } from './lib/repo.mjs';

export const PRODUCTS = Object.freeze(['client', 'launcher']);

const USAGE = `Usage: node scripts/release/release-assets.mjs <command> [options]
  list            --product <client|launcher> --version <semver>
  check-dir <dir> --product <client|launcher> --version <semver> [--with-extras]
  check-manifest <manifest.json> [--published --repo <owner/repo> --tag <tag>]
  notes           --product <client|launcher> --version <semver> [--dir <dir with the files>]
  options: --root <repository root>`;

/**
 * @typedef {{ name: string, description: string }} ReleaseAsset
 * `description` is one line of GitHub Markdown for the release notes table: commands, file names and paths go in code
 * spans (backticks). Outside code spans assetsMarkdown() escapes '<', '>' and '&', so a placeholder such as <file>
 * cannot be swallowed as an HTML tag; inside them the text is shown literally.
 */

/**
 * The files listed in the release manifest, primary first.
 * @param {string} product client | launcher
 * @param {string} version SemVer of the product
 * @param {{ fabricApiVersion: string, minecraftVersion: string }} toolchain pinned toolchain (see readToolchain)
 * @returns {ReleaseAsset[]}
 */
export function releaseAssets(product, version, toolchain) {
  if (!PRODUCTS.includes(product)) throw new Error(`product must be one of ${PRODUCTS.join(', ')}`);
  if (!isSemVer(version)) throw new Error(`'${version}' is not a SemVer version`);
  const fapi = toolchain.fabricApiVersion;
  const mc = toolchain.minecraftVersion;
  if (product === 'client') {
    return [
      { name: `vanta-client-${version}.jar`, description: `The VANTA Client Fabric mod for Minecraft ${mc}. Put it in your \`mods\` folder together with Fabric API.` },
      { name: `vanta-client-${version}-mods.zip`, description: `A complete \`mods/\` folder: \`vanta-client-${version}.jar\`, \`fabric-api-${fapi}.jar\` and the Performance pack mods that may be redistributed (third-party, own licences; EntityCulling is downloaded in game), plus \`INSTALL.txt\`, \`PERFORMANCE-PACK.txt\`, \`THIRD-PARTY-LICENSES.txt\`, \`performance-pack.json\` and \`SHA256SUMS\`.` },
      { name: `fabric-api-${fapi}.jar`, description: 'Fabric API, the unmodified FabricMC release (Apache-2.0). Required dependency of VANTA Client.' },
    ];
  }
  const javaJar = (name) => `Needs Java 21 installed: \`java -jar ${name}\`.`;
  const windowsJar = `vanta-launcher-${version}-windows-all.jar`;
  const linuxJar = `vanta-launcher-${version}-linux-all.jar`;
  const macJar = `vanta-launcher-${version}-macos-aarch64-all.jar`;
  return [
    { name: `VANTA-Launcher-${version}.msi`, description: 'Windows x64 installer (per-user, no admin rights), bundles the Java 21 runtime. Recommended for Windows.' },
    { name: `VANTA-Launcher-${version}.exe`, description: 'Windows x64 installer as an .exe; same content as the .msi.' },
    { name: `VANTA-Launcher-${version}-windows-portable.zip`, description: 'Windows x64 portable app with the Java 21 runtime, no installation: unzip and run `VANTA Launcher/VANTA Launcher.exe`.' },
    { name: windowsJar, description: `Single jar with JavaFX for Windows x64. ${javaJar(windowsJar)}` },
    { name: `VANTA-Launcher-${version}-linux-x64.tar.gz`, description: 'Linux x64 app with the Java 21 runtime: extract and run `VANTA Launcher/bin/VANTA Launcher`.' },
    { name: linuxJar, description: `Single jar with JavaFX for Linux x64. ${javaJar(linuxJar)}` },
    { name: macJar, description: `Single jar with JavaFX for Apple Silicon macOS. ${javaJar(macJar)} Built and command-line smoke-tested on a macOS runner; the window was not tested; unsigned.` },
  ];
}

/** Names of the manifest files, in order. */
export function releaseAssetNames(product, version, toolchain) {
  return releaseAssets(product, version, toolchain).map((asset) => asset.name);
}

/** Files attached to every release that are not listed in the manifest's files[]. */
export function extraAssetNames(product, version) {
  return ['SHA256SUMS.txt', `${product}-${version}.json`];
}

/** Git tag of a release. */
export function releaseTag(product, version) {
  return `${product}-v${version}`;
}

/** Public download URL of a release asset (what the workflow writes into the manifest). */
export function releaseDownloadUrl(repo, tag, name) {
  if (!/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repo ?? '')) throw new Error(`'${repo}' is not an owner/repo name`);
  return `https://github.com/${repo}/releases/download/${tag}/${name}`;
}

/**
 * Compares the regular files in a directory with the expected release files.
 * @param {string} dir
 * @param {string[]} expected names that must be present (non-empty)
 * @returns {{ ok: boolean, problems: string[] }}
 */
export function checkDirectory(dir, expected) {
  const problems = [];
  if (!existsSync(dir) || !statSync(dir).isDirectory()) return { ok: false, problems: [`${dir} is not a directory`] };
  const present = readdirSync(dir).filter((name) => statSync(join(dir, name)).isFile());
  for (const name of expected) {
    if (!present.includes(name)) problems.push(`missing: ${name}`);
    else if (statSync(join(dir, name)).size === 0) problems.push(`empty: ${name}`);
  }
  for (const name of present) {
    if (!expected.includes(name)) problems.push(`unexpected file: ${name}`);
  }
  for (const name of readdirSync(dir)) {
    if (statSync(join(dir, name)).isDirectory()) problems.push(`unexpected directory: ${name}`);
  }
  return { ok: problems.length === 0, problems };
}

/**
 * Checks that a manifest lists exactly the release files of its product/version, in order, and that its toolchain
 * matches the repository. Without `published` every entry must be either completely unpublished
 * (downloadUrl "", size 0, sha256 "") or completely filled; with `published` every entry must be filled and point
 * at the release asset URL of `repo`/`tag`.
 *
 * @param {object} manifest parsed manifest
 * @param {{ toolchain: object, published?: boolean, repo?: string, tag?: string }} options
 * @returns {{ ok: boolean, problems: string[], expected: string[] }}
 */
export function checkManifest(manifest, { toolchain, published = false, repo, tag }) {
  const problems = [];
  if (!manifest || typeof manifest !== 'object') return { ok: false, problems: ['manifest is not an object'], expected: [] };
  for (const key of ['minecraftVersion', 'fabricVersion', 'fabricApiVersion', 'javaVersion']) {
    if (manifest[key] !== toolchain[key]) problems.push(`${key} is ${JSON.stringify(manifest[key])}, the repository pins ${JSON.stringify(toolchain[key])}`);
  }
  let expected;
  try {
    expected = releaseAssetNames(manifest.product, manifest.version, toolchain);
  } catch (error) {
    return { ok: false, problems: [...problems, error.message], expected: [] };
  }
  const files = Array.isArray(manifest.files) ? manifest.files : [];
  const names = files.map((f) => f?.name);
  if (JSON.stringify(names) !== JSON.stringify(expected)) {
    problems.push(`files[] must list exactly ${JSON.stringify(expected)} in this order, found ${JSON.stringify(names)}`);
  }
  const expectedTag = tag ?? releaseTag(manifest.product, manifest.version);
  for (const file of files) {
    const filled = file.downloadUrl !== '' || file.size !== 0 || file.sha256 !== '';
    const complete = typeof file.downloadUrl === 'string' && /^https:\/\/\S+$/.test(file.downloadUrl)
      && Number.isInteger(file.size) && file.size > 0
      && typeof file.sha256 === 'string' && /^[a-f0-9]{64}$/.test(file.sha256);
    if (published) {
      if (!complete) problems.push(`${file.name}: downloadUrl, size and sha256 must all be filled (got ${JSON.stringify({ downloadUrl: file.downloadUrl, size: file.size, sha256: file.sha256 })})`);
      if (repo) {
        const url = releaseDownloadUrl(repo, expectedTag, file.name);
        if (file.downloadUrl !== url) problems.push(`${file.name}: downloadUrl is ${JSON.stringify(file.downloadUrl)}, expected ${url}`);
      }
    } else if (filled && !complete) {
      problems.push(`${file.name}: partially filled entry (all of downloadUrl, size and sha256 or none of them)`);
    }
  }
  return { ok: problems.length === 0, problems, expected };
}

const SIZE_UNITS = Object.freeze(['B', 'kB', 'MB', 'GB', 'TB']);

/**
 * `bytes` in tenths of SIZE_UNITS[unit], rounded half up in exact integer arithmetic (no binary floating point
 * rounding, so 1,450,000 bytes is 15 tenths of a MB, not 14).
 * @param {number} bytes non-negative safe integer
 * @param {number} unit index into SIZE_UNITS, at least 1
 */
function roundedTenths(bytes, unit) {
  const divisor = 10 ** (3 * unit - 1); // one tenth of the unit in bytes
  const remainder = bytes % divisor;
  const tenths = (bytes - remainder) / divisor; // exact: the dividend is a multiple of the divisor
  return remainder * 2 >= divisor ? tenths + 1 : tenths;
}

/**
 * Human-readable size in decimal units (1 kB = 1,000 bytes, 1 MB = 1,000,000 bytes) with one decimal place above
 * plain bytes, e.g. "999 B", "1.4 MB" or "66.9 MB". The rule, shared with formatBytes() in website/src/lib/format.ts
 * and ByteSizes in the launcher so the release notes, the download page and the launcher show the same numbers:
 * pick the largest unit not above the byte count, round half up to tenths of that unit with integer arithmetic, and
 * if that gives 1000.0 move up one unit ("1.0 MB" for 999,950 bytes, never "1000.0 kB").
 * @param {number} bytes non-negative integer byte count
 */
export function formatSize(bytes) {
  if (!Number.isSafeInteger(bytes) || bytes < 0) throw new Error(`'${bytes}' is not a byte count`);
  if (bytes < 1000) return `${bytes} ${SIZE_UNITS[0]}`;
  const last = SIZE_UNITS.length - 1;
  let unit = 1;
  while (unit < last && bytes >= 1000 ** (unit + 1)) unit += 1;
  let tenths = roundedTenths(bytes, unit);
  if (tenths >= 10_000 && unit < last) {
    unit += 1;
    tenths = roundedTenths(bytes, unit);
  }
  return `${Math.floor(tenths / 10)}.${tenths % 10} ${SIZE_UNITS[unit]}`;
}

/**
 * Turns one line of Markdown into a GitHub table cell. Text inside single-backtick code spans is kept as it is;
 * outside them '&', '<' and '>' become entities, so `java -jar <file>` written without backticks still shows its
 * placeholder instead of GitHub parsing <file> as an HTML tag and dropping it. '|' is escaped everywhere (GitHub
 * splits table cells before it parses code spans) and line breaks become spaces.
 * @param {string} text
 */
export function markdownCell(text) {
  return String(text)
    .replace(/\r?\n/g, ' ')
    .split(/(`[^`]*`)/)
    .map((part, index) => (index % 2 === 1 ? part : part.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')))
    .join('')
    .replace(/\|/g, '\\|');
}

/**
 * Markdown table of the release files for the GitHub Release notes.
 * @param {ReleaseAsset[]} assets
 * @param {string} [dir] directory holding the files (adds a size column)
 */
export function assetsMarkdown(assets, dir) {
  const withSize = typeof dir === 'string';
  const lines = withSize ? ['| File | Size | What it is |', '| --- | --- | --- |'] : ['| File | What it is |', '| --- | --- |'];
  for (const asset of assets) {
    const cell = markdownCell(asset.description);
    if (withSize) lines.push(`| \`${asset.name}\` | ${formatSize(statSync(join(dir, asset.name)).size)} | ${cell} |`);
    else lines.push(`| \`${asset.name}\` | ${cell} |`);
  }
  return `${lines.join('\n')}\n`;
}

/** CLI entry point. */
export function main(argv, log = console.log, logError = console.error) {
  const parsed = parseArgs(argv, {
    values: ['product', 'version', 'repo', 'tag', 'dir', 'root'],
    flags: ['with-extras', 'published', 'help'],
    aliases: { h: 'help' },
  });
  if (parsed.flags.has('help')) {
    log(USAGE);
    return 0;
  }
  const [command, ...rest] = parsed.positional;
  const o = parsed.options;
  const needsProduct = ['list', 'check-dir', 'notes'].includes(command);
  if (!command) parsed.errors.push('a command is required');
  else if (!['list', 'check-dir', 'check-manifest', 'notes'].includes(command)) parsed.errors.push(`unknown command '${command}'`);
  if (needsProduct) {
    if (!PRODUCTS.includes(o.product)) parsed.errors.push('--product client|launcher is required');
    if (!isSemVer(o.version)) parsed.errors.push('--version <semver> is required');
  }
  const wantedPositionals = command === 'check-dir' || command === 'check-manifest' ? 1 : 0;
  if (command && rest.length !== wantedPositionals) parsed.errors.push(`${command} takes ${wantedPositionals === 1 ? 'exactly one path' : 'no positional arguments'}`);
  if (parsed.flags.has('published') && command !== 'check-manifest') parsed.errors.push('--published only applies to check-manifest');
  if (parsed.errors.length > 0) {
    for (const error of parsed.errors) logError(`error: ${error}`);
    logError(USAGE);
    return 2;
  }
  const root = o.root ? resolve(o.root) : REPO_ROOT;
  const toolchain = readToolchain(root);
  try {
    if (command === 'list') {
      for (const name of releaseAssetNames(o.product, o.version, toolchain)) log(name);
      return 0;
    }
    if (command === 'notes') {
      log(assetsMarkdown(releaseAssets(o.product, o.version, toolchain), o.dir ? resolve(o.dir) : undefined).trimEnd());
      return 0;
    }
    if (command === 'check-dir') {
      const expected = releaseAssetNames(o.product, o.version, toolchain);
      if (parsed.flags.has('with-extras')) expected.push(...extraAssetNames(o.product, o.version));
      const result = checkDirectory(resolve(rest[0]), expected);
      if (!result.ok) {
        for (const problem of result.problems) logError(`  ${problem}`);
        logError(`${rest[0]} does not hold exactly the ${o.product} ${o.version} release files.`);
        return 1;
      }
      log(`${rest[0]}: all ${expected.length} expected files present, nothing else.`);
      return 0;
    }
    // check-manifest
    let manifest;
    try {
      manifest = JSON.parse(readFileSync(rest[0], 'utf8'));
    } catch (error) {
      logError(`error: cannot read ${rest[0]}: ${error.message}`);
      return 2;
    }
    const result = checkManifest(manifest, { toolchain, published: parsed.flags.has('published'), repo: o.repo, tag: o.tag });
    if (!result.ok) {
      for (const problem of result.problems) logError(`  ${problem}`);
      logError(`${rest[0]} does not match the expected release files.`);
      return 1;
    }
    log(`${rest[0]}: files[] = ${result.expected.join(', ')}${parsed.flags.has('published') ? ' (all published)' : ''}`);
    return 0;
  } catch (error) {
    logError(`error: ${error.message}`);
    return 1;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(main(process.argv.slice(2)));
}
