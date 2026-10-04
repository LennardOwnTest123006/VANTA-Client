/**
 * Tiny YAML-subset front matter parser used to validate markdown content (docs/, website/content/).
 *
 * Supported: a leading `---` block with flat `key: value` lines, `# comments`, quoted strings,
 * inline arrays `[a, b, "c d"]`, integers/decimals and `true`/`false`. Anything else stays a string.
 * Nested mappings and multi-line values are intentionally unsupported: content files are kept flat so
 * the website can parse them with the same simple rules.
 */

const FRONT_MATTER = /^---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)/;

/**
 * @param {string} raw full markdown document
 * @returns {{ hasFrontMatter: boolean, meta: Record<string, unknown>, body: string }}
 */
export function parseFrontMatter(raw) {
  const text = raw.replace(/^﻿/, '');
  const match = FRONT_MATTER.exec(text);
  if (!match) return { hasFrontMatter: false, meta: {}, body: text };
  const meta = {};
  for (const rawLine of match[1].split(/\r?\n/)) {
    const line = rawLine.trim();
    if (line === '' || line.startsWith('#')) continue;
    const separator = line.indexOf(':');
    if (separator <= 0) continue;
    const key = line.slice(0, separator).trim();
    const value = line.slice(separator + 1).trim();
    meta[key] = parseScalar(value);
  }
  return { hasFrontMatter: true, meta, body: text.slice(match[0].length) };
}

/** Converts a single front matter value into a string, number, boolean or array. */
export function parseScalar(value) {
  if (value === '') return '';
  if (value.startsWith('[') && value.endsWith(']')) {
    const inner = value.slice(1, -1).trim();
    if (inner === '') return [];
    return splitInline(inner).map((item) => parseScalar(item.trim()));
  }
  if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
    return value.slice(1, -1);
  }
  if (value === 'true') return true;
  if (value === 'false') return false;
  if (/^-?\d+$/.test(value)) return Number.parseInt(value, 10);
  if (/^-?\d+\.\d+$/.test(value)) return Number.parseFloat(value);
  return value;
}

/** Splits `a, "b, c", d` on commas outside quotes. */
function splitInline(inner) {
  const items = [];
  let current = '';
  let quote = null;
  for (const char of inner) {
    if (quote) {
      current += char;
      if (char === quote) quote = null;
    } else if (char === '"' || char === "'") {
      quote = char;
      current += char;
    } else if (char === ',') {
      items.push(current);
      current = '';
    } else {
      current += char;
    }
  }
  items.push(current);
  return items;
}
