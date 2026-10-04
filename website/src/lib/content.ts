/**
 * Minimal content loader for markdown files in `website/content/**`.
 *
 * Files start with a YAML-like front matter block (`---` … `---`) containing flat `key: value`
 * pairs. Only strings are supported on purpose; dates are ISO strings. The next content-driven pages
 * (changelog, news, documentation) reuse {@link parseFrontMatter} and {@link markdownExcerpt}.
 */

export interface ContentDocument {
  /** Front matter key/value pairs (all strings). */
  readonly meta: Readonly<Record<string, string>>;
  /** Markdown body without the front matter block. */
  readonly body: string;
}

/** Splits front matter from the body. Documents without front matter get an empty `meta`. */
export function parseFrontMatter(raw: string): ContentDocument {
  const normalized = raw.replace(/\r\n/g, '\n');
  const match = /^---\n([\s\S]*?)\n---\n?/.exec(normalized);
  if (!match) return { meta: {}, body: normalized.trim() };
  const meta: Record<string, string> = {};
  for (const line of (match[1] ?? '').split('\n')) {
    const separator = line.indexOf(':');
    if (separator === -1) continue;
    const key = line.slice(0, separator).trim();
    const value = line
      .slice(separator + 1)
      .trim()
      .replace(/^["'](.*)["']$/, '$1');
    if (key !== '') meta[key] = value;
  }
  return { meta, body: normalized.slice(match[0].length).trim() };
}

/**
 * Returns the first `limit` top-level bullet points of a markdown body as plain text, which is
 * enough for a changelog excerpt without pulling the markdown renderer into the initial bundle.
 */
export function markdownExcerpt(body: string, limit = 4): string[] {
  const items: string[] = [];
  for (const line of body.split('\n')) {
    const match = /^[-*]\s+(.*)$/.exec(line);
    if (!match) continue;
    const text = (match[1] ?? '')
      .replace(/\*\*(.+?)\*\*/g, '$1')
      .replace(/`(.+?)`/g, '$1')
      .replace(/\[(.+?)\]\((.+?)\)/g, '$1')
      .trim();
    if (text !== '') items.push(text);
    if (items.length >= limit) break;
  }
  return items;
}

/** Release notes per product version, keyed by `<product>-<version>` (e.g. `client-1.0.0`). */
export interface ChangelogEntry extends ContentDocument {
  readonly id: string;
  readonly product: string;
  readonly version: string;
}

/** Converts `import.meta.glob` results (`?raw`) into typed changelog entries. */
export function loadChangelog(modules: Readonly<Record<string, string>>): ChangelogEntry[] {
  return Object.entries(modules)
    .map(([path, raw]) => {
      const id = (path.split('/').pop() ?? path).replace(/\.md$/, '');
      const doc = parseFrontMatter(raw);
      const [productFromId, ...rest] = id.split('-');
      return {
        ...doc,
        id,
        product: doc.meta.product ?? productFromId ?? '',
        version: doc.meta.version ?? rest.join('-'),
      };
    })
    .sort((a, b) => (a.id < b.id ? 1 : a.id > b.id ? -1 : 0));
}

/** Every changelog entry of the website, loaded at build time. */
export const changelog: readonly ChangelogEntry[] = loadChangelog(
  import.meta.glob('../../content/changelog/*.md', {
    eager: true,
    query: '?raw',
    import: 'default',
  }),
);

/** Finds the release notes for a product version. */
export function findChangelog(product: string, version: string): ChangelogEntry | undefined {
  return changelog.find((entry) => entry.product === product && entry.version === version);
}
