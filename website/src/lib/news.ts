import {
  ContentError,
  type FrontMatter,
  optionalBoolean,
  optionalList,
  parseFrontMatterTyped,
  requireDate,
  requireString,
} from './front-matter';
import { readingTime } from './format';
import { trackSettled } from './thenable';

/**
 * Loader for news posts in `website/content/news/<yyyy-mm-dd>-<slug>.md`.
 *
 * Front matter follows `shared/schemas/news-post.schema.json` (title, date, summary, author,
 * optional tags and draft). Posts are loaded lazily and bundled into one `news-content` chunk.
 */

export interface NewsPost {
  /** URL slug: file name without the date prefix. */
  readonly slug: string;
  readonly file: string;
  readonly title: string;
  /** ISO date `YYYY-MM-DD`. */
  readonly date: string;
  readonly summary: string;
  readonly author: string;
  readonly tags: readonly string[];
  readonly draft: boolean;
  readonly body: string;
  /** Estimated reading time in minutes (at least 1). */
  readonly minutes: number;
}

const FILE_NAME = /^(\d{4}-\d{2}-\d{2})-([a-z0-9][a-z0-9-]*)\.md$/;

/** Parses one post. Throws {@link ContentError} for a bad file name or missing fields. */
export function parseNewsPost(path: string, raw: string): NewsPost {
  const file = path.split('/').pop() ?? path;
  const nameMatch = FILE_NAME.exec(file);
  if (!nameMatch) {
    throw new ContentError(file, 'file name must be <yyyy-mm-dd>-<slug>.md');
  }
  const meta: FrontMatter = parseFrontMatterTyped(raw);
  if (!meta.hasFrontMatter) throw new ContentError(file, 'front matter block is missing');
  const summary = requireString(meta, 'summary', file);
  if (summary.length < 10 || summary.length > 300) {
    throw new ContentError(file, '"summary" must be 10–300 characters');
  }
  const tags = optionalList(meta, 'tags');
  for (const tag of tags) {
    if (!/^[a-z0-9][a-z0-9-]{0,31}$/.test(tag)) {
      throw new ContentError(file, `tag "${tag}" must be lower-case letters, digits and hyphens`);
    }
  }
  return {
    slug: nameMatch[2] ?? '',
    file,
    title: requireString(meta, 'title', file),
    date: requireDate(meta, 'date', file),
    summary,
    author: requireString(meta, 'author', file),
    tags,
    draft: optionalBoolean(meta, 'draft'),
    body: meta.body,
    minutes: readingTime(meta.body),
  };
}

/** Parses every module (path → markdown), skips README files and drafts, newest first. */
export function parseNews(modules: Readonly<Record<string, string>>): NewsPost[] {
  const posts = Object.entries(modules)
    .filter(([path]) => !/readme\.md$/i.test(path))
    .map(([path, raw]) => parseNewsPost(path, raw))
    .filter((post) => !post.draft);
  const slugs = new Set<string>();
  for (const post of posts) {
    if (slugs.has(post.slug)) throw new ContentError(post.file, 'duplicate slug');
    slugs.add(post.slug);
  }
  return posts.sort((a, b) => b.date.localeCompare(a.date) || b.file.localeCompare(a.file));
}

/** Previous (older) and next (newer) post around `slug`, in the newest-first list. */
export function newsNeighbours(
  posts: readonly NewsPost[],
  slug: string,
): { newer: NewsPost | undefined; older: NewsPost | undefined } {
  const index = posts.findIndex((post) => post.slug === slug);
  if (index === -1) return { newer: undefined, older: undefined };
  return { newer: posts[index - 1], older: posts[index + 1] };
}

const newsModules = import.meta.glob<string>('../../content/news/*.md', {
  query: '?raw',
  import: 'default',
});

let newsPromise: Promise<readonly NewsPost[]> | undefined;

/** Loads and validates every published post once; later calls reuse the result. */
export function loadNews(): Promise<readonly NewsPost[]> {
  newsPromise ??= trackSettled(
    Promise.all(
      Object.entries(newsModules).map(async ([path, load]) => [path, await load()] as const),
    ).then((entries) => parseNews(Object.fromEntries(entries))),
  );
  return newsPromise;
}

/** Slugs of every news file (drafts included — they are filtered once the body is loaded). */
export const newsSlugs: readonly string[] = Object.keys(newsModules)
  .map((path) => path.split('/').pop() ?? path)
  .filter((file) => FILE_NAME.test(file))
  .map((file) => FILE_NAME.exec(file)?.[2] ?? '')
  .sort();
