import {
  ContentError,
  type FrontMatter,
  optionalString,
  parseFrontMatterTyped,
  requireDate,
} from './front-matter';

/**
 * Changelog loader for `website/content/changelog/<product>-<version>.md`.
 *
 * Files start with a front matter block validated against the shape of
 * `shared/schemas/changelog-entry.schema.json` (product, version, date, optional title and
 * minecraftVersion). The body uses the headings `## Added`, `## Improved`, `## Fixed` and
 * optionally `## Notes`; {@link parseChangelogSections} turns them into typed sections for the
 * changelog page. The entries are small and the download page shows an excerpt synchronously, so
 * this collection is loaded eagerly.
 */

export interface ContentDocument {
  /** Front matter key/value pairs as written (all strings). */
  readonly meta: Readonly<Record<string, string>>;
  /** Markdown body without the front matter block. */
  readonly body: string;
}

/** Splits front matter from the body, keeping every value as a string. Never throws. */
export function parseFrontMatter(raw: string): ContentDocument {
  const parsed = parseFrontMatterTyped(raw);
  return { meta: parsed.raw, body: parsed.body };
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

export const CHANGELOG_PRODUCTS = ['client', 'launcher', 'website'] as const;
export type ChangelogProduct = (typeof CHANGELOG_PRODUCTS)[number];

/** Section kinds in display order. Unknown headings are kept with `kind: 'other'`. */
export const SECTION_KINDS = ['added', 'improved', 'fixed', 'notes', 'other'] as const;
export type SectionKind = (typeof SECTION_KINDS)[number];

export interface ChangelogSection {
  readonly kind: SectionKind;
  /** Heading text as written, e.g. `Added`. */
  readonly heading: string;
  /** Markdown of the section (usually a bullet list). */
  readonly body: string;
  /** Number of top-level bullets. */
  readonly itemCount: number;
}

/** Release notes per product version, keyed by `<product>-<version>` (e.g. `client-1.0.0`). */
export interface ChangelogEntry extends ContentDocument {
  readonly id: string;
  readonly product: ChangelogProduct;
  readonly version: string;
  /** ISO date `YYYY-MM-DD`. */
  readonly date: string;
  readonly title: string;
  readonly minecraftVersion: string | undefined;
  /** Markdown before the first section heading. */
  readonly intro: string;
  readonly sections: readonly ChangelogSection[];
}

const PRODUCT_LABELS: Record<ChangelogProduct, string> = {
  client: 'VANTA Client',
  launcher: 'VANTA Launcher',
  website: 'Website',
};

/** Display name of a changelog product. */
export function productLabel(product: ChangelogProduct): string {
  return PRODUCT_LABELS[product];
}

function isProduct(value: string): value is ChangelogProduct {
  return (CHANGELOG_PRODUCTS as readonly string[]).includes(value);
}

function sectionKind(heading: string): SectionKind {
  const key = heading.trim().toLowerCase();
  return key === 'added' || key === 'improved' || key === 'fixed' || key === 'notes'
    ? key
    : 'other';
}

/** Splits a changelog body into the intro and its `## ` sections. */
export function parseChangelogSections(body: string): {
  intro: string;
  sections: ChangelogSection[];
} {
  const lines = body.split('\n');
  const introLines: string[] = [];
  const sections: { heading: string; lines: string[] }[] = [];
  let inFence = false;
  for (const line of lines) {
    if (/^\s*(```|~~~)/.test(line)) inFence = !inFence;
    const match = !inFence ? /^##\s+(.+?)\s*$/.exec(line) : null;
    if (match) {
      sections.push({ heading: match[1] ?? '', lines: [] });
      continue;
    }
    (sections[sections.length - 1]?.lines ?? introLines).push(line);
  }
  return {
    intro: introLines.join('\n').trim(),
    sections: sections.map((section) => {
      const text = section.lines.join('\n').trim();
      return {
        kind: sectionKind(section.heading),
        heading: section.heading,
        body: text,
        itemCount: text.split('\n').filter((line) => /^[-*]\s+/.test(line)).length,
      };
    }),
  };
}

/** Parses one changelog file; the file name supplies product and version when the front matter lacks them. */
export function parseChangelogEntry(path: string, raw: string): ChangelogEntry {
  const file = path.split('/').pop() ?? path;
  const id = file.replace(/\.md$/i, '');
  const meta: FrontMatter = parseFrontMatterTyped(raw);
  const [productFromId, ...rest] = id.split('-');
  const product = optionalString(meta, 'product') ?? productFromId ?? '';
  if (!isProduct(product)) {
    throw new ContentError(file, `"product" must be one of ${CHANGELOG_PRODUCTS.join(', ')}`);
  }
  const version = optionalString(meta, 'version') ?? rest.join('-');
  if (!/^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/.test(version)) {
    throw new ContentError(file, `"version" is not SemVer: ${version}`);
  }
  const { intro, sections } = parseChangelogSections(meta.body);
  return {
    meta: meta.raw,
    body: meta.body,
    id,
    product,
    version,
    date: requireDate(meta, 'date', file),
    title: optionalString(meta, 'title') ?? `${productLabel(product)} ${version}`,
    minecraftVersion: optionalString(meta, 'minecraftVersion'),
    intro,
    sections,
  };
}

const SEMVER = /^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$/;

/** Newest first: by date, then by version (SemVer), then by id. */
export function compareEntriesDesc(a: ChangelogEntry, b: ChangelogEntry): number {
  const byDate = b.date.localeCompare(a.date);
  if (byDate !== 0) return byDate;
  const pa = SEMVER.exec(a.version);
  const pb = SEMVER.exec(b.version);
  if (pa && pb) {
    for (let i = 1; i <= 3; i += 1) {
      const diff = Number(pb[i]) - Number(pa[i]);
      if (diff !== 0) return diff;
    }
  }
  return b.id.localeCompare(a.id);
}

/** Converts `import.meta.glob` results (`?raw`) into typed changelog entries, newest first. */
export function loadChangelog(modules: Readonly<Record<string, string>>): ChangelogEntry[] {
  return Object.entries(modules)
    .filter(([path]) => !/readme\.md$/i.test(path))
    .map(([path, raw]) => parseChangelogEntry(path, raw))
    .sort(compareEntriesDesc);
}

/** Every changelog entry of the website, loaded at build time. */
export const changelog: readonly ChangelogEntry[] = loadChangelog(
  import.meta.glob<string>('../../content/changelog/*.md', {
    eager: true,
    query: '?raw',
    import: 'default',
  }),
);

/** Sections the download card quotes from, in order of preference. Notes are never quoted. */
const HIGHLIGHT_KINDS = ['fixed', 'added', 'improved'] as const;

/** The first `limit` bullets of the given markdown bodies, in order. */
function excerptOf(bodies: readonly string[], limit: number): string[] {
  const items: string[] = [];
  for (const body of bodies) {
    if (items.length >= limit) break;
    items.push(...markdownExcerpt(body, limit - items.length));
  }
  return items;
}

/**
 * Up to `limit` bullets of a release as plain text for the short "In this release" excerpt of the
 * download card: the bullets of `## Fixed` first, then those of `## Added` and `## Improved`. When
 * these sections are missing or have no bullets, the excerpt falls back to the bullets of the intro
 * and of other sections in document order. Bullets of `## Notes` are never quoted.
 */
export function releaseHighlights(
  entry: Pick<ChangelogEntry, 'intro' | 'sections'>,
  limit = 3,
): string[] {
  const preferred = HIGHLIGHT_KINDS.flatMap((kind) =>
    entry.sections.filter((section) => section.kind === kind),
  );
  const highlights = excerptOf(
    preferred.map((section) => section.body),
    limit,
  );
  if (highlights.length > 0) return highlights;
  const fallback = entry.sections.filter((section) => section.kind !== 'notes');
  return excerptOf([entry.intro, ...fallback.map((section) => section.body)], limit);
}

/** Finds the release notes for a product version. */
export function findChangelog(product: string, version: string): ChangelogEntry | undefined {
  return changelog.find((entry) => entry.product === product && entry.version === version);
}

/** Products that have at least one entry, in canonical order. */
export function changelogProducts(entries: readonly ChangelogEntry[]): ChangelogProduct[] {
  return CHANGELOG_PRODUCTS.filter((product) => entries.some((e) => e.product === product));
}
