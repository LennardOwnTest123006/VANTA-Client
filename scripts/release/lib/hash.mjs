import { createHash } from 'node:crypto';
import { createReadStream } from 'node:fs';

/**
 * Streams a file and returns its size and lower-case hex SHA-256.
 * @param {string} path
 * @returns {Promise<{ size: number, sha256: string }>}
 */
export function hashFile(path) {
  return new Promise((resolve, reject) => {
    const hash = createHash('sha256');
    let size = 0;
    createReadStream(path)
      .on('data', (chunk) => {
        size += chunk.length;
        hash.update(chunk);
      })
      .on('error', reject)
      .on('end', () => resolve({ size, sha256: hash.digest('hex') }));
  });
}

/**
 * Hashes a web stream (fetch body) without buffering it.
 * @param {ReadableStream<Uint8Array>} stream
 * @returns {Promise<{ size: number, sha256: string }>}
 */
export async function hashWebStream(stream) {
  const hash = createHash('sha256');
  let size = 0;
  for await (const chunk of stream) {
    size += chunk.length;
    hash.update(chunk);
  }
  return { size, sha256: hash.digest('hex') };
}

/** True for a 64-character lower-case hex digest. */
export function isSha256Hex(value) {
  return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
}
