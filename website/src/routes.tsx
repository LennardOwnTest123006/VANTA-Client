import { lazy } from 'react';
import { Route, Routes } from 'react-router';
import { Layout } from './components/layout/Layout';
import { HomePage } from './pages/HomePage';

/*
 * Route table. The home page is part of the initial bundle; every other page is a lazy chunk.
 *
 * Adding a page: create `src/pages/<Name>Page.tsx`, add a lazy import and a <Route> below, then flip
 * `ready: true` for its entry in `src/config/siteNav.ts` so the header, footer and RouteLink pick it
 * up. The `*` route is the real 404 page and must stay last.
 */
const DownloadPage = lazy(() => import('./pages/DownloadPage'));
const FeaturesPage = lazy(() => import('./pages/FeaturesPage'));
const PerformancePage = lazy(() => import('./pages/PerformancePage'));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'));

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="download" element={<DownloadPage />} />
        <Route path="features" element={<FeaturesPage />} />
        <Route path="performance" element={<PerformancePage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
