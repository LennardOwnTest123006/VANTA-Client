/**
 * Formatting helpers for release facts. Pure functions, unit tested.
 */

const BYTE_UNITS = ['B', 'kB', 'MB', 'GB', 'TB'] as const;

/**
 * `bytes` in tenths of `BYTE_UNITS[unit]`, rounded half up in exact integer arithmetic (no binary floating point
 * rounding, so 1,450,000 bytes is 15 tenths of a MB, not 14).
 */
function roundedTenths(bytes: number, unit: number): number {
  const divisor = 10 ** (3 * unit - 1); // one tenth of the unit in bytes
  const remainder = bytes % divisor;
  const tenths = (bytes - remainder) / divisor; // exact: the dividend is a multiple of the divisor
  return remainder * 2 >= divisor ? tenths + 1 : tenths;
}

/**
 * Formats a byte count using decimal units (1 kB = 1000 B, 1 MB = 1,000,000 B) with one decimal place above plain
 * bytes, e.g. "512 B", "1.4 MB" or "66.9 MB". Same rule as `formatSize()` in scripts/release/release-assets.mjs (the
 * release notes) and `ByteSizes` in the launcher: pick the largest unit not above the byte count, round half up to
 * tenths of that unit with integer arithmetic, and if that gives 1000.0 move up one unit ("1.0 MB" for 999,950
 * bytes, never "1000.0 kB").
 * Returns `undefined` for `0`, negative, fractional or unsafe input so callers can show an honest fallback.
 */
export function formatBytes(bytes: number): string | undefined {
  if (!Number.isSafeInteger(bytes) || bytes <= 0) return undefined;
  if (bytes < 1000) return `${bytes} ${BYTE_UNITS[0]}`;
  const last = BYTE_UNITS.length - 1;
  let unit = 1;
  while (unit < last && bytes >= 1000 ** (unit + 1)) unit += 1;
  let tenths = roundedTenths(bytes, unit);
  if (tenths >= 10_000 && unit < last) {
    unit += 1;
    tenths = roundedTenths(bytes, unit);
  }
  return `${Math.floor(tenths / 10)}.${tenths % 10} ${BYTE_UNITS[unit]}`;
}

/** Parses an ISO `YYYY-MM-DD` date as UTC midnight. Returns `undefined` for anything else. */
export function parseIsoDate(value: string): Date | undefined {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value.trim());
  if (!match) return undefined;
  const [, y, m, d] = match;
  const date = new Date(Date.UTC(Number(y), Number(m) - 1, Number(d)));
  if (Number.isNaN(date.getTime())) return undefined;
  // Reject overflowed dates such as 2026-02-31.
  if (date.getUTCMonth() !== Number(m) - 1 || date.getUTCDate() !== Number(d)) return undefined;
  return date;
}

/** Formats an ISO `YYYY-MM-DD` date as "October 4, 2026" (UTC, locale `en-US`). */
export function formatDate(iso: string, locale = 'en-US'): string | undefined {
  const date = parseIsoDate(iso);
  if (!date) return undefined;
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    timeZone: 'UTC',
  }).format(date);
}

/** Validates a lowercase/uppercase hex SHA-256 digest (64 hex characters). */
export function isSha256(value: string): boolean {
  return /^[0-9a-fA-F]{64}$/.test(value.trim());
}

/** Groups a SHA-256 digest into 8-character blocks for readable display. */
export function groupHash(hash: string, groupSize = 8): string {
  const clean = hash.trim().toLowerCase();
  const groups: string[] = [];
  for (let i = 0; i < clean.length; i += groupSize) {
    groups.push(clean.slice(i, i + groupSize));
  }
  return groups.join(' ');
}

/** Formats a semantic version for display, e.g. `1.0.0` → `v1.0.0`. */
export function formatVersion(version: string): string {
  return version.startsWith('v') ? version : `v${version}`;
}

/** Words in a markdown body, ignoring front matter markup noise. */
export function wordCount(text: string): number {
  const words = text
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/[#*_`>|-]+/g, ' ')
    .split(/\s+/)
    .filter((word) => /[\p{L}\p{N}]/u.test(word));
  return words.length;
}

/** Estimated reading time in whole minutes at ~220 words per minute, never less than 1. */
export function readingTime(text: string, wordsPerMinute = 220): number {
  return Math.max(1, Math.round(wordCount(text) / wordsPerMinute));
}
