import { site } from '../config/site';

/** Builds the full document title for a page: "Download — VANTA Client" or the home page default. */
export function pageTitle(title: string | undefined): string {
  return title ? `${title} — ${site.titleSuffix}` : `${site.name} — ${site.tagline}`;
}
