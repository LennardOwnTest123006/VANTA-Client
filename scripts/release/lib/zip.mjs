/**
 * Minimal, dependency-free ZIP/JAR reader for the release checks (listing entries and reading small ones).
 *
 * Supports the central directory (including ZIP64 end records and ZIP64 size/offset extra fields), entries that
 * are stored (method 0) or deflated (method 8) and both '/' and '\' separators (some Windows tools write the
 * latter; names are normalised to '/'). It reads the whole archive into memory, which is fine for release
 * artifacts (tens of megabytes). Encryption and multi-disk archives are rejected.
 */
import { readFileSync } from 'node:fs';
import { inflateRawSync } from 'node:zlib';

const EOCD_SIGNATURE = 0x06054b50;
const ZIP64_LOCATOR_SIGNATURE = 0x07064b50;
const ZIP64_EOCD_SIGNATURE = 0x06064b50;
const CENTRAL_SIGNATURE = 0x02014b50;
const LOCAL_SIGNATURE = 0x04034b50;

/**
 * @typedef {{ name: string, method: number, compressedSize: number, size: number, localOffset: number, directory: boolean, encrypted: boolean }} ZipEntry
 */

function readBigAsNumber(buffer, offset) {
  const value = buffer.readBigUInt64LE(offset);
  if (value > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error('ZIP64 value too large');
  return Number(value);
}

/** Finds the end-of-central-directory record (it may be followed by a comment of up to 65535 bytes). */
function findEocd(buffer) {
  const min = Math.max(0, buffer.length - 22 - 0xffff);
  for (let i = buffer.length - 22; i >= min; i -= 1) {
    if (buffer.readUInt32LE(i) === EOCD_SIGNATURE) return i;
  }
  throw new Error('not a ZIP archive (no end of central directory record)');
}

/**
 * Parses the central directory of a ZIP archive held in memory.
 * @param {Buffer} buffer
 * @returns {ZipEntry[]}
 */
export function listZipEntries(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 22) throw new Error('not a ZIP archive (too small)');
  const eocd = findEocd(buffer);
  if (buffer.readUInt16LE(eocd + 4) !== 0 || buffer.readUInt16LE(eocd + 6) !== 0) throw new Error('multi-disk ZIP archives are not supported');
  let count = buffer.readUInt16LE(eocd + 10);
  let cdOffset = buffer.readUInt32LE(eocd + 16);
  if (count === 0xffff || cdOffset === 0xffffffff) {
    const locator = eocd - 20;
    if (locator < 0 || buffer.readUInt32LE(locator) !== ZIP64_LOCATOR_SIGNATURE) throw new Error('ZIP64 archive without a ZIP64 locator');
    const record = readBigAsNumber(buffer, locator + 8);
    if (buffer.readUInt32LE(record) !== ZIP64_EOCD_SIGNATURE) throw new Error('ZIP64 end of central directory record not found');
    count = readBigAsNumber(buffer, record + 32);
    cdOffset = readBigAsNumber(buffer, record + 48);
  }
  const entries = [];
  let p = cdOffset;
  for (let i = 0; i < count; i += 1) {
    if (p + 46 > buffer.length || buffer.readUInt32LE(p) !== CENTRAL_SIGNATURE) throw new Error(`corrupt central directory at entry ${i}`);
    const flags = buffer.readUInt16LE(p + 8);
    const method = buffer.readUInt16LE(p + 10);
    let compressedSize = buffer.readUInt32LE(p + 20);
    let size = buffer.readUInt32LE(p + 24);
    const nameLength = buffer.readUInt16LE(p + 28);
    const extraLength = buffer.readUInt16LE(p + 30);
    const commentLength = buffer.readUInt16LE(p + 32);
    let localOffset = buffer.readUInt32LE(p + 42);
    const rawName = buffer.subarray(p + 46, p + 46 + nameLength).toString('utf8');
    // ZIP64 extended information: only the fields whose 32-bit value is saturated are present, in this order.
    let e = p + 46 + nameLength;
    const extraEnd = e + extraLength;
    while (e + 4 <= extraEnd) {
      const id = buffer.readUInt16LE(e);
      const length = buffer.readUInt16LE(e + 2);
      if (id === 0x0001) {
        let q = e + 4;
        if (size === 0xffffffff) { size = readBigAsNumber(buffer, q); q += 8; }
        if (compressedSize === 0xffffffff) { compressedSize = readBigAsNumber(buffer, q); q += 8; }
        if (localOffset === 0xffffffff) { localOffset = readBigAsNumber(buffer, q); }
      }
      e += 4 + length;
    }
    const name = rawName.replace(/\\/g, '/');
    entries.push({ name, method, compressedSize, size, localOffset, directory: name.endsWith('/'), encrypted: (flags & 1) === 1 });
    p = extraEnd + commentLength;
  }
  return entries;
}

/**
 * Returns the uncompressed bytes of one entry.
 * @param {Buffer} buffer archive bytes
 * @param {ZipEntry} entry an entry returned by {@link listZipEntries}
 */
export function readZipEntry(buffer, entry) {
  if (entry.encrypted) throw new Error(`${entry.name} is encrypted`);
  const p = entry.localOffset;
  if (buffer.readUInt32LE(p) !== LOCAL_SIGNATURE) throw new Error(`corrupt local header for ${entry.name}`);
  const start = p + 30 + buffer.readUInt16LE(p + 26) + buffer.readUInt16LE(p + 28);
  const data = buffer.subarray(start, start + entry.compressedSize);
  let out;
  if (entry.method === 0) out = Buffer.from(data);
  else if (entry.method === 8) out = inflateRawSync(data);
  else throw new Error(`${entry.name} uses unsupported compression method ${entry.method}`);
  if (out.length !== entry.size) throw new Error(`${entry.name}: expected ${entry.size} bytes, got ${out.length}`);
  return out;
}

/**
 * Opens an archive from disk and returns helpers bound to it.
 * @param {string} path
 */
export function openZip(path) {
  const buffer = readFileSync(path);
  const entries = listZipEntries(buffer);
  const byName = new Map(entries.map((entry) => [entry.name, entry]));
  return {
    entries,
    has: (name) => byName.has(name),
    get: (name) => byName.get(name),
    read: (name) => {
      const entry = byName.get(name);
      if (!entry) throw new Error(`${name} is not in ${path}`);
      return readZipEntry(buffer, entry);
    },
  };
}

/**
 * Parses a JAR manifest (META-INF/MANIFEST.MF) into main attributes, joining continuation lines.
 * @param {string} text
 * @returns {Record<string, string>}
 */
export function parseJarManifest(text) {
  const attributes = {};
  let last = null;
  for (const line of text.split(/\r?\n/)) {
    if (line === '') break; // end of the main section
    if (line.startsWith(' ') && last) {
      attributes[last] += line.slice(1);
      continue;
    }
    const colon = line.indexOf(':');
    if (colon <= 0) continue;
    last = line.slice(0, colon).trim();
    attributes[last] = line.slice(colon + 1).trim();
  }
  return attributes;
}
