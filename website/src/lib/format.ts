/**
 * Formatting helpers for release facts. Pure functions, unit tested.
 */

const BYTE_UNITS = ['B', 'kB', 'MB', 'GB', 'TB'] as const;

/**
 * Formats a byte count using decimal units (1 kB = 1000 B), as file managers and browsers do.
 * Returns `undefined` for `0`, negative or non-finite input so callers can show an honest fallback.
 */
export function formatBytes(bytes: number, fractionDigits = 1): string | undefined {
  if (!Number.isFinite(bytes) || bytes <= 0) return undefined;
  let value = bytes;
  let unit = 0;
  while (value >= 1000 && unit < BYTE_UNITS.length - 1) {
    value /= 1000;
    unit += 1;
  }
  const digits = unit === 0 ? 0 : fractionDigits;
  return `${value.toFixed(digits)} ${BYTE_UNITS[unit] ?? 'B'}`;
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
