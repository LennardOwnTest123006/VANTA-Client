import { useEffect } from 'react';
import { pageTitle } from '../../lib/meta';

export interface PageMetaProps {
  /** Page title without the site suffix; omit for the home page. */
  readonly title?: string;
  readonly description: string;
}

function setMeta(selector: string, attribute: string, value: string) {
  let element = document.head.querySelector<HTMLMetaElement>(selector);
  if (!element) {
    element = document.createElement('meta');
    const [, attrName, attrValue] = /\[(\w+(?::\w+)?)="([^"]+)"\]/.exec(selector) ?? [];
    if (attrName && attrValue) element.setAttribute(attrName, attrValue);
    document.head.appendChild(element);
  }
  element.setAttribute(attribute, value);
}

/** Sets `document.title`, the meta description and the Open Graph title/description per route. */
export function PageMeta({ title, description }: PageMetaProps) {
  useEffect(() => {
    const full = pageTitle(title);
    document.title = full;
    setMeta('meta[name="description"]', 'content', description);
    setMeta('meta[property="og:title"]', 'content', full);
    setMeta('meta[property="og:description"]', 'content', description);
  }, [title, description]);
  return null;
}
