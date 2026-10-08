/**
 * Minimal, dependency-free tar reader for the Local AI runtime archives (llama.cpp publishes the Linux and macOS
 * builds as .tar.gz). Understands the ustar header with its name prefix, GNU long names (typeflag 'L') and pax
 * extended headers (typeflag 'x', record `path`), regular files, directories, symbolic and hard links. The whole
 * archive is held in memory (the runtime archives are tens of megabytes compressed, a few hundred uncompressed).
 *
 * Every entry name is checked before anything is written: absolute names, '.' or '..' segments and backslashes are
 * rejected, a symbolic link may only point inside the extraction root, so an archive can never write outside the
 * directory it is extracted into.
 */
import { chmodSync, mkdirSync, symlinkSync, writeFileSync, copyFileSync, readFileSync, existsSync, lstatSync, rmSync } from 'node:fs';
import { dirname, join, posix, resolve, sep } from 'node:path';
import { gunzipSync } from 'node:zlib';

const BLOCK = 512;

/**
 * @typedef {{ name: string, type: 'file'|'directory'|'symlink'|'hardlink'|'other', size: number, mode: number, linkName: string, offset: number }} TarEntry
 * `offset` is where the entry's data starts in the archive buffer; `mode` is the permission bits (octal in the header).
 */

function readString(buffer, start, length) {
  const slice = buffer.subarray(start, start + length);
  const end = slice.indexOf(0);
  return (end >= 0 ? slice.subarray(0, end) : slice).toString('utf8');
}

function readOctal(buffer, start, length) {
  const text = readString(buffer, start, length).trim();
  if (text === '') return 0;
  // GNU base-256 encoding for sizes above 8 GB: first byte 0x80.
  if (buffer[start] & 0x80) {
    let value = 0;
    for (let i = 1; i < length; i += 1) value = value * 256 + buffer[start + i];
    return value;
  }
  if (!/^[0-7]+$/.test(text)) throw new Error(`invalid octal field ${JSON.stringify(text)} in tar header`);
  return Number.parseInt(text, 8);
}

/** The pax `path` record of an extended header block, if any. */
function paxPath(data) {
  let p = 0;
  const text = data.toString('utf8');
  while (p < text.length) {
    const space = text.indexOf(' ', p);
    if (space < 0) break;
    const length = Number.parseInt(text.slice(p, space), 10);
    if (!Number.isInteger(length) || length <= 0) break;
    const record = text.slice(space + 1, p + length - 1);
    const eq = record.indexOf('=');
    if (eq > 0 && record.slice(0, eq) === 'path') return record.slice(eq + 1);
    p += length;
  }
  return null;
}

function typeOf(flag) {
  switch (flag) {
    case '0':
    case '\0':
    case '':
    case '7':
      return 'file';
    case '5':
      return 'directory';
    case '2':
      return 'symlink';
    case '1':
      return 'hardlink';
    default:
      return 'other';
  }
}

/** Normalises an entry name ('./a/b/' -> 'a/b'); throws on anything that could leave the extraction root. */
export function safeTarName(name) {
  if (name.includes('\\')) throw new Error(`unsafe tar entry name ${JSON.stringify(name)} (backslash)`);
  if (name.startsWith('/')) throw new Error(`unsafe tar entry name ${JSON.stringify(name)} (absolute)`);
  if (/^[A-Za-z]:/.test(name)) throw new Error(`unsafe tar entry name ${JSON.stringify(name)} (drive letter)`);
  const parts = name.split('/').filter((part) => part !== '' && part !== '.');
  if (parts.some((part) => part === '..')) throw new Error(`unsafe tar entry name ${JSON.stringify(name)} ('..' segment)`);
  return parts.join('/');
}

/**
 * Lists the entries of a tar archive held in memory (not gzipped).
 * @param {Buffer} buffer
 * @returns {TarEntry[]} every entry except the GNU/pax meta entries, with normalised names ('' for the root entry)
 */
