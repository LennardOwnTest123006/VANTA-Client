/**
 * Tiny ustar writer used by the release script tests to build fixture .tar.gz archives in memory (regular files,
 * directories, symbolic links, GNU long names for names above 100 characters). Not used by the release itself.
 */
import { gzipSync } from 'node:zlib';

const BLOCK = 512;

function octal(value, length) {
  return `${value.toString(8).padStart(length - 1, '0')}\0`;
}

function header({ name, size, mode, type, linkName = '' }) {
  const block = Buffer.alloc(BLOCK);
  block.write(name, 0, 100, 'utf8');
  block.write(octal(mode, 8), 100, 8, 'latin1');
  block.write(octal(0, 8), 108, 8, 'latin1');
  block.write(octal(0, 8), 116, 8, 'latin1');
  block.write(octal(size, 12), 124, 12, 'latin1');
  block.write(octal(0, 12), 136, 12, 'latin1');
  block.write('        ', 148, 8, 'latin1'); // checksum placeholder
  block.write(type, 156, 1, 'latin1');
  block.write(linkName, 157, 100, 'utf8');
  block.write('ustar\0', 257, 6, 'latin1');
  block.write('00', 263, 2, 'latin1');
  let sum = 0;
  for (const byte of block) sum += byte;
  block.write(`${sum.toString(8).padStart(6, '0')}\0 `, 148, 8, 'latin1');
  return block;
}

function padded(data) {
  const rest = data.length % BLOCK;
  return rest === 0 ? data : Buffer.concat([data, Buffer.alloc(BLOCK - rest)]);
}

/**
 * Builds a tar archive.
 * @param {Array<{ name: string, data?: Buffer|string, mode?: number, directory?: boolean, symlink?: string }>} entries
 *        a name ending in '/' or `directory: true` is a directory; `symlink` makes a symbolic link to that target
 * @returns {Buffer} the uncompressed tar bytes
 */
export function buildTar(entries) {
  const parts = [];
  for (const entry of entries) {
    const isDir = entry.directory || entry.name.endsWith('/');
    const data = Buffer.isBuffer(entry.data) ? entry.data : Buffer.from(entry.data ?? '', 'utf8');
    const type = entry.symlink !== undefined ? '2' : isDir ? '5' : '0';
    const mode = entry.mode ?? (isDir ? 0o755 : 0o644);
    let name = entry.name;
    if (Buffer.byteLength(name, 'utf8') > 100) {
      // GNU long name: a 'L' entry carrying the full name, then the real header with a truncated name.
      const nameBytes = Buffer.from(`${name}\0`, 'utf8');
      parts.push(header({ name: '././@LongLink', size: nameBytes.length, mode: 0o644, type: 'L' }), padded(nameBytes));
      name = name.slice(0, 100);
    }
    const size = type === '0' ? data.length : 0;
    parts.push(header({ name, size, mode, type, linkName: entry.symlink ?? '' }));
    if (size > 0) parts.push(padded(data));
  }
  parts.push(Buffer.alloc(BLOCK * 2));
  return Buffer.concat(parts);
}

/** Builds a gzipped tar archive. */
export function buildTarGz(entries) {
  return gzipSync(buildTar(entries));
}
