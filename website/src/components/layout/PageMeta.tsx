import { useEffect } from 'react';
import { useLocation } from 'react-router';
import { absoluteUrl, pageTitle } from '../../lib/meta';

export interface PageMetaProps {
  /** Page title without the site suffix; omit for the home page. */
  readonly title?: string;
  readonly description: string;
  /** Open Graph type. Articles (news posts) also get `article:published_time`. */
  readonly type?: 'website' | 'article';
  /** ISO date `YYYY-MM-DD` of an article. */
  readonly publishedTime?: string;
  /** Site-relative image for social cards; defaults to the 512px icon. */
  readonly image?: string;
  /** Ask search engines not to index the page (404, redirects). */
  readonly noindex?: boolean;
}

const DEFAULT_IMAGE = '/icon-512.png';

function upsertMeta(attribute: 'name' | 'property', key: string, content: string | undefined) {
  const selector = `meta[${attribute}="${key}"]`;
  let element = document.head.querySelector<HTMLMetaElement>(selector);
  if (content === undefined) {
    element?.remove();
    return;
  }
  if (!element) {
    element = document.createElement('meta');
    element.setAttribute(attribute, key);
    document.head.appendChild(element);
  }
  element.setAttribute('content', content);
}

function upsertLink(rel: string, href: string) {
  let element = document.head.querySelector<HTMLLinkElement>(`link[rel="${rel}"]`);
  if (!element) {
    element = document.createElement('link');
    element.setAttribute('rel', rel);
    document.head.appendChild(element);
  }
  element.setAttribute('href', href);
}

/**
 * Sets the per-route document metadata: title, description, canonical URL, Open Graph and Twitter
 * card tags and the robots directive. Everything is derived from the route so social previews and
 * crawlers see the right page even though the site is a client-rendered SPA.
 */
export function PageMeta({
  title,
  description,
  type = 'website',
  publishedTime,
  image = DEFAULT_IMAGE,
  noindex = false,
}: PageMetaProps) {
  const { pathname } = useLocation();
  useEffect(() => {
    const full = pageTitle(title);
    const url = absoluteUrl(pathname);
    const imageUrl = absoluteUrl(image);
    document.title = full;
    upsertMeta('name', 'description', description);
    upsertLink('canonical', url);
    upsertMeta('property', 'og:title', full);
    upsertMeta('property', 'og:description', description);
    upsertMeta('property', 'og:url', url);
    upsertMeta('property', 'og:type', type);
    upsertMeta('property', 'og:image', imageUrl);
    upsertMeta(
      'property',
      'article:published_time',
      type === 'article' ? publishedTime : undefined,
    );
    upsertMeta('name', 'twitter:card', 'summary');
    upsertMeta('name', 'twitter:title', full);
    upsertMeta('name', 'twitter:description', description);
    upsertMeta('name', 'twitter:image', imageUrl);
    upsertMeta('name', 'robots', noindex ? 'noindex, nofollow' : undefined);
  }, [title, description, pathname, type, publishedTime, image, noindex]);
  return null;
}
