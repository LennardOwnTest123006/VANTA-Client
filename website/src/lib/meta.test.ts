import { isValidElement, type ReactNode } from 'react';
import { createRoutesFromElements, matchRoutes, Navigate, type RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { type NavItem, siteNav } from '../config/siteNav';
import { AppRoutes } from '../routes';
import { absoluteUrl, canonicalPath, pageTitle } from './meta';

/** The route objects of `routes.tsx`, as React Router builds them from the <Route> elements. */
function appRouteObjects(): RouteObject[] {
  const routes = AppRoutes();
  if (!isValidElement<{ children: ReactNode }>(routes)) throw new Error('no <Routes>');
  return createRoutesFromElements(routes.props.children);
}

/** Static page routes of `routes.tsx` ("download", "documentation"): no parameter, no redirect, no 404. */
function staticPagePaths(routes: readonly RouteObject[]): string[] {
  return routes.flatMap((route) => {
    const nested = staticPagePaths(route.children ?? []);
    const path = route.path;
    if (path === undefined || path.includes(':') || path === '*') return nested;
    if (isValidElement(route.element) && route.element.type === Navigate) return nested;
    return [`/${path}`, ...nested];
  });
}

describe('pageTitle', () => {
  it('appends the site suffix, or uses the tagline on the home page', () => {
    expect(pageTitle('Download')).toBe('Download — VANTA Client');
    expect(pageTitle(undefined)).toBe('VANTA Client — Your Minecraft. Refined.');
  });
});

describe('canonicalPath', () => {
  it('keeps canonical paths as they are', () => {
    expect(canonicalPath('/')).toBe('/');
    expect(canonicalPath('/download')).toBe('/download');
    expect(canonicalPath('/documentation/installation')).toBe('/documentation/installation');
    expect(canonicalPath('/news/introducing-vanta')).toBe('/news/introducing-vanta');
  });

  it('strips trailing slashes except for the home page', () => {
    expect(canonicalPath('/download/')).toBe('/download');
    expect(canonicalPath('/download///')).toBe('/download');
    expect(canonicalPath('/documentation/installation/')).toBe('/documentation/installation');
    expect(canonicalPath('//')).toBe('/');
    expect(canonicalPath('')).toBe('/');
  });

  it('drops the query string and the hash', () => {
    expect(canonicalPath('/download?utm_source=x')).toBe('/download');
    expect(canonicalPath('/download/#verify')).toBe('/download');
    expect(canonicalPath('/?ref=home#top')).toBe('/');
    expect(canonicalPath('/features?a=1#hud')).toBe('/features');
  });

  it('spells case variants of a route the way the route is defined', () => {
    expect(canonicalPath('/DOWNLOAD')).toBe('/download');
    expect(canonicalPath('/Download/?ref=x#verify')).toBe('/download');
    expect(canonicalPath('/FAQ')).toBe('/faq');
    expect(canonicalPath('/Documentation/installation')).toBe('/documentation/installation');
    expect(canonicalPath('/NEWS/introducing-vanta/')).toBe('/news/introducing-vanta');
  });

  it('keeps the parameter part of nested paths and the spelling of unknown paths', () => {
    // Slugs are matched exactly, so a different spelling is a different (missing) page.
    expect(canonicalPath('/documentation/Installation')).toBe('/documentation/Installation');
    expect(canonicalPath('/This-Page-Does-Not-Exist/')).toBe('/This-Page-Does-Not-Exist');
    // A longer first segment is not the route.
    expect(canonicalPath('/downloads')).toBe('/downloads');
    expect(canonicalPath('/Newsletter')).toBe('/Newsletter');
  });

  it('only maps routes that are registered', () => {
    const items: NavItem[] = [
      { ...siteNav[0]!, to: '/download', ready: true },
      { ...siteNav[0]!, to: '/later', ready: false },
    ];
    expect(canonicalPath('/Download', items)).toBe('/download');
    expect(canonicalPath('/LATER', items)).toBe('/LATER');
  });

  it('builds absolute URLs from canonical paths', () => {
    expect(absoluteUrl(canonicalPath('/Download/'))).toMatch(/^https?:\/\/[^/]+\/download$/);
  });
});

describe('canonicalPath and the route table of routes.tsx', () => {
  const routes = appRouteObjects();
  const staticPaths = staticPagePaths(routes);
  const navPaths = siteNav.filter((item) => item.ready).map((item) => item.to);

  it('has a siteNav entry for every static page route', () => {
    expect(staticPaths.length).toBeGreaterThan(5);
    expect([...staticPaths].sort()).toEqual([...navPaths].sort());
  });

  it('maps every case variant React Router accepts to the route definition', () => {
    for (const path of staticPaths) {
      for (const variant of [path.toUpperCase(), `${path.toUpperCase()}/`, `${path}/?x=1#y`]) {
        // React Router renders the route for the variant (case-insensitive, trailing slash ignored)…
        const matched = matchRoutes(routes, variant.split(/[?#]/)[0] ?? '');
        expect(matched?.at(-1)?.route.path, variant).toBe(path.slice(1));
        // …and the canonical URL names the route as defined.
        expect(canonicalPath(variant), variant).toBe(path);
      }
    }
  });
});
