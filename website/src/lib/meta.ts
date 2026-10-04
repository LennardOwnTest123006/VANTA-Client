import { site } from '../config/site';
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
