import { describe, expect, it } from 'vitest';
import {
  DOC_CATEGORIES,
  docEditUrl,
  docNeighbours,
  docRoute,
  docSlugs,
  groupDocs,
  loadDocs,
  parseDocPage,
  parseDocs,
  resolveDocLink,
} from './docs';
import { ContentError } from './front-matter';
import { extractHeadings, slugify } from './slug';

const sample = (overrides: Record<string, string> = {}, body = '## Intro\n\ntext\n\n### Sub') => {
  const meta = {
    title: 'Installation',
    description: 'How to install the launcher and the client.',
    order: '1',
    category: 'Getting started',
    ...overrides,
  };
  const lines = Object.entries(meta)
    .filter(([, value]) => value !== undefined)
    .map(([key, value]) => `${key}: ${value}`);
  return `---\n${lines.join('\n')}\n---\n\n${body}\n`;
};

describe('parseDocPage', () => {
  it('reads the front matter, derives the slug and route and extracts headings', () => {
    const page = parseDocPage('/repo/docs/installation.md', sample());
    expect(page).toMatchObject({
      slug: 'installation',
      file: 'installation.md',
      title: 'Installation',
      order: 1,
      category: 'Getting started',
      route: '/documentation/installation',
    });
    expect(page.headings).toEqual([
      { level: 2, text: 'Intro', id: 'intro' },
      { level: 3, text: 'Sub', id: 'sub' },
    ]);
    expect(page.body.startsWith('## Intro')).toBe(true);
  });

  it('rejects missing or invalid fields with a helpful error', () => {
    expect(() => parseDocPage('x.md', 'no front matter')).toThrow(/front matter block is missing/);
    expect(() => parseDocPage('x.md', sample({ category: 'Other' }))).toThrow(/category/);
    expect(() => parseDocPage('x.md', sample({ order: 'first' }))).toThrow(/order/);
    expect(() => parseDocPage('x.md', sample({ description: 'short' }))).toThrow(/too short/);
    expect(() => parseDocPage('Bad Name.md', sample())).toThrow(ContentError);
  });
});

describe('parseDocs / groupDocs / docNeighbours', () => {
  const modules = {
    '/docs/zeta.md': sample({ title: 'Zeta', order: '5', category: 'Help' }),
    '/docs/alpha.md': sample({ title: 'Alpha', order: '2', category: 'Getting started' }),
    '/docs/index.md': sample({ title: 'Index', order: '0', category: 'Getting started' }),
    '/docs/README.md': 'ignored',
    '/docs/launch.md': sample({ title: 'Launch', order: '0', category: 'Launcher' }),
  };
  it('sorts by category order then order and skips README files', () => {
    const pages = parseDocs(modules);
    expect(pages.map((page) => page.slug)).toEqual(['index', 'alpha', 'launch', 'zeta']);
    expect(groupDocs(pages).map((group) => [group.category, group.pages.length])).toEqual([
      ['Getting started', 2],
      ['Launcher', 1],
      ['Help', 1],
    ]);
  });
  it('finds neighbours in reading order', () => {
    const pages = parseDocs(modules);
    expect(docNeighbours(pages, 'alpha')).toMatchObject({
      previous: { slug: 'index' },
      next: { slug: 'launch' },
    });
    expect(docNeighbours(pages, 'index').previous).toBeUndefined();
    expect(docNeighbours(pages, 'nope')).toEqual({ previous: undefined, next: undefined });
  });
  it('rejects duplicate slugs', () => {
    expect(() => parseDocs({ '/a/x.md': sample(), '/b/x.md': sample() })).toThrow(/duplicate slug/);
  });
});

describe('routes and links', () => {
  it('maps special documents to their dedicated routes', () => {
    expect(docRoute('index')).toBe('/documentation');
    expect(docRoute('privacy')).toBe('/privacy');
    expect(docRoute('terms')).toBe('/terms');
    expect(docRoute('hud')).toBe('/documentation/hud');
  });
  it('rewrites docs cross-links and leaves everything else alone', () => {
    const known = new Set(['installation', 'faq', 'privacy']);
    expect(resolveDocLink('installation.md', known)).toBe('/documentation/installation');
    expect(resolveDocLink('./installation.md#2.-Verify the checksum', known)).toBe(
      '/documentation/installation#2-verify-the-checksum',
    );
    expect(resolveDocLink('privacy.md', known)).toBe('/privacy');
    expect(resolveDocLink('unknown.md', known)).toBeUndefined();
    expect(resolveDocLink('#anchor', known)).toBeUndefined();
    expect(resolveDocLink('https://example.com/x.md', known)).toBeUndefined();
    expect(resolveDocLink('/documentation/faq', known)).toBeUndefined();
  });
  it('builds the GitHub edit URL', () => {
    expect(docEditUrl('https://github.com/o/r/', 'hud.md')).toBe(
      'https://github.com/o/r/blob/HEAD/docs/hud.md',
    );
  });
});

describe('repository documentation', () => {
  it('loads every docs/*.md file with valid front matter', async () => {
    const pages = await loadDocs();
    expect(pages.length).toBe(docSlugs.length);
    expect(pages.length).toBeGreaterThanOrEqual(19);
    for (const page of pages) {
      expect(page.title.length).toBeGreaterThan(1);
      expect(page.description.length).toBeGreaterThanOrEqual(10);
      expect(DOC_CATEGORIES).toContain(page.category);
      expect(page.body.length).toBeGreaterThan(100);
    }
    expect(pages.map((page) => page.slug)).toEqual(
      expect.arrayContaining([
        'index',
        'installation',
        'faq',
        'privacy',
        'terms',
        'troubleshooting',
      ]),
    );
    expect(pages[0]?.slug).toBe('index');
  });

  it('has only cross-links that resolve to an existing page and anchor', async () => {
    const pages = await loadDocs();
    const slugs = new Set(pages.map((page) => page.slug));
    const anchors = new Map(
      pages.map((page) => [page.slug, new Set(extractHeadings(page.body).map((h) => h.id))]),
    );
    const problems: string[] = [];
    for (const page of pages) {
      const body = page.body.replace(/```[\s\S]*?```/g, '').replace(/`[^`\n]*`/g, '');
      for (const match of body.matchAll(/\]\(([^)\s]+)\)/g)) {
        const target = match[1] ?? '';
        if (/^(https?:|mailto:)/.test(target)) continue;
        if (target.startsWith('#')) {
          if (!anchors.get(page.slug)?.has(slugify(target.slice(1)))) {
            problems.push(`${page.file}: ${target}`);
          }
          continue;
        }
        const resolved = /^(?:\.\/)?([a-z0-9-]+)\.md(#.*)?$/.exec(target);
        if (!resolved) {
          problems.push(`${page.file}: unexpected link ${target}`);
          continue;
        }
        const slug = resolved[1] ?? '';
        if (!slugs.has(slug)) {
          problems.push(`${page.file}: unknown page ${target}`);
          continue;
        }
        const hash = resolved[2];
        if (hash && !anchors.get(slug)?.has(slugify(hash.slice(1)))) {
          problems.push(`${page.file}: unknown anchor ${target}`);
        }
      }
    }
    expect(problems).toEqual([]);
  });
});
