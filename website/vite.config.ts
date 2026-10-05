/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { siteMetaPlugin } from './plugins/site-meta';
import { sitemapPlugin } from './plugins/sitemap';
import { siteNav } from './src/config/siteNav';

/**
 * Vite configuration for the VANTA website (static SPA).
 *
 * - React + Tailwind 4 plugins, plus the sitemap/robots generator and the absolute social images
 *   (`og:image`, `twitter:image`) of `index.html` once `VITE_SITE_URL` is set.
 * - `shared/`, `docs/` and `assets/` at the repository root are read at build time (release
 *   manifests, documentation, screenshots), so the dev server is allowed to serve the monorepo root.
 * - React is split into its own long-lived vendor chunk; every other page is code-split per route.
 *   Lazily loaded markdown collections are grouped into one chunk each (`docs-content`,
 *   `news-content`) so the sidebar, search and prev/next links cost a single request.
 * - A build manifest is emitted so `scripts/check-bundle-size.mjs` can enforce the gzip budget; the
 *   script deletes `dist/.vite/` afterwards so the manifest is never deployed.
 */
const root = fileURLToPath(new URL('..', import.meta.url));

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    siteMetaPlugin(),
    sitemapPlugin({
      staticRoutes: siteNav.filter((item) => item.ready).map((item) => item.to),
      docsDir: fileURLToPath(new URL('../docs', import.meta.url)),
      newsDir: fileURLToPath(new URL('./content/news', import.meta.url)),
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    fs: {
      allow: [root],
    },
  },
  build: {
    target: 'es2022',
    sourcemap: false,
    manifest: true,
    cssMinify: true,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (
            id.includes('/node_modules/react/') ||
            id.includes('/node_modules/react-dom/') ||
            id.includes('/node_modules/scheduler/')
          ) {
            return 'react-vendor';
          }
          if (/\/docs\/[^/]+\.md\?raw$/.test(id)) return 'docs-content';
          if (/\/content\/news\/[^/]+\.md\?raw$/.test(id)) return 'news-content';
          return undefined;
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: false,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}', 'plugins/**/*.test.ts'],
    css: false,
    restoreMocks: true,
    clearMocks: true,
  },
});
