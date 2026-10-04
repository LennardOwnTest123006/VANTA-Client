import {
  ContentError,
  type FrontMatter,
  parseFrontMatterTyped,
  requireInteger,
  requireString,
} from './front-matter';
import { extractHeadings, type Heading, slugify } from './slug';
import { trackSettled } from './thenable';

/**
 * Loader for the documentation in `docs/*.md` at the repository root.
 *
 * Front matter is validated against the shape of `shared/schemas/doc-page.schema.json`
 * (title, description, order, category). The files are loaded lazily — `import.meta.glob` with
 * `eager: false` — so the markdown only travels to the browser on documentation routes, and all
 * pages are bundled into one `docs-content` chunk (see `vite.config.ts`) so the sidebar, search and
 * prev/next links need a single request.
 */

/** Documentation categories in sidebar order (mirrors the schema enum). */
export const DOC_CATEGORIES = [
  'Getting started',
  'Launcher',
  'Client',
  'Reference',
  'Help',
] as const;
export type DocCategory = (typeof DOC_CATEGORIES)[number];

export interface DocPage {
  /** File name without extension, e.g. `installation`. */
  readonly slug: string;
  /** File name, e.g. `installation.md`. */
  readonly file: string;
  readonly title: string;
  readonly description: string;
  readonly order: number;
  readonly category: DocCategory;
  /** Route on the website: `/documentation/<slug>`, `/documentation` for the index, `/privacy`, `/terms`. */
  readonly route: string;
  /** Markdown body without front matter. */
  readonly body: string;
  /** Level 2 and 3 headings with their ids, for the on-this-page navigation. */
  readonly headings: readonly Heading[];
}

/** Slugs rendered on dedicated routes instead of `/documentation/<slug>`. */
const SPECIAL_ROUTES: Readonly<Record<string, string>> = {
  index: '/documentation',
  privacy: '/privacy',
  terms: '/terms',
};

/** Website route of a documentation slug. */
export function docRoute(slug: string): string {
  return SPECIAL_ROUTES[slug] ?? `/documentation/${slug}`;
}

/** Slug of a `docs/` file path or name (`../docs/java-21.md` → `java-21`). */
export function docSlugFromPath(path: string): string {
  return (path.split('/').pop() ?? path).replace(/\.md$/i, '');
}

function isCategory(value: string): value is DocCategory {
  return (DOC_CATEGORIES as readonly string[]).includes(value);
}

/** Parses one documentation file. Throws {@link ContentError} for missing or malformed fields. */
export function parseDocPage(path: string, raw: string): DocPage {
  const file = path.split('/').pop() ?? path;
  const slug = docSlugFromPath(file);
  if (!/^[a-z0-9][a-z0-9-]*$/.test(slug)) {
    throw new ContentError(file, 'file name must be lower-case letters, digits and hyphens');
  }
  const meta: FrontMatter = parseFrontMatterTyped(raw);
  if (!meta.hasFrontMatter) throw new ContentError(file, 'front matter block is missing');
  const category = requireString(meta, 'category', file);
  if (!isCategory(category)) {
    throw new ContentError(file, `"category" must be one of ${DOC_CATEGORIES.join(', ')}`);
  }
  const description = requireString(meta, 'description', file);
  if (description.length < 10) throw new ContentError(file, '"description" is too short');
  return {
    slug,
    file,
    title: requireString(meta, 'title', file),
    description,
    order: requireInteger(meta, 'order', file),
    category,
    route: docRoute(slug),
    body: meta.body,
    headings: extractHeadings(meta.body, 2, 3),
  };
}

/** Sorts pages by category (sidebar order), then `order`, then title. */
export function sortDocs(pages: readonly DocPage[]): DocPage[] {
  const rank = (page: DocPage) => DOC_CATEGORIES.indexOf(page.category);
  return [...pages].sort(
    (a, b) => rank(a) - rank(b) || a.order - b.order || a.title.localeCompare(b.title),
  );
}

/** Converts raw modules (path → markdown) into sorted, validated pages. README files are ignored. */
export function parseDocs(modules: Readonly<Record<string, string>>): DocPage[] {
  const pages = Object.entries(modules)
    .filter(([path]) => !/readme\.md$/i.test(path))
    .map(([path, raw]) => parseDocPage(path, raw));
  const slugs = new Set<string>();
  for (const page of pages) {
    if (slugs.has(page.slug)) throw new ContentError(page.file, 'duplicate slug');
    slugs.add(page.slug);
  }
  return sortDocs(pages);
}

export interface DocGroup {
  readonly category: DocCategory;
  readonly pages: readonly DocPage[];
}

/** Groups sorted pages by category, keeping the sidebar order and skipping empty categories. */
export function groupDocs(pages: readonly DocPage[]): DocGroup[] {
  return DOC_CATEGORIES.map((category) => ({
    category,
    pages: pages.filter((page) => page.category === category),
  })).filter((group) => group.pages.length > 0);
}

/** Previous and next page in reading order. */
export function docNeighbours(
  pages: readonly DocPage[],
  slug: string,
): { previous: DocPage | undefined; next: DocPage | undefined } {
  const index = pages.findIndex((page) => page.slug === slug);
  if (index === -1) return { previous: undefined, next: undefined };
  return { previous: pages[index - 1], next: pages[index + 1] };
}

/**
 * Maps a link written inside `docs/*.md` to a website URL.
 *
 * - `page.md`, `./page.md`, `page.md#anchor` → the page's route (plus hash), when the page exists;
 * - `#anchor` and absolute URLs are returned unchanged (`undefined` means "keep as is").
 */
export function resolveDocLink(href: string, knownSlugs?: ReadonlySet<string>): string | undefined {
  const match = /^(?:\.\/)?([a-z0-9][a-z0-9-]*)\.md(#.*)?$/i.exec(href.trim());
  if (!match) return undefined;
  const slug = (match[1] ?? '').toLowerCase();
  if (knownSlugs && !knownSlugs.has(slug)) return undefined;
  const hash = match[2] ?? '';
  return `${docRoute(slug)}${hash ? `#${slugify(decodeURIComponent(hash.slice(1)))}` : ''}`;
}

/** Repository URL of a documentation file, for "Edit on GitHub". */
export function docEditUrl(repositoryUrl: string, file: string): string {
  return `${repositoryUrl.replace(/\/+$/, '')}/blob/main/docs/${file}`;
}

const docModules = import.meta.glob<string>('../../../docs/*.md', {
  query: '?raw',
  import: 'default',
});

let docsPromise: Promise<readonly DocPage[]> | undefined;

/** Loads and validates every documentation page once; later calls reuse the result. */
export function loadDocs(): Promise<readonly DocPage[]> {
  docsPromise ??= trackSettled(
    Promise.all(
      Object.entries(docModules).map(async ([path, load]) => [path, await load()] as const),
    ).then((entries) => parseDocs(Object.fromEntries(entries))),
  );
  return docsPromise;
}

/** Slugs of every documentation file, available without loading the bodies. */
export const docSlugs: readonly string[] = Object.keys(docModules)
  .filter((path) => !/readme\.md$/i.test(path))
  .map(docSlugFromPath)
  .sort();

const knownSlugs: ReadonlySet<string> = new Set(docSlugs);

/** Link resolver for markdown rendered from `docs/`: maps `page.md#anchor` to website routes. */
export const docLinkResolver = (href: string): string | undefined =>
  resolveDocLink(href, knownSlugs);
