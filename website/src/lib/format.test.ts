import { describe, expect, it } from 'vitest';
import {
  formatBytes,
  formatDate,
  formatVersion,
  groupHash,
  isSha256,
  parseIsoDate,
} from './format';

describe('formatBytes', () => {
  it('uses decimal units', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(1000)).toBe('1.0 kB');
    expect(formatBytes(1_536_000)).toBe('1.5 MB');
    expect(formatBytes(4_250_000_000)).toBe('4.3 GB');
  });
  it('returns undefined for unknown sizes so the UI can say so', () => {
    expect(formatBytes(0)).toBeUndefined();
    expect(formatBytes(-1)).toBeUndefined();
    expect(formatBytes(Number.NaN)).toBeUndefined();
    expect(formatBytes(Number.POSITIVE_INFINITY)).toBeUndefined();
    expect(formatBytes(1.5)).toBeUndefined();
  });
  // The same vectors are in scripts/release/release-assets.test.mjs (formatSize, the release notes) and the
  // launcher's ByteSizesTest, so a size reads the same in the release notes, on the download page and in the launcher.
  it('rounds half up in integer arithmetic and never prints 1000.0 of a unit', () => {
    const vectors: [number, string][] = [
      [999, '999 B'],
      [1_049, '1.0 kB'],
      [1_050, '1.1 kB'],
      [1_150, '1.2 kB'], // 1.15 is 1.149999... as a double; toFixed(1) would print 1.1
      [1_450_000, '1.5 MB'], // 1.45 is 1.4499999... as a double; toFixed(1) would print 1.4
      [2_450_000, '2.5 MB'],
      [999_949, '999.9 kB'],
      [999_950, '1.0 MB'], // not "1000.0 kB"
      [999_999, '1.0 MB'],
      [1_000_000, '1.0 MB'],
      [1_437_322, '1.4 MB'],
      [66_900_000, '66.9 MB'],
      [999_949_999, '999.9 MB'],
      [999_950_000, '1.0 GB'],
      [999_950_000_000, '1.0 TB'],
      [1_000_000_000_000_000, '1000.0 TB'], // largest unit: no unit to move up to
      [Number.MAX_SAFE_INTEGER, '9007.2 TB'],
    ];
    for (const [bytes, expected] of vectors)
      expect(formatBytes(bytes), `${bytes} bytes`).toBe(expected);
  });
});

describe('parseIsoDate / formatDate', () => {
  it('parses valid dates as UTC', () => {
    expect(parseIsoDate('2026-10-04')?.toISOString()).toBe('2026-10-04T00:00:00.000Z');
    expect(formatDate('2026-10-04')).toBe('October 4, 2026');
  });
  it('rejects malformed and overflowing dates', () => {
    expect(parseIsoDate('2026-13-01')).toBeUndefined();
    expect(parseIsoDate('2026-02-31')).toBeUndefined();
    expect(parseIsoDate('04.10.2026')).toBeUndefined();
    expect(formatDate('')).toBeUndefined();
  });
});

describe('hash helpers', () => {
  const sha = 'a'.repeat(64);
  it('validates SHA-256 digests', () => {
    expect(isSha256(sha)).toBe(true);
    expect(isSha256(sha.toUpperCase())).toBe(true);
    expect(isSha256('abc')).toBe(false);
    expect(isSha256('g'.repeat(64))).toBe(false);
  });
  it('groups digests for display', () => {
    expect(groupHash('0123456789abcdef0123456789ABCDEF')).toBe(
      '01234567 89abcdef 01234567 89abcdef',
    );
  });
});

describe('formatVersion', () => {
  it('prefixes a v once', () => {
    expect(formatVersion('1.0.0')).toBe('v1.0.0');
    expect(formatVersion('v1.0.0')).toBe('v1.0.0');
  });
});

describe('readingTime', () => {
  it('estimates whole minutes with a floor of one', async () => {
    const { readingTime, wordCount } = await import('./format');
    expect(readingTime('')).toBe(1);
    expect(readingTime('one two three')).toBe(1);
    expect(
      wordCount('# Title\n\n- **bold** item `code` | cell\n```\nignored words here\n```'),
    ).toBe(5);
    expect(readingTime(Array.from({ length: 660 }, () => 'word').join(' '))).toBe(3);
  });
});
