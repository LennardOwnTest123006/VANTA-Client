import { site } from '../config/site';
import { type NavItem, siteNav } from '../config/siteNav';
import { env } from './env';

/** Builds the full document title for a page: "Download — VANTA Client" or the home page default. */
export function pageTitle(title: string | undefined): string {
  return title ? `${title} — ${site.titleSuffix}` : `${site.name} — ${site.tagline}`;
}

/**
 * Public origin of the website: `VITE_SITE_URL` when configured, otherwise the origin the page is
 * served from. Used for canonical links and absolute Open Graph URLs.
 */
export function siteOrigin(): string {
  if (env.siteUrl) return env.siteUrl;
  if (typeof window !== 'undefined' && window.location.origin !== 'null') {
    return window.location.origin;
  }
  return '';
}

/** Absolute URL of a site-relative path. */
export function absoluteUrl(path: string): string {
  return `${siteOrigin()}${path.startsWith('/') ? path : `/${path}`}`;
}

/**
 * The canonical form of a site path, for `<link rel="canonical">` and `og:url`.
 *
 * React Router matches routes case-insensitively and ignores a trailing slash, so `/Download/`
 * renders the download page as well. Its canonical URL must still be the one of the route:
 * - the query string and the hash are dropped;
 * - trailing slashes are removed, except for the home page `/`;
 * - a path whose first segment matches a registered route of `siteNav` (the routes of `routes.tsx`)
 *   in any letter case is spelled as that route's definition: `/DOWNLOAD` → `/download`. The rest of
 *   a nested path (the `:slug` of `/documentation/:slug` or `/news/:slug`) is kept as written,
 *   because those parameters are matched exactly.
 *
 * Paths that match no route (the 404 page) keep their spelling apart from the first two rules.
 */
export function canonicalPath(path: string, items: readonly NavItem[] = siteNav): string {
  const pathOnly = path.split(/[?#]/)[0] ?? '';
  const withSlash = pathOnly.startsWith('/') ? pathOnly : `/${pathOnly}`;
  const trimmed = withSlash.replace(/\/+$/, '');
  if (trimmed === '') return '/';
  const lower = trimmed.toLowerCase();
  const route = items.find((item) => {
    if (!item.ready) return false;
    const to = item.to.toLowerCase();
    return lower === to || lower.startsWith(`${to}/`);
  });
  return route ? `${route.to}${trimmed.slice(route.to.length)}` : trimmed;
}
