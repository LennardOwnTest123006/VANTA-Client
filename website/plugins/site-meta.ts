import type { Plugin } from 'vite';
import { normalizeBaseUrl } from '../src/lib/sitemap';

/**
 * Vite plugin that makes the social-card images of the static `index.html` absolute when the public
 * origin of the site is configured with `VITE_SITE_URL`.
 *
 * Crawlers that build link previews (chat apps, social networks) read the HTML as served and do not
 * run JavaScript, so the tags `PageMeta` sets at runtime never reach them, and most of them ignore a
 * site-relative `og:image`. Without `VITE_SITE_URL` the file keeps its site-relative tags, exactly as
 * written. Only an origin (`https://host`, no path) is accepted. Netlify's `URL` build variable is
 * deliberately not used here (the sitemap does use it).
 *
 * The same `index.html` answers every route (the SPA fallback `/* /index.html 200` in `_redirects`
 * and `netlify.toml`), so it must only carry tags that are true for every page. That is why the
 * plugin never adds `og:url` or `<link rel="canonical">`: a value for `/` would declare every deep
 * link (`/download`, `/documentation/…`) a duplicate of the home page for crawlers that do not run
 * JavaScript, and Google advises against changing an existing canonical with JavaScript. `PageMeta`
 * inserts both for the current route at runtime; a crawler without JavaScript falls back to the URL
 * it fetched, which is the right one.
 */

/** Image tags whose site-relative `content` is made absolute. */
const IMAGE_META =
  /(<meta\s+(?:property|name)="(?:og:image|og:image:secure_url|twitter:image)"\s+content=")(\/[^"]*)(")/g;

/** Escapes a value for a double-quoted HTML attribute. */
export function escapeAttribute(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/** The first non-empty candidate, normalised to an origin without trailing slash, or `undefined`. */
export function resolveSiteUrl(...candidates: readonly unknown[]): string | undefined {
  const first = candidates.find(
    (candidate): candidate is string => typeof candidate === 'string' && candidate.trim() !== '',
  );
  return normalizeBaseUrl(first);
}

/**
 * Makes the site-relative `og:image`, `og:image:secure_url` and `twitter:image` of `index.html`
 * absolute. Everything else, page-specific tags in particular, is left alone. Returns the HTML
 * unchanged when `siteUrl` is not a valid origin.
 */
export function applySiteUrl(html: string, siteUrl: string | undefined): string {
  const base = normalizeBaseUrl(siteUrl);
  if (!base) return html;
  return html.replace(
    IMAGE_META,
    (_match, start: string, path: string, end: string) =>
      `${start}${escapeAttribute(`${base}${path}`)}${end}`,
  );
}

export interface SiteMetaPluginOptions {
  /** Overrides the environment (tests). */
  readonly siteUrl?: string | undefined;
}

export function siteMetaPlugin(options: SiteMetaPluginOptions = {}): Plugin {
  let siteUrl: string | undefined;
  return {
    name: 'vanta:site-meta',
    configResolved(config) {
      siteUrl = resolveSiteUrl(options.siteUrl, config.env.VITE_SITE_URL);
    },
    transformIndexHtml(html) {
      return applySiteUrl(html, siteUrl);
    },
  };
}
