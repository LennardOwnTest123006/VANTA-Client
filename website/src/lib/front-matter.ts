/**
 * Tiny front matter parser for the YAML subset used by the repository's content files.
 *
 * Supported: a leading `---` block, flat `key: value` lines, quoted or unquoted strings, integers,
 * `true`/`false`, inline arrays `[a, b, "c"]` and `#` comments on their own line. Nested objects,
 * multi-line strings and anchors are intentionally not supported — the JSON schemas in
 * `shared/schemas/` describe the same flat shape, and `scripts/release/validate-json.mjs` validates
 * the files against them in CI. Values keep their raw text in `raw` so a renderer can show exactly
 * what the author wrote; the typed accessors below convert on demand.
 */

export type FrontMatterValue = string | number | boolean | readonly string[];

export interface FrontMatter {
  /** Parsed values: strings, integers, booleans and string arrays. */
  readonly values: Readonly<Record<string, FrontMatterValue>>;
  /** Raw text of every value as written (quotes removed), for callers that only need strings. */
  readonly raw: Readonly<Record<string, string>>;
  /** True when the document started with a front matter block. */
  readonly hasFrontMatter: boolean;
  /** Markdown body without the block, trimmed. */
  readonly body: string;
}

const BLOCK = /^---[ \t]*\n([\s\S]*?)\n---[ \t]*(?:\n|$)/;

/** Removes one layer of matching single or double quotes. */
function unquote(value: string): string {
  if (value.length >= 2) {
    const first = value[0];
    const last = value[value.length - 1];
    if ((first === '"' || first === "'") && first === last) return value.slice(1, -1);
  }
  return value;
}

/** Splits `[a, "b, c", d]` into its items, honouring quotes. */
export function parseInlineArray(text: string): readonly string[] {
  const inner = text.trim().slice(1, -1);
  const items: string[] = [];
  let current = '';
  let quote: string | undefined;
  for (const char of inner) {
    if (quote) {
      if (char === quote) quote = undefined;
      else current += char;
      continue;
    }
    if (char === '"' || char === "'") {
      quote = char;
      continue;
    }
    if (char === ',') {
      items.push(current.trim());
      current = '';
      continue;
    }
    current += char;
  }
  if (current.trim() !== '' || items.length > 0) items.push(current.trim());
  return items.filter((item) => item !== '');
}

/** Converts a scalar front matter value to its typed representation. */
export function parseScalar(text: string): FrontMatterValue {
  const trimmed = text.trim();
  if (trimmed.startsWith('[') && trimmed.endsWith(']')) return parseInlineArray(trimmed);
  if (/^["'].*["']$/.test(trimmed)) return unquote(trimmed);
  if (trimmed === 'true') return true;
  if (trimmed === 'false') return false;
  if (/^-?\d+$/.test(trimmed)) return Number(trimmed);
  return trimmed;
}

/** Parses a markdown document with optional front matter. Never throws. */
export function parseFrontMatterTyped(source: string): FrontMatter {
  const normalized = source.replace(/\r\n/g, '\n').replace(/^\uFEFF/, '');
  const match = BLOCK.exec(normalized);
  if (!match) {
    return { values: {}, raw: {}, hasFrontMatter: false, body: normalized.trim() };
  }
  const values: Record<string, FrontMatterValue> = {};
  const raw: Record<string, string> = {};
  for (const line of (match[1] ?? '').split('\n')) {
    if (line.trim() === '' || line.trimStart().startsWith('#')) continue;
    const separator = line.indexOf(':');
    if (separator === -1) continue;
    const key = line.slice(0, separator).trim();
    if (key === '') continue;
    const text = line.slice(separator + 1).trim();
    values[key] = parseScalar(text);
    raw[key] = text.startsWith('[') ? text : unquote(text);
  }
  return { values, raw, hasFrontMatter: true, body: normalized.slice(match[0].length).trim() };
}

/** Thrown when a content file lacks a required field or has a field of the wrong type. */
export class ContentError extends Error {
  constructor(source: string, detail: string) {
    super(`Invalid content file ${source}: ${detail}`);
    this.name = 'ContentError';
  }
}

/** Returns the string value of a required key. */
export function requireString(meta: FrontMatter, key: string, source: string): string {
  const value = meta.values[key];
  if (typeof value === 'string' && value.trim() !== '') return value;
  if (typeof value === 'number') return String(value);
  throw new ContentError(source, `"${key}" must be a non-empty string`);
}

/** Returns the string value of an optional key, or `undefined`. */
export function optionalString(meta: FrontMatter, key: string): string | undefined {
  const value = meta.values[key];
  if (typeof value === 'string' && value.trim() !== '') return value;
  if (typeof value === 'number') return String(value);
  return undefined;
}

/** Returns a required `YYYY-MM-DD` date string. */
export function requireDate(meta: FrontMatter, key: string, source: string): string {
  const value = requireString(meta, key, source);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new ContentError(source, `"${key}" must be a YYYY-MM-DD date, got "${value}"`);
  }
  return value;
}

/** Returns a required non-negative integer. */
export function requireInteger(meta: FrontMatter, key: string, source: string): number {
  const value = meta.values[key];
  if (typeof value === 'number' && Number.isInteger(value) && value >= 0) return value;
  throw new ContentError(source, `"${key}" must be a non-negative integer`);
}

/** Returns an optional string array (an inline `[a, b]` list); a single string becomes a one-item list. */
export function optionalList(meta: FrontMatter, key: string): readonly string[] {
  const value = meta.values[key];
  if (typeof value === 'object') return value;
  if (typeof value === 'string' && value.trim() !== '') return [value];
  return [];
}

/** Returns an optional boolean (defaults to `false`). */
export function optionalBoolean(meta: FrontMatter, key: string): boolean {
  return meta.values[key] === true;
}
