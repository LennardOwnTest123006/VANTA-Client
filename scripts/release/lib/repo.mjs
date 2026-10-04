import { readFileSync, writeFileSync, mkdirSync, renameSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/** Absolute path of the repository root (two levels above scripts/release/lib). */
export const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');

/** Pinned toolchain versions used when no manifest exists yet; kept in sync with the BRIEF. */
export const DEFAULT_TOOLCHAIN = Object.freeze({
  minecraftVersion: '1.21.11',
  fabricVersion: '0.19.5',
  fabricApiVersion: '0.141.6+1.21.11',
  javaVersion: 21,
});

/**
 * Parses a Java `.properties` file (key=value lines, `#` comments) into a plain object.
 * @param {string} text
 * @returns {Record<string, string>}
 */
export function parseProperties(text) {
  const out = {};
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (line === '' || line.startsWith('#') || line.startsWith('!')) continue;
    const match = /^([^=:\s]+)\s*[=:]\s*(.*)$/.exec(line);
    if (match) out[match[1]] = match[2].trim();
  }
  return out;
}

/**
 * Reads the pinned toolchain from client/gradle.properties when available, else the defaults.
 * @param {string} root repository root
 */
export function readToolchain(root = REPO_ROOT) {
  const file = resolve(root, 'client', 'gradle.properties');
  if (!existsSync(file)) return { ...DEFAULT_TOOLCHAIN };
  const props = parseProperties(readFileSync(file, 'utf8'));
  return {
    minecraftVersion: props.minecraft_version ?? DEFAULT_TOOLCHAIN.minecraftVersion,
    fabricVersion: props.loader_version ?? DEFAULT_TOOLCHAIN.fabricVersion,
    fabricApiVersion: props.fabric_api_version ?? DEFAULT_TOOLCHAIN.fabricApiVersion,
    javaVersion: DEFAULT_TOOLCHAIN.javaVersion,
  };
}

/** Reads and parses a JSON file. */
export function readJson(path) {
  return JSON.parse(readFileSync(path, 'utf8'));
}

/**
 * Writes pretty JSON (2 spaces, trailing newline) atomically: temp file in the same directory, then rename.
 * @param {string} path
 * @param {unknown} value
 */
export function writeJsonAtomic(path, value) {
  writeTextAtomic(path, `${JSON.stringify(value, null, 2)}\n`);
}

/** Writes text atomically (temp file + rename), creating parent directories. */
export function writeTextAtomic(path, text) {
  mkdirSync(dirname(path), { recursive: true });
  const tmp = `${path}.${process.pid}.tmp`;
  writeFileSync(tmp, text, 'utf8');
  renameSync(tmp, path);
}

/** Today's date as YYYY-MM-DD in UTC. */
export function todayUtc(now = new Date()) {
  return now.toISOString().slice(0, 10);
}

/** Strict SemVer 2.0.0 pattern (MAJOR.MINOR.PATCH, optional pre-release and build metadata). */
export const SEMVER = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$/;

/** True when `value` is a valid SemVer string. */
export function isSemVer(value) {
  return typeof value === 'string' && SEMVER.test(value);
}

/**
 * Compares two SemVer strings (pre-release aware). Returns negative when a < b.
 */
export function compareSemVer(a, b) {
  const pa = SEMVER.exec(a);
  const pb = SEMVER.exec(b);
  if (!pa || !pb) throw new Error(`Not SemVer: ${!pa ? a : b}`);
  for (let i = 1; i <= 3; i += 1) {
    const diff = Number(pa[i]) - Number(pb[i]);
    if (diff !== 0) return diff;
  }
  const preA = pa[4];
  const preB = pb[4];
  if (preA === preB) return 0;
  if (preA === undefined) return 1;
  if (preB === undefined) return -1;
  const partsA = preA.split('.');
  const partsB = preB.split('.');
  for (let i = 0; i < Math.max(partsA.length, partsB.length); i += 1) {
    if (partsA[i] === undefined) return -1;
    if (partsB[i] === undefined) return 1;
    const numA = /^\d+$/.test(partsA[i]);
    const numB = /^\d+$/.test(partsB[i]);
    if (numA && numB) {
      const diff = Number(partsA[i]) - Number(partsB[i]);
      if (diff !== 0) return diff;
    } else if (numA !== numB) {
      return numA ? -1 : 1;
    } else if (partsA[i] !== partsB[i]) {
      return partsA[i] < partsB[i] ? -1 : 1;
    }
  }
  return 0;
}
