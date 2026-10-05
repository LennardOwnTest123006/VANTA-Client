import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import type { Plugin } from 'vite';
import { parseFrontMatterTyped } from '../src/lib/front-matter';
import { buildRobots, buildSitemap, type SitemapEntry } from '../src/lib/sitemap';
import { resolveSiteUrl } from './site-meta';

/**
 * Vite plugin that emits `sitemap.xml` and `robots.txt` at build time.
 *
 * Routes come from three places: the static routes passed in (the ready entries of `siteNav`), the
 * documentation files in `docs/` (mapped like `src/lib/docs.ts` does: `index` → `/documentation`,
 * `privacy`/`terms` → their legal routes) and the published news posts in `content/news/`. The base
 * URL is `VITE_SITE_URL`, falling back to Netlify's `URL` build variable; when neither is set the
 * sitemap keeps site-relative paths and robots.txt omits the `Sitemap:` line. The dev server serves
 * both files too.
 */
export interface SitemapPluginOptions {
  readonly staticRoutes: readonly string[];
  readonly docsDir: string;
  readonly newsDir: string;
  readonly siteUrl?: string | undefined;
}

const DOC_ROUTES: Readonly<Record<string, string>> = {
  index: '/documentation',
  privacy: '/privacy',
  terms: '/terms',
};

function markdownFiles(dir: string): string[] {
  try {
    return readdirSync(dir)
      .filter((file) => file.endsWith('.md') && !/^readme\.md$/i.test(file))
      .sort();
  } catch {
    return [];
  }
}

/** Collects every sitemap entry. Exported for tests. */
export function collectEntries(options: SitemapPluginOptions): SitemapEntry[] {
  const entries: SitemapEntry[] = [
    { path: '/', changefreq: 'weekly', priority: 1 },
    ...options.staticRoutes
      .filter((route) => route !== '/')
      .map((route) => ({ path: route, changefreq: 'weekly' as const, priority: 0.8 })),
  ];
  for (const file of markdownFiles(options.docsDir)) {
    const slug = file.replace(/\.md$/, '');
    const path = DOC_ROUTES[slug] ?? `/documentation/${slug}`;
    if (!entries.some((entry) => entry.path === path)) {
      entries.push({ path, changefreq: 'monthly', priority: 0.6 });
    }
  }
  for (const file of markdownFiles(options.newsDir)) {
    const match = /^(\d{4}-\d{2}-\d{2})-([a-z0-9][a-z0-9-]*)\.md$/.exec(file);
    if (!match) continue;
    const meta = parseFrontMatterTyped(readFileSync(join(options.newsDir, file), 'utf8'));
    if (meta.values.draft === true) continue;
    const date = typeof meta.values.date === 'string' ? meta.values.date : match[1];
    entries.push({
      path: `/news/${match[2] ?? ''}`,
      changefreq: 'yearly',
      priority: 0.5,
      ...(date ? { lastmod: date } : {}),
    });
  }
  return entries;
}

export function sitemapPlugin(options: SitemapPluginOptions): Plugin {
  let siteUrl: string | undefined = options.siteUrl;
  const render = () => ({
    sitemap: buildSitemap(collectEntries(options), siteUrl),
    robots: buildRobots(siteUrl),
  });
  return {
    name: 'vanta:sitemap',
    configResolved(config) {
      // VITE_SITE_URL, else Netlify's URL build variable. (The social images of index.html use
      // VITE_SITE_URL only, see plugins/site-meta.ts.)
      siteUrl = resolveSiteUrl(options.siteUrl, config.env.VITE_SITE_URL, process.env.URL);
    },
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = req.url?.split('?')[0];
        if (url !== '/sitemap.xml' && url !== '/robots.txt') {
          next();
          return;
        }
        const files = render();
        res.setHeader(
          'Content-Type',
          url === '/sitemap.xml' ? 'application/xml; charset=utf-8' : 'text/plain; charset=utf-8',
        );
        res.end(url === '/sitemap.xml' ? files.sitemap : files.robots);
      });
    },
    generateBundle() {
      const files = render();
      this.emitFile({ type: 'asset', fileName: 'sitemap.xml', source: files.sitemap });
      this.emitFile({ type: 'asset', fileName: 'robots.txt', source: files.robots });
      const count = (files.sitemap.match(/<loc>/g) ?? []).length;
      this.info(
        siteUrl
          ? `sitemap.xml: ${count} URLs under ${siteUrl}`
          : `sitemap.xml: ${count} URLs (set VITE_SITE_URL for absolute URLs)`,
      );
    },
  };
}
