import { lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { Layout } from './components/layout/Layout';
import { HomePage } from './pages/HomePage';

/*
 * Route table. The home page is part of the initial bundle; every other page is a lazy chunk.
 *
 * Adding a page: create `src/pages/<Name>Page.tsx`, add its loader to `routeLoaders` in
 * `src/lib/prefetch.ts` and a <Route> below, then flip `ready: true` for its entry in
 * `src/config/siteNav.ts` so the header, footer and RouteLink pick it up. The `*` route is the real
 * 404 page and must stay last.
 */

const DownloadPage = lazy(() => import('./pages/DownloadPage'));
const FeaturesPage = lazy(() => import('./pages/FeaturesPage'));
const PerformancePage = lazy(() => import('./pages/PerformancePage'));
const ScreenshotsPage = lazy(() => import('./pages/ScreenshotsPage'));
const ChangelogPage = lazy(() => import('./pages/ChangelogPage'));
const NewsPage = lazy(() => import('./pages/NewsPage'));
const NewsArticlePage = lazy(() => import('./pages/NewsArticlePage'));
const DocumentationPage = lazy(() => import('./pages/DocumentationPage'));
const SupportPage = lazy(() => import('./pages/SupportPage'));
const FaqPage = lazy(() => import('./pages/FaqPage'));
const AboutPage = lazy(() => import('./pages/AboutPage'));
const LegalPage = lazy(() => import('./pages/LegalPage'));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'));

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="download" element={<DownloadPage />} />
        <Route path="features" element={<FeaturesPage />} />
        <Route path="performance" element={<PerformancePage />} />
        <Route path="screenshots" element={<ScreenshotsPage />} />
        <Route path="changelog" element={<ChangelogPage />} />
        <Route path="news" element={<NewsPage />} />
        <Route path="news/:slug" element={<NewsArticlePage />} />
        <Route path="documentation" element={<DocumentationPage />} />
        {/* These documents have dedicated routes; keep the documentation URLs working. */}
        <Route path="documentation/index" element={<Navigate to="/documentation" replace />} />
        <Route path="documentation/privacy" element={<Navigate to="/privacy" replace />} />
        <Route path="documentation/terms" element={<Navigate to="/terms" replace />} />
        <Route path="documentation/:slug" element={<DocumentationPage />} />
        <Route path="support" element={<SupportPage />} />
        <Route path="faq" element={<FaqPage />} />
        <Route path="about" element={<AboutPage />} />
        <Route path="privacy" element={<LegalPage document="privacy" />} />
        <Route path="terms" element={<LegalPage document="terms" />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
