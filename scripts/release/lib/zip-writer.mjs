/**
 * Tiny ZIP writer used by the release script tests to build fixture jars and archives in memory
 * (stored or deflated entries, no ZIP64). Not used by the release itself.
 */
import { deflateRawSync } from 'node:zlib';

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n += 1) {
    let c = n;
    for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();

/** CRC-32 (ZIP polynomial) of a buffer. */
export function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

/**
 * Builds a ZIP archive.
 * @param {Array<{ name: string, data?: Buffer|string, deflate?: boolean }>} entries names ending in '/' are directories
 * @param {{ comment?: string }} [options]
 * @returns {Buffer}
 */
export function buildZip(entries, options = {}) {
  const locals = [];
  const centrals = [];
  let offset = 0;
  for (const entry of entries) {
    const name = Buffer.from(entry.name, 'utf8');
    const data = Buffer.isBuffer(entry.data) ? entry.data : Buffer.from(entry.data ?? '', 'utf8');
    const method = entry.deflate ? 8 : 0;
    const stored = method === 8 ? deflateRawSync(data) : data;
    const crc = crc32(data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6); // UTF-8 names
    local.writeUInt16LE(method, 8);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(stored.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(name.length, 26);
    locals.push(local, name, stored);
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0x0800, 8);
    central.writeUInt16LE(method, 10);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(stored.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(name.length, 28);
    central.writeUInt32LE(offset, 42);
    centrals.push(central, name);
    offset += local.length + name.length + stored.length;
  }
  const cd = Buffer.concat(centrals);
  const comment = Buffer.from(options.comment ?? '', 'utf8');
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(entries.length, 8);
  eocd.writeUInt16LE(entries.length, 10);
  eocd.writeUInt32LE(cd.length, 12);
  eocd.writeUInt32LE(offset, 16);
  eocd.writeUInt16LE(comment.length, 20);
  return Buffer.concat([...locals, cd, eocd, comment]);
}

/** Minimal native library headers for the architecture checks. */
export const fakeNative = {
  pe(machine = 0x8664) {
    const b = Buffer.alloc(0x100);
    b.write('MZ', 0, 'latin1');
    b.writeUInt32LE(0x80, 0x3c);
    b.writeUInt32LE(0x00004550, 0x80);
    b.writeUInt16LE(machine, 0x84);
    return b;
  },
  elf(machine = 0x3e) {
    const b = Buffer.alloc(64);
    b.writeUInt32BE(0x7f454c46, 0);
    b[4] = 2; // 64-bit
    b[5] = 1; // little endian
    b.writeUInt16LE(machine, 18);
    return b;
  },
  macho(cpu = 0x0100000c) {
    const b = Buffer.alloc(32);
    b.writeUInt32LE(0xfeedfacf, 0);
    b.writeUInt32LE(cpu, 4);
    return b;
  },
  fat(cpus) {
    const b = Buffer.alloc(8 + cpus.length * 20);
    b.writeUInt32BE(0xcafebabe, 0);
    b.writeUInt32BE(cpus.length, 4);
    cpus.forEach((cpu, i) => b.writeUInt32BE(cpu, 8 + i * 20));
    return b;
  },
};
