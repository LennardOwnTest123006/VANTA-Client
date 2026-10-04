import { describe, expect, it } from 'vitest';
import { siteNav } from '../config/siteNav';
import { prefetchRoute, routeBase, routeLoaders } from './prefetch';

describe('prefetch', () => {
  it('has a chunk loader for every ready navigation item', () => {
    for (const item of siteNav.filter((entry) => entry.ready)) {
      expect(routeLoaders[item.to], `missing loader for ${item.to}`).toBeTypeOf('function');
    }
  });
  it('reduces paths to their top-level route', () => {
    expect(routeBase('/documentation/hud#widgets')).toBe('/documentation');
    expect(routeBase('/news?x=1')).toBe('/news');
    expect(routeBase('/')).toBe('/');
  });
  it('prefetches idempotently and ignores unknown routes', async () => {
    expect(() => {
      prefetchRoute('/nope');
    }).not.toThrow();
    prefetchRoute('/about');
    prefetchRoute('/about');
    await expect(routeLoaders['/about']!()).resolves.toHaveProperty('default');
  });
});
