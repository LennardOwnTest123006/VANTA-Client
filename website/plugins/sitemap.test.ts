import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { collectEntries } from './sitemap';

// Vitest runs with the website directory as its working directory.
const docsDir = resolve(process.cwd(), '../docs');
const newsDir = resolve(process.cwd(), 'content/news');

describe('collectEntries', () => {
  it('lists static routes, documentation pages and published news posts exactly once', () => {
    const entries = collectEntries({
      staticRoutes: ['/download', '/documentation', '/privacy', '/'],
      docsDir,
      newsDir,
    });
    const paths = entries.map((entry) => entry.path);
    expect(paths[0]).toBe('/');
    expect(new Set(paths).size).toBe(paths.length);
    expect(paths).toEqual(
      expect.arrayContaining([
        '/download',
        '/documentation',
        '/documentation/installation',
        '/privacy',
        '/terms',
        '/news/introducing-vanta',
      ]),
    );
    expect(paths).not.toContain('/documentation/index');
    expect(paths).not.toContain('/documentation/privacy');
    expect(entries.find((entry) => entry.path === '/news/introducing-vanta')?.lastmod).toBe(
      '2026-10-04',
    );
  });
  it('tolerates missing directories', () => {
    expect(collectEntries({ staticRoutes: [], docsDir: '/nope', newsDir: '/nope' })).toEqual([
      { path: '/', changefreq: 'weekly', priority: 1 },
    ]);
  });
});
