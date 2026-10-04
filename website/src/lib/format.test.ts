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
