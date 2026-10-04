import { describe, expect, it } from 'vitest';
import { findNavItem, headerNavItems, isRouteReady, navGroupItems, siteNav } from './siteNav';
import { footerColumns } from './footer';

describe('siteNav', () => {
  it('lists every planned route exactly once with an absolute path', () => {
    const paths = siteNav.map((item) => item.to);
    expect(new Set(paths).size).toBe(paths.length);
    for (const path of paths) expect(path.startsWith('/')).toBe(true);
    expect(paths).toEqual(
      expect.arrayContaining([
        '/download',
        '/features',
        '/performance',
        '/screenshots',
        '/changelog',
        '/news',
        '/documentation',
        '/support',
        '/faq',
        '/about',
        '/privacy',
        '/terms',
      ]),
    );
  });

  it('only exposes ready routes in the header and hides the Download CTA from the list', () => {
    const header = headerNavItems();
    expect(header.every((item) => item.ready)).toBe(true);
    expect(header.map((item) => item.to)).not.toContain('/download');
    expect(header.map((item) => item.to)).toEqual(['/features', '/performance']);
  });

  it('reports readiness per route, including nested paths and the home page', () => {
    expect(isRouteReady('/')).toBe(true);
    expect(isRouteReady('/download')).toBe(true);
    expect(isRouteReady('/documentation')).toBe(false);
    expect(isRouteReady('/documentation/install')).toBe(false);
    expect(isRouteReady('/nope')).toBe(false);
    expect(findNavItem('/features#hud')?.label).toBe('Features');
  });

  it('groups footer items by column and drops empty columns', () => {
    expect(navGroupItems('legal')).toEqual([]);
    const columns = footerColumns();
    const titles = columns.map((column) => column.title);
    expect(titles).toContain('Product');
    for (const column of columns) expect(column.links.length).toBeGreaterThan(0);
  });
});