export function listTarEntries(buffer) {
  const entries = [];
  let p = 0;
  let longName = null;
  while (p + BLOCK <= buffer.length) {
    const header = buffer.subarray(p, p + BLOCK);
    if (header.every((byte) => byte === 0)) break; // end-of-archive blocks
    const magic = readString(header, 257, 6);
    const rawName = readString(header, 0, 100);
    const mode = readOctal(header, 100, 8) & 0o7777;
    const size = readOctal(header, 124, 12);
    const flag = String.fromCharCode(header[156]);
    const linkName = readString(header, 157, 100);
    const prefix = magic.startsWith('ustar') ? readString(header, 345, 155) : '';
    const dataStart = p + BLOCK;
    const dataEnd = dataStart + size;
    if (dataEnd > buffer.length) throw new Error(`truncated tar archive at entry ${JSON.stringify(rawName)}`);
    const data = buffer.subarray(dataStart, dataEnd);
    if (flag === 'L') {
      longName = readString(data, 0, data.length);
    } else if (flag === 'x' || flag === 'g') {
      const path = flag === 'x' ? paxPath(data) : null;
      if (path !== null) longName = path;
    } else {
      const name = longName ?? (prefix ? `${prefix}/${rawName}` : rawName);
      longName = null;
      entries.push({ name: safeTarName(name), type: typeOf(flag), size, mode, linkName, offset: dataStart });
    }
    p = dataStart + Math.ceil(size / BLOCK) * BLOCK;
  }
  return entries;
}

/** gunzips a .tar.gz buffer; a plain tar passes through. */
export function ungzip(buffer) {
  if (buffer.length >= 2 && buffer[0] === 0x1f && buffer[1] === 0x8b) return gunzipSync(buffer);
  return buffer;
}

/** Reads a .tar.gz (or .tar) file and lists its entries. */
export function listTarGz(path) {
  const buffer = ungzip(readFileSync(path));
  return { buffer, entries: listTarEntries(buffer) };
}

/** True when something (a file, directory or a dangling symbolic link) exists at `path`. */
function exists(path) {
  try {
    lstatSync(path);
    return true;
  } catch {
    return false;
  }
}

/** True when `target` (a path on disk) is `root` or inside it. */
function inside(root, target) {
  const rel = resolve(target);
  return rel === root || rel.startsWith(root + sep);
}

/**
 * Extracts a .tar.gz (or .tar) file into `destDir`, which must exist or will be created. Regular files keep the
 * executable bits of their mode (on POSIX systems); directories are created; symbolic links are recreated only when
 * they point inside the extraction root (others are skipped and reported); hard links are copied. Anything else
 * (devices, FIFOs) is skipped and reported.
 * @param {string} archivePath
 * @param {string} destDir
 * @returns {{ files: string[], skipped: string[] }} the extracted relative file paths (regular files) in archive order
 */
export function extractTarGz(archivePath, destDir) {
  const { buffer, entries } = listTarGz(archivePath);
  const root = resolve(destDir);
  mkdirSync(root, { recursive: true });
  const files = [];
  const skipped = [];
  const posixPlatform = process.platform !== 'win32';
  for (const entry of entries) {
    if (entry.name === '') continue;
    const target = join(root, ...entry.name.split('/'));
    if (!inside(root, target)) throw new Error(`tar entry ${entry.name} resolves outside ${root}`);
    if (entry.type === 'directory') {
      mkdirSync(target, { recursive: true });
      continue;
    }
    mkdirSync(dirname(target), { recursive: true });
    if (entry.type === 'file') {
      if (exists(target) && lstatSync(target).isSymbolicLink()) rmSync(target);
      writeFileSync(target, buffer.subarray(entry.offset, entry.offset + entry.size));
      if (posixPlatform) chmodSync(target, (entry.mode & 0o111) ? 0o755 : 0o644);
      files.push(entry.name);
    } else if (entry.type === 'symlink') {
      const linkTarget = entry.linkName;
      const resolved = resolve(dirname(target), linkTarget);
      if (linkTarget === '' || posix.isAbsolute(linkTarget) || !inside(root, resolved)) {
        skipped.push(`${entry.name} -> ${linkTarget} (symbolic link outside the archive)`);
        continue;
      }
      if (exists(target)) rmSync(target, { force: true });
      symlinkSync(linkTarget, target);
    } else if (entry.type === 'hardlink') {
      const source = join(root, ...safeTarName(entry.linkName).split('/'));
      if (!inside(root, source) || !existsSync(source)) {
        skipped.push(`${entry.name} => ${entry.linkName} (hard link to a missing file)`);
        continue;
      }
      copyFileSync(source, target);
      files.push(entry.name);
    } else {
      skipped.push(`${entry.name} (unsupported entry type)`);
    }
  }
  return { files, skipped };
}
