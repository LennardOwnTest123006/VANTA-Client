import { type DocPage } from './docs';
import { headingText, Slugger, stripFencedCode } from './slug';

/**
 * Documentation search: every page is split into sections (the intro plus one section per `##`
 * or `###` heading) and indexed with MiniSearch over the page title, section heading and plain
 * text. Results link straight to the section anchor and carry a snippet with the matched terms
 * highlighted. MiniSearch itself is imported on first use so the documentation chunk stays small.
 */

export interface SearchDocument {
  /** `slug#anchor` (or `slug` for the intro). */
  readonly id: string;
  readonly slug: string;
  readonly route: string;
  /** Route plus anchor, e.g. `/documentation/installation#2-verify-the-checksum`. */
  readonly href: string;
  readonly title: string;
  readonly category: string;
  /** Section heading; equals the page title for the intro section. */
  readonly heading: string;
  readonly text: string;
}

export interface SnippetPart {
  readonly text: string;
  readonly highlight: boolean;
}

export interface SearchHit extends SearchDocument {
  readonly score: number;
  readonly terms: readonly string[];
  readonly snippet: readonly SnippetPart[];
}

/** Reduces markdown to plain text for indexing and snippets. */
export function stripMarkdown(markdown: string): string {
  return stripFencedCode(markdown)
    .replace(/^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$/gm, '') // table separators
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/^\s{0,3}#{1,6}\s+/gm, '')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s*\d+\.\s+/gm, '')
    .replace(/^\s*>\s?/gm, '')
    .replace(/[*_~]{1,3}/g, '')
    .replace(/\|/g, ' · ')
    .replace(/[ \t]+/g, ' ')
    .replace(/\s*\n\s*/g, ' ')
    .replace(/(\s·\s)+/g, ' · ')
    .trim();
}

/** Splits a page into indexable sections, matching the heading ids the renderer produces. */
export function sectionize(page: DocPage): SearchDocument[] {
  const slugger = new Slugger();
  const sections: { heading: string; anchor: string; lines: string[] }[] = [
    { heading: page.title, anchor: '', lines: [] },
  ];
  const masked = stripFencedCode(page.body).split('\n');
  const original = page.body.split('\n');
  masked.forEach((line, index) => {
    const match = /^ {0,3}(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line);
    if (match) {
      const level = (match[1] ?? '#').length;
      const text = headingText(match[2] ?? '');
      const anchor = slugger.slug(text);
      if (level <= 3) {
        sections.push({ heading: text, anchor, lines: [] });
        return;
      }
    }
    sections[sections.length - 1]?.lines.push(original[index] ?? '');
  });
  return sections
    .map((section) => ({ ...section, text: stripMarkdown(section.lines.join('\n')) }))
    .filter((section) => section.text !== '' || section.anchor === '')
    .map((section) => ({
      id: section.anchor ? `${page.slug}#${section.anchor}` : page.slug,
      slug: page.slug,
      route: page.route,
      href: section.anchor ? `${page.route}#${section.anchor}` : page.route,
      title: page.title,
      category: page.category,
      heading: section.heading,
      text: section.text,
    }));
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Builds a snippet of roughly `radius` characters on each side of the first matched term, with
 * every matched term marked for highlighting. Falls back to the beginning of the text.
 */
export function buildSnippet(text: string, terms: readonly string[], radius = 80): SnippetPart[] {
  const words = terms.map((term) => term.trim()).filter((term) => term.length > 0);
  if (text === '') return [];
  const pattern =
    words.length > 0
      ? new RegExp(`(${words.map(escapeRegExp).join('|')})[\\p{L}\\p{N}]*`, 'giu')
      : undefined;
  const first = pattern ? pattern.exec(text) : null;
  let start = 0;
  let end = Math.min(text.length, radius * 2);
  if (first) {
    start = Math.max(0, first.index - radius);
    end = Math.min(text.length, first.index + first[0].length + radius);
    // Snap to word boundaries.
    if (start > 0) {
      const space = text.lastIndexOf(' ', start);
      start = space === -1 ? start : space + 1;
    }
    if (end < text.length) {
      const space = text.indexOf(' ', end);
      end = space === -1 ? text.length : space;
    }
  } else if (end < text.length) {
    const space = text.lastIndexOf(' ', end);
    if (space > radius) end = space;
  }
  const window = text.slice(start, end);
  const parts: SnippetPart[] = [];
  if (start > 0) parts.push({ text: '… ', highlight: false });
  if (!pattern) {
    parts.push({ text: window, highlight: false });
  } else {
    let cursor = 0;
    pattern.lastIndex = 0;
    for (const match of window.matchAll(pattern)) {
      const index = match.index;
      if (index > cursor) parts.push({ text: window.slice(cursor, index), highlight: false });
      parts.push({ text: match[0], highlight: true });
      cursor = index + match[0].length;
    }
    if (cursor < window.length) parts.push({ text: window.slice(cursor), highlight: false });
  }
  if (end < text.length) parts.push({ text: ' …', highlight: false });
  return parts.filter((part) => part.text !== '');
}

export interface DocsSearch {
  readonly documents: readonly SearchDocument[];
  search(query: string, limit?: number): SearchHit[];
}

/** Builds the search index for the given pages. Imports MiniSearch lazily. */
export async function createDocsSearch(pages: readonly DocPage[]): Promise<DocsSearch> {
  const { default: MiniSearch } = await import('minisearch');
  const documents = pages.flatMap(sectionize);
  const byId = new Map(documents.map((doc) => [doc.id, doc]));
  const index = new MiniSearch<SearchDocument>({
    idField: 'id',
    fields: ['title', 'heading', 'text'],
    storeFields: ['id'],
    searchOptions: {
      boost: { title: 4, heading: 3 },
      prefix: true,
      fuzzy: (term) => (term.length > 4 ? 0.2 : false),
      combineWith: 'AND',
    },
  });
  index.addAll(documents);
  return {
    documents,
    search(query, limit = 8) {
      const trimmed = query.trim();
      if (trimmed.length < 2) return [];
      let results = index.search(trimmed);
      if (results.length === 0) results = index.search(trimmed, { combineWith: 'OR' });
      const hits: SearchHit[] = [];
      const seenHref = new Set<string>();
      for (const result of results) {
        const doc = byId.get(String(result.id));
        if (!doc || seenHref.has(doc.href)) continue;
        seenHref.add(doc.href);
        hits.push({
          ...doc,
          score: result.score,
          terms: result.terms,
          snippet: buildSnippet(doc.text, result.terms),
        });
        if (hits.length >= limit) break;
      }
      return hits;
    },
  };
}
