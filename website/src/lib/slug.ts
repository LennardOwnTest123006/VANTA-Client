/**
 * Heading slugs, GitHub style: lower-case, markup and punctuation removed, spaces to hyphens,
 * duplicates suffixed with `-1`, `-2`, …. The same algorithm lives in
 * `scripts/release/check-links.mjs`, so cross-links written as `page.md#anchor` in `docs/` resolve
 * on the website exactly as they do on GitHub.
 */

/** Slug of one heading (without duplicate handling). */
export function slugify(heading: string): string {
  return heading
    .trim()
    .toLowerCase()
    .replace(/<[^>]+>/g, '')
    .replace(/[`*_~]/g, '')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .replace(/ /g, '-');
}

/** Stateful slugger that disambiguates repeated headings within one document. */
export class Slugger {
  private readonly seen = new Map<string, number>();

  slug(heading: string): string {
    const base = slugify(heading);
    const count = this.seen.get(base) ?? 0;
    this.seen.set(base, count + 1);
    return count === 0 ? base : `${base}-${count}`;
  }
}

export interface Heading {
  readonly level: number;
  readonly text: string;
  readonly id: string;
}

/** Replaces fenced code blocks with blank lines so headings inside them are ignored (line count kept). */
export function stripFencedCode(markdown: string): string {
  return markdown.replace(/```[\s\S]*?```|~~~[\s\S]*?~~~/g, (block) => block.replace(/[^\n]/g, ''));
}

/** Plain text of a heading line: inline code, emphasis and links reduced to their text. */
export function headingText(raw: string): string {
  return raw
    .replace(/`([^`]*)`/g, '$1')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[*_~]/g, '')
    .trim();
}

/**
 * Extracts ATX headings (`#` … `######`) from markdown with the ids the renderer will assign.
 * Headings inside fenced code blocks are skipped.
 */
export function extractHeadings(markdown: string, minLevel = 1, maxLevel = 6): Heading[] {
  const slugger = new Slugger();
  const headings: Heading[] = [];
  for (const line of stripFencedCode(markdown).split('\n')) {
    const match = /^ {0,3}(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line);
    if (!match) continue;
    const level = (match[1] ?? '#').length;
    const text = headingText(match[2] ?? '');
    // Every heading consumes a slug so ids stay in sync with the renderer, even when filtered out.
    const id = slugger.slug(text);
    if (level < minLevel || level > maxLevel) continue;
    headings.push({ level, text, id });
  }
  return headings;
}
