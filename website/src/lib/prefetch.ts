/**
 * Route chunk prefetching. The header and footer call {@link prefetchRoute} when a link is hovered
 * or focused so the page chunk is usually cached before the click. Vite resolves each `import()`
 * to the same chunk as the lazy route component in `src/routes.tsx`.
 */

export const routeLoaders: Readonly<Record<string, () => Promise<unknown>>> = {
  '/download': () => import('../pages/DownloadPage'),
  '/features': () => import('../pages/FeaturesPage'),
  '/performance': () => import('../pages/PerformancePage'),
  '/screenshots': () => import('../pages/ScreenshotsPage'),
  '/changelog': () => import('../pages/ChangelogPage'),
  '/news': () => import('../pages/NewsPage'),
  '/documentation': () => import('../pages/DocumentationPage'),
  '/support': () => import('../pages/SupportPage'),
  '/faq': () => import('../pages/FaqPage'),
  '/about': () => import('../pages/AboutPage'),
  '/privacy': () => import('../pages/LegalPage'),
  '/terms': () => import('../pages/LegalPage'),
};

const prefetched = new Set<string>();

/** Top-level segment of a path: `/documentation/hud#x` → `/documentation`. */
export function routeBase(path: string): string {
  const pathOnly = path.split(/[?#]/)[0] ?? '';
  return `/${pathOnly.split('/')[1] ?? ''}`;
}

/** Starts loading the page chunk for `path` (idempotent; errors are ignored, navigation retries). */
export function prefetchRoute(path: string): void {
  const base = routeBase(path);
  const loader = routeLoaders[base];
  if (!loader || prefetched.has(base)) return;
  prefetched.add(base);
  loader().catch(() => {
    prefetched.delete(base);
  });
}
