/**
 * Sitemap and robots.txt generation (pure functions, used by the Vite plugin in
 * `plugins/sitemap.ts` and unit tested on their own).
 */

export interface SitemapEntry {
  /** Site-relative path starting with `/`. */
  readonly path: string;
  /** ISO date `YYYY-MM-DD`, when known. */
  readonly lastmod?: string;
  readonly changefreq?: 'daily' | 'weekly' | 'monthly' | 'yearly';
  readonly priority?: number;
}

function escapeXml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/** Removes trailing slashes from a base URL. */
export function normalizeBaseUrl(baseUrl: string | undefined): string | undefined {
  if (!baseUrl) return undefined;
  const trimmed = baseUrl.trim().replace(/\/+$/, '');
  return /^https?:\/\/[^\s/]+$/i.test(trimmed) ? trimmed : undefined;
}

/**
 * Builds `sitemap.xml`. With a base URL every `<loc>` is absolute as the protocol requires; without
 * one the paths are emitted as written so a local build still has a valid-looking file.
 */
export function buildSitemap(
  entries: readonly SitemapEntry[],
  baseUrl: string | undefined,
): string {
  const base = normalizeBaseUrl(baseUrl) ?? '';
  const seen = new Set<string>();
  const urls = entries
    .filter((entry) => {
      if (seen.has(entry.path)) return false;
      seen.add(entry.path);
      return true;
    })
    .map((entry) => {
      const parts = [`    <loc>${escapeXml(`${base}${entry.path}`)}</loc>`];
      if (entry.lastmod) parts.push(`    <lastmod>${entry.lastmod}</lastmod>`);
      if (entry.changefreq) parts.push(`    <changefreq>${entry.changefreq}</changefreq>`);
      if (entry.priority !== undefined)
        parts.push(`    <priority>${entry.priority.toFixed(1)}</priority>`);
      return `  <url>\n${parts.join('\n')}\n  </url>`;
    });
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls.join('\n')}\n</urlset>\n`;
}

/** Builds `robots.txt`; the `Sitemap:` line needs an absolute URL and is omitted without a base. */
export function buildRobots(baseUrl: string | undefined): string {
  const base = normalizeBaseUrl(baseUrl);
  const lines = ['User-agent: *', 'Allow: /'];
  if (base) lines.push('', `Sitemap: ${base}/sitemap.xml`);
  return `${lines.join('\n')}\n`;
}
